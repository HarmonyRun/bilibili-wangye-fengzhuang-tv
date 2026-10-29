# 哔哩客厅 UI / 交互 / 产品面审查报告

> 审查对象：`com.ponyrun.bililivingroom`，`versionName 1.4.0` / `versionCode 6`
> 审查范围：`MainActivity`、`BrowserActivity`、`AndroidManifest.xml`、`styles.xml`、README、design-qa、HANDOFF，以及为核对事实而阅读的 `PlayerActivity`、`PlaybackRepository`、`BiliAuthRepository`、`LoginActivity`、`BiliRepository`、`VideoItem`、`build.gradle`。
> 审查方式：**纯静态代码阅读** + 抽查 4 张 `qa/` 截图 + `aapt2 dump badging` 核对 APK + 读取现有 Lint 报告。**未运行应用，未抓取新截图，不声称任何真机/模拟器运行结果。**
> 标注约定：`[事实]` = 代码/产物可直接证实的结论；`[推导]` = 由代码路径与 Android 控件行为推出的结论；`[推测]` = 需真机或线上接口验证的假设。

---

## 0. 结论摘要

- **未发现可确认的 P0**（在未运行应用的前提下，代码中未见必然崩溃路径）。
- **P1 两个**：
  1. 设置页「默认弹幕 / 自动播放 / 优先高清」三项**只有 UI 状态，没有任何实际效果**，也不持久化；播放器每次进入都使用硬编码默认值。这是已交付界面里的“假开关”。
  2. `design-qa.md` 的“无 P0/P1/P2、passed”结论与本项目自身交付物矛盾：HANDOFF 明列两组 P1、多组 P2；QA 自证账号相关页只在登出态验证；`qa/` 里还有一张未被引用的“搜索番剧播放失败”截图。对外验收会被这两份文档中的一份误导。
- **P2 主体集中在**：焦点状态机边界（横向跨行、筛选“焦点=已生效”混用、空列表无焦点）、遥控器扫描页签触发请求风暴、搜索依赖 `Menu` 键与系统 `AlertDialog`/IME、无刷新/无分页（全部列表只取第一页 24 条）、Back 退出语义与 README 不符、WebView 兜底页无加载/错误/重试且使用移动版 UA、触屏-only 设备完全不可操作、生命周期无状态保存、大屏缩放只做等比缩放而无安全区/弹窗适配。
- **产品完成度**：首页/分区/账号列表/搜索/播放器主体已原生；仍为 WebView 的是 **UP 主主页、未开播主播页、图文动态详情、兜底卡片目标页**。原生化这三类页面需要新增 WBI 签名、空间接口、动态详情接口，粗估 8–12 人日（含联调）。
- **版本核对**：`build.gradle`、`output-metadata.json`、APK badging 三处均为 `1.4.0 / 6`；HANDOFF 的 APK SHA-256 与仓库根 APK、`app/build/outputs/apk/debug/app-debug.apk` 完全一致；HANDOFF 的“约 2,100 行”实测 2,094 行。**版本类文档声明是准确的。**
- **文档问题**：README 功能描述有 2 处与代码事实冲突；design-qa 的结论明显夸大且证据过期（设置页截图仍是 1.3.0）；HANDOFF 整体最准确，但漏记“设置不生效、无刷新/分页、触屏不可用”等已交付行为。

---

## 1. 页面、导航与焦点状态机现状

### 1.1 页面分发（8 页签 + 个人中心 + 搜索 + 两个外部 Activity）

`MainActivity` 用两个维度表达“当前页面”：`navIndex`（0–7）与 `personalMode`（-1 = 个人中心总览，0 = 账号，1–4 = 历史/稍后再看/收藏/追番，5 = 设置）。`onDraw` 的分发在 `MainActivity.java:187-202`。

| navIndex | 页签 | 页面 | 数据来源 | 备注 |
|---|---|---|---|---|
| 0 | 直播 | 直播广场 + 7 个直播分区筛选 | `BiliRepository.loadLive/loadLiveArea` | 筛选行始终显示 |
| 1 | 推荐 | 推荐网格 + 9 个分区筛选 | `loadRecommend` | 默认页签（`navIndex=1`，`MainActivity.java:64`） |
| 2 | 热门 | 热门网格 + 分区筛选 | `loadPopular/loadRegion` | |
| 3 | 排行榜 | 排行榜 + 9 个榜单筛选 | `loadRank` | |
| 4 | 动态 | 关注动态网格 | `loadDynamic` | 无筛选行，需登录 |
| 5 | 收藏 | 默认收藏夹网格 | `loadFavorites` | 无筛选行，需登录 |
| 6 | 我的 | `personalMode` 子页 | 账号/历史/稍后再看/收藏/追番/设置 | 由 `personalMode` 决定绘制分支 |
| 7 | 搜索 | 搜索框 + 7 个结果分类 | `performSearch` | 搜索框为画布绘制，输入走系统对话框 |

其他 Activity：`PlayerActivity`（含 `live.bilibili.com/`、`/video/`、`/bangumi/play/` 的 URL）、`BrowserActivity`（其余 URL）、`LoginActivity`（扫码登录）。路由在 `MainActivity.java:589-594`。

### 1.2 焦点模型实现方式

焦点完全由自绘 View 的字段维护，不依赖 Android 控件树（HANDOFF §4 的描述属实）：

- `area`：`AREA_NAV(0) / AREA_FILTER(1) / AREA_GRID(2) / AREA_SEARCH(3)`（`MainActivity.java:44-47, 63`）
- `navIndex / filterIndex / gridIndex / firstRow / personalMode`（`MainActivity.java:64-72`）
- 按键入口集中在 `MainActivity.java:400-415`，方向键 → `moveHorizontal/moveVertical`，确认键 → `activate`，`Menu` → `showSearch`，`Back` → 个人中心子页返回，否则交给系统。
- 顶部导航是“焦点即切页”：`moveHorizontal` 在 `AREA_NAV` 下改 `navIndex` 后立刻 `switchTopPageOnFocus()` 发请求（`MainActivity.java:417-421, 494-511`）。
- 焦点态只由 `area` + 索引决定，绘制时 `area == AREA_NAV && i == navIndex`、`area == AREA_FILTER && i == filterIndex`、`area == AREA_GRID && i == gridIndex`（`MainActivity.java:213, 243, 275, 305, 340, 362`）。

### 1.3 边界场景逐项核对

