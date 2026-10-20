# 播放与鉴权子系统审查报告

> 审查对象：`app/src/main/java/com/ponyrun/bililivingroom/PlayerActivity.java`、`PlaybackRepository.java`、`BiliAuthRepository.java`、`LoginActivity.java`
> 对照材料：`docs/HANDOFF_REPORT.md`、`README.md`、`design-qa.md`、`qa/` 截图、`app/src/main/AndroidManifest.xml`、`app/build.gradle`、`gradle.properties`
> 版本基准：`1.4.0` / `versionCode 6`（Debug APK）
> 审查日期：2026-10-18
> 审查方式：静态源码通读 + 仓库文档/QA 产物比对。**本轮未执行构建、未安装运行、未抓包、未修改任何源代码**；只新增本文档。

## 证据等级约定

| 等级 | 含义 |
| --- | --- |
| **F（代码事实）** | 可直接从源码、清单或构建配置确认的确定事实 |
| **Q（仓库证据）** | 仓库内 `qa/` 截图或 `design-qa.md` / `HANDOFF_REPORT.md` 的陈述；本轮实际读取了正文列出的截图文件 |
| **H（假设/需实测）** | 根因假设，需用抓包、Logcat、真机或服务端响应才能确认，不能当作结论 |
| **N（未验证）** | 项目内没有可用证据，不能视为已通过 |

---

## 0. 结论摘要（可直接并入评审总报告）

- **普通视频链路已跑通**：BV/AV → `x/web-interface/view` → `x/player/playurl`（`platform=html5`）→ `durl` 渐进流 → `MediaPlayer + SurfaceView`；播放、10 秒 seek、两级功能菜单在代码与 QA 截图中都有对应实现/证据（F/Q）。
- **清晰度实际上限为 720P**：`platform=html5` 的请求方式让 `accept_quality` 只返回 720P/360P（QA 清晰度截图只有这两档）；1080P/4K 需要 DASH + 登录，当前链路不具备（F/Q）。
- **番剧/PGC 播放失败是确定现象，但不是单一根因**：`qa/` 截图显示番剧标题下 0:00「正在缓冲…」→「播放失败，请返回后重试」；代码层同时存在“只取 `durl[0]`、不用 `backup_url`、PGC 未传 `ep_id`、未检查 `result.code`、无超时/重试/日志”等缺陷；`MediaPlayer` 对 302/自定义 header/DASH/fMP4 的弱支持是放大因素（F/Q + H）。
- **HANDOFF 中的“CDN 403”没有代码级证据**：全工程没有 `Log` 调用，`onError` 丢弃 `what/extra`，接口层不记录 HTTP 状态码；“403”目前只能视为文档转述（F）。
- **直播播放没有任何验证证据**：仓库中没有直播播放截图；当前使用已废弃的 `room/v1/Room/playUrl`，质量档位是写死的“高清”，无直播弹幕、断流刷新或结束提示（F/N）。
- **鉴权链路代码完整，但安全与健壮性不足**：TV 扫码登录 + 4 秒轮询 + CookieManager 持久化已实现；但 `allowBackup=true` + Debug 构建使 Cookie 可被备份通道或 `run-as` 提取（P1；`run-as` 对 Debug APK 是确定的，备份通道可用性随系统版本而异），全仓无“退出登录”，`hasSession()` 只检查 `SESSDATA=` 字符串存在，APP_KEY/SECRET 硬编码（F）。
- **未发现 P0**：本次审查的四个文件中没有可确定的崩溃或数据破坏路径；`design-qa.md` 记录过的 P0 启动崩溃属于启动阶段 insets 问题（`design-qa.md:44`），不在这四个文件内。
- **Media3 迁移可行，但应先做一次低成本决策门**：先花 1.5–3 人日做接口层取证与修复，确认剩余失败是否仍由 `MediaPlayer` 能力导致；需要迁移时按 4 阶段排期，约 **8.5–15 人日**（中位 10–12），主要风险是 AndroidX 开关、APK 体积、硬件解码兼容、番剧会员/地区权限不可绕过以及许可合规（F/H）。

---

## 1. 现状结论

### 1.1 链路分层现状

| 链路环节 | 代码位置 | 现状 | 证据 |
| --- | --- | --- | --- |
| URL 路由（直播/番剧/普通） | `PlaybackRepository.java:54-65` | 按字符串 `contains` 分流，可用；非三类 URL 会一律按普通视频解析 | F |
| 普通视频解析 | `PlaybackRepository.java:97-105` | 可用；固定取 `pages[0].cid`，忽略 `?p=` 多 P | F/Q |
| 番剧元数据解析 | `PlaybackRepository.java:107-130` | 可用；`ep`/`ss` 均可取 season/episodes | F/Q（截图标题「· 分集」格式即 `:128` 的输出形式） |
| 番剧流解析/播放 | `PlaybackRepository.java:132-159` | **不可用/不稳定**：缓冲后播放失败 | Q（见 §7.2） |
| 直播解析 | `PlaybackRepository.java:161-171` | 实现存在，播放未验证 | F/N |
| 播放器内核 | `PlayerActivity.java:90-119` | 普通视频可用；错误处理、超时、恢复缺失 | Q/F |
| 控制层（seek/暂停/两级菜单） | `PlayerActivity.java:142-233` | 可用 | Q |
| 弹幕 | `PlaybackRepository.java:67-95`；`PlayerActivity.java:356-371` | VOD 可用；性能、轨道、直播弹幕缺失 | F/Q |
| 倍速 | `PlayerActivity.java:223-227` | 有实现；仓库截图显示菜单值仍为 1×，不足以独立证明生效 | F/Q |
| 清晰度切换 | `PlayerActivity.java:184-203` | 有实现；实测档位仅 720P/360P，切换重建播放器且存在竞态 | F/Q |
| 扫码登录 | `BiliAuthRepository.java:45-76`；`LoginActivity.java` | 代码完整；真实扫码成功在仓库证据中未见（`design-qa.md:51` 说明验证覆盖的是 logged-out captures） | F/Q |
| Cookie 持久化/共享 | `BiliAuthRepository.java:141-158` | 可用；无加密、无退出、无过期校验 | F |

### 1.2 已可用（有代码 + 仓库证据支撑）

1. 普通视频从 URL 到出画面：`view` 接口取 `cid`，`playurl` 取 `durl[0].url`，`MediaPlayer.prepareAsync()` 后出画；QA 截图 `qa/implementation-player.png` 可见真实视频帧与正确 letterbox，说明 `fitVideo()`（`PlayerActivity.java:121-133`）生效（F/Q）。
2. 遥控器控制层：左/右 10 秒 seek（`PlayerActivity.java:165-169`）、OK 暂停/恢复（`:217-221`）、上/下呼出功能栏、左/右切换三项、OK 进入二级菜单（`:142-203`）；QA 截图 `qa/implementation-player-horizontal*.png`、`qa/implementation-player-quality-option.png` 有对应状态（F/Q）。
3. 弹幕（VOD）：解析 `comment.bilibili.com/<cid>.xml`（`PlaybackRepository.java:67-95`），按播放位置自绘滚动（`PlayerActivity.java:356-371`）（F/Q）。
4. 倍速菜单：0.75×–2× 档位与菜单（`PlayerActivity.java:46,296-354,223-227`）；`design-qa.md` 声称已实测 1.25×，但 `qa/implementation-player-speed-applied-v2.png` 的菜单值仍显示“倍速 1×”，**建议补测后再作为验收证据**（F/Q）。
5. 番剧元数据能解析：`pgc/view/web/season` 取 episodes、`long_title`，标题拼接正确（`PlaybackRepository.java:112-129`）；截图中的标题格式与之一致（F/Q）。
6. 扫码登录流程代码完整：取 `auth_code`/`url` → 本地生成二维码 → 4 秒轮询 → 成功写入 CookieManager（`BiliAuthRepository.java:45-76,141-158`；`LoginActivity.java:23-38`）（F）。

