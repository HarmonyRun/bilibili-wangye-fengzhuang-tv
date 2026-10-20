# 数据/网络层与工程质量审查报告

> 项目：哔哩客厅（`com.ponyrun.bililivingroom`），1.4.0 / versionCode 6，Java 8，无 AndroidX，Canvas 自绘 UI
> 审查对象：`BiliRepository.java`、`VideoItem.java`、`BiliAuthRepository.java`、`PlaybackRepository.java`、
> `app/build.gradle`、根构建文件、`AndroidManifest.xml`、工程目录与既有构建产物
> 审查方式：**静态代码阅读 + 现有构建产物/报告核对**。未运行 App、未构建、未抓包（本次审查不以运行结果为依据）
> 审查日期：2026-10-18
> 标注约定：**【事实】** = 可直接在当前源码/产物中核对；**【推测】** = 基于平台/接口常识的推断，未验证；
> **【待实测】** = 需要真机/抓包才能定论。

---

## 0. 结论摘要

### 0.1 总体判断

数据层“能跑通主路径”，但**不具备线上可用的质量水位**：错误被统一压扁成用户文案、请求无法取消、
线程池与 Activity 生命周期没有绑定、失败时会向用户展示**伪造内容**、搜索存在串行逐条校验的延迟放大器。
工程侧（版本控制、测试、CI、发布签名、日志）几乎为空白，当前 Debug APK 只能视为“演示构建”。

- **未发现必然导致 P0（崩溃/数据损坏）的确定性代码路径**；但存在两个具备 P0 潜力的风险点：
  ① 封面全尺寸解码 + 无字节预算的图片缓存（低内存 TV 盒子 OOM，见 D-04）；
  ② 搜索串行逐条 `view` 校验在弱网下可累积到分钟级（见 D-01），叠加固定 5 线程池会拖垮其他页面（见 D-03）。
  两者是否真的触发崩溃/超时，**【待实测】**，本报告不夸大。
- **P1 × 8、P2 × 16、P3 × 10**（编号见第 4 节）。
- 工程化缺口：无 git、无测试、无 CI、Debug 签名、lint 报告已过期、无任何日志（第 6 节）。
- 下一阶段数据/工程改造首轮建议投入 **约 16–24.5 人日**（第 7 节），不含 Media3 播放内核迁移。

### 0.2 健康度记分卡

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 主线程安全（网络/图片不阻塞 UI） | 基本合格 | 所有网络与图片解码都在 `ExecutorService`；结果 `main.post` 回主线程（少数方法例外，见 D-09） |
| 生命周期与资源释放 | 不合格 | 4 个 Repository 的线程池均无 `shutdown()`，`MainActivity` 无 `onDestroy`（D-03） |
| 错误模型 | 不合格 | 回调第二参数同时承载“错误信息”和“成功状态文案”；不校验 HTTP 状态与业务 `code`（D-05） |
| 超时/重试/退避 | 不合格 | 仅固定 connect/read 超时，0 重试、0 退避（D-06） |
| 缓存 | 仅图片内存缓存 | 无 JSON 缓存、无磁盘缓存、无请求去重（D-07）；图片缓存无字节预算（D-04） |
| 搜索性能 | 不合格 | 最多 28 次串行 `view` 请求，单次 9s+9s 超时（D-01） |
| JSON 健壮性 | 中等偏弱 | 大量 `opt*` 有兜底，但关键路径混用 `get*`，异常被静默吞掉（D-12、D-15） |
| 可测试性 | 不合格 | 解析逻辑与 `android.*`、网络 IO 强耦合，无接口、无纯函数边界（6.7） |
| 可观测性 | 不合格 | 全工程 0 处 `Log`（D-24） |
| 工程化（git/CI/签名/Lint） | 全部缺失 | 第 6 节 |

---

## 1. 审查范围与方法

### 1.1 实际阅读的文件

| 文件 | 行数 | 关注点 |
| --- | --- | --- |
| `app/src/main/java/.../BiliRepository.java` | 517 | 首页/排行/直播/搜索/账号内容 API、图片缓存 |
| `app/src/main/java/.../VideoItem.java` | 19 | 统一数据模型 |
| `app/src/main/java/.../BiliAuthRepository.java` | 177 | 二维码登录、签名、Cookie |
| `app/src/main/java/.../PlaybackRepository.java` | 209 | 播放地址/弹幕（与数据层同源，一并核查） |
| `app/src/main/java/.../MainActivity.java` | 617 | 回调消费、生命周期、封面加载 |
| `app/src/main/java/.../PlayerActivity.java` | 392 | 回调消费、线程池归属 |
| `app/src/main/java/.../LoginActivity.java` | 111 | 轮询与生命周期 |
| `app/src/main/java/.../BrowserActivity.java` | 52 | WebView 兜底 |
| `app/build.gradle`、根 `build.gradle`、`settings.gradle`、`gradle.properties` | — | 依赖、JDK/SDK 基线、AGP |
| `gradle/wrapper/gradle-wrapper.properties` | — | Gradle 8.9 |
| `app/src/main/AndroidManifest.xml` | 39 | 权限、备份、导出、屏幕方向 |
| `docs/HANDOFF_REPORT.md`、`README.md`、`design-qa.md` | — | 文档与代码事实的一致性 |
| `app/build/reports/lint-results-debug.txt`、`app/build/outputs/apk/debug/output-metadata.json`、根目录 APK | — | 既有产物核对 |

同时执行了只读命令：`git status`、目录/文件时间戳、`grep`（Log/HttpURLConnection/timeout/shutdown）、
APK 证书解析（`openssl pkcs7`，结果 `CN=Android Debug`）。

### 1.2 明确未做的事

- 未执行 `./gradlew assembleDebug/lintDebug`（本次仅核对既有产物）。
- 未安装运行、未抓包、未做弱网/低内存实验、未在真实账号下验证登录态与账号类接口。
- 未修改任何源代码；仅新增本报告文件。

---

## 2. API 层现状

### 2.1 接口覆盖矩阵

