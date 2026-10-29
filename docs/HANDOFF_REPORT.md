# 哔哩客厅 Android 项目交接报告

> 交接日期：2026-10-29
> 当前版本：**1.18.2**（`versionCode 36`）
> v1.18.2 代码提交：`d94d4a2`（tag `v1.18.2`）；最新 HEAD 以 `git log -1` 为准
> 最近稳定 tag：`v1.18.2`；GitHub Release：`v1.18.2`
> 应用 ID：`com.ponyrun.bililivingroom`
> 项目路径：`/Users/ponyrun/Documents/AndroidApp/ilibilib`

---

## 1. 项目一句话

面向遥控器/电视大屏的 B 站第三方客户端：纯 Java 8，无 Compose、无 XML Layout，主界面与播放器控制层全部由自定义 `View.onDraw(Canvas)` 绘制；播放内核为 AndroidX Media3 / ExoPlayer。

当前已经打通：TV 扫码登录、首页/直播/推荐/热门/排行榜/动态/收藏/我的/搜索、视频详情页、原生播放器、弹幕、番剧选集/分 P、播放进度回写与本地续播、UP 主页、动态详情、搜索 TV 化。

---

## 2. 交付物与当前状态

| 交付物 | 位置 | 状态 |
| --- | --- | --- |
| 可安装 Debug APK | `../BiliLivingRoom-debug.apk` | `1.18.2 (36)` |
| Gradle 输出 APK | `../app/build/outputs/apk/debug/app-debug.apk` | `assembleDebug` 生成 |
| 源代码 | `../app/src/main/java/com/ponyrun/bililivingroom/` | Java 8，约 10,065 行 |
| 需求/版本记录 | `CHANGELOG.md` | v1.4.0 → v1.18.2 |
| 历史基线评审 | `PROJECT_STATUS_AND_ROADMAP.md` | v1.4.0 基线评审 + 后续路线（顶部已标注为历史快照；当前状态以本报告为准） |
| 设计与实现 QA | `../design-qa.md` | 视觉、交互、验收记录 |
| 截图证据 | `../qa/` | 按版本归档，v1.5 → v1.18.2 |
| 开源说明 | `../NOTICE.md`、`../LICENSE.md` | GPL v2 与上游归属 |

当前 APK SHA-256（每次重新构建后以 `shasum -a 256 BiliLivingRoom-debug.apk` 为准）：

```text
102440ee9037a7c1bc2cee5dc82eb62636d9f768e8be23d5d2506d35a7ec6511
```

当前构建状态：

- `./gradlew clean assembleDebug lintDebug` → **BUILD SUCCESSFUL**
- Lint：**0 errors / 37 warnings**（固定横屏、DiscouragedApi、WebView JavaScript、依赖版本提示等）
- v1.17.2 已提交并打 tag、GitHub Release 已发布；v1.18.0–v1.18.1 当前仅在本地工作区
- 测试模拟器内保留了真实账号登录态；**开发收尾时再统一退出/清除 Cookie**，不要把二维码、Cookie 或账号信息提交到仓库

---

## 3. 环境与快速启动

### 3.1 已验证环境

| 项 | 值 |
| --- | --- |
| JDK | `/Applications/DevEco-Studio.app/Contents/jbr/Contents/Home`（JBR 21） |
| Android SDK | `~/Library/Android/sdk` |
| compileSdk / targetSdk | 35 |
| minSdk | 23 |
| build-tools | 35.0.0 |
| AGP / Gradle | 8.7.3 / 8.9 |
| AVD | `bili_tv`（Android 15 TV，1920×1080，横屏） |

`local.properties` 内容应为：

```properties
sdk.dir=/Users/ponyrun/Library/Android/sdk
```

如果换机器，需重新生成 `local.properties`，并把 `JAVA_HOME` 指向可用的 JDK（已验证 JBR 21）。

### 3.2 构建

```bash
cd /Users/ponyrun/Documents/AndroidApp/ilibilib

JAVA_HOME=/Applications/DevEco-Studio.app/Contents/jbr/Contents/Home \
ANDROID_HOME=$HOME/Library/Android/sdk \
./gradlew clean assembleDebug lintDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

### 3.3 模拟器与安装

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

emulator -avd bili_tv -gpu swiftshader_indirect -no-audio -no-boot-anim -no-snapshot
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ponyrun.bililivingroom/.MainActivity
```

说明：

- 模拟器可能被识别为 `emulator-5554` 或 `emulator-5556`，以 `adb devices` 为准。
- `PlayerActivity`、`UserSpaceActivity`、`VideoDetailActivity`、`DynamicDetailActivity` 未导出；直接 `am start` 调试前需要 `adb root`。
- 主界面入口 `MainActivity` 是导出的，可正常从桌面启动。
- 本机没有可用的 arm64 真机调试链路时，以 AVD 验收为主；真机帧率/性能需要另测。

---

## 4. 当前功能总览

### 4.1 主界面与导航

顶部 8 个胶囊页签：

`直播`、`推荐`、`热门`、`排行榜`、`动态`、`收藏`、`我的`、`搜索`

- 顶部导航**焦点即切换页面**，无需按确认键。
- 常规内容区为 3 列 TV 大卡片；搜索按内容类型使用 4 列横版、6 列海报或 3 列用户卡；卡片有封面、时长、播放量、标题、UP 名，并支持分页加载、错误重试、空态和顶部 `↑` 刷新。
- 列表接口统一使用 B 站 Web API；图片通过 `BiliRepository` 异步加载并缓存。

二级分类栏（有分类栏的页面）：

