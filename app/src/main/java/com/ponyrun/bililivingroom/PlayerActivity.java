package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_DANMAKU = "danmaku";
    static final String EXTRA_AUTOPLAY = "autoplay";
    static final String EXTRA_HIGH_QUALITY = "high_quality";
    private static final String STATE_POSITION = "player.position";
    private static final String STATE_QUALITY = "player.quality";
    private static final String STATE_DANMAKU = "player.danmaku";
    private static final String STATE_SPEED = "player.speed";
    private static final String STATE_EPISODE = "player.episode";
    private static final String STATE_PART = "player.part";
    private static final int MENU_VISIBLE_ROWS = 8;
    private static final int INFO_VISIBLE_CARDS = 6;
    private static final long PROGRESS_INTERVAL_MS = 15000;
    private static final long PROGRESS_SHUTDOWN_DELAY_MS = 1500;
    private static final long RESUME_SAVE_INTERVAL_MS = 5000;
    private static final long RESUME_MIN_POSITION_MS = 3000;
    private static final long RESUME_END_MARGIN_MS = 10000;
    /** 上次实测 Android UA 会被 PGC CDN 403，播放请求必须使用桌面 Chrome UA。 */
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final PlaybackRepository repository = new PlaybackRepository();
    private final BiliRepository detailRepository = new BiliRepository();
    private final Handler handler = new Handler();
    private PlaybackResume resumeStore;
    private boolean resumeSaving;
    private final List<PlaybackRepository.Danmaku> danmaku = new ArrayList<>();
    private SurfaceView surface;
    private PlayerOverlay overlay;
    private ExoPlayer player;
    private DefaultHttpDataSource.Factory dataSourceFactory;
    private PlaybackRepository.Stream stream;
    private String pageUrl = "";
    private String playbackUrl = "";
    private final List<PlaybackRepository.Episode> episodes = new ArrayList<>();
    private long currentEpId;
    private boolean episodesLoading;
    private String episodesError = "";
    private PlaybackRepository.Episode pendingEpisode;
    private final List<PlaybackRepository.Part> parts = new ArrayList<>();
    private int currentPartPage;
    private boolean partsLoading;
    private String partsError = "";
    private PlaybackRepository.Part pendingPart;
    private boolean prepared;
    private boolean destroyed;
    private boolean activityResumed;
    private boolean danmakuEnabled = true;
    private boolean autoPlay = true;
    private boolean highQuality = true;
    private boolean controlsVisible = true;
    private boolean menuMode;
    private boolean middleMode;
    private boolean subMenuMode;
    private int menuIndex;
    private int optionIndex;
    private int resumePosition;
    private int requestedQuality;
    private int candidateIndex;
    private boolean initialSeekDone;
    private boolean awaitingReady;
    private boolean progressiveFallbackTried;
    private boolean bvFallbackTried;
    private boolean pendingAutoplayRecommended;
    private boolean endedHandled;
    private boolean autoplayLaunching;
    private int speedIndex = 1;
    private final float[] speeds = {.75f, 1f, 1.25f, 1.5f, 2f};
    private long danmakuAnchorPositionMs;
    private long danmakuAnchorRealtimeMs;
    private boolean danmakuAnchorValid;
    private long danmakuLastSmoothMs;
    /** 只显示 timeMs >= 该值的弹幕，确保加载/seek/切集后从右边缘入场，而不是从屏幕中间出现。 */
    private long danmakuVisibleFromMs;
    private String stateText = "正在取得播放地址…";
    private String errorText = "";
    private String feedbackText = "";
    private boolean progressReporting;

    private final Runnable clearFeedback = () -> {
        feedbackText = "";
        if (overlay != null) overlay.invalidate();
    };

    // 下方信息区（简介 / 选集 / 推荐）
    private final List<InfoCard> infoCards = new ArrayList<>();
    private final Map<String, Bitmap> infoBitmaps = new java.util.LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > 10;
        }
    };
    private final Set<String> infoCoverLoading = new HashSet<>();
    private final Set<String> infoCoverFailed = new HashSet<>();
    private final List<VideoItem> relatedVideos = new ArrayList<>();
    private boolean infoMode;
    private int infoTab;
    private int infoFocusRow;
    private int infoCardIndex;
    private String videoDesc = "";
    private boolean detailLoading;
    private String detailError = "";
    private boolean relatedLoading;
    private String relatedError = "";

    private static final class InfoCard {
        final String title;
        final String subtitle;
        final String cover;
        final int page;
        final long epId;
        final String targetUrl;
        InfoCard(String title, String subtitle, String cover, int page, long epId, String targetUrl) {
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.cover = cover == null ? "" : cover;
            this.page = page;
            this.epId = epId;
            this.targetUrl = targetUrl == null ? "" : targetUrl;
        }
    }

    private final Runnable hideControls = () -> {
        controlsVisible = false; menuMode = false; subMenuMode = false;
        middleMode = false;
        infoMode = false; infoFocusRow = 0;
        if (overlay != null) overlay.invalidate();
    };

    /** 播放中每 15 秒回写一次进度；暂停/结束/出错/销毁时移除。 */
    private final Runnable progressReporter = new Runnable() {
        @Override public void run() {
            if (destroyed || isLive() || !prepared || player == null || stream == null || !player.isPlaying()) {
                progressReporting = false;
                return;
            }
            reportProgressNow();
            handler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    /** 每 5 秒保存一次本地续播进度；结束/出错/销毁/暂停时移除。 */
    private final Runnable resumeSaver = new Runnable() {
        @Override public void run() {
            if (destroyed || isLive() || !prepared || player == null || stream == null) {
                resumeSaving = false;
                return;
            }
            saveLocalResume();
            handler.postDelayed(this, RESUME_SAVE_INTERVAL_MS);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
            if (destroyed) return;
            if (state == Player.STATE_READY) {
                prepared = true;
                stateText = "";
                endedHandled = false;
                resetDanmakuAnchor();
                startResumeSaving();
                if (!initialSeekDone) {
                    initialSeekDone = true;
                    if (resumePosition > 0) player.seekTo(resumePosition);
                }
                applySpeed();
                if (awaitingReady) {
                    awaitingReady = false;
                    if (autoPlay && activityResumed) {
                        player.play();
                        startProgressReporting();
                        showControls(false);
                    } else {
                        player.pause();
                        stopProgressReporting();
                        controlsVisible = true; menuMode = false; subMenuMode = false;
                        handler.removeCallbacks(hideControls);
                    }
                }
                overlay.invalidate();
            } else if (state == Player.STATE_ENDED) {
                prepared = true;
                stopResumeSaving();
                clearLocalResume();
                stopProgressReporting();
                if (!endedHandled) {
                    endedHandled = true;
                    handlePlaybackEnded();
                }
                overlay.invalidate();
            } else if (state == Player.STATE_BUFFERING) {
                stateText = prepared ? "网络波动，正在缓冲…"
                        : candidateIndex == 0 ? "正在缓冲…" : "正在尝试备用线路…";
                overlay.invalidate();
            }
        }

        @Override public void onPlayerError(PlaybackException error) {
            if (destroyed) return;
            stopProgressReporting();
            stopResumeSaving();
            final String reason = describeError(error);
            handler.post(() -> handlePlaybackFailure(reason));
        }

        @Override public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) startProgressReporting();
            else stopProgressReporting();
            resetDanmakuAnchor();
            if (overlay != null) overlay.invalidate();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        overlay = new PlayerOverlay();
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        pageUrl = getIntent().getStringExtra(EXTRA_URL);
        if (pageUrl == null) pageUrl = "";
        playbackUrl = pageUrl;
        danmakuEnabled = getIntent().getBooleanExtra(EXTRA_DANMAKU, true);
        autoPlay = getIntent().getBooleanExtra(EXTRA_AUTOPLAY, true);
        highQuality = getIntent().getBooleanExtra(EXTRA_HIGH_QUALITY, true);
        int startQuality = highQuality ? 0 : 64;
        int startPosition = 0;
        if (state != null) {
            startPosition = Math.max(0, state.getInt(STATE_POSITION, 0));
            int savedQuality = state.getInt(STATE_QUALITY, 0);
            if (savedQuality > 0) startQuality = savedQuality;
            danmakuEnabled = state.getBoolean(STATE_DANMAKU, danmakuEnabled);
            speedIndex = Math.max(0, Math.min(speeds.length - 1, state.getInt(STATE_SPEED, speedIndex)));
            currentEpId = state.getLong(STATE_EPISODE, 0);
            if (currentEpId > 0 && isBangumi()) playbackUrl = episodePageUrl(currentEpId);
            currentPartPage = state.getInt(STATE_PART, 0);
            if (currentPartPage > 0 && !isBangumi()) playbackUrl = withPart(pageUrl, currentPartPage);
        }
        dataSourceFactory = buildDataSourceFactory();
        resumeStore = new PlaybackResume(this);
        player = new ExoPlayer.Builder(this).build();
        player.setVideoSurfaceView(surface);
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(playerListener);
        player.setPlayWhenReady(autoPlay && activityResumed);
        resolveAndPlay(startQuality, startPosition, true);
        if (isBangumi()) loadEpisodes();
        else if (!isLiveUrl()) loadParts();
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        int position = isLive() ? 0 : currentPosition();
        if (player != null) {
            try {
                long duration = player.getDuration();
                if (duration != C.TIME_UNSET && duration > 0 && position >= duration - 500) position = 0;
            } catch (Exception ignored) { }
        }
        out.putInt(STATE_POSITION, position);
        out.putInt(STATE_QUALITY, requestedQuality);
        out.putBoolean(STATE_DANMAKU, danmakuEnabled);
        out.putInt(STATE_SPEED, speedIndex);
        out.putLong(STATE_EPISODE, currentEpId);
        out.putInt(STATE_PART, currentPartPage);
    }

    @Override protected void onPause() {
        super.onPause();
        cancelPendingAutoplay();
        activityResumed = false;
        if (player != null && player.isPlaying()) player.pause();
        resetDanmakuAnchor();
        savePositionForResume();
        saveLocalResume();
        stopResumeSaving();
        if (prepared) reportProgressNow();
        stopProgressReporting();
    }

    private DefaultHttpDataSource.Factory buildDataSourceFactory() {
        Map<String, String> headers = new HashMap<>();
        String referer = playbackUrl.startsWith("http") ? playbackUrl : pageUrl;
        headers.put("Referer", referer.startsWith("http") ? referer : "https://www.bilibili.com/");
        headers.put("Origin", "https://www.bilibili.com");
        headers.put("User-Agent", DESKTOP_UA);
        String cookie = BiliAuthRepository.cookieHeader();
        if (!cookie.isEmpty()) headers.put("Cookie", cookie);
        return new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000);
    }

    private void resolveAndPlay(int quality, int position) {
        resolveAndPlay(quality, position, false);
    }

    private void resolveAndPlay(int quality, int position, boolean readStoredResume) {
        pendingAutoplayRecommended = false;
        endedHandled = false;
        saveLocalResume();
        stopResumeSaving();
        stopProgressReporting();
        requestedQuality = quality;
        resumePosition = Math.max(0, position);
        candidateIndex = 0;
        initialSeekDone = false;
        awaitingReady = true;
        progressiveFallbackTried = false;
        bvFallbackTried = false;
        pendingEpisode = null;
        pendingPart = null;
        prepared = false;
        errorText = "";
        stateText = quality > 0 ? "正在切换清晰度…" : "正在取得播放地址…";
        overlay.invalidate();
        resolveCurrent(quality, (result, error) -> {
            if (destroyed) return;
            if (result == null) {
                if (!bvFallbackTried && readStoredResume) {
                    String bvid = extractBvid(playbackUrl.isEmpty() ? pageUrl : playbackUrl);
                    if (!bvid.isEmpty()) {
                        bvFallbackTried = true;
                        stateText = "正在重试播放地址…";
                        errorText = "";
                        overlay.invalidate();
                        repository.resolve("https://www.bilibili.com/video/" + bvid, quality,
                                (retryResult, retryError) -> {
                                    if (destroyed) return;
                                    if (retryResult == null) showResolveError(retryError);
                                    else applyResolvedStream(retryResult, readStoredResume);
                                });
                        return;
                    }
                }
                showResolveError(error);
                return;
            }
            applyResolvedStream(result, readStoredResume);
        });
    }

    private void showResolveError(String error) {
        stateText = "";
        feedbackText = "";
        handler.removeCallbacks(clearFeedback);
        errorText = error == null ? "暂时无法取得播放地址，请稍后重试" : error;
        if (stream != null && player != null) prepared = true;
        if (stream != null) requestedQuality = stream.currentQuality;
        awaitingReady = false;
        overlay.invalidate();
    }

    private void applyResolvedStream(PlaybackRepository.Stream result, boolean readStoredResume) {
        stream = result;
        candidateIndex = 0;
        initialSeekDone = false;
        awaitingReady = true;
        if (result.cid > 0 && danmaku.isEmpty()) loadDanmakuFor(result.cid);
        int start = resumePosition;
        if (readStoredResume && start == 0) start = storedResumePosition(result);
        danmakuVisibleFromMs = Math.max(0, start);
        prepareSource(result, start);
    }

    private static String extractBvid(String url) {
        if (url == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(BV[0-9A-Za-z]{10})").matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void resolveCurrent(int quality, PlaybackRepository.StreamCallback callback) {
        if (isBangumi() && currentEpId > 0) {
            repository.resolveEpisode(currentEpId, quality, callback);
            return;
        }
        if (!isBangumi() && !isLiveUrl() && (parts.size() > 1 || currentPartPage > 1)) {
            repository.resolvePart(pageUrl, currentPartPage, quality, callback);
            return;
        }
        repository.resolve(playbackUrl.isEmpty() ? pageUrl : playbackUrl, quality, callback);
    }

    private boolean isBangumi() {
        return pageUrl.contains("/bangumi/play/");
    }

    private boolean isLiveUrl() {
        return pageUrl.contains("live.bilibili.com/");
    }

    private String episodePageUrl(long epId) {
        return "https://www.bilibili.com/bangumi/play/ep" + epId;
    }

    /** 生成带 ?p= 的分P页面地址；page<=1 时移除 p 参数。 */
    private String withPart(String url, int page) {
        if (url == null || url.isEmpty()) return url;
        android.net.Uri uri = android.net.Uri.parse(url);
        android.net.Uri.Builder builder = uri.buildUpon().clearQuery();
        for (String name : uri.getQueryParameterNames()) {
            if ("p".equals(name)) continue;
            for (String value : uri.getQueryParameters(name)) builder.appendQueryParameter(name, value);
        }
        if (page > 1) builder.appendQueryParameter("p", String.valueOf(page));
        return builder.build().toString();
    }

    private void loadDanmakuFor(long cid) {
        if (cid <= 0) return;
        repository.loadDanmaku(cid, items -> {
            if (destroyed) return;
            danmaku.clear();
            danmaku.addAll(items);
            danmakuVisibleFromMs = Math.max(0, currentPosition());
            if (overlay != null) overlay.clearDanmakuCache();
            overlay.invalidate();
        });
    }

    /** 进入番剧播放器时异步拉取选集；失败静默，不打断当前播放。 */
    private void loadEpisodes() {
        if (!isBangumi()) return;
        episodesLoading = true;
        overlay.invalidate();
        repository.loadEpisodes(pageUrl, (items, currentId, error) -> {
            if (destroyed) return;
            episodesLoading = false;
            if (items != null && !items.isEmpty()) {
                episodes.clear();
                episodes.addAll(items);
                episodesError = "";
            } else {
                episodesError = error == null ? "选集加载失败" : error;
            }
            if (currentEpId <= 0 && currentId > 0) currentEpId = currentId;
            if (currentEpId <= 0 && !episodes.isEmpty()) currentEpId = episodes.get(0).epId;
            if (infoMode) rebuildInfoCards();
            overlay.invalidate();
        });
    }

    private void switchEpisode(PlaybackRepository.Episode episode) {
        switchEpisode(episode, true);
    }

    /** 应用选集：解析目标集、更新 Referer、重载弹幕并从已存进度（或 0）开始播放。 */
    private void switchEpisode(PlaybackRepository.Episode episode, boolean readStoredResume) {
        if (episode == null || destroyed) return;
        pendingAutoplayRecommended = false;
        endedHandled = false;
        stopProgressReporting();
        saveLocalResume();
        stopResumeSaving();
        infoMode = false;
        middleMode = false;
        infoFocusRow = 0;
        final long targetEpId = episode.epId;
        prepared = false;
        errorText = "";
        stateText = "正在切换剧集…";
        controlsVisible = false;
        menuMode = false;
        subMenuMode = false;
        handler.removeCallbacks(hideControls);
        overlay.invalidate();
        repository.resolveEpisode(targetEpId, requestedQuality, (result, error) -> {
            if (destroyed) return;
            if (result == null) {
                if (player != null) player.pause();
                pendingEpisode = episode;
                stateText = "";
                errorText = error == null ? "剧集切换失败，请重试" : error;
                controlsVisible = true;
                overlay.invalidate();
                return;
            }
            pendingEpisode = null;
            currentEpId = targetEpId;
            playbackUrl = episodePageUrl(targetEpId);
            dataSourceFactory = buildDataSourceFactory();
            stream = result;
            candidateIndex = 0;
            initialSeekDone = false;
            awaitingReady = true;
            progressiveFallbackTried = false;
            danmaku.clear();
            if (overlay != null) overlay.clearDanmakuCache();
            int start = readStoredResume ? storedResumePosition(result) : 0;
            danmakuVisibleFromMs = start;
            loadDanmakuFor(result.cid);
            prepareSource(result, start);
        });
    }

    private PlaybackRepository.Episode findEpisode(long epId) {
        for (PlaybackRepository.Episode episode : episodes) {
            if (episode.epId == epId) return episode;
        }
        return null;
    }

    private int currentEpisodeOptionIndex() {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).epId == currentEpId) return i;
        }
        return 0;
    }

    private String episodeLabel() {
        if (episodes.isEmpty()) return episodesLoading ? "加载中" : "选集";
        PlaybackRepository.Episode current = findEpisode(currentEpId);
        if (current == null) return episodes.size() + "集";
        String index = current.index == null ? "" : current.index.trim();
        if (!index.isEmpty()) return index.matches("\\d+") ? "第" + index + "集" : index;
        return "第" + (currentEpisodeOptionIndex() + 1) + "集";
    }

    /** 普通视频进入时异步拉取分P列表；失败静默，不打断当前播放。 */
    private void loadParts() {
        if (isBangumi() || isLiveUrl()) return;
        partsLoading = true;
        overlay.invalidate();
        repository.loadParts(pageUrl, (items, currentPage, error) -> {
            if (destroyed) return;
            partsLoading = false;
            if (items != null && !items.isEmpty()) {
                parts.clear();
                parts.addAll(items);
                partsError = "";
            } else {
                partsError = error == null ? "分P加载失败" : error;
            }
            if (currentPartPage <= 0 && currentPage > 0) currentPartPage = currentPage;
            if (currentPartPage <= 0 && !parts.isEmpty()) currentPartPage = parts.get(0).page;
            if (currentPartPage > 0) playbackUrl = withPart(pageUrl, currentPartPage);
            // URL 带 ?p= 时首帧解析默认落到第 1 P；分P 列表回来后对齐到目标 P。
            if (currentPartPage > 1 && stream != null && currentPartPage <= parts.size()) {
                PlaybackRepository.Part target = parts.get(currentPartPage - 1);
                if (target.cid > 0 && stream.cid > 0 && target.cid != stream.cid) {
                    switchPart(target);
                }
            }
            if (infoMode) rebuildInfoCards();
            overlay.invalidate();
        });
    }

    private void switchPart(PlaybackRepository.Part part) {
        switchPart(part, true);
    }

    /** 应用分P：解析目标 P、更新 Referer、重载弹幕并从已存进度（或 0）开始播放。 */
    private void switchPart(PlaybackRepository.Part part, boolean readStoredResume) {
        if (part == null || destroyed) return;
        pendingAutoplayRecommended = false;
        endedHandled = false;
        stopProgressReporting();
        saveLocalResume();
        stopResumeSaving();
        infoMode = false;
        middleMode = false;
        infoFocusRow = 0;
        final int targetPage = part.page;
        prepared = false;
        errorText = "";
        stateText = "正在切换分P…";
        controlsVisible = false;
        menuMode = false;
        subMenuMode = false;
        handler.removeCallbacks(hideControls);
        overlay.invalidate();
        repository.resolvePart(pageUrl, targetPage, requestedQuality, (result, error) -> {
            if (destroyed) return;
            if (result == null) {
                if (player != null) player.pause();
                pendingPart = part;
                stateText = "";
                errorText = error == null ? "分P切换失败，请重试" : error;
                controlsVisible = true;
                overlay.invalidate();
                return;
            }
            pendingPart = null;
            currentPartPage = targetPage;
            playbackUrl = withPart(pageUrl, targetPage);
            dataSourceFactory = buildDataSourceFactory();
            stream = result;
            candidateIndex = 0;
            initialSeekDone = false;
            awaitingReady = true;
            progressiveFallbackTried = false;
            danmaku.clear();
            if (overlay != null) overlay.clearDanmakuCache();
            int start = readStoredResume ? storedResumePosition(result) : 0;
            danmakuVisibleFromMs = start;
            loadDanmakuFor(result.cid);
            prepareSource(result, start);
        });
    }

    private int indexOfEpisode(long epId) {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).epId == epId) return i;
        }
        return -1;
    }

    private int indexOfPart(int page) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).page == page) return i;
        }
        return -1;
    }

    private int currentPartOptionIndex() {
        int index = indexOfPart(currentPartPage);
        return index < 0 ? 0 : index;
    }

    private String partLabel() {
        if (parts.isEmpty()) return partsLoading ? "加载中" : "分P";
        if (currentPartPage > 0) return "P" + currentPartPage;
        return "P" + parts.get(0).page;
    }

    private boolean isMultiPart() {
        return parts.size() > 1;
    }

    // ===== 下方信息区（简介 / 选集 / 推荐）=====

    private int infoTabCount() {
        return isLive() ? 1 : 2;
    }

    private String infoTabTitle(int index) {
        if (index == 0) return "简介";
        return isBangumi() || isMultiPart() ? "选集" : "推荐";
    }

    private void openInfoPanel() {
        cancelPendingAutoplay();
        menuMode = false;
        middleMode = false;
        subMenuMode = false;
        infoMode = true;
        infoFocusRow = 0;
        infoTab = 0;
        controlsVisible = true;
        rebuildInfoCards();
        infoCardIndex = currentInfoCardIndex();
        loadInfoDetailIfNeeded();
        scheduleHide();
        overlay.invalidate();
    }

    private void closeInfoPanel() {
        infoMode = false;
        infoFocusRow = 0;
        scheduleHide();
        overlay.invalidate();
    }

    private void openFunctionMenu() {
        cancelPendingAutoplay();
        infoMode = false;
        infoFocusRow = 0;
        middleMode = false;
        controlsVisible = true;
        menuMode = true;
        subMenuMode = false;
        menuIndex = 0;
        scheduleHide();
        overlay.invalidate();
    }

    /** 中间进度条控制区：只高亮进度条，←→ 快退/快进，OK 暂停/继续。 */
    private void openMiddlePanel() {
        cancelPendingAutoplay();
        menuMode = false;
        subMenuMode = false;
        infoMode = false;
        infoFocusRow = 0;
        middleMode = true;
        controlsVisible = true;
        scheduleHide();
        overlay.invalidate();
    }

    private void handleDpadUp() {
        if (infoMode) {
            scheduleHide();
            if (infoFocusRow == 1) {
                infoFocusRow = 0;
                overlay.invalidate();
            } else {
                openMiddlePanel();
            }
            return;
        }
        if (middleMode) {
            openFunctionMenu();
            return;
        }
        if (menuMode) {
            if (subMenuMode) openOrMoveOption(-1);
            else openInfoPanel();
            return;
        }
        openFunctionMenu();
    }

    private void handleDpadDown() {
        if (infoMode) {
            scheduleHide();
            if (infoFocusRow == 1) {
                openFunctionMenu();
                return;
            }
            if (infoTab == 0 && infoTabCount() > 1) {
                infoTab = 1;
                infoCardIndex = currentInfoCardIndex();
                overlay.invalidate();
                return;
            }
            if (infoTab == 1 && !infoCards.isEmpty()) {
                infoFocusRow = 1;
                if (infoCardIndex >= infoCards.size()) infoCardIndex = Math.max(0, infoCards.size() - 1);
                overlay.invalidate();
                return;
            }
            openFunctionMenu();
            return;
        }
        if (middleMode) {
            openInfoPanel();
            return;
        }
        if (menuMode) {
            if (subMenuMode) openOrMoveOption(1);
            else openMiddlePanel();
            return;
        }
        openInfoPanel();
    }

    private void moveInfoSelection(int delta) {
        if (infoFocusRow == 0) {
            int count = infoTabCount();
            if (count > 1) {
                int next = Math.max(0, Math.min(count - 1, infoTab + delta));
                if (next != infoTab) {
                    infoTab = next;
                    if (infoTab == 1) infoCardIndex = currentInfoCardIndex();
                }
            }
        } else if (!infoCards.isEmpty()) {
            infoCardIndex = Math.max(0, Math.min(infoCards.size() - 1, infoCardIndex + delta));
        }
        scheduleHide();
        overlay.invalidate();
    }

    private void activateInfoSelection() {
        scheduleHide();
        if (infoFocusRow == 0 || infoCardIndex < 0 || infoCardIndex >= infoCards.size()) return;
        InfoCard card = infoCards.get(infoCardIndex);
        if (card.epId > 0) {
            if (card.epId == currentEpId) {
                closeInfoPanel();
                return;
            }
            PlaybackRepository.Episode episode = findEpisode(card.epId);
            closeInfoPanel();
            if (episode != null) switchEpisode(episode);
        } else if (card.page > 0) {
            if (card.page == currentPartPage) {
                closeInfoPanel();
                return;
            }
            PlaybackRepository.Part part = findPart(card.page);
            closeInfoPanel();
            if (part != null) switchPart(part);
        } else if (!card.targetUrl.isEmpty()) {
            closeInfoPanel();
            openRecommended(card.targetUrl);
        }
    }

    private PlaybackRepository.Part findPart(int page) {
        for (PlaybackRepository.Part part : parts) {
            if (part.page == page) return part;
        }
        return null;
    }

    private void openRecommended(String url) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_DANMAKU, danmakuEnabled);
        intent.putExtra(EXTRA_AUTOPLAY, autoPlay);
        intent.putExtra(EXTRA_HIGH_QUALITY, highQuality);
        startActivity(intent);
    }

    private int currentInfoCardIndex() {
        if (isBangumi()) {
            int index = indexOfEpisode(currentEpId);
            return index < 0 ? 0 : index;
        }
        if (isMultiPart()) {
            int index = indexOfPart(currentPartPage);
            return index < 0 ? 0 : index;
        }
        return 0;
    }

    private void rebuildInfoCards() {
        infoCards.clear();
        if (isBangumi()) {
            for (int i = 0; i < episodes.size(); i++) {
                PlaybackRepository.Episode episode = episodes.get(i);
                infoCards.add(new InfoCard(episode.title, episodeIndexLabel(episode, i),
                        episode.cover, 0, episode.epId, null));
            }
        } else if (isMultiPart()) {
            for (PlaybackRepository.Part part : parts) {
                infoCards.add(new InfoCard(part.title, "P" + part.page, part.cover, part.page, 0, null));
            }
        } else if (!isLive()) {
            for (VideoItem item : relatedVideos) {
                infoCards.add(new InfoCard(item.title, item.duration, item.coverUrl, 0, 0, item.targetUrl));
            }
        }
        if (infoCardIndex >= infoCards.size()) infoCardIndex = Math.max(0, infoCards.size() - 1);
    }

    private String episodeIndexLabel(PlaybackRepository.Episode episode, int index) {
        String value = episode.index == null ? "" : episode.index.trim();
        if (value.isEmpty()) return "第" + (index + 1) + "集";
        return value.matches("\\d+") ? "第" + value + "集" : value;
    }

    /** 简介/推荐按需加载；失败不阻塞，简介回退标题。 */
    private void loadInfoDetailIfNeeded() {
        if (isLive()) {
            if (videoDesc.isEmpty() && stream != null) videoDesc = stream.title;
            return;
        }
        if (isBangumi()) {
            if (videoDesc.isEmpty() && stream != null) videoDesc = stream.title;
            return;
        }
        if (videoDesc.isEmpty() && !detailLoading && detailError.isEmpty()) {
            detailLoading = true;
            final String target = stream != null && !stream.bvid.isEmpty() ? stream.bvid
                    : (playbackUrl.isEmpty() ? pageUrl : playbackUrl);
            detailRepository.loadVideoDetail(target, (detail, error) -> {
                if (destroyed) return;
                detailLoading = false;
                if (detail != null) {
                    videoDesc = detail.desc == null ? "" : detail.desc.trim();
                    if (videoDesc.isEmpty()) videoDesc = detail.title;
                    if (!isMultiPart() && relatedVideos.isEmpty() && !relatedLoading
                            && !detail.bvid.isEmpty()) {
                        loadInfoRelated(detail.bvid);
                    }
                } else {
                    detailError = error == null ? "" : error;
                    if (videoDesc.isEmpty() && stream != null) videoDesc = stream.title;
                }
                if (infoMode) {
                    rebuildInfoCards();
                    overlay.invalidate();
                }
            });
        }
        if (!isMultiPart() && relatedVideos.isEmpty() && !relatedLoading && relatedError.isEmpty()
                && stream != null && !stream.bvid.isEmpty()) {
            loadInfoRelated(stream.bvid);
        }
    }

    private void loadInfoRelated(String bvid) {
        if (bvid == null || bvid.isEmpty() || relatedLoading) return;
        relatedLoading = true;
        detailRepository.loadRelatedVideos(bvid, (items, error) -> {
            if (destroyed) return;
            relatedLoading = false;
            relatedVideos.clear();
            if (items != null) relatedVideos.addAll(items);
            relatedError = error == null ? "" : error;
            if (infoMode) {
                rebuildInfoCards();
            }
            if (pendingAutoplayRecommended) {
                pendingAutoplayRecommended = false;
                if (!destroyed && player != null && player.getPlaybackState() == Player.STATE_ENDED) {
                    maybeContinueWithRecommended();
                } else {
                    stateText = "";
                    controlsVisible = true;
                    overlay.invalidate();
                }
            } else {
                overlay.invalidate();
            }
        });
    }

    private void ensureInfoCover(String url) {
        if (url == null || url.isEmpty() || infoBitmaps.containsKey(url)
                || infoCoverLoading.contains(url) || infoCoverFailed.contains(url)) return;
        infoCoverLoading.add(url);
        detailRepository.loadImage(url, bitmap -> {
            infoCoverLoading.remove(url);
            if (destroyed) return;
            if (bitmap != null) infoBitmaps.put(url, bitmap);
            else infoCoverFailed.add(url);
            if (overlay != null) overlay.invalidate();
        });
    }

    private String infoCardStatusText() {
        if (isBangumi()) {
            if (episodesLoading) return "正在加载选集…";
            if (!episodesError.isEmpty()) return episodesError;
            return "暂无选集";
        }
        if (partsLoading && parts.isEmpty()) return "正在加载…";
        if (isMultiPart()) return "暂无分P";
        if (isLive()) return "直播没有选集";
        if (relatedLoading) return "正在加载推荐…";
        if (!relatedError.isEmpty()) return relatedError;
        return "暂无推荐";
    }

    private String infoIntroText() {
        if (!videoDesc.isEmpty()) return videoDesc;
        if (isBangumi() && stream != null) return stream.title;
        if (detailLoading) return "正在加载简介…";
        if (!detailError.isEmpty()) return detailError;
        if (stream != null && !stream.title.isEmpty()) return stream.title;
        return "暂无简介";
    }

    private boolean infoCardIsCurrent(InfoCard card) {
        if (card.epId > 0) return card.epId == currentEpId;
        if (card.page > 0) return card.page == currentPartPage;
        return false;
    }

    /** 播放器左下角标题：番剧显示「季名 · 第N集 剧集标题」，多P显示视频标题 · P号。 */
    private String playbackTitle() {
        if (stream == null) return "哔哩哔哩";
        String title = stream.title == null ? "" : stream.title.trim();
        if (isBangumi() && currentEpId > 0) {
            int index = indexOfEpisode(currentEpId);
            if (index >= 0) {
                PlaybackRepository.Episode episode = episodes.get(index);
                String label = episodeIndexLabel(episode, index);
                String episodeTitle = episode.title == null ? "" : episode.title.trim();
                String season = title;
                if (!episodeTitle.isEmpty() && season.endsWith(episodeTitle)) {
                    season = season.substring(0, season.length() - episodeTitle.length());
                    season = season.replaceAll("[\\s·\\-—–]+$", "");
                }
                StringBuilder builder = new StringBuilder();
                if (!season.isEmpty()) builder.append(season).append(" · ");
                builder.append(label);
                if (!episodeTitle.isEmpty()) builder.append(' ').append(episodeTitle);
                return builder.toString();
            }
        }
        if (!isBangumi() && !isLive() && isMultiPart() && currentPartPage > 0) {
            return (title.isEmpty() ? "" : title + " · ") + "P" + currentPartPage;
        }
        return title.isEmpty() ? "哔哩哔哩" : title;
    }

    /** 播放结束自动切下一集/下一P；没有下一项时返回 false，保持原有“显示控制层”的行为。 */
    private boolean playNextIfAvailable() {
        if (isBangumi()) {
            int index = indexOfEpisode(currentEpId);
            if (index >= 0 && index + 1 < episodes.size()) {
                switchEpisode(episodes.get(index + 1));
                return true;
            }
            return false;
        }
        if (!isLive() && parts.size() > 1) {
            int index = indexOfPart(currentPartPage);
            if (index >= 0 && index + 1 < parts.size()) {
                switchPart(parts.get(index + 1));
                return true;
            }
        }
        return false;
    }

    /** 播放结束：优先下一集/下一P，否则自动续播相关推荐；直播不续播。 */
    private void handlePlaybackEnded() {
        pendingAutoplayRecommended = false;
        if (destroyed || autoplayLaunching) return;
        if (!activityResumed) {
            stateText = "";
            controlsVisible = true; menuMode = false; subMenuMode = false;
            return;
        }
        if (isLive()) {
            stateText = "";
            controlsVisible = true; menuMode = false; subMenuMode = false;
            return;
        }
        if (playNextIfAvailable()) return;
        maybeContinueWithRecommended();
    }

    /** 用户接管播放/离开页面时取消等待中的自动续播。 */
    private void cancelPendingAutoplay() {
        if (!pendingAutoplayRecommended) return;
        pendingAutoplayRecommended = false;
        if (stateText.startsWith("正在寻找下一个视频")) stateText = "";
        controlsVisible = true;
        if (overlay != null) overlay.invalidate();
    }

    /** 预取相关推荐：非直播、有 bvid、且尚未加载/加载中时触发；失败静默。 */
    private void prefetchRelated() {
        if (isLive() || stream == null || stream.bvid.isEmpty()) return;
        if (relatedLoading || !relatedVideos.isEmpty() || !relatedError.isEmpty()) return;
        loadInfoRelated(stream.bvid);
    }

    private void maybeContinueWithRecommended() {
        if (destroyed || autoplayLaunching || isLive()) return;
        VideoItem next = firstPlayableRelated();
        if (next != null) {
            launchRecommended(next);
            return;
        }
        if (relatedLoading) {
            pendingAutoplayRecommended = true;
            stateText = "正在寻找下一个视频…";
            controlsVisible = false;
            menuMode = false;
            subMenuMode = false;
            infoMode = false;
            middleMode = false;
            overlay.invalidate();
            return;
        }
        stateText = "";
        controlsVisible = true; menuMode = false; subMenuMode = false;
        showFeedback("没有自动续播的推荐视频");
        overlay.invalidate();
    }

    private VideoItem firstPlayableRelated() {
        String currentBvid = stream == null ? "" : stream.bvid;
        for (VideoItem item : relatedVideos) {
            String url = item.targetUrl == null ? "" : item.targetUrl;
            if (url.isEmpty()) continue;
            String bvid = extractBvid(url);
            if (bvid.isEmpty() && !url.contains("/video/")) continue;
            if (!currentBvid.isEmpty() && currentBvid.equals(bvid)) continue;
            return item;
        }
        return null;
    }

    /** 打开推荐视频作为新播放页，强制自动播放并结束当前页，避免返回栈堆积。 */
    private void launchRecommended(VideoItem item) {
        if (destroyed || autoplayLaunching || item == null) return;
        autoplayLaunching = true;
        pendingAutoplayRecommended = false;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_URL, item.targetUrl);
        intent.putExtra(EXTRA_DANMAKU, danmakuEnabled);
        intent.putExtra(EXTRA_AUTOPLAY, true);
        intent.putExtra(EXTRA_HIGH_QUALITY, highQuality);
        startActivity(intent);
        finish();
    }

    private void prepareSource(PlaybackRepository.Stream target, int startPositionMs) {
        if (destroyed || player == null || target == null) return;
        prefetchRelated();
        MediaSource source = buildMediaSource(target);
        if (source == null) {
            handlePlaybackFailure("播放源不可用");
            return;
        }
        int start = Math.max(0, startPositionMs);
        resumePosition = start;
        initialSeekDone = false;
        prepared = false;
        awaitingReady = true;
        resetDanmakuAnchor();
        try {
            player.setMediaSource(source, start);
            player.prepare();
            player.setPlayWhenReady(autoPlay && activityResumed);
        } catch (Exception error) {
            handlePlaybackFailure("播放器初始化失败");
            return;
        }
        stateText = start > 0 ? "正在恢复播放…" : "正在缓冲…";
        overlay.invalidate();
    }

    private MediaSource buildMediaSource(PlaybackRepository.Stream target) {
        if (target.kind == PlaybackRepository.KIND_HLS) {
            String url = candidateAt(target.candidates, candidateIndex);
            if (url == null) return null;
            return new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url));
        }
        if (target.kind == PlaybackRepository.KIND_DASH) {
            String videoUrl = candidateAt(target.videoCandidates, candidateIndex);
            if (videoUrl == null) return null;
            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl));
            String audioUrl = candidateAt(target.audioCandidates, candidateIndex);
            if (audioUrl == null) return videoSource;
            MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl));
            return new MergingMediaSource(videoSource, audioSource);
        }
        String url = candidateAt(target.candidates, candidateIndex);
        if (url == null) return null;
        return new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url));
    }

    private static String candidateAt(List<String> candidates, int index) {
        if (candidates == null || candidates.isEmpty()) return null;
        int safe = Math.max(0, Math.min(index, candidates.size() - 1));
        return candidates.get(safe);
    }

    private int candidateCount() {
        if (stream == null) return 0;
        if (stream.kind == PlaybackRepository.KIND_DASH) {
            int videos = stream.videoCandidates == null ? 0 : stream.videoCandidates.size();
            if (videos == 0) return 0;
            int audios = stream.audioCandidates == null ? 0 : stream.audioCandidates.size();
            return audios == 0 ? videos : Math.min(videos, audios);
        }
        return stream.candidates == null ? 0 : stream.candidates.size();
    }

    private boolean hasMoreCandidates() {
        return stream != null && candidateIndex < candidateCount();
    }

    /**
     * DASH 播放失败先尝试一次渐进流回退；仍失败或本来就是渐进/HLS 时，按候选 CDN 逐个重试。
     */
    private void handlePlaybackFailure(String reason) {
        if (destroyed) return;
        stopProgressReporting();
        if (stream != null && stream.kind == PlaybackRepository.KIND_DASH && !progressiveFallbackTried) {
            progressiveFallbackTried = true;
            final int position = currentPosition();
            stateText = "正在尝试兼容播放…";
            errorText = "";
            overlay.invalidate();
            if (!isBangumi() && !isLiveUrl() && currentPartPage > 1) {
                // 数据层 resolveProgressive 固定按 P1 解析；分P 回退改用 resolvePart 并校验 cid，避免串回第 1 P。
                repository.resolvePart(pageUrl, currentPartPage, requestedQuality, (fallback, error) -> {
                    if (destroyed) return;
                    if (fallback != null && isCurrentPartStream(fallback)) {
                        stream = fallback;
                        candidateIndex = 0;
                        initialSeekDone = false;
                        awaitingReady = true;
                        prepareSource(fallback, position);
                    } else {
                        handleCandidateFailure(reason);
                    }
                });
                return;
            }
            repository.resolveProgressive(playbackUrl.isEmpty() ? pageUrl : playbackUrl, requestedQuality, (fallback, error) -> {
                if (destroyed) return;
                if (fallback != null) {
                    stream = fallback;
                    candidateIndex = 0;
                    initialSeekDone = false;
                    awaitingReady = true;
                    prepareSource(fallback, position);
                } else {
                    handleCandidateFailure(reason);
                }
            });
            return;
        }
        handleCandidateFailure(reason);
    }

    private boolean isCurrentPartStream(PlaybackRepository.Stream candidate) {
        if (candidate == null) return false;
        int index = indexOfPart(currentPartPage);
        if (index < 0) return true;
        long expected = parts.get(index).cid;
        return expected <= 0 || candidate.cid <= 0 || candidate.cid == expected;
    }

    private void handleCandidateFailure(String reason) {
        if (destroyed) return;
        if (stream == null) {
            stateText = "";
            feedbackText = "";
            handler.removeCallbacks(clearFeedback);
            errorText = reason;
            overlay.invalidate();
            return;
        }
        candidateIndex++;
        if (hasMoreCandidates()) {
            stateText = "正在尝试备用线路…";
            errorText = "";
            initialSeekDone = false;
            awaitingReady = true;
            int retryPosition = isLive() ? 0 : currentPosition();
            prepareSource(stream, retryPosition);
        } else {
            stateText = "";
            feedbackText = "";
            handler.removeCallbacks(clearFeedback);
            errorText = reason;
            overlay.invalidate();
        }
    }

    private static String describeError(PlaybackException error) {
        if (error == null) return "播放失败";
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS: return "播放失败（CDN 返回异常）";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: return "播放失败（网络超时）";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: return "播放失败（网络连接失败）";
            case PlaybackException.ERROR_CODE_DECODING_FAILED: return "播放失败（解码失败）";
            default: return "播放失败";
        }
    }

    private boolean isLive() {
        return stream != null && stream.kind == PlaybackRepository.KIND_HLS;
    }

    private long playbackDuration() {
        if (player == null || isLive()) return 0;
        long duration = player.getDuration();
        return duration == C.TIME_UNSET || duration <= 0 ? 0 : duration;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (infoMode) moveInfoSelection(-1);
            else if (middleMode) seekBy(-10000);
            else if (menuMode && subMenuMode) openOrMoveOption(-1);
            else if (menuMode) moveFunction(-1);
            else seekBy(-10000);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (infoMode) moveInfoSelection(1);
            else if (middleMode) seekBy(10000);
            else if (menuMode && subMenuMode) openOrMoveOption(1);
            else if (menuMode) moveFunction(1);
            else seekBy(10000);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { handleDpadUp(); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { handleDpadDown(); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!errorText.isEmpty() && !infoMode && !menuMode && !middleMode) retryPlayback();
            else if (infoMode) activateInfoSelection();
            else if (middleMode) togglePlayback();
            else if (menuMode) activateMenu();
            else togglePlayback();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && infoMode) {
            closeInfoPanel();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && middleMode) {
            middleMode = false;
            controlsVisible = false;
            handler.removeCallbacks(hideControls);
            overlay.invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && subMenuMode) {
            subMenuMode = false; scheduleHide(); overlay.invalidate(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && menuMode) {
            menuMode = false; controlsVisible = false; handler.removeCallbacks(hideControls); overlay.invalidate(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void seekBy(int delta) {
        cancelPendingAutoplay();
        if (isLive()) {
            showFeedback("直播暂不支持快进或快退");
            showControls(false);
            return;
        }
        if (!prepared || player == null) return;
        long duration = Math.max(0, playbackDuration());
        if (duration <= 0) duration = Math.max(0, player.getDuration());
        long target = Math.max(0, Math.min(duration, player.getCurrentPosition() + delta));
        player.seekTo(target);
        resetDanmakuAnchor((int) Math.min(Integer.MAX_VALUE, target));
        danmakuVisibleFromMs = Math.max(0, target);
        showFeedback((delta > 0 ? "快进 10 秒  ·  " : "快退 10 秒  ·  ") + timeLabel(target));
        showControls(false);
    }

    private void retryPlayback() {
        if (destroyed) return;
        if (pendingEpisode != null) {
            PlaybackRepository.Episode retry = pendingEpisode;
            pendingEpisode = null;
            errorText = "";
            switchEpisode(retry, false);
            return;
        }
        if (pendingPart != null) {
            PlaybackRepository.Part retry = pendingPart;
            pendingPart = null;
            errorText = "";
            switchPart(retry, false);
            return;
        }
        int position = prepared ? currentPosition() : Math.max(0, resumePosition);
        errorText = "";
        resolveAndPlay(requestedQuality, position, stream == null);
    }

    private void showFeedback(String value) {
        feedbackText = value == null ? "" : value;
        handler.removeCallbacks(clearFeedback);
        if (!feedbackText.isEmpty()) handler.postDelayed(clearFeedback, 1600);
        if (overlay != null) overlay.invalidate();
    }

    private static String timeLabel(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        return String.format(java.util.Locale.CHINA, "%02d:%02d", minutes, seconds % 60);
    }

    private void openOrMoveOption(int delta) {
        infoMode = false;
        infoFocusRow = 0;
        middleMode = false;
        controlsVisible = true;
        if (!menuMode) menuMode = true;
        else if (subMenuMode) optionIndex = Math.max(0, Math.min(optionCount() - 1, optionIndex + delta));
        scheduleHide(); overlay.invalidate();
    }

    private int functionCount() {
        return isBangumi() || parts.size() > 1 ? 4 : 3;
    }

    private String fourthMenuTitle() {
        return isBangumi() ? "选集" : "分P";
    }

    private String fourthMenuValue() {
        return isBangumi() ? episodeLabel() : partLabel();
    }

    private void moveFunction(int delta) {
        if (subMenuMode) return;
        menuIndex = Math.max(0, Math.min(functionCount() - 1, menuIndex + delta));
        scheduleHide(); overlay.invalidate();
    }

    private void activateMenu() {
        if (!subMenuMode) {
            subMenuMode = true;
            if (menuIndex == 0) optionIndex = danmakuEnabled ? 0 : 1;
            else if (menuIndex == 1) optionIndex = speedIndex;
            else if (menuIndex == 2) optionIndex = currentQualityIndex();
            else optionIndex = isBangumi() ? currentEpisodeOptionIndex() : currentPartOptionIndex();
            scheduleHide(); overlay.invalidate(); return;
        }
        if (menuIndex == 0) {
            danmakuEnabled = optionIndex == 0;
            showFeedback(danmakuEnabled ? "弹幕已开启" : "弹幕已关闭");
        }
        else if (menuIndex == 1) {
            speedIndex = optionIndex;
            applySpeed();
            showFeedback("播放速度  " + trimSpeedValue(speeds[speedIndex]));
        }
        else if (menuIndex == 2 && stream != null && !stream.qualities.isEmpty()) {
            PlaybackRepository.Quality selected = stream.qualities.get(optionIndex);
            if (selected.id != stream.currentQuality) {
                resolveAndPlay(selected.id, currentPosition());
            }
        } else if (menuIndex == 3) {
            if (isBangumi()) {
                if (optionIndex >= 0 && optionIndex < episodes.size()) {
                    PlaybackRepository.Episode selected = episodes.get(optionIndex);
                    if (selected.epId != currentEpId) switchEpisode(selected);
                }
            } else if (optionIndex >= 0 && optionIndex < parts.size()) {
                PlaybackRepository.Part selected = parts.get(optionIndex);
                if (selected.page != currentPartPage) switchPart(selected);
            }
        }
        subMenuMode = false;
        scheduleHide(); overlay.invalidate();
    }

    private int currentQualityIndex() {
        if (stream == null) return 0;
        for (int i = 0; i < stream.qualities.size(); i++) if (stream.qualities.get(i).id == stream.currentQuality) return i;
        return 0;
    }

    private int optionCount() {
        if (menuIndex == 0) return 2;
        if (menuIndex == 1) return speeds.length;
        if (menuIndex == 3) return Math.max(1, isBangumi() ? episodes.size() : parts.size());
        return stream == null ? 1 : Math.max(1, stream.qualities.size());
    }

    private void togglePlayback() {
        cancelPendingAutoplay();
        if (!prepared && player != null) {
            if (pendingEpisode != null) {
                PlaybackRepository.Episode retry = pendingEpisode;
                pendingEpisode = null;
                errorText = "";
                switchEpisode(retry, false);
                return;
            }
            if (pendingPart != null) {
                PlaybackRepository.Part retry = pendingPart;
                pendingPart = null;
                errorText = "";
                switchPart(retry, false);
                return;
            }
        }
        if (!prepared || player == null) { showControls(false); return; }
        try {
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                endedHandled = false;
                if (isLive()) {
                    prepareSource(stream, 0);
                } else {
                    player.seekTo(0);
                    player.play();
                    applySpeed();
                    startProgressReporting();
                    startResumeSaving();
                }
            } else if (player.isPlaying()) {
                player.pause();
                stopProgressReporting();
                showFeedback("已暂停");
            } else {
                player.play();
                applySpeed();
                startProgressReporting();
                startResumeSaving();
                showFeedback("继续播放");
            }
        } catch (Exception ignored) { }
        resetDanmakuAnchor();
        showControls(false);
    }

    private void applySpeed() {
        if (player == null) return;
        try { player.setPlaybackSpeed(speeds[speedIndex]); }
        catch (Exception ignored) { }
        resetDanmakuAnchor();
    }

    private void showControls(boolean menu) { controlsVisible = true; menuMode = menu; subMenuMode = false; scheduleHide(); overlay.invalidate(); }

    private static String trimSpeedValue(float speed) {
        return speed == (int) speed ? ((int) speed) + "×" : speed + "×";
    }
    private void scheduleHide() {
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls,
                middleMode ? 10000 : infoMode ? 10000 : subMenuMode ? 10000 : menuMode ? 6500 : 4000);
    }

    private void startProgressReporting() {
        if (destroyed || isLive() || stream == null || !BiliAuthRepository.hasSession()) return;
        if (progressReporting) return;
        progressReporting = true;
        handler.removeCallbacks(progressReporter);
        handler.postDelayed(progressReporter, PROGRESS_INTERVAL_MS);
    }

    private void stopProgressReporting() {
        progressReporting = false;
        handler.removeCallbacks(progressReporter);
    }

    /** 用当前 stream/playbackUrl 回写一次进度（直播、未登录或未就绪时跳过）。 */
    private void reportProgressNow() {
        if (isLive() || stream == null || player == null || !BiliAuthRepository.hasSession()) return;
        long position = Math.max(0, player.getCurrentPosition());
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET || duration < 0) duration = 0;
        try {
            repository.reportProgress(stream, playbackUrl, position, duration);
        } catch (Exception ignored) { }
    }

    // ===== 本地续播进度 =====

    private void startResumeSaving() {
        if (destroyed || isLive() || stream == null) return;
        if (resumeSaving) return;
        resumeSaving = true;
        handler.removeCallbacks(resumeSaver);
        handler.postDelayed(resumeSaver, RESUME_SAVE_INTERVAL_MS);
    }

    private void stopResumeSaving() {
        resumeSaving = false;
        handler.removeCallbacks(resumeSaver);
    }

    private String resumeKey(PlaybackRepository.Stream target) {
        if (target == null) return "";
        if (target.epId > 0) return PlaybackResume.epKey(target.epId);
        if (target.cid > 0) return PlaybackResume.cidKey(target.cid);
        return PlaybackResume.urlKey(playbackUrl == null ? "" : playbackUrl);
    }

    private int storedResumePosition(PlaybackRepository.Stream target) {
        if (resumeStore == null) return 0;
        int stored = resumeStore.position(resumeKey(target));
        return stored > RESUME_MIN_POSITION_MS ? stored : 0;
    }

    private void saveLocalResume() {
        if (destroyed || resumeStore == null || stream == null || player == null
                || !prepared || isLive()) return;
        int position = currentPosition();
        long duration = player.getDuration();
        if (duration > 0 && duration != C.TIME_UNSET && position >= duration - RESUME_END_MARGIN_MS) {
            resumeStore.clear(resumeKey(stream));
            return;
        }
        resumeStore.save(resumeKey(stream), position);
    }

    private void clearLocalResume() {
        if (resumeStore != null && stream != null) resumeStore.clear(resumeKey(stream));
    }

    private void savePositionForResume() {
        if (player == null || isLive()) return;
        try {
            long position = player.getCurrentPosition();
            long duration = player.getDuration();
            if (position > 0 && (duration == C.TIME_UNSET || duration <= 0 || position < duration - 500)) {
                resumePosition = (int) Math.min(Integer.MAX_VALUE, position);
            }
        } catch (Exception ignored) { }
    }

    private int currentPosition() {
        if (player != null) {
            try {
                long position = player.getCurrentPosition();
                if (position > 0 && position < Integer.MAX_VALUE) return (int) position;
            } catch (Exception ignored) { }
        }
        return Math.max(0, resumePosition);
    }

    /** 弹幕位置：纯 SystemClock 外推（不做逐帧缓动），仅明显跳变时重锚；单调保护避免回退。 */
    private int smoothDanmakuPosition(int actualPosition) {
        if (player == null || !prepared) {
            danmakuAnchorValid = false;
            danmakuLastSmoothMs = actualPosition;
            return actualPosition;
        }
        long now = SystemClock.elapsedRealtime();
        if (!player.isPlaying()) {
            danmakuAnchorPositionMs = actualPosition;
            danmakuAnchorRealtimeMs = now;
            danmakuAnchorValid = true;
            danmakuLastSmoothMs = actualPosition;
            return actualPosition;
        }
        if (!danmakuAnchorValid) {
            danmakuAnchorPositionMs = actualPosition;
            danmakuAnchorRealtimeMs = now;
            danmakuAnchorValid = true;
            danmakuLastSmoothMs = actualPosition;
            return actualPosition;
        }
        float speed = speeds[Math.max(0, Math.min(speeds.length - 1, speedIndex))];
        long predicted = danmakuAnchorPositionMs
                + (long) ((now - danmakuAnchorRealtimeMs) * speed);
        // 只有明显跳变（seek/缓冲/切换）才重锚；正常播放不修改 anchor。
        if (Math.abs(predicted - actualPosition) > 1000) {
            danmakuAnchorPositionMs = actualPosition;
            danmakuAnchorRealtimeMs = now;
            danmakuLastSmoothMs = actualPosition;
            // 明显跳变后确保旧弹幕不会从屏幕中间冒出。
            danmakuVisibleFromMs = Math.max(danmakuVisibleFromMs, actualPosition);
            return actualPosition;
        }
        // 单调保护：播放中弹幕位置只增不减，避免任何左右回退。
        if (predicted < danmakuLastSmoothMs) predicted = danmakuLastSmoothMs;
        danmakuLastSmoothMs = predicted;
        return (int) Math.max(0, predicted);
    }

    private void resetDanmakuAnchor() {
        danmakuAnchorValid = false;
        danmakuLastSmoothMs = 0;
    }

    private void resetDanmakuAnchor(int positionMs) {
        danmakuAnchorPositionMs = Math.max(0, positionMs);
        danmakuAnchorRealtimeMs = SystemClock.elapsedRealtime();
        danmakuAnchorValid = true;
        danmakuLastSmoothMs = danmakuAnchorPositionMs;
    }

    private void releasePlayer() {
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
        prepared = false;
        awaitingReady = false;
    }

    @Override protected void onDestroy() {
        stopProgressReporting();
        stopResumeSaving();
        saveLocalResume();
        if (prepared) reportProgressNow();
        pendingAutoplayRecommended = false;
        destroyed = true;
        releasePlayer();
        detailRepository.shutdown();
        infoCoverLoading.clear();
        handler.removeCallbacks(hideControls);
        // 进度上报走异步线程池；延迟 shutdown 让 onDestroy 的最后一次上报有机会发出。
        final PlaybackRepository repo = repository;
        handler.postDelayed(repo::shutdown, PROGRESS_SHUTDOWN_DELAY_MS);
        super.onDestroy();
    }

    private final class PlayerOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Drawable[] actionIcons = new Drawable[4];
        private final Drawable playIcon;
        private final Drawable pauseIcon;
        // 约 8MB / 单条约 30KB，相当于 256 条左右的 LRU 上限，同时避免超长弹幕撑爆内存。
        private final LruCache<String, Bitmap> danmakuCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };

        PlayerOverlay() {
            super(PlayerActivity.this);
            text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            actionIcons[0] = getDrawable(android.R.drawable.ic_menu_view);
            actionIcons[1] = getDrawable(android.R.drawable.ic_media_ff);
            actionIcons[2] = getDrawable(android.R.drawable.ic_menu_manage);
            actionIcons[3] = getDrawable(android.R.drawable.ic_menu_sort_by_size);
            playIcon = getDrawable(android.R.drawable.ic_media_play);
            pauseIcon = getDrawable(android.R.drawable.ic_media_pause);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int actualPosition = prepared ? safePosition() : 0;
            if (danmakuEnabled && prepared) drawDanmaku(canvas, smoothDanmakuPosition(actualPosition));
            if (!stateText.isEmpty()) drawCenterMessage(canvas, stateText, false);
            if (!errorText.isEmpty()) drawCenterMessage(canvas, errorText, true);
            if (!feedbackText.isEmpty()) drawFeedback(canvas, feedbackText);
            if (infoMode) drawInfoPanel(canvas);
            else if (controlsVisible) drawControls(canvas, actualPosition);
            if (prepared && player != null && player.isPlaying() && danmakuEnabled && !danmaku.isEmpty()) {
                postInvalidateOnAnimation();
            }
        }

        private void drawCenterMessage(Canvas canvas, String value, boolean error) {
            float cx = getWidth() / 2f;
            if (error) {
                paint.setColor(0x82000000);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                box.set(cx - dp(360), getHeight() * .34f, cx + dp(360), getHeight() * .68f);
                paint.setColor(0xf2182029);
                canvas.drawRoundRect(box, dp(28), dp(28), paint);
                label(canvas, "播放暂时中断", cx, box.top + dp(62), dp(28), Color.WHITE, Paint.Align.CENTER, true);
                label(canvas, ellipsize(value, 32), cx, box.top + dp(108), dp(18), 0xffffa0ba, Paint.Align.CENTER, false);
                paint.setColor(0xfffb5c91);
                canvas.drawRoundRect(cx - dp(135), box.top + dp(142), cx + dp(135), box.top + dp(202), dp(30), dp(30), paint);
                label(canvas, "OK  重新加载", cx, box.top + dp(181), dp(18), Color.WHITE, Paint.Align.CENTER, true);
                label(canvas, "返回键退出播放", cx, box.bottom - dp(26), dp(14), 0xffaeb7be, Paint.Align.CENTER, false);
            } else {
                paint.setColor(Color.argb(215, 10, 13, 18));
                box.set(cx - dp(235), getHeight() * .445f, cx + dp(235), getHeight() * .555f);
                canvas.drawRoundRect(box, dp(28), dp(28), paint);
                label(canvas, value, cx, getHeight() * .515f, dp(21), Color.WHITE, Paint.Align.CENTER, true);
                paint.setColor(0xfffb5c91);
                canvas.drawRoundRect(cx - dp(90), box.bottom - dp(12), cx + dp(90), box.bottom - dp(7), dp(3), dp(3), paint);
            }
        }

        private void drawFeedback(Canvas canvas, String value) {
            float cx = getWidth() / 2f;
            float top = getHeight() * .21f;
            text.setTextSize(dp(18));
            float width = Math.min(dp(440), Math.max(dp(180), text.measureText(value) + dp(54)));
            paint.setColor(0xe610171e);
            canvas.drawRoundRect(cx - width / 2f, top, cx + width / 2f, top + dp(58), dp(29), dp(29), paint);
            label(canvas, ellipsize(value, 28), cx, top + dp(37), dp(18), Color.WHITE, Paint.Align.CENTER, true);
        }

        private void drawControls(Canvas canvas, int position) {
            float h = getHeight(), w = getWidth();
            paint.setShader(new android.graphics.LinearGradient(0, h * .58f, 0, h, Color.TRANSPARENT, 0xe6000000, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * .52f, w, h, paint); paint.setShader(null);
            label(canvas, stream == null ? "哔哩哔哩" : ellipsize(playbackTitle(), 30), dp(64), h - dp(154), dp(25), Color.WHITE, Paint.Align.LEFT, true);
            boolean live = isLive();
            long duration = playbackDuration();
            long shownPosition = Math.max(0, position);
            float left = dp(122), right = w - dp(64), barY = h - dp(112);
            float barH = middleMode ? dp(10) : dp(6);
            paint.setColor(middleMode ? 0xccffffff : 0x66ffffff);
            canvas.drawRoundRect(left, barY, right, barY + barH, dp(3), dp(3), paint);
            if (!live && duration > 0 && player != null) {
                long buffered = 0;
                try { buffered = Math.max(shownPosition, player.getBufferedPosition()); }
                catch (Exception ignored) { }
                float bufferedRight = left + (right - left) * Math.min(duration, buffered) / (float) duration;
                paint.setColor(0x99ffffff);
                canvas.drawRoundRect(left, barY, bufferedRight, barY + barH, dp(3), dp(3), paint);
            }
            if (!live && duration > 0) {
                paint.setColor(0xfffb5c91); canvas.drawRoundRect(left, barY,
                        left + (right - left) * shownPosition / (float) duration, barY + barH, dp(3), dp(3), paint);
                float knobX = left + (right - left) * shownPosition / (float) duration;
                paint.setColor(Color.WHITE);
                canvas.drawCircle(knobX, barY + barH / 2f, middleMode ? dp(10) : dp(7), paint);
            }
            Drawable playbackIcon = player != null && player.isPlaying() ? pauseIcon : playIcon;
            if (playbackIcon != null) {
                playbackIcon.setTint(Color.WHITE);
                playbackIcon.setBounds((int) dp(66), (int) (barY - dp(19)), (int) dp(104), (int) (barY + dp(25)));
                playbackIcon.draw(canvas);
            }
            if (middleMode) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(left - dp(12), barY - dp(14), right + dp(12), barY + barH + dp(20),
                        dp(12), dp(12), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (live) {
                label(canvas, "LIVE", dp(64), h - dp(78), dp(15), 0xfffb5c91, Paint.Align.LEFT, true);
            } else {
                label(canvas, time(shownPosition), dp(64), h - dp(78), dp(15), Color.WHITE, Paint.Align.LEFT, middleMode);
                label(canvas, duration > 0 ? time(duration) : "--:--", right, h - dp(78), dp(15),
                        middleMode ? Color.WHITE : 0xffd8dce0, Paint.Align.RIGHT, middleMode);
            }
            String hint = middleMode ? "←  快退 10 秒      →  快进 10 秒      OK  暂停/继续      ↑ 上方功能 / ↓ 下方信息"
                    : !menuMode ? (live ? "↑↓  呼出功能栏      确认键  暂停/继续"
                            : "←  快退 10 秒      →  快进 10 秒      ↑↓  呼出功能栏")
                    : subMenuMode ? "←→ / ↑↓  选择具体选项      确认键应用      返回键回上一级"
                    : "←→  切换功能按钮      确认键进入选项      返回键关闭";
            label(canvas, hint, left, h - dp(38), dp(15), middleMode ? Color.WHITE : 0xffd8dce0, Paint.Align.LEFT, middleMode);
            if (menuMode) drawMenu(canvas);
        }

        private void drawMenu(Canvas canvas) {
            int count = functionCount();
            boolean four = count == 4;
            float buttonW = four ? dp(90) : dp(104), buttonH = dp(66), gap = dp(12);
            float right = getWidth() - dp(64), top = getHeight() - dp(224);
            float left = right - buttonW * count - gap * (count - 1);
            String[] names = new String[count];
            String[] values = new String[count];
            names[0] = "弹幕"; values[0] = danmakuEnabled ? "开" : "关";
            names[1] = "倍速"; values[1] = trimSpeed(speeds[speedIndex]);
            names[2] = "清晰度"; values[2] = four ? shortQualityName() : qualityName();
            if (four) { names[3] = fourthMenuTitle(); values[3] = ellipsize(fourthMenuValue(), 6); }
            for (int i = 0; i < count; i++) {
                float x = left + i * (buttonW + gap);
                paint.setColor(i == menuIndex ? 0xfffb5c91 : 0xdc0a0f16);
                canvas.drawRoundRect(x, top, x + buttonW, top + buttonH, dp(18), dp(18), paint);
                if (i == menuIndex) {
                    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setColor(Color.WHITE);
                    canvas.drawRoundRect(x - dp(3), top - dp(3), x + buttonW + dp(3), top + buttonH + dp(3), dp(20), dp(20), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                Drawable icon = actionIcons[i];
                if (icon != null) {
                    icon.setTint(Color.WHITE);
                    icon.setBounds((int) (x + dp(11)), (int) (top + dp(18)), (int) (x + dp(39)), (int) (top + dp(46)));
                    icon.draw(canvas);
                }
                float textX = x + dp(45);
                label(canvas, names[i], textX, top + dp(27), dp(14), Color.WHITE, Paint.Align.LEFT, true);
                label(canvas, ellipsize(values[i], four ? 5 : 7), textX, top + dp(48),
                        dp(11), 0xffe8ebee, Paint.Align.LEFT, false);
            }
            if (subMenuMode) drawSubMenu(canvas, right, top);
        }

        private void drawSubMenu(Canvas canvas, float right, float buttonTop) {
            if (menuIndex == 3) {
                if (isBangumi()) drawEpisodeSubMenu(canvas, right, buttonTop);
                else drawPartSubMenu(canvas, right, buttonTop);
                return;
            }
            String title;
            String[] options;
            int active;
            if (menuIndex == 0) {
                title = "弹幕"; options = new String[]{"开", "关"}; active = danmakuEnabled ? 0 : 1;
            } else if (menuIndex == 1) {
                title = "倍速"; options = new String[speeds.length];
                for (int i = 0; i < speeds.length; i++) options[i] = trimSpeed(speeds[i]);
                active = speedIndex;
            } else {
                title = "清晰度";
                int size = stream == null ? 0 : stream.qualities.size();
                options = new String[Math.max(1, size)];
                if (size == 0) options[0] = "自动";
                else for (int i = 0; i < size; i++) options[i] = stream.qualities.get(i).name;
                active = currentQualityIndex();
            }
            float gap = dp(10);
            float chipW = Math.max(dp(92), Math.min(dp(154), (dp(760) - gap * (options.length - 1)) / options.length));
            float chipsW = chipW * options.length + gap * (options.length - 1);
            float width = Math.min(dp(820), Math.max(dp(330), chipsW + dp(40)));
            float height = dp(132);
            float top = buttonTop - height - dp(18);
            float left = right - width;
            paint.setColor(0xf21a1e25);
            canvas.drawRoundRect(left, top, right, top + height, dp(26), dp(26), paint);
            label(canvas, title + "选项", left + dp(22), top + dp(37), dp(18), Color.WHITE, Paint.Align.LEFT, true);
            float chipLeft = right - dp(20) - chipsW;
            for (int i = 0; i < options.length; i++) {
                float x = chipLeft + i * (chipW + gap);
                float y = top + dp(52);
                if (i == optionIndex) {
                    paint.setColor(0xfffb5c91);
                    canvas.drawRoundRect(x, y, x + chipW, y + dp(54), dp(15), dp(15), paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(Color.WHITE);
                    canvas.drawRoundRect(x - dp(2), y - dp(2), x + chipW + dp(2), y + dp(56), dp(17), dp(17), paint);
                    paint.setStyle(Paint.Style.FILL);
                } else {
                    paint.setColor(0x6610171e);
                    canvas.drawRoundRect(x, y, x + chipW, y + dp(54), dp(15), dp(15), paint);
                }
                label(canvas, ellipsize(options[i], 10), x + chipW / 2f, y + dp(34), dp(16),
                        Color.WHITE, Paint.Align.CENTER, i == optionIndex);
                if (i == active) {
                    paint.setColor(i == optionIndex ? Color.WHITE : 0xffff9dbb);
                    canvas.drawCircle(x + chipW - dp(11), y + dp(11), dp(4), paint);
                }
            }
            label(canvas, "←→ 选择  ·  OK 应用", right - dp(22), top + dp(36), dp(12), 0xffb8bec5, Paint.Align.RIGHT, false);
        }

        /** 选集/分P 二级菜单：可见 MENU_VISIBLE_ROWS 行，随 optionIndex 滚动。 */
        private void drawEpisodeSubMenu(Canvas canvas, float right, float buttonTop) {
            List<String> rows = new ArrayList<>(episodes.size());
            for (PlaybackRepository.Episode episode : episodes) rows.add(episodeText(episode));
            String empty = episodesLoading ? "正在加载选集…"
                    : (episodesError.isEmpty() ? "暂无选集" : ellipsize(episodesError, 22));
            drawListSubMenu(canvas, right, buttonTop, "选集", rows,
                    optionIndex, indexOfEpisode(currentEpId), empty, "集");
        }

        private void drawPartSubMenu(Canvas canvas, float right, float buttonTop) {
            List<String> rows = new ArrayList<>(parts.size());
            for (PlaybackRepository.Part part : parts) rows.add(partText(part));
            String empty = partsLoading ? "正在加载分P…"
                    : (partsError.isEmpty() ? "暂无分P" : ellipsize(partsError, 22));
            drawListSubMenu(canvas, right, buttonTop, "分P", rows,
                    optionIndex, indexOfPart(currentPartPage), empty, "P");
        }

        private void drawListSubMenu(Canvas canvas, float right, float buttonTop, String title,
                                     List<String> rows, int selected, int current,
                                     String emptyText, String unit) {
            int total = rows.size();
            int first = total == 0 ? 0 : listFirstVisible(total, selected);
            int visibleCount = total == 0 ? 1 : Math.min(MENU_VISIBLE_ROWS, total - first);
            float width = dp(560), rowH = dp(54);
            float height = dp(76) + visibleCount * rowH + dp(38);
            float top = buttonTop - height - dp(18);
            paint.setColor(0xf21a1e25);
            canvas.drawRoundRect(right - width, top, right, top + height, dp(26), dp(26), paint);
            label(canvas, title, right - width + dp(24), top + dp(43), dp(19), Color.WHITE, Paint.Align.LEFT, true);
            if (total == 0) {
                label(canvas, emptyText, right - width + dp(28), top + dp(61) + dp(34), dp(17), 0xffd8dce0, Paint.Align.LEFT, false);
            } else {
                for (int i = 0; i < visibleCount; i++) {
                    int index = first + i;
                    float y = top + dp(61) + i * rowH;
                    if (index == selected) {
                        paint.setColor(0xfffb5c91);
                        canvas.drawRoundRect(right - width + dp(12), y, right - dp(12), y + rowH - dp(7), dp(15), dp(15), paint);
                    }
                    label(canvas, ellipsize(rows.get(index), 24), right - width + dp(28), y + dp(34),
                            dp(17), Color.WHITE, Paint.Align.LEFT, index == selected);
                    if (index == current) {
                        label(canvas, "当前", right - dp(28), y + dp(34), dp(13),
                                index == selected ? Color.WHITE : 0xffff9dbb, Paint.Align.RIGHT, false);
                    }
                }
            }
            String footer = total == 0 ? "返回键回上一级"
                    : "共 " + total + " " + unit + "  ·  第 " + (first + 1) + "-" + (first + visibleCount) + " " + unit + "  ·  确认应用";
            label(canvas, footer, right - dp(20), top + height - dp(15), dp(12), 0xffb8bec5, Paint.Align.RIGHT, false);
        }

        private int listFirstVisible(int total, int selected) {
            if (total <= MENU_VISIBLE_ROWS) return 0;
            int first = selected - MENU_VISIBLE_ROWS / 2;
            return Math.max(0, Math.min(first, total - MENU_VISIBLE_ROWS));
        }

        private String episodeText(PlaybackRepository.Episode episode) {
            String index = episode.index == null ? "" : episode.index.trim();
            String title = episode.title == null ? "" : episode.title.trim();
            if (index.isEmpty()) return title;
            if (title.isEmpty()) return index;
            return index + "  " + title;
        }

        private String partText(PlaybackRepository.Part part) {
            String title = part.title == null ? "" : part.title.trim();
            return title.isEmpty() ? "P" + part.page : "P" + part.page + "  " + title;
        }

        private String ellipsize(String value, int max) {
            if (value == null) return "";
            return value.length() > max ? value.substring(0, Math.max(1, max - 1)) + "…" : value;
        }

        // ===== 下方信息区：简介 / 选集 / 推荐 =====

        private void drawInfoPanel(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float panelTop = h * 0.56f;
            paint.setShader(new android.graphics.LinearGradient(0, h * 0.40f, 0, h,
                    Color.TRANSPARENT, 0xf2000000, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * 0.40f, w, h, paint);
            paint.setShader(null);
            paint.setColor(0xcc0b0f14);
            canvas.drawRoundRect(dp(28), panelTop, w - dp(28), h - dp(20), dp(22), dp(22), paint);

            float tabX = dp(56), tabY = panelTop + dp(26);
            for (int i = 0; i < infoTabCount(); i++) {
                String title = infoTabTitle(i);
                boolean selected = i == infoTab;
                boolean focused = infoFocusRow == 0 && i == infoTab;
                text.setTextSize(dp(19));
                text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
                float tabW = Math.max(dp(88), text.measureText(title) + dp(44));
                paint.setColor(selected ? 0xfffb5c91 : 0x33ffffff);
                canvas.drawRoundRect(tabX, tabY, tabX + tabW, tabY + dp(46), dp(23), dp(23), paint);
                if (focused) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(Color.WHITE);
                    canvas.drawRoundRect(tabX - dp(3), tabY - dp(3), tabX + tabW + dp(3), tabY + dp(49),
                            dp(25), dp(25), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                label(canvas, title, tabX + dp(22), tabY + dp(31), dp(19), Color.WHITE,
                        Paint.Align.LEFT, selected);
                tabX += tabW + dp(16);
            }

            float contentTop = tabY + dp(70);
            if (infoTab == 0) drawInfoIntro(canvas, contentTop);
            else drawInfoCards(canvas, contentTop);

            String hint = infoFocusRow == 0
                    ? "←→ 切换 Tab    ↓ 进入列表    ↑ 功能栏    Back 关闭"
                    : "←→ 选择    OK 播放    ↑ 返回 Tab    Back 关闭";
            label(canvas, hint, dp(56), h - dp(44), dp(14), 0xffb8bec5, Paint.Align.LEFT, false);
        }

        private void drawInfoIntro(Canvas canvas, float top) {
            String desc = infoIntroText();
            drawWrappedText(canvas, desc, dp(56), top + dp(30), getWidth() - dp(112),
                    dp(30), 4, dp(18), 0xffd8dce0);
        }

        private void drawInfoCards(Canvas canvas, float top) {
            if (infoCards.isEmpty()) {
                label(canvas, infoCardStatusText(), getWidth() / 2f, top + dp(70), dp(19),
                        0xffd8dce0, Paint.Align.CENTER, false);
                return;
            }
            int total = infoCards.size();
            int visible = Math.min(INFO_VISIBLE_CARDS, total);
            int first = cardFirstVisible(total, infoCardIndex);
            float cardW = dp(272), gap = dp(16), coverH = dp(153);
            float coverTop = top + dp(8);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < visible; i++) {
                    int index = first + i;
                    boolean focused = infoFocusRow == 1 && index == infoCardIndex;
                    if ((pass == 0 && focused) || (pass == 1 && !focused)) continue;
                    InfoCard card = infoCards.get(index);
                    float baseX = dp(56) + i * (cardW + gap);
                    float grow = focused ? dp(9) : 0;
                    float x = baseX - grow;
                    RectF coverRect = new RectF(x, coverTop - grow,
                            baseX + cardW + grow, coverTop + coverH + grow);
                    paint.setColor(0xcc1a1f27);
                    canvas.drawRoundRect(coverRect, dp(12), dp(12), paint);
                    Bitmap bitmap = infoBitmaps.get(card.cover);
                    if (bitmap != null) drawCoverBitmap(canvas, bitmap, coverRect);
                    else ensureInfoCover(card.cover);
                    if (infoCardIsCurrent(card)) {
                        paint.setColor(0xcc000000);
                        canvas.drawRoundRect(coverRect.right - dp(52), coverRect.top + dp(8),
                                coverRect.right - dp(8), coverRect.top + dp(30), dp(10), dp(10), paint);
                        label(canvas, "当前", coverRect.right - dp(30), coverRect.top + dp(25),
                                dp(12), 0xffff9dbb, Paint.Align.CENTER, true);
                    }
                    if (focused) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(4));
                        paint.setColor(Color.WHITE);
                        canvas.drawRoundRect(coverRect.left - dp(4), coverRect.top - dp(4), coverRect.right + dp(4),
                                coverRect.bottom + dp(60), dp(15), dp(15), paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    label(canvas, ellipsize(card.title, 16), x, coverRect.bottom + dp(26), dp(16),
                            Color.WHITE, Paint.Align.LEFT, focused);
                    label(canvas, ellipsize(card.subtitle, 12), x, coverRect.bottom + dp(48), dp(13),
                            0xff9aa4ae, Paint.Align.LEFT, false);
                }
            }
        }

        private int cardFirstVisible(int total, int selected) {
            if (total <= INFO_VISIBLE_CARDS) return 0;
            int first = selected - INFO_VISIBLE_CARDS / 2;
            return Math.max(0, Math.min(first, total - INFO_VISIBLE_CARDS));
        }

        private void drawCoverBitmap(Canvas canvas, Bitmap bitmap, RectF dst) {
            if (bitmap == null || bitmap.isRecycled()) return;
            paint.setAlpha(255);
            float dstRatio = dst.width() / dst.height();
            float srcRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            Rect src = new Rect();
            if (srcRatio > dstRatio) {
                int width = Math.round(bitmap.getHeight() * dstRatio);
                int left = (bitmap.getWidth() - width) / 2;
                src.set(left, 0, left + width, bitmap.getHeight());
            } else {
                int height = Math.round(bitmap.getWidth() / dstRatio);
                int top = (bitmap.getHeight() - height) / 2;
                src.set(0, top, bitmap.getWidth(), top + height);
            }
            canvas.drawBitmap(bitmap, src, dst, paint);
        }

        private void drawWrappedText(Canvas canvas, String value, float x, float y, float maxWidth,
                                     float lineHeight, int maxLines, float size, int color) {
            String content = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
            text.setStyle(Paint.Style.FILL);
            text.setTextSize(size);
            text.setColor(color);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            int line = 0;
            int start = 0;
            while (start < content.length() && line < maxLines) {
                int end = start;
                float width = 0;
                while (end < content.length()) {
                    float charWidth = text.measureText(content.substring(end, end + 1));
                    if (width + charWidth > maxWidth) break;
                    width += charWidth;
                    end++;
                }
                if (end == start) end = Math.min(content.length(), start + 1);
                String lineText = content.substring(start, end);
                if (line == maxLines - 1 && end < content.length()) lineText = lineText + "…";
                canvas.drawText(lineText, x, y + line * lineHeight, text);
                start = end;
                line++;
            }
        }

        private void drawDanmaku(Canvas canvas, long position) {
            final long travel = 8000;
            final int lanes = 8;
            final float laneHeight = dp(64);
            final float startY = dp(36);
            final float safeLeft = dp(28);
            final float safeRight = getWidth() - dp(28);
            final float gap = dp(42);
            float[] laneRight = new float[lanes];
            java.util.Arrays.fill(laneRight, -Float.MAX_VALUE);
            List<DanmakuFrame> frames = new ArrayList<>();
            int save = canvas.save();
            canvas.clipRect(safeLeft, dp(22), safeRight, Math.min(getHeight() * .59f, startY + lanes * laneHeight));
            for (int i = 0; i < danmaku.size(); i++) {
                PlaybackRepository.Danmaku item = danmaku.get(i);
                if (item.timeMs < danmakuVisibleFromMs - 100) continue;
                long age = position - item.timeMs;
                if (age < 0 || age > travel) continue;
                Bitmap bitmap = danmakuBitmap(item.text);
                if (bitmap == null) continue;
                float x = safeRight - (safeRight - safeLeft + bitmap.getWidth()) * age / (float) travel;
                frames.add(new DanmakuFrame(bitmap, x));
            }
            java.util.Collections.sort(frames, (left, right) -> Float.compare(left.x, right.x));
            int visible = 0;
            for (DanmakuFrame frame : frames) {
                if (visible >= 28) break;
                Bitmap bitmap = frame.bitmap;
                float x = frame.x;
                int lane = -1;
                for (int candidate = 0; candidate < lanes; candidate++) {
                    if (x > laneRight[candidate] + gap) { lane = candidate; break; }
                }
                if (lane < 0) continue;
                float y = startY + lane * laneHeight;
                canvas.drawBitmap(bitmap, x, y, null);
                laneRight[lane] = x + bitmap.getWidth();
                visible++;
            }
            canvas.restoreToCount(save);
        }

        private final class DanmakuFrame {
            final Bitmap bitmap;
            final float x;

            DanmakuFrame(Bitmap bitmap, float x) {
                this.bitmap = bitmap;
                this.x = x;
            }
        }

        /** 预渲染弹幕文字（填充 + 描边）为 Bitmap，LRU 缓存，避免每帧 measureText/双 drawText。 */
        private Bitmap danmakuBitmap(String value) {
            if (value == null || value.isEmpty()) return null;
            Bitmap cached = danmakuCache.get(value);
            if (cached != null) return cached;
            String content = value.length() > 34 ? value.substring(0, 34) + "…" : value;
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bitmapPaint.setTextSize(dp(34));
            bitmapPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            float textWidth = bitmapPaint.measureText(content);
            Paint.FontMetrics metrics = bitmapPaint.getFontMetrics();
            int pad = Math.max(2, (int) Math.ceil(dp(8)));
            int bitmapWidth = Math.max(1, (int) Math.ceil(textWidth) + pad * 2);
            int bitmapHeight = Math.max(1, (int) Math.ceil(metrics.descent - metrics.ascent) + pad * 2);
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(bitmap);
            float baseline = pad - metrics.ascent;
            bitmapPaint.setStyle(Paint.Style.STROKE);
            bitmapPaint.setStrokeWidth(dp(6));
            bitmapPaint.setColor(0xcc000000);
            bitmapCanvas.drawText(content, pad, baseline, bitmapPaint);
            bitmapPaint.setStyle(Paint.Style.FILL);
            bitmapPaint.setColor(Color.WHITE);
            bitmapCanvas.drawText(content, pad, baseline, bitmapPaint);
            danmakuCache.put(value, bitmap);
            return bitmap;
        }

        private void clearDanmakuCache() {
            danmakuCache.evictAll();
        }

        private void label(Canvas c, String value, float x, float y, float size, int color, Paint.Align align, boolean bold) {
            text.setStyle(Paint.Style.FILL); text.setTextSize(size); text.setColor(color); text.setTextAlign(align);
            text.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            c.drawText(value, x, y, text);
        }

        private int safePosition() {
            if (player == null) return 0;
            try { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, player.getCurrentPosition())); }
            catch (Exception e) { return 0; }
        }
        private String shortQualityName() {
            String full = qualityName();
            int space = full.lastIndexOf(' ');
            return space >= 0 && space + 1 < full.length() ? full.substring(space + 1) : full;
        }

        private String qualityName() {
            if (stream == null) return "自动";
            for (PlaybackRepository.Quality quality : stream.qualities) if (quality.id == stream.currentQuality) return quality.name;
            return "自动";
        }
        private String trimSpeed(float speed) { return speed == (int) speed ? ((int) speed) + "×" : speed + "×"; }
        private String time(long millis) {
            long total = Math.max(0, millis / 1000);
            return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
        }
        private float dp(float value) { return value * Math.min(getWidth() / 1920f, getHeight() / 1080f); }
    }
}
