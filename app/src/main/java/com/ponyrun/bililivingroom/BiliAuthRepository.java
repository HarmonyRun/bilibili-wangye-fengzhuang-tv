package com.ponyrun.bililivingroom;

import android.graphics.Bitmap;
import android.webkit.CookieManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BiliAuthRepository {
    interface QrCallback { void onResult(String authCode, String url, Bitmap qr, String error); }
    interface PollCallback { void onResult(int state, String message); }

    static final int WAITING = 0;
    static final int SUCCESS = 1;
    static final int EXPIRED = 2;
    static final int FAILED = 3;

    private static final String APP_KEY = "5ae412b53418aac5";
    private static final String APP_SECRET = "5b9cf6c9786efd204dcf0c1ce2d08436";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Activity 销毁时停止登录轮询线程。 */
    void shutdown() {
        executor.shutdownNow();
    }

    void requestQr(QrCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = post("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code", new HashMap<>()).getJSONObject("data");
                String authCode = data.getString("auth_code");
                String url = data.getString("url");
                callback.onResult(authCode, url, qr(url, 540), null);
            } catch (Exception e) {
                callback.onResult(null, null, null, "二维码生成失败，请检查网络");
            }
        });
    }

    void poll(String authCode, PollCallback callback) {
        executor.execute(() -> {
            try {
                Map<String, String> input = new HashMap<>();
                input.put("auth_code", authCode);
                JSONObject root = post("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll", input);
                int code = root.optInt("code", -1);
                if (code == 0) {
                    JSONObject data = root.getJSONObject("data");
                    persistCookies(data.optJSONObject("cookie_info"));
                    callback.onResult(SUCCESS, "登录成功");
                } else if (code == 86038) callback.onResult(EXPIRED, "二维码已过期");
                else if (code == 86039) callback.onResult(WAITING, "等待手机确认");
                else callback.onResult(FAILED, root.optString("message", "登录失败"));
            } catch (Exception e) {
                callback.onResult(FAILED, "网络连接失败");
            }
        });
    }

    static boolean hasSession() {
        String cookie = CookieManager.getInstance().getCookie("https://api.bilibili.com");
        return cookie != null && cookie.contains("SESSDATA=");
    }

    static String cookieHeader() {
        String cookie = CookieManager.getInstance().getCookie("https://api.bilibili.com");
        return cookie == null ? "" : cookie;
    }

    static String userMid() {
        String cookie = cookieHeader();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|;\\s*)DedeUserID=([0-9]+)").matcher(cookie);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 退出登录：清空 CookieManager 中的 B 站凭据。 */
    static void logout() {
        logout(null);
    }

    /** 退出登录，Cookie 清除完成后回调（回调在主线程）。 */
    static void logout(android.webkit.ValueCallback<Boolean> callback) {
        CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(callback);
        manager.flush();
    }

    private JSONObject post(String endpoint, Map<String, String> input) throws Exception {
        Map<String, String> params = new HashMap<>(input);
        params.put("appkey", APP_KEY);
        params.put("ts", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("local_id", "0");
        params.put("mobi_app", "iphone");
        params.put("device", "pad");
        params.put("device_name", "iPad");
        List<Map.Entry<String, String>> entries = new ArrayList<>(params.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, String>>() {
            @Override public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return a.getKey().compareTo(b.getKey());
            }
        });
        StringBuilder raw = new StringBuilder();
        for (Map.Entry<String, String> entry : entries) {
            if (raw.length() > 0) raw.append('&');
            raw.append(entry.getKey()).append('=').append(entry.getValue());
        }
        params.put("sign", md5(raw + APP_SECRET));
        StringBuilder body = new StringBuilder();
        entries = new ArrayList<>(params.entrySet());
        for (Map.Entry<String, String> entry : entries) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 BiliLivingRoom/" + BuildConfig.VERSION_NAME);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            return new JSONObject(json.toString());
        } finally {
            connection.disconnect();
        }
    }

    private void persistCookies(JSONObject info) {
        if (info == null) return;
        JSONArray domains = info.optJSONArray("domains");
        JSONArray cookies = info.optJSONArray("cookies");
        if (domains == null || cookies == null) return;
        CookieManager manager = CookieManager.getInstance();
        manager.setAcceptCookie(true);
        for (int d = 0; d < domains.length(); d++) {
            String domain = domains.optString(d).replaceFirst("^\\.", "");
            for (int i = 0; i < cookies.length(); i++) {
                JSONObject cookie = cookies.optJSONObject(i);
                if (cookie == null) continue;
                String value = cookie.optString("name") + "=" + cookie.optString("value") + "; Domain=." + domain + "; Path=/; Secure";
                manager.setCookie("https://" + domain, value);
            }
        }
        manager.flush();
    }

    private static Bitmap qr(String value, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            bitmap.setPixel(x, y, matrix.get(x, y) ? 0xff101418 : 0xffffffff);
        return bitmap;
    }

    private static String md5(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