| 页面 | 分类 |
| --- | --- |
| 推荐 | 综合 / **番剧** / 动画 / 游戏 / 科技 / 知识 / 影视 / 生活 / 音乐 / 美食 |
| 热门 | 同推荐 |
| 排行榜 | 全站 / **番剧** / 动画 / 音乐 / 舞蹈 / 游戏 / 知识 / 科技 / 生活 / 影视 |
| 搜索 | 全部 / 视频 / 番剧 / 影视 / 主播 / 直播间 / 用户 |
| 直播 | 推荐 / 网游 / 手游 / 娱乐 / 单机 / 虚拟主播 / 电台（按需求**不加入番剧**） |

二级导航行为：

- 选中项背景为**粉色**，未选中保持原色；
- 左右移动焦点时**直接切换并加载分类**，无需确认键；
- 快速连续切换时以最后一次请求为准（由 `pageRequestGeneration` 保证旧结果不覆盖新页面）。

### 4.2 视频详情页

所有普通视频缩略图先进入 `VideoDetailActivity`，在详情页按“播放”才进入播放器（直播/番剧目前直接进播放器）。

详情页包含：

- 标题、UP 头像/昵称/粉丝数、发布时间、播放/弹幕/评论数、简介；
- 右侧视频封面 + 时长；
- 操作按钮：`▶ 播放`、`稍后再看`、`点赞`、`投币`、`收藏`（显示数量）；
- 多 P 视频：底部自动切换为「选集」，显示 P1..Pn 缩略图，点击后带 `?p=` 进入播放器并自动切 P；
- 单 P 视频：底部显示「推荐」相关视频；
- UP 名称/头像可点击进入原生 UP 主页（`UserSpaceActivity`）；
- 焦点选中按钮为粉色，未选中为原色；播放 ↑→ UP 行，UP 行 ↓→ 播放。
- 背景由横向影院遮罩与底部垂直暗场共同保证文字/按钮对比；五个操作按钮均带平台图标，焦点使用品牌粉与白色外框。

### 4.3 播放器

基于 AndroidX Media3 / ExoPlayer 1.4.1：

- 普通视频/番剧：优先 DASH 音视频分轨，失败回退 progressive；
- 直播：HLS；
- 清晰度切换、候选 URL/备用 CDN 回退、桌面 Chrome UA + Referer/Cookie；
- 倍速 0.75×–2×；
- 单 P/多 P 切换、番剧剧集切换、自动连播；
- 播放中每 15 秒回写 B 站观看进度（`/x/v2/history/report`，失败回退 heartbeat）；
- 本地播放进度续播（`PlaybackResume`，按 `ep:/cid:/url:` 保存，5 秒/暂停/退出保存，片尾清除）；
- 番剧左下标题显示「季名 · 第N集 剧集标题」，多 P 显示「标题 · P<number>」。
- 连续播放：播放结束优先按顺序下一集/下一 P；没有下一项时自动打开相关推荐第一条；直播不自动续播。
- 竖屏视频按原始比例居中显示（左右黑边），横屏视频全屏，不再被拉伸。
- 进度层显示播放/暂停图标、缓存进度、品牌粉已播进度、白色拖动圆点与时间；直播显示 `LIVE`。
- 快进/快退、暂停/继续、弹幕开关、倍速切换均有短时状态反馈；直播按左右键会提示不支持快进或快退。
- 播放中重缓冲会显式提示；解析或线路失败显示可读错误面板，按 OK 可重新解析并加载。
- 弹幕/倍速/清晰度的下级选项为横向紧凑条，左右或上下均可选项；选集/分 P 因条目多保留纵向八行滚动列表。
- 弹幕限制在屏幕上方安全区，按横向位置排序后分配八条车道，并截短超长文本，降低碰撞和控制层遮挡。

播放器三区（上/中/下）：

| 区域 | 呼出 | 内容 |
| --- | --- | --- |
| 上区 | `↑` | 弹幕、倍速、清晰度、选集/分P |
| 中区 | 上区 `↓` / 下区 `↑` | 进度条控制：`←/→` 快退/快进 10 秒，`OK` 暂停/继续 |
| 下区 | `↓` | 简介 + 选集/推荐缩略图（多 P/番剧为选集，单 P 为推荐） |

三区可通过 `↑/↓` 循环；下区内部保留 tab/卡片导航，卡片行 `↓` 才回到上区。

### 4.4 弹幕

- 加载 `comment.bilibili.com/<cid>.xml`，支持 raw deflate / gzip 解码；
- 默认开启，可在播放器上区关闭/开启；
- 字号可在小/中/大三档间选择，透明度可选 60%/80%/100%，速度可选慢/标准/快；8 条通道、描边增强、阴影位图预渲染；
- 移动使用 SystemClock 单调时钟外推，正常播放不逐帧修正锚点，带单调保护避免左右回退；
- 目标 60fps（`postInvalidateOnAnimation`）；弹幕文字位图 LRU 缓存（8MB 上限）；
- 加载/seek/切 P/切集后只让新弹幕从右侧边缘进入，跳过更早的旧弹幕。

### 4.5 搜索

- TV 搜索落地页：热门搜索（B 站热搜接口）+ 本地搜索历史（SharedPreferences，最多 8 条）+「清空历史」chip；
- 遥控器 D-pad 直接选词搜索，无需键盘；
- 搜索框 OK 打开大屏输入弹层（仍使用系统 IME，作为手动输入兜底）；
- 分类、分页、错误重试、顶部 `↑` 刷新保留。

### 4.6 账号 / “我的”