| # | 功能 | 代码位置 | 接口（均为硬编码 URL） | 分页 | 鉴权 | 失败回退 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 推荐 | `BiliRepository.java:98-100` | `/x/web-interface/index/top/feed/rcmd?ps=24&fresh_idx=1` | 仅第 1 页 | Cookie 可选 | **伪造精选内容** |
| 2 | 热门 | `BiliRepository.java:40-42` | `/x/web-interface/popular?pn=1&ps=24` | 仅第 1 页 | 可选 | **伪造精选内容** |
| 3 | 排行榜 | `BiliRepository.java:44-46` | `/x/web-interface/ranking/v2?type=all&rid=` | 仅第 1 页 | 可选 | **伪造精选内容** |
| 4 | 分区 | `BiliRepository.java:48-50` | `/x/web-interface/newlist?pn=1&ps=24&rid=` | 仅第 1 页 | 可选 | **伪造精选内容** |
| 5 | 直播推荐 | `BiliRepository.java:52-72` | `api.live` `/xlive/web-interface/v1/webMain/getList` | 仅第 1 页 | 可选 | **伪造精选内容** |
| 6 | 直播分区 | `BiliRepository.java:74-96` | `api.live` `/room/v3/area/getRoomList` | 仅第 1 页 | 可选 | 空列表 + “该分区暂时没有可用内容” |
| 7 | 综合搜索 | `BiliRepository.java:106-110` | `/x/web-interface/search/all/v2` | 仅第 1 页 | 可选 | 通用错误文案 |
| 8 | 分类搜索 | `BiliRepository.java:112-121` | `/x/web-interface/search/type` | 仅第 1 页 | 可选 | 回退到 #7 并本地按类型过滤 |
| 9 | 可播性校验 | `BiliRepository.java:177-195` | `/x/web-interface/view` | 逐条 | 可选 | **静默丢弃该条** |
| 10 | 关注动态 | `BiliRepository.java:245-287` | `/x/polymer/web-dynamic/v1/feed/all` | 仅第 1 页 | 需登录 | 错误文案 |
| 11 | 收藏 | `BiliRepository.java:289-305` | `/x/v3/fav/folder/created/list-all` + `/x/v3/fav/resource/list` | 仅第 1 页、仅第 1 个收藏夹 | 需登录 | 错误文案 |
| 12 | 历史 | `BiliRepository.java:307-328` | `/x/web-interface/history/cursor` | 仅第 1 页 | 需登录 | 错误文案 |
| 13 | 稍后再看 | `BiliRepository.java:330-339` | `/x/v2/history/toview` | 仅第 1 页 | 需登录 | 错误文案 |
| 14 | 追番 | `BiliRepository.java:341-363` | `/x/space/bangumi/follow/list` | 仅第 1 页 | 需登录 | 错误文案 |
| 15 | 封面图 | `BiliRepository.java:443-467` | B 站 CDN 直连 | — | Cookie（无必要） | 返回 `null`，UI 画占位图 |
| 16 | 二维码登录 | `BiliAuthRepository.java:48,63` | `passport.bilibili.com` TV 登录 auth_code/poll | 4s 轮询 | 内置 appkey/secret | 文案 |
| 17 | 播放地址 | `PlaybackRepository.java:102/112/136/143/165/166` | `view` / `pgc/view/web/season` / `playurl` / `pgc/player/web/playurl` / live `get_info`+`playUrl` | — | 可选 | 由 task-1 详查 |
| 18 | 弹幕 | `PlaybackRepository.java:73` | `comment.bilibili.com/{cid}.xml` | — | 无 | 空列表 |

**事实**：全部 API 均**手工拼接 URL + `HttpURLConnection` + `org.json`**，没有 Retrofit/OkHttp/Gson，
没有统一请求层；同一套 HTTP 样板在 `BiliRepository.getJson`（L469-485）、`PlaybackRepository.getJson/open`
（L173-195）、`BiliAuthRepository.post`（L94-139）中出现 **3 份**，超时值分别为 9s/9s、10s/12s、9s/9s。

### 2.2 请求/解析/回退的既有模式

1. **线程**：`executor.execute(...)` 内同步发请求 → 解析 → `main.post(() -> callback.onResult(...))`
   （`BiliRepository.java:31-32,67,91,125,282,300,323,334,358,436,465`）。
2. **解析兜底**：首页类共用 `load()`（L406-441），依次尝试 `data.list` → `data.item` → `data.result` → `data.archives`，
   取不到就抛 `IllegalStateException`（L415）。
3. **回退策略**：
   - 搜索分类接口异常 → 回退综合接口并本地过滤（L117-121），这是唯一的“风控/接口降级”设计；
   - 其余所有首页类接口失败 → 返回 `fallbackVideos()`（L437-439、L67-70），即 **8 条写死的伪造卡片**（L505-516）；
   - 登录类页面失败 → 空列表 + 面向用户的错误文案。
4. **业务 code**：`BiliRepository.getJson` **不检查** `root.optInt("code")`（L469-485）；
   `PlaybackRepository.getJson` 检查（L180）；两套行为不一致。

### 2.3 鉴权与 Cookie 现状

**【事实】** `BiliAuthRepository.java:41-42` 硬编码 `APP_KEY = "5ae412b53418aac5"`、`APP_SECRET = "5b9cf6c9786efd204dcf0c1ce2d08436"`；
签名算法为“参数排序拼接 + MD5 + secret”（L94-139）。Cookie 由 TV 登录接口返回后写入
`CookieManager`（L141-158），此后 API 请求与 WebView 共用；`hasSession()` 仅判断
`SESSDATA=` 是否存在（L78-81）；`userMid()` 用正则从 `DedeUserID` 提取（L88-92）。

**风险事实**：`persistCookies` 写 Cookie 时只拼 `name=value; Domain=…; Path=/; Secure`（L153），
**未写 `Expires`/`Max-Age`**，也丢弃了服务端返回的 HttpOnly 等属性；同时把每个 Cookie 写给
domains 列表中的**每个**域（L148-155）。→ 会话 Cookie 是否能在进程重启后存活，**【待实测】**（见第 8 节 V-1）。

### 2.4 与既有文档的差异（数据层相关）

| 文档表述 | 代码事实 | 结论 |
| --- | --- | --- |
| `docs/HANDOFF_REPORT.md:90`“API 与图片在后台线程执行，结果回到主线程更新 View” | `BiliAuthRepository.requestQr/poll` 回调直接在单线程池里执行；`loadImage` 缓存命中在调用线程同步回调；`loadDanmaku(cid<=0)` 同步回调 | **部分不成立**，见 D-09 |
| `docs/HANDOFF_REPORT.md:61`“分类接口被风控时回退到综合搜索接口” | 仅当 `search/type` **抛异常**时回退（L117-121）；若返回 `code=-352` 且 `data` 存在/缺失但整体能解析，则不会走回退 | 表述不完整 |
| `design-qa.md:58`“No actionable P0, P1, or P2 findings remain” | 该结论针对视觉/交互面；数据层 P1 问题独立存在 | 易被误读为全项目无问题 |
| `design-qa.md:75`“Build and Android lint pass” | 现有 lint 报告出自旧根目录且早于最新源码修改（见 6.5） | **不可作为当前代码的通过证据** |

---

## 3. 数据模型 `VideoItem` 评估

**【事实】** `VideoItem.java:3-18` 仅 6 个 `final String` 字段：`title / owner / stats / duration / coverUrl / targetUrl`，
无 id、无类型、无比值统计、无封面尺寸、无序列化能力、无语义化字段。

由此直接导致：

| 编号 | 问题 | 影响 |
| --- | --- | --- |
| D-22 | 无稳定身份（bvid/seasonId/roomId 仅隐含在 `targetUrl` 字符串里） | 无法按 id 去重、无法做“已看/进度”、无法以 id 为缓存键；同一视频在推荐/搜索/历史中出现时无法合并 |
| D-22 | `stats`、`duration` 是**预格式化展示串**（`"128.6万观看"`、`"LIVE"`、`""`） | 无法排序/筛选/本地化；`duration` 字段被当作“类型标记”复用（直播塞 `"LIVE"`，番剧塞 `"番剧"`），类型语义靠调用方约定 |
| D-22 | `targetUrl` 混装 `www.bilibili.com/video/...`、`live.bilibili.com/...`、`space.bilibili.com/...`、`t.bilibili.com/...` | 路由靠 `String.contains`（`MainActivity.java:590`），新增类型易漏判 |
| D-22 | 无 `Parcelable/Serializable` | Activity 重建/进程恢复时列表状态无法保存（当前无 `onSaveInstanceState`） |
| D-22 | `coverUrl` 保留原始 URL，无宽高 | 无法按卡片尺寸请求合适规格的图（见 D-04） |

**建议**：扩展为 `id`、`kind`（枚举：VIDEO/BANGUMI/LIVE/EPISODE/DYNAMIC/USER）、`title`、`owner`、
`coverUrl` + `coverWidth/Height`、`viewCount`(long)、`durationSeconds`(int)、`webUrl`，
并把“展示格式化”移到 UI 层。该项是后续缓存/去重/可测试性的前置（见 7 节 C3）。

---

## 4. 问题清单

