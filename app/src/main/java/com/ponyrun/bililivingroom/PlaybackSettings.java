package com.ponyrun.bililivingroom;

import android.content.Context;
import android.content.SharedPreferences;

/** 播放器默认行为设置（弹幕/自动播放/优先高清），包内共享。 */
final class PlaybackSettings {
    private static final String PREFS_NAME = "playback_settings";
    private static final String KEY_DANMAKU = "danmaku";
    private static final String KEY_AUTOPLAY = "autoplay";
    private static final String KEY_HIGH_QUALITY = "high_quality";

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

    void setDanmaku(boolean value) {
        preferences.edit().putBoolean(KEY_DANMAKU, value).apply();
    }

    void setAutoPlay(boolean value) {
        preferences.edit().putBoolean(KEY_AUTOPLAY, value).apply();
    }

    void setHighQuality(boolean value) {
        preferences.edit().putBoolean(KEY_HIGH_QUALITY, value).apply();
    }
}
