# Pearl Glass Gallery Design QA

- Source visual truth: `design/concepts/02-pearl-glass-gallery.png`, `design/concepts/07-my.png`, `design/concepts/08-search.png`, `design/concepts/09-detail.png`, `design/concepts/10-player-controls.png`, `design/concepts/11-settings.png`
- Implementation evidence: `qa/v1.17.2/home-focus-reflow-first.png`, `qa/v1.17.2/home-focus-reflow-second.png`, `qa/v1.17.2/home-focus-reflow-next-row.png`, `qa/v1.17.2/dynamic-reflow-first.png`, `qa/v1.17.2/dynamic-reflow-second.png`, `qa/v1.17.2/favorites-sidebar-focus-first.png`, `qa/v1.17.2/favorites-sidebar-focus-second.png`, `qa/v1.17.2/my-content-focus.png`, `qa/v1.17.2/search-result-focus-second.png`，以及前序版本 `qa/v1.15.0/`–`qa/v1.17.1/` 的页面与状态证据
- Full-view comparison: `qa/v1.17.2/compare-home-reflow-final.png`, `qa/v1.17.2/compare-dynamic-reflow-final.png`, `qa/v1.17.2/compare-favorites-sidebar-final.png`, `qa/v1.17.2/compare-my-content-final.png`, `qa/v1.17.2/compare-search-result-final.png`，以及前序详情、播放器、设置和状态对照
- Focused comparison: `qa/v1.15.0/compare-detail-focus-final.png`（详情主信息、封面、操作按钮）、`qa/v1.15.0/compare-player-controls-focus-final.png`（清晰度条、功能按钮、进度条）、`qa/v1.16.3/compare-detail-icons.png` 与 `compare-player-icons.png`（统一图标的笔画、尺寸、基线和焦点态）、`qa/v1.17.0/nav-focus-pink-final.png`（顶部导航选中/焦点胶囊）、`qa/v1.17.1/state-cards-consistency.png`（UP 主页/动态详情/视频详情状态卡一致性）
- Viewport: 1920×1080 Android emulator, landscape, density-normalized reference stretched from 1672×941 to 1920×1080 for composition comparison
- Pixel normalization: source images 1672×941 px；implementation screenshots 1920×1080 px；v1.16.3–v1.17.2 对照图将参考图缩放到 1920×1080 后与实现图横向合并为 3840×1080。状态卡一致性图将三张 1920×1080 实现图各缩放为 960×540，合并为 2880×540。Native Android Canvas/View rendering has no CSS viewport; AVD output is device-pixel 1920×1080 at its configured density.
- State: v1.17.2 同时验证首页第一张聚焦、第二张聚焦、下一行滚动，动态与收藏第一/第二张聚焦，登录态“我的”内容聚焦以及搜索第二条结果聚焦；前序版本继续覆盖普通视频详情、播放器菜单、设置与错误/空状态。

## Findings

- [P3] 当前账号的空间头图接口未返回可用图片，页面使用产品主题环境色兜底
  - 位置：`MainActivity.drawPersonalRoot`。
  - 证据：`compare-my-polish-final.png` 中资料区已有明确的横幅、头像压边、身份与统计层级；实现已尝试 WBI 空间头图接口，但当前账号环境未取得图片。
  - 影响：不影响导航与资料辨识，仅少一层个性化。
  - 后续：接口返回真实 `top_photo` 时会自动替换横幅底图；风控或空值时继续安全回退，不使用无关视频封面冒充。

- [P3] 效果图未定义加载、空内容、无效参数和网络错误态
  - 位置：主画廊、搜索、UP 主页、动态详情与视频详情。
  - 证据：`qa/v1.17.1/state-cards-consistency.png` 展示三类详情页在同一信息层级、圆角、对齐和状态色系下的实现扩展。
  - 影响：这是效果图的覆盖缺口，不是与已定义视觉的偏移；实现已将错误可重试和非操作状态分开。
  - 后续：如补充专用空态效果图，再以同一页面/同一状态做像素级对照。

- [P3] 收藏页当前只接入真实默认收藏夹
  - 位置：`MainActivity.drawFavoritesGallery`。
  - 证据：`qa/v1.17.2/compare-favorites-sidebar-final.png` 已对齐左侧账号/收藏夹与右侧视频画廊的整体构图。
  - 影响：效果图中的多收藏夹目录和排序控件暂未出现，但页面没有伪造名称或数量；默认收藏视频可正常浏览。
  - 后续：接入收藏夹目录 API 后再把左侧信息面板升级为可聚焦目录列表。