> 级别定义：P0 = 崩溃/数据损坏/安全事件；P1 = 明显损害可用性、稳定性或信任；P2 = 明确缺陷但可绕行；
> P3 = 工程整洁度/可维护性。仅列数据/网络/工程范围内问题；播放器与 UI 细节见 task-1/task-3。

### 4.1 P1（8 项）

| 编号 | 位置 | 代码事实 | 影响 | 修复建议 |
| --- | --- | --- | --- | --- |
| **D-01** | `BiliRepository.java:122,177-195` | 搜索返回后，对每条 `targetUrl` 含 `/video/` 的结果**串行**调用一次 `/x/web-interface/view`；单次超时 9s connect + 9s read（L475-476）；结果上限 28 条（L135/L153） | 弱网下搜索可被放大到数十秒（理论最坏 28×18s≈504s，见 5.4）；期间占用固定池中的 1 个线程；用户切分类会重复触发（`MainActivity.java:429`），旧任务不取消 | 仅校验当前可见项（≤8）或前 N 条；并发上限 3；结果缓存（按 bvid，TTL）；点击播放前兜底校验；最终方案见 7 节 B4 |
| **D-02** | `BiliRepository.java:437-439,67-70,505-516` | 推荐/热门/排行/分区/直播推荐失败时返回 `fallbackVideos()`：8 条**写死标题+伪造播放量**（如“128.6万观看”）的卡片，且 `coverUrl=""`、`targetUrl` 指向旧版分区页 `https://www.bilibili.com/v/tech/` 等 | 用户无法分辨“网络失败”和“真实内容”；伪造数据进入产品展示，属于数据完整性与信任问题；直播页失败时还会混入非直播内容 | 失败即展示错误态（可附带重试按钮/返回键重试）；如保留示例数据，必须显式标注“示例”且不可点击；`loadLive` 失败应返回直播专用空态 |
| **D-03** | `BiliRepository.java:31`、`PlaybackRepository.java:51`、`BiliAuthRepository.java:43`、`MainActivity.java:51`、`PlayerActivity.java:28,243-245` | 4 个 Repository 的 `ExecutorService` 从未 `shutdown()`（全工程 grep 无 shutdown）；`MainActivity` 无 `onDestroy` 清理；`BiliTvView` 是非静态内部类且持有 repository（`MainActivity.java:43,51`） | 每次进入播放页/登录页都会新建 2/1 个非守护线程并**永久驻留**；线程池 worker 持有任务/lambda → 持有回调 → 持有 Activity，形成可累积的泄漏；固定池无界队列在快速切页时堆积（`MainActivity.java:420,496-501` 焦点即切换） | 单例 `AppExecutors`；Repository 提供 `close()`；Activity `onDestroy` 中取消在途请求；或引入可取消的 `Future`/`CancellationSignal`；切页加 150–300ms 防抖 |
| **D-04** | `BiliRepository.java:33-38,443-467` | 图片缓存仅按**条数**淘汰（36 条），无字节预算；`BitmapFactory.decodeStream`（L457）**无 `inSampleSize`/`inJustDecodeBounds`**，按原图全尺寸 ARGB_8888 解码；`setVideos` 一次性请求整页 24–28 张封面（`MainActivity.java:178-184`） | 单张全尺寸封面最坏约 1920×1080×4B≈8.3MB；36 条缓存理论上限可达数百 MB（实际取决于 B 站返回原图尺寸，**【待实测】**）；低内存 TV 盒子存在 OOM 风险，属 P0 潜力项 | 请求时追加尺寸修饰（如 `@672w_378h_1c`）或 `inSampleSize` 采样；缓存改为字节级 `LruCache`（如 1/8 堆）；只加载可见/即将可见的封面；保留 `RGB_565` 选项 |
| **D-05** | `BiliRepository.java:28-29,406-441,469-485`；`MainActivity.java:169-185` | `VideosCallback.onResult(List, String)` 的第二个参数同时是“错误文案”（L438）、“成功状态文案”（L123“已找到 N 条结果”）或 `null`；`getJson` 不校验 HTTP 状态码、不校验业务 `code`；`PlaybackRepository` 却校验 `code`（L180） | UI 无法区分“空结果 / 网络失败 / 风控 / 未登录 / 接口变更”，只能把错误当状态文本展示；风控（HTML 页）与 404/5xx 全部退化为 `JSONException` | 定义 `ApiError{kind: NETWORK/TIMEOUT/HTTP/BIZ/PARSE/AUTH/RISK_CONTROL, code, message}`；回调改 `onSuccess(T)`/`onError(ApiError)`；统一在 HttpClient 内做 HTTP 状态 + `code` 判定 |
| **D-06** | `BiliRepository.java:454-455,475-476`；`PlaybackRepository.java:187-188`；`BiliAuthRepository.java:124-125` | 全工程 0 次重试、0 次指数退避；超时固定为 8s/9s/10s/12s 三套；无 429/`Retry-After` 处理；登录轮询固定 4s（`LoginActivity.java:35,67`） | 瞬时抖动直接失败并触发伪造回退；风控/限流时继续按固定节奏请求 | 统一超时（如 connect 5s/read 8s）+ 1–2 次指数退避重试（仅幂等 GET，对 5xx/超时）；登录轮询加入指数退避与上限 |
| **D-07** | 全工程（`BiliRepository` 所有 `load*`）；`MainActivity.java:420,494-511` | 无任何数据层缓存（无内存 TTL、无磁盘、无 `If-None-Match`）；顶部导航“焦点即切换”会在每次左右移动时触发新请求（L420 → `switchTopPageOnFocus` L496-501） | 反复切页 = 反复全量请求；快速按住方向键可瞬间向 5 线程池投递大量任务，既耗流量/电量，也提高触发风控概率 | 加内存 TTL 缓存（首页 60–120s）；对焦点切换做 200–300ms 防抖；同一页面在缓存有效期内不重复请求 |
| **D-08** | `BiliAuthRepository.java:41-42` | `APP_KEY`/`APP_SECRET` 明文硬编码在源码中（HANDOFF §7 亦承认） | 一旦仓库公开/APK 反编译即可获得；无法轮换；不符合“生产凭据”管理基线；`sign` 可被离线复现 | 视为公开凭据并在合规/隐私评估中说明；把凭据移入 `BuildConfig`（不入库）或服务端代签；**不要**在文档里给出更多细节；同步评估 B 站接口授权 |

### 4.2 P2（16 项）

