package com.ponyrun.bililivingroom;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PlaybackRepository {
    /** 渐进式 MP4（durl）流，Media3 侧用 ProgressiveMediaSource 播放。 */
    static final int KIND_PROGRESSIVE = 0;
    /** B 站 DASH（fMP4 分片）：视频/音频分轨，Media3 侧用 MergingMediaSource 播放。 */
    static final int KIND_DASH = 1;
    /** HLS（直播 m3u8）流，Media3 侧用 HlsMediaSource 播放。 */
    static final int KIND_HLS = 2;

    /** 番剧选集单次返回上限。 */
    private static final int MAX_EPISODES = 200;
    /** 普通视频分P单次返回上限。 */
    private static final int MAX_PARTS = 100;
    /** 播放进度上报日志 tag（便于本次验证，debug 日志可保留）。 */
    private static final String PROGRESS_TAG = "BiliProgress";

    static final class Quality {
        final int id;
        final String name;
        Quality(int id, String name) { this.id = id; this.name = name; }
    }

    static final class Stream {
        /** 流类型，取值 {@link PlaybackRepository#KIND_PROGRESSIVE} / {@link PlaybackRepository#KIND_DASH} / {@link PlaybackRepository#KIND_HLS}。 */
        final int kind;
        /** PROGRESSIVE/HLS 主地址；DASH 时为 ""。 */
        final String url;
        /** PROGRESSIVE/HLS 候选（含主 URL，去重且按优先级排列）；DASH 时为空列表。 */
        final List<String> candidates;
        /** DASH 视频候选（baseUrl + backupUrl，去重且按优先级排列）；非 DASH 时为空列表。 */
        final List<String> videoCandidates;
        /** DASH 音频候选（baseUrl + backupUrl，去重且按优先级排列）；非 DASH 时为空列表。 */
        final List<String> audioCandidates;
        final String title;
        final long cid;
        final List<Quality> qualities;
        final int currentQuality;
        /** 播放身份：普通视频/番剧对应视频的 aid；直播为 0。 */
        final long aid;
        /** 播放身份：bvid；直播或缺失时为 ""。 */
        final String bvid;
        /** 番剧/影视当前集 epId；非 PGC 或未知为 0。 */
        final long epId;
        /** 番剧/影视 season_id；非 PGC 或未知为 0。 */
        final long seasonId;
        /** 是否为番剧/影视（PGC）内容，用于心跳 type=4 与弹幕/历史逻辑。 */
        final boolean isPgc;

        /** 兼容 v1.5 渐进式调用方：等价于 KIND_PROGRESSIVE 的全参数构造。 */
        Stream(String url, List<String> candidates, String title, long cid, List<Quality> qualities, int currentQuality) {
            this(KIND_PROGRESSIVE, url, candidates, null, null, title, cid, qualities, currentQuality);
        }

        Stream(int kind, String url, List<String> candidates, List<String> videoCandidates,
               List<String> audioCandidates, String title, long cid, List<Quality> qualities,
               int currentQuality) {
            this(kind, url, candidates, videoCandidates, audioCandidates, title, cid, qualities,
                    currentQuality, 0, "", 0, 0, false);
        }

        Stream(int kind, String url, List<String> candidates, List<String> videoCandidates,
               List<String> audioCandidates, String title, long cid, List<Quality> qualities,
               int currentQuality, long aid, String bvid, long epId, long seasonId, boolean isPgc) {
            this.kind = kind;
            this.url = url == null ? "" : url;
            List<String> mainCandidates = immutableCopy(candidates);
            if (mainCandidates.isEmpty() && kind != KIND_DASH && !this.url.isEmpty()) {
                mainCandidates = Collections.singletonList(this.url);
            }
            this.candidates = mainCandidates;
            this.videoCandidates = immutableCopy(videoCandidates);
            this.audioCandidates = immutableCopy(audioCandidates);
            this.title = title;
            this.cid = cid;
            this.qualities = immutableCopy(qualities);
            this.currentQuality = currentQuality;
            this.aid = aid;
            this.bvid = bvid == null ? "" : bvid;
            this.epId = epId;
            this.seasonId = seasonId;
            this.isPgc = isPgc;
        }

        private static <T> List<T> immutableCopy(List<T> source) {
            return source == null || source.isEmpty()
                    ? Collections.<T>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(source));
        }
    }

    static final class Danmaku {
        final long timeMs;
        final String text;
        Danmaku(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }

    /** 番剧/影视的一集。index 为 API 的短集号（如 "1"），title 为 long_title 优先的展示标题。 */
    static final class Episode {
        final long epId;
        final String title;
        final String index;
        final long cid;
        /** 剧集封面（已规范为 https）；缺失时为空串。 */
        final String cover;
        Episode(long epId, String title, String index, long cid) {
            this(epId, title, index, cid, "");
        }
        Episode(long epId, String title, String index, long cid, String cover) {
            this.epId = epId;
            this.title = title;
            this.index = index;
            this.cid = cid;
            this.cover = cover == null ? "" : cover;
        }
    }

    /** 普通视频的一个分P。page 从 1 开始，对应 view.pages 的下标 page-1。 */
    static final class Part {
        final int page;
        final String title;
        final long cid;
        /** 分P封面（已规范为 https）；缺失时为空串。 */
        final String cover;
        Part(int page, String title, long cid) {
            this(page, title, cid, "");
        }
        Part(int page, String title, long cid, String cover) {
            this.page = page;
            this.title = title;
            this.cid = cid;
            this.cover = cover == null ? "" : cover;
        }
    }

    /** 可以直接展示给用户的解析失败原因。 */
    static final class PlaybackException extends Exception {
        PlaybackException(String message) { super(message); }
    }

    interface StreamCallback { void onResult(Stream stream, String error); }
    interface DanmakuCallback { void onResult(List<Danmaku> items); }
    interface EpisodesCallback { void onResult(List<Episode> episodes, long currentEpId, String error); }
    interface PartsCallback { void onResult(List<Part> parts, int currentPage, String error); }

    private interface StreamResolver { Stream resolve() throws Exception; }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    // 进度上报状态：同一 cid 同一秒不重复上报；换集/换视频时重置 start_ts 与最大值。
    private volatile long progressCid = -1;
    private volatile long lastReportedSecond = -1;
    private volatile long maxReportedSecond = 0;
    private volatile long progressStartTs = 0;

    /** 默认解析：普通视频/番剧优先 DASH，失败或没有分轨时自动回退渐进式 durl；直播为 HLS。 */
    void resolve(String pageUrl, int quality, StreamCallback callback) {
        executeResolve(() -> pageUrl.contains("live.bilibili.com/") ? resolveLive(pageUrl)
                : pageUrl.contains("/bangumi/play/") ? resolveBangumi(pageUrl, quality, true)
                : resolveVideo(pageUrl, quality, true, 1), callback);
    }

    /** 强制渐进式解析：供 Media3 无法播放 DASH 时做一次性回退；直播仍走 HLS。 */
    void resolveProgressive(String pageUrl, int quality, StreamCallback callback) {
        executeResolve(() -> pageUrl.contains("live.bilibili.com/") ? resolveLive(pageUrl)
                : pageUrl.contains("/bangumi/play/") ? resolveBangumi(pageUrl, quality, false)
                : resolveVideo(pageUrl, quality, false, 1), callback);
    }

    /**
     * 解析普通视频指定分P：与 resolve 相同的 DASH 优先/渐进回退逻辑，只是 cid 取 pages[page-1]。
     * page 越界时钳到 1..pages.size；番剧/直播地址仍按 resolve 的分派处理。
     */
    void resolvePart(String pageUrl, int page, int quality, StreamCallback callback) {
        final int requestedPage = Math.max(1, page);
        executeResolve(() -> pageUrl.contains("live.bilibili.com/") ? resolveLive(pageUrl)
                : pageUrl.contains("/bangumi/play/") ? resolveBangumi(pageUrl, quality, true)
                : resolveVideo(pageUrl, quality, true, requestedPage), callback);
    }

    /**
     * 读取番剧/影视 season 的全部剧集，供播放器选集使用。
     * pageUrl 支持 /bangumi/play/ep{id} 与 /bangumi/play/ss{id}；ep 链接时 currentEpId=该集，
     * ss 链接时 currentEpId=第一集。失败时回调空列表 + 可直接展示的错误文案。
     */
    void loadEpisodes(String pageUrl, EpisodesCallback callback) {
        if (closed) return;
        executor.execute(() -> {
            if (closed) return;
            List<Episode> result = new ArrayList<>();
            long currentEpId = 0;
            String error = null;
            try {
                long requestedEpId = extractNumber(pageUrl, "ep");
                long seasonId = extractNumber(pageUrl, "ss");
                if (requestedEpId <= 0 && seasonId <= 0) {
                    throw new PlaybackException("无法识别番剧集数，请返回后重试");
                }
                String identity = requestedEpId > 0 ? "ep_id=" + requestedEpId : "season_id=" + seasonId;
                JSONObject root = getJson("https://api.bilibili.com/pgc/view/web/season?" + identity);
                JSONObject season = root.optJSONObject("result");
                if (season == null) season = root.optJSONObject("data");
                if (season == null) throw new PlaybackException("暂时无法打开该番剧，请稍后重试");
                JSONArray episodes = season.optJSONArray("episodes");
                if (episodes == null || episodes.length() == 0) {
                    throw new PlaybackException("该番剧暂时没有可播放的剧集");
                }
                for (int i = 0; i < episodes.length() && result.size() < MAX_EPISODES; i++) {
                    JSONObject item = episodes.optJSONObject(i);
                    if (item == null) continue;
                    long epId = item.optLong("id");
                    if (epId <= 0) continue;
                    String shortTitle = item.optString("title");
                    String longTitle = item.optString("long_title");
                    String title = longTitle.isEmpty() ? shortTitle : longTitle;
                    result.add(new Episode(epId, title, shortTitle, item.optLong("cid"),
                            normalizeCover(item.optString("cover"))));
                }
                if (result.isEmpty()) throw new PlaybackException("该番剧暂时没有可播放的剧集");
                currentEpId = requestedEpId > 0 ? requestedEpId : result.get(0).epId;
            } catch (Exception failure) {
                result = new ArrayList<>();
                currentEpId = 0;
                error = failure instanceof PlaybackException && failure.getMessage() != null
                        ? failure.getMessage()
                        : "暂时无法打开番剧选集，请稍后重试";
            }
            final List<Episode> episodesResult = result;
            final long currentEpIdResult = currentEpId;
            final String errorResult = error;
            post(() -> callback.onResult(episodesResult, currentEpIdResult, errorResult));
        });
    }

    /** 按 epId 解析播放地址，等价于 resolve("https://www.bilibili.com/bangumi/play/ep"+epId, quality, cb)。 */
    void resolveEpisode(long epId, int quality, StreamCallback callback) {
        resolve("https://www.bilibili.com/bangumi/play/ep" + epId, quality, callback);
    }

    /**
     * 上报播放进度到 B 站历史：先走 /x/v2/history/report，失败或 code != 0 时回退 /x/player/heartbeat。
     * 未登录、或直播（HLS 且非 PGC）直接跳过；positionMs/durationMs 为毫秒，内部转秒并夹到 >= 0。
     * 同一 cid 同一秒不重复上报；换集/换视频时重置 start_ts 与最大进度。
     */
    void reportProgress(Stream stream, String pageUrl, long positionMs, long durationMs) {
        if (stream == null) return;
        if (!BiliAuthRepository.hasSession()) return;
        if (stream.kind == KIND_HLS && !stream.isPgc) return;
        final long cid = stream.cid;
        if (cid <= 0) return;
        final long positionSec = Math.max(0, positionMs / 1000);
        final long durationSec = Math.max(0, durationMs / 1000);
        if (cid != progressCid) {
            progressCid = cid;
            lastReportedSecond = -1;
            maxReportedSecond = 0;
            progressStartTs = System.currentTimeMillis() / 1000;
        }
        if (positionSec == lastReportedSecond) return;
        lastReportedSecond = positionSec;
        if (positionSec > maxReportedSecond) maxReportedSecond = positionSec;
        final long maxSec = maxReportedSecond;
        final long startTs = progressStartTs > 0 ? progressStartTs : System.currentTimeMillis() / 1000;
        final long aid = stream.aid;
        final String bvid = stream.bvid;
        final long epId = stream.epId;
        final long seasonId = stream.seasonId;
        final boolean pgc = stream.isPgc;
        final String referer = pageUrl == null || pageUrl.isEmpty() ? "https://www.bilibili.com/" : pageUrl;
        if (closed) return;
        executor.execute(() -> {
            if (closed) return;
            String csrf = csrfToken();
            if (csrf.isEmpty()) {
                Log.d(PROGRESS_TAG, "skip: no bili_jct, aid=" + aid + " cid=" + cid + " progress=" + positionSec);
                return;
            }
            Map<String, String> form = new HashMap<>();
            form.put("aid", String.valueOf(aid));
            form.put("cid", String.valueOf(cid));
            if (!bvid.isEmpty()) form.put("bvid", bvid);
            form.put("mid", BiliAuthRepository.userMid());
            form.put("csrf", csrf);
            form.put("played_time", String.valueOf(positionSec));
            form.put("realtime", String.valueOf(positionSec));
            form.put("start_ts", String.valueOf(startTs));
            form.put("type", pgc ? "4" : "3");
            form.put("sub_type", "0");
            form.put("play_type", "0");
            form.put("video_duration", String.valueOf(durationSec));
            form.put("last_play_progress_time", String.valueOf(positionSec));
            form.put("max_play_progress_time", String.valueOf(maxSec));
            if (pgc) {
                if (epId > 0) form.put("epid", String.valueOf(epId));
                if (seasonId > 0) form.put("sid", String.valueOf(seasonId));
            }
            // 实测：/x/v2/history/report 对普通视频和番剧均返回 code=0，作为主通道；
            // /x/player/heartbeat 在模拟器上 404，仅作为兜底保留。
            Map<String, String> legacyForm = new HashMap<>();
            legacyForm.put("aid", String.valueOf(aid));
            legacyForm.put("cid", String.valueOf(cid));
            legacyForm.put("progress", String.valueOf(positionSec));
            legacyForm.put("csrf", csrf);
            try {
                JSONObject legacy = postForm("https://api.bilibili.com/x/v2/history/report", legacyForm, referer);
                int code = legacy.optInt("code", -1);
                Log.d(PROGRESS_TAG, "report code=" + code + " msg=" + legacy.optString("message")
                        + " aid=" + aid + " cid=" + cid + " progress=" + positionSec);
                if (code == 0) return;
            } catch (Exception error) {
                Log.d(PROGRESS_TAG, "report failed: " + error + " aid=" + aid + " cid=" + cid);
            }
            try {
                JSONObject response = postForm("https://api.bilibili.com/x/player/heartbeat", form, referer);
                Log.d(PROGRESS_TAG, "heartbeat fallback code=" + response.optInt("code", -1)
                        + " aid=" + aid + " cid=" + cid + " progress=" + positionSec);
            } catch (Exception error) {
                Log.d(PROGRESS_TAG, "heartbeat fallback failed: " + error + " aid=" + aid + " cid=" + cid);
            }
        });
    }

    /**
     * 读取普通视频的分P列表（view.pages），供播放器分P菜单使用。
     * currentPage 取自 URL 的 ?p=/&p=，缺省 1，越界钳到 1；单P视频也返回 1 条。
     * 失败时回调空列表 + 可直接展示的错误文案。
     */
    void loadParts(String pageUrl, PartsCallback callback) {
        if (closed) return;
        executor.execute(() -> {
            if (closed) return;
            List<Part> result = new ArrayList<>();
            int currentPage = 1;
            String error = null;
            try {
                String bvid = extractBvid(pageUrl);
                long aid = extractAid(pageUrl);
                if (bvid.isEmpty() && aid <= 0) throw new PlaybackException("无法识别视频地址，请返回后重试");
                String identity = bvid.isEmpty() ? "aid=" + aid : "bvid=" + Uri.encode(bvid);
                JSONObject root;
                try {
                    root = getJson("https://api.bilibili.com/x/web-interface/view?" + identity);
                } catch (Exception failure) {
                    throw new PlaybackException("暂时无法取得分P信息，请稍后重试");
                }
                JSONObject data = root.optJSONObject("data");
                if (data == null) throw new PlaybackException("暂时无法取得分P信息，请稍后重试");
                JSONArray pages = data.optJSONArray("pages");
                if (pages == null || pages.length() == 0) throw new PlaybackException("该视频暂时没有可播放的分P");
                for (int i = 0; i < pages.length() && result.size() < MAX_PARTS; i++) {
                    JSONObject item = pages.optJSONObject(i);
                    if (item == null) continue;
                    String title = item.optString("part");
                    if (title.isEmpty()) title = "P" + (i + 1);
                    result.add(new Part(i + 1, title, item.optLong("cid"),
                            normalizeCover(item.optString("first_frame"))));
                }
                if (result.isEmpty()) throw new PlaybackException("该视频暂时没有可播放的分P");
                currentPage = extractPageParam(pageUrl);
                if (currentPage > result.size()) currentPage = 1;
            } catch (Exception failure) {
                result = new ArrayList<>();
                currentPage = 1;
                error = failure instanceof PlaybackException && failure.getMessage() != null
                        ? failure.getMessage()
                        : "暂时无法取得分P信息，请稍后重试";
            }
            final List<Part> partsResult = result;
            final int currentPageResult = currentPage;
            final String errorResult = error;
            post(() -> callback.onResult(partsResult, currentPageResult, errorResult));
        });
    }

    void loadDanmaku(long cid, DanmakuCallback callback) {
        if (cid <= 0) { callback.onResult(new ArrayList<>()); return; }
        if (closed) return;
        executor.execute(() -> {
            if (closed) return;
            List<Danmaku> result = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                connection = open("https://comment.bilibili.com/" + cid + ".xml");
                // comment.bilibili.com 固定返回 Content-Encoding: deflate（raw deflate），
                // Android HttpURLConnection 不会自动解压 deflate，必须手动处理，否则 XML 解析失败会静默丢光弹幕。
                try (InputStream input = decodeStream(connection, connection.getInputStream())) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(input, "UTF-8");
                    int type;
                    while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                        if (type == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                            String p = parser.getAttributeValue(null, "p");
                            String text = parser.nextText();
                            if (p == null || text == null || text.trim().isEmpty()) continue;
                            // 单条脏数据不应让整集弹幕消失。
                            try {
                                float seconds = Float.parseFloat(p.split(",")[0]);
                                result.add(new Danmaku((long) (seconds * 1000), text.trim()));
                            } catch (Exception ignoredItem) {
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            java.util.Collections.sort(result, (a, b) -> Long.compare(a.timeMs, b.timeMs));
            post(() -> callback.onResult(result));
        });
    }

    private static InputStream decodeStream(HttpURLConnection connection, InputStream raw) throws Exception {
        String encoding = connection.getContentEncoding();
        if (encoding == null) return raw;
        String value = encoding.toLowerCase(Locale.ROOT);
        if (value.contains("gzip")) return new java.util.zip.GZIPInputStream(raw);
        // B 站弹幕接口返回 raw deflate（无 zlib header），必须用 nowrap=true 的 Inflater。
        if (value.contains("deflate")) {
            return new java.util.zip.InflaterInputStream(raw, new java.util.zip.Inflater(true));
        }
        return raw;
    }

    /** 停止后台线程并丢弃尚未回到主线程的结果。 */
    void shutdown() {
        closed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private void post(Runnable action) {
        if (!closed) main.post(action);
    }

    private void executeResolve(StreamResolver resolver, StreamCallback callback) {
        if (closed) return;
        executor.execute(() -> {
            if (closed) return;
            try {
                Stream result = resolver.resolve();
                post(() -> callback.onResult(result, null));
            } catch (Exception error) {
                final String message = error instanceof PlaybackException && error.getMessage() != null
                        ? error.getMessage()
                        : "暂时无法取得播放地址，请稍后重试";
                post(() -> callback.onResult(null, message));
            }
        });
    }

    private Stream resolveVideo(String pageUrl, int requestedQuality, boolean allowDash, int requestedPage) throws Exception {
        String bvid = extractBvid(pageUrl);
        long aid = extractAid(pageUrl);
        if (bvid.isEmpty() && aid <= 0) throw new PlaybackException("无法识别视频地址，请返回后重试");
        String identity = bvid.isEmpty() ? "aid=" + aid : "bvid=" + Uri.encode(bvid);
        JSONObject view;
        try {
            view = getJson("https://api.bilibili.com/x/web-interface/view?" + identity).getJSONObject("data");
        } catch (Exception error) {
            throw new PlaybackException("暂时无法取得播放地址，请稍后重试");
        }
        JSONArray pages = view.optJSONArray("pages");
        if (pages == null || pages.length() == 0) throw new PlaybackException("暂时无法取得播放地址，请稍后重试");
        int page = Math.max(1, Math.min(requestedPage, pages.length()));
        JSONObject pageInfo = pages.optJSONObject(page - 1);
        if (pageInfo == null || pageInfo.optLong("cid") <= 0) {
            throw new PlaybackException("暂时无法取得播放地址，请稍后重试");
        }
        long cid = pageInfo.optLong("cid");
        String title = view.optString("title", "哔哩哔哩视频");
        int qn = requestedQuality > 0 ? requestedQuality : 80;
        String streamIdentity = bvid.isEmpty() ? "avid=" + aid : "bvid=" + Uri.encode(bvid);
        Stream result;
        if (allowDash) {
            Stream dash = tryResolveVideoDash(streamIdentity, cid, qn, title);
            if (dash != null && dash.currentQuality >= 64) {
                result = dash;
            } else {
                // 未登录时 DASH 常被限制到 480P，而 html5 渐进流可到 720P；取实际档位更高者。
                try {
                    Stream progressive = resolveVideoProgressive(streamIdentity, cid, title, qn);
                    if (dash == null) result = progressive;
                    else result = progressive.currentQuality > dash.currentQuality ? progressive : dash;
                } catch (Exception error) {
                    if (dash != null) result = dash;
                    else throw error;
                }
            }
        } else {
            result = resolveVideoProgressive(streamIdentity, cid, title, qn);
        }
        long viewAid = view.optLong("aid", aid);
        String viewBvid = view.optString("bvid", bvid);
        return withIdentity(result, viewAid > 0 ? viewAid : aid, viewBvid, 0, 0, false);
    }

    /** 复制一份 Stream 并填入播放身份字段（aid/bvid/epId/seasonId/isPgc），用于进度上报。 */
    private static Stream withIdentity(Stream base, long aid, String bvid, long epId, long seasonId, boolean isPgc) {
        return new Stream(base.kind, base.url, base.candidates, base.videoCandidates, base.audioCandidates,
                base.title, base.cid, base.qualities, base.currentQuality, aid, bvid, epId, seasonId, isPgc);
    }

    /** DASH 请求失败或响应中没有可用音视频分轨时返回 null，由调用方回退渐进式 durl。 */
    private Stream tryResolveVideoDash(String identity, long cid, int qn, String title) {
        try {
            String api = String.format(Locale.ROOT,
                    "https://api.bilibili.com/x/player/playurl?%s&cid=%d&qn=%d&fnval=16&fnver=0&fourk=1&platform=pc",
                    identity, cid, qn);
            JSONObject root = getJsonRoot(api);
            if (root.optInt("code", -1) != 0) return null;
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            return buildDashStream(data, title, cid, qn);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Stream resolveVideoProgressive(String identity, long cid, String title, int qn) throws Exception {
        String api = String.format(Locale.ROOT,
                "https://api.bilibili.com/x/player/playurl?%s&cid=%d&qn=%d&fnval=1&fnver=0&fourk=1&platform=html5&high_quality=1",
                identity, cid, qn);
        JSONObject data;
        try {
            data = getJson(api).getJSONObject("data");
        } catch (Exception error) {
            throw new PlaybackException("暂时无法取得播放地址，请稍后重试");
        }
        return buildProgressiveStream(data, title, cid, qn);
    }

    private Stream resolveBangumi(String pageUrl, int requestedQuality, boolean allowDash) throws Exception {
        long epId = extractNumber(pageUrl, "ep");
        long seasonId = extractNumber(pageUrl, "ss");
        if (epId <= 0 && seasonId <= 0) throw new PlaybackException("无法识别番剧集数，请返回后重试");
        String identity = epId > 0 ? "ep_id=" + epId : "season_id=" + seasonId;
        JSONObject root;
        try {
            root = getJson("https://api.bilibili.com/pgc/view/web/season?" + identity);
        } catch (Exception error) {
            throw new PlaybackException("暂时无法打开该番剧，请稍后重试");
        }
        JSONObject season = root.optJSONObject("result");
        if (season == null) season = root.optJSONObject("data");
        if (season == null) throw new PlaybackException("暂时无法打开该番剧，请稍后重试");
        JSONArray episodes = season.optJSONArray("episodes");
        if (episodes == null || episodes.length() == 0) throw new PlaybackException("该番剧暂时没有可播放的剧集");
        JSONObject episode = episodes.optJSONObject(0);
        if (epId > 0) {
            boolean found = false;
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject candidate = episodes.optJSONObject(i);
                if (candidate != null && candidate.optLong("id") == epId) {
                    episode = candidate;
                    found = true;
                    break;
                }
            }
            if (!found) throw new PlaybackException("找不到该集，可能已下架或需要从番剧页进入");
        }
        if (episode == null) throw new PlaybackException("该番剧暂时没有可播放的剧集");
        String bvid = episode.optString("bvid");
        long aid = episode.optLong("aid");
        long cid = episode.optLong("cid");
        if (cid <= 0 || (aid <= 0 && bvid.isEmpty())) {
            throw new PlaybackException("该集暂时没有可用的播放信息");
        }
        String epTitle = episode.optString("long_title", episode.optString("title"));
        String title = season.optString("title", "哔哩哔哩番剧") + (epTitle.isEmpty() ? "" : " · " + epTitle);
        long episodeId = episode.optLong("id");
        if (episodeId <= 0) episodeId = epId;
        long seasonIdValue = season.optLong("season_id");
        if (seasonIdValue <= 0) seasonIdValue = seasonId;
        return resolvePgcStream(epId, episodeId, seasonIdValue, aid, bvid, cid, title, requestedQuality, allowDash);
    }

    /**
     * PGC 必须走 pgc/player/web/playurl：实测 x/player/playurl 对番剧返回 -404，
     * 传 bvid 会返回 -400，传 ep_id / avid 才能拿到 durl。
     * 身份优先级：ep_id > avid > bvid。allowDash 时先尝试 DASH，失败/无分轨再回退 fnval=0。
     */
    private Stream resolvePgcStream(long urlEpId, long episodeId, long seasonId, long aid, String bvid,
                                    long cid, String title, int requestedQuality, boolean allowDash) throws Exception {
        int qn = requestedQuality > 0 ? requestedQuality : 80;
        String identity;
        if (urlEpId > 0) identity = "ep_id=" + urlEpId;
        else if (aid > 0) identity = "avid=" + aid;
        else identity = "bvid=" + Uri.encode(bvid);
        Stream result;
        if (allowDash) {
            Stream dash = tryResolvePgcDash(identity, cid, qn, title);
            if (dash != null && dash.currentQuality >= 64) {
                result = dash;
            } else {
                try {
                    Stream progressive = resolvePgcProgressive(identity, cid, qn, title);
                    if (dash == null) result = progressive;
                    else result = progressive.currentQuality > dash.currentQuality ? progressive : dash;
                } catch (Exception error) {
                    if (dash != null) result = dash;
                    else throw error;
                }
            }
        } else {
            result = resolvePgcProgressive(identity, cid, qn, title);
        }
        return withIdentity(result, aid, bvid, episodeId, seasonId, true);
    }

    /** DASH 请求失败或没有 dash 时返回 null，由 fnval=0 渐进路径保留原有业务码与错误文案。 */
    private Stream tryResolvePgcDash(String identity, long cid, int qn, String title) {
        try {
            String api = String.format(Locale.ROOT,
                    "https://api.bilibili.com/pgc/player/web/playurl?%s&cid=%d&qn=%d&fnval=16&fnver=0&fourk=1&platform=pc",
                    identity, cid, qn);
            JSONObject root = getJsonRoot(api);
            if (root.optInt("code", -1) != 0) return null;
            JSONObject data = root.optJSONObject("result");
            if (data == null) data = root.optJSONObject("data");
            if (data == null) return null;
            if (data.optInt("code", 0) != 0) return null;
            return buildDashStream(data, title, cid, qn);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Stream resolvePgcProgressive(String identity, long cid, int qn, String title) throws Exception {
        String api = String.format(Locale.ROOT,
                "https://api.bilibili.com/pgc/player/web/playurl?%s&cid=%d&qn=%d&fnval=0&fnver=0&fourk=1",
                identity, cid, qn);
        JSONObject root = getJsonRoot(api);
        int code = root.optInt("code", -1);
        if (code != 0) {
            throw new PlaybackException(pgcErrorMessage(code, root.optString("message")));
        }
        JSONObject data = root.optJSONObject("result");
        if (data == null) data = root.optJSONObject("data");
        if (data == null) throw new PlaybackException("暂时无法取得播放地址，请稍后重试");
        int innerCode = data.optInt("code", 0);
        if (innerCode != 0) {
            throw new PlaybackException(pgcErrorMessage(innerCode,
                    firstNonEmpty(data.optString("error_info"), data.optString("error_text"),
                            data.optString("message"))));
        }
        if (data.optJSONArray("durl") == null) {
            Stream dash = buildDashStream(data, title, cid, qn);
            if (dash != null) return dash;
            String info = firstNonEmpty(data.optString("error_info"), data.optString("error_text"),
                    data.optString("message"));
            if (!info.isEmpty()) throw new PlaybackException(info);
        }
        return buildProgressiveStream(data, title, cid, qn);
    }

    private Stream resolveLive(String pageUrl) throws Exception {
        String path = Uri.parse(pageUrl).getPath();
        String room = path == null ? "" : path.replaceAll("[^0-9]", "");
        if (room.isEmpty()) throw new PlaybackException("无法识别直播间，请返回后重试");
        JSONObject roomInfo;
        try {
            roomInfo = getJson("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + room)
                    .optJSONObject("data");
        } catch (Exception error) {
            roomInfo = null;
        }
        JSONObject data;
        try {
            data = getJson("https://api.live.bilibili.com/room/v1/Room/playUrl?cid=" + room
                    + "&quality=4&platform=h5").getJSONObject("data");
        } catch (Exception error) {
            throw new PlaybackException("直播间暂未开播或暂时无法播放");
        }
        List<String> candidates = collectCandidates(data);
        if (candidates.isEmpty()) throw new PlaybackException("直播间暂未开播或暂时无法播放");
        List<Quality> qualities = new ArrayList<>();
        qualities.add(new Quality(4, "高清"));
        return new Stream(KIND_HLS, candidates.get(0), candidates, null, null,
                roomInfo == null ? "哔哩哔哩直播" : roomInfo.optString("title", "哔哩哔哩直播"),
                0, qualities, 4);
    }

    private Stream buildProgressiveStream(JSONObject data, String title, long cid, int qn)
            throws PlaybackException {
        List<String> candidates = collectCandidates(data);
        if (candidates.isEmpty()) throw new PlaybackException("暂时无法取得播放地址，请稍后重试");
        List<Quality> qualities = buildQualities(data, null);
        return new Stream(KIND_PROGRESSIVE, candidates.get(0), candidates, null, null,
                displayTitle(title, data), cid, qualities, data.optInt("quality", qn));
    }

    /**
     * 解析 DASH 响应。返回 null 表示响应里没有可用的视频/音频分轨，调用方应回退渐进式 durl。
     * 选流规则：qn 精确匹配 → 小于等于 qn 的最大 id → 最大 id；同一 id 优先 AVC(codecid 7)，再按带宽取高。
     */
    private static Stream buildDashStream(JSONObject data, String title, long cid, int qn) {
        JSONObject dash = data.optJSONObject("dash");
        if (dash == null) return null;
        JSONArray videos = dash.optJSONArray("video");
        JSONArray audios = dash.optJSONArray("audio");
        if (videos == null || videos.length() == 0 || audios == null || audios.length() == 0) {
            return null;
        }
        JSONObject videoRep = pickVideoRep(videos, qn);
        if (videoRep == null) return null;
        List<String> videoCandidates = collectDashCandidates(videoRep);
        if (videoCandidates.isEmpty()) return null;
        JSONObject audioRep = pickAudioRep(audios);
        if (audioRep == null) return null;
        List<String> audioCandidates = collectDashCandidates(audioRep);
        if (audioCandidates.isEmpty()) return null;
        int actualQuality = videoRep.optInt("id", qn);
        List<Quality> qualities = buildQualities(data, videos);
        return new Stream(KIND_DASH, "", null, videoCandidates, audioCandidates,
                displayTitle(title, data), cid, qualities, actualQuality);
    }

    private static JSONObject pickVideoRep(JSONArray videos, int requestedQn) {
        int selectedId = -1;
        int bestBelow = -1;
        int bestAny = -1;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject rep = videos.optJSONObject(i);
            if (rep == null) continue;
            int id = rep.optInt("id", 0);
            if (id <= 0) continue;
            if (id > bestAny) bestAny = id;
            if (id <= requestedQn && id > bestBelow) bestBelow = id;
            if (id == requestedQn) selectedId = id;
        }
        if (selectedId < 0) selectedId = bestBelow;
        if (selectedId < 0) selectedId = bestAny;
        if (selectedId < 0) return null;
        JSONObject best = null;
        long bestBandwidth = -1;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject rep = videos.optJSONObject(i);
            if (rep == null) continue;
            if (rep.optInt("id", 0) != selectedId) continue;
            boolean avc = rep.optInt("codecid", 0) == 7;
            boolean bestIsAvc = best != null && best.optInt("codecid", 0) == 7;
            long bandwidth = rep.optLong("bandwidth", 0);
            if (best == null || (avc && !bestIsAvc) || (avc == bestIsAvc && bandwidth > bestBandwidth)) {
                best = rep;
                bestBandwidth = bandwidth;
            }
        }
        return best;
    }

    private static JSONObject pickAudioRep(JSONArray audios) {
        JSONObject best = null;
        long bestBandwidth = -1;
        for (int i = 0; i < audios.length(); i++) {
            JSONObject rep = audios.optJSONObject(i);
            if (rep == null) continue;
            long bandwidth = rep.optLong("bandwidth", 0);
            if (best == null || bandwidth > bestBandwidth) {
                best = rep;
                bestBandwidth = bandwidth;
            }
        }
        return best;
    }

    private static List<String> collectDashCandidates(JSONObject rep) {
        List<String> candidates = new ArrayList<>();
        if (rep == null) return candidates;
        addCandidate(candidates, firstNonEmpty(rep.optString("baseUrl"), rep.optString("base_url")));
        JSONArray backups = rep.optJSONArray("backupUrl");
        if (backups == null) backups = rep.optJSONArray("backup_url");
        if (backups != null) {
            for (int i = 0; i < backups.length(); i++) addCandidate(candidates, backups.optString(i));
        }
        return candidates;
    }

    private static List<Quality> buildQualities(JSONObject data, JSONArray videoReps) {
        List<Quality> qualities = new ArrayList<>();
        JSONArray ids = data.optJSONArray("accept_quality");
        JSONArray names = data.optJSONArray("accept_description");
        if (ids != null && ids.length() > 0) {
            for (int i = 0; i < ids.length(); i++) {
                int id = ids.optInt(i, 0);
                if (id <= 0) continue;
                String name = names == null ? "" : names.optString(i, "");
                qualities.add(new Quality(id, name.isEmpty() ? qualityName(id) : name));
            }
        } else if (videoReps != null) {
            List<Integer> repIds = new ArrayList<>();
            for (int i = 0; i < videoReps.length(); i++) {
                JSONObject rep = videoReps.optJSONObject(i);
                if (rep == null) continue;
                int id = rep.optInt("id", 0);
                if (id > 0 && !repIds.contains(id)) repIds.add(id);
            }
            Collections.sort(repIds, Collections.reverseOrder());
            for (Integer id : repIds) qualities.add(new Quality(id, qualityName(id)));
        }
        return qualities;
    }

    private static String qualityName(int id) {
        switch (id) {
            case 127: return "8K";
            case 126: return "杜比视界";
            case 125: return "HDR";
            case 120: return "4K";
            case 116: return "1080P60";
            case 112: return "1080P+";
            case 80: return "1080P";
            case 74: return "720P60";
            case 64: return "720P";
            case 32: return "480P";
            case 16: return "360P";
            case 6: return "240P";
            default: return String.valueOf(id);
        }
    }

    private static String displayTitle(String title, JSONObject data) {
        if (data.optInt("is_preview", 0) == 1 && !title.endsWith("（试看）")) {
            return title + "（试看）";
        }
        return title;
    }

    private static List<String> collectCandidates(JSONObject data) {
        List<String> candidates = new ArrayList<>();
        JSONArray durls = data.optJSONArray("durl");
        if (durls == null || durls.length() == 0) return candidates;
        JSONObject first = durls.optJSONObject(0);
        if (first == null) return candidates;
        addCandidate(candidates, first.optString("url"));
        JSONArray backups = first.optJSONArray("backup_url");
        if (backups != null) {
            for (int i = 0; i < backups.length(); i++) addCandidate(candidates, backups.optString(i));
        }
        return candidates;
    }

    private static void addCandidate(List<String> candidates, String url) {
        if (url == null || url.isEmpty()) return;
        if (!url.startsWith("http")) return;
        if (!candidates.contains(url)) candidates.add(url);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty() && !"0".equals(value)) return value;
        }
        return "";
    }

    private static String pgcErrorMessage(int code, String fallback) {
        switch (code) {
            case -10403: return "该内容在当前地区不可观看";
            case 87007: return "该内容为充电专属，暂无法播放";
            case 87008: return "该内容为大会员专享，请使用大会员账号登录后观看";
            case 87009: return "该内容需付费购买后观看";
            case 87012: return "该内容仅限特定地区观看";
            case -101: return "登录状态已失效，请重新登录";
            case -404: return "该内容不存在或已下架";
            default: {
                String detail = firstNonEmpty(fallback);
                return detail.isEmpty() ? "暂时无法取得播放地址，请稍后重试" : "暂时无法播放：" + detail;
            }
        }
    }

    private JSONObject getJson(String endpoint) throws Exception {
        JSONObject root = getJsonRoot(endpoint);
        if (root.optInt("code", 0) != 0) throw new IllegalStateException(root.optString("message"));
        return root;
    }

    /** 读取 JSON 但不在业务 code 非 0 时抛错，供需要区分错误码的 PGC 接口使用。 */
    private JSONObject getJsonRoot(String endpoint) throws Exception {
        HttpURLConnection connection = open(endpoint);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder value = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
            return new JSONObject(value.toString());
        } finally {
            connection.disconnect();
        }
    }

    private static String csrfToken() {
        String cookie = BiliAuthRepository.cookieHeader();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|;\\s*)bili_jct=([0-9A-Za-z]+)").matcher(cookie);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** POST application/x-www-form-urlencoded 并解析 JSON；供播放进度上报使用。 */
    private JSONObject postForm(String endpoint, Map<String, String> form, String referer) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(Uri.encode(entry.getKey())).append('=')
                    .append(Uri.encode(entry.getValue() == null ? "" : entry.getValue()));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("Origin", "https://www.bilibili.com");
        connection.setRequestProperty("Accept", "application/json");
        String cookie = BiliAuthRepository.cookieHeader();
        if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
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

    private HttpURLConnection open(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Safari/537.36");
        connection.setRequestProperty("Referer", "https://www.bilibili.com/");
        connection.setRequestProperty("Accept", "*/*");
        String cookie = BiliAuthRepository.cookieHeader();
        if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
        return connection;
    }

    /** 封面 URL 规范化：协议相对补 https，http 升 https；空值返回空串。 */
    private static String normalizeCover(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://")) return "https://" + url.substring(7);
        return url;
    }

    private static String extractBvid(String input) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(BV[0-9A-Za-z]{10})").matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 解析 URL 的 ?p=/&p=（从 1 开始，缺省 1）。 */
    private static int extractPageParam(String input) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[?&]p=(\\d+)").matcher(input == null ? "" : input);
        if (!matcher.find()) return 1;
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static long extractAid(String input) { return extractNumber(input, "av"); }

    private static long extractNumber(String input, String prefix) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|/|\\b)" + prefix + "([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        if (!matcher.find()) return 0;
        try { return Long.parseLong(matcher.group(1)); } catch (NumberFormatException ignored) { return 0; }
    }
}
