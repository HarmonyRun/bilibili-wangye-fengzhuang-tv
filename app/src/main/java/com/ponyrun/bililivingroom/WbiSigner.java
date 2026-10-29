package com.ponyrun.bililivingroom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * B 站 WBI 签名（wts + w_rid）：按 key 排序、过滤 !'()* 后百分号编码，md5(query + mixinKey)。
 * 纯 Java 实现，无 Android 依赖，便于复用与单测。
 */
final class WbiSigner {
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private WbiSigner() { }

    /** 由 imgKey + subKey 按固定置换表取出 32 位 mixinKey。 */
    static String mixinKey(String imgKey, String subKey) {
        String raw = (imgKey == null ? "" : imgKey) + (subKey == null ? "" : subKey);
        StringBuilder mixin = new StringBuilder(32);
        for (int index : MIXIN_KEY_ENC_TAB) {
            if (index < raw.length()) mixin.append(raw.charAt(index));
        }
        int length = Math.min(32, mixin.length());
        return mixin.substring(0, length);
    }

    /**
     * 对参数签名，返回可直接拼到 URL 后的 query（已按 key 排序、百分号编码，含 wts 与 w_rid）。
     * 值为空或 null 的参数会被跳过。
     */
    static String sign(Map<String, String> params, String imgKey, String subKey) {
        long wts = System.currentTimeMillis() / 1000L;
        List<String> keys = new ArrayList<>(params.size() + 1);
        for (String key : params.keySet()) {
            if (!"wts".equals(key) && !"w_rid".equals(key)) keys.add(key);
        }
        keys.add("wts");
        Collections.sort(keys);
        StringBuilder query = new StringBuilder();
        for (String key : keys) {
            String value = "wts".equals(key) ? String.valueOf(wts) : params.get(key);
            if (value == null || value.isEmpty()) continue;
            value = stripSpecialChars(value);
            if (query.length() > 0) query.append('&');
            query.append(encode(key)).append('=').append(encode(value));
        }
        String wRid = md5(query.toString() + mixinKey(imgKey, subKey));
        return query + "&w_rid=" + wRid;
    }

    private static String stripSpecialChars(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '!' || c == '\'' || c == '(' || c == ')' || c == '*') continue;
            out.append(c);
        }
        return out.toString();
    }

    /** RFC3986 百分号编码（空格为 %20，保留 -_.~ 与字母数字）。 */
    private static String encode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return out.toString();
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception error) {
            return "";
        }
    }
}