| 编号 | 位置 | 代码事实 | 影响 | 修复建议 |
| --- | --- | --- | --- | --- |
| **D-09** | `BiliRepository.java:444-445`；`BiliAuthRepository.java:51,68,71`；`PlaybackRepository.java:68` | 回调线程契约不统一：图片缓存命中时**同步**在调用线程回调；登录两个方法直接在单线程池回调；`loadDanmaku(cid<=0)` 同步回调。其余方法均 `main.post` | 调用方（现在恰好在主线程）暂时安全；一旦从后台线程调用即变成“UI 在子线程更新”；契约无法在编译期约束 | 明确“所有回调在主线程”并强制 `main.post`；缓存命中也要异步 post；在文档/接口注释中写明 |
| **D-10** | `MainActivity.java:178-184`；`BiliRepository.java:443-467` | `setVideos` 遍历**全部**结果加载封面（含屏幕外），回调里 `bitmaps.put(...)` **不校验 generation**；切页时 `bitmaps.clear()`（L172）后，旧页面的迟到回调仍会把旧图塞进新页面的 map | 无用下载、旧图残留占用内存、偶发“串图”重绘；切页越频繁越明显 | 回调里校验 `generation`/页面标识；只请求可见行 + 预取下一行；用 RecyclerView 式的可见性驱动加载（自绘场景可维护 `firstRow` 可见集合） |
| **D-11** | `BiliRepository.java:61-62`（对比 L84-86、L399-404） | `loadLive` 只把 `//` 前缀补成 `https:`，**未处理 `http://`**；而 `loadLiveArea`（L86）和 `normalizeImage`（L402）都做了 `http→https`。targetSdk 28+ 默认禁止明文流量 | 若直播封面返回 `http://` 链接，图片会被系统静默拒绝，直播页大量占位图 | 统一走 `normalizeImage`，删除 4 处重复规范化；加“http 封面”单测 |
| **D-12** | `BiliRepository.java:253,276` | `items.optJSONObject(i).optJSONObject("modules")`、`pictures.optJSONObject(0).optString(...)`：当数组元素不是 `JSONObject` 时 `optJSONObject` 返回 `null`，随后解引用会 NPE；NPE 被外层 `catch` 捕获（L283-285） | 单个畸形条目会让**整页动态**失败并显示“动态加载失败”，而不是跳过该条 | 统一 `JSONObject item = ...; if (item == null) continue;`；解析层做逐条容错并统计跳过数 |
| **D-13** | `BiliRepository.java:296-298` | 收藏只取 `folders.getJSONObject(0)`，即第一个收藏夹；`list-all` 返回的全量收藏夹未被使用 | 用户默认收藏夹之外的收藏内容永远看不到；且“我的收藏”入口与个人中心入口行为相同但语义不明 | 允许选择收藏夹（至少提供原生列表让用户选），或按“最近更新”合并展示 |
| **D-14** | `BiliRepository.java:406-441` | `load(String endpoint, boolean search, ...)` 的 `search` 参数在全部调用点均为 `false`（L41/45/49/99），是死参数；`duration` 解析分支（L428-429）实际不可达 | 误导维护者；说明代码曾计划复用搜索解析但未完成 | 删除死参数/死分支；搜索解析单独成方法，纳入单测 |
| **D-15** | 全工程 `load*` 方法；`BiliRepository.java:310,298,333,346` | 所有**支持分页**的列表接口均固定第 1 页（`pn=1`/`page=1`；`ranking/v2` 本身不分页），无“加载更多”、无刷新策略；历史游标 `max=0&view_at=0` 写死 | 内容量被锁死在 24 条；无法翻页；历史只能看到最近一页 | 抽出 `Page<T>{items, hasMore, nextCursor}`；网格滚动到底部触发下一页；优先做推荐/热门/搜索三处 |
| **D-16** | `BiliRepository.java:469-485` | 不区分风控：B 站风控常见形态是 HTTP 200 + HTML 验证页或 `code=-352/-412/-509`；当前 HTML → `JSONException` → 通用文案/静默丢弃（L192）；也未主动初始化 `buvid3` 等设备指纹（依赖 WebView Cookie 的副作用） | 一旦触发风控，所有页面同时“网络开小差”，无法提示用户稍后重试或降低频率；排障困难 | HttpClient 层识别 `Content-Type` 非 JSON、识别常见风控 code；返回 `RISK_CONTROL` 错误类型；必要时补设备指纹初始化接口；降低并发/加退避 |
| **D-17** | `BiliAuthRepository.java:149-155` | 写 Cookie 未带 `Expires`/`Max-Age`；`Secure` 固定加；每个 cookie 写入每个 domain；`flush()` 在后台线程（L157） | 若 WebView 将无过期时间的 Cookie 视为会话 Cookie，**重启 App 后登录态可能丢失**；重复写入不同域也可能产生冲突 Cookie | 保留服务端返回的过期时间（parse `cookie_info.cookies[].expires`）再 `setCookie`；域名按服务端返回匹配；用 `flush()` 后的回调确认落盘；【待实测】V-1 |
| **D-18** | `BiliAuthRepository.java:78-81` | `hasSession()` 只判断 Cookie 字符串含 `SESSDATA=`，不校验有效性/过期/UID 一致性 | 过期或损坏的 Cookie 会让 UI 显示“已登录”，但所有账号接口返回 `-101`，用户只会看到“加载失败，请确认账号已登录” | 增加 `x/web-interface/nav` 探测（缓存结果），失败时清理并引导重新登录 |
| **D-19** | `PlaybackRepository.java:139-150` | PGC 回退只包住“标准 `playurl` **抛异常**”这一种情况；若标准接口返回 `code=0` 但无 `durl`，L150 抛 `IllegalStateException`，不会再尝试 `pgc/player/web/playurl` | 部分番剧/影视“接口成功但无渐进流”的场景会直接播放失败，而本来有 PGC 接口可兜底 | 把“无 durl/无 dash”也纳入 PGC 回退条件；回退链改为显式候选列表 + 逐个尝试 |
| **D-20** | `PlaybackRepository.java:103` | 普通视频只取 `pages[0].cid`，`extractBvid/extractAid`（L197-208）不读取 URL 的 `?p=` | 多 P 视频从任意分 P 点入都会播放第 1 P，与用户预期不符 | 解析 `p` 参数并校验范围，再按 `pages[p-1]` 取 cid；无 `p` 时保底第 1 P |
| **D-21** | `PlaybackRepository.java:75-95` | 弹幕 XML 解析的 `try` 包住整个循环，任一 `<d p="...">` 的 `Float.parseFloat` 失败（L83）即抛异常，被 L89 `catch (Exception ignored)` 吞掉并丢弃**全部**已解析结果；弹幕数量无上限 | 一条脏数据 → 整集弹幕消失，且无任何日志/提示 | 逐条 try/catch 跳过坏条目；给 `Danmaku` 列表设上限（如 2 万条）或按时间窗口分片解析 |
| **D-22** | `VideoItem.java:3-18`（详见第 3 节） | 模型只有 6 个字符串字段，无 id/类型/数值/序列化 | 阻塞去重、排序、缓存键、状态恢复、可测试性 | 按第 3 节建议扩展，先加 `id`+`kind`+数值字段（C3） |
| **D-23** | `AndroidManifest.xml:7` | `android:allowBackup="true"`，未配置 `android:dataExtractionRules`/`fullBackupContent`（targetSdk 35） | WebView 的 Cookie 数据库可能进入 Auto Backup/设备迁移，等同会话凭据外流风险 | `allowBackup="false"`，或至少提供 `dataExtractionRules` 排除 WebView/CookieManager 数据 |
| **D-24** | 全工程 | `grep "Log\."` 结果为 **0**；无崩溃上报、无请求耗时/错误码统计 | 线上问题（风控、解析变更、CDN 失败）完全不可诊断；本次审查也只能靠静态推断 | 引入统一 `Logger`（debug 构建详细、release 只报错误码）；错误类型上屏到可展开的调试面板（TV 可用 Menu 长按） |

### 4.3 P3（10 项）