### 1.3 已确认的缺陷（详见 §2）

- 番剧/PGC 播放会缓冲后失败（Q）；失败原因不可见、不可重试、不可降级（F）。
- 只支持渐进 `durl`，无 DASH；只取第一条 CDN，无 `backup_url` 回退（F）。
- 清晰度被 `platform=html5` 限制在 720P/360P（F/Q）。
- 直播链路缺少实测，且实现使用废弃接口、假清晰度、无直播弹幕（F/N）。
- 播放器不处理生命周期（无 `onPause`/`onStop`/音频焦点）、surface 重建丢进度、清晰度切换失败会让旧流继续播放但控制失效（F）。
- 设置页“默认弹幕/自动播放/优先高清”只在 MainActivity 内部生效，未传给播放器、也未持久化（F，跨模块）。
- 鉴权：Cookie 明文可备份、无退出登录、会话不校验、密钥硬编码（F）。

---

## 2. 代码级问题清单

> 每条包含：位置、问题、影响、严重级别（P0–P3）、修复建议、证据等级。
> 本轮未发现 P0；以下按 P1 → P3 排列。

### P1（影响核心场景或账号安全，建议下一迭代前处理）

#### PA-01（P1）番剧/PGC 只取 `durl[0]`，完全不使用备用 CDN
- **位置**：`PlaybackRepository.java:149-158`（`durls.getJSONObject(0).getString("url")`）
- **问题**：`durl` 数组只取第一条；`durl` 条目通常还带 `backup_url[]`（B 站惯例），代码从未读取；单一 URL 失败后没有第二次尝试。
- **影响**：单条 PGC 直链超时、限速、403 或过期即整体失败；这正是 HANDOFF P1 描述场景的放大因素。
- **修复建议**：构造候选列表（`durl` 各段 + 每条 `backup_url`），按顺序重试；失败时切换下一个 CDN；记录每次 attempt 的 URL 主机与结果。
- **证据等级**：F

#### PA-02（P1）PGC 播放请求身份不完整，`result.code` 被丢弃
- **位置**：`PlaybackRepository.java:107-130,143-148`
- **问题**：`resolveBangumi()` 已解析出 `epId`，但 `resolveArchive()` 只用 `bvid`/`avid` + `cid` 请求 `pgc/player/web/playurl`（`:143-144`），没有传 `ep_id`；`pgcRoot.result` 内部自己的 `code`/`error_info` 未校验（`:146-147`），非 0 时直接落到 `:150` 的 `no progressive stream`。
- **影响**：会员、地区、版权、试看、付费等限制全部被抹平成同一句“暂时无法取得播放地址”，无法判断原因；部分 PGC 内容用 `ep_id` 才能拿到正确授权/清晰度档位。
- **修复建议**：`resolveArchive` 增加 `epId` 参数并传给 PGC 接口；解析 `result.code`、`result.error_info`、`error_text`；把限制类错误（如 `-10403` 地区/版权、会员专享码等，具体码值以实测响应为准）映射为用户可读提示；同时保留原始 code 到日志。
- **证据等级**：F

#### PA-03（P1）播放器错误被吞掉，全工程无日志
- **位置**：`PlayerActivity.java:111-113`；全仓 `grep "Log\."` 无匹配（F）
- **问题**：`onError` 返回 `true` 并把所有 `what`/`extra` 丢弃，只显示“播放失败，请返回后重试”；接口层也不记录 HTTP 状态码或响应。
- **影响**：无法区分 403、解码失败、DNS/超时、URL 过期；HANDOFF 的“CDN 403”无法从现有产物复现；线上只能靠猜。
- **修复建议**：映射 `MEDIA_ERROR_UNKNOWN`/`MEDIA_ERROR_SERVER_DIED` 及 `extra` 码写 `Log.e`；对外按类别给“重试 / 切换清晰度 / 重新登录”三种操作；服务端接口同样记录 `code` 与 HTTP 状态。
- **证据等级**：F

#### PA-04（P1）无 prepare 超时、无缓冲事件、无重试上限
- **位置**：`PlayerActivity.java:104-108,114-115`
- **问题**：只在 `startPlayer()` 里把 `stateText` 设为“正在缓冲…”（`:114`），随后 `prepareAsync()`（`:115`）；没有超时 watchdog，没有 `setOnInfoListener`/`setOnBufferingUpdateListener`，没有失败重试。
- **影响**：“长缓冲”可无限持续，用户只能退出；与 QA 截图中 0:00「正在缓冲…」最终失败的现象一致。
- **修复建议**：增加 15–20 秒 prepare 超时与播放中卡顿检测；超时后按“降清晰度 → 换 backup_url → 报错”处理；监听 `MEDIA_INFO_BUFFERING_START/END` 更新状态；给出“重试”入口。
- **证据等级**：F

#### PA-05（P1）全链路只支持渐进流，请求参数与解析不匹配
- **位置**：`PlaybackRepository.java:136-137,143,149-158`
- **问题**：普通视频请求带 `fnval=1`（按 B 站 playurl 参数约定该位表示请求 DASH）却只解析 `durl`；PGC 回退又改为 `fnval=0`；全工程没有任何 `dash` 字段处理。`platform=html5` 时服务端通常仍回 `durl`，这是普通视频能播的原因，但该行为不应被当作契约。
- **影响**：一旦服务端按 `fnval=1` 返回 `dash`，必然抛 `no progressive stream`（`:150`）；只提供 DASH 的番剧/影视内容在当前实现下无法播放；清晰度上限被压到 720P。
- **修复建议**：短期显式设置与解析一致的参数（渐进流就用 `fnval=0`，DASH 就走 §4），对“只有 dash”的内容给出明确提示；长期按 §4 支持 DASH。
- **证据等级**：F（参数语义按公开 API 约定，实际响应需抓包确认 = H）

#### PA-06（P1）登录 Cookie 明文落盘且可被系统备份/调试提取
- **位置**：`AndroidManifest.xml:7`（`allowBackup="true"`）+ `BiliAuthRepository.java:141-158`（CookieManager）+ 当前交付为 Debug APK
- **问题**：Cookie 写在 WebView CookieManager（应用数据目录），没有额外加密；`allowBackup=true`，且当前交付是 debuggable 的 Debug APK。Debug 包下 `run-as` 读取应用数据目录是确定的（F）；备份通道（adb/云备份）是否可用取决于系统版本与备份配置（H）。B 站 `SESSDATA` 等同账号长期会话凭据。
- **影响**：一旦设备被调试连接、备份被导出或 APK/数据被拷走，账号会话可直接复用；这是该子系统最现实的账号安全风险。
- **修复建议**：`allowBackup=false`，或用 `android:dataExtractionRules`/`fullBackupContent` 明确排除 `app_webview`、`shared_prefs`；交付前改 release 构建；如需更高保障，再评估 Keystore 加密方案（WebView Cookie 无法直接加密，可用“加密保存 SESSDATA + 启动时回填 CookieManager”的折中方案）。
- **证据等级**：F

