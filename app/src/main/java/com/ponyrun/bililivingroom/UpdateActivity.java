package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/** 原生软件更新页：检查 GitHub Release、显示说明、下载 APK 并拉起系统安装器。 */
public final class UpdateActivity extends Activity {
    private UpdateManager updateManager;
    private UpdateManager.UpdateInfo updateInfo;
    private File downloadedApk;
    private boolean checking;
    private boolean downloading;
    private boolean destroyed;
    private float scale;

    private TextView statusView;
    private TextView versionView;
    private TextView notesView;
    private TextView sizeView;
    private TextView progressView;
    private ProgressBar progressBar;
    private Button actionButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = Math.min(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f);
        updateManager = new UpdateManager(this);
        buildUi();
        checkForUpdate();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.app_window_background);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(88), (int) dp(54), (int) dp(88), (int) dp(70));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("软件更新", 42, 0xff13212b, true);
        content.addView(title);
        TextView current = text("当前版本  " + BuildConfig.VERSION_NAME, 18, 0xff60717c, false);
        current.setPadding(0, (int) dp(10), 0, (int) dp(26));
        content.addView(current);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xf2ffffff);
        cardBg.setCornerRadius(dp(26));
        cardBg.setStroke((int) dp(1), 0x2213212b);
        card.setBackground(cardBg);
        card.setPadding((int) dp(38), (int) dp(30), (int) dp(38), (int) dp(30));
        content.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = text("正在检查更新…", 27, 0xff13212b, true);
        card.addView(statusView);
        versionView = text("", 20, 0xff3f525d, false);
        versionView.setPadding(0, (int) dp(12), 0, (int) dp(4));
        card.addView(versionView);

        notesView = text("", 17, 0xff4c616c, false);
        notesView.setMaxLines(8);
        notesView.setEllipsize(TextUtils.TruncateAt.END);
        notesView.setLineSpacing(dp(6), 1f);
        notesView.setPadding(0, (int) dp(10), 0, (int) dp(8));
        card.addView(notesView);

        sizeView = text("", 16, 0xff60717c, false);
        card.addView(sizeView);

        progressView = text("", 18, 0xfffb5c91, true);
        progressView.setPadding(0, (int) dp(14), 0, (int) dp(6));
        progressView.setVisibility(View.GONE);
        card.addView(progressView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(0xfffb5c91));
        progressBar.setVisibility(View.GONE);
        card.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(12)));

        actionButton = new Button(this);
        actionButton.setText("检查中…");
        actionButton.setFocusable(true);
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(22));
        actionButton.setBackgroundTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{0xffe94f86, 0xfffb5c91}));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams((int) dp(320), (int) dp(72));
        actionParams.topMargin = (int) dp(28);
        content.addView(actionButton, actionParams);

        TextView hint = text("更新来自 GitHub  Release：HarmonyRun/bilibili-wangye-fengzhuang-tv\n"
                + "下载完成后会拉起系统安装器；如系统提示未知来源，请先允许本应用安装应用。",
                15, 0xff71838d, false);
        hint.setLineSpacing(dp(6), 1f);
        hint.setPadding(0, (int) dp(24), 0, 0);
        content.addView(hint);

        actionButton.setOnClickListener(v -> onAction());
        setContentView(scroll);
        actionButton.requestFocus();
    }

    private void checkForUpdate() {
        if (checking || downloading) return;
        checking = true;
        updateInfo = null;
        downloadedApk = null;
        setProgressVisible(false);
        statusView.setText("正在检查更新…");
        versionView.setText("");
        notesView.setText("");
        sizeView.setText("");
        actionButton.setText("检查中…");
        updateManager.check((info, error) -> {
            if (destroyed) return;
            checking = false;
            if (error != null && !error.isEmpty()) {
                statusView.setText("检查更新失败");
                versionView.setText(error);
                actionButton.setText("重新检查");
                return;
            }
            if (info == null) {
                statusView.setText("检查更新失败");
                versionView.setText("没有获取到版本信息");
                actionButton.setText("重新检查");
                return;
            }
            if (!info.hasUpdate) {
                statusView.setText("已是最新版本");
                versionView.setText("当前版本 " + BuildConfig.VERSION_NAME + " 已是 GitHub 上的最新版本");
                actionButton.setText("重新检查");
                return;
            }
            updateInfo = info;
            statusView.setText("发现新版本  " + (info.version == null || info.version.isEmpty() ? info.tag : info.version));
            StringBuilder detail = new StringBuilder();
            if (info.tag != null && !info.tag.isEmpty()) detail.append("版本：").append(info.tag).append('\n');
            versionView.setText(detail.toString().trim());
            notesView.setText(info.notes == null || info.notes.trim().isEmpty() ? "该版本没有提供更新说明。" : info.notes.trim());
            sizeView.setText("安装包大小：" + formatSize(info.size));
            actionButton.setText(UpdateManager.canInstall(UpdateActivity.this) ? "下载并安装" : "去授权并安装");
        });
    }

    private void onAction() {
        if (checking || downloading) return;
        if (updateInfo == null) {
            checkForUpdate();
            return;
        }
        if (downloadedApk != null && downloadedApk.isFile() && downloadedApk.length() > 0) {
            installDownloaded();
            return;
        }
        if (!UpdateManager.canInstall(this)) {
            statusView.setText("需要允许安装未知来源应用");
            versionView.setText("请在系统设置中允许“哔哩客厅”安装应用，返回后再次选择下载并安装");
            UpdateManager.openUnknownSourcesSettings(this);
            actionButton.setText("去授权并安装");
            return;
        }
        startDownload();
    }

    private void startDownload() {
        if (updateInfo == null || downloading) return;
        downloading = true;
        setProgressVisible(true);
        progressBar.setProgress(0);
        progressView.setText("准备下载…");
        actionButton.setText("下载中…");
        updateManager.download(updateInfo, new UpdateManager.DownloadCallback() {
            @Override public void onProgress(int percent) {
                if (destroyed) return;
                int value = Math.max(0, Math.min(100, percent));
                progressBar.setProgress(value);
                progressView.setText("下载中  " + value + "%");
            }

            @Override public void onResult(File file, String error) {
                if (destroyed) return;
                downloading = false;
                setProgressVisible(false);
                if (error != null && !error.isEmpty()) {
                    statusView.setText("下载失败");
                    versionView.setText(error);
                    actionButton.setText("重试下载");
                    return;
                }
                if (file == null || !file.isFile() || file.length() <= 0) {
                    statusView.setText("下载失败");
                    versionView.setText("安装包文件无效，请稍后重试");
                    actionButton.setText("重试下载");
                    return;
                }
                downloadedApk = file;
                statusView.setText("下载完成");
                versionView.setText("正在启动系统安装器…");
                progressView.setText("100%");
                installDownloaded();
            }
        });
    }

    private void installDownloaded() {
        if (downloadedApk == null || !downloadedApk.isFile()) return;
        if (!UpdateManager.canInstall(this)) {
            statusView.setText("需要允许安装未知来源应用");
            versionView.setText("请在系统设置中允许安装应用后重试");
            UpdateManager.openUnknownSourcesSettings(this);
            return;
        }
        try {
            updateManager.install(this, downloadedApk);
            actionButton.setText("重新安装");
        } catch (Exception error) {
            statusView.setText("安装器启动失败");
            versionView.setText(error.getMessage() == null ? "请稍后重试" : error.getMessage());
            actionButton.setText("重新安装");
        }
    }

    private void setProgressVisible(boolean visible) {
        progressView.setVisibility(visible ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override protected void onResume() {
        super.onResume();
        if (updateInfo != null && !downloading) {
            actionButton.setText(downloadedApk != null ? "重新安装"
                    : UpdateManager.canInstall(this) ? "下载并安装" : "去授权并安装");
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (updateManager != null) updateManager.shutdown();
        super.onDestroy();
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "未知";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1) return String.format(Locale.CHINA, "%.1f MB", mb);
        return String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private float dp(float value) {
        return value * scale;
    }
}