| 场景 | 输入 | 代码实际行为 | 结论 |
|---|---|---|---|
| 页签扫描 | 在导航区按住 →/← | 每跨一个页签立即 `loadX()`；每次清空 `videos/bitmaps`；`pageRequestGeneration` 只丢弃旧结果，**不取消已排队请求** | 有功能，但会产生请求风暴与闪烁（UI-03） |
| 导航右边界 | 在“搜索”按 → | `clamp` 不动 | 正常 |
| 导航左边界 | 在“直播”按 ← | 页签不动，但 `filterIndex=0` 被重置（`MainActivity.java:421`），内容仍是旧分区 | 选中态与内容可能不一致（UI-05） |
| 导航下移（直播/推荐/热门/排行） | ↓ | 进入筛选行 | 正常 |
| 导航下移（动态/收藏） | ↓ | 直接进入 `AREA_GRID`；列表空或加载中时**没有可见焦点** | 焦点不可见（UI-06） |
| 导航下移（我的） | ↓ | 进入 3 列个人卡（`gridColumns()==3`） | 正常 |
| 个人中心子页返回 | Back | 回到个人中心总览，清空列表（`MainActivity.java:410-413`） | 正常 |
| 个人中心子页上移 | ↑ | 回导航区；再 ↓ 会回到原子页（`personalMode` 未清） | 可接受 |
| 搜索页下移 | ↓ | 导航 → 搜索框 → 分类行 → 结果网格 | 正常 |
| 搜索框左右 | ←/→ | 只 `invalidate()`，无任何变化（`MainActivity.java:422-423`） | 消费者不理解的按键 |
| 结果网格横向边界 | 在第一行最后一列按 → | `gridIndex+1`，下一帧渲染到下一行第一列（`MainActivity.java:432-436` + `drawGrid` 的行列换算 `256-279`） | 焦点斜跳，违反 TV 网格直觉（UI-04） |
| 结果网格下边界 | 在最后一行按 ↓ | `Math.min(maxGridIndex, ...)` 夹住 | 正常 |
| 空列表按确认 | 动态/收藏登出且为空 | 打开 `LoginActivity`（`MainActivity.java:485`） | 正常 |
| 空列表按确认 | 已登录但接口失败/确实为空 | 无任何反馈（`MainActivity.java:491` 条件不成立） | 死键（UI-06） |
| 设置页第 4 张卡 | 确认键 | `toggleSetting(3)` 无分支（`MainActivity.java:546-551`） | 可聚焦但无动作（UI-23） |
| 账号页任意卡 | 确认键 | 一律 `LoginActivity`（`MainActivity.java:488`） | 语义不合理（UI-24） |
| 搜索页 Back | Back | 交给系统 → **退出整个应用**（`MainActivity.java:410-413`） | 与 README“返回首页”不符（UI-08） |
| 全局 Menu | Menu 键 | 弹出系统 `AlertDialog` 输入（`MainActivity.java:409, 553-577`） | 无遥控器 Menu 键时搜索入口不可达（UI-09） |

### 1.4 正面的代码事实（避免只列问题）

- 8 个页签、个人中心、搜索分类与播放器入口都在代码中真实存在，不是文档夸大。
- 通过 `pageRequestGeneration` 防止旧请求覆盖新页面（`MainActivity.java:98-167`）；`BiliRepository`/`PlaybackRepository` 的回调都 `main.post` 回主线程（`BiliRepository.java:31-32, 436-438`；`PlaybackRepository.java:52, 60-62`）。
- 登录轮询有正确的生命周期处理（`LoginActivity.java:49-50` 在 `onPause` 移除回调并置 `active=false`）。
- 登录态变化回主界面时会重载账号页（`MainActivity.java:88-96`）。
- 个人中心子页 Back 返回、播放器二级菜单 Back 逐级返回、WebView 后退逻辑都已实现（`MainActivity.java:411`；`PlayerActivity.java:156-161`；`BrowserActivity.java:43-46`）。
- 现有 Lint 报告 `app/build/reports/lint-results-debug.txt` 为 **0 errors, 6 warnings**（固定方向 ×4、`setJavaScriptEnabled`、`onDraw` 内 `RectF` 分配），不是“零告警”。
- APK badging：`versionCode 6 / versionName 1.4.0`、仅 `LAUNCHER`、`touchscreen` 非必需、无 Leanback 声明，与 README/HANDOFF 一致；APK 为 debuggable。

---

## 2. 问题清单

严重级别：P0 阻断/必然崩溃；P1 已交付功能虚假或验收误导；P2 明显影响核心体验/可维护性；P3 打磨项。

### 2.1 P1

#### UI-01 [P1][事实] 设置页三个开关全部不生效，且不持久化
- 位置：`MainActivity.java:73-75`（字段）、`329-333`（绘制）、`546-551`（切换）；`PlayerActivity.java:38`（`danmakuEnabled = true`）、`45`（`speedIndex = 1`）、`104-108`（`prepareAsync` 完成后无条件 `mp.start()`）；`PlaybackRepository.java:132-137`（未传清晰度时 `qn=80`）。
- 事实：
  - 三个 `boolean` 只被设置页文字与 `toggleSetting` 读写，项目内**没有 `SharedPreferences`、没有通过 Intent 传递设置、没有静态配置类**（全仓 grep 无 `SharedPreferences/getPreferences`；`MainActivity.java:592` 只传了 URL）。
  - 播放器永远以“弹幕开、1×、默认 qn=80”进入；`settingAutoPlay` 在整个工程没有任何消费方，而播放器在 `onPrepared` 里无条件 `mp.start()`，所以“自动播放”实际恒为“开”，却与开关无关。
  - 三个开关只在 `MainActivity` 存活期间保持；进程死亡/Activity 重建后复位为 `true`。
- 影响：设置页是已交付可见功能，但用户切换后不产生任何行为变化；「自动播放/优先高清」两个概念甚至没有可执行定义（自动播放是起播即播？还是连播下一集？）。
- 修复建议：
  1. 定义设置语义：`autoplay` 指起播即播放（默认开），`highQuality` 指起播 qn 上限，`danmakuDefault` 指播放器初始弹幕开关；
  2. 新建 `PlaybackPreferences`（`SharedPreferences`）持久化三项；
  3. `MainActivity` 通过 Intent extras 或读取偏好传给 `PlayerActivity`，`PlayerActivity` 初始化时使用；
  4. 如需连播，补“播完自动下一集/下一条”逻辑，否则将设置项改名为“起播即播”；
  5. 设置页第 4 项“应用版本”改为纯信息行，不可聚焦。
- 验证建议：改动后逐项做“关闭→退出重进→回播放器”对照，并覆盖进程死亡（开发者选项“不保留活动”）。

#### UI-02 [P1][事实/文档] design-qa 的“通过”结论与项目事实、HANDOFF、QA 证据矛盾
- 位置：`design-qa.md:46, 58, 71, 83, 85`；`docs/HANDOFF_REPORT.md:139-168`；`qa/implementation-search-bangumi-playback-v1.4-fixed-late.png`。
- 事实：
  - design-qa 写 “No actionable P0, P1, or P2 findings remain.”（:58）与 “final result: passed”（:85）。
  - 同一仓库的 HANDOFF 列有 **P1 番剧/PGC 播放不稳定（:141-146）**、**P1 生产凭据与 Cookie（:148-152）**、**P2 搜索风控（:154-158）**、**P2 用户/主播详情仍是 WebView（:160-163）**、**P2 无正式发布链路（:165-168）**。
  - design-qa 自己的验收清单写明账号相关页是 “exercised while logged out”（:71），并在 Follow-up 里承认 “authenticated … should receive an additional device test”（:83）——即“通过”并未覆盖登录态。
  - 抽查（我实际读取）`qa/implementation-search-bangumi-playback-v1.4-fixed-late.png`：画面为黑色播放器 + 红色“播放失败，请返回后重试”，文件修改时间 2026-10-09 22:00。该证据未被 design-qa/HANDOFF 引用，且文件名带 “fixed” 却仍是失败态。
  - design-qa 把 `qa/implementation-settings-native.png` 当作当前账号面证据（:12），但该截图里显示“应用版本 1.3.0”，文件时间 2026-10-08 23:29，早于代码改为 1.4.0 的 `MainActivity.java`（2026-10-09 20:43）。
