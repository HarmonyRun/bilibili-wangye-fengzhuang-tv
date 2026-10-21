package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 原生图文动态详情：作者/正文/最多 9 张图片，Pearl Glass 大屏布局。 */
public final class DynamicDetailActivity extends Activity {
    static final String EXTRA_DYNAMIC_ID = "dynamic_id";

    private final BiliRepository repository = new BiliRepository();
    private boolean destroyed;
    private String dynamicId = "";
    private float scale;
    private int imageWidth;

    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout authorRow;
    private ImageView avatarView;
    private TextView nameView;
    private TextView timeView;
    private TextView typeView;
    private TextView bodyView;
    private TextView originalView;
    private LinearLayout imageContainer;
    private TextView statusView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = Math.min(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f);
        imageWidth = Math.max(1, (int) ((metrics.widthPixels - dp(120) - dp(40)) / 3));
        dynamicId = getIntent().getStringExtra(EXTRA_DYNAMIC_ID);
        if (dynamicId == null) dynamicId = "";
        buildUi();
        loadDetail();
    }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(pearlBackground());
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.requestFocus();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(60), (int) dp(46), (int) dp(60), (int) dp(80));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        authorRow = new LinearLayout(this);
        authorRow.setOrientation(LinearLayout.HORIZONTAL);
        authorRow.setGravity(Gravity.CENTER_VERTICAL);
        authorRow.setPadding((int) dp(24), (int) dp(22), (int) dp(24), (int) dp(22));
        authorRow.setBackground(surface(0xf0ffffff, 24, 0x1813212b, 1));
        authorRow.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        authorParams.bottomMargin = (int) dp(18);
        content.addView(authorRow, authorParams);

        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xff202a33);
        avatarView.setBackground(oval);
        authorRow.addView(avatarView, new LinearLayout.LayoutParams((int) dp(96), (int) dp(96)));

        LinearLayout authorInfo = new LinearLayout(this);
        authorInfo.setOrientation(LinearLayout.VERTICAL);
        authorInfo.setPadding((int) dp(24), 0, 0, 0);
        authorRow.addView(authorInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameView = text("", 26, 0xff13212b, true);
        authorInfo.addView(nameView);
        timeView = text("", 16, 0xff60717c, false);
        timeView.setPadding(0, (int) dp(6), 0, 0);
        authorInfo.addView(timeView);

        typeView = text("动态", 15, 0xfffb5c91, true);
        typeView.setGravity(Gravity.CENTER);
        typeView.setPadding((int) dp(18), (int) dp(8), (int) dp(18), (int) dp(8));
        typeView.setBackground(surface(0xffffe5ee, 18, 0xffffa8c4, 1));
        typeView.setVisibility(View.GONE);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams((int) dp(132), ViewGroup.LayoutParams.WRAP_CONTENT);
        typeParams.bottomMargin = (int) dp(14);
        content.addView(typeView, typeParams);

        bodyView = text("", 22, 0xff13212b, false);
        bodyView.setLineSpacing(dp(8), 1f);
        bodyView.setPadding((int) dp(28), (int) dp(26), (int) dp(28), (int) dp(26));
        bodyView.setBackground(surface(0xeaffffff, 24, 0x1813212b, 1));
        bodyView.setVisibility(View.GONE);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.bottomMargin = (int) dp(20);
        content.addView(bodyView, bodyParams);

        originalView = text("", 18, 0xff52656d, false);
        originalView.setLineSpacing(dp(6), 1f);
        originalView.setPadding((int) dp(28), (int) dp(22), (int) dp(28), (int) dp(22));
        originalView.setBackground(surface(0xd9fff2f7, 20, 0x30fb5c91, 1));
        originalView.setVisibility(View.GONE);
        LinearLayout.LayoutParams originalParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        originalParams.bottomMargin = (int) dp(20);
        content.addView(originalView, originalParams);

        imageContainer = new LinearLayout(this);
        imageContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(imageContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = text("正在加载动态…", 20, 0xff60717c, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding((int) dp(40), (int) dp(34), (int) dp(40), (int) dp(34));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(230));
        statusParams.setMargins((int) dp(320), (int) dp(180), (int) dp(320), 0);
        content.addView(statusView, statusParams);
        setStatusState("正在加载动态…", "请稍候，正在整理正文与图片", false, false);
        statusView.setOnClickListener(v -> loadDetail());

        setContentView(scroll);
    }

    private void loadDetail() {
        if (dynamicId.isEmpty()) {
            setStatusState("无效的动态 ID", "请返回上一页后重新进入", false, true);
            return;
        }
        authorRow.setVisibility(View.INVISIBLE);
        typeView.setVisibility(View.GONE);
        bodyView.setVisibility(View.GONE);
        originalView.setVisibility(View.GONE);
        imageContainer.removeAllViews();
        setStatusState("正在加载动态…", "请稍候，正在整理正文与图片", false, false);
        repository.loadDynamicDetail(dynamicId, (detail, error) -> {
            if (destroyed) return;
            if (detail == null) {
                showStatus(error == null || error.isEmpty() ? "动态加载失败" : error);
                return;
            }
            statusView.setVisibility(View.GONE);
            scroll.requestFocus();
            authorRow.setVisibility(View.VISIBLE);
            nameView.setText(detail.authorName == null || detail.authorName.isEmpty() ? "B 站用户" : detail.authorName);
            timeView.setText(detail.pubTime == null ? "" : detail.pubTime);
            typeView.setText(detail.kind == null || detail.kind.isEmpty() ? "动态" : detail.kind);
            typeView.setVisibility(View.VISIBLE);
            bodyView.setText(detail.text == null ? "" : detail.text);
            bodyView.setVisibility(detail.text == null || detail.text.trim().isEmpty() ? View.GONE : View.VISIBLE);
            if ((detail.originalAuthor != null && !detail.originalAuthor.isEmpty())
                    || (detail.originalText != null && !detail.originalText.isEmpty())) {
                String original = "原动态";
                if (detail.originalAuthor != null && !detail.originalAuthor.isEmpty()) original += " · @" + detail.originalAuthor;
                if (detail.originalText != null && !detail.originalText.isEmpty()) original += "\n" + detail.originalText;
                originalView.setText(original);
                originalView.setVisibility(View.VISIBLE);
            } else {
                originalView.setVisibility(View.GONE);
            }
            if (detail.authorFace != null && !detail.authorFace.isEmpty()) {
                repository.loadImage(detail.authorFace, bitmap -> {
                    if (!destroyed && bitmap != null) avatarView.setImageBitmap(bitmap);
                });
            }
            buildImages(detail.imageUrls);
            if ((detail.text == null || detail.text.trim().isEmpty()) && (detail.imageUrls == null || detail.imageUrls.isEmpty())) {
                setStatusState("这条动态没有可显示的正文或图片", "发布者暂未提供可展示内容", false, false);
            }
        });
    }

    private void buildImages(List<String> urls) {
        imageContainer.removeAllViews();
        if (urls == null || urls.isEmpty()) return;
        int max = Math.min(9, urls.size());
        LinearLayout row = null;
        for (int i = 0; i < max; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                imageContainer.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xffdce5ea);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(imageWidth, (int) dp(220));
            params.setMargins(0, 0, (int) dp(10), (int) dp(10));
            if (row != null) row.addView(image, params);
            final String url = urls.get(i);
            if (url != null && !url.isEmpty()) {
                repository.loadImage(url, bitmap -> {
                    if (!destroyed && bitmap != null) image.setImageBitmap(bitmap);
                });
            }
        }
    }

    private void showStatus(String message) {
        setStatusState(message, "检查网络后按 OK 重新加载", true, true);
    }

    private void setStatusState(String title, String hint, boolean actionable, boolean error) {
        statusView.setText(title + (hint == null || hint.isEmpty() ? "" : "\n" + hint));
        statusView.setTextColor(error ? 0xff8a3b55 : 0xff52656d);
        statusView.setLineSpacing(dp(12), 1f);
        statusView.setFocusable(actionable);
        statusView.setClickable(actionable);
        statusView.setBackground(statusBackground(actionable));
        statusView.setVisibility(View.VISIBLE);
        if (actionable) statusView.requestFocus();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable pearlBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xffdff7fc, 0xfff6f8fa, 0xffffe7ef});
    }

    private GradientDrawable surface(int color, float radius, int strokeColor, float strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke((int) dp(strokeWidth), strokeColor);
        return drawable;
    }

    private StateListDrawable statusBackground(boolean actionable) {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = surface(0xffffffff, 26, 0xfffb5c91, 5);
        GradientDrawable normal = surface(0xeaffffff, 26, actionable ? 0x55fb5c91 : 0x1813212b, actionable ? 2 : 1);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, normal);
        return drawable;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        repository.shutdown();
        super.onDestroy();
    }

    private float dp(float value) {
        return value * scale;
    }
}
