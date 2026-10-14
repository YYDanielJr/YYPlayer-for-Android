# 首次引导、媒体库与音乐控件（U07–U14）

当前基线为 dev `607ceb3`（2026-10-14）。U08 固定页头和分页行为保留；U11–U14 的歌曲分类／队列／双语歌词、按钮及封面转场覆盖早期同名实现，见 [音乐体验](UI_MUSIC_EXPERIENCE.md)。下方旧截图限定于当时版本，不是当前 UI 验收图。

2026-10-05 用户七项新需求覆盖旧 U02–U04 底部纯图标导航规则。音乐仍在 MainActivity 内展开，视频与设置仍使用原生 Activity；共享 Media3 引擎、SAF 身份、两个独立联网开关不变。实际验收见 [STATUS](STATUS.md) 最新记录。

U08（同日，用户已复核满意）细化第 4 条：分页只发生在 TabRow 下方，页头固定；标题与右侧图标按 pager 偏移量渐隐渐显，滚动时大标题收成栏内小标题；TabRow 40dp；当时的收藏卡片已在 U13 移除。证据在 `docs/validation/ui-v8/`。

## 用户路径

1. 新安装打开欢迎页，可开始或稍后设置。文件页通过系统 SAF 选择歌曲、视频或音乐文件夹，仅持久保存实际授予的权限；取消或暂不授权仍可继续。
2. 分别选择联网歌曲和联网歌词，默认均关闭；可导入现有 version=1 JSON 配置，也可保留内置 GD 示例。原有配置校验与失败保护继续生效。
3. 选择跟随系统／浅色／深色、强调色与减少动画，最后开始使用。设置 → 使用引导可重新进入；旧安装有媒体或偏好时迁移为已完成，不重置任何选择。首次创建独立 setup_v1 标记，因此中途改变主题后重开仍继续显示引导。Android 重建保存当前步；完全关闭后未完成引导重新从欢迎页开始，已选择的偏好与授权保留。
4. 竖屏音乐／视频切换放在库上方。页头（标题、右侧图标、Miuix TabRowWithContour）固定在分页之外：切换时只有 TabRow 下方的内容左右滑动，标题与右侧图标按 pager 偏移量渐隐渐显，并在两页交界处换字；TabRow 常驻，不随内容滚走。滚动时大标题收成栏内小标题。TabRow 高 40dp；歌曲／歌手／专辑／歌单分类在其内容区，首页不再显示“全部音乐／收藏”卡片，收藏从“歌单 → 我的收藏”访问。本地搜索、在线搜索（开关允许时）、设置和添加放在页头。宽横屏保留侧栏。底部仅在有当前媒体时显示播放胶囊，点击文字进入详情；仅保留播放／暂停按钮，直接控制唯一真实播放器；下一首留在详情页。
5. 播放胶囊使用完整圆角、API33+既有独立 blur facade 的背景磨砂／玻璃描边与 SinkFeedback。API26–32不透明回退。环形进度在播放键外圈，直接取实际 position／duration，保留无障碍进度与 seek；没有模拟时间或源输出资格推断。
6. 歌曲封面与阴影从左下角展开／收回，保留轻微回弹；U14 共享层在收回后持续绘制底栏小封面，避免终点换图，透明详情退出时不再响应点击。角落按压使用 9°、临界阻尼 1／刚度 95 的慢跟随效果，无触底回弹；封面／歌词切换约500ms。减少动画／系统动画关闭时禁用相应持续效果，横屏保留歌词展开区域。
7. 设置 → 歌词显示：选中／非选中字号各14–48sp，字重各400／500／600／700／800／900，独立自动主题色或6／8位十六进制颜色，所有行额外间距0–40dp，实时预览并持久保存。LRC／纯文本都应用间距，LRC仍按真实时间强调／seek。多行折行采用1.35倍字体行高；每段歌词保留阅读触控内边距。样式已升级版本2并迁移版本1；译文显示开关、当前／其他译文12–48sp、字重及原译间距0–40dp可独立设置。所有值有范围／有限数校验，坏值备份后回退默认。
8. 本地音乐长按一首进入多选，可勾选、多选当前列表、播放所选、批量收藏／取消收藏、确认后从库移除；更多菜单也提供多选入口。返回／退出／切换到视频取消选中。批量播放按列表原顺序建立真实队列；移除只改库记录，不删除原文件，也不偷偷更改正在播放的队列。