### P2（重要缺陷、体验或安全加固）

#### PA-07（P2）清晰度切换存在竞态，失败后旧流状态不一致
- **位置**：`PlayerActivity.java:72-88,184-203`
- **问题**：每次切清晰度都重新调用 `repository.resolve()`（2 线程池，`PlaybackRepository.java:51`），没有请求序号/取消；快速切换时旧响应可能后到并覆盖新选择。失败时只设置 `errorText`，旧 `MediaPlayer` 仍在播放，但 `prepared=false`，暂停/seek/菜单全部失效。
- **影响**：切档可能“点 720P 实际播 360P”；切档失败后出现“错误提示 + 旧流继续播放 + 无法操作”的僵死状态。
- **修复建议**：为每次 resolve 增加 generation/requestId，只接受最新请求；失败时恢复旧 `stream`/`prepared` 状态并提供“重试”；把切档做成状态机（旧流继续 → 新流就绪再切换 → 失败回滚）。
- **证据等级**：F

#### PA-08（P2）surface 重建后从 0 开始，进度丢失
- **位置**：`PlayerActivity.java:135-140,235-241,104-107`
- **问题**：`surfaceDestroyed()` 直接 `releasePlayer()`，没有先记录 `getCurrentPosition()`；`onPrepared` 只 seek 到 `resumePosition`（清晰度切换时才会更新）。Activity 只有 `onDestroy` 清理（`:243-245`），没有保存/恢复逻辑。
- **影响**：切后台、息屏、TV 多窗口、回到播放页时从 0 重播；番剧长视频体验明显受损。
- **修复建议**：release 前记录位置（字段 + `onSaveInstanceState`），surface 恢复后 seek 回去；与“继续观看”能力一并做。
- **证据等级**：F

#### PA-09（P2）没有 `onPause`/`onStop` 暂停，也没有音频焦点
- **位置**：`PlayerActivity.java:243-245`（类中只有 `onDestroy`）
- **问题**：播放状态只在 `onDestroy` 释放；Activity 失去前台时不会主动暂停，也没有 `AudioManager.requestAudioFocus`/`abandonAudioFocus`。
- **影响**：Home 键、其他应用播放、系统弹窗时可能继续出声或与其他音频混播；不同 ROM 对 SurfaceView 销毁行为不同，表现不确定。
- **修复建议**：`onPause` 暂停并记录位置，`onResume` 按用户意图恢复；申请/释放音频焦点；如需后台播放再引入 MediaSession/前台服务（当前不需要）。
- **证据等级**：F

#### PA-10（P2）仓库线程池从不关闭，重复进入会累积线程
- **位置**：`PlaybackRepository.java:51-52`；`PlayerActivity.java:28`
- **问题**：`Executors.newFixedThreadPool(2)` 没有 `shutdown()`/取消入口；每次进入 `PlayerActivity` 都 new 一个仓库。`BiliAuthRepository.java:43` 的单线程池同样不关闭。
- **影响**：反复进出播放器会累积空闲线程与未取消任务；Activity 销毁后仍可能回主线程调用 `overlay.invalidate()`（虽然通常不崩溃）。
- **修复建议**：仓库改为应用级单例或提供 `close()`；在 `onDestroy` 取消在途任务；回调前检查 Activity 存活/generation。
- **证据等级**：F

#### PA-11（P2）弹幕每帧全量扫描 + 30fps 全屏重绘
- **位置**：`PlayerActivity.java:261-269,356-371`
- **问题**：`onDraw` 里每帧遍历整个 `danmaku` 列表（只有可见数达到 36 才提前结束）；播放中固定 `postInvalidateDelayed(33)`（`:268`），即使弹幕关闭、控制层隐藏也全屏重绘；`drawDanmaku` 对每条文本做两次 `drawText`（描边+填充）。
- **影响**：热门视频弹幕可达数万条，低端 TV 盒子/盒芯片可能出现掉帧、发热；与自绘 UI 的 CPU 成本叠加。
- **修复建议**：按时间建立索引/游标（只扫描当前 8 秒窗口）、弹幕单独使用一层 View 或 `SurfaceView` 叠加、限制重绘频率；控制层隐藏且弹幕关闭时停止无效 invalidate。
- **证据等级**：F

#### PA-12（P2）设置页三项开关没有接入播放器，也未持久化
- **位置**：`MainActivity.java:73-75,329-332,547-549,591-593`；`PlayerActivity.java:38`
- **问题**：`settingDanmaku`/`settingAutoPlay`/`settingHighQuality` 只是 MainActivity 内部字段，切换后仅重绘；启动播放器时 Intent 只带 `EXTRA_URL`；`PlayerActivity` 里 `danmakuEnabled = true` 写死。设置没有 SharedPreferences/DataStore。
- **影响**：设置页“默认弹幕/自动播放/优先高清”对播放器完全无效，重启后恢复默认；README/HANDOFF 的“设置已做原生界面”会被理解为功能可用，存在文档与事实偏差。
- **修复建议**：抽一个最小 `PlayerPreferences`（SharedPreferences 即可），MainActivity 写入、PlayerActivity 读取并作为初始值；把“优先高清”映射到 qn 请求；明确“自动播放”的具体语义（进入播放页即播/续播）。
- **证据等级**：F（UI 部分由 `docs/review/ui-product.md` 展开）

#### PA-13（P2）清晰度被 `platform=html5` 限制在 720P/360P
- **位置**：`PlaybackRepository.java:134-137`
- **问题**：默认 `qn=80`（1080P），但请求固定 `platform=html5`、`fnval=1`；服务端对该组合只回 360P/720P 的 `accept_quality`。
- **影响**：大屏上最高只有 720P，与“客厅大屏客户端”的定位不符；用户看到“优先高清”但无法获得 1080P。
- **修复建议**：短期在清晰度菜单/设置中如实说明“当前最高 720P”；中期用 DASH + 登录获取 1080P，并按设备解码能力选择档位。
- **证据等级**：F（QA 截图 `implementation-player-speed-applied-v2.png` 中清晰度选项仅“高清 720P / 流畅 360P” = Q）

#### PA-14（P2）多 P 视频与时间戳被忽略
- **位置**：`PlaybackRepository.java:97-105`
- **问题**：`resolveVideo()` 固定 `pages.getJSONObject(0)`；不解析 URL 的 `?p=` 参数；也不解析 `?t=` 起始时间。
- **影响**：从 WebView/分享链接进入多 P 视频时永远播第 1 P；带时间戳的链接不生效。
- **修复建议**：解析 `p` 与 `t` 查询参数，`pages[p-1]` 取 cid，`resumePosition` 取 t；参数非法时回退并提示。
- **证据等级**：F