- 影响：README/HANDOFF/design-qa 是对外交接材料；design-qa 的结论会让人误以为视觉 QA 已验证全部 P0–P2，尤其是“搜索番剧可播放”“无 P1/P2”与 HANDOFF、QA 截图直接冲突，验收与排期会被误导。
- 修复建议：
  1. 把 design-qa 结论限定为“视觉/布局 QA”，明确列出**不在本次范围**的项（PGC 播放、登录态列表、设置生效、触屏、WebView）；
  2. 删除或改写 “no actionable P0/P1/P2”“passed”，改为“视觉通过；功能遗留项见 HANDOFF §7”；
  3. 把 `implementation-search-bangumi-playback-v1.4-fixed-late.png` 纳入或明确标注为失败证据，不要在“fixed”命名下留失败截图；
  4. 所有 QA 截图标注构建号/版本号与日期，替换 1.3.0 旧图或明确标注“历史证据”。

### 2.2 P2

#### UI-03 [P2][事实] “焦点即切页”在遥控器扫描时触发请求风暴与整屏闪烁
- 位置：`MainActivity.java:417-421, 494-511`；`BiliRepository.java:31`（5 线程池，无取消）；`MainActivity.java:178-184`（每个封面回调都 `invalidate`）。
- 事实：每跨一个页签就 `loadLive/loadRecommend/loadPopular/loadRank/loadDynamic/loadFavorites`，先 `videos.clear(); bitmaps.clear()` 再发网络请求；`pageRequestGeneration` 只保证旧结果不落地，但已提交到线程池的请求与图片下载仍会执行；回到首页时 24 张封面各自触发一次 `invalidate`。
- 影响：从“直播”一路按到“搜索”（8 个页签）会瞬间发起 8 组接口 + 最多上百次图片请求；弱网下表现为页面空白/闪烁、焦点已在新页但内容迟迟不回、返回旧页时又重发。对 5 线程池是明显浪费，也增加 B 站风控概率。
- 修复建议：页签**聚焦只移动**、确认键/停顿 200–300ms 才加载；或增加 `pendingPage` 去抖 + `Future.cancel`/`RequestHandle` 取消；图片请求与当前页绑定 generation，失效即丢弃；封面加载完成后合并一次 `invalidate`。

#### UI-04 [P2][事实/推导] 结果网格 ←/→ 会跨行斜跳，不是同排移动
- 位置：`MainActivity.java:432-436`（`gridIndex + delta`）、`256-279`（`row = local / cols; col = local % cols`）、`522-533`。
- 事实：横向移动直接对线性 `gridIndex` 做 ±1，绘制时再按 `index / 4` 换算行列。第 0 行第 3 列按 → 会变成 index 4，渲染为第 1 行第 0 列；个人中心 3 列/2 列布局同理。
- 影响：遥控器用户按“右”看到焦点跳到下一行最左；按“左”又跳回上一行最右，与 TV 网格的空间直觉相反，也会让长列表的横向定位变得不可预测。
- 修复建议：横向移动应保持行不变，只在 `gridIndex % cols` 范围内夹取；到边界时可选“停留”或“换行”（建议停留），把换行交给上下键。

#### UI-05 [P2][事实] 分类 pill 的“焦点”与“已生效筛选”共用一个索引
- 位置：`MainActivity.java:243-247`（`i == filterIndex` 即渲染为深色选中）、`424-430`（←/→ 只改 `filterIndex`）、`472-483`（确认键才真正重载）。
- 事实：用户只是把焦点移到“游戏”pill，界面就把它画成已选中，但内容仍然是旧分类，直到按确认键。直播/推荐/热门/排行榜/搜索页都存在；搜索页因为移动即搜索，问题较小。
- 影响：视觉状态领先于数据状态，用户容易误以为已切换；配合 UI-03 的页签切换会出现“pill 显示 综合、内容却是别的分区”的短暂错位。
- 修复建议：拆成 `filterFocusIndex` 与 `appliedFilterIndex` 两个字段；焦点用白色描边、已应用用深色底；确认键后再更新 applied 并加载。

#### UI-06 [P2][事实] 空态、错误态、登出态三者不可区分，且没有重试入口
- 位置：`MainActivity.java:315-320`（`drawPrivateEmpty` 只按 `hasSession()` 二选一）、`368-374`（搜索空态忽略 `status`）、`228`（错误只写到右上角 14dp 的 `status`）；`BiliRepository.java:125-127, 282-284, 300-302, 323-325, 358-360, 436-438`。
- 事实：
  - 动态/收藏/历史接口失败时返回空列表 + 错误文案，但列表页只显示“登录后查看账号内容 / 这里暂时没有内容 / 稍后刷新再看看”，真正原因只在右上角小字；
  - 搜索失败（如“搜索服务暂时不可用”）与“当前分类没有找到内容”同时出现，中心文案会说“当前分类没有找到内容”，而实际是网络/风控；
  - 所有错误态都没有“重试”按钮/按键，只能靠切页签触发重新加载；已登录但空列表时按确认键无任何反应（`MainActivity.java:485` 的登录分支不成立、`491` 也不成立）。
- 影响：用户无法判断是“没内容”“没登录”还是“网络坏了”，也无法原地恢复；失败体验直接退化成重新扫页签。
- 修复建议：为网格页增加明确的 `EMPTY / ERROR / LOGIN_REQUIRED / LOADING` 状态枚举；错误态中心展示可读原因 + “按确认键重试”；区分 HTTP 失败、风控、未登录；右上角 `status` 只做辅助，不再承担唯一错误出口。

#### UI-07 [P2][事实] 没有刷新机制，也没有分页；所有列表只加载第一页 24 条
- 位置：`MainActivity.java:98-167`（所有 load 方法固定一次请求）、`494-511`（切页签才重载）、`466-492`（确认键在导航区不会刷新）；`BiliRepository.java:40-48, 98-100, 106-110, 247, 289-306, 307-327, 330-336, 341-360`（`pn=1 / page=1 / ps=24`，无翻页参数）。
- 事实：推荐、热门、排行榜、分区、直播、动态、收藏、历史、稍后再看、追番、搜索全部只有一页；焦点滚到第 24 条后没有“加载更多”，也没有任何刷新快捷键/入口；`loadRecommend` 的 `fresh_idx` 永远是 1（`BiliRepository.java:99`）。
- 影响：大屏浏览的核心能力只剩一屏多；用户看完 24 条就“到底了”，且想刷新推荐只能切到别的页签再切回来（或杀进程），与“首页刷新与返回”产品预期不符。
- 修复建议：短期在网格底部加“加载更多/刷新”焦点项并在导航区加长按刷新；中期实现分页/游标加载（各接口已带 `pn/ps`，逐个补 `page` 状态即可），推荐每次刷新递增 `fresh_idx`；返回首页时保留滚动位置。

#### UI-08 [P2][事实/文档] Back 在搜索页/首页直接退出应用，README 却写“返回首页”
- 位置：`MainActivity.java:400-415`（仅个人中心子页拦截，其余 `super.onKeyDown`）；`README.md:31`；`docs/HANDOFF_REPORT.md:106`。
- 事实：只有 `navIndex == 6 && personalMode >= 0` 会返回个人中心；在搜索页（navIndex=7）或其余页面按 Back 都会走 `Activity` 默认 → 退出应用。README 写的是 “`Back`：… 网页后退或返回首页”。
- 影响：用户从搜索/个人中心以外的页面按返回会直接退出；搜索页无法用返回回到进入搜索前的内容页，只能再去导航区换页签；与文档承诺不一致。
- 修复建议：定义清晰层级：`个人子页 → 个人中心 → 上次内容页 → 首页 → 退出`；搜索页 Back 先回上一个 navIndex（记录 `previousNavIndex`），再按一次才退出；README/HANDOFF 同步改写。

