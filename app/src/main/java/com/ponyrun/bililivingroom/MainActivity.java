package com.ponyrun.bililivingroom;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private BiliTvView contentView;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        contentView = new BiliTvView();
        setContentView(contentView);
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }

    @Override protected void onResume() {
        super.onResume();
        if (contentView != null) contentView.onHostResume();
    }

    @Override protected void onDestroy() {
        if (contentView != null) contentView.shutdown();
        super.onDestroy();
    }

    private final class BiliTvView extends View {
        private static final int AREA_NAV = 0;
        private static final int AREA_FILTER = 1;
        private static final int AREA_GRID = 2;
        private static final int AREA_SEARCH = 3;
        private static final int AREA_SUGGEST = 4;
        private static final int AREA_SIDEBAR = 5;
        private static final int AREA_FAVORITE_FOLDERS = 6;
        private static final int AREA_FAVORITE_CONTROLS = 7;
        private static final int PAGE_SIZE = 24;
        private static final int MAX_PAGES = 50;
        private final String[] sidebarMenu = {"历史记录", "收藏与订阅", "稍后再看", "我的追番", "设置", "退出登录"};
        private final int[] sidebarModes = {1, 3, 2, 4, 5, -1};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int COLOR_BG = 0xfff6f8fa;
        private static final int COLOR_TEXT = 0xff13212b;
        private static final int COLOR_MUTED = 0xff60717c;
        private static final int COLOR_PINK = 0xfffb5c91;
        private static final int COLOR_FOCUS = 0x66fb5c91;
        private static final int COLOR_CARD = 0xeaffffff;
        private static final int COLOR_CARD_SOFT = 0xd8ffffff;
        private final BiliRepository repository = new BiliRepository();
        private final List<VideoItem> videos = new ArrayList<>();
        private final Map<String, Bitmap> bitmaps = new HashMap<>();
        private final String[] nav = {"直播", "推荐", "热门", "排行榜", "动态", "收藏", "我的", "搜索"};
        private final String[] filters = {"综合", "番剧", "动画", "游戏", "科技", "知识", "影视", "生活", "音乐", "美食"};
        private final int[] regionIds = {0, 13, 1, 4, 188, 36, 181, 160, 3, 211};
        private final String[] liveFilters = {"推荐", "网游", "手游", "娱乐", "单机", "虚拟主播", "电台"};
        private final int[] liveAreaIds = {0, 2, 3, 5, 6, 9, 10};
        private final String[] rankFilters = {"全站", "番剧", "动画", "音乐", "舞蹈", "游戏", "知识", "科技", "生活", "影视"};
        private final int[] rankIds = {0, 13, 1005, 1003, 1004, 1008, 1010, 1012, 160, 1001};
        private final String[] searchFilters = {"全部", "视频", "番剧", "影视", "主播", "直播间", "用户"};
        private final String[] searchTypes = {"all", "video", "media_bangumi", "media_ft", "live_user", "live_room", "bili_user"};
        private final PlaybackSettings settings = new PlaybackSettings(MainActivity.this);
        private final PlaybackResume playbackResume = new PlaybackResume(MainActivity.this);
        private final SearchHistory searchHistory = new SearchHistory(MainActivity.this);
        private final List<String> suggestHistory = new ArrayList<>();
        private final List<String> hotKeywords = new ArrayList<>();
        private int suggestIndex = 0;
        private boolean hotLoading = false;
        private String hotError = "";
        private int area = AREA_NAV;
        private int navIndex = 1;
        private int filterIndex = 0;
        private int filterFocusIndex = 0;
        private int gridIndex = 0;
        private int firstRow = 0;
        private int sidebarIndex = 0;
        private int rootSection = 0;
        private final List<BiliRepository.FavoriteFolder> favoriteFolders = new ArrayList<>();
        private int favoriteFolderIndex = 0;
        private boolean favoriteFoldersLoading = false;
        private String favoriteFoldersError = "";
        private int favoriteControlIndex = 0;
        private int favoriteSortIndex = 0;
        private int favoriteFilterIndex = 0;
        private int profileRequestGeneration = 0;
        private BiliRepository.AccountProfile accountProfile;
        private Bitmap profileFace;
        private Bitmap profileBanner;
        private boolean profileLoading = false;
        private String profileError = "";
        private boolean loading = true;
        private String status = "正在为你挑选内容…";
        private int pageRequestGeneration = 0;
        private int personalMode = -1;
        private String searchKeyword = "";
        private Runnable lastLoadAction;
        private int loadedPage = 1;
        private int lastPageSize = 0;
        private boolean loadingMore = false;
        private boolean noMore = false;
        private boolean moreFailed = false;
        private String pageCursor = "";
        private boolean destroyed;
        private boolean lastSessionState = BiliAuthRepository.hasSession();
        private final RectF reusable = new RectF();
        private ValueAnimator gridFocusAnimator;
        private int previousGridIndex = -1;
        private float gridFocusProgress = 1f;

        BiliTvView() {
            super(MainActivity.this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
            textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            loadRecommend();
        }

        private void animateGridFocus(int oldArea, int oldIndex) {
            if (gridFocusAnimator != null) gridFocusAnimator.cancel();
            previousGridIndex = oldArea == AREA_GRID && oldIndex != gridIndex ? oldIndex : -1;
            gridFocusProgress = 0f;
            gridFocusAnimator = ValueAnimator.ofFloat(0f, 1f);
            gridFocusAnimator.setDuration(170L);
            gridFocusAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            gridFocusAnimator.addUpdateListener(animation -> {
                gridFocusProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            gridFocusAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    gridFocusProgress = 1f;
                    previousGridIndex = -1;
                    invalidate();
                }
            });
            gridFocusAnimator.start();
        }

        private void finishFocusMove(int oldArea, int oldIndex) {
            if (area == AREA_GRID && (oldArea != AREA_GRID || oldIndex != gridIndex)) {
                animateGridFocus(oldArea, oldIndex);
            } else if (area != AREA_GRID && gridFocusAnimator != null) {
                gridFocusAnimator.cancel();
                previousGridIndex = -1;
                gridFocusProgress = 1f;
            }
        }

        private float gridFocusAmount(int index) {
            if (area != AREA_GRID) return 0f;
            if (index == gridIndex) return gridFocusProgress;
            if (index == previousGridIndex) return 1f - gridFocusProgress;
            return 0f;
        }

        private int focusColor(int color, float amount) {
            float value = Math.max(0f, Math.min(1f, amount));
            return Color.argb(Math.round(Color.alpha(color) * value),
                    Color.red(color), Color.green(color), Color.blue(color));
        }

        private void onHostResume() {
            boolean current = BiliAuthRepository.hasSession();
            if (current == lastSessionState) return;
            lastSessionState = current;
            if (navIndex == 4) loadDynamic();
            else if (navIndex == 5) loadFavorites();
            else if (navIndex == 6 && personalMode < 0) {
                if (current) loadPersonalRoot();
                else { clearPersonalRootState(); area = AREA_GRID; invalidate(); }
            }
            else if (navIndex == 6 && personalMode >= 1 && personalMode <= 4) loadPersonalVideos(personalMode);
            else invalidate();
        }

        private boolean isPersonalRoot() {
            return navIndex == 6 && personalMode < 0 && BiliAuthRepository.hasSession();
        }

        /** “我的”根页：账号资料 + 当前选中功能区的数据。 */
        private void loadPersonalRoot() {
            if (destroyed) return;
            if (!BiliAuthRepository.hasSession()) {
                clearPersonalRootState();
                invalidate();
                return;
            }
            lastLoadAction = this::loadPersonalRoot;
            resetPagination();
            final int generation = ++pageRequestGeneration;
            final int profileGeneration = ++profileRequestGeneration;
            accountProfile = null;
            profileFace = null;
            profileBanner = null;
            profileLoading = true;
            profileError = "";
            videos.clear();
            bitmaps.clear();
            gridIndex = 0;
            firstRow = 0;
            invalidate();
            repository.loadAccountProfile((profile, error) -> {
                if (destroyed || profileGeneration != profileRequestGeneration) return;
                profileLoading = false;
                accountProfile = profile;
                profileError = profile == null ? (error == null || error.isEmpty() ? "账号资料加载失败" : error) : "";
                invalidate();
                if (profile != null && profile.face != null && !profile.face.isEmpty()) {
                    repository.loadPriorityImage(profile.face, bitmap -> {
                        if (destroyed || profileGeneration != profileRequestGeneration || bitmap == null) return;
                        profileFace = bitmap;
                        invalidate();
                    });
                }
                if (profile != null && profile.topPhoto != null && !profile.topPhoto.isEmpty()) {
                    repository.loadPriorityImage(profile.topPhoto, bitmap -> {
                        if (destroyed || profileGeneration != profileRequestGeneration || bitmap == null) return;
                        profileBanner = bitmap;
                        invalidate();
                    });
                }
            });
            loadRootSectionData(generation);
        }

        /** 左侧菜单切换时只重载右侧功能区，不重复拉账号资料。 */
        private void loadRootSection() {
            if (destroyed || !BiliAuthRepository.hasSession()) return;
            lastLoadAction = this::loadPersonalRoot;
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loadRootSectionData(generation);
        }

        private void loadRootSectionData(final int generation) {
            videos.clear();
            bitmaps.clear();
            gridIndex = 0;
            firstRow = 0;
            if (rootSection == 4) {
                loading = false;
                status = "";
                invalidate();
                return;
            }
            loading = true;
            status = rootSectionLoadingText();
            invalidate();
            BiliRepository.PageCallback callback = result -> {
                if (destroyed || generation != pageRequestGeneration) return;
                applyFirstPageResult(result);
            };
            if (rootSection == 0) repository.loadHistoryPage("", callback);
            else if (rootSection == 1) repository.loadFavoritesPage("", callback);
            else if (rootSection == 2) repository.loadWatchLaterPage("", callback);
            else if (rootSection == 3) repository.loadBangumiPage("", callback);
        }

        private String rootSectionLoadingText() {
            if (rootSection == 1) return "正在加载收藏视频…";
            if (rootSection == 2) return "正在加载稍后再看…";
            if (rootSection == 3) return "正在加载我的追番…";
            return "正在加载观看历史…";
        }

        private String rootSectionTitle() {
            if (rootSection == 1) return "收藏与订阅";
            if (rootSection == 2) return "稍后再看";
            if (rootSection == 3) return "我的追番";
            if (rootSection == 4) return "设置";
            return "历史记录";
        }

        private String rootSectionEmptyText() {
            if (rootSection == 1) return "暂无收藏内容";
            if (rootSection == 2) return "暂无稍后再看";
            if (rootSection == 3) return "暂无追番追剧";
            return "暂无观看历史";
        }

        private String rootSectionEmptyHint() {
            if (rootSection == 1) return "收藏的视频会显示在这里";
            if (rootSection == 2) return "把想看的视频加入稍后再看";
            if (rootSection == 3) return "关注的番剧和影视会显示在这里";
            return "播放过的视频会显示在这里";
        }

        private void clearPersonalRootState() {
            pageRequestGeneration++;
            profileRequestGeneration++;
            lastLoadAction = null;
            resetPagination();
            loading = false;
            status = "";
            accountProfile = null;
            profileFace = null;
            profileBanner = null;
            profileLoading = false;
            profileError = "";
            favoriteFolders.clear();
            favoriteFolderIndex = 0;
            favoriteFoldersLoading = false;
            favoriteFoldersError = "";
            favoriteControlIndex = 0;
            favoriteSortIndex = 0;
            favoriteFilterIndex = 0;
            videos.clear();
            bitmaps.clear();
            gridIndex = 0;
            firstRow = 0;
            sidebarIndex = 0;
            rootSection = 0;
        }

        private void loadRecommend() {
            lastLoadAction = this::loadRecommend;
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loading = true;
            status = "正在为你挑选内容…";
            invalidate();
            repository.loadRecommend((items, message) -> { if (generation == pageRequestGeneration) setVideos(items, message); });
        }

        private void loadPopular() {
            lastLoadAction = this::loadPopular;
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loading = true;
            status = "正在加载全站热门…";
            invalidate();
            repository.loadPopular((items, message) -> { if (generation == pageRequestGeneration) setVideos(items, message); });
        }

        private void loadLive() {
            lastLoadAction = this::loadLive;
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loading = true; status = "正在加载直播…"; videos.clear(); bitmaps.clear(); invalidate();
            repository.loadLive((items, message) -> { if (generation == pageRequestGeneration) setVideos(items, message); });
        }

        private void loadRank(int rid, String name) {
            lastLoadAction = () -> loadRank(rid, name);
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loading = true; status = "正在加载" + name + "榜…"; videos.clear(); bitmaps.clear(); invalidate();
            repository.loadRankPage(rid, 1, result -> { if (generation == pageRequestGeneration) applyFirstPageResult(result); });
        }

        private void loadRegion(int rid, String name) {
            lastLoadAction = () -> loadRegion(rid, name);
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loading = true; status = "正在加载“" + name + "”分区…"; videos.clear(); bitmaps.clear(); invalidate();
            repository.loadRegion(rid, (items, message) -> { if (generation == pageRequestGeneration) setVideos(items, message); });
        }

        private void loadLiveArea(int parentAreaId, String name) {
            lastLoadAction = () -> loadLiveArea(parentAreaId, name);
            resetPagination();
            final int generation = ++pageRequestGeneration;
            loading = true; status = "正在加载“" + name + "”直播…"; videos.clear(); bitmaps.clear(); invalidate();
            repository.loadLiveArea(parentAreaId, (items, message) -> { if (generation == pageRequestGeneration) setVideos(items, message); });
        }

        private boolean preparePrivatePage(String loadingText) {
            videos.clear(); bitmaps.clear(); gridIndex = 0; firstRow = 0;
            if (!BiliAuthRepository.hasSession()) {
                pageRequestGeneration++; loading = false; status = "登录后可查看账号内容"; invalidate(); return false;
            }
            loading = true; status = loadingText; invalidate(); return true;
        }

        private void loadDynamic() {
            lastLoadAction = this::loadDynamic;
            resetPagination();
            if (!preparePrivatePage("正在同步关注动态…")) return;
            final int generation = ++pageRequestGeneration;
            repository.loadDynamicPage("", result -> { if (generation == pageRequestGeneration) applyFirstPageResult(result); });
        }

        private void loadFavorites() {
            lastLoadAction = this::loadFavorites;
            resetPagination();
            if (!preparePrivatePage("正在同步收藏视频…")) return;
            ensureAccountProfileForFavorites();
            final int generation = ++pageRequestGeneration;
            favoriteFolders.clear();
            favoriteFolderIndex = 0;
            favoriteFoldersLoading = true;
            favoriteFoldersError = "";
            repository.loadFavoriteFolders((folders, error) -> {
                if (destroyed || generation != pageRequestGeneration) return;
                favoriteFoldersLoading = false;
                favoriteFolders.clear();
                if (folders != null) favoriteFolders.addAll(folders);
                favoriteFoldersError = error == null ? "" : error;
                if (favoriteFolders.isEmpty()) {
                    loading = false;
                    status = favoriteFoldersError.isEmpty() ? "暂无收藏夹" : favoriteFoldersError;
                    invalidate();
                    return;
                }
                loadFavoriteFolderAt(0, generation);
            });
        }

        private void loadFavoriteFolderAt(int index, int generation) {
            if (favoriteFolders.isEmpty() || generation != pageRequestGeneration) return;
            favoriteFolderIndex = clamp(index, 0, favoriteFolders.size() - 1);
            BiliRepository.FavoriteFolder folder = favoriteFolders.get(favoriteFolderIndex);
            lastLoadAction = this::reloadSelectedFavoriteFolder;
            loading = true;
            loadingMore = false;
            moreFailed = false;
            noMore = false;
            pageCursor = "";
            status = "正在加载“" + folder.title + "”…";
            videos.clear();
            bitmaps.clear();
            gridIndex = 0;
            firstRow = 0;
            invalidate();
            repository.loadFavoritesPage(folder.id, "", favoriteSortIndex == 1 ? "view" : "mtime",
                    favoriteFilterIndex == 1, result -> {
                if (generation == pageRequestGeneration) applyFirstPageResult(result);
            });
        }

        private void reloadSelectedFavoriteFolder() {
            if (favoriteFolders.isEmpty()) {
                loadFavorites();
                return;
            }
            resetPagination();
            int generation = ++pageRequestGeneration;
            loadFavoriteFolderAt(favoriteFolderIndex, generation);
        }

        private void selectFavoriteFolder(int index) {
            if (favoriteFolders.isEmpty()) return;
            int next = clamp(index, 0, favoriteFolders.size() - 1);
            if (next == favoriteFolderIndex) return;
            favoriteFolderIndex = next;
            resetPagination();
            int generation = ++pageRequestGeneration;
            loadFavoriteFolderAt(next, generation);
        }

        private void ensureAccountProfileForFavorites() {
            if (accountProfile != null || profileLoading) return;
            final int generation = ++profileRequestGeneration;
            profileLoading = true;
            profileError = "";
            repository.loadAccountProfile((profile, error) -> {
                if (destroyed || generation != profileRequestGeneration) return;
                profileLoading = false;
                accountProfile = profile;
                profileError = profile == null ? (error == null ? "" : error) : "";
                invalidate();
                if (profile != null && profile.face != null && !profile.face.isEmpty()) {
                    repository.loadPriorityImage(profile.face, bitmap -> {
                        if (destroyed || generation != profileRequestGeneration || bitmap == null) return;
                        profileFace = bitmap;
                        invalidate();
                    });
                }
            });
        }

        private void loadPersonalVideos(int mode) {
            lastLoadAction = () -> loadPersonalVideos(mode);
            resetPagination();
            String[] messages = {"", "正在加载历史记录…", "正在加载稍后再看…", "正在加载收藏视频…", "正在加载追番追剧…"};
            if (!preparePrivatePage(messages[mode])) return;
            final int generation = ++pageRequestGeneration;
            BiliRepository.PageCallback pageCallback = result -> { if (generation == pageRequestGeneration) applyFirstPageResult(result); };
            if (mode == 1) repository.loadHistoryPage("", pageCallback);
            else if (mode == 2) repository.loadWatchLaterPage("", pageCallback);
            else if (mode == 3) repository.loadFavoritesPage("", pageCallback);
            else repository.loadBangumiPage("", pageCallback);
        }

        private void setVideos(List<VideoItem> result, String message) {
            if (destroyed) return;
            videos.clear();
            if (navIndex == 7 && filterIndex == 0) videos.addAll(orderAllSearchResults(result));
            else videos.addAll(result);
            bitmaps.clear();
            loading = false;
            loadingMore = false;
            moreFailed = false;
            status = message == null ? "" : message;
            loadedPage = 1;
            lastPageSize = videos.size();
            pageCursor = "";
            noMore = !currentPageSupportsPaging() || lastPageSize < PAGE_SIZE;
            gridIndex = 0;
            firstRow = 0;
            invalidate();
            loadItemImages(videos);
        }

        /** “全部”搜索首屏按效果图组织为 3 个视频 + 1 个番剧/影视，再接用户/直播分组。 */
        private List<VideoItem> orderAllSearchResults(List<VideoItem> source) {
            List<VideoItem> ordered = new ArrayList<>();
            if (source == null || source.isEmpty()) return ordered;
            boolean[] used = new boolean[source.size()];
            appendSearchKinds(source, used, ordered, 3, "video");
            appendSearchKinds(source, used, ordered, 1, "media_bangumi", "media_ft");
            while (ordered.size() < Math.min(4, source.size())) appendFirstUnused(source, used, ordered);
            appendSearchKinds(source, used, ordered, 4, "bili_user", "live_user", "live_room");
            while (ordered.size() < Math.min(8, source.size())) appendFirstUnused(source, used, ordered);
            while (ordered.size() < source.size()) appendFirstUnused(source, used, ordered);
            return ordered;
        }

        private void appendSearchKinds(List<VideoItem> source, boolean[] used, List<VideoItem> output,
                                       int limit, String... kinds) {
            int added = 0;
            for (int i = 0; i < source.size() && added < limit; i++) {
                if (used[i]) continue;
                String kind = source.get(i).kind;
                boolean match = false;
                for (String candidate : kinds) if (candidate.equals(kind)) { match = true; break; }
                if (!match) continue;
                used[i] = true;
                output.add(source.get(i));
                added++;
            }
        }

        private void appendFirstUnused(List<VideoItem> source, boolean[] used, List<VideoItem> output) {
            for (int i = 0; i < source.size(); i++) {
                if (used[i]) continue;
                used[i] = true;
                output.add(source.get(i));
                return;
            }
        }

        /** 排行榜/动态/收藏/历史/稍后再看/追番使用 cursor 分页（PageResult/PageCallback）。 */
        private boolean currentPageUsesCursorPaging() {
            if (navIndex == 3) return true;
            if (navIndex == 4 || navIndex == 5) return true;
            if (navIndex == 6 && personalMode < 0 && BiliAuthRepository.hasSession()) return rootSection >= 0 && rootSection <= 3;
            if (navIndex == 6 && personalMode >= 1 && personalMode <= 4) return true;
            return false;
        }

        /** 当前页面是否支持分页；仅推荐/热门/分区/直播/搜索使用数字页码分页。 */
        private boolean currentPageSupportsPaging() {
            if (currentPageUsesCursorPaging()) return true;
            if (navIndex == 0) return true;
            if (navIndex == 1 || navIndex == 2) return true;
            if (navIndex == 7) return !searchKeyword.isEmpty();
            return false;
        }

        private void resetPagination() {
            loadedPage = 1;
            lastPageSize = 0;
            loadingMore = false;
            moreFailed = false;
            pageCursor = "";
            noMore = !currentPageSupportsPaging();
        }

        private boolean atLastVideo() {
            return !videos.isEmpty() && gridIndex >= videos.size() - 1;
        }

        /** 顶部导航栏按 ↑：刷新当前页，重置分页/cursor，保留页签/筛选/搜索词。 */
        private void refreshCurrentPage() {
            if (destroyed) return;
            if (loading || loadingMore) { invalidate(); return; }
            if (lastLoadAction == null) { invalidate(); return; }
            repository.clearPagingCaches();
            resetPagination();
            status = "正在刷新…";
            invalidate();
            lastLoadAction.run();
        }

        /** 焦点在最后一项时按 ↓/→ 调用的加载更多入口。 */
        private void maybeLoadMore() {
            if (destroyed || loading || loadingMore || videos.isEmpty()) return;
            if (noMore) return;
            if (!currentPageSupportsPaging()) {
                noMore = true;
                invalidate();
                return;
            }
            loadMore();
        }

        private void loadMore() {
            if (destroyed || loading || loadingMore || noMore || videos.isEmpty()) return;
            if (!currentPageSupportsPaging()) {
                noMore = true;
                invalidate();
                return;
            }
            if (currentPageUsesCursorPaging()) {
                loadMoreCursor();
                return;
            }
            if (loadedPage >= MAX_PAGES) {
                noMore = true;
                invalidate();
                return;
            }
            final int nextPage = loadedPage + 1;
            final int generation = pageRequestGeneration;
            loadingMore = true;
            moreFailed = false;
            status = "加载更多…";
            invalidate();
            BiliRepository.VideosCallback callback = (items, message) -> {
                if (destroyed || generation != pageRequestGeneration) return;
                applyMorePage(nextPage, items, message);
            };
            if (navIndex == 0) {
                if (filterIndex == 0) repository.loadLivePage(nextPage, callback);
                else repository.loadLiveAreaPage(liveAreaIds[filterIndex], nextPage, callback);
            } else if (navIndex == 1) {
                if (filterIndex == 0) repository.loadRecommendPage(nextPage, callback);
                else repository.loadRegionPage(regionIds[filterIndex], nextPage, callback);
            } else if (navIndex == 2) {
                if (filterIndex == 0) repository.loadPopularPage(nextPage, callback);
                else repository.loadRegionPage(regionIds[filterIndex], nextPage, callback);
            } else if (navIndex == 7) {
                repository.searchPage(searchKeyword, searchTypes[filterIndex], nextPage, callback);
            } else {
                loadingMore = false;
                noMore = true;
                invalidate();
            }
        }

        /** 动态/收藏/历史第一页（cursor 为空）。 */
        private void applyFirstPageResult(BiliRepository.PageResult result) {
            if (destroyed) return;
            loading = false;
            loadingMore = false;
            moreFailed = false;
            loadedPage = 1;
            videos.clear();
            bitmaps.clear();
            List<VideoItem> items = result == null || result.items == null ? new ArrayList<>() : result.items;
            String error = result == null ? "加载失败，请稍后重试" : result.error;
            if (items.isEmpty() && error != null && !error.isEmpty()) {
                pageCursor = "";
                lastPageSize = 0;
                noMore = true;
                status = error;
                gridIndex = 0;
                firstRow = 0;
                invalidate();
                return;
            }
            videos.addAll(items);
            pageCursor = result == null || result.cursor == null ? "" : result.cursor;
            lastPageSize = items.size();
            noMore = pageCursor.isEmpty() || items.isEmpty();
            status = "";
            gridIndex = 0;
            firstRow = 0;
            invalidate();
            loadItemImages(items);
        }

        /** 动态/收藏/历史加载下一页，使用保存的 cursor 追加。 */
        private void loadMoreCursor() {
            if (pageCursor.isEmpty()) { noMore = true; invalidate(); return; }
            final String cursor = pageCursor;
            final int generation = pageRequestGeneration;
            loadingMore = true;
            moreFailed = false;
            status = "加载更多…";
            invalidate();
            BiliRepository.PageCallback callback = result -> {
                if (destroyed || generation != pageRequestGeneration) return;
                applyMoreCursor(result);
            };
            if (navIndex == 4) repository.loadDynamicPage(cursor, callback);
            else if (navIndex == 5) {
                if (favoriteFolders.isEmpty()) { loadingMore = false; noMore = true; invalidate(); }
                else repository.loadFavoritesPage(favoriteFolders.get(favoriteFolderIndex).id, cursor,
                        favoriteSortIndex == 1 ? "view" : "mtime", favoriteFilterIndex == 1, callback);
            }
            else if (navIndex == 6 && personalMode < 0) {
                if (rootSection == 0) repository.loadHistoryPage(cursor, callback);
                else if (rootSection == 1) repository.loadFavoritesPage(cursor, callback);
                else if (rootSection == 2) repository.loadWatchLaterPage(cursor, callback);
                else if (rootSection == 3) repository.loadBangumiPage(cursor, callback);
                else { loadingMore = false; noMore = true; invalidate(); }
            }
            else if (navIndex == 3) {
                int page = cursorPageNumber(cursor);
                if (page <= 0) { loadingMore = false; noMore = true; invalidate(); return; }
                repository.loadRankPage(rankIds[filterIndex], page, callback);
            }
            else if (navIndex == 6 && personalMode == 1) repository.loadHistoryPage(cursor, callback);
            else if (navIndex == 6 && personalMode == 2) repository.loadWatchLaterPage(cursor, callback);
            else if (navIndex == 6 && personalMode == 3) repository.loadFavoritesPage(cursor, callback);
            else if (navIndex == 6 && personalMode == 4) repository.loadBangumiPage(cursor, callback);
            else { loadingMore = false; noMore = true; invalidate(); }
        }

        /** 排行榜/稍后再看/追番的 cursor 是下一页页码字符串。 */
        private int cursorPageNumber(String cursor) {
            if (cursor == null) return 0;
            try {
                int page = Integer.parseInt(cursor.trim());
                return page > 0 ? page : 0;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private void applyMoreCursor(BiliRepository.PageResult result) {
            if (destroyed) return;
            loadingMore = false;
            List<VideoItem> items = result == null || result.items == null ? new ArrayList<>() : result.items;
            String error = result == null ? "加载更多失败，请稍后重试" : result.error;
            if (items.isEmpty()) {
                if (error != null && !error.isEmpty()) {
                    moreFailed = true;
                    status = error;
                } else {
                    moreFailed = false;
                    pageCursor = result == null || result.cursor == null ? "" : result.cursor;
                    noMore = true;
                    status = "";
                }
                invalidate();
                return;
            }
            videos.addAll(items);
            pageCursor = result.cursor == null ? "" : result.cursor;
            lastPageSize = items.size();
            moreFailed = false;
            noMore = pageCursor.isEmpty();
            status = "";
            invalidate();
            loadItemImages(items);
        }

        private void applyMorePage(int page, List<VideoItem> items, String message) {
            if (destroyed) return;
            loadingMore = false;
            if (items == null || items.isEmpty()) {
                if (isErrorMessage(message)) {
                    moreFailed = true;
                    status = message;
                } else {
                    moreFailed = false;
                    noMore = true;
                    status = "";
                }
                invalidate();
                return;
            }
            videos.addAll(items);
            loadedPage = page;
            lastPageSize = items.size();
            moreFailed = false;
            status = "";
            if (items.size() < PAGE_SIZE) noMore = true;
            invalidate();
            loadItemImages(items);
        }

        private void loadItemImages(List<VideoItem> items) {
            for (VideoItem item : items) {
                loadItemImage(item.coverUrl);
                loadItemImage(item.avatarUrl);
            }
        }

        private void loadItemImage(String url) {
            if (url == null || url.isEmpty() || bitmaps.containsKey(url)) return;
            repository.loadImage(url, bitmap -> {
                if (destroyed) return;
                if (bitmap != null) bitmaps.put(url, bitmap);
                invalidate();
            });
        }

        private boolean isErrorMessage(String message) {
            if (message == null || message.isEmpty()) return false;
            return message.contains("失败") || message.contains("不可用") || message.contains("没有可用")
                    || message.contains("重试") || message.contains("网络") || message.contains("请确认")
                    || message.contains("错误") || message.contains("超时");
        }

        void shutdown() {
            destroyed = true;
            if (gridFocusAnimator != null) gridFocusAnimator.cancel();
            repository.shutdown();
        }

        private boolean isPrivateContentPage() {
            return navIndex == 4 || navIndex == 5
                    || (navIndex == 6 && personalMode >= 1 && personalMode <= 4);
        }

        private boolean canRetryEmptyState() {
            return videos.isEmpty() && !loading && lastLoadAction != null && !status.isEmpty()
                    && !(!BiliAuthRepository.hasSession() && isPrivateContentPage());
        }

        private void drawEmptyOrError(Canvas c, float w, float h) {
            if (!BiliAuthRepository.hasSession() && isPrivateContentPage()) {
                drawPrivateEmpty(c, w, h);
                return;
            }
            if (status.isEmpty()) {
                drawPrivateEmpty(c, w, h);
                return;
            }
            boolean error = isErrorMessage(status);
            drawStatePanel(c, w, h, h * .35f, status,
                    error ? "检查网络或账号状态后重新加载" : "这里暂时没有可显示的内容",
                    canRetryEmptyState() ? "按 OK 重新加载" : "", error);
        }

        private void drawStatePanel(Canvas c, float w, float h, float top, String title,
                                    String hint, String action, boolean error) {
            float left = w * .30f;
            float right = w * .70f;
            float bottom = Math.min(h - dp(70), top + dp(action == null || action.isEmpty() ? 210 : 255));
            rounded(c, left, top, right, bottom, dp(28), 0xeeffffff);
            stroke(c, left, top, right, bottom, dp(28), 0x1813212b, dp(1));
            rounded(c, left + dp(28), top + dp(28), left + dp(108), top + dp(34), dp(3), error ? COLOR_PINK : 0xff8ed9e8);
            label(c, ellipsize(title == null || title.isEmpty() ? "暂时没有内容" : title, 30), w / 2f,
                    top + dp(91), dp(25), error ? 0xff8a3b55 : COLOR_TEXT, Paint.Align.CENTER, true);
            label(c, hint, w / 2f, top + dp(130), dp(16), COLOR_MUTED, Paint.Align.CENTER, false);
            if (action != null && !action.isEmpty()) {
                rounded(c, w / 2f - dp(116), bottom - dp(76), w / 2f + dp(116), bottom - dp(25), dp(25), COLOR_PINK);
                label(c, action, w / 2f, bottom - dp(43), dp(16), Color.WHITE, Paint.Align.CENTER, true);
            }
        }

        private void logout() {
            lastSessionState = false;
            pageRequestGeneration++;
            profileRequestGeneration++;
            lastLoadAction = null;
            resetPagination();
            loading = false;
            status = "";
            videos.clear();
            bitmaps.clear();
            accountProfile = null;
            profileFace = null;
            profileBanner = null;
            profileLoading = false;
            profileError = "";
            favoriteFolders.clear();
            favoriteFolderIndex = 0;
            favoriteFoldersLoading = false;
            favoriteFoldersError = "";
            favoriteControlIndex = 0;
            favoriteSortIndex = 0;
            favoriteFilterIndex = 0;
            gridIndex = 0;
            firstRow = 0;
            sidebarIndex = 0;
            rootSection = 0;
            area = AREA_GRID;
            invalidate();
            BiliAuthRepository.logout(result -> { if (!destroyed) invalidate(); });
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float w = getWidth();
            final float h = getHeight();
            drawBackground(canvas, w, h);

            drawNav(canvas, w);
            if (navIndex == 6 && personalMode < 0) {
                if (BiliAuthRepository.hasSession()) drawPersonalRoot(canvas, w, h);
                else drawPersonal(canvas, w, h);
            }
            else if (navIndex == 6 && personalMode == 0) drawAccountPage(canvas, w, h);
            else if (navIndex == 6 && personalMode == 5) drawSettingsPage(canvas, w, h);
            else if (navIndex == 7) drawSearchPage(canvas, w, h);
            else drawVideoHome(canvas, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            c.drawColor(COLOR_BG);
            paint.setShader(new RadialGradient(w * .10f, h * .05f, w * .62f,
                    0xb8d9f7ff, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w * .92f, h * .18f, w * .58f,
                    0xa8ffe3ec, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    0x00ffffff, 0x52ffffff, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }

        private void drawNav(Canvas c, float w) {
            float navW = Math.min(w * .64f, dp(1140));
            float left = (w - navW) / 2f;
            float top = dp(30);
            float height = dp(82);
            rounded(c, left, top, left + navW, top + height, height / 2f, 0xeeffffff);
            stroke(c, left, top, left + navW, top + height, height / 2f, 0x1a13212b, dp(1));
            float cell = navW / nav.length;
            for (int i = 0; i < nav.length; i++) {
                boolean selected = i == navIndex;
                boolean focused = area == AREA_NAV && i == navIndex;
                if (selected) rounded(c, left + cell * i + dp(5), top + dp(5), left + cell * (i + 1) - dp(5), top + height - dp(5), dp(34), COLOR_PINK);
                if (focused) {
                    stroke(c, left + cell * i + dp(1), top + dp(1), left + cell * (i + 1) - dp(1), top + height - dp(1), dp(39), 0x55fb5c91, dp(7));
                    stroke(c, left + cell * i + dp(7), top + dp(7), left + cell * (i + 1) - dp(7), top + height - dp(7), dp(32), 0xeaffffff, dp(2));
                }
                label(c, nav[i], left + cell * (i + .5f), top + dp(52), dp(24), selected ? Color.WHITE : COLOR_TEXT, Paint.Align.CENTER, selected);
            }
        }

        private void drawPageTitle(Canvas c, float w) {
            String[] titles = {"直播广场", "为你推荐", "综合热门", "全站排行榜", "关注动态", "我的收藏", "个人中心", "搜索"};
            String title = titles[navIndex];
            if (navIndex == 6 && personalMode < 0) title = "我的";
            if (navIndex == 6 && personalMode >= 0) title = new String[]{"账号中心", "历史记录", "稍后再看", "我的收藏", "追番追剧", "设置"}[personalMode];
            label(c, title, dp(58), dp(141), dp(34), COLOR_TEXT, Paint.Align.LEFT, true);
            String subtitle = navIndex == 1 ? "发现值得在大屏上看的好内容" : navIndex == 2 ? "此刻全站都在看" : navIndex == 3 ? "按分区查看高热内容" : navIndex == 4 ? "关注的 UP 主最新发布" : navIndex == 5 ? "已收藏的视频内容" : navIndex == 6 && personalMode >= 0 ? "按返回键回到个人中心" : navIndex == 6 ? "账号资料与观看历史" : navIndex == 7 ? "搜视频、番剧、影视、主播、直播间和用户" : "用遥控器轻松浏览";
            label(c, subtitle, dp(58), dp(174), dp(17), COLOR_MUTED, Paint.Align.LEFT, false);
            if (!status.isEmpty()) label(c, status, w - dp(54), dp(160), dp(14), COLOR_PINK, Paint.Align.RIGHT, false);
            if (area == AREA_NAV) label(c, "↑ 刷新", w - dp(54), dp(136), dp(14), Color.rgb(138, 101, 111), Paint.Align.RIGHT, false);
        }

        private void drawVideoHome(Canvas c, float w, float h) {
            if (navIndex == 4 && !videos.isEmpty() && firstRow == 0) {
                drawDynamicGallery(c, w, h);
                return;
            }
            if (navIndex == 5 && BiliAuthRepository.hasSession()
                    && (!favoriteFolders.isEmpty() || favoriteFoldersLoading || !videos.isEmpty())) {
                drawFavoritesGallery(c, w, h);
                return;
            }
            if (!hasFilters()) {
                if (loading && videos.isEmpty()) {
                    drawStatePanel(c, w, h, h * .36f, "内容正在路上", status.isEmpty() ? "请稍候" : status, "", false);
                    return;
                }
                if (videos.isEmpty()) { drawEmptyOrError(c, w, h); return; }
                drawGrid(c, w, h); return;
            }
            float filterTop = dp(150);
            float left = dp(52);
            float gap = dp(12);
            String[] activeFilters = activeFilters();
            float pillW = (w - dp(104) - gap * (activeFilters.length - 1)) / activeFilters.length;
            for (int i = 0; i < activeFilters.length; i++) {
                boolean focused = area == AREA_FILTER && i == filterFocusIndex;
                int bg = i == filterIndex ? COLOR_PINK : COLOR_CARD_SOFT;
                rounded(c, left + i * (pillW + gap), filterTop, left + i * (pillW + gap) + pillW, filterTop + dp(60), dp(30), bg);
                if (focused) {
                    stroke(c, left + i * (pillW + gap) - dp(3), filterTop - dp(3), left + i * (pillW + gap) + pillW + dp(3), filterTop + dp(63), dp(33), 0x55fb5c91, dp(7));
                    stroke(c, left + i * (pillW + gap), filterTop, left + i * (pillW + gap) + pillW, filterTop + dp(60), dp(30), 0xeaffffff, dp(2));
                }
                label(c, activeFilters[i], left + i * (pillW + gap) + pillW / 2f, filterTop + dp(39), dp(19), i == filterIndex ? Color.WHITE : COLOR_TEXT, Paint.Align.CENTER, focused || i == filterIndex);
            }
            if (loading && videos.isEmpty()) {
                drawStatePanel(c, w, h, h * .38f, "内容正在路上", status.isEmpty() ? "请稍候" : status, "", false);
                return;
            }
            if (videos.isEmpty()) { drawEmptyOrError(c, w, h); return; }
            drawGrid(c, w, h);
        }

        private void drawFavoritesGallery(Canvas c, float w, float h) {
            float panelLeft = dp(52), panelTop = dp(150), panelRight = dp(515), panelBottom = h - dp(58);
            rounded(c, panelLeft, panelTop, panelRight, panelBottom, dp(28), 0xeaffffff);
            stroke(c, panelLeft, panelTop, panelRight, panelBottom, dp(28), 0x1813212b, dp(1));

            float avatarSize = dp(94);
            float avatarLeft = panelLeft + dp(28);
            float avatarTop = panelTop + dp(30);
            drawProfileFace(c, avatarLeft, avatarTop, avatarSize);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.WHITE);
            c.drawCircle(avatarLeft + avatarSize / 2f, avatarTop + avatarSize / 2f, avatarSize / 2f + dp(2), paint);
            paint.setStyle(Paint.Style.FILL);
            float textLeft = avatarLeft + avatarSize + dp(20);
            if (accountProfile != null) {
                label(c, ellipsize(accountProfile.name.isEmpty() ? "B 站用户" : accountProfile.name, 10),
                        textLeft, panelTop + dp(72), dp(25), COLOR_TEXT, Paint.Align.LEFT, true);
                label(c, "LV" + accountProfile.level + (accountProfile.vip ? "  VIP" : ""), textLeft,
                        panelTop + dp(103), dp(14), accountProfile.vip ? COLOR_PINK : COLOR_MUTED, Paint.Align.LEFT, false);
            } else {
                label(c, profileLoading ? "正在加载账号…" : "B 站账号", textLeft,
                        panelTop + dp(76), dp(19), COLOR_TEXT, Paint.Align.LEFT, true);
            }

            String[] statLabels = {"硬币", "关注", "粉丝"};
            long[] values = accountProfile == null ? new long[]{0, 0, 0}
                    : new long[]{accountProfile.coins, accountProfile.following, accountProfile.fans};
            float statTop = panelTop + dp(154);
            float statCol = (panelRight - panelLeft - dp(40)) / 3f;
            for (int i = 0; i < statLabels.length; i++) {
                float cx = panelLeft + dp(20) + statCol * i + statCol / 2f;
                label(c, accountProfile == null ? "—" : formatNumber(values[i]), cx, statTop + dp(24), dp(22), COLOR_TEXT, Paint.Align.CENTER, true);
                label(c, statLabels[i], cx, statTop + dp(50), dp(13), COLOR_MUTED, Paint.Align.CENTER, false);
            }

            label(c, "收藏夹", panelLeft + dp(24), panelTop + dp(238), dp(18), COLOR_MUTED, Paint.Align.LEFT, true);
            if (!favoriteFolders.isEmpty()) {
                label(c, (favoriteFolderIndex + 1) + " / " + favoriteFolders.size(), panelRight - dp(24),
                        panelTop + dp(238), dp(14), COLOR_MUTED, Paint.Align.RIGHT, false);
            }
            float rowLeft = panelLeft + dp(20), rowRight = panelRight - dp(20), rowTop = panelTop + dp(258);
            int visibleFolderCount = Math.min(4, favoriteFolders.size());
            int folderStart = Math.max(0, Math.min(favoriteFolderIndex - 2,
                    Math.max(0, favoriteFolders.size() - visibleFolderCount)));
            for (int local = 0; local < visibleFolderCount; local++) {
                int i = folderStart + local;
                BiliRepository.FavoriteFolder folder = favoriteFolders.get(i);
                float y = rowTop + local * dp(66);
                boolean active = i == favoriteFolderIndex;
                boolean focused = area == AREA_FAVORITE_FOLDERS && active;
                rounded(c, rowLeft, y, rowRight, y + dp(58), dp(18), active ? 0xffffe8f0 : 0xfff7fafb);
                if (focused) {
                    stroke(c, rowLeft - dp(4), y - dp(4), rowRight + dp(4), y + dp(62), dp(22), 0x55fb5c91, dp(7));
                    stroke(c, rowLeft, y, rowRight, y + dp(58), dp(18), Color.WHITE, dp(2));
                }
                rounded(c, rowLeft + dp(12), y + dp(10), rowLeft + dp(50), y + dp(48), dp(19), active ? 0xffffd6e4 : 0xffeaf0f2);
                drawSystemIcon(c, R.drawable.ic_tv_favorite, rowLeft + dp(21), y + dp(19), dp(20), active ? COLOR_PINK : COLOR_MUTED);
                label(c, ellipsize(folder.title, 13), rowLeft + dp(62), y + dp(37), dp(18), active ? COLOR_PINK : COLOR_TEXT, Paint.Align.LEFT, active);
                label(c, String.valueOf(folder.mediaCount), rowRight - dp(17), y + dp(37), dp(14), active ? COLOR_PINK : COLOR_MUTED, Paint.Align.RIGHT, active);
            }
            if (favoriteFoldersLoading && favoriteFolders.isEmpty()) {
                label(c, "正在读取收藏夹…", rowLeft + dp(8), rowTop + dp(38), dp(17), COLOR_MUTED, Paint.Align.LEFT, false);
            }

            float infoTop = panelBottom - dp(130);
            rounded(c, rowLeft, infoTop, rowRight, infoTop + dp(106), dp(20), 0xfff7fafb);
            label(c, "当前视图", rowLeft + dp(22), infoTop + dp(34), dp(15), COLOR_MUTED, Paint.Align.LEFT, false);
            String currentFolder = favoriteFolders.isEmpty() ? "收藏视频" : favoriteFolders.get(favoriteFolderIndex).title;
            label(c, ellipsize(currentFolder, 15), rowLeft + dp(22), infoTop + dp(64), dp(20), COLOR_TEXT, Paint.Align.LEFT, true);
            String sortHint = favoriteSortIndex == 0 ? "按收藏时间排列" : "按播放量排列";
            String filterHint = favoriteFilterIndex == 0 ? "全部内容" : "仅可播放";
            label(c, sortHint + " · " + filterHint, rowLeft + dp(22), infoTop + dp(90), dp(13), COLOR_MUTED, Paint.Align.LEFT, false);

            float gridLeft = dp(555);
            label(c, "我的收藏", gridLeft, dp(174), dp(30), COLOR_TEXT, Paint.Align.LEFT, true);
            label(c, favoriteFolders.isEmpty() ? "正在同步收藏夹" : ellipsize(currentFolder, 22), gridLeft, dp(207), dp(15), COLOR_MUTED, Paint.Align.LEFT, false);
            String[] controlText = {favoriteSortIndex == 0 ? "最近收藏  ▾" : "最多播放  ▾",
                    favoriteFilterIndex == 0 ? "全部内容  ▾" : "可播放视频  ▾"};
            float controlW = dp(188), controlGap = dp(14), controlTop = dp(156);
            float controlStart = w - dp(52) - controlW * 2 - controlGap;
            for (int i = 0; i < 2; i++) {
                float x = controlStart + i * (controlW + controlGap);
                boolean focused = area == AREA_FAVORITE_CONTROLS && favoriteControlIndex == i;
                rounded(c, x, controlTop, x + controlW, controlTop + dp(58), dp(29), focused ? 0xffffe8f0 : 0xeaffffff);
                stroke(c, x, controlTop, x + controlW, controlTop + dp(58), dp(29), focused ? COLOR_PINK : 0x2213212b, focused ? dp(3) : dp(1));
                if (focused) stroke(c, x - dp(4), controlTop - dp(4), x + controlW + dp(4), controlTop + dp(62), dp(33), 0x44fb5c91, dp(7));
                label(c, controlText[i], x + controlW / 2f, controlTop + dp(37), dp(16), focused ? COLOR_PINK : COLOR_TEXT, Paint.Align.CENTER, focused);
            }
            if (!videos.isEmpty()) {
                drawThreeColumnGallery(c, w, h, gridLeft, dp(52), dp(18), dp(20), dp(238));
            } else {
                float emptyLeft = gridLeft, emptyRight = w - dp(52), emptyTop = dp(250), emptyBottom = h - dp(86);
                rounded(c, emptyLeft, emptyTop, emptyRight, emptyBottom, dp(28), 0xeaffffff);
                stroke(c, emptyLeft, emptyTop, emptyRight, emptyBottom, dp(28), 0x1813212b, dp(1));
                String message = loading || favoriteFoldersLoading ? "正在加载收藏内容…"
                        : status.isEmpty() ? "这个收藏夹还没有视频" : status;
                boolean error = isErrorMessage(message);
                label(c, ellipsize(message, 28), (emptyLeft + emptyRight) / 2f, emptyTop + dp(132), dp(25),
                        error ? COLOR_PINK : COLOR_TEXT, Paint.Align.CENTER, true);
                label(c, loading || favoriteFoldersLoading ? "请稍候"
                                : error ? "检查网络后按 OK 重新加载" : "可在左侧切换其他收藏夹",
                        (emptyLeft + emptyRight) / 2f, emptyTop + dp(174), dp(16), COLOR_MUTED, Paint.Align.CENTER, false);
                if (error) {
                    float retryW = dp(206), retryLeft = (emptyLeft + emptyRight - retryW) / 2f;
                    rounded(c, retryLeft, emptyTop + dp(204), retryLeft + retryW, emptyTop + dp(252), dp(24), 0xffffe8f0);
                    stroke(c, retryLeft, emptyTop + dp(204), retryLeft + retryW, emptyTop + dp(252), dp(24), COLOR_PINK, dp(2));
                    label(c, "按 OK 重新加载", retryLeft + retryW / 2f, emptyTop + dp(236), dp(15), COLOR_PINK,
                            Paint.Align.CENTER, true);
                }
            }
        }

        private void drawDynamicGallery(Canvas c, float w, float h) {
            label(c, "关注动态", dp(58), dp(101), dp(30), COLOR_TEXT, Paint.Align.LEFT, true);
            label(c, "关注的 UP 主最新发布", dp(58), dp(130), dp(16), COLOR_MUTED, Paint.Align.LEFT, false);

            List<VideoItem> creators = new ArrayList<>();
            for (VideoItem item : videos) {
                boolean exists = false;
                for (VideoItem creator : creators) if (creator.owner.equals(item.owner)) { exists = true; break; }
                if (!exists) creators.add(item);
                if (creators.size() >= 10) break;
            }
            float avatarTop = dp(150);
            float slotW = (w - dp(104)) / 10f;
            for (int i = 0; i < creators.size(); i++) {
                VideoItem item = creators.get(i);
                float cx = dp(52) + slotW * i + slotW / 2f;
                drawRoundImage(c, bitmaps.get(item.avatarUrl), cx, avatarTop + dp(43), dp(43), item.owner);
                rounded(c, cx + dp(27), avatarTop + dp(68), cx + dp(43), avatarTop + dp(84), dp(8), COLOR_PINK);
                label(c, ellipsize(item.owner, 8), cx, avatarTop + dp(112), dp(15), COLOR_TEXT, Paint.Align.CENTER, true);
            }

            float left = dp(52), right = dp(52), gap = dp(22), top = dp(300);
            float available = w - left - right - gap * 2;
            int start = firstRow * 3;
            int count = Math.min(videos.size(), start + 9);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = start; i < count; i++) {
                    boolean current = area == AREA_GRID && gridIndex == i;
                    if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                    int globalRow = i / 3, col = i % 3;
                    float y = top;
                    for (int row = firstRow; row < globalRow; row++) {
                        y += dynamicRowHeight(available, row) + gap;
                    }
                    float totalWeight = 0f;
                    float beforeWeight = 0f;
                    for (int slot = 0; slot < 3; slot++) {
                        float weight = 1f + .62f * gridFocusAmount(globalRow * 3 + slot);
                        totalWeight += weight;
                        if (slot < col) beforeWeight += weight;
                    }
                    float focus = gridFocusAmount(i);
                    float cardW = available * (1f + .62f * focus) / totalWeight;
                    float x = left + available * beforeWeight / totalWeight + col * gap;
                    boolean activeRow = area == AREA_GRID && gridIndex / 3 == globalRow;
                    float imageH = activeRow ? dp(230) + dp(90) * focus : cardW * .42f;
                    drawDynamicCard(c, videos.get(i), x, y - dp(6) * focus, cardW, imageH, focus);
                }
            }
            drawGridFooter(c, w, h);
        }

        private float dynamicRowHeight(float available, int globalRow) {
            boolean activeRow = area == AREA_GRID && gridIndex / 3 == globalRow;
            if (activeRow) {
                float maxFocus = 0f;
                for (int slot = 0; slot < 3; slot++) {
                    maxFocus = Math.max(maxFocus, gridFocusAmount(globalRow * 3 + slot));
                }
                return dp(230) + dp(90) * maxFocus + dp(78);
            }
            return (available / 3f) * .42f + dp(78);
        }

        private void drawDynamicCard(Canvas c, VideoItem item, float x, float y, float cardW,
                                     float imageH, float focus) {
            boolean focused = focus > .02f;
            boolean live = "dynamic_live".equals(item.kind);
            boolean forward = "dynamic_forward".equals(item.kind);
            boolean article = "dynamic_article".equals(item.kind);
            boolean draw = "dynamic_draw".equals(item.kind);
            boolean textOnly = item.coverUrl == null || item.coverUrl.isEmpty();
            float footerH = dp(78);
            rounded(c, x, y, x + cardW, y + imageH + footerH, dp(18), focused ? 0xffffffff : COLOR_CARD);
            stroke(c, x, y, x + cardW, y + imageH + footerH, dp(18), 0x1813212b, dp(1));
            if (focused) {
                stroke(c, x - dp(7), y - dp(7), x + cardW + dp(7), y + imageH + footerH + dp(7), dp(25), focusColor(COLOR_FOCUS, focus), dp(9));
                stroke(c, x - dp(1), y - dp(1), x + cardW + dp(1), y + imageH + footerH + dp(1), dp(19), focusColor(COLOR_PINK, focus), dp(2));
            }
            reusable.set(x, y, x + cardW, y + imageH);
            Bitmap coverBitmap = bitmaps.get(item.coverUrl);
            if (coverBitmap != null) drawCover(c, coverBitmap, reusable);
            else if (textOnly) {
                rounded(c, x, y, x + cardW, y + imageH, dp(17), forward ? 0xfffff2f7 : 0xfffbfcfd);
                String heading = forward ? "转发动态" : live ? "直播动态" : article ? "专栏动态" : "文字动态";
                rounded(c, x + dp(24), y + dp(24), x + dp(128), y + dp(58), dp(17),
                        forward ? 0xffffd6e4 : 0xffe4f5fa);
                label(c, heading, x + dp(76), y + dp(47), dp(14), forward ? COLOR_PINK : 0xff39778a,
                        Paint.Align.CENTER, true);
                label(c, ellipsize(item.title, 28), x + dp(28), y + dp(102), dp(22), COLOR_TEXT, Paint.Align.LEFT, true);
                label(c, ellipsize(item.title, 42), x + dp(28), y + dp(145), dp(16), COLOR_MUTED, Paint.Align.LEFT, false);
                if (forward) {
                    rounded(c, x + dp(24), y + imageH - dp(72), x + cardW - dp(24), y + imageH - dp(22), dp(14), 0xffffffff);
                    label(c, "查看原动态  ›", x + dp(42), y + imageH - dp(40), dp(15), 0xff8a6571, Paint.Align.LEFT, true);
                }
            } else drawFallbackCover(c, reusable, item.title);
            if (!textOnly) {
                String badge = live ? "LIVE" : forward ? "转发" : article ? "专栏" : draw ? "图文" : "视频";
                float badgeW = live ? dp(66) : dp(58);
                rounded(c, x + dp(12), y + dp(12), x + dp(12) + badgeW, y + dp(43), dp(15),
                        live ? 0xffe53958 : forward ? 0xff7c658f : COLOR_PINK);
                label(c, badge, x + dp(12) + badgeW / 2f, y + dp(34), dp(13), Color.WHITE, Paint.Align.CENTER, true);
                rounded(c, x + dp(12), y + imageH - dp(30), x + dp(138), y + imageH - dp(8), dp(11), 0x9b081118);
                label(c, item.stats, x + dp(20), y + imageH - dp(14), dp(12), Color.WHITE, Paint.Align.LEFT, false);
                label(c, live ? "直播中" : item.duration, x + cardW - dp(14), y + imageH - dp(13), dp(13), Color.WHITE, Paint.Align.RIGHT, true);
            }
            drawRoundImage(c, bitmaps.get(item.avatarUrl), x + dp(34), y + imageH + dp(38), dp(22), item.owner);
            int titleChars = Math.max(20, Math.min(28, Math.round(cardW / dp(24))));
            label(c, ellipsize(item.title, titleChars), x + dp(68), y + imageH + dp(31), dp(18), COLOR_TEXT, Paint.Align.LEFT, true);
            label(c, ellipsize(item.owner, 12) + "  " + item.published, x + dp(68), y + imageH + dp(58), dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
        }

        private void drawRoundImage(Canvas c, Bitmap bitmap, float cx, float cy, float radius, String fallback) {
            c.save();
            Path clip = new Path();
            clip.addCircle(cx, cy, radius, Path.Direction.CW);
            c.clipPath(clip);
            if (bitmap != null) {
                RectF dst = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
                int sx = (bitmap.getWidth() - side) / 2;
                int sy = (bitmap.getHeight() - side) / 2;
                paint.setAlpha(255);
                c.drawBitmap(bitmap, new Rect(sx, sy, sx + side, sy + side), dst, paint);
            } else {
                paint.setColor(0xffdce8ec);
                c.drawCircle(cx, cy, radius, paint);
                label(c, fallback == null || fallback.isEmpty() ? "UP" : fallback.substring(0, 1), cx, cy + radius * .28f,
                        radius * .72f, COLOR_MUTED, Paint.Align.CENTER, true);
            }
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xffff8fb0);
            c.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawGrid(Canvas c, float w, float h) {
            int cols = navIndex == 7 ? 4 : 3;
            float left = dp(52);
            float right = dp(52);
            float gapX = dp(22);
            float gapY = dp(22);
            float top = navIndex == 7 ? dp(356) : hasFilters() ? dp(242) : dp(150);
            if (navIndex == 0 && videos.size() <= 2) {
                float showcaseW = Math.min(dp(720), (w - left - right - gapX) / Math.max(1, videos.size()));
                float showcaseImageH = showcaseW * .56f;
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < videos.size(); i++) {
                        boolean current = area == AREA_GRID && i == gridIndex;
                        if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                        float x = left + i * (showcaseW + gapX);
                        float focus = gridFocusAmount(i);
                        float grow = dp(22) * focus;
                        drawVideoCard(c, videos.get(i), x - grow, top - grow,
                                showcaseW + grow * 2, showcaseImageH + grow * 2, focus);
                    }
                }
                drawGridFooter(c, w, h);
                return;
            }
            if (cols == 3) {
                drawThreeColumnGallery(c, w, h, left, right, gapX, gapY, top);
                return;
            }
            float cardW = (w - left - right - gapX * (cols - 1)) / cols;
            float imageH = cardW * .5625f;
            float cardH = imageH + dp(82);
            int visibleRows = Math.max(1, (int) Math.ceil((h - top + gapY) / (cardH + gapY)));
            int start = firstRow * cols;
            int end = Math.min(videos.size(), start + visibleRows * cols);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = start; i < end; i++) {
                    boolean current = area == AREA_GRID && i == gridIndex;
                    if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                    int local = i - start;
                    int row = local / cols;
                    int col = local % cols;
                    float x = left + col * (cardW + gapX);
                    float y = top + row * (cardH + gapY);
                    float focus = gridFocusAmount(i);
                    float grow = dp(22) * focus;
                    drawVideoCard(c, videos.get(i), x - grow, y - grow,
                            cardW + grow * 2, imageH + grow * 2, focus);
                }
            }
            drawGridFooter(c, w, h);
        }

        /**
         * Apple TV 式三列画廊：未聚焦时三张等宽；聚焦后当前卡逐帧扩展，
         * 同行其余卡逐帧收窄。宽度权重总和恒定，因此焦点移动时不会挤出屏幕。
         */
        private void drawThreeColumnGallery(Canvas c, float w, float h, float left, float right,
                                            float gapX, float gapY, float top) {
            final int cols = 3;
            float available = w - left - right - gapX * (cols - 1);
            boolean compact = available < dp(1500);
            float focusedBaseImageH = compact ? dp(200) : dp(260);
            float focusedBonusImageH = compact ? dp(72) : dp(90);
            float regularImageRatio = compact ? .52f : .46f;
            int start = firstRow * cols;
            int visibleRows = 3;
            int end = Math.min(videos.size(), start + visibleRows * cols);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = start; i < end; i++) {
                    boolean current = area == AREA_GRID && i == gridIndex;
                    if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                    int globalRow = i / cols;
                    int col = i % cols;
                    float rowTop = top;
                    for (int row = firstRow; row < globalRow; row++) {
                        rowTop += threeColumnRowHeight(available, row) + gapY;
                    }
                    float totalWeight = 0f;
                    float beforeWeight = 0f;
                    for (int slot = 0; slot < cols; slot++) {
                        float weight = 1f + .62f * gridFocusAmount(globalRow * cols + slot);
                        totalWeight += weight;
                        if (slot < col) beforeWeight += weight;
                    }
                    float focus = gridFocusAmount(i);
                    float cardW = available * (1f + .62f * focus) / totalWeight;
                    float x = left + available * beforeWeight / totalWeight + col * gapX;
                    boolean activeRow = area == AREA_GRID && gridIndex / cols == globalRow;
                    float imageH = activeRow ? focusedBaseImageH + focusedBonusImageH * focus : cardW * regularImageRatio;
                    float lift = dp(6) * focus;
                    drawVideoCard(c, videos.get(i), x, rowTop - lift, cardW, imageH, focus);
                }
            }
            drawGridFooter(c, w, h);
        }

        private float threeColumnRowHeight(float available, int globalRow) {
            boolean compact = available < dp(1500);
            float focusedBaseImageH = compact ? dp(200) : dp(260);
            float focusedBonusImageH = compact ? dp(72) : dp(90);
            float regularImageRatio = compact ? .52f : .46f;
            boolean activeRow = area == AREA_GRID && gridIndex / 3 == globalRow;
            if (activeRow) {
                float maxFocus = 0f;
                for (int slot = 0; slot < 3; slot++) {
                    maxFocus = Math.max(maxFocus, gridFocusAmount(globalRow * 3 + slot));
                }
                return focusedBaseImageH + focusedBonusImageH * maxFocus + dp(82);
            }
            return (available / 3f) * regularImageRatio + dp(82);
        }

        private void drawGridFooter(Canvas c, float w, float h) {
            if (videos.isEmpty()) return;
            String text;
            if (loadingMore) text = "加载更多…";
            else if (moreFailed) text = "加载失败，按 ↓/→ 重试";
            else if (noMore) text = "已经到底了";
            else return;
            float size = dp(16);
            textPaint.setTextSize(size);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            float textWidth = textPaint.measureText(text);
            float right = w - dp(54);
            float baseline = h - dp(20);
            float left = right - textWidth - dp(34);
            float top = baseline - dp(27);
            float bottom = baseline + dp(10);
            rounded(c, left, top, right, bottom, dp(18), Color.argb(200, 255, 255, 255));
            label(c, text, right - dp(17), baseline, size, 0xff52676c, Paint.Align.RIGHT, false);
        }

        private void drawVideoCard(Canvas c, VideoItem item, float x, float y, float cardW, float imageH, float focus) {
            boolean focused = focus > .02f;
            rounded(c, x, y, x + cardW, y + imageH + dp(75), dp(17), focused ? 0xffffffff : COLOR_CARD);
            stroke(c, x, y, x + cardW, y + imageH + dp(75), dp(17), 0x1813212b, dp(1));
            if (focused) {
                stroke(c, x - dp(7), y - dp(7), x + cardW + dp(7), y + imageH + dp(82), dp(24), focusColor(COLOR_FOCUS, focus), dp(9));
                stroke(c, x - dp(1), y - dp(1), x + cardW + dp(1), y + imageH + dp(76), dp(18), focusColor(COLOR_PINK, focus), dp(2));
            }
            reusable.set(x, y, x + cardW, y + imageH);
            Bitmap bitmap = bitmaps.get(item.coverUrl);
            if (bitmap != null) drawCover(c, bitmap, reusable); else drawFallbackCover(c, reusable, item.title);
            rounded(c, x + dp(10), y + imageH - dp(29), x + dp(118), y + imageH - dp(8), dp(10), Color.argb(155, 8, 17, 24));
            label(c, item.stats, x + dp(19), y + imageH - dp(13), dp(12), Color.WHITE, Paint.Align.LEFT, false);
            label(c, item.duration, x + cardW - dp(12), y + imageH - dp(12), dp(13), Color.WHITE, Paint.Align.RIGHT, true);
            if (navIndex == 0) {
                rounded(c, x + dp(10), y + dp(10), x + dp(66), y + dp(36), dp(13), COLOR_PINK);
                label(c, "LIVE", x + dp(38), y + dp(29), dp(12), Color.WHITE, Paint.Align.CENTER, true);
            }
            if (navIndex == 3) {
                rounded(c, x + dp(10), y + dp(10), x + dp(51), y + dp(51), dp(13), 0xe616202a);
                label(c, String.valueOf(Math.max(1, videos.indexOf(item) + 1)), x + dp(30), y + dp(39), dp(19), Color.WHITE, Paint.Align.CENTER, true);
            }
            if (navIndex == 4 || navIndex == 5) {
                String badge = navIndex == 4 ? "动态" : "★ 收藏";
                float badgeW = navIndex == 4 ? dp(60) : dp(78);
                rounded(c, x + dp(10), y + dp(10), x + dp(10) + badgeW, y + dp(37), dp(13),
                        navIndex == 4 ? 0xe616202a : COLOR_PINK);
                label(c, badge, x + dp(10) + badgeW / 2f, y + dp(29), dp(12), Color.WHITE, Paint.Align.CENTER, true);
            }
            int titleChars = Math.max(21, Math.min(28, Math.round(cardW / dp(24))));
            label(c, ellipsize(item.title, titleChars), x + dp(12), y + imageH + dp(30), dp(19), COLOR_TEXT, Paint.Align.LEFT, true);
            label(c, ellipsize(item.owner, 16), x + dp(12), y + imageH + dp(60), dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
        }

        private void drawPersonal(Canvas c, float w, float h) {
            String[] names = BiliAuthRepository.hasSession()
                    ? new String[]{"账号已登录", "历史记录", "稍后再看", "我的收藏", "追番追剧", "设置"}
                    : new String[]{"扫码登录", "历史记录", "稍后再看", "我的收藏", "追番追剧", "设置"};
            float left = dp(62), top = dp(210), gap = dp(23);
            int cols = 3;
            float cardW = (w - dp(124) - gap * 2) / 3f;
            float cardH = dp(155);
            for (int i = 0; i < names.length; i++) {
                int row = i / cols, col = i % cols;
                float x = left + col * (cardW + gap), y = top + row * (cardH + gap);
                boolean focused = area == AREA_GRID && gridIndex == i;
                rounded(c, x, y, x + cardW, y + cardH, dp(22), COLOR_CARD);
                stroke(c, x, y, x + cardW, y + cardH, dp(22), 0x1813212b, dp(1));
                if (focused) {
                    stroke(c, x - dp(5), y - dp(5), x + cardW + dp(5), y + cardH + dp(5), dp(27), COLOR_FOCUS, dp(5));
                    stroke(c, x, y, x + cardW, y + cardH, dp(22), COLOR_PINK, dp(2));
                }
                label(c, names[i], x + dp(22), y + dp(78), dp(28), COLOR_TEXT, Paint.Align.LEFT, true);
                String note = i == 0 ? (BiliAuthRepository.hasSession() ? "可同步 B 站账号数据" : "使用手机端确认登录") : "按确认键进入";
                label(c, note, x + dp(23), y + dp(112), dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
            }
        }

        /** 已登录“我的”根页：左侧资料卡+菜单，右侧 3 列历史记录。 */
        private void drawPersonalRoot(Canvas c, float w, float h) {
            float panelLeft = dp(48);
            float panelRight = dp(600);
            float panelTop = dp(196);
            float panelBottom = h - dp(58);
            rounded(c, panelLeft, panelTop, panelRight, panelBottom, dp(26), 0xf2ffffff);
            stroke(c, panelLeft, panelTop, panelRight, panelBottom, dp(26), 0x1c13212b, dp(1));

            // Profile hero: a compact Pearl Glass banner so the account block reads as a designed header,
            // not a large blank panel when the remote focus is in the menu below.
            c.save();
            Path profileClip = new Path();
            profileClip.addRoundRect(new RectF(panelLeft, panelTop, panelRight, panelTop + dp(168)),
                    dp(26), dp(26), Path.Direction.CW);
            c.clipPath(profileClip);
            if (profileBanner != null) {
                drawCover(c, profileBanner, new RectF(panelLeft, panelTop, panelRight, panelTop + dp(168)));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x28f8fcff);
                c.drawRect(panelLeft, panelTop, panelRight, panelTop + dp(168), paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(255);
                paint.setShader(new LinearGradient(panelLeft, panelTop, panelRight, panelTop + dp(168),
                        0xff8edff5, 0xffff9fc3, Shader.TileMode.CLAMP));
                c.drawRect(panelLeft, panelTop, panelRight, panelTop + dp(168), paint);
                paint.setShader(new RadialGradient(panelRight - dp(90), panelTop + dp(18), dp(210),
                        0x80ffffff, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(panelLeft, panelTop, panelRight, panelTop + dp(168), paint);
            }
            paint.setShader(null);
            paint.setAlpha(255);
            c.restore();
            rounded(c, panelLeft, panelTop + dp(132), panelRight, panelTop + dp(224), dp(24), 0xdfffffff);

            float avatarSize = dp(104);
            float avatarLeft = panelLeft + dp(30);
            float avatarTop = panelTop + dp(88);
            drawProfileFace(c, avatarLeft, avatarTop, avatarSize);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.WHITE);
            c.drawCircle(avatarLeft + avatarSize / 2f, avatarTop + avatarSize / 2f, avatarSize / 2f + dp(2), paint);
            paint.setStyle(Paint.Style.FILL);
            float textLeft = avatarLeft + avatarSize + dp(20);
            if (accountProfile != null) {
                String name = accountProfile.name == null || accountProfile.name.isEmpty() ? "B 站用户" : accountProfile.name;
                label(c, ellipsize(name, 12), textLeft, avatarTop + dp(42), dp(26), COLOR_TEXT, Paint.Align.LEFT, true);
                String badge = "LV" + accountProfile.level + (accountProfile.vip ? "   VIP" : "");
                label(c, badge, textLeft, avatarTop + dp(75), dp(15),
                        accountProfile.vip ? COLOR_PINK : COLOR_MUTED, Paint.Align.LEFT, false);
            } else if (profileLoading) {
                label(c, "正在加载账号资料…", textLeft, avatarTop + dp(50), dp(17), COLOR_MUTED, Paint.Align.LEFT, false);
            } else {
                label(c, profileError.isEmpty() ? "账号资料加载失败" : ellipsize(profileError, 11),
                        textLeft, avatarTop + dp(42), dp(16), COLOR_PINK, Paint.Align.LEFT, false);
                label(c, "按 ↑ 刷新", textLeft, avatarTop + dp(70), dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
            }

            float statTop = panelTop + dp(214);
            String[] statLabels = {"硬币", "关注", "粉丝"};
            long[] statValues = accountProfile == null ? new long[]{0, 0, 0}
                    : new long[]{accountProfile.coins, accountProfile.following, accountProfile.fans};
            float statLeft = panelLeft + dp(26);
            float statCol = (panelRight - panelLeft - dp(52)) / 3f;
            for (int i = 0; i < statLabels.length; i++) {
                float cx = statLeft + statCol * i + statCol / 2f;
                label(c, accountProfile == null ? "—" : formatNumber(statValues[i]), cx, statTop + dp(24), dp(24), COLOR_TEXT, Paint.Align.CENTER, true);
                label(c, statLabels[i], cx, statTop + dp(46), dp(13), COLOR_MUTED, Paint.Align.CENTER, false);
            }

            float menuTop = panelTop + dp(282);
            float rowLeft = panelLeft + dp(20);
            float rowRight = panelRight - dp(20);
            float rowH = dp(56);
            for (int i = 0; i < sidebarMenu.length; i++) {
                float y = menuTop + i * (rowH + dp(10));
                boolean focused = area == AREA_SIDEBAR && sidebarIndex == i;
                boolean active = i == rootSection;
                int rowColor = focused ? 0xffffe8f0 : active ? 0xffffe8f0 : 0x00ffffff;
                rounded(c, rowLeft, y, rowRight, y + rowH, dp(16), rowColor);
                if (focused) {
                    stroke(c, rowLeft - dp(3), y - dp(3), rowRight + dp(3), y + rowH + dp(3), dp(19), 0x55fb5c91, dp(7));
                    stroke(c, rowLeft + dp(1), y + dp(1), rowRight - dp(1), y + rowH - dp(1), dp(15), 0xffffffff, dp(2));
                }
                int color = i == sidebarMenu.length - 1 ? COLOR_PINK : focused || active ? COLOR_PINK : COLOR_TEXT;
                int iconBg = focused || active ? 0xffffd6e4 : 0xffedf2f4;
                rounded(c, rowLeft + dp(12), y + dp(10), rowLeft + dp(48), y + dp(46), dp(18), iconBg);
                drawSystemIcon(c, sidebarIcon(i), rowLeft + dp(20), y + dp(18), dp(20), color);
                label(c, sidebarMenu[i], rowLeft + dp(62), y + rowH / 2f + dp(7), dp(19), color, Paint.Align.LEFT, focused || active);
                label(c, "›", rowRight - dp(18), y + rowH / 2f + dp(8), dp(22), focused ? COLOR_PINK : COLOR_MUTED, Paint.Align.RIGHT, false);
            }

            float gridLeft = dp(640);
            float gridRight = w - dp(56);
            label(c, rootSectionTitle(), gridLeft, dp(210), dp(24), Color.rgb(17, 37, 44), Paint.Align.LEFT, true);
            if (rootSection == 4) {
                drawRootSettings(c, w, h);
                return;
            }
            if (loading && videos.isEmpty()) {
                drawPersonalSkeleton(c, gridLeft, gridRight, h);
                return;
            }
            if (videos.isEmpty()) {
                String message = status.isEmpty() ? rootSectionEmptyText() : status;
                float emptyLeft = gridLeft + dp(80), emptyRight = gridRight - dp(80);
                float emptyTop = h * .36f, emptyBottom = h * .64f;
                rounded(c, emptyLeft, emptyTop, emptyRight, emptyBottom, dp(28), 0xeaffffff);
                stroke(c, emptyLeft, emptyTop, emptyRight, emptyBottom, dp(28), 0x1813212b, dp(1));
                label(c, ellipsize(message, 24), (gridLeft + gridRight) / 2f, emptyTop + dp(82), dp(24),
                        status.isEmpty() ? 0xff60747a : 0xff8a3b55, Paint.Align.CENTER, true);
                String emptyHint = status.isEmpty() ? rootSectionEmptyHint() : "检查网络或账号状态后重新加载";
                label(c, emptyHint, (gridLeft + gridRight) / 2f, emptyTop + dp(122), dp(16), COLOR_MUTED, Paint.Align.CENTER, false);
                rounded(c, (gridLeft + gridRight) / 2f - dp(100), emptyTop + dp(150),
                        (gridLeft + gridRight) / 2f + dp(100), emptyTop + dp(198), dp(24), COLOR_PINK);
                label(c, "按 OK 重新加载", (gridLeft + gridRight) / 2f, emptyTop + dp(181), dp(16), Color.WHITE, Paint.Align.CENTER, true);
                return;
            }
            int cols = 3;
            float gapX = dp(20), gapY = dp(20);
            float gridTop = dp(250);
            float available = gridRight - gridLeft - gapX * (cols - 1);
            int start = firstRow * cols;
            int end = Math.min(videos.size(), start + 6);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = start; i < end; i++) {
                    boolean current = area == AREA_GRID && i == gridIndex;
                    if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                    int globalRow = i / cols;
                    int col = i % cols;
                    float y = gridTop;
                    for (int row = firstRow; row < globalRow; row++) {
                        y += personalRowHeight(available, row) + gapY;
                    }
                    float totalWeight = 0f;
                    float beforeWeight = 0f;
                    for (int slot = 0; slot < cols; slot++) {
                        float weight = 1f + .45f * gridFocusAmount(globalRow * cols + slot);
                        totalWeight += weight;
                        if (slot < col) beforeWeight += weight;
                    }
                    float focus = gridFocusAmount(i);
                    float cardW = available * (1f + .45f * focus) / totalWeight;
                    float x = gridLeft + available * beforeWeight / totalWeight + col * gapX;
                    boolean activeRow = area == AREA_GRID && gridIndex / cols == globalRow;
                    float imageH = activeRow ? dp(210) + dp(60) * focus : cardW * .52f;
                    drawPersonalContentCard(c, videos.get(i), x, y - dp(5) * focus, cardW, imageH, focus);
                }
            }
            drawGridFooter(c, w, h);
        }

        private float personalRowHeight(float available, int globalRow) {
            boolean activeRow = area == AREA_GRID && gridIndex / 3 == globalRow;
            if (activeRow) {
                float maxFocus = 0f;
                for (int slot = 0; slot < 3; slot++) {
                    maxFocus = Math.max(maxFocus, gridFocusAmount(globalRow * 3 + slot));
                }
                return dp(210) + dp(60) * maxFocus + dp(80);
            }
            return (available / 3f) * .52f + dp(80);
        }

        private void drawRootSettings(Canvas c, float w, float h) {
            drawSettingsGrid(c, dp(640), w - dp(56), dp(278));
        }

        private void drawPersonalContentCard(Canvas c, VideoItem item, float x, float y,
                                             float cardW, float imageH, float focus) {
            drawVideoCard(c, item, x, y, cardW, imageH, focus);
            String badge;
            int badgeColor;
            int badgeIcon;
            if ("personal_history".equals(item.kind)) {
                badge = "历史";
                badgeColor = 0xff536b78;
                badgeIcon = R.drawable.ic_tv_history;
                float progress = historyProgress(item.stats, item.duration);
                rounded(c, x + dp(12), y + imageH - dp(10), x + cardW - dp(12), y + imageH - dp(5), dp(3), 0x90ffffff);
                if (progress > 0f) rounded(c, x + dp(12), y + imageH - dp(10),
                        x + dp(12) + (cardW - dp(24)) * progress, y + imageH - dp(5), dp(3), COLOR_PINK);
            } else if ("personal_favorite".equals(item.kind)) {
                badge = "收藏";
                badgeColor = COLOR_PINK;
                badgeIcon = R.drawable.ic_tv_favorite;
            } else if ("personal_watchlater".equals(item.kind)) {
                badge = "待看";
                badgeColor = 0xff4f7f92;
                badgeIcon = R.drawable.ic_tv_watch_later;
            } else if ("personal_bangumi".equals(item.kind)) {
                badge = "追番";
                badgeColor = 0xff8a668f;
                badgeIcon = R.drawable.ic_tv_bangumi;
            } else {
                return;
            }
            float badgeW = dp(76);
            rounded(c, x + dp(12), y + dp(12), x + dp(12) + badgeW, y + dp(43), dp(15), badgeColor);
            drawSystemIcon(c, badgeIcon, x + dp(20), y + dp(20), dp(15), Color.WHITE);
            label(c, badge, x + dp(41), y + dp(34), dp(13), Color.WHITE, Paint.Align.LEFT, true);
            if (item.published != null && !item.published.isEmpty()) {
                label(c, item.published, x + cardW - dp(12), y + imageH + dp(60), dp(13),
                        0xff829198, Paint.Align.RIGHT, false);
            }
        }

        private int sidebarIcon(int index) {
            if (index == 0) return R.drawable.ic_tv_history;
            if (index == 1) return R.drawable.ic_tv_favorite;
            if (index == 2) return R.drawable.ic_tv_watch_later;
            if (index == 3) return R.drawable.ic_tv_bangumi;
            if (index == 4) return R.drawable.ic_tv_settings;
            return R.drawable.ic_tv_logout;
        }

        private void drawSystemIcon(Canvas c, int resourceId, float left, float top, float size, int color) {
            Drawable drawable;
            try {
                drawable = getResources().getDrawable(resourceId, getTheme()).mutate();
            } catch (Exception ignored) {
                return;
            }
            drawable.setTint(color);
            drawable.setBounds(Math.round(left), Math.round(top), Math.round(left + size), Math.round(top + size));
            drawable.draw(c);
        }

        private void drawPersonalSkeleton(Canvas c, float left, float right, float h) {
            label(c, rootSectionLoadingText(), left, dp(242), dp(16), COLOR_MUTED, Paint.Align.LEFT, false);
            int cols = 3;
            float gapX = dp(20), gapY = dp(20), top = dp(268);
            float cardW = (right - left - gapX * (cols - 1)) / cols;
            float imageH = cardW * .56f;
            float cardH = imageH + dp(80);
            float pulse = .55f + .18f * (float) Math.sin(System.currentTimeMillis() / 280.0);
            int block = Color.argb(Math.round(255 * pulse), 226, 233, 236);
            int line = Color.argb(Math.round(220 * pulse), 211, 221, 225);
            int visibleRows = Math.min(2, Math.max(1, (int) Math.ceil((h - top) / (cardH + gapY))));
            for (int row = 0; row < visibleRows; row++) {
                for (int col = 0; col < cols; col++) {
                    float x = left + col * (cardW + gapX);
                    float y = top + row * (cardH + gapY);
                    rounded(c, x, y, x + cardW, y + cardH, dp(17), COLOR_CARD);
                    rounded(c, x, y, x + cardW, y + imageH, dp(17), block);
                    rounded(c, x + dp(14), y + imageH + dp(16), x + cardW * .78f,
                            y + imageH + dp(31), dp(8), line);
                    rounded(c, x + dp(14), y + imageH + dp(46), x + cardW * .48f,
                            y + imageH + dp(57), dp(6), line);
                }
            }
            postInvalidateDelayed(80);
        }

        private float historyProgress(String watched, String total) {
            if (watched == null || !watched.startsWith("看到 ")) return 0f;
            int current = parseClock(watched.substring(3));
            int duration = parseClock(total);
            return duration <= 0 ? 0f : Math.max(0f, Math.min(1f, current / (float) duration));
        }

        private int parseClock(String value) {
            if (value == null || value.isEmpty()) return 0;
            String[] parts = value.split(":");
            int seconds = 0;
            try {
                for (String part : parts) seconds = seconds * 60 + Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) { return 0; }
            return seconds;
        }

        private void drawProfileFace(Canvas c, float left, float top, float size) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffffdbe7);
            c.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint);
            if (profileFace == null) {
                String name = accountProfile == null || accountProfile.name == null ? "B" : accountProfile.name.trim();
                String initial = name.isEmpty() ? "B" : name.substring(0, 1);
                label(c, initial, left + size / 2f, top + size * .68f, size * .43f,
                        COLOR_PINK, Paint.Align.CENTER, true);
                return;
            }
            c.save();
            Path clip = new Path();
            clip.addCircle(left + size / 2f, top + size / 2f, size / 2f, Path.Direction.CW);
            c.clipPath(clip);
            Rect src = new Rect(0, 0, profileFace.getWidth(), profileFace.getHeight());
            RectF dst = new RectF(left, top, left + size, top + size);
            paint.setAlpha(255);
            c.drawBitmap(profileFace, src, dst, paint);
            c.restore();
        }

        private void drawPrivateEmpty(Canvas c, float w, float h) {
            rounded(c, w * .32f, h * .36f, w * .68f, h * .61f, dp(28), COLOR_CARD);
            stroke(c, w * .32f, h * .36f, w * .68f, h * .61f, dp(28), 0x1813212b, dp(1));
            String headline = BiliAuthRepository.hasSession() ? "这里暂时没有内容" : "登录后查看账号内容";
            label(c, headline, w / 2f, h * .45f, dp(30), COLOR_TEXT, Paint.Align.CENTER, true);
            label(c, BiliAuthRepository.hasSession() ? "稍后刷新再看看" : "按确认键打开电视二维码登录", w / 2f, h * .51f, dp(18), COLOR_MUTED, Paint.Align.CENTER, false);
            if (!BiliAuthRepository.hasSession()) {
                rounded(c, w / 2f - dp(105), h * .54f, w / 2f + dp(105), h * .59f, dp(25), COLOR_PINK);
                label(c, "扫码登录", w / 2f, h * .575f, dp(18), Color.WHITE, Paint.Align.CENTER, true);
            }
        }

        private void drawAccountPage(Canvas c, float w, float h) {
            boolean session = BiliAuthRepository.hasSession();
            String[] names;
            String[] notes;
            if (session) {
                names = new String[]{"已登录", "用户 UID  " + BiliAuthRepository.userMid(), "二维码重新登录", "退出登录"};
                notes = new String[]{"B 站账号状态", "普通安卓手机应用身份", "按确认键打开登录", "按确认键清除登录状态"};
            } else {
                names = new String[]{"尚未登录", "手机扫码登录", "登录后同步数据"};
                notes = new String[]{"B 站账号状态", "普通安卓手机应用身份", "按确认键打开登录"};
            }
            drawInfoCards(c, w, names, notes);
        }

        private void drawSettingsPage(Canvas c, float w, float h) {
            drawSettingsGrid(c, dp(92), w - dp(92), dp(230));
        }

        private void drawSettingsGrid(Canvas c, float left, float right, float top) {
            String[] names = {"默认弹幕", "弹幕字号", "自动播放", "弹幕透明度", "优先高清", "弹幕速度",
                    "清除搜索历史", "检测更新", "清除续播进度", "应用信息"};
            String[] notes = {"进入播放器时默认显示弹幕", "播放器中的弹幕文字大小", "进入视频后自动开始播放", "播放器中的弹幕显示浓度",
                    "优先请求当前可用的高清画质", "弹幕横向移动的速度", "移除本机保存的搜索词", "检查 GitHub 上的新版本",
                    "移除本机保存的播放位置", "普通 Android 手机应用身份"};
            float columnGap = dp(28);
            float columnW = (right - left - columnGap) / 2f;
            float rowH = dp(78), rowGap = dp(14);
            float groupGap = dp(42);
            label(c, "播放", left, top - dp(22), dp(17), COLOR_MUTED, Paint.Align.LEFT, true);
            label(c, "弹幕", left + columnW + columnGap, top - dp(22), dp(17), COLOR_MUTED, Paint.Align.LEFT, true);
            for (int i = 0; i < names.length; i++) {
                int row = i / 2, column = i % 2;
                float x = left + column * (columnW + columnGap);
                float y = top + row * (rowH + rowGap) + (row >= 3 ? groupGap : 0f);
                if (i == 6) label(c, "账户与数据", left, y - dp(18), dp(17), COLOR_MUTED, Paint.Align.LEFT, true);
                if (i == 7) label(c, "关于", left + columnW + columnGap, y - dp(18), dp(17), COLOR_MUTED, Paint.Align.LEFT, true);
                boolean focused = area == AREA_GRID && gridIndex == i;
                rounded(c, x, y, x + columnW, y + rowH, dp(18), COLOR_CARD);
                stroke(c, x, y, x + columnW, y + rowH, dp(18), 0x1813212b, dp(1));
                if (focused) {
                    stroke(c, x - dp(4), y - dp(4), x + columnW + dp(4), y + rowH + dp(4), dp(22), 0x55fb5c91, dp(7));
                    stroke(c, x, y, x + columnW, y + rowH, dp(18), 0xff29333c, dp(2));
                }
                label(c, names[i], x + dp(24), y + dp(31), dp(20), COLOR_TEXT, Paint.Align.LEFT, true);
                label(c, notes[i], x + dp(24), y + dp(57), dp(13), COLOR_MUTED, Paint.Align.LEFT, false);
                if (i == 0) drawToggle(c, x + columnW - dp(88), y + dp(21), settings.danmaku(), focused);
                else if (i == 2) drawToggle(c, x + columnW - dp(88), y + dp(21), settings.autoPlay(), focused);
                else if (i == 4) drawToggle(c, x + columnW - dp(88), y + dp(21), settings.highQuality(), focused);
                else drawSettingValue(c, settingValue(i), x + columnW - dp(24), y + dp(41), focused);
            }
            float footer = top + 5 * (rowH + rowGap) + groupGap + dp(13);
            label(c, "OK 打开或切换 · 方向键选择 · 返回键回到我的", left, footer, dp(15), 0xff8a656f, Paint.Align.LEFT, false);
            label(c, "哔哩客厅  " + BuildConfig.VERSION_NAME, right, footer, dp(15), COLOR_MUTED, Paint.Align.RIGHT, false);
        }

        private void drawSettingValue(Canvas c, String value, float right, float centerY, boolean focused) {
            float width = Math.max(dp(74), textPaint.measureText(value) + dp(30));
            rounded(c, right - width, centerY - dp(20), right, centerY + dp(20), dp(20), focused ? 0xffffe5ee : 0xffedf1f3);
            label(c, value, right - width / 2f, centerY + dp(6), dp(14), focused ? 0xffc83e6c : COLOR_MUTED, Paint.Align.CENTER, true);
        }

        private String settingValue(int index) {
            if (index == 1) return new String[]{"小", "中", "大"}[settings.danmakuSize()];
            if (index == 3) return new String[]{"60%", "80%", "100%"}[settings.danmakuOpacity()];
            if (index == 5) return new String[]{"慢", "标准", "快"}[settings.danmakuSpeed()];
            if (index == 7) return BuildConfig.VERSION_NAME + "  ›";
            if (index == 9) return "手机版声明  ›";
            return "执行  ›";
        }

        private void drawInfoCards(Canvas c, float w, String[] names, String[] notes) {
            float left = dp(62), top = dp(220), gap = dp(24);
            float cardW = (w - dp(124) - gap) / 2f, cardH = dp(150);
            for (int i = 0; i < names.length; i++) {
                int row = i / 2, col = i % 2; float x = left + col * (cardW + gap), y = top + row * (cardH + gap);
                boolean focused = area == AREA_GRID && gridIndex == i;
                rounded(c, x, y, x + cardW, y + cardH, dp(23), COLOR_CARD);
                stroke(c, x, y, x + cardW, y + cardH, dp(23), 0x1813212b, dp(1));
                if (focused) {
                    stroke(c, x - dp(5), y - dp(5), x + cardW + dp(5), y + cardH + dp(5), dp(28), COLOR_FOCUS, dp(5));
                    stroke(c, x, y, x + cardW, y + cardH, dp(23), COLOR_PINK, dp(2));
                }
                label(c, names[i], x + dp(24), y + dp(72), dp(27), COLOR_TEXT, Paint.Align.LEFT, true);
                label(c, notes[Math.min(i, notes.length - 1)], x + dp(24), y + dp(108), dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
            }
        }

        private void drawSearchPage(Canvas c, float w, float h) {
            float boxLeft = dp(88), boxTop = dp(140), boxRight = w - dp(88), boxBottom = boxTop + dp(110);
            rounded(c, boxLeft, boxTop, boxRight, boxBottom, dp(30), Color.argb(242, 255, 255, 255));
            stroke(c, boxLeft, boxTop, boxRight, boxBottom, dp(30), area == AREA_SEARCH ? 0x55fb5c91 : 0xffd7e1e4, area == AREA_SEARCH ? dp(7) : dp(2));
            if (area == AREA_SEARCH) stroke(c, boxLeft + dp(5), boxTop + dp(5), boxRight - dp(5), boxBottom - dp(5), dp(25), 0xeaffffff, dp(2));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(4)); paint.setColor(area == AREA_SEARCH ? COLOR_PINK : COLOR_MUTED);
            c.drawCircle(boxLeft + dp(50), boxTop + dp(49), dp(17), paint);
            c.drawLine(boxLeft + dp(62), boxTop + dp(62), boxLeft + dp(76), boxTop + dp(76), paint); paint.setStyle(Paint.Style.FILL);
            String value = searchKeyword.isEmpty() ? "输入关键词搜索 B 站" : searchKeyword;
            label(c, value, boxLeft + dp(104), boxTop + dp(68), dp(30), searchKeyword.isEmpty() ? 0xff839397 : 0xff1f2d32, Paint.Align.LEFT, !searchKeyword.isEmpty());
            rounded(c, boxRight - dp(152), boxTop + dp(17), boxRight - dp(18), boxBottom - dp(17), dp(35), COLOR_PINK);
            label(c, "搜索", boxRight - dp(85), boxTop + dp(67), dp(20), Color.WHITE, Paint.Align.CENTER, true);

            float left = dp(52), top = dp(280), gap = dp(12);
            float pillW = (w - dp(104) - gap * (searchFilters.length - 1)) / searchFilters.length;
            for (int i = 0; i < searchFilters.length; i++) {
                boolean focused = area == AREA_FILTER && i == filterFocusIndex;
                int bg = i == filterIndex ? COLOR_PINK : COLOR_CARD_SOFT;
                rounded(c, left + i * (pillW + gap), top, left + i * (pillW + gap) + pillW, top + dp(48), dp(24), bg);
                if (focused) {
                    stroke(c, left + i * (pillW + gap) - dp(3), top - dp(3), left + i * (pillW + gap) + pillW + dp(3), top + dp(51), dp(27), 0x55fb5c91, dp(7));
                    stroke(c, left + i * (pillW + gap), top, left + i * (pillW + gap) + pillW, top + dp(48), dp(24), 0xeaffffff, dp(2));
                }
                label(c, searchFilters[i], left + i * (pillW + gap) + pillW / 2f, top + dp(32), dp(17), i == filterIndex ? Color.WHITE : COLOR_TEXT, Paint.Align.CENTER, focused || i == filterIndex);
            }
            if (loading && videos.isEmpty()) {
                drawStatePanel(c, w, h, h * .44f, "正在搜索“" + ellipsize(searchKeyword, 14) + "”…", "正在整理当前分类的结果", "", false);
            } else if (videos.isEmpty()) {
                if (searchKeyword.isEmpty()) {
                    drawSearchSuggest(c, w, h);
                } else {
                    String empty = status;
                    if (empty.isEmpty()) empty = "当前分类没有找到内容";
                    boolean error = isErrorMessage(empty);
                    drawStatePanel(c, w, h, h * .44f, empty,
                            error ? "检查网络后重新搜索" : "试试更短的关键词或切换分类",
                            error ? "按 OK 重新搜索" : "", error);
                }
            } else drawSearchResults(c, w, h);
        }

        /** Search has several genuinely different result types; each gets a TV-sized card instead of a generic video tile. */
        private void drawSearchResults(Canvas c, float w, float h) {
            if (filterIndex == 0 && firstRow == 0 && videos.size() >= 4) {
                drawGroupedAllSearchResults(c, w, h);
                return;
            }
            int cols = gridColumns();
            float left = dp(52), right = dp(52), gapX = dp(22), gapY = dp(22), top = dp(356);
            boolean posterMode = filterIndex == 2 || filterIndex == 3;
            boolean peopleMode = filterIndex == 4 || filterIndex == 6;
            float cardW = (w - left - right - gapX * (cols - 1)) / cols;
            float imageH = posterMode ? dp(342) : peopleMode ? dp(128) : cardW * .5625f;
            float footerH = posterMode ? dp(82) : peopleMode ? dp(62) : dp(75);
            float cardH = imageH + footerH;
            int visibleRows = Math.max(1, (int) Math.ceil((h - top + gapY) / (cardH + gapY)));
            int start = firstRow * cols;
            int end = Math.min(videos.size(), start + visibleRows * cols);

            for (int pass = 0; pass < 2; pass++) {
                for (int i = start; i < end; i++) {
                    boolean current = area == AREA_GRID && i == gridIndex;
                    if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                    int local = i - start;
                    int row = local / cols;
                    int col = local % cols;
                    float x = left + col * (cardW + gapX);
                    float y = top + row * (cardH + gapY);
                    float focus = gridFocusAmount(i);
                    float grow = (posterMode ? dp(14) : peopleMode ? dp(16) : dp(18)) * focus;
                    drawSearchResultCard(c, videos.get(i), x - grow, y - grow,
                            cardW + grow * 2, imageH + grow * 2, focus, posterMode, peopleMode);
                }
            }
            drawGridFooter(c, w, h);
        }

        private void drawGroupedAllSearchResults(Canvas c, float w, float h) {
            float left = dp(52), right = dp(52), gap = dp(18);
            float top = dp(396);
            float posterW = dp(245);
            float videoW = (w - left - right - gap * 3 - posterW) / 3f;
            float imageH = dp(252);
            float posterImageH = dp(252);
            label(c, "视频", left, dp(371), dp(18), COLOR_TEXT, Paint.Align.LEFT, true);
            label(c, "番剧 / 影视", w - right - posterW, dp(371), dp(18), COLOR_TEXT, Paint.Align.LEFT, true);

            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 4; i++) {
                    boolean current = area == AREA_GRID && i == gridIndex;
                    if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                    float x = i < 3 ? left + i * (videoW + gap) : w - right - posterW;
                    float baseW = i < 3 ? videoW : posterW;
                    float focus = gridFocusAmount(i);
                    float grow = dp(14) * focus;
                    boolean poster = "media_bangumi".equals(videos.get(i).kind) || "media_ft".equals(videos.get(i).kind);
                    drawSearchResultCard(c, videos.get(i), x - grow, top - grow,
                            baseW + grow * 2, (poster ? posterImageH : imageH) + grow * 2,
                            focus, poster, false);
                }
            }

            if (videos.size() > 4) {
                float compactTop = dp(808);
                int end = Math.min(8, videos.size());
                boolean hasPeopleOrLive = false;
                for (int i = 4; i < end; i++) {
                    String kind = videos.get(i).kind;
                    if ("bili_user".equals(kind) || "live_user".equals(kind) || "live_room".equals(kind)) {
                        hasPeopleOrLive = true;
                        break;
                    }
                }
                label(c, hasPeopleOrLive ? "用户与直播" : "更多结果", left, compactTop - dp(18), dp(18), COLOR_TEXT, Paint.Align.LEFT, true);
                float cardW = (w - left - right - gap * 3) / 4f;
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 4; i < end; i++) {
                        boolean current = area == AREA_GRID && i == gridIndex;
                        if ((pass == 0 && current) || (pass == 1 && !current)) continue;
                        float x = left + (i - 4) * (cardW + gap);
                        drawSearchCompactAllCard(c, videos.get(i), x, compactTop, cardW, dp(126), gridFocusAmount(i));
                    }
                }
            }
            drawGridFooter(c, w, h);
        }

        private void drawSearchCompactAllCard(Canvas c, VideoItem item, float x, float y,
                                              float cardW, float cardH, float focus) {
            float lift = dp(5) * focus;
            y -= lift;
            rounded(c, x, y, x + cardW, y + cardH, dp(20), Color.WHITE);
            stroke(c, x, y, x + cardW, y + cardH, dp(20), 0x1813212b, dp(1));
            if (focus > .02f) {
                stroke(c, x - dp(6), y - dp(6), x + cardW + dp(6), y + cardH + dp(6), dp(25), focusColor(COLOR_FOCUS, focus), dp(8));
                stroke(c, x, y, x + cardW, y + cardH, dp(20), focusColor(COLOR_PINK, focus), dp(2));
            }
            Bitmap bitmap = bitmaps.get(item.coverUrl);
            float art = dp(96);
            reusable.set(x + dp(14), y + dp(15), x + dp(14) + art, y + dp(15) + art);
            if ("bili_user".equals(item.kind) || "live_user".equals(item.kind)) {
                drawRoundImage(c, bitmap, x + dp(14) + art / 2f, y + dp(15) + art / 2f, art / 2f, item.title);
            } else if (bitmap != null) drawCover(c, bitmap, reusable);
            else drawFallbackCover(c, reusable, item.title);
            float textLeft = x + dp(128);
            label(c, ellipsize(item.title, 13), textLeft, y + dp(42), dp(18), COLOR_TEXT, Paint.Align.LEFT, true);
            String secondary = item.stats == null || item.stats.isEmpty() ? item.owner : item.stats;
            label(c, ellipsize(secondary, 14), textLeft, y + dp(70), dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
            String badge = searchBadge(item);
            float badgeW = dp(Math.max(68, 28 + badge.length() * 14));
            rounded(c, textLeft, y + dp(82), textLeft + badgeW, y + dp(108), dp(13),
                    "LIVE".equals(badge) ? 0xffe53958 : 0xffffe3ec);
            label(c, badge, textLeft + badgeW / 2f, y + dp(100), dp(12),
                    "LIVE".equals(badge) ? Color.WHITE : COLOR_PINK, Paint.Align.CENTER, true);
        }

        private void drawSearchResultCard(Canvas c, VideoItem item, float x, float y, float cardW,
                                          float imageH, float focus, boolean posterMode, boolean peopleMode) {
            boolean focused = focus > .02f;
            boolean person = peopleMode || "bili_user".equals(item.kind) || "live_user".equals(item.kind);
            boolean poster = posterMode;
            float footerH = poster ? dp(82) : person ? dp(62) : dp(75);
            rounded(c, x, y, x + cardW, y + imageH + footerH, dp(18), focused ? Color.WHITE : COLOR_CARD);
            stroke(c, x, y, x + cardW, y + imageH + footerH, dp(18), 0x1813212b, dp(1));
            if (focused) {
                stroke(c, x - dp(7), y - dp(7), x + cardW + dp(7), y + imageH + footerH + dp(7),
                        dp(25), focusColor(COLOR_FOCUS, focus), dp(9));
                stroke(c, x - dp(1), y - dp(1), x + cardW + dp(1), y + imageH + footerH + dp(1),
                        dp(19), focusColor(COLOR_PINK, focus), dp(2));
            }

            Bitmap bitmap = bitmaps.get(item.coverUrl);
            if (person) {
                paint.setShader(new LinearGradient(x, y, x + cardW, y + imageH,
                        0xffffffff, 0xfffff1f6, Shader.TileMode.CLAMP));
                c.drawRoundRect(x, y, x + cardW, y + imageH, dp(17), dp(17), paint);
                paint.setShader(null);
                float avatarRadius = Math.min(imageH * .34f, dp(48));
                drawRoundImage(c, bitmap, x + dp(30) + avatarRadius, y + imageH / 2f, avatarRadius, item.title);
                label(c, ellipsize(item.title, 16), x + dp(46) + avatarRadius * 2, y + imageH * .43f,
                        dp(21), COLOR_TEXT, Paint.Align.LEFT, true);
                label(c, ellipsize(item.owner, 22), x + dp(46) + avatarRadius * 2, y + imageH * .68f,
                        dp(14), COLOR_MUTED, Paint.Align.LEFT, false);
                if ("live_user".equals(item.kind)) {
                    int badgeColor = "正在直播".equals(item.stats) ? COLOR_PINK : 0xff93a0a6;
                    rounded(c, x + cardW - dp(112), y + dp(22), x + cardW - dp(18), y + dp(55), dp(16), badgeColor);
                    label(c, item.stats, x + cardW - dp(65), y + dp(44), dp(13), Color.WHITE, Paint.Align.CENTER, true);
                }
                label(c, item.stats, x + dp(18), y + imageH + dp(28), dp(14), COLOR_PINK, Paint.Align.LEFT, true);
                label(c, item.duration, x + cardW - dp(18), y + imageH + dp(28), dp(14), COLOR_MUTED, Paint.Align.RIGHT, false);
                label(c, "进入主页  ›", x + cardW - dp(18), y + imageH + dp(51), dp(13), COLOR_MUTED, Paint.Align.RIGHT, false);
                return;
            }

            reusable.set(x, y, x + cardW, y + imageH);
            if (bitmap != null) drawCover(c, bitmap, reusable); else drawFallbackCover(c, reusable, item.title);
            String badge = searchBadge(item);
            float badgeW = dp(Math.max(58, 22 + badge.length() * 16));
            rounded(c, x + dp(10), y + dp(10), x + dp(10) + badgeW, y + dp(40), dp(15),
                    "LIVE".equals(badge) ? 0xffe53958 : COLOR_PINK);
            label(c, badge, x + dp(10) + badgeW / 2f, y + dp(31), dp(13), Color.WHITE, Paint.Align.CENTER, true);
            if (!poster) {
                rounded(c, x + dp(10), y + imageH - dp(29), x + dp(126), y + imageH - dp(8), dp(10), 0x9b081118);
                label(c, item.stats, x + dp(18), y + imageH - dp(13), dp(12), Color.WHITE, Paint.Align.LEFT, false);
                label(c, item.duration, x + cardW - dp(12), y + imageH - dp(12), dp(13), Color.WHITE, Paint.Align.RIGHT, true);
            }
            label(c, ellipsize(item.title, poster ? 15 : 22), x + dp(12), y + imageH + dp(30),
                    poster ? dp(17) : dp(19), COLOR_TEXT, Paint.Align.LEFT, true);
            label(c, ellipsize(poster ? item.stats : item.owner, poster ? 15 : 16), x + dp(12), y + imageH + dp(59),
                    dp(14), poster ? COLOR_PINK : COLOR_MUTED, Paint.Align.LEFT, false);
        }

        private String searchBadge(VideoItem item) {
            if ("media_bangumi".equals(item.kind)) return "番剧";
            if ("media_ft".equals(item.kind)) return "影视";
            if ("live_room".equals(item.kind)) return "LIVE";
            if ("live_user".equals(item.kind)) return "主播";
            if ("bili_user".equals(item.kind)) return "用户";
            return "视频";
        }

        /** TV 搜索落地页：搜索历史 + 热门搜索 chip（2 行网格，D-pad 可直接选择）。 */
        private void drawSearchSuggest(Canvas c, float w, float h) {
            float left = dp(52), right = w - dp(52);
            float y = dp(368);
            int historyCount = suggestHistory.size();
            if (historyCount > 0) {
                label(c, "搜索历史", left, y + dp(13), dp(18), 0xff253237, Paint.Align.LEFT, true);
                y += dp(28);
                List<String> historyDisplay = new ArrayList<>(suggestHistory);
                historyDisplay.add("清空历史");
                y = drawChipGrid(c, w, historyDisplay, 0, 4, left, right, y, 13);
                y += dp(30);
            }
            if (!hotKeywords.isEmpty()) {
                label(c, "热门搜索", left, y + dp(13), dp(18), 0xff253237, Paint.Align.LEFT, true);
                y += dp(28);
                drawChipGrid(c, w, hotKeywords, suggestHistorySlotCount(), 6, left, right, y, 9);
            } else if (hotLoading) {
                label(c, "热门搜索加载中…", left, y + dp(13), dp(17), 0xff60747a, Paint.Align.LEFT, false);
            } else if (!hotError.isEmpty()) {
                label(c, "热门搜索加载失败，按 OK 手动输入", left, y + dp(13), dp(16), 0xff8a656f, Paint.Align.LEFT, false);
            } else if (historyCount == 0) {
                label(c, "暂无热门搜索，按 OK 输入关键词", left, y + dp(13), dp(16), 0xff8a656f, Paint.Align.LEFT, false);
            }
            String hint = "←/→ 选择 · ↑ 分类 · OK 搜索";
            label(c, hint, w / 2f, h - dp(46), dp(16), 0xff8a656f, Paint.Align.CENTER, false);
        }

        private float drawChipGrid(Canvas c, float w, List<String> keywords, int startIndex, int columns,
                                   float left, float right, float top, int maxChars) {
            float gap = dp(14);
            float chipW = (right - left - gap * (columns - 1)) / columns;
            float chipH = dp(56);
            float rowGap = dp(12);
            for (int i = 0; i < keywords.size(); i++) {
                int row = i / columns;
                int col = i % columns;
                float x = left + col * (chipW + gap);
                float chipTop = top + row * (chipH + rowGap);
                boolean focused = area == AREA_SUGGEST && startIndex + i == suggestIndex;
                rounded(c, x, chipTop, x + chipW, chipTop + chipH, dp(28),
                        focused ? 0xffffe8f0 : COLOR_CARD);
                if (focused) stroke(c, x - dp(3), chipTop - dp(3), x + chipW + dp(3), chipTop + chipH + dp(3), dp(31), 0x55fb5c91, dp(7));
                label(c, ellipsize(keywords.get(i), maxChars), x + chipW / 2f, chipTop + dp(35), dp(16),
                        focused ? 0xffc83e6c : 0xff2b3a3f, Paint.Align.CENTER, focused);
            }
            int rows = Math.max(1, (keywords.size() + columns - 1) / columns);
            return top + rows * (chipH + rowGap);
        }

        private int suggestHistorySlotCount() {
            return suggestHistory.size() + (suggestHistory.isEmpty() ? 0 : 1);
        }

        private boolean isClearHistoryIndex(int index) {
            return !suggestHistory.isEmpty() && index == suggestHistory.size();
        }

        private int suggestTotalCount() {
            return suggestHistorySlotCount() + hotKeywords.size();
        }

        private boolean hasVisibleSuggest() {
            return navIndex == 7 && searchKeyword.isEmpty() && videos.isEmpty() && !loading && suggestTotalCount() > 0;
        }

        private void refreshSuggestHistory() {
            suggestHistory.clear();
            List<String> values = searchHistory.keywords();
            if (values == null) return;
            for (String value : values) {
                if (value == null || value.trim().isEmpty()) continue;
                suggestHistory.add(value);
                if (suggestHistory.size() >= 8) break;
            }
            if (suggestIndex >= suggestTotalCount()) suggestIndex = Math.max(0, suggestTotalCount() - 1);
        }

        private void ensureHotSearches() {
            if (hotLoading || !hotKeywords.isEmpty()) return;
            hotLoading = true;
            hotError = "";
            invalidate();
            repository.loadHotSearches((keywords, error) -> {
                if (destroyed) return;
                hotLoading = false;
                hotKeywords.clear();
                if (keywords != null) {
                    for (String keyword : keywords) {
                        if (keyword == null || keyword.trim().isEmpty()) continue;
                        hotKeywords.add(keyword);
                        if (hotKeywords.size() >= 12) break;
                    }
                }
                hotError = error == null ? "" : error;
                if (suggestIndex >= suggestTotalCount()) suggestIndex = Math.max(0, suggestTotalCount() - 1);
                invalidate();
            });
        }

        private void moveSuggestHorizontal(int delta) {
            int total = suggestTotalCount();
            if (total <= 0) { area = AREA_SEARCH; return; }
            if (suggestIndex >= total) suggestIndex = total - 1;
            int historySlots = suggestHistorySlotCount();
            boolean inHistory = suggestIndex < historySlots;
            int row = inHistory ? suggestIndex / 4 : (suggestIndex - historySlots) / 6;
            int start = inHistory ? row * 4 : historySlots + row * 6;
            int end = inHistory ? Math.min(historySlots - 1, start + 3) : Math.min(total - 1, start + 5);
            suggestIndex = clamp(suggestIndex + delta, start, end);
        }

        private void moveSuggestVertical(int delta) {
            int historySlots = suggestHistorySlotCount();
            int hotCount = hotKeywords.size();
            int total = historySlots + hotCount;
            if (total <= 0) { area = AREA_SEARCH; return; }
            if (suggestIndex >= total) suggestIndex = total - 1;
            boolean inHistory = suggestIndex < historySlots;
            int col = inHistory ? suggestIndex % 4 : (suggestIndex - historySlots) % 6;
            int row = inHistory ? suggestIndex / 4 : (suggestIndex - historySlots) / 6;
            if (delta < 0) {
                if (row > 0) suggestIndex = suggestIndexInRow(inHistory, row - 1, col);
                else if (!inHistory && historySlots > 0) suggestIndex = suggestIndexInRow(true, (historySlots - 1) / 4, col);
                else area = AREA_FILTER;
            } else {
                int rows = inHistory ? (historySlots + 3) / 4 : (hotCount + 5) / 6;
                if (row < rows - 1) suggestIndex = suggestIndexInRow(inHistory, row + 1, col);
                else if (inHistory && hotCount > 0) suggestIndex = suggestIndexInRow(false, 0, col);
                else invalidate();
            }
        }

        private int suggestIndexInRow(boolean history, int row, int col) {
            int historySlots = suggestHistorySlotCount();
            if (history) {
                int start = row * 4;
                int end = Math.min(historySlots - 1, start + 3);
                return clamp(start + Math.min(col, 3), start, Math.max(start, end));
            }
            int start = historySlots + row * 6;
            int end = Math.min(historySlots + hotKeywords.size() - 1, start + 5);
            return clamp(start + Math.min(col, 5), start, Math.max(start, end));
        }

        private void selectSuggestChip() {
            int total = suggestTotalCount();
            if (total <= 0) { area = AREA_SEARCH; invalidate(); return; }
            if (suggestIndex >= total) suggestIndex = total - 1;
            int historyCount = suggestHistory.size();
            int historySlots = suggestHistorySlotCount();
            if (isClearHistoryIndex(suggestIndex)) {
                clearSearchHistory();
                return;
            }
            String keyword = suggestIndex < historyCount
                    ? suggestHistory.get(suggestIndex)
                    : hotKeywords.get(suggestIndex - historySlots);
            if (keyword == null || keyword.trim().isEmpty()) return;
            searchKeyword = keyword;
            navIndex = 7;
            area = AREA_GRID;
            performSearch();
        }

        private void clearSearchHistory() {
            searchHistory.clear();
            refreshSuggestHistory();
            suggestIndex = 0;
            area = suggestTotalCount() > 0 ? AREA_SUGGEST : AREA_SEARCH;
            invalidate();
        }

        private void drawCover(Canvas c, Bitmap bitmap, RectF dst) {
            int sw = bitmap.getWidth(), sh = bitmap.getHeight();
            float target = dst.width() / dst.height();
            Rect src;
            if ((float) sw / sh > target) {
                int crop = (int) (sh * target);
                src = new Rect((sw - crop) / 2, 0, (sw + crop) / 2, sh);
            } else {
                int crop = (int) (sw / target);
                src = new Rect(0, (sh - crop) / 2, sw, (sh + crop) / 2);
            }
            c.save();
            Path path = new Path(); path.addRoundRect(dst, dp(15), dp(15), Path.Direction.CW); c.clipPath(path);
            paint.setAlpha(255);
            c.drawBitmap(bitmap, src, dst, paint); c.restore();
        }

        private void drawFallbackCover(Canvas c, RectF box, String title) {
            c.save();
            Path clip = new Path();
            clip.addRoundRect(box, dp(15), dp(15), Path.Direction.CW);
            c.clipPath(clip);
            paint.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                    navIndex == 4 ? 0xff203849 : 0xff2c3944,
                    navIndex == 4 ? 0xff6a4d68 : 0xff526774, Shader.TileMode.CLAMP));
            c.drawRect(box, paint);
            paint.setShader(null);
            c.restore();
            label(c, "bilibili", box.centerX(), box.centerY() - dp(4), dp(26), Color.WHITE, Paint.Align.CENTER, true);
            label(c, ellipsize(title, 15), box.centerX(), box.centerY() + dp(27), dp(13), Color.rgb(225, 232, 234), Paint.Align.CENTER, false);
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveHorizontal(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveHorizontal(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP: moveVertical(-1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: moveVertical(1); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER: activate(); return true;
                case KeyEvent.KEYCODE_MENU: showSearch(); return true;
                case KeyEvent.KEYCODE_BACK:
                    if (navIndex == 6 && personalMode >= 0) {
                        personalMode = -1;
                        lastLoadAction = null;
                        resetPagination();
                        videos.clear();
                        bitmaps.clear();
                        loading = false;
                        status = "";
                        gridIndex = 0;
                        firstRow = 0;
                        if (BiliAuthRepository.hasSession()) {
                            sidebarIndex = 0;
                            area = AREA_SIDEBAR;
                            loadPersonalRoot();
                        } else {
                            clearPersonalRootState();
                            area = AREA_GRID;
                            invalidate();
                        }
                        return true;
                    }
                    // Back 层级：内容页 → 推荐首页；首页再按一次才交给系统退出。
                    if (navIndex != 1) { navIndex = 1; area = AREA_NAV; filterIndex = 0; filterFocusIndex = 0; switchTopPageOnFocus(); return true; }
                    if (area != AREA_NAV) { area = AREA_NAV; invalidate(); return true; }
                    return super.onKeyDown(keyCode, event);
                default: return super.onKeyDown(keyCode, event);
            }
        }

        private void moveHorizontal(int delta) {
            int oldArea = area;
            int oldGridIndex = gridIndex;
            if (area == AREA_NAV) {
                int next = clamp(navIndex + delta, 0, nav.length - 1);
                if (next != navIndex) {
                    navIndex = next;
                    switchTopPageOnFocus();
                }
            } else if (area == AREA_SIDEBAR) {
                if (delta > 0) {
                    area = AREA_GRID;
                    gridIndex = 0;
                    firstRow = 0;
                } else {
                    area = AREA_NAV;
                }
            } else if (area == AREA_FAVORITE_FOLDERS) {
                if (delta > 0 && !videos.isEmpty()) {
                    area = AREA_GRID;
                    gridIndex = 0;
                    firstRow = 0;
                } else if (delta < 0) {
                    area = AREA_NAV;
                }
            } else if (area == AREA_FAVORITE_CONTROLS) {
                favoriteControlIndex = clamp(favoriteControlIndex + delta, 0, 1);
            } else if (area == AREA_SEARCH) {
                invalidate();
            } else if (area == AREA_SUGGEST) {
                moveSuggestHorizontal(delta);
            } else if (area == AREA_FILTER) {
                int count = activeFilters().length;
                int before = filterFocusIndex;
                filterFocusIndex = clamp(filterFocusIndex + delta, 0, count - 1);
                // 二级导航：焦点移动即切换分类并进入对应页面，无需再按确认键。
                if (filterFocusIndex != before) applyFilterSelection();
            } else {
                if (isPersonalRoot() && delta < 0 && gridIndex % gridColumns() == 0) {
                    area = AREA_SIDEBAR;
                    finishFocusMove(oldArea, oldGridIndex);
                    invalidate();
                    return;
                }
                if (navIndex == 5 && delta < 0 && gridIndex % gridColumns() == 0
                        && !favoriteFolders.isEmpty()) {
                    area = AREA_FAVORITE_FOLDERS;
                    finishFocusMove(oldArea, oldGridIndex);
                    invalidate();
                    return;
                }
                int before = gridIndex;
                int columns = gridColumns();
                int row = gridIndex / columns;
                int col = gridIndex % columns;
                int nextCol = clamp(col + delta, 0, columns - 1);
                gridIndex = clamp(row * columns + nextCol, 0, Math.max(0, maxGridIndex()));
                ensureVisible();
                // 已在最后一项仍按 →：尝试加载下一页（不移动焦点）。
                if (delta > 0 && !videos.isEmpty() && before >= videos.size() - 1) maybeLoadMore();
            }
            finishFocusMove(oldArea, oldGridIndex);
            invalidate();
        }

        private void moveVertical(int delta) {
            // 焦点在顶部导航栏时按 ↑：刷新当前页；其它区域 ↑ 保持原焦点移动逻辑。
            if (delta < 0 && area == AREA_NAV) {
                refreshCurrentPage();
                return;
            }
            int oldArea = area;
            int oldGridIndex = gridIndex;
            int columns = gridColumns();
            if (delta < 0) {
                if (area == AREA_GRID && gridIndex / columns > 0) { gridIndex = Math.max(0, gridIndex - columns); ensureVisible(); }
                else if (area == AREA_SIDEBAR) {
                    if (sidebarIndex > 0) sidebarIndex--;
                    else area = AREA_NAV;
                }
                else if (area == AREA_FAVORITE_FOLDERS) {
                    if (favoriteFolderIndex > 0) selectFavoriteFolder(favoriteFolderIndex - 1);
                    else area = AREA_NAV;
                }
                else if (area == AREA_FAVORITE_CONTROLS) area = AREA_NAV;
                else if (area == AREA_GRID && navIndex == 5 && gridIndex / columns == 0) {
                    area = AREA_FAVORITE_CONTROLS;
                    favoriteControlIndex = 0;
                }
                else if (area == AREA_GRID && hasFilters()) area = AREA_FILTER;
                else if (area == AREA_SUGGEST) moveSuggestVertical(-1);
                else if (area == AREA_FILTER && navIndex == 7) area = AREA_SEARCH;
                else if (area == AREA_SEARCH) area = AREA_NAV;
                else if (area == AREA_GRID || area == AREA_FILTER) area = AREA_NAV;
            } else {
                if (area == AREA_NAV) {
                    if (navIndex == 6 && isPersonalRoot()) {
                        area = AREA_SIDEBAR;
                        sidebarIndex = 0;
                    } else if (navIndex == 5 && !favoriteFolders.isEmpty()) {
                        area = AREA_FAVORITE_FOLDERS;
                    } else {
                        area = navIndex == 7 ? AREA_SEARCH : hasFilters() ? AREA_FILTER : AREA_GRID;
                        if (area == AREA_FILTER) filterFocusIndex = filterIndex;
                    }
                }
                else if (area == AREA_SIDEBAR) {
                    if (sidebarIndex < sidebarMenu.length - 1) sidebarIndex++;
                }
                else if (area == AREA_FAVORITE_FOLDERS) {
                    if (favoriteFolderIndex < favoriteFolders.size() - 1) {
                        selectFavoriteFolder(favoriteFolderIndex + 1);
                    }
                }
                else if (area == AREA_FAVORITE_CONTROLS) {
                    if (!videos.isEmpty()) { area = AREA_GRID; gridIndex = 0; firstRow = 0; }
                    else area = AREA_FAVORITE_FOLDERS;
                }
                else if (area == AREA_SEARCH) area = AREA_FILTER;
                else if (area == AREA_SUGGEST) moveSuggestVertical(1);
                else if (area == AREA_FILTER) {
                    if (navIndex == 7 && hasVisibleSuggest() && videos.isEmpty() && searchKeyword.isEmpty()) {
                        area = AREA_SUGGEST;
                        suggestIndex = Math.min(suggestIndex, Math.max(0, suggestTotalCount() - 1));
                    } else area = AREA_GRID;
                }
                else {
                    // 已在最后一项仍按 ↓：尝试加载下一页，同时保持焦点不越界。
                    if (atLastVideo()) maybeLoadMore();
                    int max = maxGridIndex();
                    gridIndex = Math.min(Math.max(0, max), gridIndex + columns); ensureVisible();
                }
            }
            finishFocusMove(oldArea, oldGridIndex);
            invalidate();
        }

        private void ensureVisible() {
            int row = gridIndex / gridColumns();
            if (navIndex == 7 && filterIndex == 0 && row <= 1) {
                firstRow = 0;
                return;
            }
            if (row < firstRow) firstRow = row;
            // 放大后的焦点卡高度会超出第二可见行；进入下一行时把该行滚到顶部，
            // 保证完整封面、标题、作者与柔光描边都留在安全区内。
            if (row > firstRow) firstRow = row;
        }

        private void activate() {
            if (area == AREA_NAV && canRetryEmptyState()) { lastLoadAction.run(); return; }
            if (area == AREA_SIDEBAR) { activateSidebar(); return; }
            if (area == AREA_FAVORITE_FOLDERS) {
                if (!videos.isEmpty()) { area = AREA_GRID; gridIndex = 0; firstRow = 0; }
                else if (canRetryEmptyState()) reloadSelectedFavoriteFolder();
                invalidate();
                return;
            }
            if (area == AREA_FAVORITE_CONTROLS) {
                if (favoriteControlIndex == 0) favoriteSortIndex = (favoriteSortIndex + 1) % 2;
                else favoriteFilterIndex = (favoriteFilterIndex + 1) % 2;
                reloadSelectedFavoriteFolder();
                invalidate();
                return;
            }
            if (area == AREA_NAV) {
                if (navIndex == 7) { area = AREA_SEARCH; invalidate(); return; }
                if (navIndex == 5 && !favoriteFolders.isEmpty()) area = AREA_FAVORITE_FOLDERS;
                else if (navIndex >= 4) area = AREA_GRID;
                else { filterFocusIndex = filterIndex; area = AREA_FILTER; }
                invalidate(); return;
            }
            if (area == AREA_SEARCH) { showSearch(); return; }
            if (area == AREA_SUGGEST) { selectSuggestChip(); return; }
            if (area == AREA_FILTER) {
                applyFilterSelection();
                return;
            }
            if ((navIndex == 4 || navIndex == 5) && videos.isEmpty() && !BiliAuthRepository.hasSession()) startActivity(new Intent(MainActivity.this, LoginActivity.class));
            else if (navIndex == 6 && personalMode < 0) {
                if (isPersonalRoot()) {
                    if (rootSection == 4) {
                        activateSetting(gridIndex);
                        return;
                    }
                    if (canRetryEmptyState()) { lastLoadAction.run(); return; }
                    if (!videos.isEmpty() && gridIndex < videos.size()) open(videos.get(gridIndex).targetUrl);
                } else {
                    openPersonal(gridIndex);
                }
            }
            else if (navIndex == 6 && personalMode >= 1 && personalMode <= 4 && videos.isEmpty() && !BiliAuthRepository.hasSession()) startActivity(new Intent(MainActivity.this, LoginActivity.class));
            else if (navIndex == 6 && personalMode == 0) {
                if (!BiliAuthRepository.hasSession()) startActivity(new Intent(MainActivity.this, LoginActivity.class));
                else if (gridIndex == 3) logout();
                else if (gridIndex == 2) startActivity(new Intent(MainActivity.this, LoginActivity.class));
            }
            else if (navIndex == 6 && personalMode == 5) activateSetting(gridIndex);
            else if (canRetryEmptyState()) lastLoadAction.run();
            else if (navIndex == 7 && videos.isEmpty()) { area = AREA_SEARCH; invalidate(); }
            else if (!videos.isEmpty() && gridIndex < videos.size()) open(videos.get(gridIndex).targetUrl);
        }

        private void activateSidebar() {
            if (!isPersonalRoot()) return;
            if (sidebarIndex < 0 || sidebarIndex >= sidebarModes.length) return;
            if (sidebarIndex >= sidebarModes.length - 1) {
                logout();
                return;
            }
            selectRootSection(sidebarIndex);
        }

        /** 左侧菜单切换右侧功能区（原地显示，不跳页）。 */
        /** 应用当前二级导航分类；焦点移动时直接调用，无需确认键。 */
        private void applyFilterSelection() {
            filterIndex = filterFocusIndex;
            if (navIndex == 7) {
                if (!searchKeyword.isEmpty()) performSearch();
                else invalidate();
                return;
            }
            if (navIndex == 3) {
                loadRank(rankIds[filterIndex], rankFilters[filterIndex]);
            } else if (navIndex == 0) {
                if (filterIndex == 0) loadLive();
                else loadLiveArea(liveAreaIds[filterIndex], liveFilters[filterIndex]);
            } else if (filterIndex == 0) {
                if (navIndex == 2) loadPopular();
                else loadRecommend();
            } else loadRegion(regionIds[filterIndex], filters[filterIndex]);
        }

        private void selectRootSection(int section) {
            if (!isPersonalRoot()) return;
            rootSection = clamp(section, 0, 4);
            sidebarIndex = rootSection;
            area = AREA_SIDEBAR;
            loadRootSection();
            invalidate();
        }

        private void switchTopPageOnFocus() {
            gridIndex = 0; firstRow = 0; filterIndex = 0; filterFocusIndex = 0;
            resetPagination();
            if (navIndex == 0) loadLive();
            else if (navIndex == 1) loadRecommend();
            else if (navIndex == 2) loadPopular();
            else if (navIndex == 3) loadRank(rankIds[0], rankFilters[0]);
            else if (navIndex == 4) loadDynamic();
            else if (navIndex == 5) loadFavorites();
            else if (navIndex == 7) {
                personalMode = -1;
                refreshSuggestHistory();
                ensureHotSearches();
                if (searchKeyword.isEmpty()) { pageRequestGeneration++; lastLoadAction = null; videos.clear(); bitmaps.clear(); loading = false; status = ""; area = AREA_NAV; invalidate(); }
                else performSearch();
            } else {
                personalMode = -1;
                sidebarIndex = 0;
                rootSection = 0;
                if (BiliAuthRepository.hasSession()) {
                    loadPersonalRoot();
                } else {
                    clearPersonalRootState();
                    invalidate();
                }
            }
        }

        private String[] activeFilters() {
            if (navIndex == 0) return liveFilters;
            if (navIndex == 3) return rankFilters;
            if (navIndex == 7) return searchFilters;
            return filters;
        }

        private boolean hasFilters() { return (navIndex >= 0 && navIndex <= 3) || navIndex == 7; }

        private int gridColumns() {
            if (navIndex == 6 && personalMode < 0) return rootSection == 4 ? 2 : 3;
            if (navIndex == 6 && (personalMode == 0 || personalMode == 5)) return 2;
            if (navIndex == 7) {
                if (filterIndex == 2 || filterIndex == 3) return 6;
                if (filterIndex == 4 || filterIndex == 6) return 3;
                return 4;
            }
            return 3;
        }

        private int maxGridIndex() {
            if (navIndex == 6 && personalMode < 0) return isPersonalRoot() ? (rootSection == 4 ? 9 : videos.size() - 1) : 5;
            if (navIndex == 6 && personalMode == 0) return BiliAuthRepository.hasSession() ? 3 : 2;
            if (navIndex == 6 && personalMode == 5) return 9;
            return videos.size() - 1;
        }

        private void openLoggedIn(String url) {
            if (BiliAuthRepository.hasSession()) open(url);
            else startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }

        private void openPersonal(int index) {
            personalMode = index; gridIndex = 0; firstRow = 0; status = ""; lastLoadAction = null;
            pageRequestGeneration++;
            resetPagination();
            if (index >= 1 && index <= 4) loadPersonalVideos(index);
            else { videos.clear(); bitmaps.clear(); loading = false; invalidate(); }
        }

        private void activateSetting(int index) {
            if (index == 0) settings.setDanmaku(!settings.danmaku());
            else if (index == 1) settings.setDanmakuSize((settings.danmakuSize() + 1) % 3);
            else if (index == 2) settings.setAutoPlay(!settings.autoPlay());
            else if (index == 3) settings.setDanmakuOpacity((settings.danmakuOpacity() + 1) % 3);
            else if (index == 4) settings.setHighQuality(!settings.highQuality());
            else if (index == 5) settings.setDanmakuSpeed((settings.danmakuSpeed() + 1) % 3);
            else if (index == 6) {
                searchHistory.clear();
                refreshSuggestHistory();
                Toast.makeText(MainActivity.this, "搜索历史已清除", Toast.LENGTH_SHORT).show();
            } else if (index == 7) {
                startActivity(new Intent(MainActivity.this, UpdateActivity.class));
            } else if (index == 8) {
                playbackResume.clearAll();
                Toast.makeText(MainActivity.this, "本地续播进度已清除", Toast.LENGTH_SHORT).show();
            } else if (index == 9) {
                Toast.makeText(MainActivity.this, "哔哩客厅 " + BuildConfig.VERSION_NAME + " · Android 手机版声明", Toast.LENGTH_LONG).show();
            }
            invalidate();
        }

        /** 大屏输入弹层：约屏宽 80%、深色背景、放大字号，保留系统 IME 作为手动输入兜底。 */
        private void showSearch() {
            final Dialog dialog = new Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding((int) dp(38), (int) dp(26), (int) dp(38), (int) dp(26));
            root.setBackgroundColor(0xfff6f8fa);

            TextView title = new TextView(MainActivity.this);
            title.setText("搜索 B 站");
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(26));
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setPadding(0, 0, 0, (int) dp(14));
            root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final EditText input = new EditText(MainActivity.this);
            input.setText(searchKeyword);
            input.setHint("输入视频、番剧、影视、主播或用户名称");
            input.setHintTextColor(0xff82919a);
            input.setTextColor(COLOR_TEXT);
            input.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(28));
            input.setSingleLine(true);
            input.setSelectAllOnFocus(true);
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setInputType(EditorInfo.TYPE_CLASS_TEXT);
            input.setPadding((int) dp(18), (int) dp(12), (int) dp(18), (int) dp(12));
            GradientDrawable inputBackground = new GradientDrawable();
            inputBackground.setColor(Color.WHITE);
            inputBackground.setCornerRadius(dp(18));
            inputBackground.setStroke((int) dp(3), COLOR_PINK);
            input.setBackground(inputBackground);
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            inputParams.bottomMargin = (int) dp(18);
            root.addView(input, inputParams);

            LinearLayout actions = new LinearLayout(MainActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);
            Button cancel = new Button(MainActivity.this);
            cancel.setText("取消");
            cancel.setFocusable(true);
            cancel.setTextColor(COLOR_TEXT);
            cancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(20));
            cancel.setBackgroundTintList(ColorStateList.valueOf(0xffe6ebee));
            Button submit = new Button(MainActivity.this);
            submit.setText("搜索");
            submit.setFocusable(true);
            submit.setTextColor(Color.WHITE);
            submit.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(20));
            submit.setBackgroundTintList(ColorStateList.valueOf(COLOR_PINK));
            submit.setId(View.generateViewId());
            input.setNextFocusDownId(submit.getId());
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cancelParams.rightMargin = (int) dp(10);
            actions.addView(cancel, cancelParams);
            actions.addView(submit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            cancel.setOnClickListener(v -> dialog.dismiss());
            submit.setOnClickListener(v -> submitSearchInput(dialog, input));
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    submitSearchInput(dialog, input);
                    return true;
                }
                return false;
            });

            dialog.setContentView(root);
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(0xfff6f8fa));
                int screenWidth = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
                window.setLayout((int) (screenWidth * .8f), ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            }
        }

        private void submitSearchInput(Dialog dialog, EditText input) {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) return;
            dialog.dismiss();
            searchKeyword = keyword;
            navIndex = 7;
            area = AREA_GRID;
            performSearch();
        }

        private void performSearch() {
            resetPagination();
            if (searchKeyword.isEmpty()) { lastLoadAction = null; area = AREA_SEARCH; loading = false; videos.clear(); bitmaps.clear(); invalidate(); return; }
            searchHistory.add(searchKeyword);
            refreshSuggestHistory();
            lastLoadAction = this::performSearch;
            final int generation = ++pageRequestGeneration;
            loading = true; status = "正在搜索“" + searchKeyword + "”·" + searchFilters[filterIndex] + "…";
            videos.clear(); bitmaps.clear(); gridIndex = 0; firstRow = 0; invalidate();
            repository.search(searchKeyword, searchTypes[filterIndex], (items, message) -> {
                if (generation == pageRequestGeneration) setVideos(items, message);
            });
        }

        private void open(String url) {
            String value = url == null ? "" : url;
            if (value.contains("/video/")) {
                Intent intent = new Intent(MainActivity.this, VideoDetailActivity.class);
                intent.putExtra(VideoDetailActivity.EXTRA_TARGET, value);
                startActivity(intent);
                return;
            }
            boolean media = value.contains("live.bilibili.com/") || value.contains("/bangumi/play/");
            if (!media && value.contains("space.bilibili.com/")) {
                long mid = parseSpaceMid(value);
                if (mid > 0) {
                    Intent intent = new Intent(MainActivity.this, UserSpaceActivity.class);
                    intent.putExtra(UserSpaceActivity.EXTRA_MID, mid);
                    long roomId = parseRoomId(value);
                    if (roomId > 0) intent.putExtra(UserSpaceActivity.EXTRA_ROOM_ID, roomId);
                    startActivity(intent);
                    return;
                }
            }
            if (!media && value.contains("t.bilibili.com/")) {
                String dynamicId = parseDynamicId(value);
                if (!dynamicId.isEmpty()) {
                    Intent intent = new Intent(MainActivity.this, DynamicDetailActivity.class);
                    intent.putExtra(DynamicDetailActivity.EXTRA_DYNAMIC_ID, dynamicId);
                    startActivity(intent);
                    return;
                }
            }
            Intent intent = new Intent(MainActivity.this, media ? PlayerActivity.class : BrowserActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_URL, value);
            if (media) {
                intent.putExtra(PlayerActivity.EXTRA_DANMAKU, settings.danmaku());
                intent.putExtra(PlayerActivity.EXTRA_AUTOPLAY, settings.autoPlay());
                intent.putExtra(PlayerActivity.EXTRA_HIGH_QUALITY, settings.highQuality());
            }
            startActivity(intent);
        }

        private long parseSpaceMid(String url) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("space\\.bilibili\\.com/(\\d+)").matcher(url);
            if (!matcher.find()) return 0;
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private long parseRoomId(String url) {
            try {
                Uri uri = Uri.parse(url);
                String value = uri.getQueryParameter("room");
                if (value == null || value.isEmpty()) value = uri.getQueryParameter("room_id");
                if (value != null && !value.isEmpty()) return Long.parseLong(value);
            } catch (Exception ignored) {
            }
            return 0;
        }

        private String parseDynamicId(String url) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("t\\.bilibili\\.com/(\\d+)").matcher(url);
            if (matcher.find()) return matcher.group(1);
            try {
                String value = Uri.parse(url).getQueryParameter("id");
                if (value != null && !value.isEmpty()) return value;
            } catch (Exception ignored) {
            }
            return "";
        }

        private void rounded(Canvas c, float l, float t, float r, float b, float radius, int color) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(color); c.drawRoundRect(l, t, r, b, radius, radius, paint);
        }

        private void stroke(Canvas c, float l, float t, float r, float b, float radius, int color, float width) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); paint.setColor(color); c.drawRoundRect(l, t, r, b, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
        }

        private void drawToggle(Canvas c, float left, float top, boolean enabled, boolean rowFocused) {
            float width = dp(64), height = dp(36);
            rounded(c, left, top, left + width, top + height, height / 2f,
                    enabled ? COLOR_PINK : (rowFocused ? 0xff59636c : 0xffc8d0d5));
            paint.setColor(Color.WHITE);
            float radius = dp(14);
            float cx = enabled ? left + width - dp(18) : left + dp(18);
            c.drawCircle(cx, top + height / 2f, radius, paint);
        }

        private void label(Canvas c, String value, float x, float y, float size, int color, Paint.Align align, boolean bold) {
            textPaint.setTextSize(size); textPaint.setColor(color); textPaint.setTextAlign(align);
            textPaint.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            c.drawText(value, x, y, textPaint);
        }

        private String ellipsize(String text, int max) { return text.length() > max ? text.substring(0, max - 1) + "…" : text; }
        private String formatNumber(long value) {
            if (value >= 100000000L) return String.format(java.util.Locale.CHINA, "%.1f亿", value / 100000000f);
            if (value >= 10000L) return String.format(java.util.Locale.CHINA, "%.1f万", value / 10000f);
            return String.valueOf(value);
        }
        private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
        private float dp(float value) {
            if (getWidth() <= 0 || getHeight() <= 0) return value;
            return value * Math.min(getWidth() / 1920f, getHeight() / 1080f);
        }
    }
}
