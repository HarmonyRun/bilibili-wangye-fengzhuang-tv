package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

public final class LoginActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BiliAuthRepository auth = new BiliAuthRepository();
    private LoginView loginView;
    private String authCode;
    private boolean active;
    private boolean completed;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active || authCode == null) return;
            auth.poll(authCode, (state, message) -> handler.post(() -> {
                if (!active) return;
                loginView.status = message;
                loginView.invalidate();
                if (state == BiliAuthRepository.SUCCESS) {
                    completed = true;
                    setResult(RESULT_OK);
                    handler.postDelayed(LoginActivity.this::finish, 700);
                } else if (state == BiliAuthRepository.EXPIRED) {
                    requestQr();
                } else handler.postDelayed(poll, 4000);
            }));
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        loginView = new LoginView();
        setContentView(loginView);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        requestQr();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        if (!completed && authCode != null) handler.post(poll);
    }

    @Override protected void onPause() { active = false; handler.removeCallbacks(poll); super.onPause(); }

    @Override protected void onDestroy() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        auth.shutdown();
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private void requestQr() {
        loginView.status = "正在生成二维码…";
        loginView.qr = null;
        loginView.invalidate();
        auth.requestQr((code, url, bitmap, error) -> handler.post(() -> {
            if (!active) return;
            authCode = code;
            loginView.qr = bitmap;
            loginView.status = error == null ? "请使用哔哩哔哩手机客户端扫码" : error;
            loginView.invalidate();
            if (error == null) handler.postDelayed(poll, 4000);
        }));
    }

    private final class LoginView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap qr;
        String status = "";
        LoginView() { super(LoginActivity.this); setFocusable(true); requestFocus(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Math.min(w / 1920f, h / 1080f);
            c.drawColor(0xff0a0f16);
            paint.setShader(new RadialGradient(w * .16f, h * .14f, w * .55f,
                    0x5540d8f4, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w * .88f, h * .86f, w * .58f,
                    0x66fb5c91, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            float panelLeft = w * .10f, panelTop = h * .11f, panelRight = w * .90f, panelBottom = h * .89f;
            paint.setColor(0xd9141b24);
            c.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom, 36*s, 36*s, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1*s); paint.setColor(0x35ffffff);
            c.drawRoundRect(panelLeft, panelTop, panelRight, panelBottom, 36*s, 36*s, paint);
            paint.setStyle(Paint.Style.FILL);

            text(c, "哔哩客厅", panelLeft + 54*s, panelTop + 67*s, 24*s, 0xfffb5c91, true);
            text(c, "用手机扫码登录", w * .56f, h * .30f, 44*s, Color.WHITE, true);
            text(c, "登录后同步动态、收藏、历史记录和追番", w * .56f, h * .36f, 19*s, 0xff9fadb7, false);
            if (qr != null) {
                float size = 420 * s;
                RectF dst = new RectF(w * .30f - size / 2, h * .53f - size / 2, w * .30f + size / 2, h * .53f + size / 2);
                paint.setColor(Color.WHITE); c.drawRoundRect(dst.left - 22*s, dst.top - 22*s, dst.right + 22*s, dst.bottom + 22*s, 28*s, 28*s, paint);
                c.drawBitmap(qr, null, dst, paint);
            } else {
                float size = 420 * s;
                RectF loading = new RectF(w * .30f - size / 2, h * .53f - size / 2, w * .30f + size / 2, h * .53f + size / 2);
                paint.setColor(0xff202a34); c.drawRoundRect(loading, 28*s, 28*s, paint);
                text(c, "二维码生成中", loading.centerX(), loading.centerY() + 8*s, 22*s, 0xffa9b6bf, true, Paint.Align.CENTER);
            }
            drawStep(c, "1", "打开哔哩哔哩手机客户端", w * .56f, h * .45f, s);
            drawStep(c, "2", "扫描左侧二维码并确认", w * .56f, h * .54f, s);
            text(c, status, w * .56f, h * .65f, 20*s, 0xfffb8bad, false);
            paint.setColor(0xfffb5c91);
            c.drawRoundRect(w*.56f, h*.71f, w*.78f, h*.80f, 28*s, 28*s, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*s); paint.setColor(Color.WHITE);
            c.drawRoundRect(w*.56f + 4*s, h*.71f + 4*s, w*.78f - 4*s, h*.80f - 4*s, 24*s, 24*s, paint); paint.setStyle(Paint.Style.FILL);
            text(c, "重新生成二维码", w*.67f, h*.768f, 21*s, Color.WHITE, true, Paint.Align.CENTER);
            text(c, "按返回键取消登录", w*.67f, h*.85f, 15*s, 0xff8f9ba4, false, Paint.Align.CENTER);
        }

        private void drawStep(Canvas c, String number, String value, float x, float y, float s) {
            paint.setColor(0xff26313c);
            c.drawCircle(x + 20*s, y - 8*s, 20*s, paint);
            text(c, number, x + 20*s, y, 17*s, Color.WHITE, true, Paint.Align.CENTER);
            text(c, value, x + 58*s, y, 23*s, 0xffe2e8ec, false);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
            text(c, value, x, y, size, color, bold, Paint.Align.LEFT);
        }
        private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setTextSize(size); paint.setColor(color); paint.setTextAlign(align);
            int style = bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL;
            paint.setTypeface(android.graphics.Typeface.create("sans", style)); c.drawText(value, x, y, paint);
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) { requestQr(); return true; }
            return super.onKeyDown(keyCode, event);
        }
    }
}