#### UI-09 [P2][事实/推导] 搜索输入依赖 Menu 键 + 系统 AlertDialog/IME，电视端可用性差
- 位置：`MainActivity.java:409`（仅 `KEYCODE_MENU` 全局打开）、`553-577`（`AlertDialog` + `EditText` + `showSoftInput`）、`559`（`setTextSize(20)`）、`348-357`（画布上的“搜索框”本身不可编辑）。
- 事实：画布搜索框只是一个展示控件，按确认键才会弹出系统 Material `AlertDialog` 和软键盘；对话框没有任何 TV 尺寸适配（固定 20sp、系统默认对话框宽度），也没有设置 `imeOptions`/`OnEditorActionListener`，提交只能靠点击/焦点到“搜索”按钮。
- 推导/风险：没有硬件 Menu 键的触屏设备无法进入搜索；部分电视盒子没有可用 IME；`EditText` 在 `AlertDialog` 中可能吃掉方向键，遥控器无法从输入框移动到“搜索/取消”按钮。以上三点需真机验证，但代码没有为任何一条做兜底。
- 修复建议：搜索页内直接绘制 TV 键盘或提供“热门搜索/搜索历史”供遥控器选择；至少为对话框设置 TV 友好的宽度/字号（按视口缩放）、`imeOptions=actionSearch` 并监听 `EditorAction` 提交、确保按钮可 D-pad 到达；补一个显式“搜索”按钮的焦点模型。

#### UI-10 [P2][事实] WebView 兜底页没有加载/错误/重试 UI，使用移动版 UA，且无 `onPause`
- 位置：`BrowserActivity.java:18-51`（无 `WebViewClient.onPageStarted/onPageFinished/onReceivedError`、无进度、无超时；`28` 使用 `Mobile Safari` UA；无 `onPause/onResume`）。
- 事实：加载任何用户主页/动态详情时只是黑底 WebView；失败时是白屏/默认错误页，没有返回/重试提示；UA 声明 “Mobile Safari”，在 1920×1080 上会拿到 B 站移动版布局；Activity 生命周期不暂停 WebView。
- 影响：这是项目里唯一的“非原生”体验入口，失败时用户看到无解释的空白；移动版页面在大屏上字号、点击密度都不适合遥控器。
- 修复建议：增加加载进度/失败重试/“按返回键回到首页”；改为桌面版 UA 或 TV 友好 UA；`onPause` 调用 `webView.onPause()`、`onResume` 调 `onResume()`；`shouldOverrideUrlLoading` 中对 `intent://`/`bilibili://` 做显式处理。

#### UI-11 [P2][文档] README 对 WebView 使用范围的描述已经过时
- 位置：`README.md:13`（“账号、历史和分区页才使用独立网页容器”）、`README.md:17,19`（又称分区/账号/历史均为原生）、`BrowserActivity.java:14`（注释“Web content is reserved for account/category pages; video playback is always native.”）。
- 事实：代码中账号、历史、稍后再看、追番、分区都已经原生；`BrowserActivity` 实际承接的是**用户空间、图文动态详情、兜底卡片链接**（`MainActivity.java:589-594`；`BiliRepository.java:239, 279, 505-514`）。HANDOFF 的 P2 描述（:160-163）是准确的，README 和 BrowserActivity 注释都停留在旧版本。
- 影响：接手人和产品会按 README 误判 WebView 边界，规划“原生化”时找错范围。
- 修复建议：README 改为“UP 主主页、未开播主播页、图文动态详情与兜底链接仍用 WebView”；删除 BrowserActivity 的 “account/category” 注释。

#### UI-12 [P2][产品] 用户/主播/动态详情仍未原生（HANDOFF P2 属实）
- 位置：`docs/HANDOFF_REPORT.md:160-163`；`MainActivity.java:589-594`；`BiliRepository.java:227-240, 279`。
- 事实：搜索结果中的 `bili_user` → `space.bilibili.com/<mid>`，`live_user` 未开播时 → 空间页，图文动态 → `t.bilibili.com/<id>`，全部由 `BrowserActivity` 打开；番剧/影视搜索卡片则直接进播放器，没有详情/选集页。
- 影响：账号体系之外最常用的“看 UP 主/看主播/看动态”链路仍是网页，遥控器在 WebView 里体验最差；也是 design-qa“全部原生 TV 壳”表述最容易被放大的地方。
- 修复建议：见 §3 原生化清单与接口需求。

#### UI-13 [P2][事实] 未开播主播只要有 roomId 就被送进播放器并必然报错
- 位置：`BiliRepository.java:227-233`（`target = roomId > 0 ? live 房间 : 空间页`，无视 `live_status`）；`MainActivity.java:589-594`（含 `live.bilibili.com/` 即进播放器）；`PlaybackRepository.java:161-171`（只走 playUrl，无“未开播”分支）。
- 事实：“主播”分类里 `live_status != 1` 的条目仍会拼出 `https://live.bilibili.com/<roomid>`，点击直接进入 `PlayerActivity`，`resolveLive` 拿不到可播地址后显示“暂时无法取得播放地址，请稍后重试”（`PlaybackRepository.java:54-64`）。
- 影响：搜索“主播”时点击未开播主播是常见路径，用户看到的是播放失败而不是主播主页/状态页；且该错误态只有文字，无重试/跳转。
- 修复建议：`live_status != 1` 的 `live_user` 目标改为空间页或（更好）原生主播状态页；`PlayerActivity` 在解析前处理“未开播/已下播”并给出明确文案与返回入口。

#### UI-14 [P2][事实] 触屏-only 设备无法操作（可安装但不可用）
- 位置：`AndroidManifest.xml:4`（`touchscreen` 非必需 → 手机上可安装）、`MainActivity.java:43-86`（`BiliTvView` 未重写 `onTouchEvent`，全项目 grep 无 `onTouchEvent`）、`MainActivity.java:82`（`setFocusableInTouchMode(true)` 只让 View 能取焦点，不产生点击行为）。
- 事实：主界面、个人中心、播放器菜单、登录页全部只响应 `KeyEvent`；触摸点击没有任何处理。触屏设备最多能通过系统键盘的方向键/焦点操作（如果有）或蓝牙遥控器操作；搜索对话框可以触摸输入，但在没有 Menu 键的设备上无法进入。
- 影响：README 把“手机版声明”当作安装兼容卖点，但实际是“能装、不能点”。对把 APK 装到平板/手机试用的用户，第一印象是“应用没反应”。
- 修复建议（二选一）：
  1. 明确产品定位为遥控器/键盘设备，README 标注“触屏设备不可用”；
  2. 给主界面加最小触摸支持：卡片/导航 `onTouchEvent` 命中区域映射到现有 `activate`/索引；播放器 overlay 支持点击暂停与拖动进度；长按/滑动可后续迭代。
- 短期至少先做 2 中的“点击 = 确认键”和播放器点击，成本低、收益大。