- TV 二维码登录（4 秒轮询），Cookie 写入 `CookieManager`；
- “我的”页改版：左侧固定资料卡（头像/昵称/LV/VIP、硬币/关注/粉丝）+ 菜单；右侧**原地**显示：
  - 历史记录（3 列网格，cursor 分页）；
  - 收藏与订阅；
  - 稍后再看；
  - 我的追番；
  - 设置（双栏「播放 / 弹幕 / 账户与数据 / 关于」分组，含默认弹幕、自动播放、优先高清、字号、透明度、速度、清除本地数据和应用信息）；
  - 检测更新（位于设置「关于」分组，GitHub Release OTA，见 4.9）；
  - 退出登录（动作，不跳页）；
- 四类内容卡具有独立状态表达：历史观看进度条、收藏「收藏」、稍后再看「待看」、追番「追番」；列表默认等大，仅当前遥控器焦点放大；
- 空内容或加载失败时显示居中说明面板，按 OK 可重新加载；
- 侧栏和内容徽标均使用 Android 图标资源；四类内容卡显示观看/收藏/加入日期；切换栏目时显示 3×2 骨架屏；
- 资料横幅优先使用空间接口 `top_photo`，接口风控、超时或空值时自动回退 Pearl Glass 主题横幅；
- 顶部 `↑` 刷新资料 + 当前功能区；
- 未登录保留扫码登录卡片；登录态变化时自动重载/清空资料。

### 4.7 UP 主页与动态详情

- `UserSpaceActivity`：原生 UP 主页，头像/昵称/签名/粉丝/投稿数 + WBI 签名的投稿视频网格，支持分页；视频卡片点击进入视频详情页；可显示可选直播状态。
- `DynamicDetailActivity`：原生动态详情，作者/时间/正文/最多 9 图；失败可重试。
- 路由：
  - `space.bilibili.com/<mid>` → `UserSpaceActivity`
  - `t.bilibili.com/<id>` → `DynamicDetailActivity`
  - `/video/` → `VideoDetailActivity`
  - live / bangumi → `PlayerActivity`
  - 其它链接 → `BrowserActivity` 兜底；页面带原生 TV 返回/刷新工具栏，MENU 聚焦工具栏，方向下键返回网页正文。

### 4.8 直播与番剧

- 直播卡片直接进入 `PlayerActivity`，使用 HLS；未开播主播的搜索结果 target 已改为 space URL，进原生 UP 页。
- 番剧卡片直接进入 `PlayerActivity`，支持 season/episode 解析、选集切换、剧集标题、进度回写与本地续播。
- 番剧/直播**暂无独立详情页**（视频详情页当前面向普通视频）。

### 4.9 OTA 更新

- 设置右栏「关于 / 检测更新」→ `UpdateActivity`。
- `UpdateManager` 请求 `https://api.github.com/repos/HarmonyRun/bilibili-wangye-fengzhuang-tv/releases/latest`，比较 tag 版本与 `BuildConfig.VERSION_NAME`。
- 有新版本时显示版本号/更新说明/APK 大小；下载到 `cache/updates`，通过 `FileProvider` + `ACTION_VIEW` 拉起系统安装器；API 26+ 需允许“安装未知应用”。
- 当前 Release：`v1.16.1`，APK 使用本机 Debug 签名；正式发布需固定 release keystore，否则已安装应用无法覆盖升级。

---

## 5. 遥控器交互约定

| 场景 | 按键 | 行为 |
| --- | --- | --- |
| 主界面顶部导航 | `← →` | 焦点即切页，无需 OK |
| 主界面二级分类 | `← →` | 焦点即切分类并加载，无需 OK |
| 主界面 | `↓` | 导航 → 分类/侧栏 → 网格 |
| 主界面 | `↑` | 网格 → 分类 → 导航；导航上按 `↑` 刷新 |
| 主界面卡片 | `OK` | 打开详情/播放/栏目 |
| 搜索页 | `↓` | 搜索框 → 分类 → 热搜/历史 chip |
| 搜索框 | `OK` | 打开大屏输入弹层 |
| 视频详情 | `↑ / ↓` | UP 行 ↔ 操作按钮；`← →` 切换按钮 |
| 视频详情按钮 | `OK` | 播放 / 写操作 |
| 播放器（无面板） | `↑` / `↓` | 打开上方功能栏 / 下方信息区 |
| 播放器上区 | `↓` / `↑` | 中区 / 下区循环；`← →` 切功能 |
| 播放器中区 | `← →` | 快退/快进 10 秒；`OK` 暂停/继续 |
| 播放器下区 | `↑ / ↓` | tab → 卡片行 → 上区循环 |
| 播放器 | `OK` | 暂停/播放，菜单内为确认 |
| 网页兜底页 | `Menu` | 聚焦原生返回/刷新工具栏 |
| 全局 | `Back` | 返回上一层/关闭面板/退出 |
| 全局 | `Menu` | 快捷打开搜索输入 |

---

## 6. 代码结构