#### PA-15（P2）直播链路未验证且实现薄弱
- **位置**：`PlaybackRepository.java:161-171`
- **问题**：使用旧版 `room/v1/Room/playUrl?quality=4&platform=h5`（社区已普遍改用 `xlive/web-room/v2/index/getRoomPlayInfo`，前者可用性需实测）；只取 `durl[0]`；清晰度列表写死一个 `Quality(4,"高清")`；`roomInfo` 只用于标题；`cid=0`，没有直播弹幕；没有断流重连、URL 过期刷新、直播结束状态。仓库里没有任何直播播放截图（`qa/implementation-live.png` 是直播列表页，不是播放页）。
- **影响**：直播播放是否可用完全未知；即便今天可用，接口废弃、CDN 变更、断流后无恢复；用户从直播卡片进入可能直接看到“播放失败”。
- **修复建议**：先用真实直播间验证 `durl` 返回的是 HLS 还是 FLV（决定 MediaPlayer 能否播）；改用 `xlive/web-room/v2/index/getRoomPlayInfo` 或现有可用接口；实现断流重连与“直播已结束”提示；直播弹幕另行评估（WebSocket），暂可如实不做。
- **证据等级**：F/N

#### PA-16（P2）会话有效性未校验，缺少 `-101` 失效处理
- **位置**：`BiliAuthRepository.java:78-81`
- **问题**：`hasSession()` 只判断 Cookie 字符串里是否含 `SESSDATA=`，不检查过期/服务端有效性；代码里没有对 B 站 `code=-101`（未登录）或 `-352`（风控）的统一处理。
- **影响**：Cookie 过期后 UI 仍显示“已登录”，动态/收藏等接口失败只会提示“请确认账号已登录”，用户不知道该重新扫码；TV 长期待机场景很常见。
- **修复建议**：启动/`onResume` 调 `x/web-interface/nav` 校验登录态；遇 `-101` 清理 Cookie 并引导重新登录；统一错误模型（与 `BiliRepository` 一并做）。
- **证据等级**：F

#### PA-17（P2）没有退出登录/清除 Cookie 的入口
- **位置**：`BiliAuthRepository.java:141-158`（只写 Cookie）；全仓无 `removeAllCookies`/`removeSessionCookies`/“退出登录”（F）
- **问题**：登录页只有“二维码重新登录”，账号页没有“退出登录”；Cookie 一旦写入只能靠清应用数据移除。
- **影响**：换号、共享设备、出借设备时无法安全退出；隐私与合规风险。
- **修复建议**：账号页加“退出登录”：`CookieManager.removeAllCookies()` + `flush()`，清空用户相关缓存/内存状态，回到未登录 UI。
- **证据等级**：F

#### PA-18（P2）登录轮询状态机有缺陷，可能并发多轮询/卡死
- **位置**：`LoginActivity.java:23-38,57-69,106-107`
- **问题**：① 每次 `requestQr()` 成功都 `postDelayed(poll, 4000)`，重复按确认（`:106-107`）会生成多个二维码和多个轮询链；② `onPause` 只移除当前 `poll` Runnable，但 `requestQr` 的回调在 `!active` 时直接 return（`:62`），回到前台后没有重新请求，可能永久停在“正在生成二维码…”；③ 网络失败后固定 4 秒重试，无退避。
- **影响**：登录页可能重复请求、被服务端限流；用户回到前台看到卡死状态只能退出重进。
- **修复建议**：单一状态机：generation 标记、同一时刻只允许一个 QR/轮询链、`requestQr` 前取消旧链；`onResume` 时若 `authCode==null` 或有未完成状态则重新发起；失败重试加指数退避；对 EXPIRED 明确“已过期，重新生成”。
- **证据等级**：F

#### PA-19（P2）全部 Cookie 发往媒体 CDN，范围过宽
- **位置**：`PlaybackRepository.java:192-193`；`BiliAuthRepository.java:83-86`
- **问题**：`cookieHeader()` 返回 CookieManager 里 `api.bilibili.com` 匹配到的全部 Cookie（代码确定；实际集合通常含 `SESSDATA`、`bili_jct`（CSRF token）、`buvid3`、`DedeUserID` 等，具体需抓包确认），`open()` 把它整包发给 `comment.bilibili.com` 以及后续 `setDataSource` 的媒体 CDN 主机（`:101-103`）。
- **影响**：CSRF token 等与播放无关的凭据暴露给 CDN/第三方域名，扩大泄露面；不符合最小权限原则。
- **修复建议**：媒体请求只带必要字段（播放通常只需 `SESSDATA` + 必要时 `buvid3`）；`bili_jct` 绝不外发；按 host 白名单构造 Cookie header。
- **证据等级**：F

#### PA-20（P2）APP_KEY / APP_SECRET 硬编码
- **位置**：`BiliAuthRepository.java:41-42`（签名逻辑 `:94-113`）
- **问题**：TV 登录签名用的 appkey/secret 直接写在源码中，可被反编译提取；该 appkey 是公开客户端通用凭据，secret 用于签名，滥用可能被服务端限流/封禁。
- **影响**：无法在客户端真正保密；APK 公开分发后凭据可被复用，服务端策略变化时可能整体失效。
- **修复建议**：承认它是公开凭据而非机密，写入 `NOTICE.md`/安全说明；用 `BuildConfig` 字段集中管理并准备轮换；不要把它当作用户数据保护手段（用户数据保护见 PA-06/PA-17）。
- **证据等级**：F

#### PA-21（P2）接口层不区分 HTTP 状态 / 风控 HTML
- **位置**：`PlaybackRepository.java:173-183`
- **问题**：`getJson()` 只读取 `getInputStream()`，不检查 `getResponseCode()`；B 站风控返回 HTML 时 `JSONObject` 解析抛异常，统一被 `resolve()` 的 catch 吞成“暂时无法取得播放地址”（`:61-63`）。
- **影响**：403/412/风控/服务端 5xx 全部表现为网络错误，无法针对性重试或提示；与 PA-03 叠加导致完全不可观测。
- **修复建议**：检查状态码、Content-Type；把 HTML 响应识别为风控；区分可重试（5xx/超时）与不可重试（403/风控）；统一错误模型并与 `BiliRepository` 对齐。
- **证据等级**：F

### P3（改进项、健壮性与体验）