#### UI-15 [P2][事实] 无状态保存/恢复，`configChanges` 覆盖不全
- 位置：`MainActivity.java:29-36`（无 `onSaveInstanceState`/`onRestoreInstanceState`）；`AndroidManifest.xml:30`（`configChanges=0x4a0` = `keyboardHidden|orientation|screenSize`，APK 中同样为 `0x4a0`）；`PlayerActivity.java:243-245`（只 `onDestroy`，不保存播放位置/清晰度/倍速）；`BrowserActivity.java` 同样无状态保存。
- 事实：任何未声明的配置变化（`uiMode`、`density`、`locale`、`smallestScreenSize`、`keyboard`、`screenLayout` 等）都会重建 Activity；重建或进程死亡后 `MainActivity` 回到 `navIndex=1`、`searchKeyword=""`、筛选/网格位置全丢；播放器重建后从头开始，不恢复进度（虽然番剧/普通视频都解析了 cid，但没保存）。
- 影响：折叠屏/外接显示器/系统字体缩放/语言切换后回到推荐首页；播放中进程被杀则丢失进度；与“大屏/盒子”场景下的稳定性预期不符。
- 修复建议：`MainActivity` 保存 `navIndex/personalMode/filterIndex/gridIndex/firstRow/searchKeyword`；`PlayerActivity` 保存 `pageUrl/position/quality/speed/danmaku` 并 `resumePosition` 起播；`configChanges` 补 `uiMode|density|locale|smallestScreenSize|keyboard|screenLayout` 或改为不拦截、依赖状态恢复。

#### UI-16 [P2][事实] 大屏缩放只做“等比缩小”，缺 overscan/弹窗/字体缩放适配
- 位置：`MainActivity.java:612-615`（`dp = value * min(w/1920, h/1080)`）、`PlayerActivity.java:390`、`LoginActivity.java:78`；`MainActivity.java:559`（对话框固定 20sp）；`styles.xml:5`（`windowFullscreen`）。
- 事实：所有自绘尺寸按 1920×1080 的 `min` 比例缩放，无安全区（TV overscan）余量；弹窗/IME 是系统控件，不参与自绘缩放；不读取系统字体缩放（`fontScale`）或无障碍字号；非 16:9 比例下（21:9、4:3、带鱼屏）内容按左上布局、纵向可能留白/裁切。
- 影响：部分电视边缘 3–5% 会被裁掉（标题/卡片贴边 52–58dp 处风险最大）；4K 盒子上系统对话框字体与画布字体比例失衡；用户开“大字体”完全不生效，与 TV 10-foot 体验冲突。
- 修复建议：增加 24–32dp 以上的安全区内边距（尤其在 5% overscan 设备上）；把弹窗替换为自绘 TV 对话框或至少按视口放大字号；用 `Configuration.fontScale` 对 `dp` 做上限修正（例如 `min(1.3f, fontScale)`）；横纵比差异大时改为以安全区宽度/高度分别计算，避免只用 `min`。

#### UI-17 [P2][事实] 线程池/回调没有取消与关闭，Activity 重建会累积线程与回调
- 位置：`BiliRepository.java:31`（`Executors.newFixedThreadPool(5)`）、`443-466`（图片加载）；`PlaybackRepository.java:51`（`newFixedThreadPool(2)`）；`BiliAuthRepository.java:43`（单线程）；全仓无 `shutdown()`（grep 无结果）；`MainActivity.java:178-184`（图片回调直接持有 `bitmaps` 并 `invalidate`）。
- 事实：三个 Repository 都是 Activity/View 的实例字段，Activity 重建（配置变化、多窗口、进程内重建）会创建新实例，旧线程池不会关闭；页面切换时旧请求继续跑，图片回调仍会在新页面触发重绘；`BiliRepository` 的 `imageCache` 只有 36 项上限（`BiliRepository.java:33-38`），但网络请求本身没有并发限制/取消。
- 影响：长时间使用或反复重建后线程/内存缓慢增长；切换页签时旧图片回调导致无意义重绘甚至闪图；对低内存 TV 盒子不友好。
- 修复建议：Repository 改为 Activity 级作用域加 `close()/shutdownNow()`（`onDestroy` 调用）；请求带 generation/token，取消或丢弃；图片下载限制并发（例如 3–4）并与当前页 generation 绑定；用 `ExecutorService` 的 `shutdownNow()` 处理离开页面。

#### UI-18 [P2][推测] 播放器/WebView 没有 `onPause`，后台音频行为未定义
- 位置：`PlayerActivity.java:243-245` 只有 `onDestroy`，无 `onPause/onStop`；`BrowserActivity.java:48-51` 同；`LoginActivity.java:50` 是唯一有 `onPause` 的页面。
- 事实：进入后台时没有主动暂停 `MediaPlayer`/`WebView`；是否继续出声取决于 `SurfaceView` 是否被销毁。
- 影响：按 Home 后可能继续播放声音（尤其是 WebView 内嵌视频/直播），或返回时视频黑屏；没有音频焦点管理，来电/其他应用播放时会抢音。
- 修复建议：`onPause` 暂停、`onResume` 恢复；`AudioManager.requestAudioFocus`；`onStop` 前保存播放位置。

#### UI-19 [P2][事实] 网络失败兜底卡片是死链，直播页兜底还会放普通视频
- 位置：`BiliRepository.java:505-514`（`fallbackVideos()` 的 8 条假数据，目标为 `https://www.bilibili.com/v/tech/` 等旧版分区路由，最后一条是 `https://live.bilibili.com/`）；`BiliRepository.java:69`（直播接口失败也返回这 8 条）；`MainActivity.java:589-594`（`/v/...` 不属于媒体 → 进 WebView；`https://live.bilibili.com/` 含 `live.bilibili.com/` → 进播放器，room 为空 → 报错）。
- 影响：断网/风控时用户看到“已展示精选内容”，点进去却是过期网页或播放器错误；直播页出现普通视频卡片；错误态被伪装成内容态。
- 修复建议：兜底改为“错误 + 重试”状态；若保留精选卡片，换成可用的真实 BV 链接与封面；直播页不要复用普通视频兜底；移除旧版 `/v/` 路由。

#### UI-20 [P2][事实] 没有退出登录，也没有清除 Cookie 的入口
- 位置：`MainActivity.java:322-327`（账号页只有“二维码重新登录”）、`535-538`（`openLoggedIn` 为死代码，全仓未被调用）；`BiliAuthRepository.java:78-92`（只有 `hasSession/cookieHeader/userMid`，无 `logout/removeAllCookies`）。
- 影响：换账号/共享设备只能“重新扫码覆盖”，不能退出；Cookie 一直留在 `CookieManager`；账号页也没有显示昵称/头像，只有 UID。
- 修复建议：账号页增加“退出登录”（`CookieManager.removeAllCookies` + 清偏好 + 回登出态），补昵称/头像（可用 `x/web-interface/nav` 或登录态 cookie 中的 `DedeUserID` 再查）；删除或使用 `openLoggedIn` 死代码。

#### UI-21 [P2][事实] 版本号/UA 多处硬编码且已经漂移
- 位置：`MainActivity.java:331`（`"应用版本  1.4.0"` 硬编码）；`BrowserActivity.java:28`（`BiliLivingRoom/1.1`）；`BiliAuthRepository.java:127`（`BiliLivingRoom/1.0`）；`build.gradle:13-14`（`1.4.0/6`）。
- 事实：设置页版本号与 gradle 当前一致纯属人工同步；两个 UA 版本号仍是 1.1/1.0；无 `BuildConfig` 引用（`android.buildFeatures.buildConfig` 未开启）。APK badging 显示实际为 1.4.0/6。
- 影响：下次改版本号极易漏改 UI 或 UA，分平台日志/接口行为无法按版本区分；用户看到的版本号可能与安装包不一致。
- 修复建议：启用 `buildConfig` 或 `PackageManager.getPackageInfo().versionName` 动态显示；UA 统一从 `BuildConfig.VERSION_NAME` 生成；删除所有手写版本字符串。

