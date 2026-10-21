package com.ponyrun.bililivingroom;

import android.content.Context;
import android.content.SharedPreferences;

/** 播放器默认行为设置（弹幕/自动播放/画质/弹幕外观），包内共享。 */
final class PlaybackSettings {
    private static final String PREFS_NAME = "playback_settings";
    private static final String KEY_DANMAKU = "danmaku";
    private static final String KEY_AUTOPLAY = "autoplay";
    private static final String KEY_HIGH_QUALITY = "high_quality";
    private static final String KEY_DANMAKU_SIZE = "danmaku_size";
    private static final String KEY_DANMAKU_OPACITY = "danmaku_opacity";
    private static final String KEY_DANMAKU_SPEED = "danmaku_speed";

    private final SharedPreferences preferences;

    PlaybackSettings(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean danmaku() {
        return preferences.getBoolean(KEY_DANMAKU, true);
    }

    boolean autoPlay() {
        return preferences.getBoolean(KEY_AUTOPLAY, true);
    }

    boolean highQuality() {
        return preferences.getBoolean(KEY_HIGH_QUALITY, true);
    }

    int danmakuSize() {
        return clampOption(preferences.getInt(KEY_DANMAKU_SIZE, 1));
    }

    int danmakuOpacity() {
        return clampOption(preferences.getInt(KEY_DANMAKU_OPACITY, 1));
    }

    int danmakuSpeed() {
        return clampOption(preferences.getInt(KEY_DANMAKU_SPEED, 1));
    }

    void setDanmaku(boolean value) {
        preferences.edit().putBoolean(KEY_DANMAKU, value).apply();
    }

    void setAutoPlay(boolean value) {
        preferences.edit().putBoolean(KEY_AUTOPLAY, value).apply();
    }

    void setHighQuality(boolean value) {
        preferences.edit().putBoolean(KEY_HIGH_QUALITY, value).apply();
    }

    void setDanmakuSize(int value) {
        preferences.edit().putInt(KEY_DANMAKU_SIZE, clampOption(value)).apply();
    }

    void setDanmakuOpacity(int value) {
        preferences.edit().putInt(KEY_DANMAKU_OPACITY, clampOption(value)).apply();
    }

    void setDanmakuSpeed(int value) {
        preferences.edit().putInt(KEY_DANMAKU_SPEED, clampOption(value)).apply();
    }

    private static int clampOption(int value) {
        return Math.max(0, Math.min(2, value));
    }
}
