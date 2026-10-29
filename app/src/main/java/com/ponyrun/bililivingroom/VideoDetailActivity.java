package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 原生视频详情页：标题/UP/元信息/简介 + 右侧封面 + 操作按钮 + 底部推荐横排。 */
public final class VideoDetailActivity extends Activity {
    static final String EXTRA_TARGET = "video_target";
    static final String EXTRA_BVID = "video_bvid";

    private final BiliRepository repository = new BiliRepository();

    private PlaybackSettings settings;
    private boolean destroyed;
    private boolean loading;
    private String target = "";
    private String bvid = "";
    private int initialPage = 1;
    private long aid;
    private BiliRepository.VideoDetail detail;
    private float scale;

    private FrameLayout root;
    private ImageView backdrop;
    private ImageView cover;
    private ImageView avatar;
    private TextView durationBadge;
    private TextView titleView;
    private TextView upNameView;
    private TextView upFansView;
    private TextView upFocusBadge;
    private TextView metaView;
    private TextView descView;
    private TextView statusView;
    private TextView playButton;
    private TextView watchLaterButton;
    private TextView likeButton;
    private TextView coinButton;
    private TextView favoriteButton;
    private LinearLayout relatedSection;
    private LinearLayout relatedRow;
    private TextView sectionTitleView;
    private LinearLayout upRow;
    private LinearLayout topSection;
    private LinearLayout buttonsRow;
    private boolean showingParts;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = Math.min(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f);
        settings = new PlaybackSettings(this);
        target = getIntent().getStringExtra(EXTRA_TARGET);
        if (target == null) target = "";
        initialPage = extractPageParam(target);
        target = target.trim();
        if (target.isEmpty()) {
            String extraBvid = getIntent().getStringExtra(EXTRA_BVID);
            if (extraBvid != null) target = extraBvid.trim();
        }
        buildUi();
        loadDetail();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xff0b1117);

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(.78f);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        GradientDrawable cinematicScrim = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xe80b1117, 0xc00b1117, 0x8f0b1117});
        scrim.setBackground(cinematicScrim);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View bottomScrim = new View(this);
        GradientDrawable verticalScrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000b1117, 0x260b1117, 0xe60b1117});
        bottomScrim.setBackground(verticalScrim);
        root.addView(bottomScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(72), (int) dp(72), (int) dp(72), (int) dp(60));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        topSection = new LinearLayout(this);
        topSection.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(topSection, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        topSection.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        titleView = text("", 52, Color.WHITE, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        upRow = new LinearLayout(this);
        upRow.setOrientation(LinearLayout.HORIZONTAL);
        upRow.setGravity(Gravity.CENTER_VERTICAL);
        upRow.setPadding((int) dp(8), (int) dp(14), (int) dp(8), (int) dp(6));
        upRow.setFocusable(true);
        upRow.setClickable(true);
        upRow.setBackground(upRowBackground(false));
        upRow.setContentDescription("进入 UP 主主页");
        upRow.setOnClickListener(v -> openUserSpace());
        left.addView(upRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xff202a33);
        avatar.setBackground(oval);
        avatar.setOnClickListener(v -> openUserSpace());
        avatar.setFocusable(false);
        upRow.addView(avatar, new LinearLayout.LayoutParams((int) dp(64), (int) dp(64)));
        upNameView = text("", 24, Color.WHITE, true);
        upNameView.setPadding((int) dp(16), 0, 0, 0);
        upNameView.setOnClickListener(v -> openUserSpace());
        upNameView.setFocusable(false);
        upRow.addView(upNameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        upFansView = text("", 18, 0xff96a8b0, false);
        upFansView.setPadding((int) dp(16), 0, 0, 0);
        upRow.addView(upFansView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView upHint = text("› 进入主页", 16, 0xffff8fb0, false);
        upHint.setPadding((int) dp(20), 0, 0, 0);
        upHint.setOnClickListener(v -> openUserSpace());
        upHint.setFocusable(false);
        upRow.addView(upHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        upFocusBadge = text("已选中", 14, 0xff1b2229, true);
        upFocusBadge.setPadding((int) dp(10), (int) dp(3), (int) dp(10), (int) dp(3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xffff8fb0);
        badgeBg.setCornerRadius(dp(20));
        upFocusBadge.setBackground(badgeBg);
        upFocusBadge.setVisibility(View.GONE);
        upRow.addView(upFocusBadge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        upRow.setBackground(upRowBackground(false));
        upRow.setOnFocusChangeListener((v, hasFocus) -> {
            upRow.setBackground(upRowBackground(hasFocus));
            upNameView.setTextColor(hasFocus ? 0xffff8fb0 : Color.WHITE);
            upHint.setTextColor(hasFocus ? Color.WHITE : 0xffff8fb0);
            upFocusBadge.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        });

        metaView = text("", 18, 0xffa9b8c0, false);
        metaView.setPadding(0, (int) dp(6), 0, (int) dp(14));
        left.addView(metaView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        descView = text("", 20, 0xffc2ced5, false);
        descView.setMaxLines(3);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        descView.setLineSpacing(dp(5), 1f);
        left.addView(descView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout coverBox = new FrameLayout(this);
        GradientDrawable coverSurface = new GradientDrawable();
        coverSurface.setColor(0xff17212b);
        coverSurface.setCornerRadius(dp(20));
        coverBox.setBackground(coverSurface);
        coverBox.setClipToOutline(true);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams((int) dp(780), (int) dp(438));
        coverParams.leftMargin = (int) dp(42);
        topSection.addView(coverBox, coverParams);
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverBox.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        durationBadge = text("", 15, Color.WHITE, true);
        durationBadge.setBackgroundColor(0xb3000000);
        durationBadge.setPadding((int) dp(10), (int) dp(4), (int) dp(10), (int) dp(4));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
        badgeParams.setMargins(0, 0, (int) dp(10), (int) dp(10));
        coverBox.addView(durationBadge, badgeParams);

        buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setPadding(0, (int) dp(24), 0, (int) dp(14));
        content.addView(buttonsRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playButton = actionButton("播放", true);
        watchLaterButton = actionButton("稍后再看", false);
        likeButton = actionButton("点赞", false);
        coinButton = actionButton("投币", false);
        favoriteButton = actionButton("收藏", false);
        setButtonIcon(playButton, R.drawable.ic_tv_play);
        setButtonIcon(watchLaterButton, R.drawable.ic_tv_watch_later);
        setButtonIcon(likeButton, R.drawable.ic_tv_like);
        setButtonIcon(coinButton, R.drawable.ic_tv_coin);
        setButtonIcon(favoriteButton, R.drawable.ic_tv_favorite);
        addButton(buttonsRow, playButton);
        addButton(buttonsRow, watchLaterButton);
        addButton(buttonsRow, likeButton);
        addButton(buttonsRow, coinButton);
        addButton(buttonsRow, favoriteButton);

        playButton.setOnClickListener(v -> openPlayer());
        // 明确焦点上下关系：播放 ↑→ UP 行，UP 行 ↓→ 播放（避免几何搜索落到投币）。
        upRow.setId(View.generateViewId());
        playButton.setId(View.generateViewId());
        upRow.setNextFocusDownId(playButton.getId());
        playButton.setNextFocusUpId(upRow.getId());
        watchLaterButton.setOnClickListener(v -> runWatchLater());
        likeButton.setOnClickListener(v -> runLike());
        coinButton.setOnClickListener(v -> runCoin());
        favoriteButton.setOnClickListener(v -> runFavorite());

        relatedSection = new LinearLayout(this);
        relatedSection.setOrientation(LinearLayout.VERTICAL);
        relatedSection.setVisibility(View.GONE);
        content.addView(relatedSection, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView relatedTitle = text("推荐", 28, Color.WHITE, true);
        relatedTitle.setPadding(0, (int) dp(20), 0, (int) dp(12));
        sectionTitleView = relatedTitle;
        relatedSection.addView(relatedTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalScrollView relatedScroll = new HorizontalScrollView(this);
        relatedScroll.setHorizontalScrollBarEnabled(false);
        relatedRow = new LinearLayout(this);
        relatedRow.setOrientation(LinearLayout.HORIZONTAL);
        relatedScroll.addView(relatedRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        relatedSection.addView(relatedScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = text("正在加载视频详情…", 20, 0xffb7c2c8, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding((int) dp(38), (int) dp(30), (int) dp(38), (int) dp(30));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(190));
        statusParams.setMargins((int) dp(390), (int) dp(280), (int) dp(390), (int) dp(20));
        content.addView(statusView, statusParams);
        setStatusState("正在加载视频详情…", "请稍候，正在获取视频信息", false, false);
        setDetailContentVisible(false);
        statusView.setOnClickListener(v -> loadDetail());

        setContentView(root);
    }

    private void loadDetail() {
        if (loading || destroyed) return;
        if (target.isEmpty()) {
            setStatusState("无效的视频地址", "请返回上一页后重新进入", false, true);
            return;
        }
        loading = true;
        setDetailContentVisible(false);
        relatedSection.setVisibility(View.GONE);
        relatedRow.removeAllViews();
        sectionTitleView.setText("推荐");
        showingParts = false;
        setStatusState("正在加载视频详情…", "请稍候，正在获取视频信息", false, false);
        repository.loadVideoDetail(target, (detail, error) -> {
            if (destroyed) return;
            loading = false;
            if (detail == null) {
                setStatusState(error == null || error.isEmpty() ? "视频详情加载失败" : error,
                        "检查网络后按 OK 重新加载", true, true);
                return;
            }
            this.detail = detail;
            aid = detail.aid;
            bvid = detail.bvid == null ? "" : detail.bvid;
            setDetailContentVisible(true);
            renderDetail(detail);
            statusView.setVisibility(View.GONE);
            playButton.requestFocus();
            if (detail.parts != null && detail.parts.size() > 1) {
                renderParts(detail);
            } else {
                loadRelated(detail.bvid);
            }
            if (detail.ownerMid > 0) {
                repository.loadUserCard(detail.ownerMid, (card, cardError) -> {
                    if (!destroyed && card != null) upFansView.setText(formatCount(card.fans) + " 粉丝");
                });
            }
        });
    }

    private void renderDetail(BiliRepository.VideoDetail info) {
        titleView.setText(info.title == null ? "" : info.title);
        upNameView.setText(info.ownerName == null || info.ownerName.isEmpty() ? "B 站 UP 主" : info.ownerName);
        upFansView.setText("");
        metaView.setText(formatDate(info.pubdate) + "  ·  " + formatCount(info.view) + " 播放  ·  "
                + formatCount(info.danmaku) + " 弹幕  ·  " + formatCount(info.reply) + " 评论");
        descView.setText(info.desc == null ? "" : info.desc);
        durationBadge.setText(formatDuration(info.durationSec));
        likeButton.setText("点赞 " + formatCount(info.like));
        coinButton.setText("投币 " + formatCount(info.coin));
        favoriteButton.setText("收藏 " + formatCount(info.favorite));
        if (info.ownerFace != null && !info.ownerFace.isEmpty()) {
            repository.loadImage(info.ownerFace, bitmap -> {
                if (!destroyed && bitmap != null) avatar.setImageBitmap(bitmap);
            });
        }
        if (info.pic != null && !info.pic.isEmpty()) {
            repository.loadImage(info.pic, bitmap -> {
                if (destroyed || bitmap == null) return;
                cover.setImageBitmap(bitmap);
                backdrop.setImageBitmap(bitmap);
            });
        }
    }

    private void loadRelated(String videoBvid) {
        if (videoBvid == null || videoBvid.isEmpty()) return;
        showingParts = false;
        sectionTitleView.setText("推荐");
        repository.loadRelatedVideos(videoBvid, (items, error) -> {
            if (destroyed) return;
            relatedRow.removeAllViews();
            if (items == null || items.isEmpty()) {
                relatedSection.setVisibility(View.GONE);
                return;
            }
            relatedSection.setVisibility(View.VISIBLE);
            int max = Math.min(8, items.size());
            for (int i = 0; i < max; i++) {
                View card = createRelatedCard(items.get(i));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) dp(380), ViewGroup.LayoutParams.WRAP_CONTENT);
                params.rightMargin = (int) dp(18);
                relatedRow.addView(card, params);
            }
        });
    }

    private View createRelatedCard(VideoItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding((int) dp(8), (int) dp(8), (int) dp(8), (int) dp(12));
        card.setBackground(cardBackground());
        card.setContentDescription(item.title);
        attachCardFocusScale(card);

        final ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xff111821);
        card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(214)));

        TextView title = text(item.title, 18, Color.WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, (int) dp(8), 0, (int) dp(4));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = text(item.owner + " · " + item.stats + " · " + item.duration, 15, 0xff96a8b0, false);
        card.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> {
            Intent intent = new Intent(VideoDetailActivity.this, VideoDetailActivity.class);
            intent.putExtra(VideoDetailActivity.EXTRA_TARGET, item.targetUrl);
            startActivity(intent);
        });
        if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
            repository.loadImage(item.coverUrl, bitmap -> {
                if (!destroyed && bitmap != null) image.setImageBitmap(bitmap);
            });
        }
        return card;
    }

    private void renderParts(BiliRepository.VideoDetail info) {
        showingParts = true;
        sectionTitleView.setText("选集");
        relatedRow.removeAllViews();
        if (info.parts == null || info.parts.isEmpty()) return;
        relatedSection.setVisibility(View.VISIBLE);
        for (BiliRepository.VideoPart part : info.parts) {
            View card = createPartCard(part, info);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) dp(380), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = (int) dp(18);
            relatedRow.addView(card, params);
        }
    }

    private View createPartCard(BiliRepository.VideoPart part, BiliRepository.VideoDetail info) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding((int) dp(8), (int) dp(8), (int) dp(8), (int) dp(12));
        card.setBackground(cardBackground());
        attachCardFocusScale(card);
        String partTitle = part.title == null || part.title.trim().isEmpty() ? ("P" + part.page) : part.title;
        card.setContentDescription("选集 " + partTitle);

        final ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xff111821);
        card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(214)));

        TextView title = text(partTitle, 18, Color.WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, (int) dp(8), 0, (int) dp(4));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = text("P" + part.page + " · " + formatDuration(part.durationSec), 15, 0xff96a8b0, false);
        card.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> openPart(part.page));

        String imageUrl = part.cover == null ? "" : part.cover;
        if (imageUrl.isEmpty()) imageUrl = info.pic == null ? "" : info.pic;
        if (!imageUrl.isEmpty()) {
            repository.loadImage(imageUrl, bitmap -> {
                if (!destroyed && bitmap != null) image.setImageBitmap(bitmap);
            });
        }
        return card;
    }

    private int extractPageParam(String url) {
        if (url == null) return 1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[?&]p=(\\d+)").matcher(url);
        if (!matcher.find()) return 1;
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void openPart(int page) {
        String base = videoBaseUrl();
        if (base.isEmpty()) {
            Toast.makeText(this, "视频信息尚未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, base + "?p=" + page);
        intent.putExtra(PlayerActivity.EXTRA_DANMAKU, settings.danmaku());
        intent.putExtra(PlayerActivity.EXTRA_AUTOPLAY, settings.autoPlay());
        intent.putExtra(PlayerActivity.EXTRA_HIGH_QUALITY, settings.highQuality());
        startActivity(intent);
    }

    private String videoBaseUrl() {
        if (bvid != null && !bvid.isEmpty()) return "https://www.bilibili.com/video/" + bvid;
        if (aid > 0) return "https://www.bilibili.com/video/av" + aid;
        return "";
    }

    private void openUserSpace() {
        if (detail == null || detail.ownerMid <= 0) return;
        Intent intent = new Intent(this, UserSpaceActivity.class);
        intent.putExtra(UserSpaceActivity.EXTRA_MID, detail.ownerMid);
        startActivity(intent);
    }

    private void openPlayer() {
        String url = videoBaseUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "视频信息尚未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        // 历史记录/外部链接可能带 ?p=：详情页再播放时保留上次分P，避免回到 P1。
        if (initialPage > 1) url = url + "?p=" + initialPage;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, url);
        intent.putExtra(PlayerActivity.EXTRA_DANMAKU, settings.danmaku());
        intent.putExtra(PlayerActivity.EXTRA_AUTOPLAY, settings.autoPlay());
        intent.putExtra(PlayerActivity.EXTRA_HIGH_QUALITY, settings.highQuality());
        startActivity(intent);
    }

    private boolean ensureLoaded() {
        if (detail != null && aid > 0) return true;
        Toast.makeText(this, "视频信息尚未加载", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void runWatchLater() {
        if (!ensureLoaded()) return;
        repository.addToWatchLater(aid, actionCallback("已加入稍后再看"));
    }

    private void runLike() {
        if (!ensureLoaded()) return;
        repository.setVideoLike(aid, true, actionCallback("点赞成功"));
    }

    private void runCoin() {
        if (!ensureLoaded()) return;
        repository.addVideoCoin(aid, 1, actionCallback("投币成功"));
    }

    private void runFavorite() {
        if (!ensureLoaded()) return;
        repository.addVideoFavorite(aid, actionCallback("收藏成功"));
    }

    private BiliRepository.VideoActionCallback actionCallback(final String successText) {
        return (success, message) -> {
            if (destroyed) return;
            String text = success ? successText : (message == null || message.isEmpty() ? "操作失败" : message);
            Toast.makeText(VideoDetailActivity.this, text, Toast.LENGTH_SHORT).show();
        };
    }

    private void setStatusState(String title, String hint, boolean actionable, boolean error) {
        statusView.setText(title + (hint == null || hint.isEmpty() ? "" : "\n" + hint));
        statusView.setTextColor(error ? 0xffffa9c4 : 0xffd6e1e6);
        statusView.setLineSpacing(dp(11), 1f);
        statusView.setFocusable(actionable);
        statusView.setClickable(actionable);
        statusView.setBackground(statusBackground(actionable));
        statusView.setVisibility(View.VISIBLE);
        if (actionable) statusView.requestFocus();
    }

    private void setDetailContentVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (topSection != null) topSection.setVisibility(visibility);
        if (buttonsRow != null) buttonsRow.setVisibility(visibility);
        if (!visible && relatedSection != null) relatedSection.setVisibility(View.GONE);
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(label, 22, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding((int) dp(28), (int) dp(14), (int) dp(28), (int) dp(14));
        button.setMinWidth((int) dp(190));
        button.setMinHeight((int) dp(70));
        button.setFocusable(true);
        button.setClickable(true);
        button.setBackground(buttonBackground(primary));
        return button;
    }

    private void setButtonIcon(TextView button, int drawableId) {
        Drawable icon = getDrawable(drawableId);
        if (icon == null) return;
        icon.setTint(Color.WHITE);
        int size = (int) dp(28);
        icon.setBounds(0, 0, size, size);
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding((int) dp(12));
    }

    private void addButton(LinearLayout parent, View button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = (int) dp(18);
        parent.addView(button, params);
    }

    private StateListDrawable buttonBackground(boolean primary) {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(primary ? 0xfffb5c91 : 0xff24303a);
        focused.setCornerRadius(dp(16));
        focused.setStroke((int) dp(4), primary ? Color.WHITE : 0xfffb5c91);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(primary ? 0xfffb5c91 : 0xff1e2a33);
        normal.setCornerRadius(dp(16));
        normal.setStroke((int) dp(1), primary ? 0xffffa8c3 : 0xff35434e);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, normal);
        return drawable;
    }

    private StateListDrawable statusBackground(boolean actionable) {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xee18232d);
        focused.setCornerRadius(dp(24));
        focused.setStroke((int) dp(5), 0xfffb5c91);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xd918232d);
        normal.setCornerRadius(dp(24));
        normal.setStroke((int) dp(actionable ? 2 : 1), actionable ? 0x88fb5c91 : 0x337b8b95);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, normal);
        return drawable;
    }

    private GradientDrawable upRowBackground(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? 0xff18222c : Color.TRANSPARENT);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(focused ? (int) dp(3) : 0, 0xfffb5c91);
        return drawable;
    }

    private StateListDrawable cardBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xff202d38);
        focused.setCornerRadius(dp(14));
        focused.setStroke((int) dp(4), 0xfffb5c91);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xff16212b);
        normal.setCornerRadius(dp(14));
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, normal);
        return drawable;
    }

    private void attachCardFocusScale(View card) {
        card.setOnFocusChangeListener((view, focused) -> {
            view.setTranslationZ(focused ? dp(18) : 0f);
            view.animate()
                    .scaleX(focused ? 1.06f : 1f)
                    .scaleY(focused ? 1.06f : 1f)
                    .setDuration(180)
                    .start();
        });
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private String formatCount(long value) {
        if (value >= 100000000L) return String.format(Locale.CHINA, "%.1f亿", value / 100000000f);
        if (value >= 10000L) return String.format(Locale.CHINA, "%.1f万", value / 10000f);
        return String.valueOf(value);
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "";
        int total = seconds;
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int secs = total % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, secs);
    }

    private String formatDate(long timestamp) {
        if (timestamp <= 0) return "";
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            return format.format(new Date(timestamp * 1000L));
        } catch (Exception ignored) {
            return "";
        }
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