#### UI-22 [P2][产品] 收藏只取第一个收藏夹，账号类列表均无第二页
- 位置：`BiliRepository.java:289-306`（`folders.getJSONObject(0)` 固定第一个收藏夹，无选择界面）、`:307-360`（历史/稍后再看/追番均单页）。
- 影响：用户无法选择收藏夹；历史/稍后再看只能看到最近 24 条；与“我的”作为原生账号中心的完成度不匹配。
- 修复建议：增加收藏夹列表页（接口已在 `loadFavorites` 中使用 `x/v3/fav/folder/created/list-all`），列表页支持分页/加载更多；保留 `media_id` 状态。

#### UI-23 [P2][文档/证据] QA 证据过期、命名混乱、与“通过”结论不成体系
- 位置：`design-qa.md:12`（引用 `implementation-settings-native.png`）、`qa/implementation-settings-native.png`（显示 1.3.0，mtime 2026-10-08 23:29）、`qa/implementation-home-1.3.1.png`（文件名即 1.3.1，未被任何文档引用）、`qa/implementation-search-bangumi-playback-v1.4-fixed-late.png`（失败态，未引用）；`design-qa.md:12-16` 的“current refinement”清单。
- 事实：48 张截图跨 1.3.1 / 1.4.0 两个版本命名；11 张截图未被 README/design-qa/HANDOFF 引用；`design-qa.md` 引用的文件都存在（这点没问题），但同一张“设置页”截图已过期；当前 `qa/` 目录没有版本/构建号标注文件。
- 影响：无法从证据判断某张图对应哪个 APK；review 者会拿旧图当现状；“fixed” 命名与失败画面并存，降低证据可信度。
- 修复建议：按 `qa/<version>/<state>.png` 归档；在 design-qa 中标注构建日期/版本；删除或隔离失败/历史截图；给未引用截图建索引或清理。

### 2.3 P3 清单

| 编号 | 位置 | 事实与影响 | 建议 |
|---|---|---|---|
| UI-24 | `MainActivity.java:331-332`、`546-551` | 设置页第 4 张“应用版本”卡可聚焦、按确认无动作 | 改为不可聚焦信息行 |
| UI-25 | `MainActivity.java:322-327`、`488` | 账号页 3 张卡任意一张按确认都会进登录（含“已登录”“UID”） | 只有“重新登录”卡触发登录，其余聚焦无动作或展示详情 |
| UI-26 | `MainActivity.java:310`、`486` | 个人中心卡 0 登出态显示“使用手机端确认登录”，实际进入账号页再按一次才登录 | 文案改为“进入账号中心”或直接登录 |
| UI-27 | `MainActivity.java:371-374`、`567` | 搜索无结果提示“使用 ←→ 切换结果分类”，但通过对话框搜索后 `area=AREA_GRID`，←→ 实际在网格里移动 | 按当前 `area` 给不同提示，或搜索后把焦点放在分类行 |
| UI-28 | `PlayerActivity.java:111-113, 271-276` | 播放失败只显示“请返回后重试”，无重试/换清晰度/换直播源入口 | 错误态增加“按确认键重试”和“切换清晰度” |
| UI-29 | 全部自绘 View | Canvas 绘制无 `contentDescription`/AccessibilityNode，TalkBack 只看到一个空白 View | 至少给导航/卡片提供虚拟节点或声明遥控器专用；README 标注无障碍限制 |
| UI-30 | `MainActivity.java:604-608, 377-392`；`PlayerActivity.java:278-296, 386-388` | 每帧 `Typeface.create`、每卡 `new Path()`、每帧 `new LinearGradient`/`String.format`，播放器 overlay 33ms 重绘 | 缓存 Typeface/Paint/Path；时间字符串按秒缓存；复用 RectF/渐变 |
| UI-31 | `MainActivity.java:35`、`BrowserActivity.java:38`、`LoginActivity.java:45`、`PlayerActivity.java:58` | 魔法数字 `5894` 重复 4 处、使用已废弃 `setSystemUiVisibility`，无 WindowInsets 处理 | 提取常量；README/HANDOFF 记录仅验证 Android 15 模拟器，其他系统栏行为待真机确认（推测） |
| UI-32 | `AndroidManifest.xml:10` | `supportsRtl="true"`，但所有绘制按 LTR 硬编码 `left/top`，RTL 语言下会布局错乱 | 去掉 RTL 声明或实现镜像布局 |
| UI-33 | 全仓无 `strings.xml` | 全部中文硬编码在 Java/Manifest，无 i18n，无法做文案统一/翻译 | 至少把导航/标题/状态文案抽到常量或资源 |
| UI-34 | `AndroidManifest.xml:7-8` | `allowBackup=true`（CookieManager 数据可能被云备份）；图标直接使用 `ic_launcher_foreground.xml` vector，无 adaptive/round icon | 关闭备份或加 backup rules；补 adaptive icon/mipmap |
| UI-35 | `AndroidManifest.xml:33-37` | 无 `VIEW` intent-filter/深链，系统 TV 桌面或手机浏览器无法直接唤起本应用打开 B 站链接 | 视产品需要补 `bilibili.com` 深链，并声明对应 exported |
| UI-36 | `LoginActivity.java:88-95, 106-109` | “重新生成二维码”按钮绘制成按钮样式但无焦点态，按确认键在页面任意处都会重新生成；无“返回 App”提示 | 按钮纳入焦点模型或去掉按钮样式；加返回提示 |
| UI-37 | `MainActivity.java:256-279` | 网格第三行被裁切且无滚动条/“加载更多”指示，用户不知道下面还有没有内容 | 增加底部渐隐/页码/滚动指示 |
| UI-38 | `README.md:10` | “默认页签：直播、推荐…” 实际是“页签集合”，默认 `navIndex=1`（推荐） | 改为“默认打开推荐页” |
| UI-39 | `docs/HANDOFF_REPORT.md:122-127, 168` | 交接写的验证环境 `/tmp/android-sdk` 现已不存在，`local.properties` 指向 `~/Library/Android/sdk`；:168 说 Lint 警告“主要为全屏标志和旧 Java 目标版本”，实际报告是固定方向 ×4、JS、DrawAllocation | 更新可复现环境说明；把 Lint 警告原文列出 |
| UI-40 | `design-qa.md:54, 51` | “persistent native search field”实际输入仍走系统对话框；“native video/image-text dynamics”列表原生、详情仍 WebView | 措辞精确到“列表原生、详情 WebView” |
| UI-41 | `README.md:11` | “加载/离线状态”有兜底，但兜底是假数据/死链且无重试，不能算真正的离线状态 | 与 UI-19 一起改，或删掉“离线状态”表述 |

---

## 3. 产品完成度与 WebView 原生化

### 3.1 当前完成度（按页面）

| 模块 | 状态 | 缺口 |
|---|---|---|
| 首页/直播/热门/排行/分区 | 原生网格 ✓ | 仅第一页；无刷新/分页；焦点扫描即请求；横向跨行 |
| 动态/收藏/历史/稍后再看/追番 | 原生列表 ✓ | 仅第一页；收藏只有第一个文件夹；错误/空/登录态混同；无退出登录 |
| 我的/账号/设置 | 原生界面 ✓ | 设置不生效；版本卡无动作；无退出登录；无昵称头像 |
| 搜索 | 原生结果 ✓ | 输入依赖对话框/IME；仅第一页；错误/空态混同 |
| 播放器 | 原生 MediaPlayer ✓ | 仅渐进式 durl；无记忆播放/连播；设置未打通；错误态无重试 |
| 用户主页/主播详情/图文动态/兜底卡片 | WebView ✗ | 无加载错误态；移动 UA；遥控器体验差；部分路由直接失败 |