| 编号 | 位置 | 代码事实 | 影响 | 修复建议 |
| --- | --- | --- | --- | --- |
| **D-25** | `BiliRepository.java:469-485`、`PlaybackRepository.java:173-195`、`BiliAuthRepository.java:94-139` | 3 份重复的 HttpURLConnection 样板，UA/Referer/超时/Cookie 设置各不相同（如 Auth UA 是 `BiliLivingRoom/1.0`，Playback 无 app 标识） | 修改一处易漏；行为漂移（例如只有 Playback 校验 `code`） | 抽 `NetClient` 单实现（B1） |
| **D-26** | `MainActivity.java:331`、`BrowserActivity.java:28`、`BiliAuthRepository.java:127` | 版本号硬编码且不一致：设置页写死 `1.4.0`，WebView UA 为 `BiliLivingRoom/1.1`，登录 UA 为 `BiliLivingRoom/1.0` | 升版本时必然漏改；UA 不一致对风控/统计不友好 | 开启 `buildConfig` 或 `BuildConfig.VERSION_NAME` + `resValue`；UA 统一由常量生成 |
| **D-27** | `BiliRepository.java:491-493` | `clean()` 只处理 `<tag>`、`&quot;`、`&amp;`，不处理 `&#39;`/`&lt;`/`&gt;`/`&nbsp;`；`replaceAll` 每次调用编译正则 | 标题/签名可能残留 HTML 实体或误删尖括号内容；每卡片多次正则开销 | 用固定预编译 Pattern（或 `Html.fromHtml`），补齐实体解码 |
| **D-28** | `BiliRepository.java:61-62,84-86,399-404,425-427` | 封面 URL 规范化写了 4 处，且行为不一致（见 D-11） | 同 D-11 的坑会反复出现 | 只保留 `normalizeImage` 一个入口 |
| **D-29** | `BiliRepository.java:41,45,49,55,99,135,153`、`MainActivity.java:56-62` | 魔法数字分散：请求 `ps=24`、输出上限 `28`、缓存 `36`、分区 `regionIds`/`liveAreaIds`/`rankIds` 数组全部硬编码在 UI 类 | 调整页大小/分区需要改多处；分区 id 与实际分区列表可能漂移 | 抽 `ApiConfig` 常量 + 分区配置（后续可由接口拉取） |
| **D-30** | `BiliRepository.java:451-453,471-474`、`PlaybackRepository.java:190-193` | 同一条 `CookieManager.getCookie("https://api.bilibili.com")` 结果被无条件加到**所有**请求头，包括 `comment.bilibili.com`、`api.live.bilibili.com`、图片 CDN | 会话凭据被发送到不必要的子域，扩大暴露面 | 按 host 白名单决定是否附加 Cookie；图片请求默认不带 SESSDATA |
| **D-31** | `BiliRepository.java:292,344,453,474` | `BiliAuthRepository.cookieHeader()`/`userMid()` 在**后台线程**调用 `CookieManager`；`CookieManager.getInstance()` 首次调用可能触发 WebView provider 初始化 | 多数设备可用，但属于未验证的跨线程平台调用；理论上存在竞态/初始化异常 | 【待实测】V-6；稳妥做法是主线程取 Cookie 快照后传入后台，或缓存 Cookie 字符串并监听更新 |
| **D-32** | `MainActivity.java:389` | `drawCover` 每个卡片每帧 `new Path()`（Lint 同类 `DrawAllocation` 已对 LoginActivity 提示过） | 高频重绘时产生 GC 压力（自绘 UI 无 View 复用，滚动/焦点动画会明显） | 复用 Path/Matrix；预裁剪封面或缓存裁剪结果 |
| **D-33** | `app/src/main/res/values/` 仅 `styles.xml` | 无 `strings.xml`；全部中文文案写死在 Java（`MainActivity` 数百处、`BiliRepository` 错误文案、`LoginActivity`） | 无法本地化、无法复用、文案修改需改代码；对可访问性（TalkBack）不友好 | 抽 `strings.xml`；错误类型与文案分离（错误码 → 文案映射） |
| **D-34** | 全工程 | 无 `SharedPreferences`/数据库/文件持久化（grep 为 0）：设置项（弹幕/自动播放/优先高清，`MainActivity.java:73-75,546-551`）仅存内存 | 重启后设置全部丢失；也无播放进度/缓存目录管理 | 引入轻量 `SharedPreferences`（无需 AndroidX）或文件存储；设置项纳入持久化 |

### 4.4 关于 P0

- **结论：本次静态审查未发现必然触发的 P0。**
- 两个“P0 潜力项”需实测确认：**D-04**（封面全尺寸解码 + 无字节预算缓存 → OOM）与
  **D-01/D-03** 组合（搜索占用线程 + 无取消 → 页面长时间无响应，但网络不在主线程，理论上不会 ANR）
  的实际表现。若实测在目标盒子上出现 `OutOfMemoryError`，D-04 应直接升为 P0 并以最高优先级处理。

---

## 5. 专题分析

### 5.1 线程模型与回调

**【事实】**：
- 网络 IO 与图片解码均在后台线程：`BiliRepository` 固定 5 线程（L31）、`PlaybackRepository` 固定 2 线程（L51）、
  `BiliAuthRepository` 单线程（L43）。**没有主线程网络调用**，这一点是达标的。
- 大多数回调通过 `main.post` 回主线程；例外见 D-09。
- 回调无法取消：`executor.execute` 的 `Future` 未被保存，页面切换仅靠 `pageRequestGeneration`
  （`MainActivity.java:70,103`）丢弃结果，请求仍会跑完、仍占线程、仍消耗流量。
- 图片回调（`MainActivity.java:180-183`）**没有** generation 校验（D-10）。

**【推测】**：在 5 线程池被长任务（搜索校验）占满时，首页图片与列表请求会排队，表现为“图片迟迟不出、
列表转圈”，用户体感类似卡死。

### 5.2 错误模型 / 超时 / 重试

- 回调签名 `(List<VideoItem>, String error)` 无法区分成功/失败；`error` 常被填成成功提示（D-05）。
- `BiliRepository.getJson` 缺少：HTTP 状态码判断、`Content-Type` 判断、业务 `code` 判断、响应体大小上限、
  重试/退避、超时分级（D-05/D-06/D-16）。
- 失败后的产品策略是“降级展示伪造内容”（D-02），这属于把**可恢复错误**变成**错误信息**，是可信度问题。
- 登录轮询：4s 固定间隔，`FAILED` 也会继续每 4s 重试（`LoginActivity.java:35`），无退避上限（D-06）。

### 5.3 图片缓存与内存

**【事实】**：
- 缓存键 = 原始 URL；`accessOrder=true` 的 `LinkedHashMap`，条数上限 36（L33-38），`synchronizedMap` 保证并发安全。
- 无 `inSampleSize`；`decodeStream` 直接出原图（L457）。
- 页面级 `bitmaps`（`MainActivity.java:53`）是第二个引用容器，切页 `clear()` 但不 recycle（现代 Android 下不 recycle 是正确的，
  但缓存仍持有引用，内存不会立刻归还）。
- 背景图 `tv_background.png` 为 1672×941 RGB（解码为 ARGB_8888 约 6.3MB），在 `BiliTvView` 构造时解码一次（L50），
  是常驻内存大头之一。**事实**（文件尺寸）+ **推测**（解码占用按 ARGB_8888 估算）。
- **无磁盘缓存** → 每次冷启动都要重新下载全部可见封面。

### 5.4 搜索逐条 `view` 校验的性能（量化）

**【事实】** 链路：`search()`（L102-130）→ 解析出 ≤28 条（L135/L153）→
`removeUnavailableSearchVideos`（L177-195）对每条视频串行请求 `view`（L188），单次超时 9s+9s（L475-476）。

| 场景 | 估算 |
| --- | --- |
| 28 条全部命中网络，单次 RTT 300ms | ≈ 8.4s 纯网络 + 解析/建连开销（连接可复用，实际略低） |
| 弱网/丢包，单次 2–3s | ≈ 56–84s |
| 每次请求都卡到超时（DNS 解析缓慢、代理挂起） | 理论最坏 28×18s ≈ 504s（8.4 分钟） |
| 用户切分类（`MainActivity.java:429`） | 每次切换再叠加一轮；旧任务不取消，多个搜索任务可在 5 线程池中并行执行 |