| 编号 | 位置 | 问题 | 影响 | 修复建议 | 证据 |
| --- | --- | --- | --- | --- | --- |
| PA-22 | `PlaybackRepository.java:57-59`；`MainActivity.java:591-593` | 用 `contains` 判断媒体类型；`https://live.bilibili.com/` 首页、非三类链接会误入播放器 | 进入播放器后报“缺少 room/id”，体验差 | 用 URL host/path 正则严格解析，无法识别时走 WebView | F |
| PA-23 | `PlayerActivity.java:356-371` | 弹幕轨道固定 `i % 7`（只有 7 条轨道），固定 8000ms 穿越，与文本长度无关；无字号/透明度/屏蔽词/类型开关 | 弹幕密集时重叠、出屏时间不一致 | 轨道分配 + 长度计算时长 + 基本设置项 | F |
| PA-24 | `PlayerActivity.java:278-294,373-377` | `drawControls` 每帧 `new LinearGradient`；`label()` 每次 `Typeface.create` | 每帧对象分配，低端盒子 GC 抖动 | Paint/Shader/Typeface 复用 | F |
| PA-25 | `PlayerActivity.java:142-169` | 进度条不可拖动，无长按快进，未处理 `KEYCODE_MEDIA_PLAY_PAUSE`/`MEDIA_FAST_FORWARD` 等媒体键 | 大屏遥控器常用操作缺失 | 增加媒体键映射；进度条可聚焦拖动 | F |
| PA-26 | `PlayerActivity.java:111-118` | 错误态只有文案，没有“重试”操作 | 用户必须退出重进 | 错误卡增加“重试/换清晰度” | F |
| PA-27 | `PlayerActivity.java`（无 `onSaveInstanceState`） | 进程被杀/重建后丢失播放位置、清晰度、弹幕开关、倍速 | 恢复体验差 | 保存/恢复播放状态 | F |
| PA-28 | `PlayerActivity.java:58`；`PlayerActivity.java:256-258` | `setSystemUiVisibility(5894)`、`getDrawable` 等为过时 API；项目已通过 Lint 但不代表未来可用 | 未来 targetSdk 升级风险 | 分期迁移到 `WindowInsetsController`/`ResourcesCompat` | F |
| PA-29 | `PlaybackRepository.java:67-95`；`PlayerActivity.java:83-85` | 弹幕下载/解析异常被静默忽略（`:89`）；XML 无大小上限；失败后 `danmaku` 为空且不会重试 | 大弹幕视频可能 OOM/无提示；失败不恢复 | 加大小/条数上限、失败提示与重试、流式解析限额 | F |
| PA-30 | `LoginActivity.java:84-94,106-109` | “重新生成二维码”按钮只是绘制，整个 View 任意位置按确认都会重新生成；无焦点态 | 与自绘 UI 的交互约定不一致，误触重复生成 | 可聚焦按钮/焦点态，或改为明确的按键提示 | F |
| PA-31 | `BrowserActivity.java:27-36` | WebView 开启 JS、DOM Storage、第三方 Cookie；`shouldOverrideUrlLoading` 只拦截非 http(s)，http(s) 外链会在同一容器打开 | 与登录 Cookie 共享容器的页面若跳转到外部站点，扩大攻击/泄露面 | 第三方 Cookie 默认关闭；外链加域名白名单；WebView 与登录态隔离评估 | F（安全加固，非确定性漏洞） |
| PA-32 | `LoginActivity.java:31`；`MainActivity.java:591` | `setResult(RESULT_OK)` 但调用方用 `startActivity` 而非 `startActivityForResult` | 死代码，容易被误读为有结果回调 | 删除或改为 `startActivityForResult` 并处理 | F |

---

## 3. PGC / DASH 根因判断与迁移决策

### 3.1 能确认的事实

1. 番剧元数据链路正常，能拿到 `title`、`bvid`、`aid`、`cid`（`PlaybackRepository.java:112-129`）。
2. 番剧标题按「season · 分集」拼接（`:128`）；`qa/implementation-search-bangumi-playback-v1.4*.png` 的画面标题正是这种格式，说明截图对应的是番剧解析路径（Q）。
3. 该路径下画面停在 0:00「正在缓冲…」，随后出现 `PlayerActivity.java:112` 的原文“播放失败，请返回后重试”（Q，截图 `-late.png`）。
4. 流解析只接受 `durl`（`:149-158`），从不读取 `dash`；PGC 分支只在普通 playurl 抛异常时才触发（`:139-148`），且用 `fnval=0`。
5. PGC 请求不带 `ep_id`，也不校验 `result.code`（`:143-147`）。
6. 没有日志、没有超时、没有备用 URL、没有重试（F）。
7. HANDOFF 声称“部分 PGC 直链…返回 CDN 403”，但代码里没有任何记录 403 的位置，仓库也没有抓包/Logcat 证据（F）。

### 3.2 根因分层（按可验证性）

| 层 | 假设 | 依据 | 如何验证 | 若成立的处理 |
| --- | --- | --- | --- | --- |
| A. 接口/解析缺陷（高置信） | 只取 `durl[0]`、无 `backup_url`、PGC 未传 `ep_id`、未检查 `result.code`、无超时重试日志 | F（代码） | 对同一 ep 抓取 `pgc/player/web/playurl` 原始响应：看 `result.code`/`error_info`、`durl`/`dash`、`backup_url`，再用候选 URL 逐个 curl | 先修代码，可能直接解决或缩小问题（1.5–3 人日） |
| B. 播放器能力（高概率） | MediaPlayer 对 DASH/fMP4 不支持；302 到 CDN 后自定义 header（Referer/Cookie/Origin）可能丢失；无 Range/超时控制 | F（能力边界）+ H（行为） | Logcat 过滤 `MediaPlayer`/`NuPlayer`，抓 `what/extra`；用抓包看 302 后请求头是否仍带 Referer/Cookie/UA；对比浏览器播放同一 URL | 迁移 Media3（§4），或 MediaPlayer 下改用 `setDataSource` 可控制的 URL/减少跳转 |
| C. 内容/权限（服务端，高概率之一） | 会员、地区、版权、付费/电影券限制；免费集与大会员集可用性不同 | H | 用真实账号在网页端/手机端播同一 `ep`；对比登录/未登录、大会员/非大会员响应 | 不能绕过：做错误分类与引导（登录/开通会员/地区提示），验收时排除该类样本 |
| D. 网络/CDN | 单 CDN 故障、URL 过期、限速、IPv6 回退 | H | 抓包看状态码/主机/DNS；复测 2–3 次；换网络 | backup_url + 多 CDN + 重试（PA-01/PA-04） |

**判断**：现有证据不足以把番剧失败单一归因于 `MediaPlayer`。更准确的表述是：**接口解析缺陷让失败不可恢复、不可诊断；播放器能力决定了一部分 DASH/重定向内容无法播放；内容权限决定了另一些内容永远无法播放**。因此“直接换 ExoPlayer”既可能解决 B 类，也可能完全不解决 A/C 类。

### 3.3 “长缓冲 / 403”的机理假设（需实测）

- **长缓冲**：若 PGC 直链是 `moov` 在文件尾的 MP4 或分片 fMP4，`MediaPlayer` 需要先下载大量数据才能 `onPrepared`；长视频（番剧 24 分钟）比短视频明显，这与“普通视频正常、番剧长缓冲”的现象吻合（H）。
- **403**：常见于 302 跳转到 CDN 后自定义 header 丢失、URL 过期（带 `deadline`/签名）、CDN 校验 Referer/UA/Cookie、或会员档位不匹配；也不排除范围请求被拒（H）。
- **验证顺序建议**：① 抓 PGC playurl 响应（判断 A/C 层）；② 对拿到的 URL 做 `curl -I` + 带 Referer/Cookie 的 `curl -r 0-65535`（判断 403/Range）；③ 真机 Logcat 看 MediaPlayer `what/extra`（判断 B 层）；④ 用 ExoPlayer demo/小工程播同一 URL（判断是否需要迁移）。四步都不需要改业务代码。

### 3.4 建议的决策门（Gate）

1. **先做阶段 0**（§4.3，1.5–3 人日）：修 PGC 请求/解析/`backup_url`/错误/日志/超时，并抓包取证。
2. 若阶段 0 后番剧在测试样本上可稳定播放 → 先不迁移，把 Media3 作为 P3 技术债（可接受）。
3. 若失败仍集中在“返回 dash 或 302/CDN 403” → 启动 Media3 迁移，按 §4 排期。
4. 无论是否迁移，都应在验收中区分“可播放样本 / 权限受限样本 / 网络失败样本”，不能只用“成功解析到 URL”作为通过标准（HANDOFF §9 已提醒）。

---

## 4. Media3 / ExoPlayer 迁移方案

### 4.1 目标架构（建议）

保持现有自绘 UI 与遥控器状态机不变，只替换播放内核，降低回归面：