### 3.2 原生化页面与接口需求（接口名来自现有仓库代码或公开 API，**线上行为需实测**）

| 页面 | 需要的内容 | 建议接口（`[推测]` 为待验证） | 粗估工作量 |
|---|---|---|---|
| UP 主主页 | 头像/昵称/签名/粉丝/关注数 + 投稿视频网格 + 动态 tab | `x/web-interface/card?mid=`（基础资料）；投稿列表 `x/space/wbi/arc/search?mid=&pn=&ps=&order=pubdate` `[推测-需 WBI]`；动态 `x/polymer/web-dynamic/v1/feed/space?host_mid=` `[推测-需 WBI]` | 2–3 人日（含 WBI 签名） |
| 未开播/已下播主播页 | 主播信息 + 直播间封面/状态 + 最近回放/投稿 | `room/v1/Room/get_info?room_id=`（`PlaybackRepository.java:165` 已在用）；`live_user/v1/Master/info?uid=` `[推测]`；`xlive/web-room/v1/index/getInfoByRoom?room_id=` `[推测]` | 1–2 人日 |
| 图文动态详情 | 作者信息 + 正文/图片/转发卡片 + 评论 | `x/polymer/web-dynamic/v1/detail?id=` `[推测]`；评论 `x/v2/reply/main?type=11&oid=` `[推测]` | 2–3 人日（结构最杂） |
| 番剧/影视详情 + 选集 | 封面/简介/评分 + 选集网格 + 直接从某一集起播 | `pgc/view/web/season?ep_id=/season_id=`（`PlaybackRepository.java:107-130` 已在用） | 1–2 人日 |
| 收藏夹选择页 | 收藏夹列表 + 进入某个收藏夹 | `x/v3/fav/folder/created/list-all?up_mid=`（`BiliRepository.java:294-297` 已在用） | 0.5–1 人日 |
| 兜底/错误页改造 | 错误原因 + 重试 + 真实链接 | 无需新接口 | 0.5 人日 |

**关键前置**：B 站空间类接口普遍需要 **WBI 签名**（`wbi/img` 取 key + mixin key 重排），当前代码只有 passport 的 `appkey+md5+APP_SECRET` 签名（`BiliAuthRepository.java:94-139`）。建议单独抽 `WbiSigner`（缓存 key、失败回退），否则“UP 主主页/投稿列表/空间动态”无法上线。合计约 **8–12 人日**（不含设计稿/QA，接口变动与风控返工另计）。

### 3.3 建议的产品口径

- 对外别宣称“全部原生”：准确表述是“首页/分区/账号列表/搜索/播放器原生；UP 主主页、主播状态、图文动态详情仍为网页兜底”。
- 设置页先“说到做到”再考虑增加项；无法实现的“自动播放”不要放开关。
- 触屏要么支持，要么在 README 明确“仅遥控器/键盘设备”。

---

## 4. 文档一致性检查

### 4.1 版本与产物（已核对，一致）

| 声明 | 证据 | 结论 |
|---|---|---|
| `1.4.0 / versionCode 6`（HANDOFF:5） | `app/build.gradle:13-14`；`output-metadata.json`；APK badging `versionName='1.4.0' versionCode='6'` | ✅ 一致 |
| 根 APK SHA-256 `3d1fb35d…5ce8`（HANDOFF:33） | 我计算仓库根 `BiliLivingRoom-debug.apk` 与 `app/build/outputs/apk/debug/app-debug.apk` 的 SHA-256，均完全相同 | ✅ 一致 |
| “主要代码约 2,100 行”（HANDOFF:25） | 8 个 Java 文件合计 2,094 行 | ✅ 基本准确 |
| 仅 LAUNCHER、无 Leanback、touchscreen 非必需（README:21、HANDOFF:12-17） | APK badging：`launchable-activity: MainActivity`、`uses-feature-not-required: android.hardware.touchscreen`，无 leanback/television feature | ✅ 一致 |
| BrowserActivity 兜底用户主页/动态详情（HANDOFF:81,160-163） | `MainActivity.java:589-594` + `BrowserActivity.java` | ✅ 一致 |
| 自绘 Canvas、`area/navIndex/...` 焦点、1920×1080 基准 `dp()`（HANDOFF:84-90） | `MainActivity.java:44-77, 612-615` | ✅ 一致 |

### 4.2 README（45 行）与代码事实

| README 行 | 原文 | 代码事实 | 结论 |
|---|---|---|---|
| :8 | 搜索页提供明显的原生输入框 | 画布只绘制搜索框，按确认键弹系统 `AlertDialog`+IME（MainActivity:348-357, 553-577） | ⚠️ 表述有歧义（UI-09） |
| :13 | 账号、历史和分区页才使用独立网页容器 | 账号/历史/分区均已原生；WebView 实际是用户空间/动态详情/兜底（MainActivity:589-594） | ❌ 过时（UI-11） |
| :17,19 | 分区、我的内账号/历史/收藏/追番/设置均原生 | 与代码一致 | ✅ |
| :10 | “默认页签：直播、推荐…” | 是页签集合，默认 `navIndex=1` 推荐 | ⚠️ 措辞（UI-38） |
| :11 | 分区页与加载/离线状态 | 有加载态；离线兜底是假数据/死链且无重试 | ⚠️ 夸大（UI-19/41） |
| :12 | 顶部导航焦点即切换 | `MainActivity.java:417-421` 一致 | ✅ |
| :14-16 | 原生弹幕/倍速/清晰度、两级菜单、图标按钮 | `PlayerActivity.java:184-215, 296-354` 一致 | ✅（但设置未打通，见 UI-01） |
| :21 | Manifest 只声明 LAUNCHER、无 TV 特征、触摸屏非必需 | APK badging 一致 | ✅ |
| :25-31 | 遥控器按键表 | 基本一致；唯 `Back` “返回首页”不成立（UI-08） | ⚠️ 1 处错误 |
| :39 | APK 输出 `app/build/outputs/apk/debug/app-debug.apk` | 文件存在且 SHA 与根 APK 相同 | ✅ |

### 4.3 design-qa（85 行）主要问题

- :46 “no remaining P0/P1/P2 visual issues” 与 :58 “No actionable P0, P1, or P2 findings remain”、:85 “passed”：**结论范围从“视觉”扩到“全部”**，与 HANDOFF P1/P2 冲突（见 UI-02）。
- :71 “Dynamic, favorites, account, history, settings … exercised while logged out” 与 :83 “authenticated … additional device test”：**“通过”不覆盖登录态**。
- :12 引用设置页截图 `implementation-settings-native.png`，图内版本为 **1.3.0**，早于代码 1.4.0；证据已过期（UI-23）。
- :54 “persistent native search field”“search video played”：搜索输入仍是系统对话框；普通视频播放截图存在，但 `qa/implementation-search-bangumi-playback-v1.4-fixed-late.png` 显示搜索番剧播放失败，与“no P1/P2”矛盾（UI-02）。
- :66、:75 声称 Android 15 模拟器与 lint 通过：lint 报告可核对为 **0 errors, 6 warnings**（不是无告警）；模拟器 API 级别无法从产物验证（只有截图分辨率 1920×1080 可核对，抽查的 4 张均为 1920×1080）。
- :81-83 P3 的三条“未来打磨”实际包含 Media3/PGC 与登录态验证——与 HANDOFF 的 P1/P2 定位不一致。