**结论**：这是当前数据层最大的可用性风险；P1，且与 D-03、D-07 形成叠加效应。
**优化方向**：先校验可见项（8 条）→ 再后台补校验；并发 3；按 bvid 缓存校验结果；点击播放时兜底（播放器本来就会 `view` 一次，见 D-01 备注）。

### 5.5 JSON 解析健壮性

| 类型 | 位置 | 表现 |
| --- | --- | --- |
| 关键路径用 `get*`（缺字段即抛） | `loadLive` L56、`loadLiveArea` L79、`loadDynamic` L249-250、`loadFavorites` L294-299、`loadHistory` L310-311、`loadWatchLater` L333、`loadBangumi` L346-347 | 接口字段变更 → 整页失败；但至少会显示错误文案（除 L192 静默丢弃） |
| `optJSONObject(...).optXxx(...)` 链式解引用 | L253、L276 | 元素非对象时 NPE，被外层吞掉 → 整页失败（D-12） |
| 静默吞异常 | L192（`view` 校验失败即丢弃条目）、L460（图片失败返回 `null`）、`PlaybackRepository` L89（弹幕全丢） | 用户与开发者都看不到“为什么少了内容” |
| 字符串/数字类型假设 | L262（`jump_url` 正则提取）、L424（`optLong("play")`）、`PlaybackRepository` L83（`Float.parseFloat`） | 字段类型变化时个别条目解析失败或整页失败 |
| HTML/实体清理 | L491-493 | 覆盖不全（D-27） |

**建议**：把“解析”抽成纯函数（输入 `JSONObject`，输出 `VideoItem`，返回 `ParseResult` 含 skipped 计数），
用固定 JSON fixture 做单测——这是投入产出比最高的可测试性改造（见 7 节 C1）。

### 5.6 生命周期 / 泄漏 / 并发与取消

- **线程池不关闭**（D-03）：`PlayerActivity` 每次进入都 `new PlaybackRepository()`（L28），退出时仅释放播放器（L243-245），
  2 个 worker 线程驻留；`LoginActivity` 同理（L19）。属于可累积的资源泄漏。
- **Activity 泄漏链**：worker 线程 → `ThreadPoolExecutor` → 任务 `Runnable`/lambda → `VideosCallback`
  → `BiliTvView`（非静态内部类）→ `MainActivity`。`MainActivity` 无 `onDestroy`，无取消。
- **回调晚到**：`PlayerActivity.resolveAndPlay`（L72-88）在 Activity 已销毁后仍可能执行 `overlay.invalidate()`
  （无害）或 `loadDanmaku`；但**连续切清晰度**时多个 `resolve` 并发（`PlaybackRepository` 2 线程），
  回调顺序不保证，存在“后发起的先返回、先发起的后返回”从而用旧 `stream` 覆盖新 `stream` 的竞态
  （`PlayerActivity.java:82,86,198`）。**【推测】** 表现为切台后清晰度/标题回退到旧值，需实测确认。
- **主线程状态**：`pageRequestGeneration` 只在主线程读写，可见性无问题（事实）；但图片回调绕过它（D-10）。

### 5.7 风控与 API 稳定性

- 唯一降级设计：搜索分类失败 → 综合搜索（L117-121）；但**只捕获异常**，不识别业务 `code`（D-16）。
- 缺少：设备指纹/`buvid` 初始化、风控 code 分类、`Retry-After` 处理、请求频率控制、UA 多样性、
  `x/web-interface/nav` 会话探测（D-16/D-18）。
- **【推测】** B 站 Web 接口对无 `buvid3`、无导航 Cookie 的请求更容易返回 `-352`；当前依赖“用户登录后 CookieManager 里有值”
  才能规避部分风控，未登录场景的风控概率更高。需抓包验证（V-4）。
- 接口全部是**非公开稳定 API**，且项目中无版本隔离/开关，B 站字段变更会直接导致对应页面不可用。

### 5.8 硬编码清单（治理对象）

| 类别 | 位置 |
| --- | --- |
| 凭据 | `BiliAuthRepository.java:41-42` |
| API 主机与路径 | 各 `load*`/`resolve*` 方法内联 URL（第 2.1 节矩阵） |
| UA / Referer | `BiliRepository.java:451,471-472,488`、`PlaybackRepository.java:189-190`、`BiliAuthRepository.java:127`、`BrowserActivity.java:28` |
| 版本号 | `MainActivity.java:331`、`BrowserActivity.java:28`、`BiliAuthRepository.java:127` |
| 分区 id / 榜单 id / 页大小 | `MainActivity.java:56-62`、`BiliRepository.java:41,45,49,55,99` |
| 伪造回退数据 | `BiliRepository.java:505-516` |
| 全屏 systemUi 魔数 `5894` | `MainActivity.java:35`、`PlayerActivity.java:58`、`LoginActivity.java:45`、`BrowserActivity.java:38` |
| 中文文案 | 全工程（无 `strings.xml`，D-33） |

---

## 6. 工程与交付质量

### 6.1 版本控制与产物管理

| 检查项 | 结果 |
| --- | --- |
| git 仓库 | **无**（`git status` → `fatal: not a git repository`） |
| `.gitignore` | 无 |
| `local.properties` | 无（构建依赖外部 `ANDROID_HOME` 或 `/tmp/android-sdk`，见 HANDOFF §6） |
| 构建产物与源码混放 | 根目录有 `BiliLivingRoom-debug.apk`（1.78MB），`app/build/` 全量中间产物在工程内；无 VCS 意味着无法区分“源码/产物/临时文件” |

**影响**：无历史可追溯、无法回滚、无法 code review、无法并行开发；文档中“当前 APK SHA-256”这类事实只能手工维护。
**建议**：`git init` + 标准 Android `.gitignore`（`app/build/`、`.gradle/`、`*.apk`、`.DS_Store`、`local.properties`），
首个提交保留源码与文档；APK 作为 Release 附件而非源码树文件（A1）。

### 6.2 测试

- `app/src/` 下只有 `main`，**无 `test/`、无 `androidTest/`**（find 结果）。
- 无测试依赖（`app/build.gradle:30-32` 仅 zxing）。
- 解析逻辑与 `android.*`、网络 IO 耦合，当前结构下即使加 JUnit 也只能覆盖 `clean()`/`compact()` 这类私有静态方法（且需反射）。

**影响**：本报告列出的错误模型/解析/缓存改造缺少安全网；B 站字段一变只能靠人工回归。
**建议**：C1——把 JSON→`VideoItem` 的解析抽成不依赖 `android.*` 的纯 Java 类（`org.json` 在 JVM 单测中需额外依赖，
可换 Gson 或自带轻量 JSON，或引入 Robolectric）；为每个解析分支准备 fixture（正常/缺字段/风控 HTML/类型变更）。

### 6.3 CI

- 无 `.github/`、无 GitLab CI、无 Jenkinsfile 等任何 CI 配置。
- HANDOFF §6 的人工检查清单（`assembleDebug lintDebug` + 模拟器安装 + 按键走查）无法自动执行。

**建议**：A2——GitHub Actions（或等价）最小流水线：`./gradlew assembleDebug lintDebug testDebugUnitTest`；
缓存 Gradle；产物上传 artifact；后续加签名 release 与 TAG 触发。

### 6.4 签名与发布

**【事实】**：
- `app/build.gradle` **无 `signingConfigs`**，`release` 块 `minifyEnabled false`（L22-27），`proguard-rules.pro` 内容只有一行注释。
- 根目录 APK 证书解析结果：`subject=/CN=Android Debug/O=Android/C=US`（`openssl pkcs7`），即 **Debug 签名**。
- `output-metadata.json` 确认 `versionCode 6 / versionName 1.4.0 / variantName debug`。