页面操作共用 HyperActionButton：52dp／18sp，中等字重；弹窗50dp／17sp；并排等宽、12dp间隔，次要／取消灰底，确认主题色。队列使用独立 Activity，提供单／多选移除、长按拖动及无障碍替代动作；歌曲只有整组首尾外圆角。

## 文件与验证

核心文件：SetupRepository／SetupScreen、LibraryScreens／YYPlayerApp／HyperDesign、MusicPlayerScreen／MediaArtwork、LyricStyle／LyricStyleScreen／PlayerApplication／PlayerHostActivity、MediaLibraryRepository。U08 后库页拆分为 `LibraryTabs`（40dp 轮廓标签）／`LibraryHeaderActions`（页头图标）／`LibraryPageController`（多选状态，带 Saver）／`LibraryContent`（仅滚动内容与对话框）；`HyperPage` 新增 `headerAlpha: () -> Float` 延迟读取与 `tabs`（`TopAppBar.bottomContent`）槽位，页头因此可以留在 pager 之外。库页自持 scroll behavior：共享 `TopAppBarState` 会让滚动后栏内小标题不再出现（U08.4）。

新增测试覆盖首次标记与旧用户迁移、歌词颜色／非法样式、长按不播放／真实选中集／确认取消／无障碍入口；MusicExperienceUiTest 使用应用私有30秒WAV与原创几何PNG验证实际Media3 seek／pause／next、稳定封面尺寸、按住倾斜及进入歌词。OnboardingUiTest 重新打开真实引导并检查跳过权限不改原偏好与媒体库。LibraryTabsUiTest 校验切换标签时页头与 TabRow 的 bounds 不变、只有内容区变化。没有使用生产假播放器或清空用户数据。

专项边界：本轮以 MuMu Android15/API35为设备证据；低版本真实机磨砂回退、物理小米ROM玻璃视觉和200%字号／TalkBack另验。不把屏幕截图当作端到端音质、后台播放或私有HyperOS能力证明。

## MuMu 实际界面

[引导欢迎](validation/ui-v7/setup-welcome.png) · [文件授权](validation/ui-v7/setup-files.png) · [API导入](validation/ui-v7/setup-api-imported.png) · [浅色主题](validation/ui-v7/setup-theme.png) · [顶部标签](validation/ui-v7/library-tabs-light.png) · [批量管理](validation/ui-v7/music-batch-selection.png) · [实际两首队列](validation/ui-v7/batch-real-queue.png) · [歌词自定义](validation/ui-v7/lyric-style-custom.png) · [真实同步歌词](validation/ui-v7/lyrics-custom-real.png) · [按压倾斜](validation/ui-v7/cover-tilt-pressed.png) · [播放胶囊与环](validation/ui-v7/mini-player-ring.png) · [Sink](validation/ui-v7/mini-player-sink-pressed.png)。完整命令、失误修正、版本／数据保留与未验范围见STATUS最新U07记录。

## 用户实体小米界面（U08）

同一台小米 2509FPN0BC 的改动前／后对照：改动前用户参考图（当前仓库未收录原图） · [改动后首页](validation/ui-v8/after-home.png) · [滚动收起小标题](validation/ui-v8/after-scroll.png) · [切换中间帧（跟手淡出）](validation/ui-v8/after-tab-midfade.png) · [视频页](validation/ui-v8/after-tab-video.png)。命令与像素核对见 [STATUS](STATUS.md) 的 U08 记录与 `docs/validation/ui-v8/README.md`。