### 4.4 HANDOFF（201 行）主要问题

- 版本、APK 哈希、行数、Manifest、架构、已知问题（PGC、凭据、搜索风控、WebView、发布链路）均与代码/产物一致，是四份文档中最可靠的。
- **遗漏**：设置页三项不生效且不持久化；无刷新/分页（全部列表只取第一页）；触屏设备不可操作；无退出登录；无状态保存。这些是已交付界面的一部分，交接报告未记录。
- :69 “设置已做原生界面”属实，但没有说明“仅界面、开关无效果”，容易被接手人理解为已实现。
- :122-127 验证环境 `/tmp/android-sdk` 在本机已不存在，`local.properties` 指向 `~/Library/Android/sdk`；:168 对 Lint 警告的描述（全屏标志/旧 Java）与现有报告（固定方向 ×4、`setJavaScriptEnabled`、`DrawAllocation`）不符（UI-39）。
- :186 “用真实账号重新测试动态、收藏、历史和追番” 说明账号态尚未验收，与 design-qa 的 passed 形成对照（支持 UI-02）。

### 4.5 结论

- **版本号、APK、Manifest 声明、构建产物类文档可信。**
- **功能描述类文档需要重写**：README 2 处过时/错误；design-qa 结论夸大且证据过期；HANDOFF 遗漏 3 类已交付行为。
- 建议在 README 增加“已知限制”小节（设置仅 UI 状态、列表仅第一页、触屏不可用、WebView 范围），并把 design-qa 明确标注为“视觉 QA，不代表功能验收”。

---

## 5. 修复优先级建议

**第 1 批（P1，1–2 天）**
1. UI-01 打通设置三项：偏好持久化 + 传参 + 播放器读取；明确“自动播放”语义。
2. UI-02 修正 design-qa 结论范围、替换 1.3.0 截图、把失败截图纳入证据；README 补“已知限制”。

**第 2 批（P2，核心体验，3–5 天）**
3. UI-04 横向焦点同行移动；UI-05 拆分筛选焦点/生效索引；UI-03 页签去抖与请求取消。
4. UI-06/07 统一 `LOADING/EMPTY/ERROR/LOGIN_REQUIRED`，加“重试”和“加载更多/刷新”。
5. UI-08 Back 层级；UI-09 搜索输入 TV 化（至少 IME action + 按钮可达 + 热门搜索）。
6. UI-10/11 WebView 加载/错误/UA/暂停；UI-13 未开播主播路由修正；UI-19 兜底链接修正。
7. UI-14 触屏最小支持（点击=确认、播放器点击暂停）；UI-15 状态保存。
8. UI-12 + §3 原生化按季度排期（WBI 签名先行）。

**第 3 批（P3，随版本清理）**
UI-16 安全区/弹窗缩放、UI-17 线程关闭、UI-18 播放器生命周期、UI-20 退出登录、UI-21 版本统一、UI-23 证据归档、UI-29 无障碍、UI-30 绘制性能、UI-31~41。

**验收建议（真机）**
- 1080p TV、720p 盒子、4K 盒子、触屏平板各一台；
- 覆盖：8 页签 D-pad 全遍历、每页 Back、搜索结果 Back、设置开关前后播放器行为、登录/登出、断网/风控、进程死亡恢复、WebView 用户页/动态详情、未开播主播、触屏点击；
- 用 `dumpsys`/Logcat 确认无 `FATAL EXCEPTION`，并复核 `design-qa.md` 每一项在对应构建上仍成立。

---

## 6. 证据与限制

**已读取的文件**：`MainActivity.java`、`BrowserActivity.java`、`AndroidManifest.xml`、`styles.xml`、`PlayerActivity.java`、`PlaybackRepository.java`、`BiliAuthRepository.java`、`LoginActivity.java`、`BiliRepository.java`、`VideoItem.java`、`app/build.gradle`、`build.gradle`、`settings.gradle`、`gradle.properties`、`README.md`、`design-qa.md`、`docs/HANDOFF_REPORT.md`、`app/build/reports/lint-results-debug.txt`。

**对 APK 的核对（只读命令）**：
- `aapt2 dump badging BiliLivingRoom-debug.apk`：`versionCode=6`、`versionName=1.4.0`、`application-debuggable`、仅 `LAUNCHER`、`touchscreen` 非必需；
- SHA-256：根 APK 与 `app/build/outputs/apk/debug/app-debug.apk` 均为 `3d1fb35dc746ab8218c1f81ba9207ec92495979d2b03e54a3e706b0308105ce8`（与 HANDOFF 一致）。

**抽查的截图（用 read_image 实际查看，均为 1920×1080）**：
1. `qa/implementation-settings-native.png`（1.3.0，设置页，焦点在“默认弹幕”）；
2. `qa/implementation-home-1.3.1.png`（推荐首页，4 列网格，第三行被裁切）；
3. `qa/implementation-search-v1.4.png`（搜索页空态，7 个结果分类，搜索框未被编辑）；
4. `qa/implementation-search-bangumi-playback-v1.4-fixed-late.png`（播放器“播放失败，请返回后重试”）。

**未验证/需实测**：
- 所有“运行时会怎样”的判断（如 D-pad 是否能到达 AlertDialog 按钮、后台音频是否继续、Android 15 沉浸式行为、非 16:9 设备实际留白/裁切）；
- 登录态动态/收藏/历史/追番的真实返回结构与 24 条上限的真实表现；
- PGC/番剧播放失败的具体根因（HANDOFF 的 CDN/会员/版权解释合理，但本次未联网验证）；
- 各接口（尤其 WBI 空间接口）当前是否仍可用、字段是否变化。

**未修改任何源文件**：本报告只新增 `docs/review/ui-product.md`；未改动 `app/`、`README.md`、`design-qa.md`、`docs/HANDOFF_REPORT.md` 或任何构建产物。

---

## 附录：快速索引（文件:行号）

- 8 页签/筛选定义：`MainActivity.java:54-62`
- 页面分发：`MainActivity.java:187-202`
- 焦点状态字段：`MainActivity.java:63-77`
- 按键入口：`MainActivity.java:400-415`
- 横向/纵向移动：`MainActivity.java:417-458`
- 确认动作：`MainActivity.java:466-492`
- 页签切换加载：`MainActivity.java:494-511`
- 网络/空态辅助：`MainActivity.java:138-185, 231-254, 315-320, 368-374`
- 设置页：`MainActivity.java:329-333, 546-551`
- 搜索输入：`MainActivity.java:553-587`
- 路由：`MainActivity.java:589-594`
- 缩放：`MainActivity.java:612-615`
- WebView：`BrowserActivity.java:18-51`
- 播放器设置相关：`PlayerActivity.java:38, 45, 104-108, 243-245`
- 播放解析默认清晰度：`PlaybackRepository.java:132-137`
- 兜底数据：`BiliRepository.java:505-514`
- 主播/用户/动态目标：`BiliRepository.java:227-240, 279`
- 收藏首夹：`BiliRepository.java:289-306`
- 认证/UA：`BiliAuthRepository.java:127, 78-92`
- Manifest：`AndroidManifest.xml:4, 7-8, 10, 30, 33-37`
- 证据/文档：`design-qa.md:12, 46, 54, 58, 71, 75, 83, 85`；`README.md:10-13, 21, 31, 39`；`docs/HANDOFF_REPORT.md:5, 25, 33, 69, 81, 106, 122-127, 139-168, 186`；`qa/implementation-settings-native.png`