```
PlayerActivity（保留：按键、Overlay、弹幕、菜单、进度）
        │
PlayerEngine（新接口）
   ├── MediaPlayerEngine（现有实现，作为回退/开关）
   └── Media3Engine（新）
        │
PlaybackRepository（扩展：Stream 增加 format / dashVideo / dashAudio / backupUrls / headers）
```

- `PlayerEngine` 建议方法：`prepare(Stream, Map<String,String> headers, int startPositionMs)`、`play/pause/seekTo/setSpeed/release`、`setListener(Listener)`（prepared/ended/error/buffering/timeUpdate）。
- `PlaybackRepository.Stream` 扩展：`format = PROGRESSIVE | DASH | HLS`；`List<StreamCandidate>`（主 URL + `backup_url` + 音/视频分离 URL）；headers 集中构造，避免 UI 与网络层各拼一份。
- 错误模型统一：`ErrorCategory { NETWORK, HTTP_STATUS(403/404/5xx), RISK_CONTROL, AUTH(-101), MEMBER(-10403), DECODE, TIMEOUT }`，UI 按类别给动作。

### 4.2 工程改动

1. **打开 AndroidX**：`gradle.properties:2` 当前 `android.useAndroidX=false`，Media3 必须为 `true`。项目没有 support 库依赖，风险低；`android.nonTransitiveRClass=true` 可保留，不需要 Jetifier。
2. **依赖**（版本以构建时最新稳定 1.x 为准，示例）：
   ```gradle
   implementation 'androidx.media3:media3-exoplayer:1.4.1'
   implementation 'androidx.media3:media3-exoplayer-dash:1.4.1' // 番剧 DASH
   implementation 'androidx.media3:media3-exoplayer-hls:1.4.1'  // 直播 HLS（若返回 m3u8）
   // 不需要 media3-ui：继续用现有 SurfaceView + 自绘 Overlay，节省体积
   ```
3. **渲染**：`player.setVideoSurfaceView(surface)`，保留 `fitVideo()`；`onVideoSizeChanged` 改由 `Player.Listener.onVideoSizeChanged` 驱动。
4. **网络**：`DefaultHttpDataSource.Factory` 设置 `setDefaultRequestProperties(headers)`、`setAllowCrossProtocolRedirects(true)`、连接/读取超时；这是相对 MediaPlayer 的关键收益——**重定向后仍可控制 header**。
5. **DASH 组装**（B 站 playurl 返回的是 `dash.video[]`/`dash.audio[]` 的 baseUrl 列表，不是标准 MPD 文件）：
   - 首选：`MergingMediaSource(new ProgressiveMediaSource(videoUrl), new ProgressiveMediaSource(audioUrl))`（B 站 DASH 单文件 + `SegmentBase`，服务端支持 Range 时可行，需实测）。
   - 备选：由 `segment_base`/`SegmentList` 生成最小 MPD，交给 `DashMediaSource`；或实现自定义 `MediaSource`。
   - 音视频选择：`dash.video[]` 按 `id`/`width`/`height`/`codecs` 与设备能力匹配（低端盒子优先 `avc1`，避免 HEVC/AV1），`dash.audio[]` 取最高码率或默认。
   - 清晰度菜单改绑 `DefaultTrackSelector` 参数（DASH 可无缝切轨，不必重建播放器）。
6. **HLS/直播**：`HlsMediaSource`；若接口返回 FLV（`MediaPlayer`/ExoPlayer 都不支持 HTTP-FLV），需要单独方案（转 m3u8 或保留 WebView 兜底），这是直播迁移的最大不确定点。
7. **错误映射**：`ExoPlaybackException` + `HttpDataSource.InvalidResponseCodeException.responseCode`；403 → 版权/会员/地区提示；`ERROR_CODE_DECODING_FAILED` → 换档/换编码；超时 → 重试。
8. **保留回退**：用 `BuildConfig`/设置项在 MediaPlayer 与 Media3 间切换至少一个版本，降低 TV 盒子兼容风险。

### 4.3 工作量估算（1 名熟悉 Android 的开发者）

| 阶段 | 内容 | 乐观（人日） | 保守（人日） |
| --- | --- | --- | --- |
| 0 | 抓包/Logcat 取证 + 接口层修复（backup_url、ep_id、result.code、超时/重试/日志/文案） | 1.5 | 3 |
| 1 | 打开 AndroidX + 引入 Media3 + 普通视频/基础控制（不含 DASH） | 2.5 | 4 |
| 2 | 番剧 DASH 解析/组装/清晰度/错误映射 | 2 | 3.5 |
| 3 | 多 CDN 回退/重试、直播 HLS、生命周期与进度保存 | 1 | 2 |
| 4 | 三类内容回归（遥控器、弹幕、倍速、清晰度、老设备）与文档 | 1.5 | 2.5 |
| **合计** |  | **8.5** | **15** |

建议按 **10–12 人日** 排期；其中阶段 0 是决策门，先做可避免“迁移完仍失败”的返工。若只做“普通视频 720P → 1080P DASH”而不碰直播，阶段 3 可压缩到 0.5–1 人日。

### 4.4 风险与缓解

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| AndroidX 开关 | `android.useAndroidX=false` → `true`；AGP 8.7.3 与项目无 support 库，风险低 | 单独一次提交 + 全量构建/回归；确认 `nonTransitiveRClass` 不变 |
| APK 体积 | ExoPlayer + dash + hls 预计增加约 1.5–2.5MB（当前 debug APK 约 1.78MB，`BiliLivingRoom-debug.apk`） | 不开 `media3-ui`；release 开启 `minifyEnabled`/`shrinkResources` 后回归 |
| 硬件解码兼容 | 廉价 TV 盒子对 HEVC/AV1/4K、DASH 分轨支持差异大 | TrackSelector 限制 avc1/分辨率；保留 MediaPlayer 回退开关；多设备矩阵测试 |
| 番剧权限 | 会员/地区/版权/电影券限制无法通过换播放器绕过 | 错误分类 + 登录/会员引导；验收样本分级 |
| 直播返回 FLV | ExoPlayer 不支持 HTTP-FLV | 先确认接口返回格式；不支持则用 HLS 或保留 WebView 兜底并如实标注 |
| B 站接口变动 | playurl 参数/字段、风控策略可能变化 | 解析层隔离 + 日志/错误码可观测 + 抓包回归脚本 |
| 许可合规 | Media3 为 Apache-2.0，项目声明 GPL-2.0；已有 Apache-2.0 的 zxing 依赖，属同类既存问题 | 在 `NOTICE.md` 补充依赖许可说明；必要时请法务确认 GPL-2.0 与 Apache-2.0 的组合风险 |
| 回归成本 | 自绘 UI 无自动化测试，遥控器状态机路径多 | 先补播放器状态机的关键路径清单；人工回归绑定固定测试片单 |

### 4.5 不建议的方案

- **只换播放器、不改接口/解析**：无法修复 A/C 类根因，可能白做。
- **引入 ijkplayer/其他老播放器**：维护停滞、包体大、解码兼容问题更多。
- **把番剧整体改成 WebView 播放**：与“原生全屏播放”目标冲突、遥控器体验差；可作为临时兜底开关，但不应作为主路径。
- **在客户端做 DRM/破解会员限制**：合规与法律风险，报告不建议。

---

## 5. 番剧 / 直播 / 普通视频三类目标差异与缺口