- [P3] 搜索结果按真实类型使用单一当前分类网格
  - 位置：`MainActivity` 搜索结果布局。
  - 证据：`qa/v1.17.2/compare-search-result-final.png`；搜索框、分类栏、卡片密度和焦点语言与目标一致。
  - 影响：效果图同屏混排的多个分组改为“全部/视频/番剧/影视/主播/直播间/用户”焦点即切换，属于已确认的信息架构差异。
  - 后续：如需要完全同屏分组，需为各类型独立分页与焦点区补充状态模型。

## Required Fidelity Surfaces

- Typography: 首页、导航、分类和详情标题已放大；三列画廊标题按卡片实际宽度截断，搜索视频、海报、主播/用户资料卡均有独立文本层级。
- Spacing/layout: 首页、动态与收藏使用随焦点重排的三列画廊，只有当前卡成为宽幅主视觉；纵向换行会滚动到安全位置。详情封面、按钮、推荐区比例已重构。
- Colors/tokens: 珍珠白、冷青、暖粉、品牌粉和深色影院背景与目标一致。
- Image quality: 视频封面保持原图、不加白雾；详情使用同一封面作为全屏氛围背景；动态与搜索用户卡使用真实头像。
- Copy/content: 真实接口内容替代效果图占位文案，属于预期差异；搜索分类和资料字段与目标信息层级一致。
- Player controls: 功能按钮固定在进度条右上方横排；左右切换功能，OK 后进入弹幕/倍速/清晰度/选集下级菜单；进度条补齐播放状态、缓存进度、当前进度和拖动圆点。
- Player states: 快进、快退、暂停、继续、弹幕和倍速均有短时反馈；缓冲和失败为独立面板，失败态可直接按 OK 重新解析播放地址。
- Player submenu: 弹幕、倍速和清晰度使用横向紧凑选择条，选集/分 P 因条目多继续使用纵向列表；两类菜单均支持方向键选择和 OK 应用。
- Danmaku: 画布限制在上方安全区域；可见弹幕先按横向位置排序再分配八条车道，并限制超长文本，避免同车道碰撞和正文侵入控制区。
- Icons: 播放器、详情页、“我的”侧栏和内容徽标统一使用 Google Material Round 矢量资源；不再混用旧 Android 系统图标。实机对照中图标笔画、光学尺寸与文字基线一致。
- Focus/accessibility: 顶部导航和内容卡均使用品牌粉柔光与细描边，去除突兀黑圈；内容卡焦点变化以 170ms 缓动呈现，新卡扩展时旧卡同步恢复。设置行、侧栏、详情操作、播放器功能栏和更新按钮均有高对比焦点态；更新页滚动容器不再抢占初始焦点。
- States/content: 加载、空内容、无效参数与错误状态均使用独立卡片和双行信息层级；只有可重试错误获得焦点和 OK 操作，详情数据成功前不暴露占位按钮。

## Comparison History