**影响**：无法发布到任何正式渠道；无 R8 混淆/压缩；`proguard-rules.pro` 为空意味着未来开启 minify 时（`org.json`/反射）需要补规则。
**建议**：C5——生成 release keystore（本机/密钥库分离），`signingConfigs` + `buildTypes.release.minifyEnabled true`，
首次开启 R8 后跑一遍全链路回归；CI 中以环境变量注入 keystore。

### 6.5 Lint 现状（含“报告已过期”的事实）

- 现存报告：`app/build/reports/lint-results-debug.txt`（44 行），结论 **0 errors, 6 warnings**：
  - `AndroidManifest.xml` 4 处 `DiscouragedApi`：Activity 固定横屏；
  - `BrowserActivity.java:24` `SetJavaScriptEnabled`；
  - `LoginActivity.java:84` `DrawAllocation`。
- **【事实】** 该报告中的路径是 `/Users/ponyrun/Documents/AndroidApp/app/src/...`，比当前工程根
  `/Users/ponyrun/Documents/AndroidApp/ilibilib/app/...` 少一层 `ilibilib`；且报告时间戳（20:45）早于
  `BiliRepository.java`（21:13）与 `PlayerActivity.java`（21:51）的最后修改。
- 因此 `design-qa.md:75` / `HANDOFF_REPORT.md:168` 的“Lint 通过”**不能作为当前源码的证据**，需重新执行（A2/CI 覆盖）。
- `app/build.gradle` 无 `lint {}` 配置（未开启 `abortOnError`/`warningsAsErrors`/`checkReleaseBuilds`）。

### 6.6 依赖分析：仅 `zxing:core:3.5.3` 说明了什么

**事实**：依赖只有二维码库（`app/build.gradle:31`），`gradle.properties:2` 关闭 AndroidX。  
**这意味着**：
- **优点**：APK 小（1.78MB）、供应链风险极低、无 AndroidX 迁移负担、构建简单；
- **代价**：HTTP/JSON/图片/缓存/生命周期全部手写——本报告中的 D-05～D-07、D-09～D-11、D-25 基本都是“没有库兜底”的直接结果。
  同一份 HTTP 样板写了 3 遍、没有连接池调优、没有 gzip/重试/拦截器/日志/磁盘缓存。
- **建议**：保持“无 AndroidX”目标时，**优先自建薄封装**（`NetClient` + 错误模型 + 单例执行器 + 字节级图片缓存），
  不必为此引入 OkHttp/Retrofit；若未来接受 AndroidX，则 Media3/OkHttp/Coil/Room 能显著减少自维护成本（与 task-1 的 Media3 结论联动）。

### 6.7 可维护性

| 指标 | 现状 | 评价 |
| --- | --- | --- |
| 单文件仓库类 | `BiliRepository.java` 517 行，12 个对外数据方法（含 `search`）+ 私有 `load()` + 6 个解析方法 + 图片/HTTP | 职责过载：传输、解析、回退、缓存、格式化混在一起 |
| UI 类 | `MainActivity.java` 617 行，其中 `BiliTvView` 是 570+ 行非静态内部类，自绘全部页面 | 状态机（area/nav/filter/grid/firstRow/generation/personalMode）+ 绘制 + 业务调用耦合 |
| 分层 | 无 `data/`、`domain/`、`ui/` 分包；无接口/抽象；无 DI | 无法替换实现，无法 mock |
| 命名与重复 | 3 套 HTTP；4 处封面规范化；多个 `*Ids` 数组硬编码 | 修改成本随页面增长线性上升 |
| 可观测性 | 0 日志、0 指标、0 崩溃上报 | 线上问题只能靠用户描述（D-24） |
| 文档 | README/design-qa/HANDOFF 详细，但与代码已有偏差（2.4/6.5） | 文档质量高，但需随代码同步，否则成为误导航 |

**结论**：当前结构能支撑“单人大屏玩具”继续迭代，但一旦要接第二个人/上真实用户，先做“传输层 + 错误模型 + 解析层可测”
三件事的收益最大。

### 6.8 其他工程质量事实（与数据/构建相关）

| 项 | 事实 | 级别 |
| --- | --- | --- |
| 构建基线 | AGP 8.7.3（根 `build.gradle:2`）、Gradle 8.9（wrapper）、`compileSdk/targetSdk 35`、`minSdk 23`、source/target Java 8（`app/build.gradle:7,10-12,17-20`） | — |
| AGP 8.7 需要 JDK 17 构建，源码却是 Java 8 | `gradle.properties:4` `android.javaCompile.suppressSourceTargetDeprecationWarning=true` 只是**压掉警告**，未升级语言级 | P3：建议择机升到 Java 17 source/target（AGP 8 已支持），并在 CI 固定 JDK |
| `targetSdk 35` + 固定横屏 + `setSystemUiVisibility(5894)` | Android 15 对 targetSdk 35 强制 edge-to-edge，旧式沉浸式 flag 行为可能变化；工程无 insets 处理 | P2【推测/待实测】：V-7 |
| `allowBackup=true` | 见 D-23 | P2 |
| `INTERNET` 权限、无明文流量配置 | 事实：仅 INTERNET；所有 API 为 HTTPS；图片大多规范为 HTTPS（个别缺口 D-11） | 合格项 |
| `BrowserActivity` WebView | JS 开启、第三方 Cookie 开启、`onReceivedError` 未处理、仅 API 24+ 的 `shouldOverrideUrlLoading` 重载 | 与 task-3 交叉；数据层关注：无错误上报、Cookie 共享面 |
| 三份 dex | APK 内含 `classes.dex`/`classes2.dex`/`classes3.dex`（classes2 仅 1.4KB） | P3 现象，建议确认是否意外引入 multidex/多余依赖（不影响功能，仅整洁度） |
| 构建产物陈旧风险 | `app/build` 中 lint 报告路径指向旧工程根（6.5） | 建议清理 `app/build` 后重建 |

---

## 7. 下一阶段工程化改造建议与工作量

> 人日按“熟悉该项目的 1 名 Android 工程师”估算，含自测，不含产品验收与真机回归排期。
> 依赖关系：A → B → C（B 的 NetClient 是 C1/C2 的前置；C3 依赖 B2 的错误模型）。

### 阶段 A：止血与工程地基（1.9–2.4 人日，立即）

| 任务 | 内容 | 优先级 | 人日 | 对应问题 |
| --- | --- | --- | --- | --- |
| A1 | `git init` + Android 标准 `.gitignore` + 首个提交（源码/文档入库，`app/build`、`*.apk`、`.gradle`、`.DS_Store` 排除）；APK 移到 `dist/` 或 Release 附件 | P1 | 0.5 | 6.1 |
| A2 | 最小 CI：`assembleDebug` + `lintDebug` + `testDebugUnitTest`；固定 JDK 17；上传 APK artifact | P1 | 0.5–1 | 6.2/6.3/6.5 |
| A3 | 删除伪造回退：失败→错误态（含重试入口）；`loadLive` 失败返回直播空态 | P1 | 0.5 | D-02 |
| A4 | 统一封面 URL 规范化（修 `loadLive` 的 `http://` 缺口） | P2 | 0.2 | D-11/D-28 |
| A5 | 清理 `app/build` 并重跑 lint，把真实结果写回文档 | P2 | 0.2 | 6.5 |

### 阶段 B：数据层 P1 治理（6–10.5 人日）

