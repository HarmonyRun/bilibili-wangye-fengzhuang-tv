package com.ponyrun.bililivingroom;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地搜索历史：SharedPreferences 存储（换行分隔），最近在前，最多 8 条。
 * 约定只在主线程调用；写入使用 apply()，不阻塞 UI。
 */
final class SearchHistory {
    private static final String PREFS_NAME = "search_history";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String SEPARATOR = "\n";
    private static final int MAX_SIZE = 8;

    private final SharedPreferences preferences;

    SearchHistory(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 最近在前，最多 8 条。 */
    List<String> keywords() {
        String raw = preferences.getString(KEY_KEYWORDS, "");
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String value : raw.split(SEPARATOR)) {
            String keyword = value.trim();
            if (keyword.isEmpty() || result.contains(keyword)) continue;
            result.add(keyword);
            if (result.size() >= MAX_SIZE) break;
        }
        return result;
    }

    /** 去重、置顶、截断 8 条；空白关键词忽略。 */
    void add(String keyword) {
        String value = sanitize(keyword);
        if (value.isEmpty()) return;
        List<String> updated = new ArrayList<>();
        updated.add(value);
        for (String existing : keywords()) {
            if (updated.size() >= MAX_SIZE) break;
            if (existing.equals(value)) continue;
            updated.add(existing);
        }
        save(updated);
    }

    /** 清空搜索历史。 */
    void clear() {
        preferences.edit().remove(KEY_KEYWORDS).apply();
    }

    private static String sanitize(String keyword) {
        if (keyword == null) return "";
        return keyword.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void save(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) joined.append(SEPARATOR);
            joined.append(value);
        }
        preferences.edit().putString(KEY_KEYWORDS, joined.toString()).apply();
    }
}