| 文件（`app/src/main/java/com/ponyrun/bililivingroom/`） | 行数 | 职责 |
| --- | ---: | --- |
| `MainActivity.java` | 2251 | 8 页主界面、自绘导航/网格/搜索/我的、焦点状态机、刷新与分页 |
| `PlayerActivity.java` | 2344 | ExoPlayer 播放、控制层、三功能区、弹幕、选集/分P、续播、进度回写 |
| `BiliRepository.java` | 1670 | 首页/排行/直播/搜索/账号/动态/UP/详情/收藏夹/WBI 相关 API、图片缓存 |
| `PlaybackRepository.java` | 1001 | BV/AV/番剧/直播流解析、清晰度、弹幕、剧集/分P、进度上报 |
| `VideoDetailActivity.java` | 645 | 原生视频详情页、选集/推荐、UP 跳转、写操作 |
| `UserSpaceActivity.java` | 423 | 原生 UP 主页、投稿分页、可选直播状态 |
| `BiliAuthRepository.java` | 194 | TV 二维码登录、签名、Cookie、用户 UID |
| `DynamicDetailActivity.java` | 213 | 原生动态详情 |
| `LoginActivity.java` | 157 | 登录界面与轮询生命周期 |
| `WbiSigner.java` | 95 | WBI mixinKey / wts / w_rid 签名 |
| `SearchHistory.java` | 72 | 搜索历史 SharedPreferences |
| `BrowserActivity.java` | 176 | 带原生 TV 工具栏、网页放大和焦点增强的 WebView 兜底页 |
| `PlaybackResume.java` | 49 | 本地播放进度 |
| `PlaybackSettings.java` | 43 | 弹幕/自动播放/高清设置 |
| `VideoItem.java` | 36 | 卡片统一模型 |
| `UpdateManager.java` | 332 | GitHub Release 检查、APK 下载、FileProvider 安装 |
| `UpdateActivity.java` | 291 | 软件更新页面 |

### 关键实现特征

- UI 主体不是 Compose/XML Layout，而是 `View.onDraw(Canvas)` 自绘；改界面前先理解坐标、焦点、滚动窗口。
- 尺寸以 1920×1080 为基准，`dp()` 按视口比例缩放。
- 焦点由 `area` / `navIndex` / `filterFocusIndex` / `gridIndex` / `firstRow` / `sidebarIndex` / `rootSection` 等状态维护，不走 Android 控件焦点树（详情页/UP 页/动态页则使用标准 View 焦点）。
- 网络请求在后台线程，结果回主线程；列表用 `pageRequestGeneration` 防止旧请求覆盖新页面。
- 图片统一 `BiliRepository.loadImage`，带内存缓存；播放器详情页/下区封面另有小 LRU。
- 设置/搜索历史/播放进度分别用 `PlaybackSettings`、`SearchHistory`、`PlaybackResume` 存 SharedPreferences。

---

## 7. 数据与 API 清单

> 所有请求都以桌面 Chrome UA + `Referer: https://www.bilibili.com/` 为基础；需要登录的接口带 Cookie。
> 空间/UP 接口需要 WBI 签名；写操作需要 Cookie 中的 `bili_jct` 作为 csrf。

| 领域 | 主要接口 |
| --- | --- |
| 登录 | `x/web-interface/nav`、TV 二维码相关接口、Cookie 写入 |
| 视频 | `x/web-interface/view`、`x/player/playurl`（DASH/progressive） |
| 番剧 | `pgc/view/web/season`、`pgc/player/web/playurl` |
| 首页/分区 | `x/web-interface/ranking/v2`、`x/web-interface/newlist`、`x/web-interface/popular`、推荐 rcmd |
| 搜索 | `x/web-interface/search/square`、`x/web-interface/search/all/v2`、`x/web-interface/search/type` |
| 动态 | `x/polymer/web-dynamic/v1/feed/all`、`x/polymer/web-dynamic/v1/feed/space`、`.../detail` |
| 账号内容 | `x/v2/history`、`x/v3/fav/resource/list`、`x/v2/history/toview`、`x/space/wbi/arc/search`、`pgc/web/season` |
| UP/空间 | `x/web-interface/card`、`x/space/wbi/arc/search`（WBI）、`x/relation/stat` |
| 直播 | `room/v1/Room/get_info`、直播列表/分区、HLS playurl |
| 弹幕 | `comment.bilibili.com/<cid>.xml` |
| 进度 | `x/v2/history/report`（主）、心跳接口（兜底） |
| 详情页动作 | `x/web-interface/archive/like`、`x/web-interface/coin/add`、`x/v2/history/toview/add`、`x/v3/fav/folder/created/list-all`、`x/v3/fav/resource/deal` |
| OTA | `api.github.com/repos/HarmonyRun/bilibili-wangye-fengzhuang-tv/releases/latest`、Release APK asset |

错误处理原则：

- 区分网络失败、接口 `code != 0`、风控 HTML、无播放地址；
- UI 显示可读错误文案，并提供 `↑` 刷新/OK 重试；
- 列表分页结果带 `cursor`，由页面状态机维护 `loadedPage/pageCursor/loadingMore/noMore/moreFailed`。

---

## 8. 版本与 Git 索引

最近稳定 tag：`v1.16.2`；当前 v1.16.3 视觉一致性更新尚未要求提交/tag。

最近版本：

| 版本 | 主题 |
| --- | --- |
| v1.4.0 | 首个可运行 TV 客户端基线 |
| v1.5.0 | 项目稳定化、焦点/构建修复 |
| v1.6.0 | Media3/ExoPlayer 播放内核迁移 |
| v1.7.x | 列表分页、番剧选集、多 P、弹幕 |
| v1.8.x | 账号分页、顶部 ↑ 刷新、排行榜/稍后再看/追番分页 |
| v1.9.0 | 播放进度回写 B 站 |
| v1.10.0 | 搜索 TV 化（热搜/历史/大屏输入） |
| v1.11.0 | WBI、原生 UP/主播页、动态详情、清空搜索历史 |
| v1.12.x | 原生视频详情页、多 P 选集、UP 跳转 |
| v1.13.x | 播放器三区（上/中/下）、番剧集数标题、本地续播、弹幕优化 |
| v1.14.0 | 播放器三区循环、我的页改版、UP 聚焦、UP URL 规范化 |
| v1.14.1 | UP 选中态修复、我的页右侧原地切换 |
| v1.14.2 | 弹幕字号/60fps/平滑优化 |
| v1.14.3 | 详情焦点回退、弹幕匀速与边缘入场 |
| v1.14.4 | 二级导航番剧、焦点即切换、详情按钮粉色选中 |
| v1.15.0 | Pearl Glass Gallery 全局视觉升级 |
| v1.16.1 | 连续播放 + GitHub OTA 更新 |
| v1.16.2 | 竖屏视频比例修复 |
| v1.16.3 | Material Round 图标统一 + 更新页焦点视觉 |
| v1.17.x | 双栏设置、状态卡、动态画廊与收藏资料页 |
| v1.18.0 | 收藏夹目录与搜索分组 |
| v1.18.1 | 收藏筛选、搜索资料卡、账号头像 |
| v1.18.2 | 历史记录恢复选集/分P进度 |