| 任务 | 内容 | 优先级 | 人日 | 对应问题 |
| --- | --- | --- | --- | --- |
| B1 | `NetClient` 统一封装：GET/POST、超时分级、HTTP 状态 + `Content-Type` + 业务 `code`、gzip、UA/Referer/Cookie 策略、响应大小上限、幂等 GET 重试与指数退避 | P1 | 2–3 | D-05/D-06/D-16/D-25 |
| B2 | 统一错误模型 `ApiError{kind, code, message}` + 回调 `onSuccess/onError`；UI 侧错误文案映射（区分风控/未登录/网络/空） | P1 | 1–1.5 | D-05/D-18 |
| B3 | 单例 `AppExecutors` + Repository `close()` + 请求可取消（保存 `Future`，页面切换/销毁时 cancel）；切页 200ms 防抖 | P1 | 1–2 | D-03/D-07/D-10 |
| B4 | 搜索校验改造：可见项优先、并发 ≤3、按 bvid 结果缓存（TTL 10min）、点击兜底 | P1 | 1–2 | D-01 |
| B5 | 图片：尺寸修饰/采样解码、字节级 `LruCache`、可见性驱动加载、失败重试一次 | P1 | 1–2 | D-04/D-10 |

### 阶段 C：工程化基座（8–11.5 人日）

| 任务 | 内容 | 优先级 | 人日 | 对应问题 |
| --- | --- | --- | --- | --- |
| C1 | 解析层抽离为纯 Java（`JsonParser`：JSON→`VideoItem`，含 skipped 计数/容错），JUnit + fixture（正常/缺字段/风控 HTML/类型变更） | P2 | 2–3 | 6.2/D-12/D-15 |
| C2 | 内存 TTL 缓存（按接口/参数键）+ 可选文件/SQLite 缓存（首页类 60–120s），往返 `If-None-Match` 作为进阶 | P2 | 2–3 | D-07 |
| C3 | `VideoItem` 扩展：`id`、`kind` 枚举、数值型 `viewCount/durationSeconds`、`coverWidth/Height` | P2 | 1 | D-22/第 3 节 |
| C4 | 统一日志 + 错误码面板（TV 下 Menu 长按打开），debug/release 分级 | P2 | 0.5–1 | D-24 |
| C5 | Release 签名 + keystore 管理 + R8（`minifyEnabled true`）+ 隐私/Manifest 加固（`allowBackup`、`dataExtractionRules`） | P1（发布前置） | 1–2 | 6.4/D-23 |
| C6 | `strings.xml` 抽取 + `BuildConfig`/`resValue` 版本号，统一 UA | P3 | 1 | D-26/D-33 |
| C7 | 文档同步（README/HANDOFF/design-qa 与代码事实对齐，标注“视觉通过 ≠ 数据层通过”） | P3 | 0.5 | 2.4/6.5 |

### 汇总

| 阶段 | 人日 | 产出 |
| --- | --- | --- |
| A 止血 + 地基 | 1.9–2.4 | 可追溯、可回归、无伪造数据 |
| B 数据层 P1 | 6–10.5 | 统一传输/错误/生命周期/缓存 |
| C 工程化基座 | 8–11.5 | 可测试解析层、缓存、发布能力 |
| **合计（不含 Media3/原生化）** | **约 16–24.5 人日** | 数据层达到“可上线试运行”的最低质量线 |

**优先级排序建议**：A（约 2 天）→ B1/B2/B3（1 周内）→ B4/B5 → C5 → C1/C2/C3 → C4/C6/C7。
**与其它审查的联动**：Media3 迁移（task-1）应建立在 B1/B2 的错误模型之上，否则播放器错误仍只能显示“播放失败”；
原生化详情页（task-3）需要先有 C3 的模型扩展与 D-15 的分页能力。

### 不推荐的做法

- 不建议为了“用库”直接引入完整 Retrofit/OkHttp/Room 体系而不抽接口——当前无 AndroidX 的约束下收益有限，
  且会把问题从“缺封装”变成“缺抽象”。
- 不建议仅靠 `try/catch` 继续加兜底；本报告的核心问题是**错误不可分类**，不是“异常没接住”。

---

## 8. 建议的实测验证清单（区分于代码事实）

| 编号 | 验证项 | 方法 | 关联问题 |
| --- | --- | --- | --- |
| V-1 | 登录态是否跨进程存活 | 登录成功 → 强杀进程 → 冷启动看 `hasSession()` 与账号页 | D-17 |
| V-2 | 封面内存上限与 OOM | 连续切 20 个以上页签，`adb shell dumpsys meminfo` + `dumpsys gfxinfo`，低内存盒子复测 | D-04 |
| V-3 | 弱网下搜索耗时 | 限速 200kbps/丢包 20%，测“全部/视频”搜索到出结果的时间；切分类是否叠加 | D-01/D-07 |
| V-4 | 风控返回形态 | 抓包或反复请求触发，确认是 HTML 页/`-352`/`-412`；确认未登录是否需要 `buvid3` | D-16 |
| V-5 | 伪造回退的暴露面 | 飞行模式打开推荐/热门/直播页，确认用户是否看到伪造播放量 | D-02 |
| V-6 | 后台线程调用 `CookieManager` | 观察首次冷启动（WebView 未初始化）时是否出现初始化异常/ANR；Logcat 过滤 `chromium` | D-31 |
| V-7 | targetSdk 35 行为 | Android 15 实机：全屏/焦点/横屏/返回键（预测性返回）是否异常 | 6.8 |
| V-8 | 多 P 视频 | 打开 `?p=2` 的 URL，确认实际播放集数 | D-20 |
| V-9 | PGC “无 durl”路径 | 用已知会员/版权受限番剧，确认是否走了 PGC 回退 | D-19 |
| V-10 | 清晰度快速切换竞态 | 连续切清晰度，观察标题/画质是否回退到旧值 | 5.6 |

---

## 9. 附录：证据索引

### 9.1 关键代码位置速查

- 线程池：`BiliRepository.java:31`、`PlaybackRepository.java:51`、`BiliAuthRepository.java:43`
- 图片缓存与解码：`BiliRepository.java:33-38,443-467`
- 伪造回退数据：`BiliRepository.java:505-516`（被 L67-70、L437-439 使用）
- 搜索串行校验：`BiliRepository.java:122,177-195`
- 统一 HTTP（无 code 校验）：`BiliRepository.java:469-485`
- 回调主线程切换：`BiliRepository.java:32,67,91,125,282,300,323,334,358,436,465`
- Cookie 持久化：`BiliAuthRepository.java:141-158`
- 硬编码凭据：`BiliAuthRepository.java:41-42`
- 生命周期缺口：`MainActivity.java`（无 `onDestroy`）、`PlayerActivity.java:243-245`
- 构建配置：`app/build.gradle:22-32`（无 signing/lint/test）、根 `build.gradle:2`、`gradle.properties:2-4`
- Manifest：`AndroidManifest.xml:3-4,6-11,28-37`
- 过期 lint 报告：`app/build/reports/lint-results-debug.txt`（路径为旧工程根，时间戳早于最新源码）

### 9.2 既有产物核对结果（只读）

- APK 证书：`subject=/CN=Android Debug/O=Android/C=US`（Debug 签名，事实）。
- APK 大小：1,777,725 字节；资源中 `tv_background.png` 解压后 1,372,553 字节（占包体约 77%）。
- `output-metadata.json`：`versionCode 6`、`versionName 1.4.0`、`variantName debug`。
- lint 报告：`0 errors, 6 warnings`（但见 6.5 的时效性说明）。
- 工程内 `git status`：非 git 仓库。

---

## 10. 给接手的 Lead 的一句话结论

数据层“主路径可用、失败路径不可用”：**先修错误模型与生命周期（B1–B3），再谈体验优化**；
工程侧第 1 天就能做 A1/A2，成本极低、收益最大。伪造回退内容（D-02）建议在对外任何演示前删除。
