# Pearl Glass Gallery Design QA

- Source visual truth: `design/concepts/02-pearl-glass-gallery.png`, `design/concepts/07-my.png`, `design/concepts/08-search.png`, `design/concepts/09-detail.png`, `design/concepts/10-player-controls.png`
- Implementation evidence: `qa/v1.15.0/home-gallery-pass3.png`, `qa/v1.15.0/search-type-layout-all-final.png`, `qa/v1.15.0/search-type-layout-user-final.png`, `qa/v1.15.0/detail-polish-final.png`, `qa/v1.15.0/dynamic-types-final.png`, `qa/v1.15.0/dynamic-detail-type-final.png`, `qa/v1.15.0/my-profile-hero-final.png`, `qa/v1.15.0/my-history-polish-final.png`, `qa/v1.15.0/my-favorites-polish-final.png`, `qa/v1.15.0/my-watchlater-polish-final.png`, `qa/v1.15.0/my-bangumi-polish-final.png`, `qa/v1.15.0/my-settings-icons-final.png`, `qa/v1.15.0/my-loading-skeleton-final.png`, `qa/v1.15.0/my-error-retry-final.png`, `qa/v1.15.0/my-retry-recovered-final.png`, `qa/v1.15.0/player-quality-horizontal-final.png`, `qa/v1.15.0/player-speed-horizontal-final.png`, `qa/v1.15.0/player-error-regression-final.png`, `qa/v1.15.0/player-bangumi-list-regression-final.png`, `qa/v1.15.0/player-live-danmaku-menu-final.png`
- Full-view comparison: `qa/v1.15.0/compare-home-pass3.png`, `qa/v1.15.0/compare-detail-polish-final.png`, `qa/v1.15.0/compare-dynamic-types-final.png`, `qa/v1.15.0/compare-search-types-final.png`, `qa/v1.15.0/compare-my-profile-final.png`, `qa/v1.15.0/compare-my-polish-final.png`, `qa/v1.15.0/compare-player-horizontal-final.png`
- Focused comparison: `qa/v1.15.0/compare-detail-focus-final.png`（详情主信息、封面、操作按钮）、`qa/v1.15.0/compare-player-controls-focus-final.png`（清晰度条、功能按钮、进度条）
- Viewport: 1920×1080 Android emulator, landscape, density-normalized reference stretched from 1672×941 to 1920×1080 for composition comparison
- Pixel normalization: source detail/player images 1672×941 px; implementation screenshots 1920×1080 px; references normalized to 1920×1080 before comparison. Native Android Canvas/View rendering has no CSS viewport; AVD output is device-pixel 1920×1080 at its configured density.
- State: 推荐/综合第一张卡聚焦；综合搜索第一张结果聚焦；用户搜索第一张资料卡聚焦；登录态“我的”历史记录聚焦；普通视频详情/播放按钮聚焦；播放器清晰度按钮与 1080P 下级选项聚焦

## Findings

- [P3] 当前账号的空间头图接口未返回可用图片，页面使用产品主题环境色兜底
  - 位置：`MainActivity.drawPersonalRoot`。
  - 证据：`compare-my-polish-final.png` 中资料区已有明确的横幅、头像压边、身份与统计层级；实现已尝试 WBI 空间头图接口，但当前账号环境未取得图片。
  - 影响：不影响导航与资料辨识，仅少一层个性化。
  - 后续：接口返回真实 `top_photo` 时会自动替换横幅底图；风控或空值时继续安全回退，不使用无关视频封面冒充。
- [P3] 详情页操作按钮使用 Android 系统图标，与效果图的定制线性图标笔画略有差异
  - 位置：`VideoDetailActivity.setButtonIcon`。
  - 证据：`compare-detail-focus-final.png`；图标语义、对齐和焦点对比已完整，系统图标的造型更偏平台原生。
  - 影响：不影响辨识和遥控器操作，仅有轻微风格差异。
  - 后续：如引入正式品牌图标资产，可整体替换为同一套线性图标。

## Required Fidelity Surfaces

- Typography: 首页、导航、分类和详情标题已放大；搜索视频、海报、主播/用户资料卡均有独立文本层级。
- Spacing/layout: 首页已改为导航后直接进入分类和主视觉画廊；详情封面、按钮、推荐区比例已重构。
- Colors/tokens: 珍珠白、冷青、暖粉、品牌粉和深色影院背景与目标一致。
- Image quality: 视频封面保持原图、不加白雾；详情使用同一封面作为全屏氛围背景；动态与搜索用户卡使用真实头像。
- Copy/content: 真实接口内容替代效果图占位文案，属于预期差异；搜索分类和资料字段与目标信息层级一致。
- Player controls: 功能按钮固定在进度条右上方横排；左右切换功能，OK 后进入弹幕/倍速/清晰度/选集下级菜单；进度条补齐播放状态、缓存进度、当前进度和拖动圆点。
- Player states: 快进、快退、暂停、继续、弹幕和倍速均有短时反馈；缓冲和失败为独立面板，失败态可直接按 OK 重新解析播放地址。
- Player submenu: 弹幕、倍速和清晰度使用横向紧凑选择条，选集/分 P 因条目多继续使用纵向列表；两类菜单均支持方向键选择和 OK 应用。
- Danmaku: 画布限制在上方安全区域；可见弹幕先按横向位置排序再分配八条车道，并限制超长文本，避免同车道碰撞和正文侵入控制区。

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

## Implementation Checklist

1. 后续如接口稳定提供空间头图，以真实头图替换主题横幅。
2. 保留现有 4/6/3 列焦点状态机回归测试。

## Focused Region Comparison

首页顶部导航、分类栏、首行卡片、搜索多类型卡片、动态类型徽标与图文详情、“我的”资料区均可由全屏拼图判断；详情页主操作区和播放器控制区另有局部放大对照，用于核对图标、间距、焦点描边和菜单密度。

final result: passed
