package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 少量尚未原生化的图文/活动链接使用的 TV 兜底浏览器。 */
public final class BrowserActivity extends Activity {
    private WebView webView;
    private TextView titleView;
    private TextView backButton;
    private TextView refreshButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        configureWebView();
        getWindow().getDecorView().setSystemUiVisibility(5894);
        String url = getIntent().getStringExtra(PlayerActivity.EXTRA_URL);
        webView.loadUrl(url == null ? "https://www.bilibili.com/" : url);
        webView.requestFocus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff6f8fa);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding((int) dp(28), (int) dp(10), (int) dp(28), (int) dp(10));
        toolbar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xffdff7fc, 0xfff6f8fa, 0xffffe7ef}));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(78)));

        backButton = toolbarButton("‹  返回");
        backButton.setOnClickListener(v -> goBackOrFinish());
        toolbar.addView(backButton, new LinearLayout.LayoutParams((int) dp(150), ViewGroup.LayoutParams.MATCH_PARENT));

        titleView = new TextView(this);
        titleView.setText("正在打开页面…");
        titleView.setTextColor(0xff13212b);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(21));
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding((int) dp(24), 0, (int) dp(24), 0);
        toolbar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView hint = new TextView(this);
        hint.setText("MENU 聚焦工具栏");
        hint.setTextColor(0xff60717c);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(14));
        hint.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        toolbar.addView(hint, new LinearLayout.LayoutParams((int) dp(190), ViewGroup.LayoutParams.MATCH_PARENT));

        refreshButton = toolbarButton("刷新");
        refreshButton.setOnClickListener(v -> webView.reload());
        toolbar.addView(refreshButton, new LinearLayout.LayoutParams((int) dp(126), ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(0xfff6f8fa);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private TextView toolbarButton(String value) {
        TextView button = new TextView(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(18));
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setClickable(true);
        button.setBackground(toolbarButtonBackground());
        return button;
    }

    private StateListDrawable toolbarButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rounded(0xff16202a, 18, 0xfffb5c91, 3));
        states.addState(new int[]{}, rounded(0xfffb5c91, 18, 0xffffa8c3, 1));
        return states;
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, float strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke((int) dp(strokeWidth), strokeColor);
        return drawable;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(125);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36 BiliLivingRoom/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String pageTitle = view.getTitle();
                titleView.setText(pageTitle == null || pageTitle.trim().isEmpty() ? "哔哩哔哩页面" : pageTitle);
                view.evaluateJavascript("(function(){var s=document.createElement('style');" +
                        "s.innerHTML='*:focus{outline:4px solid #FB5C91!important;outline-offset:3px!important}' +" +
                        "'body{font-size:20px!important;background:#F6F8FA!important}' +" +
                        "'a,button,[role=button]{min-height:38px}';document.head.appendChild(s);})()", null);
            }
        });
    }

    private void goBackOrFinish() {
        if (webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            backButton.requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && (backButton.hasFocus() || refreshButton.hasFocus())) {
            webView.requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goBackOrFinish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