完整逐版本说明见 `CHANGELOG.md`；版本迭代细节、剩余路线见 `PROJECT_STATUS_AND_ROADMAP.md`。

---

## 9. 验证与证据

### 9.1 已执行的工程验证

- `clean assembleDebug lintDebug`：**成功，0 errors / 37 warnings**；
- APK 可安装到 `bili_tv_qa` AVD；
- 模拟器真实账号登录态下完成主要流程验收（见下）。

### 9.2 已在模拟器验证的关键路径

- 首页/推荐/热门/排行榜/直播/动态/收藏/我的/搜索 8 页可进入；
- 二级分类「番剧」可直接加载，选中粉色、无需 OK；
- 推荐/搜索/UP 投稿卡片 → 视频详情页 → 播放；
- 视频详情：标题/UP/粉丝/日期/统计/简介/封面/操作按钮；多 P 选集；UP 行选中态；
- 播放器：上/中/下三区循环、清晰度/倍速/弹幕开关、分 P/剧集切换、自动连播、进度回写、本地续播；
- 播放器状态：普通视频/多 P、番剧选集和真实直播 HLS 均通过 1080p AVD 复核；错误面板、操作反馈和直播禁用 seek 状态均有截图证据；
- 弹幕：字号、平滑移动、seek 后边缘入场；
- 搜索：热搜/历史/清空历史、输入弹层、分类/分页；
- “我的”：左资料/菜单 + 右侧历史/收藏/稍后再看/追番/设置原地切换；
- UP 主页（WBI 投稿列表/分页/播放）、动态文字详情、动态/视频路由；
- 番剧标题显示第 N 集；00:52 退出后重进续播；
- 普通视频结束自动进入推荐视频；多P/番剧按顺序下一P/下一集；
- GitHub Release v1.16.1 检测、下载、FileProvider 安装器拉起，1.16.0 → 1.16.1 更新成功；
- 竖屏 BV13EnG63E4Z 比例正确、横屏 BV186Jq6uEFu 全屏无回归。
- v1.16.3 复核“我的”侧栏、视频详情操作区、播放器横向功能栏和更新页；统一矢量图标后焦点顺序、按钮尺寸和下级菜单入口无回归。

### 9.3 未做运行时验收/需下一阶段补测

- 详情页写操作：点赞、投币、稍后再看、收藏（实现已接接口，但为避免修改真实账号，未点击实测；也没有取消点赞/取消收藏状态）；
- 番剧/直播详情页（尚未实现）；
- 真机 60fps/长时间播放稳定性、弹幕车道碰撞、内存曲线；
- 未登录/登录过期/风控/网络断开等异常矩阵；
- 评论、发弹幕、投屏/遥控器语音等未实现。

### 9.4 证据位置

- 最新版本：`../qa/v1.18.1/`
- 公开精选截图：`../screenshots/`（首页/搜索/详情/播放器/动态/设置）
- 历史版本：`../qa/v1.5/` … `../qa/v1.18.0/`
- 早期截图：`../qa/implementation-*.png`
- 视觉/交互验收：`../design-qa.md`

> 模拟器使用 `swiftshader_indirect` 软件 GPU，`dumpsys gfxinfo` 帧率偏低，不代表真机；弹幕/播放器性能需要 TV 真机复测。

---

## 10. 已知问题与限制

### P0：发布工程化与安全

- 当前只有 **Debug APK**，未配置 release keystore、R8/ProGuard、正式签名、CI/CD、自动化测试。
- `BiliAuthRepository` 中仍有硬编码的 TV `APP_KEY` / `APP_SECRET`；Cookie 存在 `CookieManager`，没有独立加密存储。
- 公开发布前必须确认 B 站接口授权、凭据来源、隐私政策、GPL 归属与 NOTICE 合规。

### P1：功能缺口

- 番剧/直播没有独立详情页：番剧直接进播放器，直播直接进 HLS 播放器。
- 详情页写操作（点赞/投币/稍后再看/收藏）未实测，且没有 toggle 状态；收藏只调用默认收藏夹。
- WBI/空间接口、排行榜、搜索等依赖 B 站 Web API，存在风控、字段变更、接口失效风险。
- 登录过期/多设备挤下线时，目前主要靠页面重进/刷新恢复，缺少统一登录失效拦截。
- OTA 更新包必须与已安装应用使用同一签名；当前 Release 使用本机 Debug 签名，换机器或 CI 构建会因签名不一致无法覆盖安装，需要固定 release keystore。

### P2：体验与数据

