package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 原生 UP 主/主播页：头像资料 + 直播状态 + 投稿视频网格（不再使用 WebView）。 */
public final class UserSpaceActivity extends Activity {
    static final String EXTRA_MID = "user_mid";
    static final String EXTRA_ROOM_ID = "room_id";

    private static final int COLUMNS = 3;

    private final BiliRepository repository = new BiliRepository();
    private final List<View> cards = new ArrayList<>();

    private PlaybackSettings settings;
    private boolean destroyed;
    private long mid;
    private long roomId;
    private float scale;
    private int cardWidth;

    private LinearLayout content;
    private LinearLayout gridContainer;
    private LinearLayout currentRow;
    private int currentRowCount;
    private ImageView avatarView;
    private TextView nameView;
    private TextView signView;
    private TextView statsView;
    private LinearLayout liveRow;
    private TextView liveStatusView;
    private Button liveButton;
    private TextView statusView;
    private TextView loadMoreView;
    private View lastCard;

    private int page = 1;
    private boolean hasMore = true;
    private boolean loadingMore = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = Math.min(metrics.widthPixels / 1920f, metrics.heightPixels / 1080f);
        cardWidth = Math.max(1, (int) ((metrics.widthPixels - dp(120) - dp(60)) / COLUMNS));
        settings = new PlaybackSettings(this);
        mid = getIntent().getLongExtra(EXTRA_MID, 0L);
        roomId = getIntent().getLongExtra(EXTRA_ROOM_ID, 0L);
        buildUi();
        if (mid <= 0) {
            showStatus("无效的 UP 主 ID");
            return;
        }
        loadUserCard();
        loadVideos(1);
        if (roomId > 0) loadLiveRoom();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(pearlBackground());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(60), (int) dp(42), (int) dp(60), (int) dp(80));
        content.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int) dp(26), (int) dp(24), (int) dp(26), (int) dp(24));
        header.setBackground(surface(0xf0ffffff, 26, 0x1813212b, 1));
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setBackground(oval(0xff202a33));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams((int) dp(170), (int) dp(170));
        header.addView(avatarView, avatarParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding((int) dp(32), 0, 0, 0);
        header.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        nameView = text("UP 主", 34, 0xff13212b, true);
        info.addView(nameView);
        signView = text("", 18, 0xff60717c, false);
        signView.setPadding(0, (int) dp(8), 0, (int) dp(8));
        info.addView(signView);
        statsView = text("", 17, 0xff60717c, false);
        info.addView(statsView);

        liveRow = new LinearLayout(this);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        liveRow.setVisibility(View.GONE);
        liveRow.setPadding(0, (int) dp(22), 0, 0);
        content.addView(liveRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        liveStatusView = text("", 18, 0xffffb0c8, false);
        liveRow.addView(liveStatusView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        liveButton = new Button(this);
        liveButton.setText("进入直播间");
        liveButton.setFocusable(true);
        liveButton.setTextColor(Color.WHITE);
        liveButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(18));
        liveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xfffb5c91));
        liveRow.addView(liveButton, new LinearLayout.LayoutParams((int) dp(220), (int) dp(64)));

        TextView section = text("投稿视频", 25, 0xff13212b, true);
        section.setPadding(0, (int) dp(36), 0, (int) dp(12));
        content.addView(section);

        statusView = text("正在加载 UP 主主页…", 20, 0xff60717c, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, (int) dp(36), 0, (int) dp(36));
        statusView.setFocusable(true);
        content.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(gridContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadMoreView = text("", 17, 0xff60717c, false);
        loadMoreView.setGravity(Gravity.CENTER);
        loadMoreView.setPadding(0, (int) dp(20), 0, (int) dp(10));
        content.addView(loadMoreView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView.setOnClickListener(v -> {
            if (destroyed || mid <= 0) return;
            loadUserCard();
            loadVideos(1);
        });

        setContentView(scroll);
    }

    private void loadUserCard() {
        repository.loadUserCard(mid, (card, error) -> {
            if (destroyed) return;
            if (card == null) {
                nameView.setText("UP 主信息加载失败");
                signView.setText(error == null ? "" : error);
                return;
            }
            nameView.setText(card.name == null || card.name.isEmpty() ? ("UID " + mid) : card.name);
            signView.setText(card.sign == null || card.sign.trim().isEmpty() ? "这个人很神秘，什么都没有写" : card.sign);
            statsView.setText(formatCount(card.fans) + " 粉丝 · " + Math.max(0, card.videos) + " 投稿");
            if (card.face != null && !card.face.isEmpty()) {
                repository.loadImage(card.face, bitmap -> {
                    if (!destroyed && bitmap != null) avatarView.setImageBitmap(bitmap);
                });
            }
        });
    }

    private void loadLiveRoom() {
        repository.loadLiveRoomInfo(roomId, (info, error) -> {
            if (destroyed) return;
            liveRow.setVisibility(View.VISIBLE);
            if (info == null) {
                liveStatusView.setText("直播状态暂时不可用");
                liveButton.setVisibility(View.GONE);
                return;
            }
            String title = info.title == null || info.title.isEmpty() ? "B 站直播间" : info.title;
            boolean live = info.liveStatus == 1;
            liveStatusView.setText((live ? "直播中" : "未开播") + " · " + title);
            if (live) {
                liveButton.setVisibility(View.VISIBLE);
                liveButton.setOnClickListener(v -> openPlayer("https://live.bilibili.com/" + info.roomId));
            } else {
                liveButton.setVisibility(View.GONE);
            }
        });
    }

    private void loadVideos(int page) {
        if (destroyed || loadingMore || mid <= 0) return;
        loadingMore = true;
        if (page == 1) {
            statusView.setText("正在加载投稿视频…");
            statusView.setVisibility(View.VISIBLE);
            gridContainer.removeAllViews();
            cards.clear();
            currentRow = null;
            currentRowCount = 0;
            lastCard = null;
            hasMore = true;
        } else {
            loadMoreView.setText("加载更多…");
        }
        final int requestedPage = page;
        repository.loadUserVideos(mid, page, (items, more, error) -> {
            if (destroyed) return;
            loadingMore = false;
            if (error != null && !error.isEmpty()) {
                if (requestedPage == 1) {
                    statusView.setText(error + "\n按 OK 重试");
                    statusView.setVisibility(View.VISIBLE);
                    statusView.requestFocus();
                } else {
                    loadMoreView.setText("加载失败，按 ↓/→ 重试");
                }
                return;
            }
            if (requestedPage == 1) {
                gridContainer.removeAllViews();
                cards.clear();
                currentRow = null;
                currentRowCount = 0;
                lastCard = null;
                if (items == null || items.isEmpty()) {
                    statusView.setText("这里还没有投稿视频");
                    statusView.setVisibility(View.VISIBLE);
                    hasMore = false;
                    this.page = 1;
                    loadMoreView.setText("");
                    return;
                }
                statusView.setVisibility(View.GONE);
            }
            appendItems(items);
            if (requestedPage == 1 && !cards.isEmpty()) cards.get(0).requestFocus();
            boolean emptyPage = items == null || items.isEmpty();
            hasMore = !emptyPage && more;
            this.page = requestedPage;
            loadMoreView.setText(hasMore ? "" : "已经到底了");
        });
    }

    private void appendItems(List<VideoItem> items) {
        if (items == null) return;
        for (VideoItem item : items) {
            View card = createCard(item);
            cards.add(card);
            addCardToRow(card);
        }
        lastCard = cards.isEmpty() ? null : cards.get(cards.size() - 1);
    }

    private void addCardToRow(View card) {
        if (currentRow == null || currentRowCount >= COLUMNS) {
            currentRow = new LinearLayout(this);
            currentRow.setOrientation(LinearLayout.HORIZONTAL);
            gridContainer.addView(currentRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            currentRowCount = 0;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins((int) dp(10), (int) dp(10), (int) dp(10), (int) dp(10));
        currentRow.addView(card, params);
        currentRowCount++;
    }

    private View createCard(VideoItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(cardBackground());
        card.setPadding((int) dp(10), (int) dp(10), (int) dp(10), (int) dp(14));
        card.setContentDescription(item.title);

        final ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(0xffdce5ea);
        card.addView(cover, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(300)));

        TextView title = text(item.title, 18, 0xff13212b, true);
        title.setTextColor(focusTextColor(0xff13212b, Color.WHITE));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, (int) dp(10), 0, (int) dp(4));
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = text(item.stats + "   " + item.duration, 14, 0xff60717c, false);
        meta.setTextColor(focusTextColor(0xff60717c, 0xffdbe3e8));
        card.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> openVideoDetail(item.targetUrl));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(170).start();
            v.setElevation(focused ? dp(18) : dp(2));
        });
        if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
            repository.loadImage(item.coverUrl, bitmap -> {
                if (!destroyed && bitmap != null) cover.setImageBitmap(bitmap);
            });
        }
        return card;
    }

    private void openVideoDetail(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra(VideoDetailActivity.EXTRA_TARGET, url);
        startActivity(intent);
    }

    private void openPlayer(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, url);
        intent.putExtra(PlayerActivity.EXTRA_DANMAKU, settings.danmaku());
        intent.putExtra(PlayerActivity.EXTRA_AUTOPLAY, settings.autoPlay());
        intent.putExtra(PlayerActivity.EXTRA_HIGH_QUALITY, settings.highQuality());
        startActivity(intent);
    }

    private void maybeLoadMore() {
        if (destroyed || loadingMore || !hasMore || mid <= 0) return;
        loadVideos(page + 1);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && lastCard != null && getCurrentFocus() == lastCard) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT) maybeLoadMore();
        }
        return super.dispatchKeyEvent(event);
    }

    private void showStatus(String message) {
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        repository.shutdown();
        super.onDestroy();
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private StateListDrawable cardBackground() {
        StateListDrawable drawable = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xff16202a);
        focused.setCornerRadius(dp(18));
        focused.setStroke((int) dp(4), 0xfffb5c91);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xedffffff);
        normal.setCornerRadius(dp(18));
        normal.setStroke((int) dp(1), 0x1813212b);
        drawable.addState(new int[]{android.R.attr.state_focused}, focused);
        drawable.addState(new int[]{}, normal);
        return drawable;
    }

    private ColorStateList focusTextColor(int normal, int focused) {
        return new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{}
        }, new int[]{focused, normal});
    }

    private GradientDrawable pearlBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xffdff7fc, 0xfff6f8fa, 0xffffe7ef});
        return drawable;
    }

    private GradientDrawable surface(int color, float radius, int strokeColor, float strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke((int) dp(strokeWidth), strokeColor);
        return drawable;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private String formatCount(long value) {
        if (value >= 10000) return String.format(Locale.CHINA, "%.1f万", value / 10000f);
        return String.valueOf(value);
    }

    private float dp(float value) {
        return value * scale;
    }
}
