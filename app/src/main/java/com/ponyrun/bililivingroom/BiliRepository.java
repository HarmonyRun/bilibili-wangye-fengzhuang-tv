package com.ponyrun.bililivingroom;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BiliRepository {
    interface VideosCallback { void onResult(List<VideoItem> videos, String error); }
    interface ImageCallback { void onResult(Bitmap bitmap); }

    /** 分页结果：cursor 为空表示没有下一页。 */
    static final class PageResult {
        final List<VideoItem> items;
        final String cursor;
        final String error;
        PageResult(List<VideoItem> items, String cursor, String error) {
            this.items = items == null || items.isEmpty()
                    ? Collections.<VideoItem>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(items));
            this.cursor = cursor == null ? "" : cursor;
            this.error = error;
        }
        static PageResult empty(String error) {
            return new PageResult(new ArrayList<>(), "", error);
        }
    }

    interface PageCallback { void onResult(PageResult result); }
    interface HotSearchCallback { void onResult(List<String> keywords, String error); }

    /** UP 主/用户卡片信息（原生 UP 页头部）。 */
    static final class UserCard {
        final long mid;
        final String name;
        final String sign;
        final String face;
        final long fans;
        final int videos;
        UserCard(long mid, String name, String sign, String face, long fans, int videos) {
            this.mid = mid;
            this.name = name == null ? "" : name;
            this.sign = sign == null ? "" : sign;
            this.face = face == null ? "" : face;
            this.fans = fans;
            this.videos = videos;
        }
    }

    interface UserCardCallback { void onResult(UserCard card, String error); }

    /** 投稿分页回调：hasMore 表示是否还有下一页。 */
    interface VideoPageCallback { void onResult(List<VideoItem> items, boolean hasMore, String error); }

    /** 动态详情（原生动态页）。 */
    static final class DynamicDetail {
        final String id;
        final String authorName;
        final String authorFace;
        final String text;
        final String pubTime;
        final List<String> imageUrls;
        final String kind;
        final String originalAuthor;
        final String originalText;
        DynamicDetail(String id, String authorName, String authorFace, String text, String pubTime,
                      List<String> imageUrls, String kind, String originalAuthor, String originalText) {
            this.id = id == null ? "" : id;
            this.authorName = authorName == null ? "" : authorName;
            this.authorFace = authorFace == null ? "" : authorFace;
            this.text = text == null ? "" : text;
            this.pubTime = pubTime == null ? "" : pubTime;
            this.imageUrls = imageUrls == null || imageUrls.isEmpty()
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(imageUrls));
            this.kind = kind == null ? "" : kind;
            this.originalAuthor = originalAuthor == null ? "" : originalAuthor;
            this.originalText = originalText == null ? "" : originalText;
        }
    }

    interface DynamicDetailCallback { void onResult(DynamicDetail detail, String error); }

    /** 直播间信息（原生直播间页）。 */
    static final class LiveRoomInfo {
        final long roomId;
        final long uid;
        final String title;
        final String uname;
        final String cover;
        final int liveStatus;
        final long online;
        LiveRoomInfo(long roomId, long uid, String title, String uname, String cover,
                     int liveStatus, long online) {
            this.roomId = roomId;
            this.uid = uid;
            this.title = title == null ? "" : title;
            this.uname = uname == null ? "" : uname;
            this.cover = cover == null ? "" : cover;
            this.liveStatus = liveStatus;
            this.online = online;
        }
    }

    interface LiveRoomCallback { void onResult(LiveRoomInfo info, String error); }

    /** 视频详情页的分P信息（page 从 1 开始，与 view.pages 顺序一致）。 */
    static final class VideoPart {
        final int page;
        final String title;
        final long cid;
        final int durationSec;
        final String cover;
        VideoPart(int page, String title, long cid, int durationSec, String cover) {
            this.page = page;
            this.title = title == null ? "" : title;
            this.cid = cid;
            this.durationSec = durationSec;
            this.cover = cover == null ? "" : cover;
        }
    }

    /** 视频详情（原生视频详情页）。 */
    static final class VideoDetail {
        final String bvid;
        final long aid;
        final long cid;
        final String title;
        final String desc;
        final String pic;
        final long pubdate;
        final int durationSec;
        final String ownerName;
        final String ownerFace;
        final long ownerMid;
        final long view;
        final long danmaku;
        final long reply;
        final long like;
        final long coin;
        final long favorite;
        /** 分P列表：单P 或接口未返回时为空列表。 */
        final List<VideoPart> parts;
        VideoDetail(String bvid, long aid, long cid, String title, String desc, String pic,
                    long pubdate, int durationSec, String ownerName, String ownerFace, long ownerMid,
                    long view, long danmaku, long reply, long like, long coin, long favorite,
                    List<VideoPart> parts) {
            this.bvid = bvid == null ? "" : bvid;
            this.aid = aid;
            this.cid = cid;
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.pic = pic == null ? "" : pic;
            this.pubdate = pubdate;
            this.durationSec = durationSec;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.ownerFace = ownerFace == null ? "" : ownerFace;
            this.ownerMid = ownerMid;
            this.view = view;
            this.danmaku = danmaku;
            this.reply = reply;
            this.like = like;
            this.coin = coin;
            this.favorite = favorite;
            this.parts = parts == null || parts.isEmpty()
                    ? Collections.<VideoPart>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(parts));
        }
    }

    interface VideoDetailCallback { void onResult(VideoDetail detail, String error); }

    interface RelatedVideosCallback { void onResult(List<VideoItem> items, String error); }

    /** 点赞/投币/稍后再看/收藏等动作结果。 */
    interface VideoActionCallback { void onResult(boolean success, String message); }

    /** 账号资料（“我的”页左栏）：昵称/头像/硬币/关注/粉丝/等级/VIP。 */
    static final class AccountProfile {
        final String name;
        final String face;
        final String topPhoto;
        final long mid;
        final long coins;
        final long following;
        final long fans;
        final int level;
        final boolean vip;
        AccountProfile(String name, String face, String topPhoto, long mid, long coins, long following, long fans,
                       int level, boolean vip) {
            this.name = name == null ? "" : name;
            this.face = face == null ? "" : face;
            this.topPhoto = topPhoto == null ? "" : topPhoto;
            this.mid = mid;
            this.coins = coins;
            this.following = following;
            this.fans = fans;
            this.level = level;
            this.vip = vip;
        }
    }

    interface AccountProfileCallback { void onResult(AccountProfile profile, String error); }

    /** 账号创建的收藏夹目录；用于收藏页左栏真实导航。 */
    static final class FavoriteFolder {
        final long id;
        final String title;
        final int mediaCount;
        final String cover;
        FavoriteFolder(long id, String title, int mediaCount, String cover) {
            this.id = id;
            this.title = title == null || title.trim().isEmpty() ? "未命名收藏夹" : title.trim();
            this.mediaCount = Math.max(0, mediaCount);
            this.cover = cover == null ? "" : cover;
        }
    }

    interface FavoriteFoldersCallback { void onResult(List<FavoriteFolder> folders, String error); }

    /** WBI 签名需要的两个 key。 */
    private static final class WbiKeys {
        final String imgKey;
        final String subKey;
        WbiKeys(String imgKey, String subKey) {
            this.imgKey = imgKey;
            this.subKey = subKey;
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService priorityImageExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> imageCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Bitmap>(40, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > 36;
                }
            });
    // 播放可用性正缓存：identity -> 过期时间戳，避免每次搜索重复校验同一批视频。
    private final Map<String, Long> availabilityCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 96;
                }
            });
    // 排行榜 ranking/v2 一次返回约 100 条且不支持服务端分页：按 rid 缓存完整列表后本地切片，只保留最近 4 个榜单。
    private final Map<Integer, List<VideoItem>> rankCache = Collections.synchronizedMap(
            new LinkedHashMap<Integer, List<VideoItem>>(8, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Integer, List<VideoItem>> eldest) {
                    return size() > 4;
                }
            });
    // 稍后再看接口一次返回完整列表：首次拉取后缓存，本地按页切片。
    private volatile List<VideoItem> watchLaterCache;
    // WBI 签名 key（nav 接口取得）：imgKey/subKey + 获取时间，默认缓存 8 小时。
    private static final long WBI_KEY_TTL_MS = 8 * 60 * 60 * 1000L;
    private volatile String wbiImgKey = "";
    private volatile String wbiSubKey = "";
    private volatile long wbiFetchedAt = 0L;

    /** 清空分页缓存，让排行榜/稍后再看的“刷新”真正重新请求网络。 */
    void clearPagingCaches() {
        rankCache.clear();
        watchLaterCache = null;
    }

    /** 页面销毁时释放线程池，避免 Activity 重复重建累积线程。 */
    void shutdown() {
        executor.shutdownNow();
        imageExecutor.shutdownNow();
        priorityImageExecutor.shutdownNow();
    }

    void loadPopular(VideosCallback callback) {
        loadPopularPage(1, callback);
    }

    /** 热门分页，page 从 1 开始。 */
    void loadPopularPage(int page, VideosCallback callback) {
        load("https://api.bilibili.com/x/web-interface/popular?pn=" + pageOf(page) + "&ps=24", callback);
    }

    void loadRank(int rid, VideosCallback callback) {
        load("https://api.bilibili.com/x/web-interface/ranking/v2?type=all&rid=" + rid, callback);
    }

    /**
     * 排行榜分页：ranking/v2 不支持服务端分页，首次拉取完整列表后按 rid 缓存，本地按 24 条/页切片。
     * page 从 1 开始；cursor 为下一页页码，空表示没有下一页。失败时空 items + error。
     */
    void loadRankPage(int rid, int page, PageCallback callback) {
        final int safePage = pageOf(page);
        executor.execute(() -> {
            try {
                List<VideoItem> all = rankCache.get(rid);
                if (all == null) {
                    all = fetchVideoList("https://api.bilibili.com/x/web-interface/ranking/v2?type=all&rid=" + rid);
                    if (!all.isEmpty()) rankCache.put(rid, all);
                }
                final PageResult result = slicePage(all, safePage);
                main.post(() -> callback.onResult(result));
            } catch (Exception e) {
                main.post(() -> callback.onResult(PageResult.empty("排行榜加载失败，请稍后重试")));
            }
        });
    }

    void loadRegion(int rid, VideosCallback callback) {
        loadRegionPage(rid, 1, callback);
    }

    /** 分区列表分页，page 从 1 开始。 */
    void loadRegionPage(int rid, int page, VideosCallback callback) {
        load("https://api.bilibili.com/x/web-interface/newlist?pn=" + pageOf(page) + "&ps=24&rid=" + rid, callback);
    }

    void loadLive(VideosCallback callback) {
        loadLivePage(1, callback);
    }

    /** 直播推荐分页，page 从 1 开始。 */
    void loadLivePage(int page, VideosCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.live.bilibili.com/xlive/web-interface/v1/webMain/getList?platform=web&page="
                        + pageOf(page) + "&page_size=24");
                JSONArray list = root.getJSONObject("data").getJSONArray("recommend_room_list");
                List<VideoItem> output = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String cover = item.optString("keyframe", item.optString("cover"));
                    if (cover.startsWith("//")) cover = "https:" + cover;
                    if (cover.startsWith("http://")) cover = "https://" + cover.substring(7);
                    output.add(new VideoItem(item.optString("title", "直播间"), item.optString("uname", "哔哩哔哩主播"),
                            compact(item.optLong("online")) + "人气", "LIVE", cover,
                            "https://live.bilibili.com/" + item.optLong("roomid")));
                }
                main.post(() -> callback.onResult(output, null));
            } catch (Exception e) {
                main.post(() -> callback.onResult(new ArrayList<>(), "直播数据暂时不可用，请稍后重试"));
            }
        });
    }

    void loadLiveArea(int parentAreaId, VideosCallback callback) {
        loadLiveAreaPage(parentAreaId, 1, callback);
    }

    /** 直播分区列表分页，page 从 1 开始。 */
    void loadLiveAreaPage(int parentAreaId, int page, VideosCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.live.bilibili.com/room/v3/area/getRoomList?parent_area_id="
                        + parentAreaId + "&area_id=0&page=" + pageOf(page) + "&page_size=24&sort_type=online");
                JSONArray list = root.getJSONObject("data").getJSONArray("list");
                List<VideoItem> output = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String cover = item.optString("cover", item.optString("keyframe"));
                    if (cover.startsWith("//")) cover = "https:" + cover;
                    if (cover.startsWith("http://")) cover = "https://" + cover.substring(7);
                    output.add(new VideoItem(item.optString("title", "直播间"), item.optString("uname", "哔哩哔哩主播"),
                            compact(item.optLong("online")) + "人气", "LIVE", cover,
                            "https://live.bilibili.com/" + item.optLong("roomid")));
                }
                main.post(() -> callback.onResult(output, null));
            } catch (Exception e) {
                main.post(() -> callback.onResult(new ArrayList<>(), "该直播分区暂时没有可用内容"));
            }
        });
    }

    void loadRecommend(VideosCallback callback) {
        loadRecommendPage(1, callback);
    }

    /** 推荐分页，page 从 1 开始，映射到 fresh_idx。 */
    void loadRecommendPage(int page, VideosCallback callback) {
        load("https://api.bilibili.com/x/web-interface/index/top/feed/rcmd?ps=24&fresh_idx=" + pageOf(page), callback);
    }

    void search(String keyword, String type, VideosCallback callback) {
        searchPage(keyword, type, 1, callback);
    }

    /**
     * 搜索分页，page 从 1 开始。分类接口不可用时保留“回退综合搜索 + 本地按类型过滤”逻辑；
     * 可播性校验只对第 1 页执行（后续页跳过，避免连续加载卡顿）。
     */
    void searchPage(String keyword, String type, int page, VideosCallback callback) {
        final int safePage = pageOf(page);
        executor.execute(() -> {
            try {
                String encoded = Uri.encode(keyword);
                String allEndpoint = "https://api.bilibili.com/x/web-interface/search/all/v2?page=" + safePage + "&keyword=" + encoded;
                List<VideoItem> output;
                if ("all".equals(type)) {
                    JSONObject data = getJson(allEndpoint).getJSONObject("data");
                    output = parseSearchAll(data.optJSONArray("result"));
                } else {
                    try {
                        String endpoint = "https://api.bilibili.com/x/web-interface/search/type?search_type=" + Uri.encode(type)
                                + "&page=" + safePage + "&keyword=" + encoded;
                        JSONObject data = getJson(endpoint).getJSONObject("data");
                        output = parseSearchItems(type, data.optJSONArray("result"));
                    } catch (Exception categoryError) {
                        JSONObject data = getJson(allEndpoint).getJSONObject("data");
                        output = parseSearchAllType(data.optJSONArray("result"), type);
                    }
                }
                if (safePage == 1) output = removeUnavailableSearchVideos(output);
                String message = output.isEmpty()
                        ? (safePage == 1 ? "当前分类没有找到内容" : "没有更多内容了")
                        : "已找到 " + output.size() + " 条结果";
                List<VideoItem> result = output;
                main.post(() -> callback.onResult(result, message));
            } catch (Exception error) {
                main.post(() -> callback.onResult(new ArrayList<>(), "搜索服务暂时不可用，请稍后重试"));
            }
        });
    }

    /**
     * 搜索页热门搜索：/x/web-interface/search/square?limit=12，解析 data.trending.list[]，
     * 优先 keyword，其次 show_name；去重后最多 12 个。失败返回空列表 + 错误文案。
     */
    void loadHotSearches(HotSearchCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.bilibili.com/x/web-interface/search/square?limit=12");
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                JSONObject trending = data == null ? null : data.optJSONObject("trending");
                JSONArray list = trending == null ? null : trending.optJSONArray("list");
                List<String> output = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length() && output.size() < 12; i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) continue;
                        String keyword = item.optString("keyword");
                        if (keyword.isEmpty()) keyword = item.optString("show_name");
                        keyword = keyword == null ? "" : keyword.trim();
                        if (keyword.isEmpty() || output.contains(keyword)) continue;
                        output.add(keyword);
                    }
                }
                main.post(() -> callback.onResult(output, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(new ArrayList<>(), "热门搜索加载失败，请稍后重试"));
            }
        });
    }

    /** 用户卡片信息（无需 WBI）：/x/web-interface/card?mid=。失败时 card 为 null + error。 */
    void loadUserCard(long mid, UserCardCallback callback) {
        if (mid <= 0) {
            main.post(() -> callback.onResult(null, "用户不存在"));
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.bilibili.com/x/web-interface/card?mid=" + mid);
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                JSONObject card = data == null ? null : data.optJSONObject("card");
                if (card == null) throw new IllegalStateException("no card");
                int archiveCount = data.optInt("archive_count", card.optInt("videos", 0));
                UserCard result = new UserCard(mid, card.optString("name"), card.optString("sign"),
                        normalizeImage(card.optString("face")), card.optLong("fans", 0),
                        archiveCount);
                main.post(() -> callback.onResult(result, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, "用户信息加载失败，请稍后重试"));
            }
        });
    }

    /**
     * 账号资料：nav 取 uname/face/mid/money、level_info.current_level、vipStatus；
     * relation/stat 取 following/follower。未登录直接返回 null + "请先登录"。
     */
    void loadAccountProfile(AccountProfileCallback callback) {
        if (!BiliAuthRepository.hasSession()) {
            main.post(() -> callback.onResult(null, "请先登录"));
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject nav = getJson("https://api.bilibili.com/x/web-interface/nav");
                int navCode = nav.optInt("code", 0);
                if (navCode != 0) throw new IllegalStateException(nav.optString("message"));
                JSONObject data = nav.optJSONObject("data");
                if (data == null) throw new IllegalStateException("no data");
                long mid = data.optLong("mid", 0);
                String name = data.optString("uname");
                String face = normalizeImage(data.optString("face"));
                long coins = data.optLong("money", 0);
                JSONObject levelInfo = data.optJSONObject("level_info");
                int level = levelInfo == null ? 0 : levelInfo.optInt("current_level", 0);
                boolean vip = data.optInt("vipStatus", 0) > 0;
                long following = 0;
                long fans = 0;
                String topPhoto = "";
                if (mid > 0) {
                    JSONObject relation = getJson("https://api.bilibili.com/x/relation/stat?vmid=" + mid)
                            .optJSONObject("data");
                    if (relation != null) {
                        following = relation.optLong("following", 0);
                        fans = relation.optLong("follower", 0);
                    }
                    // 空间资料接口偶尔受风控影响，因此头图只作为可选增强，失败不影响账号资料。
                    try {
                        WbiKeys keys = wbiKeys();
                        Map<String, String> params = new HashMap<>();
                        params.put("mid", String.valueOf(mid));
                        params.put("platform", "web");
                        params.put("web_location", "1550101");
                        String query = WbiSigner.sign(params, keys.imgKey, keys.subKey);
                        JSONObject spaceRoot = getJson("https://api.bilibili.com/x/space/wbi/acc/info?" + query,
                                2500, 3000);
                        JSONObject spaceData = spaceRoot.optInt("code", -1) == 0 ? spaceRoot.optJSONObject("data") : null;
                        if (spaceData != null) topPhoto = normalizeImage(spaceData.optString("top_photo"));
                    } catch (Exception ignored) {
                        topPhoto = "";
                    }
                }
                AccountProfile profile = new AccountProfile(name, face, topPhoto, mid, coins, following, fans, level, vip);
                main.post(() -> callback.onResult(profile, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, "账号资料加载失败，请稍后重试"));
            }
        });
    }

    /**
     * 用户投稿分页：WBI 签名 GET /x/space/wbi/arc/search?mid=&pn=&ps=24&order=pubdate。
     * 解析 data.list.vlist[]；hasMore 优先按 data.page.count 判断，缺失时按本页是否满 24 条。
     */
    void loadUserVideos(long mid, int page, VideoPageCallback callback) {
        final int pn = pageOf(page);
        executor.execute(() -> {
            try {
                WbiKeys keys = wbiKeys();
                Map<String, String> params = new HashMap<>();
                params.put("mid", String.valueOf(mid));
                params.put("pn", String.valueOf(pn));
                params.put("ps", "24");
                params.put("order", "pubdate");
                params.put("platform", "web");
                params.put("web_location", "1550101");
                String query = WbiSigner.sign(params, keys.imgKey, keys.subKey);
                JSONObject root = getJson("https://api.bilibili.com/x/space/wbi/arc/search?" + query);
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                JSONObject listObj = data == null ? null : data.optJSONObject("list");
                JSONArray vlist = listObj == null ? null : listObj.optJSONArray("vlist");
                List<VideoItem> items = parseUserVideos(vlist);
                JSONObject pageObj = data == null ? null : data.optJSONObject("page");
                long count = pageObj == null ? 0 : pageObj.optLong("count", 0);
                final boolean hasMore = count > 0 ? (long) pn * 24 < count : items.size() >= 24;
                main.post(() -> callback.onResult(items, hasMore, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(new ArrayList<>(), false, "用户投稿加载失败，请稍后重试"));
            }
        });
    }

    /**
     * 动态详情：/x/polymer/web-dynamic/v1/detail?id=，按 feed 相同结构解析作者/正文/图片。
     * 图片去重后最多 9 张；失败时 detail 为 null + error。
     */
    void loadDynamicDetail(String id, DynamicDetailCallback callback) {
        if (id == null || id.trim().isEmpty()) {
            main.post(() -> callback.onResult(null, "动态不存在"));
            return;
        }
        final String dynamicId = id.trim();
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id="
                        + Uri.encode(dynamicId) + "&features=itemOpusStyle");
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                JSONObject item = data == null ? null : data.optJSONObject("item");
                if (item == null) throw new IllegalStateException("no item");
                JSONObject modules = item.optJSONObject("modules");
                if (modules == null) throw new IllegalStateException("no modules");
                JSONObject author = modules.optJSONObject("module_author");
                JSONObject dynamic = modules.optJSONObject("module_dynamic");
                JSONObject desc = dynamic == null ? null : dynamic.optJSONObject("desc");
                JSONObject major = dynamic == null ? null : dynamic.optJSONObject("major");
                List<String> images = new ArrayList<>();
                JSONObject draw = major == null ? null : major.optJSONObject("draw");
                JSONArray pictures = draw == null ? null : draw.optJSONArray("items");
                if (pictures != null) {
                    for (int i = 0; i < pictures.length() && images.size() < 9; i++) {
                        JSONObject picture = pictures.optJSONObject(i);
                        if (picture == null) continue;
                        String src = normalizeImage(picture.optString("src"));
                        if (src.isEmpty() || images.contains(src)) continue;
                        images.add(src);
                    }
                }
                JSONObject opus = major == null ? null : major.optJSONObject("opus");
                JSONArray opusPics = opus == null ? null : opus.optJSONArray("pics");
                if (opusPics != null) {
                    for (int i = 0; i < opusPics.length() && images.size() < 9; i++) {
                        JSONObject picture = opusPics.optJSONObject(i);
                        if (picture == null) continue;
                        String src = normalizeImage(picture.optString("url", picture.optString("src")));
                        if (src.isEmpty() || images.contains(src)) continue;
                        images.add(src);
                    }
                }
                String text = desc == null ? "" : clean(desc.optString("text", ""));
                if (text.isEmpty() && opus != null) {
                    JSONObject summary = opus.optJSONObject("summary");
                    if (summary != null) text = clean(summary.optString("text"));
                    if (text.isEmpty()) text = clean(opus.optString("title"));
                }
                JSONObject original = item.optJSONObject("orig");
                JSONObject originalModules = original == null ? null : original.optJSONObject("modules");
                JSONObject originalAuthorObject = originalModules == null ? null : originalModules.optJSONObject("module_author");
                JSONObject originalDynamic = originalModules == null ? null : originalModules.optJSONObject("module_dynamic");
                JSONObject originalDesc = originalDynamic == null ? null : originalDynamic.optJSONObject("desc");
                String originalAuthor = originalAuthorObject == null ? "" : originalAuthorObject.optString("name");
                String originalText = originalDesc == null ? "" : clean(originalDesc.optString("text"));
                String kind = item.optString("type").contains("FORWARD") || original != null ? "转发动态"
                        : draw != null || opus != null ? "图文动态" : "文字动态";
                DynamicDetail detail = new DynamicDetail(
                        item.optString("id_str", dynamicId),
                        author == null ? "" : author.optString("name"),
                        author == null ? "" : normalizeImage(author.optString("face")),
                        text,
                        author == null ? "" : author.optString("pub_time"),
                        images, kind, originalAuthor, originalText);
                main.post(() -> callback.onResult(detail, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, "动态详情加载失败，请稍后重试"));
            }
        });
    }

    /** 直播间信息：/room/v1/Room/get_info?room_id=，取 uid/title/live_status/封面/online。 */
    void loadLiveRoomInfo(long roomId, LiveRoomCallback callback) {
        if (roomId <= 0) {
            main.post(() -> callback.onResult(null, "直播间不存在"));
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + roomId);
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                if (data == null) throw new IllegalStateException("no data");
                String cover = data.optString("user_cover");
                if (cover.isEmpty()) cover = data.optString("keyframe");
                LiveRoomInfo info = new LiveRoomInfo(roomId, data.optLong("uid", 0),
                        data.optString("title"), data.optString("uname"),
                        normalizeImage(cover), data.optInt("live_status", 0), data.optLong("online", 0));
                main.post(() -> callback.onResult(info, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, "直播间信息加载失败，请稍后重试"));
            }
        });
    }

    /** 视频详情：/x/web-interface/view。targetUrlOrBvid 可为完整 URL、BV 号、av 号或 aid。 */
    void loadVideoDetail(String targetUrlOrBvid, VideoDetailCallback callback) {
        final String bvid = extractVideoBvid(targetUrlOrBvid);
        final long aid = extractVideoAid(targetUrlOrBvid);
        if (bvid.isEmpty() && aid <= 0) {
            main.post(() -> callback.onResult(null, "无法识别视频地址"));
            return;
        }
        executor.execute(() -> {
            try {
                String query = bvid.isEmpty() ? "aid=" + aid : "bvid=" + Uri.encode(bvid);
                JSONObject root = getJson("https://api.bilibili.com/x/web-interface/view?" + query);
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                if (data == null) throw new IllegalStateException("no data");
                JSONObject owner = data.optJSONObject("owner");
                JSONObject stat = data.optJSONObject("stat");
                JSONArray pages = data.optJSONArray("pages");
                String pic = normalizeImage(data.optString("pic", ""));
                long cid = 0;
                if (pages != null && pages.length() > 0 && pages.optJSONObject(0) != null) {
                    cid = pages.optJSONObject(0).optLong("cid", 0);
                }
                List<VideoPart> parts = new ArrayList<>();
                if (pages != null) {
                    for (int i = 0; i < pages.length() && parts.size() < 100; i++) {
                        JSONObject pageItem = pages.optJSONObject(i);
                        if (pageItem == null) continue;
                        int pageNo = pageItem.optInt("page", i + 1);
                        String partTitle = pageItem.optString("part");
                        if (partTitle.isEmpty()) partTitle = "P" + pageNo;
                        String partCover = normalizeImage(pageItem.optString("first_frame"));
                        if (partCover.isEmpty()) partCover = pic;
                        parts.add(new VideoPart(pageNo, partTitle, pageItem.optLong("cid", 0),
                                pageItem.optInt("duration", 0), partCover));
                    }
                }
                VideoDetail detail = new VideoDetail(
                        data.optString("bvid", bvid), data.optLong("aid", aid), cid,
                        clean(data.optString("title", "")), data.optString("desc", ""),
                        pic, data.optLong("pubdate", 0),
                        data.optInt("duration", 0),
                        owner == null ? "" : clean(owner.optString("name", "")),
                        owner == null ? "" : normalizeImage(owner.optString("face", "")),
                        owner == null ? 0 : owner.optLong("mid", 0),
                        stat == null ? 0 : stat.optLong("view", 0),
                        stat == null ? 0 : stat.optLong("danmaku", 0),
                        stat == null ? 0 : stat.optLong("reply", 0),
                        stat == null ? 0 : stat.optLong("like", 0),
                        stat == null ? 0 : stat.optLong("coin", 0),
                        stat == null ? 0 : stat.optLong("favorite", 0),
                        parts);
                main.post(() -> callback.onResult(detail, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, "视频详情加载失败，请稍后重试"));
            }
        });
    }

    /** 相关推荐：/x/web-interface/archive/related（data 直接是数组），最多 20 条。 */
    void loadRelatedVideos(String bvid, RelatedVideosCallback callback) {
        final String identity = bvid == null ? "" : bvid.trim();
        if (identity.isEmpty()) {
            main.post(() -> callback.onResult(new ArrayList<>(), "无法识别视频地址"));
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject root = getJson("https://api.bilibili.com/x/web-interface/archive/related?bvid="
                        + Uri.encode(identity));
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONArray data = root.optJSONArray("data");
                List<VideoItem> output = new ArrayList<>();
                if (data != null) {
                    for (int i = 0; i < data.length() && output.size() < 20; i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item == null) continue;
                        String itemBvid = item.optString("bvid");
                        if (itemBvid.isEmpty()) continue;
                        JSONObject owner = item.optJSONObject("owner");
                        JSONObject stat = item.optJSONObject("stat");
                        long view = stat == null ? item.optLong("play", 0) : stat.optLong("view", 0);
                        output.add(new VideoItem(clean(item.optString("title", "推荐视频")),
                                owner == null ? "哔哩哔哩" : clean(owner.optString("name", "哔哩哔哩")),
                                compact(view) + "观看", formatDuration(item.optInt("duration", 0)),
                                normalizeImage(item.optString("pic")),
                                "https://www.bilibili.com/video/" + itemBvid));
                    }
                }
                main.post(() -> callback.onResult(output, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(new ArrayList<>(), "相关推荐加载失败，请稍后重试"));
            }
        });
    }

    /** 点赞/取消点赞：/x/web-interface/archive/like，like=1|0。 */
    void setVideoLike(long aid, boolean like, VideoActionCallback callback) {
        if (aid <= 0) {
            main.post(() -> callback.onResult(false, "参数错误"));
            return;
        }
        Map<String, String> form = new HashMap<>();
        form.put("aid", String.valueOf(aid));
        form.put("like", like ? "1" : "0");
        postUserAction("https://api.bilibili.com/x/web-interface/archive/like", form,
                like ? "点赞成功" : "已取消点赞", callback);
    }

    /** 投币：/x/web-interface/coin/add，multiply=1|2。 */
    void addVideoCoin(long aid, int count, VideoActionCallback callback) {
        if (aid <= 0) {
            main.post(() -> callback.onResult(false, "参数错误"));
            return;
        }
        Map<String, String> form = new HashMap<>();
        form.put("aid", String.valueOf(aid));
        form.put("multiply", String.valueOf(Math.max(1, Math.min(2, count))));
        form.put("select_like", "0");
        postUserAction("https://api.bilibili.com/x/web-interface/coin/add", form, "投币成功", callback);
    }

    /** 加入稍后再看：/x/v2/history/toview/add。 */
    void addToWatchLater(long aid, VideoActionCallback callback) {
        if (aid <= 0) {
            main.post(() -> callback.onResult(false, "参数错误"));
            return;
        }
        Map<String, String> form = new HashMap<>();
        form.put("aid", String.valueOf(aid));
        postUserAction("https://api.bilibili.com/x/v2/history/toview/add", form, "已加入稍后再看", callback);
    }

    /** 收藏到默认收藏夹：先查 folders[0]，再 POST /x/v3/fav/resource/deal。 */
    void addVideoFavorite(long aid, VideoActionCallback callback) {
        if (aid <= 0) {
            main.post(() -> callback.onResult(false, "参数错误"));
            return;
        }
        final String csrf = csrfToken();
        if (!BiliAuthRepository.hasSession() || csrf.isEmpty()) {
            main.post(() -> callback.onResult(false, "请先登录"));
            return;
        }
        executor.execute(() -> {
            try {
                String mid = BiliAuthRepository.userMid();
                if (mid.isEmpty()) throw new IllegalStateException("not logged in");
                JSONArray folders = getJson("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid)
                        .getJSONObject("data").getJSONArray("list");
                if (folders.length() == 0) throw new IllegalStateException("no folder");
                long mediaId = folders.getJSONObject(0).getLong("id");
                Map<String, String> form = new HashMap<>();
                form.put("rid", String.valueOf(aid));
                form.put("type", "2");
                form.put("add_media_ids", String.valueOf(mediaId));
                form.put("csrf", csrf);
                JSONObject root = postForm("https://api.bilibili.com/x/v3/fav/resource/deal", form);
                int code = root.optInt("code", -1);
                boolean success = code == 0;
                String serverMessage = root.optString("message");
                String message = success ? "已收藏" : (serverMessage.isEmpty() ? "收藏失败" : serverMessage);
                main.post(() -> callback.onResult(success, message));
            } catch (Exception error) {
                main.post(() -> callback.onResult(false, "收藏失败，请稍后重试"));
            }
        });
    }

    /** 统一的登录态动作 POST：未登录或无 csrf 直接失败；成功按响应 code==0 判定。 */
    private void postUserAction(String endpoint, Map<String, String> form, String successMessage,
                                VideoActionCallback callback) {
        final String csrf = csrfToken();
        if (!BiliAuthRepository.hasSession() || csrf.isEmpty()) {
            main.post(() -> callback.onResult(false, "请先登录"));
            return;
        }
        final Map<String, String> body = new HashMap<>(form);
        body.put("csrf", csrf);
        executor.execute(() -> {
            try {
                JSONObject root = postForm(endpoint, body);
                int code = root.optInt("code", -1);
                boolean success = code == 0;
                String serverMessage = root.optString("message");
                String message = success ? successMessage : (serverMessage.isEmpty() ? "操作失败" : serverMessage);
                main.post(() -> callback.onResult(success, message));
            } catch (Exception error) {
                main.post(() -> callback.onResult(false, "网络异常，请稍后重试"));
            }
        });
    }

    private List<VideoItem> parseUserVideos(JSONArray vlist) {
        List<VideoItem> output = new ArrayList<>();
        if (vlist == null) return output;
        for (int i = 0; i < vlist.length(); i++) {
            JSONObject item = vlist.optJSONObject(i);
            if (item == null) continue;
            String bvid = item.optString("bvid");
            long aid = item.optLong("aid", 0);
            // 优先标准 BV 链接，其次 av 链接，最后才回退 arcurl（避免非标准 URL 导致播放取流失败）。
            String target;
            if (bvid.matches("BV[0-9A-Za-z]{10}")) {
                target = "https://www.bilibili.com/video/" + bvid;
            } else if (aid > 0) {
                target = "https://www.bilibili.com/video/av" + aid;
            } else {
                target = normalizeImage(item.optString("arcurl"));
            }
            if (target.isEmpty()) continue;
            output.add(new VideoItem(clean(item.optString("title", "投稿视频")),
                    item.optString("author", "UP 主"), compact(item.optLong("play")) + "观看",
                    item.optString("duration"), normalizeImage(item.optString("pic")),
                    target));
        }
        return output;
    }

    private List<VideoItem> parseSearchAll(JSONArray groups) {
        List<VideoItem> output = new ArrayList<>();
        if (groups == null) return output;
        for (int i = 0; i < groups.length() && output.size() < 28; i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String type = group.optString("result_type");
            JSONArray items = group.optJSONArray("data");
            if ("live".equals(type) && items != null) {
                for (int j = 0; j < items.length() && output.size() < 28; j++) {
                    JSONObject liveGroup = items.optJSONObject(j);
                    if (liveGroup == null) continue;
                    String liveType = liveGroup.optString("type", "live_room");
                    JSONArray liveItems = liveGroup.optJSONArray("data");
                    output.addAll(parseSearchItems(liveType, liveItems));
                }
            } else if ("video".equals(type) || "media_bangumi".equals(type) || "media_ft".equals(type)
                    || "bili_user".equals(type) || "live_user".equals(type) || "live_room".equals(type)) {
                output.addAll(parseSearchItems(type, items));
            }
        }
        return output.size() > 28 ? new ArrayList<>(output.subList(0, 28)) : output;
    }

    private List<VideoItem> parseSearchAllType(JSONArray groups, String wantedType) {
        List<VideoItem> output = new ArrayList<>();
        if (groups == null) return output;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String type = group.optString("result_type");
            JSONArray items = group.optJSONArray("data");
            if (wantedType.equals(type)) output.addAll(parseSearchItems(type, items));
            else if ("live".equals(type) && ("live_user".equals(wantedType) || "live_room".equals(wantedType)) && items != null) {
                for (int j = 0; j < items.length(); j++) {
                    JSONObject liveGroup = items.optJSONObject(j);
                    if (liveGroup == null) continue;
                    String nestedType = liveGroup.optString("type");
                    if (wantedType.equals(nestedType)) output.addAll(parseSearchItems(nestedType, liveGroup.optJSONArray("data")));
                }
            }
        }
        return output;
    }

    private List<VideoItem> removeUnavailableSearchVideos(List<VideoItem> input) {
        List<VideoItem> output = new ArrayList<>();
        java.util.regex.Pattern bvidPattern = java.util.regex.Pattern.compile("(BV[0-9A-Za-z]{10})");
        java.util.regex.Pattern aidPattern = java.util.regex.Pattern.compile("/av([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        long deadline = System.currentTimeMillis() + 8000;
        int validated = 0;
        for (VideoItem item : input) {
            if (!item.targetUrl.contains("/video/")) { output.add(item); continue; }
            java.util.regex.Matcher bvid = bvidPattern.matcher(item.targetUrl);
            java.util.regex.Matcher aid = aidPattern.matcher(item.targetUrl);
            String identity = bvid.find() ? "bvid=" + Uri.encode(bvid.group(1)) : aid.find() ? "aid=" + aid.group(1) : "";
            if (identity.isEmpty()) continue;
            Long cachedValidUntil = availabilityCache.get(identity);
            if (cachedValidUntil != null && cachedValidUntil > System.currentTimeMillis()) {
                output.add(item);
                continue;
            }
            // 只校验前若干条且带总时间预算；未校验/校验失败的结果保留，播放前还有一次兜底校验。
            if (validated >= 8 || System.currentTimeMillis() > deadline) {
                output.add(item);
                continue;
            }
            validated++;
            try {
                JSONObject root = getJson("https://api.bilibili.com/x/web-interface/view?" + identity, 3500, 4500);
                JSONObject data = root.optJSONObject("data");
                if (root.optInt("code", -1) == 0 && data != null && data.optJSONArray("pages") != null
                        && data.optJSONArray("pages").length() > 0) {
                    availabilityCache.put(identity, System.currentTimeMillis() + 10 * 60 * 1000L);
                    output.add(item);
                }
            } catch (Exception ignored) {
                // 校验失败通常意味着网络/风控问题，不应因此丢掉整页搜索结果。
                output.add(item);
            }
        }
        return output;
    }

    private List<VideoItem> parseSearchItems(String type, JSONArray list) {
        List<VideoItem> output = new ArrayList<>();
        if (list == null) return output;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            if ("video".equals(type)) {
                String bvid = item.optString("bvid");
                long aid = item.optLong("aid");
                if (!bvid.matches("BV[0-9A-Za-z]{10}") && aid <= 0) continue;
                String target = "https://www.bilibili.com/video/" + (bvid.matches("BV[0-9A-Za-z]{10}") ? bvid : "av" + aid);
                output.add(new VideoItem(clean(item.optString("title", "视频")), item.optString("author", "哔哩哔哩 UP 主"),
                        compact(item.optLong("play")) + "观看", item.optString("duration"), normalizeImage(item.optString("pic")), target,
                        "", item.optString("pubdate"), "video"));
            } else if ("media_bangumi".equals(type) || "media_ft".equals(type)) {
                long seasonId = item.optLong("season_id");
                long epId = 0;
                JSONArray eps = item.optJSONArray("eps");
                if (eps != null && eps.length() > 0 && eps.optJSONObject(0) != null) epId = eps.optJSONObject(0).optLong("id");
                if (seasonId <= 0 && epId <= 0) continue;
                String target = "https://www.bilibili.com/bangumi/play/" + (epId > 0 ? "ep" + epId : "ss" + seasonId);
                String owner = "media_bangumi".equals(type) ? "番剧" : "影视";
                String note = item.optString("index_show", item.optString("areas", owner));
                output.add(new VideoItem(clean(item.optString("title", owner)), owner, note, owner,
                        normalizeImage(item.optString("cover")), target, "", item.optString("styles"), type));
            } else if ("live_room".equals(type)) {
                long roomId = item.optLong("roomid", item.optLong("room_id"));
                if (roomId <= 0) continue;
                String cover = item.optString("user_cover", item.optString("cover"));
                output.add(new VideoItem(clean(item.optString("title", "直播间")), item.optString("uname", item.optString("name", "哔哩哔哩主播")),
                        compact(item.optLong("online")) + "人气", "LIVE", normalizeImage(cover), "https://live.bilibili.com/" + roomId,
                        normalizeImage(item.optString("uface", item.optString("face"))), "正在直播", "live_room"));
            } else if ("live_user".equals(type)) {
                long roomId = item.optLong("roomid", item.optLong("room_id"));
                long mid = item.optLong("mid", item.optLong("uid"));
                int liveStatus = item.optInt("live_status", 0);
                // 未开播且拿得到 mid 时走原生 UP 页；开播或有房间号时仍走直播间。
                String target = liveStatus != 1 && mid > 0 ? "https://space.bilibili.com/" + mid
                        : roomId > 0 ? "https://live.bilibili.com/" + roomId
                        : "https://space.bilibili.com/" + mid;
                output.add(new VideoItem(clean(item.optString("uname", item.optString("name", "主播"))), "主播",
                        liveStatus == 1 ? "正在直播" : "未开播", roomId > 0 ? "LIVE" : "主播",
                        normalizeImage(item.optString("uface", item.optString("face"))), target,
                        normalizeImage(item.optString("uface", item.optString("face"))), "", "live_user"));
            } else if ("bili_user".equals(type)) {
                long mid = item.optLong("mid");
                if (mid <= 0) continue;
                output.add(new VideoItem(clean(item.optString("uname", "B 站用户")), clean(item.optString("usign", "UP 主")),
                        compact(item.optLong("fans")) + "粉丝", item.optInt("videos") + "个视频",
                        normalizeImage(item.optString("upic")), "https://space.bilibili.com/" + mid,
                        normalizeImage(item.optString("upic")), "", "bili_user"));
            }
        }
        return output;
    }

    void loadDynamic(VideosCallback callback) {
        loadDynamicPage("", result -> callback.onResult(result.items, result.error));
    }

    /**
     * 关注动态分页。cursor 为空表示首页；否则作为 offset 游标请求同一接口。
     * nextCursor 取 data.offset（缺失/空/为 0 视为没有下一页），单页最多 24 条。
     */
    void loadDynamicPage(String cursor, PageCallback callback) {
        executor.execute(() -> {
            try {
                String offset = cursor == null ? "" : cursor.trim();
                String endpoint = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=all&timezone_offset=-480&page=1"
                        + (offset.isEmpty() ? "" : "&offset=" + Uri.encode(offset));
                JSONObject data = getJson(endpoint).getJSONObject("data");
                JSONArray items = data.getJSONArray("items");
                List<VideoItem> output = parseDynamicItems(items);
                String nextCursor = data.optString("offset", "");
                if ("0".equals(nextCursor)) nextCursor = "";
                final String cursorResult = nextCursor;
                main.post(() -> callback.onResult(new PageResult(output, cursorResult, null)));
            } catch (Exception e) {
                main.post(() -> callback.onResult(PageResult.empty("动态加载失败，请确认账号已登录")));
            }
        });
    }

    private List<VideoItem> parseDynamicItems(JSONArray items) {
        List<VideoItem> output = new ArrayList<>();
        for (int i = 0; i < items.length() && output.size() < 24; i++) {
            JSONObject feedItem = items.optJSONObject(i);
            if (feedItem == null) continue;
            JSONObject modules = feedItem.optJSONObject("modules");
            if (modules == null) continue;
            JSONObject author = modules.optJSONObject("module_author");
            JSONObject dynamic = modules.optJSONObject("module_dynamic");
            JSONObject major = dynamic == null ? null : dynamic.optJSONObject("major");
            JSONObject archive = major == null ? null : major.optJSONObject("archive");
            String owner = author == null ? "关注的 UP 主" : author.optString("name", "关注的 UP 主");
            String avatar = author == null ? "" : normalizeImage(author.optString("face"));
            String published = author == null ? "动态" : author.optString("pub_time", "动态");
            if (archive != null) {
                String bvid = archive.optString("bvid");
                if (bvid.isEmpty()) bvid = archive.optString("jump_url").replaceAll(".*(BV[0-9A-Za-z]{10}).*", "$1");
                if (!bvid.startsWith("BV")) continue;
                JSONObject stat = archive.optJSONObject("stat");
                String views = stat == null ? "动态" : stat.optString("play", "动态");
                output.add(new VideoItem(archive.optString("title", "关注动态"), owner,
                        views + "观看", archive.optString("duration_text"), normalizeImage(archive.optString("cover")),
                        "https://www.bilibili.com/video/" + bvid, avatar, published, "dynamic_video"));
            } else {
                JSONObject desc = dynamic == null ? null : dynamic.optJSONObject("desc");
                String text = desc == null ? "发布了新动态" : clean(desc.optString("text", "发布了新动态"));
                if (text.trim().isEmpty()) text = "发布了新动态";
                String feedType = feedItem.optString("type");
                String kind = "dynamic_text";
                String cover = firstDynamicCover(dynamic);
                String target = "https://t.bilibili.com/" + feedItem.optString("id_str");
                String stats = published;
                String duration = "动态";
                if (major != null && major.optJSONObject("draw") != null) {
                    kind = "dynamic_draw";
                    if ("发布了新动态".equals(text)) text = "分享了新图片";
                }
                else if (major != null && major.optJSONObject("opus") != null) {
                    kind = "dynamic_draw";
                    JSONObject opus = major.optJSONObject("opus");
                    JSONObject summary = opus.optJSONObject("summary");
                    String opusText = summary == null ? "" : clean(summary.optString("text"));
                    if (opusText.isEmpty()) opusText = clean(opus.optString("title"));
                    if (!opusText.isEmpty()) text = opusText;
                }
                else if (major != null && major.optJSONObject("article") != null) {
                    kind = "dynamic_article";
                    String articleTitle = clean(major.optJSONObject("article").optString("title"));
                    if (!articleTitle.isEmpty()) text = articleTitle;
                }
                else if (major != null && major.optJSONObject("live_rcmd") != null) {
                    kind = "dynamic_live";
                    JSONObject liveRcmd = major.optJSONObject("live_rcmd");
                    try {
                        JSONObject live = new JSONObject(liveRcmd.optString("content", "{}"));
                        JSONObject info = live.optJSONObject("live_play_info");
                        if (info != null) {
                            String liveTitle = clean(info.optString("title"));
                            if (!liveTitle.isEmpty()) text = liveTitle;
                            cover = normalizeImage(info.optString("cover"));
                            long roomId = info.optLong("room_id");
                            if (roomId > 0) target = "https://live.bilibili.com/" + roomId;
                            JSONObject watched = info.optJSONObject("watched_show");
                            if (watched != null) stats = watched.optString("text_large", "直播中");
                        }
                    } catch (Exception ignored) { }
                    duration = "LIVE";
                } else if (feedType.contains("FORWARD") || feedItem.optJSONObject("orig") != null) {
                    kind = "dynamic_forward";
                    JSONObject original = feedItem.optJSONObject("orig");
                    JSONObject originalModules = original == null ? null : original.optJSONObject("modules");
                    JSONObject originalDynamic = originalModules == null ? null : originalModules.optJSONObject("module_dynamic");
                    String originalCover = firstDynamicCover(originalDynamic);
                    if (!originalCover.isEmpty()) cover = originalCover;
                    duration = "转发";
                }
                String id = feedItem.optString("id_str");
                output.add(new VideoItem(text, owner, stats,
                        duration, normalizeImage(cover), target, avatar, published, kind));
            }
        }
        return output;
    }

    private String firstDynamicCover(JSONObject dynamic) {
        if (dynamic == null) return "";
        JSONObject major = dynamic.optJSONObject("major");
        if (major == null) return "";
        JSONObject draw = major.optJSONObject("draw");
        JSONArray pictures = draw == null ? null : draw.optJSONArray("items");
        if (pictures != null && pictures.length() > 0 && pictures.optJSONObject(0) != null) {
            return normalizeImage(pictures.optJSONObject(0).optString("src"));
        }
        JSONObject opus = major.optJSONObject("opus");
        JSONArray pics = opus == null ? null : opus.optJSONArray("pics");
        if (pics != null && pics.length() > 0 && pics.optJSONObject(0) != null) {
            JSONObject first = pics.optJSONObject(0);
            return normalizeImage(first.optString("url", first.optString("src")));
        }
        JSONObject article = major.optJSONObject("article");
        JSONArray covers = article == null ? null : article.optJSONArray("covers");
        if (covers != null && covers.length() > 0) return normalizeImage(covers.optString(0));
        JSONObject archive = major.optJSONObject("archive");
        if (archive != null) return normalizeImage(archive.optString("cover"));
        return "";
    }

    void loadFavorites(VideosCallback callback) {
        loadFavoritesPage("", result -> callback.onResult(result.items, result.error));
    }

    /** 拉取账号真实收藏夹目录，保持服务端顺序。 */
    void loadFavoriteFolders(FavoriteFoldersCallback callback) {
        executor.execute(() -> {
            try {
                String mid = BiliAuthRepository.userMid();
                if (mid.isEmpty()) throw new IllegalStateException("not logged in");
                JSONObject root = getJson("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid);
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                JSONArray list = data == null ? null : data.optJSONArray("list");
                List<FavoriteFolder> folders = new ArrayList<>();
                if (list != null) for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    long id = item.optLong("id", 0);
                    if (id <= 0) continue;
                    folders.add(new FavoriteFolder(id, item.optString("title"),
                            item.optInt("media_count", 0), normalizeImage(item.optString("cover"))));
                }
                if (folders.isEmpty()) throw new IllegalStateException("no folder");
                main.post(() -> callback.onResult(folders, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(new ArrayList<>(), "收藏夹目录加载失败，请确认账号已登录"));
            }
        });
    }

    /**
     * 默认收藏夹分页。cursor 为本页页码（空/非法视为第 1 页）；
     * nextCursor 仅在满页（>=24 条）时给出下一页页码，否则为空（没有下一页）。
     */
    void loadFavoritesPage(String cursor, PageCallback callback) {
        executor.execute(() -> {
            try {
                int pn = parsePositiveInt(cursor, 1);
                String mid = BiliAuthRepository.userMid();
                if (mid.isEmpty()) throw new IllegalStateException("not logged in");
                JSONArray folders = getJson("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid)
                        .getJSONObject("data").getJSONArray("list");
                if (folders.length() == 0) throw new IllegalStateException("no folder");
                long mediaId = folders.getJSONObject(0).getLong("id");
                JSONObject data = getJson("https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + mediaId
                        + "&ps=24&pn=" + pn + "&platform=web").getJSONObject("data");
                List<VideoItem> items = parseFavoriteMedias(data.optJSONArray("medias"));
                String nextCursor = items.size() >= 24 ? String.valueOf(pn + 1) : "";
                main.post(() -> callback.onResult(new PageResult(items, nextCursor, null)));
            } catch (Exception e) {
                main.post(() -> callback.onResult(PageResult.empty("收藏加载失败，请确认账号已登录")));
            }
        });
    }

    /** 指定收藏夹分页；供收藏页目录切换使用。 */
    void loadFavoritesPage(long mediaId, String cursor, PageCallback callback) {
        loadFavoritesPage(mediaId, cursor, "mtime", false, callback);
    }

    /** 指定收藏夹、排序和有效性筛选分页。order 支持 mtime（最近收藏）/view（最多播放）。 */
    void loadFavoritesPage(long mediaId, String cursor, String order, boolean availableOnly,
                           PageCallback callback) {
        if (mediaId <= 0) {
            main.post(() -> callback.onResult(PageResult.empty("收藏夹不存在")));
            return;
        }
        executor.execute(() -> {
            try {
                int pn = parsePositiveInt(cursor, 1);
                String safeOrder = "view".equals(order) ? "view" : "mtime";
                JSONObject root = getJson("https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + mediaId
                        + "&ps=24&pn=" + pn + "&order=" + safeOrder + "&type=0&tid=0&platform=web");
                int code = root.optInt("code", 0);
                if (code != 0) throw new IllegalStateException(root.optString("message"));
                JSONObject data = root.optJSONObject("data");
                List<VideoItem> items = parseFavoriteMedias(data == null ? null : data.optJSONArray("medias"), availableOnly);
                String nextCursor = items.size() >= 24 ? String.valueOf(pn + 1) : "";
                main.post(() -> callback.onResult(new PageResult(items, nextCursor, null)));
            } catch (Exception error) {
                main.post(() -> callback.onResult(PageResult.empty("收藏视频加载失败，请稍后重试")));
            }
        });
    }

    void loadHistory(VideosCallback callback) {
        loadHistoryPage("", result -> callback.onResult(result.items, result.error));
    }

    /**
     * 观看历史分页。cursor 格式 "max,view_at"（空表示首页）；nextCursor 由 data.cursor.max/view_at 生成，
     * 无 cursor 或本页不足 24 条时为空（没有下一页）。
     */
    void loadHistoryPage(String cursor, PageCallback callback) {
        executor.execute(() -> {
            try {
                long max = 0;
                long viewAt = 0;
                String value = cursor == null ? "" : cursor.trim();
                if (!value.isEmpty()) {
                    String[] parts = value.split(",");
                    if (parts.length >= 2) {
                        max = parseLong(parts[0].trim(), 0);
                        viewAt = parseLong(parts[1].trim(), 0);
                    }
                }
                JSONObject data = getJson("https://api.bilibili.com/x/web-interface/history/cursor?max=" + max
                        + "&view_at=" + viewAt + "&business=archive&ps=24").getJSONObject("data");
                List<VideoItem> items = parseHistoryItems(data.optJSONArray("list"));
                String nextCursor = "";
                JSONObject next = data.optJSONObject("cursor");
                if (items.size() >= 24 && next != null) {
                    long nextMax = next.optLong("max", 0);
                    long nextViewAt = next.optLong("view_at", 0);
                    if (nextMax != 0 || nextViewAt != 0) nextCursor = nextMax + "," + nextViewAt;
                }
                final String cursorResult = nextCursor;
                main.post(() -> callback.onResult(new PageResult(items, cursorResult, null)));
            } catch (Exception e) {
                main.post(() -> callback.onResult(PageResult.empty("历史记录加载失败，请确认账号已登录")));
            }
        });
    }

    private List<VideoItem> parseHistoryItems(JSONArray list) {
        List<VideoItem> output = new ArrayList<>();
        if (list == null) return output;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject history = item.optJSONObject("history");
            String bvid = history == null ? "" : history.optString("bvid");
            if (bvid.isEmpty()) continue;
            // 历史进度：progress 为已看秒数，>0 时显示“看到 mm:ss”，便于验证心跳回写。
            int progress = item.optInt("progress", 0);
            String stats = progress > 0 ? "看到 " + formatDuration(progress) : "看过";
            output.add(new VideoItem(item.optString("title", "观看记录"), item.optString("author_name", "哔哩哔哩"),
                    stats, formatDuration(item.optInt("duration")), normalizeImage(item.optString("cover")),
                    "https://www.bilibili.com/video/" + bvid, "", formatShortDate(item.optLong("view_at", 0)), "personal_history"));
        }
        return output;
    }

    void loadWatchLater(VideosCallback callback) {
        executor.execute(() -> {
            try {
                JSONArray list = getJson("https://api.bilibili.com/x/v2/history/toview").getJSONObject("data").optJSONArray("list");
                main.post(() -> callback.onResult(parseArchiveList(list), null));
            } catch (Exception e) {
                main.post(() -> callback.onResult(new ArrayList<>(), "稍后再看加载失败，请确认账号已登录"));
            }
        });
    }

    /**
     * 稍后再看分页：接口一次返回完整列表，首次拉取后缓存，本地按 24 条/页切片。
     * cursor 为页码（空/非法视为 1）；cursor 为空表示没有下一页。失败时空 items + error。
     */
    void loadWatchLaterPage(String cursor, PageCallback callback) {
        final int pn = parsePositiveInt(cursor, 1);
        executor.execute(() -> {
            try {
                List<VideoItem> all = watchLaterCache;
                if (all == null) {
                    JSONArray list = getJson("https://api.bilibili.com/x/v2/history/toview")
                            .getJSONObject("data").optJSONArray("list");
                    all = parseArchiveList(list);
                    watchLaterCache = all;
                }
                final PageResult result = slicePage(all, pn);
                main.post(() -> callback.onResult(result));
            } catch (Exception e) {
                main.post(() -> callback.onResult(PageResult.empty("稍后再看加载失败，请确认账号已登录")));
            }
        });
    }

    void loadBangumi(VideosCallback callback) {
        executor.execute(() -> {
            try {
                String mid = BiliAuthRepository.userMid();
                if (mid.isEmpty()) throw new IllegalStateException("not logged in");
                JSONArray list = getJson("https://api.bilibili.com/x/space/bangumi/follow/list?vmid=" + mid + "&type=1&pn=1&ps=24")
                        .getJSONObject("data").optJSONArray("list");
                main.post(() -> callback.onResult(parseBangumiItems(list), null));
            } catch (Exception e) {
                main.post(() -> callback.onResult(new ArrayList<>(), "追番列表加载失败，请确认账号已登录"));
            }
        });
    }

    /**
     * 追番分页：follow/list 支持服务端分页，pn=cursor（空/非法视为 1）；
     * 本页满 24 条才给下一页 cursor，否则为空。失败时空 items + error。
     */
    void loadBangumiPage(String cursor, PageCallback callback) {
        final int pn = parsePositiveInt(cursor, 1);
        executor.execute(() -> {
            try {
                String mid = BiliAuthRepository.userMid();
                if (mid.isEmpty()) throw new IllegalStateException("not logged in");
                JSONArray list = getJson("https://api.bilibili.com/x/space/bangumi/follow/list?vmid=" + mid
                        + "&type=1&pn=" + pn + "&ps=24").getJSONObject("data").optJSONArray("list");
                List<VideoItem> items = parseBangumiItems(list);
                String nextCursor = items.size() >= 24 ? String.valueOf(pn + 1) : "";
                main.post(() -> callback.onResult(new PageResult(items, nextCursor, null)));
            } catch (Exception e) {
                main.post(() -> callback.onResult(PageResult.empty("追番列表加载失败，请确认账号已登录")));
            }
        });
    }

    private List<VideoItem> parseBangumiItems(JSONArray list) {
        List<VideoItem> output = new ArrayList<>();
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject episode = item.optJSONObject("new_ep");
            String progress = item.optString("progress", episode == null ? "" : episode.optString("index_show"));
            output.add(new VideoItem(item.optString("title", "追番追剧"), progress, "已追番", "",
                    normalizeImage(episode == null ? item.optString("cover") : episode.optString("cover", item.optString("cover"))),
                    "https://www.bilibili.com/bangumi/play/ss" + item.optLong("season_id"), "",
                    formatShortDate(item.optLong("mtime", 0)), "personal_bangumi"));
        }
        return output;
    }

    private List<VideoItem> parseFavoriteMedias(JSONArray list) {
        return parseFavoriteMedias(list, false);
    }

    private List<VideoItem> parseFavoriteMedias(JSONArray list, boolean availableOnly) {
        List<VideoItem> output = new ArrayList<>();
        if (list == null) return output;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("title", "收藏视频");
            if (availableOnly && (title.contains("已失效") || item.optInt("attr", 0) == 9)) continue;
            JSONObject upper = item.optJSONObject("upper");
            JSONObject count = item.optJSONObject("cnt_info");
            String bvid = item.optString("bvid");
            if (bvid.isEmpty()) continue;
            output.add(new VideoItem(title, upper == null ? "哔哩哔哩" : upper.optString("name"),
                    compact(count == null ? 0 : count.optLong("play")) + "观看", formatDuration(item.optInt("duration")),
                    normalizeImage(item.optString("cover")), "https://www.bilibili.com/video/" + bvid,
                    "", formatShortDate(item.optLong("fav_time", 0)), "personal_favorite"));
        }
        return output;
    }

    private List<VideoItem> parseArchiveList(JSONArray list) {
        List<VideoItem> output = new ArrayList<>();
        if (list == null) return output;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject owner = item.optJSONObject("owner");
            JSONObject stat = item.optJSONObject("stat");
            String bvid = item.optString("bvid");
            if (bvid.isEmpty()) continue;
            output.add(new VideoItem(item.optString("title", "视频"), owner == null ? "哔哩哔哩" : owner.optString("name"),
                    compact(stat == null ? 0 : stat.optLong("view")) + "观看", formatDuration(item.optInt("duration")),
                    normalizeImage(item.optString("pic")), "https://www.bilibili.com/video/" + bvid,
                    "", formatShortDate(item.optLong("add_at", item.optLong("pubdate", 0))), "personal_watchlater"));
        }
        return output;
    }

    private static String formatShortDate(long epochSeconds) {
        if (epochSeconds <= 0) return "";
        return new SimpleDateFormat("MM-dd", Locale.CHINA).format(new Date(epochSeconds * 1000L));
    }

    private static String normalizeImage(String cover) {
        if (cover == null) return "";
        if (cover.startsWith("//")) return "https:" + cover;
        if (cover.startsWith("http://")) return "https://" + cover.substring(7);
        return cover;
    }

    private void load(String endpoint, VideosCallback callback) {
        executor.execute(() -> {
            try {
                List<VideoItem> output = fetchVideoList(endpoint);
                main.post(() -> callback.onResult(output, null));
            } catch (Exception e) {
                main.post(() -> callback.onResult(new ArrayList<>(), "网络连接失败，请稍后重试"));
            }
        });
    }

    /** 同步拉取并解析列表接口，供 load() 与需要完整列表的本地分页（排行榜）复用。 */
    private List<VideoItem> fetchVideoList(String endpoint) throws Exception {
        JSONObject root = getJson(endpoint);
        JSONObject data = root.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("list");
        if (list == null && data != null) list = data.optJSONArray("item");
        if (list == null && data != null) list = data.optJSONArray("result");
        if (list == null && data != null) list = data.optJSONArray("archives");
        if (list == null) throw new IllegalStateException("接口暂时没有返回视频");
        List<VideoItem> output = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            JSONObject owner = item.optJSONObject("owner");
            JSONObject stat = item.optJSONObject("stat");
            String title = clean(item.optString("title", "精彩视频"));
            String author = owner != null ? owner.optString("name") : item.optString("author", "哔哩哔哩");
            long views = stat != null ? stat.optLong("view") : item.optLong("play");
            String cover = item.optString("pic");
            if (cover.startsWith("//")) cover = "https:" + cover;
            if (cover.startsWith("http://")) cover = "https://" + cover.substring(7);
            String duration = formatDuration(item.optInt("duration"));
            String bvid = item.optString("bvid");
            String arcurl = item.optString("arcurl");
            String target = !arcurl.isEmpty() ? arcurl : "https://www.bilibili.com/video/" + bvid;
            output.add(new VideoItem(title, author, compact(views) + "观看",
                    duration, cover, target));
        }
        return output;
    }

    void loadImage(String url, ImageCallback callback) {
        loadImageOnExecutor(url, callback, imageExecutor);
    }

    /** 账号头像/头图不排在几十张视频封面之后，保证资料头先完成。 */
    void loadPriorityImage(String url, ImageCallback callback) {
        loadImageOnExecutor(url, callback, priorityImageExecutor);
    }

    private void loadImageOnExecutor(String url, ImageCallback callback, ExecutorService targetExecutor) {
        Bitmap cached = imageCache.get(url);
        if (cached != null) { callback.onResult(cached); return; }
        targetExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestProperty("Referer", "https://www.bilibili.com/");
                connection.setRequestProperty("User-Agent", userAgent());
                if (!BiliAuthRepository.cookieHeader().isEmpty()) connection.setRequestProperty("Cookie", BiliAuthRepository.cookieHeader());
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                try (InputStream stream = new BufferedInputStream(connection.getInputStream())) {
                    byte[] bytes = readAll(stream);
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 960, 720);
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }
                if (bitmap != null) imageCache.put(url, bitmap);
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap finalBitmap = bitmap;
            main.post(() -> callback.onResult(finalBitmap));
        });
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static int sampleSize(int width, int height, int maxWidth, int maxHeight) {
        int sample = 1;
        while (width / (sample * 2) >= maxWidth || height / (sample * 2) >= maxHeight) sample *= 2;
        return sample;
    }

    /** 取 WBI keys（缓存 8 小时；失败重试一次）。 */
    private WbiKeys wbiKeys() throws Exception {
        long now = System.currentTimeMillis();
        if (wbiImgKey.isEmpty() || wbiSubKey.isEmpty() || now - wbiFetchedAt > WBI_KEY_TTL_MS) {
            fetchWbiKeys();
        }
        return new WbiKeys(wbiImgKey, wbiSubKey);
    }

    private void fetchWbiKeys() throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JSONObject root = getJson("https://api.bilibili.com/x/web-interface/nav");
                JSONObject data = root.optJSONObject("data");
                JSONObject wbiImg = data == null ? null : data.optJSONObject("wbi_img");
                if (wbiImg == null) throw new IllegalStateException("no wbi_img");
                String imgKey = fileNameWithoutExtension(wbiImg.optString("img_url"));
                String subKey = fileNameWithoutExtension(wbiImg.optString("sub_url"));
                if (imgKey.isEmpty() || subKey.isEmpty()) throw new IllegalStateException("bad wbi key");
                wbiImgKey = imgKey;
                wbiSubKey = subKey;
                wbiFetchedAt = System.currentTimeMillis();
                return;
            } catch (Exception error) {
                lastError = error;
            }
        }
        if (lastError != null) throw lastError;
        throw new IllegalStateException("wbi key unavailable");
    }

    private static String fileNameWithoutExtension(String url) {
        if (url == null || url.isEmpty()) return "";
        int slash = url.lastIndexOf('/');
        String name = slash >= 0 ? url.substring(slash + 1) : url;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** POST application/x-www-form-urlencoded 并解析 JSON；供点赞/投币/稍后再看/收藏等动作使用。 */
    private JSONObject postForm(String endpoint, Map<String, String> form) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(Uri.encode(entry.getKey())).append('=')
                    .append(Uri.encode(entry.getValue() == null ? "" : entry.getValue()));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("User-Agent", desktopUserAgent());
        connection.setRequestProperty("Referer", "https://www.bilibili.com/");
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

    private static String desktopUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    }

    private static String csrfToken() {
        String cookie = BiliAuthRepository.cookieHeader();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|;\\s*)bili_jct=([0-9A-Za-z]+)").matcher(cookie);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String extractVideoBvid(String input) {
        if (input == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(BV[0-9A-Za-z]{10})").matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static long extractVideoAid(String input) {
        if (input == null) return 0;
        String value = input.trim();
        if (value.matches("[0-9]+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|/|\\b)(?:av|aid=)([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(value);
        if (!matcher.find()) return 0;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private JSONObject getJson(String endpoint) throws Exception {
        return getJson(endpoint, 9000, 9000);
    }

    private JSONObject getJson(String endpoint, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestProperty("User-Agent", userAgent());
        connection.setRequestProperty("Referer", "https://www.bilibili.com/");
        connection.setRequestProperty("Accept", "application/json");
        if (!BiliAuthRepository.cookieHeader().isEmpty()) connection.setRequestProperty("Cookie", BiliAuthRepository.cookieHeader());
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            return new JSONObject(json.toString());
        } finally {
            connection.disconnect();
        }
    }

    private String userAgent() {
        return "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36";
    }

    private static int pageOf(int page) {
        return Math.max(1, page);
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 把完整列表按 24 条/页切片；page 从 1 开始，越界返回空 items，cursor 为空。 */
    private static PageResult slicePage(List<VideoItem> all, int page) {
        if (all == null || all.isEmpty()) return new PageResult(new ArrayList<>(), "", null);
        int start = (page - 1) * 24;
        if (start < 0 || start >= all.size()) return new PageResult(new ArrayList<>(), "", null);
        int end = Math.min(all.size(), start + 24);
        List<VideoItem> items = new ArrayList<>(all.subList(start, end));
        String nextCursor = end < all.size() ? String.valueOf(page + 1) : "";
        return new PageResult(items, nextCursor, null);
    }

    private static String clean(String value) {
        return value.replaceAll("<[^>]*>", "").replace("&quot;", "\"").replace("&amp;", "&");
    }

    private static String compact(long value) {
        if (value >= 10000) return String.format(Locale.CHINA, "%.1f万", value / 10000f);
        return String.valueOf(value);
    }

    private static String formatDuration(int seconds) {
        if (seconds <= 0) return "";
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
