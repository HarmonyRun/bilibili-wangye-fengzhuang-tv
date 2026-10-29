package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Releases OTA：检查最新版本、下载 APK、拉起系统安装器。
 * 所有回调都在主线程；APK 下载到 cache/updates 下，安装时通过 FileProvider 授权。
 * FileProvider 的 &lt;provider&gt; 声明与 androidx.core 依赖由 App 侧（UI 层）负责。
 */
final class UpdateManager {
    static final String GITHUB_REPO = "HarmonyRun/bilibili-wangye-fengzhuang-tv";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String USER_AGENT = "BiliLivingRoom";
    private static final String ACCEPT = "application/vnd.github+json";
    private static final String API_VERSION = "2022-11-28";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    /** 一次检查更新的结果。hasUpdate=false 时仍会带回最新 release 信息，便于 UI 提示“已是最新”。 */
    static final class UpdateInfo {
        final boolean hasUpdate;
        final String tag;
        final String version;
        final String notes;
        final String apkUrl;
        final long size;
        UpdateInfo(boolean hasUpdate, String tag, String version, String notes, String apkUrl, long size) {
            this.hasUpdate = hasUpdate;
            this.tag = tag == null ? "" : tag;
            this.version = version == null ? "" : version;
            this.notes = notes == null ? "" : notes;
            this.apkUrl = apkUrl == null ? "" : apkUrl;
            this.size = size;
        }
    }

    interface CheckCallback { void onResult(UpdateInfo info, String error); }

    interface DownloadCallback {
        void onProgress(int percent);
        void onResult(File file, String error);
    }

    private static final class UpdateException extends Exception {
        UpdateException(String message) { super(message); }
    }

    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    UpdateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 停止后台线程（可选调用，页面销毁时使用）。 */
    void shutdown() {
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    /** 请求 GitHub releases/latest，解析 APK 资产并与当前版本比较。 */
    void check(CheckCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject release = getJson(API_URL);
                String tag = release.optString("tag_name");
                String name = release.optString("name");
                String notes = release.optString("body");
                int[] remoteVersion = parseVersion(firstNonEmpty(tag, name));
                if (remoteVersion == null) throw new UpdateException("无法解析发布版本号，请稍后再试");
                int[] currentVersion = parseVersion(BuildConfig.VERSION_NAME);
                boolean hasUpdate = currentVersion == null || compareVersion(remoteVersion, currentVersion) > 0;
                JSONObject asset = pickApkAsset(release.optJSONArray("assets"));
                if (asset == null) throw new UpdateException("该版本没有可下载的 APK 安装包");
                String apkUrl = asset.optString("browser_download_url");
                if (apkUrl.isEmpty()) throw new UpdateException("该版本没有可下载的 APK 安装包");
                UpdateInfo info = new UpdateInfo(hasUpdate, tag, formatVersion(remoteVersion), notes, apkUrl,
                        asset.optLong("size", 0));
                main.post(() -> callback.onResult(info, null));
            } catch (UpdateException error) {
                main.post(() -> callback.onResult(null, error.getMessage()));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, "检查更新失败，请检查网络后重试"));
            }
        });
    }

    /** 下载 APK 到 cache/updates/update-<version>.apk，约每 5% 回调一次进度。 */
    void download(UpdateInfo info, DownloadCallback callback) {
        if (info == null || info.apkUrl.isEmpty()) {
            main.post(() -> callback.onResult(null, "下载地址无效"));
            return;
        }
        final File dir = new File(context.getCacheDir(), "updates");
        final File target = new File(dir, "update-" + sanitizeVersion(info.version) + ".apk");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            File temp = new File(dir, target.getName() + ".part");
            try {
                if (!dir.exists() && !dir.mkdirs()) throw new UpdateException("无法创建缓存目录");
                connection = (HttpURLConnection) new URL(info.apkUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "*/*");
                int code = connection.getResponseCode();
                if (code != 200) throw new UpdateException("下载失败（HTTP " + code + "）");
                long total = info.size;
                if (total <= 0) total = parseLong(connection.getHeaderField("Content-Length"), 0);
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     OutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        downloaded += read;
                        if (total > 0) {
                            int percent = (int) (downloaded * 100 / total);
                            if (percent >= lastPercent + 5) {
                                lastPercent = percent;
                                final int progress = Math.max(0, Math.min(100, percent));
                                main.post(() -> callback.onProgress(progress));
                            }
                        }
                    }
                    output.flush();
                }
                long length = temp.length();
                if (length <= 0) throw new UpdateException("下载失败，安装包为空");
                if (total > 0 && length < total) throw new UpdateException("下载不完整，请重试");
                if (target.exists() && !target.delete()) throw new UpdateException("无法覆盖旧的安装包");
                if (!temp.renameTo(target)) {
                    copyFile(temp, target);
                    // 复制成功后清理临时文件，失败也不影响最终文件
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
                if (target.length() <= 0) throw new UpdateException("下载失败，安装包为空");
                final File result = target;
                main.post(() -> {
                    callback.onProgress(100);
                    callback.onResult(result, null);
                });
            } catch (UpdateException error) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                main.post(() -> callback.onResult(null, error.getMessage()));
            } catch (Exception error) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                main.post(() -> callback.onResult(null, "下载失败，请检查网络后重试"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /** 用 FileProvider 生成 content:// Uri 并拉起系统安装器；失败抛出 IllegalStateException。 */
    static void install(Activity activity, File apk) {
        try {
            if (apk == null || !apk.exists()) throw new UpdateException("安装包不存在，请重新下载");
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception error) {
            throw new IllegalStateException("无法启动安装器，请检查 FileProvider 配置", error);
        }
    }

    /** API>=26 需要“安装未知应用”授权；更低版本默认允许。 */
    static boolean canInstall(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return activity.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /** 跳转“安装未知应用”授权页（API>=26）。 */
    static void openUnknownSourcesSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
            // 部分设备无此设置页，静默忽略
        }
    }

    private static JSONObject getJson(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", ACCEPT);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION);
        try {
            int code = connection.getResponseCode();
            if (code == 404) throw new UpdateException("暂时没有可用的更新版本");
            if (code == 403 || code == 429) throw new UpdateException("更新服务暂时被限制，请稍后再试");
            if (code != 200) throw new UpdateException("检查更新失败（HTTP " + code + "）");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                return new JSONObject(body.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    /** 优先名字含 debug/release 的 apk，其次第一个 .apk 资产。 */
    private static JSONObject pickApkAsset(JSONArray assets) {
        if (assets == null) return null;
        JSONObject fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk")) continue;
            if (fallback == null) fallback = asset;
            if (name.contains("debug") || name.contains("release")) return asset;
        }
        return fallback;
    }

    /** 从 tag/name 提取 major.minor.patch；无法提取时返回 null。 */
    private static int[] parseVersion(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (matcher.find()) {
            try {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
                return new int[]{major, minor, patch};
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher number = NUMBER_PATTERN.matcher(value);
        if (number.find()) {
            try {
                return new int[]{Integer.parseInt(number.group(1)), 0, 0};
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int compareVersion(int[] remote, int[] current) {
        for (int i = 0; i < 3; i++) {
            if (remote[i] != current[i]) return remote[i] > current[i] ? 1 : -1;
        }
        return 0;
    }

    private static String formatVersion(int[] version) {
        return version[0] + "." + version[1] + "." + version[2];
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String sanitizeVersion(String version) {
        if (version == null || version.isEmpty()) return "latest";
        return version.replaceAll("[^0-9A-Za-z._-]", "_");
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(source));
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