- 弹幕采用位置排序与八车道避碰，但尚未做 TV 真机长时间压力测试；极端高密度弹幕仍可能受性能影响。
- 相关推荐卡片没有弹幕数。
- “我的”侧栏未放“账号中心”入口；无二级导航的收藏/动态/我的页没有分类栏。
- 二级导航焦点即加载会连续请求，快速扫过时请求略多；可考虑 200ms 防抖。
- 本地续播只保存在本机，不跨设备；自动连播会读取下一集/下一 P 的已存进度。
- 仅横屏、未专门优化触屏；无后台播放/画中画；评论、发送弹幕未做。
- 主体代码集中在巨型 Canvas View/Activity，后续需要拆分页面状态与绘制组件，补焦点单测。

---

## 11. 发布前检查清单

- [ ] 生成并保管 release keystore；配置 `signingConfigs.release`；开启 R8/ProGuard 并验证资源/反射。
- [ ] 版本号/versionName 与 `CHANGELOG.md` 同步；打 tag。
- [ ] 清理/外置硬编码 APP_KEY/SECRET；确认 Cookie 存储与退出登录清理。
- [ ] 配置 CI：`assembleRelease` + `lintRelease` + 冒烟测试；归档 mapping/APK。
- [ ] 增加核心单元测试：WBI 签名、URL/BV 解析、分页状态、焦点状态机。
- [ ] 真机矩阵：至少 1 台 Android TV + 1 台电视盒子；验证遥控器、长时间播放、弱网、登录过期。
- [ ] 验证写操作（点赞/投币/收藏/稍后再看）后，补取消/状态回显。
- [ ] 合规复核：GPL v2、NOTICE、B 站接口授权、隐私政策、二维码/账号信息不落库。
- [ ] 发布前移除开发期登录态与测试 Cookie，重置 AVD/测试设备。

---

## 12. 推荐后续路线

### P0（发布阻断）

1. Release 签名、R8、CI、崩溃/错误上报；
2. 凭据安全与登录失效处理；
3. 真机性能/弱网/长播验收。

### P1（产品完整性）

1. 番剧详情页（season/episode 列表、简介、播放按钮）与直播详情页（主播、状态、简介）；
2. 详情页写操作的 toggle 状态与取消逻辑；
3. 评论/弹幕发送；收藏夹选择；
4. 统一错误模型与接口降级。

### P2（体验/工程）

1. 弹幕高密度压力测试、性能模式、真机 60fps；
2. 触屏/手机适配、后台播放；
3. 拆分 `MainActivity`/`PlayerActivity` 的绘制与状态机，补测试；
4. 收藏/动态/我的二级分类与更多账号内容。

---

## 13. 接手人 30 分钟检查清单

- [ ] 读 `README.md`、本报告、`CHANGELOG.md`、`PROJECT_STATUS_AND_ROADMAP.md`。
- [ ] 执行 `clean assembleDebug lintDebug`，确认 0 errors。
- [ ] 安装 APK，确认顶部 8 页签、焦点即切页。
- [ ] 走通：推荐 → 番剧分类 → 视频详情 → 播放 → 弹幕 → 返回。
- [ ] 走通：播放器 ↑/↓ 三区、清晰度/倍速/选集、进度续播。
- [ ] 走通：搜索热搜/历史/输入、我的页各功能区、UP 主页、动态详情。
- [ ] 检查 logcat `FATAL EXCEPTION`、`ExoPlaybackException`、`BiliProgress`。
- [ ] 验收 v1.18.1 后确认 Git 工作区并按需提交/tag。
- [ ] 若要继续开发，先建分支；不要提交二维码截图、Cookie、`local.properties`、`app/build/`。

---

## 14. 关键文件索引

- 项目说明：`../README.md`
- 版本记录：`CHANGELOG.md`
- v1.4.0 历史基线评审与后续路线：`PROJECT_STATUS_AND_ROADMAP.md`（顶部已标注历史快照）
- 设计/QA：`../design-qa.md`
- 截图证据：`../qa/`
- 开源归属：`../NOTICE.md`、`../LICENSE.md`
- 构建配置：`../app/build.gradle`、`../gradle.properties`
- Manifest：`../app/src/main/AndroidManifest.xml`
- 主要源码：`../app/src/main/java/com/ponyrun/bililivingroom/`

---

## 15. 交接备注

1. **登录态**：`bili_tv` AVD 当前保留真实账号登录；开发完成后统一退出登录并清除 Cookie，再交付/归档。
2. **不要提交**：二维码截图、Cookie、账号截图、`local.properties`、`app/build/`、APK 以外的私密信息。
3. **调试命令**（非导出 Activity）：

   ```bash
   adb root
   adb shell am start -n com.ponyrun.bililivingroom/.VideoDetailActivity \
     -e video_target "https://www.bilibili.com/video/BV..."
   adb shell am start -n com.ponyrun.bililivingroom/.UserSpaceActivity --el user_mid 123456
   ```

4. **版本纪律**：每次交付更新 `CHANGELOG.md` / 本报告 / QA 截图，构建通过后 commit + tag。
5. **当前状态**：v1.18.1 已完成真实收藏夹目录切换与排序筛选、搜索全部及用户/主播分类、“我的”头像优先加载和动态多类型回归；发布级质量仍需完成第 11 节清单。

---

## 16. v1.16.1 连续播放与 OTA（2026-10-20）