1. 初始实现：首页存在额外标题区、四列等宽小卡；详情为几乎纯黑背景、小封面和紧凑按钮。结果：blocked。
2. 第二轮：首页移除额外标题区，导航/分类按效果图比例重排，改三列大画廊；详情恢复全屏封面背景并放大封面。结果：仍有按钮和文字比例偏小。
3. 第三轮：首页增加首张主视觉宽卡，详情放大标题、元信息、按钮和推荐卡文字；首页与详情全屏构图已无阻断级差异。
4. 第四轮：动态接口补作者头像和发布时间，新增关注头像横栏、主视觉动态、普通动态与图文动态卡；登录态“我的”完成同状态复核。动态页原 P1 已关闭，搜索结果分区与个人横幅仍待处理。
5. 第五轮：纠正“主视觉宽卡”误读；所有视频/动态缩略图恢复统一基础尺寸，仅焦点卡放大并置顶绘制。首页和动态页分别验证焦点从第一张转移到第二张，旧卡均正确恢复。
6. 第六轮：搜索数据保留内容类型，新增横版视频/直播、竖版番剧影视、头像主播/用户三套卡片与动态列数；“我的”补横幅式资料头。搜索结果播放、用户卡焦点和登录态资料页均通过实机截图复核。
7. 第七轮：动态数据保留视频、图文/OPUS、专栏、转发、直播和文字类型；画廊以类型徽标和不同正文/封面处理区分，图文详情补完整正文、九宫格与转发引用。真实动态流的视频、图文、直播及详情滚动通过截图复核；当前样本未出现转发卡，保留为后续数据态抽检。
8. 第八轮：“我的”历史、收藏、稍后再看、追番分别增加进度或状态徽标，设置页增加分组与说明，空态增加明确重试操作；五个二级页均完成 1080p 遥控器焦点复核，所有卡片默认等大且仅当前焦点放大。
9. 第九轮：“我的”侧栏和内容徽标统一为 Android 图标资源，四类内容补日期，加载态补骨架屏；断网错误态与 OK 重试恢复完成实机验证。空间头图实现真实接口优先、Pearl Glass 横幅降级，当前账号因接口未返回图片而使用降级状态。
10. 第十轮：播放器按效果图补齐播放/暂停图标、缓存层、品牌粉进度和白色圆点；功能栏保持右上横排并用 OK 打开下级菜单。新增操作反馈、重缓冲提示和可重试错误面板，普通视频/多 P、番剧选集与真实直播 HLS 均完成 1080p 实机复核。
11. 第十一轮：普通设置下级菜单由纵向大面板改为效果图式横向选择条，左右键可直接选择；长选集继续使用纵向列表。弹幕增加安全区、位置排序、车道避碰和长度上限；详情页增加垂直暗场及统一平台图标。全屏与局部对照均未发现 P0/P1/P2 差异。
12. 第十二轮：播放器、详情页和“我的”移除旧 Android 系统图标，统一为 Material Round；更新页补状态图标和稳定的按钮初始焦点。首次更新页截图发现焦点落在 ScrollView、按钮无白色焦点圈（P2），修复为滚动容器不可聚焦且按钮支持 touch-mode focus；`update-page-unified.png` 显示按钮白色外描边，P2 已关闭。四组同尺寸并排对照未发现新的 P0/P1/P2 差异。
13. 第十三轮：首次设置对照发现实现虽已双栏，但五行连续排列，缺少效果图中“播放/弹幕”与“账户与数据/关于”的分组节奏（P2）；补充第二组标题与 42dp 组间距后重新截图。`compare-settings-final.png` 显示栏目、三档值、动作入口与焦点态均形成对应层级；顶部导航黑圈已替换为粉色柔光 + 白色内描边，未发现新的 P0/P1/P2 差异。
14. 第十四轮：首次状态审查发现主页/搜索仍用浮置文字，动态详情加载时显示空白正文，视频详情失败时仍显示占位操作，“我的”侧栏/搜索 chip/UP 投稿卡仍有黑色填充焦点（P2）。已统一 Pearl Glass 状态卡、将错误与非操作状态分层，并在详情数据成功前隐藏占位内容；焦点改为浅粉底 + 品牌粉文字 + 柔光描边。后续证据 `compare-my-focus.png`、`compare-search-focus.png`、`compare-detail-regression.png` 与 `state-cards-consistency.png` 未发现新的 P0/P1/P2 差异。
15. 第十五轮：首次对照发现内容卡仍有深色外圈、焦点放大幅度偏弱且瞬间跳变，下一行焦点可能被底部裁切，收藏页也缺少效果图中的左侧账号/收藏夹结构（P2）。修复为 170ms 品牌粉柔光动画、三列按焦点权重重排、换行滚动安全区，并以真实资料重构收藏侧栏；`compare-home-reflow-final.png`、`compare-dynamic-reflow-final.png`、`compare-favorites-sidebar-final.png`、`compare-my-content-final.png` 与 `compare-search-result-final.png` 的修复后检查未发现 P0/P1/P2 差异。

## Implementation Checklist

1. 后续如接口稳定提供空间头图，以真实头图替换主题横幅。
2. 保留现有动态重排三列主画廊与搜索 4/6/3 列焦点状态机回归测试，重点覆盖焦点移走后旧卡恢复。
3. 真机发布前复测长标题、弱网错误态和高密度弹幕；这些属于运行环境风险，不是当前截图中的视觉阻断。

## Focused Region Comparison

首页顶部导航、分类栏、首行卡片、搜索多类型卡片、动态类型徽标与图文详情、“我的”资料区均可由全屏拼图判断；详情页主操作区和播放器控制区另有局部放大对照，用于核对图标、间距、焦点描边和菜单密度。

final result: passed