| 维度 | 普通视频 | 番剧/影视（PGC） | 直播 |
| --- | --- | --- | --- |
| 识别 | `pageUrl.contains("/video/")`（`PlaybackRepository.java:59`） | `contains("/bangumi/play/")`（`:58`） | `contains("live.bilibili.com/")`（`:57`） |
| 元数据接口 | `x/web-interface/view`（`:102`） | `pgc/view/web/season?ep_id/season_id`（`:112`） | `room/v1/Room/get_info`（`:165`） |
| 流接口 | `x/player/playurl`（`fnval=1`、`platform=html5`、`fourk=1`）（`:136-137`） | 先普通 playurl，异常后 `pgc/player/web/playurl`（`fnval=0`，不带 `ep_id`）（`:139-148`） | `room/v1/Room/playUrl?quality=4&platform=h5`（`:166`） |
| 流形态 | `durl[0]` 渐进 MP4 | `durl[0]`（权限受限；实际常为 DASH） | `durl[0]`（h5 通常 m3u8，也可能是 FLV，**需实测**） |
| 清晰度 | `accept_quality`/`accept_description`，实测仅 360P/720P | 同左，且受会员/地区影响 | 写死单一“高清”，无真实档位 |
| 弹幕 | XML（`cid > 0`） | XML（`cid > 0`） | 无（`cid=0`，未接 WebSocket） |
| 鉴权依赖 | 低（未登录可 720P） | 高（会员/地区/版权/付费） | 低（部分房间需登录） |
| 失败表现 | 通用「播放失败」 | 缓冲后失败（QA 截图） | 未验证；断流/过期无提示 |
| 续播/进度 | 无；surface 重建从 0 | 无；不支持从历史进度续播 | N/A |
| 主要缺口 | 720P 上限、多 P/时间戳、DASH | DASH、`ep_id`、错误码、多 CDN、会员引导、下一集 | 接口废弃、格式未知、无弹幕、无重连、未验证 |

补充缺口：

- **番剧**：播放器内没有分集列表/“下一集”；追番列表点入固定用 `ss` 取第一集（`BiliRepository.java:356`），不能定位到用户当前进度；没有“试看/会员”状态提示。
- **直播**：进入未开播/不存在房间时解析直接抛错，统一显示“暂时无法取得播放地址”；没有“主播未开播”状态。
- **普通视频**：从搜索/首页进入时 URL 由 `BiliRepository` 规范化（BV/AV），多 P 与 `t=` 信息在这条链路中本来就不存在——不是当前主流程的问题，但 WebView 兜底链接/外部分享会触发 PA-14。

---

## 6. 鉴权与 Cookie 安全

### 6.1 登录链路代码事实

1. **二维码申请**：`POST https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code`，参数经 appkey + ts + 设备字段排序后 MD5 签名（`BiliAuthRepository.java:45-56,94-113`），取 `auth_code` 与 `url`，本地用 zxing 生成 540px 二维码（`:51,160-169`）。
2. **轮询**：同一接口族 `qrcode/poll`（`:58-76`）；`code=0` 成功并保存 Cookie；`86038` 过期；`86039` 等待；其他失败。`LoginActivity` 每 4 秒轮询一次（`:35,67`）。
3. **Cookie 持久化**：把 `cookie_info.domains × cookies` 逐条 `CookieManager.setCookie("https://"+domain, "name=value; Domain=.domain; Path=/; Secure")`，最后 `flush()`（`BiliAuthRepository.java:141-158`）。
4. **共享**：所有 HTTP 请求（`PlaybackRepository.java:192-193`、`BiliRepository.java:453,474`）与 WebView（`BrowserActivity.java:35-36`）共用同一 CookieManager。
5. **会话判断**：`hasSession()` 只检查 `getCookie("https://api.bilibili.com")` 是否包含 `SESSDATA=`（`:78-81`）；`userMid()` 用正则从 `DedeUserID` 取 UID（`:88-92`）。
6. **没有**：退出登录/清 Cookie、Cookie 过期时间校验、服务端会话校验、`refresh_token` 保存与刷新、登录态失效（`-101`）处理（全仓 grep 无对应实现）。

### 6.2 安全问题清单（与 §2 对应）

| 级别 | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| P1 | Cookie 明文落盘 + `allowBackup=true` + Debug 构建，可被备份/调试提取 | `AndroidManifest.xml:7`、`BiliAuthRepository.java:141-158` | 关闭备份/排除目录；发布 release；评估 Keystore 加密回填 |
| P2 | 无退出登录/清 Cookie 入口 | 全仓无 `removeAllCookies` | 账号页加退出登录并清理内存态 |
| P2 | 会话只检查字符串存在，不校验有效性 | `BiliAuthRepository.java:78-81` | 启动/onResume 调 `nav` 校验；`-101` 清 Cookie 并引导登录 |
| P2 | 全部 Cookie 发往 CDN（含 `bili_jct`） | `PlaybackRepository.java:192-193`、`BiliAuthRepository.java:83-86` | 按 host 构造最小 Cookie header；CSRF token 不外发 |
| P2 | APP_KEY/APP_SECRET 硬编码 | `BiliAuthRepository.java:41-42` | 声明为公开凭据、集中管理、准备轮换；不作为用户数据保护 |
| P3 | 登录 Cookie 属性被丢弃（`Expires`/`HttpOnly`/`SameSite`），硬编码 `Secure`；会话 Cookie 冷启动是否保留依赖 WebView 行为 | `BiliAuthRepository.java:153-154` | 保留服务端返回属性；冷启动实测“重启后是否仍登录” |
| P3 | 后台线程访问 CookieManager（接口 `open()` 在 executor 中调 `cookieHeader()`） | `PlaybackRepository.java:192`、`BiliRepository.java:453,474` | 统一主线程读取/缓存 cookie header；至少确认各 ROM 无异常（H） |
| P3 | WebView 开启第三方 Cookie 且 http(s) 外链不拦截 | `BrowserActivity.java:27-36` | 第三方 Cookie 关闭、域名白名单、登录态与浏览容器隔离评估 |

### 6.3 建议的优先修复

1. **P1（发布前必须）**：`allowBackup=false` / 备份规则排除 `app_webview`；取消把 Debug APK 作为对外交付物；确认 Cookie 冷启动保留行为。
2. **P2（下一迭代）**：退出登录；`nav` 校验 + `-101` 处理；媒体请求最小 Cookie；统一错误模型。
3. **P3（加固）**：Cookie 属性保留、Thread 约束、WebView 策略；补 `NOTICE.md` 的凭据说明与依赖许可。

---

## 7. 与文档的一致性、证据核查

### 7.1 文档陈述 vs 代码事实