- 连续播放：`PlayerActivity` 预取相关推荐；结束时优先 `playNextIfAvailable()`（番剧下一集/多P下一P），否则自动打开推荐第一条新播放页（强制 autoplay、finish 当前页）；推荐加载中会 pending 等待；直播不续播。
- OTA：`UpdateManager` 请求 GitHub Releases latest、版本比较（semver）、APK 下载到 `cache/updates`、FileProvider 安装；`UpdateActivity` 自动检查/重新检查、下载进度、未知来源授权引导；设置第 4 行入口。
- Release：`v1.16.1` 已上传 APK 到 `HarmonyRun/bilibili-wangye-fengzhuang-tv`；实测 1.16.0 检测并更新到 1.16.1。
- 版本/构建：`1.16.1 (28)`，Lint 0 errors / 36 warnings，APK SHA-256 `2ad6f18554a18aa4138c0885f262873128bbc3ae8314a672d251ca6021ab32c4`。
- 证据：`qa/v1.16.1/`。
- 注意：OTA 必须保持签名一致；公开仓库默认不包含 QA/design 截图与 APK。

---

## 17. v1.16.2 竖屏视频比例修复（2026-10-20）

- 现象：竖屏视频在横屏 Activity 中被 SurfaceView 铺满，导致拉伸。
- 修复：`Player.onVideoSizeChanged` 计算 `width/height/旋转/像素宽高比`，SurfaceView 按比例居中；竖屏左右黑边，横屏全屏；overlay/弹幕/控制层保持全屏。
- 验证：竖屏 BV13EnG63E4Z（1080×1920）比例正确；横屏 BV186Jq6uEFu 全屏无回归。证据 `qa/v1.16.2/`。
- 版本：`1.16.2 (29)`，APK SHA-256 `800cf5f7e86c331dc75d0d40b723e2ad66f2bd2c7cc44bd78cf6eb3f3246d955`。

---

## 18. v1.16.3 图标与焦点一致性收尾（2026-10-21）

- 图标：播放器、视频详情、“我的”侧栏与内容徽标统一为 Material Round 矢量资源，不再依赖样式不一致的旧版 Android 系统图标。
- 更新页：增加更新状态图标，按钮补白色焦点描边、粉色按压态和非全大写文本。
- 遥控器回归：顶部焦点即切换、“我的”侧栏 → 内容网格、详情播放、播放器 ↑ 呼出功能栏、左右切换功能按钮及 OK 进入下级菜单均通过 1920×1080 AVD 验证。
- 视觉证据：`qa/v1.16.3/my-icons-unified.png`、`detail-icons-unified.png`、`player-icons-unified.png`、`settings-icons-unified.png`、`update-page-unified.png`。
- 构建：`clean assembleDebug lintDebug` 成功，Lint 0 errors / 37 warnings。
- 版本：`1.16.3 (30)`，APK SHA-256 `060fafa6cde907a6e46d596260d26a2ed655a571217f2c8c7d03c49ac3eab4c7`。
- Git：本轮未提交、未打 tag；用户验收后再决定是否提交/tag/发布。

---

## 19. v1.17.0 顶部导航与双栏设置（2026-10-21）

- 顶部导航：去除焦点态黑色外圈，改为品牌粉柔光与细白内描边；选中项保持品牌粉胶囊，视觉与 `design/concepts/11-settings.png` 对齐。
- 设置：右侧内容区改为两栏四分组（播放 / 弹幕 / 账户与数据 / 关于），十个项目按遥控器行列顺序排列；当前焦点使用白底、细深色内线与粉色柔光。
- 弹幕：新增字号、透明度和移动速度三档持久设置；`PlayerActivity` 按设置调整位图字号/Alpha、车道高度和横向运动时长。
- 数据动作：新增清除搜索历史、清除本地续播进度与应用信息入口；检测更新移动到“关于”分组。
- 遥控器验证：双栏左右切换、逐行上下移动、三个弹幕值循环切换、详情页进入播放器均已在 1920×1080 AVD 实测。
- 视觉证据：`qa/v1.17.0/nav-focus-pink-final.png`、`settings-two-column-final.png`、`settings-danmaku-values.png`、`compare-settings-final.png`。
- 构建：`clean assembleDebug lintDebug` 成功，Lint 0 errors / 38 warnings。
- 版本：`1.17.0 (31)`，APK SHA-256 `7f3e67ae63da37c1497f5a1faf0366acc9712c890645d1c99177cf0aebc80cf6`；本轮未提交、未打 tag。

---

## 20. v1.17.1 全局状态卡与浅色焦点收尾（2026-10-21）

- 状态系统：首页/分类/搜索、UP 主页、动态详情、视频详情统一加载、空内容和错误卡片；加载/无效参数不伪装成可重试状态，真正错误才允许按 OK 重试。
- 内容显示：动态详情加载时隐藏空白正文；视频详情成功前隐藏封面和操作按钮，已用真实视频回归成功态。
- 焦点视觉：“我的”侧栏、搜索词/分类与 UP 投稿卡去除黑色填充，统一浅粉底、品牌粉文字与柔光描边。
- 验收：1920×1080 AVD 截图与效果图对照，证据见 `qa/v1.17.1/`；三类详情空态并排见 `state-cards-consistency.png`。
- 构建：`clean assembleDebug lintDebug` 成功，Lint 0 errors / 38 warnings；APK 已安装到 AVD 验证。
- 包声明：`1.17.1 (32)`，仅 `android.intent.category.LAUNCHER`，无 Leanback launcher，触摸屏为非必需。
- APK SHA-256：`9c36c4d64ca1234e146c69825c2e03bba8c63a08704155f854c6483366a9c921`；本轮未提交、未打 tag。

---

## 21. v1.17.2 动态卡片画廊与收藏页（2026-10-21）

