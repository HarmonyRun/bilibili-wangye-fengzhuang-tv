package com.ponyrun.bililivingroom;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 播放器本地续播进度：按 epId / cid / url 保存最近播放位置。
 * 只在主线程调用（SharedPreferences 读写 + apply 异步落盘）。
 */
final class PlaybackResume {
    private static final String PREFS_NAME = "playback_resume";
    private static final String KEY_PREFIX_EP = "ep:";
    private static final String KEY_PREFIX_CID = "cid:";
    private static final String KEY_PREFIX_URL = "url:";

    private final SharedPreferences preferences;

    PlaybackResume(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    int position(String key) {
        if (key == null || key.isEmpty()) return 0;
        return Math.max(0, preferences.getInt(key, 0));
    }

    void save(String key, int positionMs) {
        if (key == null || key.isEmpty() || positionMs <= 0) return;
        preferences.edit().putInt(key, positionMs).apply();
    }

    void clear(String key) {
        if (key == null || key.isEmpty()) return;
        preferences.edit().remove(key).apply();
    }

    static String epKey(long epId) {
        return KEY_PREFIX_EP + epId;
    }

    static String cidKey(long cid) {
        return KEY_PREFIX_CID + cid;
    }

    static String urlKey(String url) {
        return KEY_PREFIX_URL + (url == null ? "" : url);
    }
}