| 文档 | 陈述 | 核查结果 |
| --- | --- | --- |
| `HANDOFF_REPORT.md:48` | “普通视频和直播使用原生全屏 `MediaPlayer + SurfaceView`” | 代码属实（F）；但**直播播放没有验证证据**（N），不能视为已实现验收 |
| `HANDOFF_REPORT.md:52-53` | “已实现 XML 弹幕、0.75×–2× 倍速和接口返回的清晰度列表” | 弹幕/倍速/清晰度菜单有实现（F）；清晰度实测仅 720P/360P（Q）；倍速生效证据不足（见 7.2） |
| `HANDOFF_REPORT.md:143-146` | “部分 PGC 直链…长时缓冲或返回 CDN 403” | 现象有 QA 截图佐证（缓冲→失败）（Q）；“403”无代码/抓包证据（H）；“已传精确 Referer + Origin”属实（`PlayerActivity.java:98-99`，F） |
| `HANDOFF_REPORT.md:150-152` | 硬编码 APP_KEY/SECRET、Cookie 未加密 | 属实（F）；本案进一步补充：`allowBackup=true` + Debug 构建使风险更高（P1） |
| `HANDOFF_REPORT.md:186` | “用真实账号重新测试动态、收藏、历史和追番” | 仍未完成；`design-qa.md:51` 也承认依赖真实账号 |
| `design-qa.md:47` 第 4 条 | “Emulator evidence confirms real video playback…and a live 1.25× speed change” | 播放画面有截图（Q）；`qa/implementation-player-speed-applied-v2.png` 菜单显示“倍速 1×”，**不足以单独证明 1.25× 生效**，建议补一张显示 1.25× 的截图 |
| `design-qa.md:82` | “future Media3 migration could add adaptive DASH…” | 与本文建议一致；但“current native player intentionally uses platform media stack to keep the APK small”是取舍说明，不代表能播 DASH |
| `design-qa.md:56,85` “Findings: No actionable P0, P1, or P2” + “final result: passed” | 与 HANDOFF 的 P1 番剧问题冲突 | 应把番剧/PGC 播放从“通过”范围中明确排除（HANDOFF §9 已这样提醒） |
| `README.md:13-14` | “视频与直播使用原生全屏播放器…支持清晰度档位” | 直播未验证、清晰度上限 720P，应补充限定语 |
| `NOTICE.md` | “Android WebView playback bridge” | 与现状不符（早已改为原生播放），属过时描述，建议修正 |

### 7.2 本轮实际读取的 QA 截图（只读，未修改）

| 文件 | 画面内容（本轮观察） | 能证明什么 |
| --- | --- | --- |
| `qa/implementation-player.png` | 竖屏视频真实画面、左右黑边（letterbox） | 普通视频可出画；`fitVideo()` 生效 |
| `qa/implementation-player-speed-applied-v2.png` | 实际播放画面 + 清晰度子菜单“高清 720P / 流畅 360P” + 右侧菜单“倍速 1×” | 清晰度只有 720P/360P；**不能**证明 1.25× 已应用 |
| `qa/implementation-search-bangumi-playback-v1.4.png` | 番剧标题 + 0:00 + “正在缓冲…” | 番剧路径进入播放器后卡在缓冲 |
| `qa/implementation-search-bangumi-playback-v1.4-fixed.png` | 与上一张 **SHA-256 完全相同**（`d6719f…`） | “fixed”没有任何视觉变化证据 |
| `qa/implementation-search-bangumi-playback-v1.4-late.png` | 番剧标题 + 0:00 + “播放失败，请返回后重试” | 番剧播放失败（Q） |
| `qa/implementation-search-bangumi-playback-v1.4-fixed-late.png` | 与上一张 **SHA-256 完全相同**（`568404…`） | “fixed-late”同样没有新证据 |
| `qa/implementation-live.png` | 直播**列表页**（直播广场卡片） | 只证明直播列表，不证明直播播放 |

> `qa/implementation-login.png` 本轮未逐像素复核；`design-qa.md:51` 说明其覆盖的是 logged-out 捕获，只能证明登录 UI 存在，不能证明扫码成功后的会话。

> 证据提示：`implementation-search-bangumi-playback-v1.4-fixed*.png` 与旧图字节级重复，说明“修复后复测”要么没有真正重拍，要么结果没有任何变化。建议不要用这两个文件宣称“已修复”；如需保留，请在文档中标注其实际结论（失败）。

### 7.3 建议补充的验证（尚未执行，仅清单）

- 对同一个番剧 `ep`：抓 `pgc/player/web/playurl` 原始响应，确认 `result.code`、`error_info`、`durl`/`dash`、`backup_url`。
- 对返回的媒体 URL：带 `Referer: https://www.bilibili.com/bangumi/play/ep…` + Cookie 做 `curl -I`、`curl -r 0-65535 -o /dev/null`，记录状态码与重定向链。
- Logcat：`MediaPlayer`/`NuPlayer` 的 `what`/`extra`；ExoPlayer 试用时看 `InvalidResponseCodeException`。
- 三类样本各 3 个：普通视频、番剧免费集、番剧会员/地区受限集、直播开播中、直播未开播。
- 生命周期：后台→前台、返回键→重进、TV 息屏、切清晰度 5 次、切集。
- 会话：冷启动后 Cookie 是否保留；手动使 Cookie 失效后 `-101` 的表现；退出登录后 WebView 与 API 是否都失效。
- 安全：release 包检查 `run-as`/`adb backup` 是否可取到 `app_webview` 数据；确认 `allowBackup` 行为。

---

## 8. 建议的修复优先级（给 lead）

1. **P1 番剧取证 + 接口层修复（1.5–3 人日）**：PA-01/PA-02/PA-03/PA-04/PA-05 中不依赖 Media3 的部分；同时完成 §7.3 的抓包。此步决定是否需要媒体内核迁移。
2. **P1 账号安全（0.5–1 人日）**：PA-06（备份/发布构建），PA-17（退出登录）可紧随。
3. **决策门**：若阶段 0 后番剧样本仍失败且集中在 DASH/302/403 → 启动 §4 迁移（8.5–15 人日）；否则把 Media3 排入后续技术债。
4. **P2 播放器健壮性（1–2 人日）**：PA-07/PA-08/PA-09/PA-10/PA-11。
5. **P2 产品一致性（0.5–1 人日）**：PA-12/PA-13/PA-14/PA-15（直播先实测再决定改造范围）。
6. **P2/P3 鉴权与文档（0.5–1 人日）**：PA-16/PA-18/PA-19/PA-20/PA-21、§7.1 文档修订。
7. **P3 收尾**：弹幕轨道/性能、媒体键、进度拖动、错误态重试、状态保存、弃用 API。

> 本报告的所有“可用/失败”判断均来自源码与仓库内既有产物；未运行 App、未产生新的运行结果或截图。

---

## 附录 A：审查涉及文件与关键行号索引

- `PlayerActivity.java`：生命周期 `:55-70,243-245`；解析入口 `:72-88`；播放器 `:90-119`；画面适配 `:121-133`；surface `:135-140`；按键 `:142-163`；菜单 `:171-233`；Overlay `:247-391`。
- `PlaybackRepository.java`：路由 `:54-65`；弹幕 `:67-95`；普通视频 `:97-105`；番剧 `:107-130`；流解析 `:132-159`；直播 `:161-171`；HTTP `:173-195`；ID 提取 `:197-208`。
- `BiliAuthRepository.java`：常量 `:41-42`；二维码 `:45-56`；轮询 `:58-76`；会话/cookie `:78-92`；签名请求 `:94-139`；Cookie 写入 `:141-158`；QR 生成 `:160-169`。
- `LoginActivity.java`：轮询链 `:23-38`；生命周期 `:40-50`；二维码请求 `:57-69`；登录界面 `:71-110`。
- 相关跨模块：`MainActivity.java:73-75,329-332,547-549,591-593`；`AndroidManifest.xml:7`；`BrowserActivity.java:27-36`；`gradle.properties:2`；`app/build.gradle:31`。