- 焦点动画：首页、直播、推荐、热门、排行、动态、收藏、搜索和“我的”内容卡统一为 170ms 缓动；移动焦点时新卡扩展、旧卡同步恢复，不再让第一张封面永久变大。
- 三列重排：首页及动态/收藏三列画廊按当前焦点动态分配宽度，焦点卡形成宽幅主视觉，其余两张保持正常比例；长标题按卡片实际宽度截断。
- 焦点视觉：移除内容卡深色粗圈，改为品牌粉柔光与细描边；上下换行时将新焦点行滚动到安全位置，避免放大卡片被屏幕底部裁切。
- 收藏页：使用真实账号头像、昵称、等级、硬币、关注/粉丝及默认收藏夹数量构建左侧资料面板，右侧显示真实收藏视频；未接入的收藏夹分类不做伪数据。
- 视觉验收：1920×1080 AVD 覆盖首页第一/第二卡及下一行、动态第一/第二卡、收藏第一/第二卡、搜索结果和“我的”内容区。源效果图与实现并排证据见 `qa/v1.17.2/compare-*-final.png`。
- 包声明：`1.17.2 (33)`，仍为普通手机 Launcher 声明，无 Leanback launcher，触摸屏标记为非必需。
- 构建：`clean assembleDebug lintDebug` 成功，Lint 0 errors / 38 warnings；APK 已重新复制到项目根目录。
- APK SHA-256：`fa8dab627757d363c569e0c48046be994fb1cd1374ed47441bb4f99b8bc02949`。
- Git：本轮未提交、未打 tag；用户验收后再决定是否提交/tag/发布。

---

## 22. v1.18.0 收藏夹目录与搜索分组（2026-10-21）

- 收藏目录：`BiliRepository` 新增真实收藏夹目录模型与接口，页面显示名称、服务端视频数，并按选中 `media_id` 分页读取视频。
- 遥控器：收藏页新增独立目录焦点区；从顶部向下进入目录，上下移动即切换内容，向右进入视频画廊，画廊首列向左返回目录。
- 搜索：全部结果按三张横版视频 + 一张番剧/影视海报 + 第二组紧凑卡构图；结果重新排序只作用于首屏展示，不改变真实目标地址或分类页数据。
- “我的”：四类内容画廊采用与首页一致的权重重排和 170ms 焦点动画。
- 证据：`qa/v1.18.0/favorites-folder-switched.png`、`search-all-grouped.png`、`my-weighted-second.png` 及三张同尺寸并排对照图。
- 包声明：`1.18.0 (34)`，继续使用普通手机 Launcher，无 Leanback launcher，触摸屏非必需。
- 构建：`clean assembleDebug lintDebug` 成功，Lint 0 errors / 38 warnings；APK 已覆盖安装并完成冷启动与崩溃日志检查。
- APK SHA-256：`bc6acf59bd533a3b9d3315ab7139c7c152acaa808369f68f2d010f034d998b2a`。
- Git：本轮未提交、未打 tag；用户验收后再决定是否提交/tag/发布。

---

## 23. v1.18.1 收藏筛选、搜索资料卡与账号头像（2026-10-22）

- 收藏控制：右上增加排序和内容筛选两个遥控器控件；左右选择控件，OK 在“最近收藏 / 最多播放”和“全部内容 / 可播放视频”之间切换，并重新加载当前收藏夹。
- 收藏侧栏：目录超过四项时随焦点滚动，标题显示当前位置/总数；“当前视图”会同步显示真实排序与筛选状态。空目录和接口错误使用独立提示，错误支持 OK 重试。
- 账号图片：头像与空间头图走独立单线程高优先级图片队列，避免被历史/收藏视频封面队列阻塞；头像不可用时显示品牌粉文字头像，不再出现近黑占位圆。
- 搜索：用户页显示头像、签名、粉丝和投稿数；主播页显示开播状态；直播间保持横版 LIVE 卡。真实关键词 `onepiece` 完成“全部 / 用户 / 主播 / 直播间空态”回归。
- 动态：真实登录流覆盖视频、图文和转发卡；头像横栏、类型徽标、发布时间与三列焦点重排均通过 1080p 截图检查。
- 视觉证据：`qa/v1.18.1/favorites-playable.png`、`my-avatar.png`、`search-all.png`、`search-users.png`、`search-live-users.png`、`search-live-rooms.png`、`dynamic-types.png`；同尺寸设计对照为 `compare-*.png`。
- 包声明：`1.18.1 (35)`，继续使用普通手机 Launcher，无 Leanback launcher，触摸屏非必需。
- 构建：`clean assembleDebug lintDebug` 成功，Lint 0 errors / 37 warnings；最终 APK 已覆盖安装并完成冷启动与崩溃日志检查。
- APK SHA-256：`9505e06c10cc8e3705844998479b97e6a896d01d9cb799b53e8426edd2f269c3`。
- Git：本轮未提交、未打 tag；用户验收后再决定是否提交/tag/发布。

---

## 24. v1.18.2 历史记录选集恢复（2026-10-29）

- 根因：历史记录只取了 `history.bvid`，PGC/番剧丢失 `history.epid`，多 P 丢失 `history.page`，点击后按普通视频/第 1 集打开。
- 修复：`BiliRepository.parseHistoryItems` 读取 `business/epid/page`；PGC 生成 `bangumi/play/ep<epid>`，普通多 P 生成 `video/<bvid>?p=<page>`；缺少 epid 的 PGC 旧记录退回 BV 地址，不误用 avid。
- 验证：历史页可正常打开；代码路径与 B 站 history/cursor 字段定义对齐（`epid` 仅用于剧集、`page` 仅用于稿件视频）。
- 版本：`1.18.2 (36)`，APK SHA-256 `102440ee9037a7c1bc2cee5dc82eb62636d9f768e8be23d5d2506d35a7ec6511`。

---

*本报告由当前代码与模拟器验收结果整理；如与代码不一致，以代码和 `CHANGELOG.md` 为准。*
