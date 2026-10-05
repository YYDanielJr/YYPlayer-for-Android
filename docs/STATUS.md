# 进度与交接记录

## 2026-10-05：U05 音乐标签、同页详情与歌词（当前记录）

状态：**本轮实现、构建和 MuMu 功能／UI 验收完成；物理设备动效与低版本专项待验。** 新版已安装在 MuMu；不宣称达到 HyperOS 私有动效或硬件输出资格。

任务：U05.1 歌曲名／歌手／专辑／独立时长；U05.2 MainActivity 内音乐覆盖层、封面色渐变与分层弹簧；U05.3 本地歌词、同步／seek／对齐／紧凑控件。用户本轮指令替代 U04 的后续 UI 优先级；视频与设置仍用原生 Activity，音乐例外见 ADR0004。桌面工程仅只读参考 lyrics.rs / music_assets.rs，没有修改其文件。

已实现：音乐标签持久化与旧库后台补齐、标签搜索；目录 SAF 授权和有界导入；同目录 LRC、手动关联、内嵌 ID3/Vorbis/Opus/MP4 标签；桌面 LRC 时间／offset／多时间戳／稳定重复语义与 UTF-8／UTF-16／GB18030；动态封面取色、静态多层光晕、可中断展开收起／页面跟随／歌词强调、系统 scale=0 和减少动画。真实播放进度只取 PlaybackBackend，不使用假计时或 mock 生产数据。

文件：PlaybackBackend / AndroidMedia3PlaybackBackend / MediaLibraryRepository / MusicDocuments / MusicLyricsRepository / Lyrics / EmbeddedLyrics；MediaArtwork / MusicDocumentContracts / MusicPlayerScreen / YYPlayerApp / HyperDesign / LibraryScreens / PlayerScreens；MainActivity / PlayerHostActivity / PlayerApplication；LyricsTest / MusicLyricsRepositoryTest / ActivityPlaybackTest；scripts/Generate-MusicUiFixtures.ps1 / Test-MumuUi.ps1、ADR0004。自动 EOF 后实际曲目索引与 mediaGeneration 已修正，旧歌词／松手事件不会套到新歌曲。

实际验证：

- 最终代码 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` BUILD SUCCESSFUL（50s）；KTX 建议已修正，lint **No issues found**。最终 `./scripts/Test-MumuUi.ps1` exit 0，构建复核 BUILD SUCCESSFUL（10s），JVM 10 项、直接 Android instrumentation 10 项均 0 失败；设备输出 `OK (10 tests)` / Time 5.417s。记录见 [final-checks](validation/ui-v6/final-checks.txt)。设备回归新增真实 WAV 自动 EOF、主 Activity 音乐路由、手动关联失败保护、ID3/FLAC/MP4 UTF-8/UTF-16BE 标签，与原视频变换测试一起通过。
- MuMu 现有实例，ADB `127.0.0.1:16384`，Android15/API35、1080×1920。真实 SAF 导入，曲名／歌手／专辑／右侧时长、同目录 LRC、手动歌词、无歌词、左右对齐、点句 seek（暂停状态跳到 00:10）、细进度拖动（02:08）、末句居中阅读位置、跨页手势／切歌换色、收起返回／再次进入均实际操作。覆盖层空白顶部点击保持音乐页，没有点穿库设置。最终普通无 extra 启动为 1920×1080 横屏，已检查封面／歌词并排和控件，结束时暂停并收起音乐。最终库为 4 首音频／2 个 QA 视频／1 个收藏。图与原始动画录像见 [U05 验收说明](UI_MUSIC_EXPERIENCE.md)。
- 素材为脚本生成的自有低电平 FLAC/WAV、原创封面和 LRC；实际播放不使用 mock 或假进度。素材与 APK SHA256 见 [artifacts.json](validation/ui-v6/artifacts.json)。桌面仓库未改，手机未更新／清除，本轮截图没有个人视频帧。
- 初次 Gradle `connectedDebugAndroidTest` 的 9 项断言通过，但 UTP 收尾 exit 1，默认卸载 MuMu 应用并丢失应用数据；**不能把该轮写成成功或数据保留**。之后用 SAF 恢复观察到的 QA 音频／视频与收藏，未备份历史偏好不能逐项保证。安全脚本改用 `install -r`＋直接 `am instrument`，后续检查实际保留数据与 APK，不再走默认卸载路径。初次自有素材脚本遇到 Windows PowerShell 编码问题，补 BOM 后生成成功；失败结果不用于验收。

未执行／限制：物理120Hz/OEM 动效／多指，API26–32/R8、200%字体／TalkBack、横屏实机、长时间／20k 库性能未专项验收；USB/native 与后台服务未新增。目录读取需要真实 SAF 授权；内嵌歌词支持常见标签，ID3 v2.2／SYLT／加密等变体未支持，可手动 LRC/TXT。原视频片尾 Surface 问题没有因本轮音乐索引修复自动视为解决。下一具体动作：按用户真实音乐库与视觉反馈检查标签／歌词／物理跟手感；随后处理视频片尾问题，完整核心继续 S02 native gate。

## 2026-10-05：U04 弹窗反馈铺满、导航回退（历史记录）

状态：**修复/回退与本轮真机检查完成，等待用户反馈。** 用户新增09:30两张截图，指出媒体选项按压反馈左右留白，并否定U03图文导航效果、允许回退；读取指定的两张截图，没有读取更早的照片。规则和证据见 [UI_DIALOG_FEEDBACK](UI_DIALOG_FEEDBACK.md)。

修改：HyperDialog原生WindowDialog水平insideMargin改0，HyperSettingRow/HyperSpinnerPreference条目内部24dp，点击/原生indication覆盖整个行；取消按钮/媒体标题单独24dp。卡片内16dp、方向SemiBold和固定取消空间保持。FloatingLibraryNavigation恢复U02官方FloatingNavigationBarItem纯图标项、180dp包装/52dp搜索，库底部空间恢复192/108dp；不继续声称U03已达到相册的动画/模糊/间距。文件为HyperDesign.kt、LibraryScreens.kt、YYPlayerApp.kt。

验证：

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 最终BUILD SUCCESSFUL（42s）；策略5项0失败，lint No issues found。初次补丁整体未匹配、随后使用新增常量时构建失败，补齐完整补丁后重跑通过；未安装失败版本。
- 小米17 Pro Max实际型号2509FPN0BC，Android17/API37、1200×2608/density480。`adb install -r` Success，最新包已留在手机；媒体9首/收藏0等数据保留。按住收藏后截图，再移到弹窗外取消并释放，仍显示“收藏”，没有更改收藏。
- 真实UI层级：收藏/播放/移除三行clickable从x36至1164，与弹窗左右边界相同；文字x108，保持24dp。按压截图灰色实际覆盖两侧，用户提供的旧图同位置有72px白条。证据：[原图](validation/ui-v5/reference-player.jpg)、[新按压](validation/ui-v5/dialog-pressed.png)、[旧行](validation/ui-v5/pressed-before-row.png)、[新行](validation/ui-v5/pressed-after-row.png)、[回退导航](validation/ui-v5/navigation-restored.png)。
- 未改系统密度、尺寸、方向锁或偏好；没有删除原手机截图/用户媒体。只清理本轮临时截图/XML。没有新增镜像单测或重跑旧6项设备测试；窗口/后端/手势生命周期没有改动。未将相册等效动画、API26–32/R8/200%字体、USB和旧片尾问题标记已验。

下一动作：优先用户实际反馈；之后修复已记录片尾Surface问题，再按产品计划推进native。U03的字重和设置缩进保留，导航和弹窗边距规则以U04为准。

## 2026-10-05：U03 小米真机缩进、方向字重、图文悬浮栏（历史记录）

状态：**本轮三项实现、构建与指定真机布局检查完成，等待用户视觉反馈。** 用户授权使用其小米17 Pro Max取材调试；实际ADB型号2509FPN0BC、Android17/API37、1200×2608、density480。没有改密度、尺寸、字体、旋转锁或系统设置。取材及对应规则见 [UI_REAL_DEVICE_REFINEMENTS](UI_REAL_DEVICE_REFINEMENTS.md)。

实现：

- 设置和播放窗口缩进统一：系统“锁屏”条目文字左边界约83px，对应卡片外12dp/内16dp；原设置原生解码行x84px，自写行x96px，修改后主题/强调色/减少动画/视频解码/方向/手势及分组标题统一x84px。HyperDesign增加PreferenceInset/PageTextInset，HyperSettingRow与HyperSpinnerPreference统一局部令牌；SettingsScreen和PlayerScreens使用同一包装。
- WindowDialog保留自身24dp，内部行水平边距0，避免再加16/20dp；真机横屏播放设置条目由原生x794px/自写x806px统一x746px。检查其原生倍速锚定浮窗可以展开/系统返回关闭。横屏还发现旧滚动区把取消按钮挤出屏幕，改为整个内容区有界、滚动列weight(fill=false)，固定取消按钮预留空间，实际可见并能关闭。
- OrientationPage的自动/竖屏/横屏保留17sp，全部SemiBold600；勾选槽与选择逻辑保留，说明/行边距按共同规则对齐。
- 按真机相册“图标上、文字下”取材，FloatingNavigationBar容器中组合官方NavigationBarItem/IconAndText，两个标签始终可见，选中胶囊底色；搜索64dp与条目等高，YYPlayerApp给库底部空间增加12dp。没有伪造FloatingNavigationBarItem的showLabel参数；Miuix图标/原生press/分页弹簧/Activity架构保留。

最终检查：

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 最后一次 **BUILD SUCCESSFUL in31s**；5项纯策略测试0失败；lint No issues found。首次布局构建41s也通过，发现横屏取消后修复并重跑。APK大小18,885,524bytes。
- 真机两次 `adb install -r` 均Success；没有卸载、清库或变更用户偏好。原9首音频、2个视频仍可展示，已导入视频仍能实际打开；测试期间暂停视频。
- 实际查看新版设置/方向页、点击图文导航切到视频库、打开视频播放设置和倍速浮窗、滚动长内容、点击取消。证据在 [validation/ui-v4](validation/ui-v4/)：system-settings、gallery-navigation局部、settings-before/after、orientation-before/after、navigation-before/after、video-dialog-before/after。最终弹窗截图包含可见取消按钮。视频画面/相册整屏临时文件与设备临时截图/XML已删除，仅保留必要UI局部。
- 手机自然横放时已有debug qa_portrait用于手机布局截图；横屏播放按真实比例，尺寸/密度未经override。结束停止测试播放、无extra普通MainActivity启动；最终包已留在手机。手机截图是这台设备实际渲染，不是模拟器或UI mock。
- 本轮未重跑U02六项instrumentation，未更改引擎/手势/Activity生命周期；没有为可逆布局变更添加镜像单测。API26–32、release R8、200%字体/TalkBack、物理手势专项、OEM转场专项、USB DAC和片尾Surface问题仍未因本轮布局验证自动通过。

修改文件：HyperDesign.kt、SettingsScreen.kt、PlayerScreens.kt、OrientationPage.kt、YYPlayerApp.kt；README/AGENTS/本记录/界面说明同步当前规则。下一动作：优先接收真机视觉反馈，然后处理已记录片尾Surface恢复；不能跳过用户UI反馈直接进入native阶段。

## 2026-10-04：U02 Miuix窗口、图标、Activity及动画（历史记录）

状态：**本轮实现与MuMu检查完成，等待用户视觉反馈；HyperOS厂商动效待真机。** 严格对照用户新增5张窗口截图，以及只读 `references/miuix-0.9.4/` 中文文档、示例、组件源码；没有修改参考文件。对应与动效参数详见 [UI_COMPONENTS_AND_MOTION](UI_COMPONENTS_AND_MOTION.md)。

实现与文件职责：

- `HyperDesign.kt`：原生WindowDialog/居中标题/TextButton，所有弹窗保留节点直到原生退出；原生FloatingNavigationBar/Item默认图标布局、独立搜索、按压反馈和默认顶栏回弹。`PlayerIcons`改为MiuixIcons.Regular，移除自绘向量和Material扩展图标实现，新增同版本icons/preference依赖。
- `SettingsScreen.kt`、`PlayerScreens.kt`：锚定选择用WindowSpinnerPreference；速度/队列按上游WindowDropdownDialog源码组合WindowDialog与DropdownImpl；原生布局/勾选/触觉/取消按钮，显式surface底色修正上游标题灰块；列表有界可滚动。暗色视频菜单同样使用原生窗口。
- `OrientationPage.kt`：完整独立选择页，大标题、白色分立卡片、左侧选中勾选；选择后留在页内并持久保存。
- `MainActivity.kt`、`PlayerHostActivity.kt`、manifest：SettingsActivity、PlayerActivity、OrientationActivity实际入栈，移除二级页面AnimatedContent。普通start/finish不覆盖系统/ROM转场；reduce-motion才关闭Activity转场。截图extra仅debug可用。
- `PlayerApplication.kt`：前台唯一runtime、共享播放/偏好，内部切页不暂停/释放引擎；所有Activity停止后暂停，最后owner退出释放。生命周期与以后service迁移规则见 [ADR0003](adr/0003-activity-stack-runtime.md)。`VideoSurface.kt`保留外层变换状态，只在Activity停止时detach实际AndroidView。
- `YYPlayerApp.kt`：媒体库仍为根页内音乐/视频分页，使用上游springAnimateToPage、horizontalPagerSwipeOverride、PagerGestureNestedScrollConnection；重复点击取消旧动画job。视频控件采用Miuix DecelerateEasing1.5/300–250ms淡入淡出。

最终验证：

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4`：BUILD SUCCESSFUL（37s）。纯策略5项通过；lint报告No issues found。
- 两个APK使用 `adb -s emulator-5554 install -r` 更新，保留媒体库/偏好；`am instrument -w yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner`：**OK (6 tests)，3.431s**。包括1项真实30秒PCM WAV跨Activity播放/同实例/返回旧Main，4项TextureView手势/变换/临时倍速测试，原context测试1项。测试文件临时创建在私有cache后删除，没有改用户导入或偏好。
- MuMu Android15/API35：原生白色倍速选择1.5×确实更新快照、关闭窗口，再还原1×；浅色锚定解码浮窗/全页方向/媒体操作窗口、深色播放菜单/倍速窗口逐张检查。实际PlayerActivity→OrientationActivity→PlayerActivity，播放中和暂停在1.25秒的视频均重显真实解码帧；活动栈记录确认有独立Activity。
- 证据：[总览](validation/ui-v3/overview.png)、[选择窗口](validation/ui-v3/speed-dialog.png)、[深色窗口](validation/ui-v3/speed-dark.png)、[锚定浮窗](validation/ui-v3/decoder-popup.png)、[方向页](validation/ui-v3/orientation-page.png)、[暂停视频返回](validation/ui-v3/video-paused-return.png)、[Activity栈](validation/ui-v3/activity-stack.txt)、[6项测试](validation/ui-v3/instrumentation.txt)、[切换录屏](validation/ui-v3/motion.mp4)。录屏展示实际系统Activity进出、锚定浮窗和主页弹簧翻页；不是HyperOS真机动画证明。
- 手机截图临时density440/debug qa_portrait；结束恢复原density280、1080×1920、free rotation，并无extra普通启动。没有新增提交、清理用户数据、修改Raidian/Rust/截图/Miuix参考仓库。

已知问题与范围：视频已到片尾STATE_ENDED后，detach/新建TextureView时可能不再重绘最后一帧而显示黑色；点播放重头播放可恢复。本轮没有改播放状态伪造最后帧，下一播放修复应保留真实EOF/重播意图并添加末帧恢复测试。播放中/暂停往返已验不受此问题影响。小米第一方OEM转场、系统手势返回、API26–32/release R8/200%字体、物理多指、真机/DAC沿用待验；Miuix自带动效的使用不代表全部第一方应用完全相同。下一动作优先用户视觉反馈及片尾Surface恢复，之后再按计划推进native。

## 2026-10-04：U01 HyperOS 前端整体重写（历史视觉基线）

状态：**实现与本轮模拟器检查完成，等待用户审美反馈。** 用户明确否定 E00 前端；本轮阅读 `references/` 的 11 张 HyperOS 4 截图与 Raidian 的 Miuix 页面、主题和磨砂实现，以此整体重写界面。Raidian 和 Rust 参考仓库均未修改。具体设计/文件职责见 [UI_REWRITE](UI_REWRITE.md)。

实现：音乐/视频双分页、可折叠大标题、灰白圆角分组、悬浮磨砂导航与独立搜索、真实封面/视频首帧、收藏过滤、迷你播放器、音乐播放页、沉浸视频控件与弹层、浅深/系统主题、三种强调色与减少动画、横屏侧栏。SAF 导入留在媒体库，点击后才播放；后台读取元数据，限制并发和缓存；技术信息收进菜单。应用入口替换了 Android 模板图标。收藏、主题、强调色、减少动画和方向偏好持久化；解码模式仍为本次播放策略，没有伪称重启保持。

修复：长按等待曾阻止第二指进入缩放；改成单一 pointer 事件循环，第二指立即取消临时加速并变换真实 TextureView。新增 3 项 instrumentation：双指缩放/旋转实际矩阵、3×结束恢复原1.5×、移除 surface 时恢复速度；取消/媒体切换路径不向新媒体回写旧速度。还修正主题实时切换后的预览丢失、卡片点击反馈越过圆角，以及窗口尺寸变化后的系统栏明暗图标。

构建与测试：

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --no-daemon --max-workers=4` 通过；纯策略测试 5 项，设备测试 4 项（3 手势 + 原有 context 测试），0 失败。
- 最后视觉资源/主题预览/系统栏修正后，重复 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 通过；lint `No issues found`。手势生产实现没有继续改动，因此未重复 connected 测试。
- MuMu `emulator-5554`，Android15/API35：实际 SAF 导入 WAV 与两个 H.264/AAC MP4；播放、暂停、进度、收藏过滤、搜索、浅深主题、重启后 URI/收藏保留已检查。新版菜单实际软解 `OMX.google.h264.decoder`、严格硬解 `OMX.qcom.video.decoder.avc`；同一竖屏素材手动横屏再自动恢复竖屏成功。
- 新证据在 [validation/ui-v2](validation/ui-v2/)；`music-light.png`、`music-dark.png`、`video-library.png`、`audio-player.png`、`settings-light.png`、`settings-dark.png`、`search.png`、`favorites.png`、`video-menu.png`、`video-portrait.png`、`video-landscape.png`、两个 decoder info 图和 `library-wide.png`。素材沿用 E00 的 hash；不是虚构曲库。

验收复现注意：手机截图临时设置 density440，并以 debug-only `qa_portrait=true` Intent 启动，因为 MuMu host sensor 覆盖 wm 方向锁；显式视频方向优先。宽屏布局以临时 `wm size 1920x1080` 验证。结束已恢复 `wm size reset`、density280、`wm user-rotation free`，以无 extra 的普通 Intent 启动。常规安装用 `adb install -r` 保留导入/偏好。Gradle connected UTP 在本机卸载目标包并清掉测试导入/偏好，后续只在可丢弃 QA 实例执行；本轮仅测试素材导入受影响，已从真实 SAF 重新导入，设备媒体文件保留。

Miuix blur0.9.4 的 manifest min33；项目仍 min26，通过 [ADR0002](adr/0002-api33-blur-facade.md) 的无类型 facade、SDK 守卫与 `@Keep` API33 类隔离。API26–32 的不透明回退、release R8 仍需对应系统实测。USB HiFi/native、真机物理多指、后台服务等没有因本轮 UI 工作变成已验。下一步先接收用户对新版视觉的反馈；无进一步 UI 调整时继续 S02。

## 2026-10-04：E00 MuMu 前后端与 UI 纵切

状态：**历史播放纵切。其视觉已被用户否定，并由 U01 替换。** 已安装/运行 MuMu；真实 SAF 本地音视频经过 Media3 播放；实际硬件与软件 H.264 decoder 都切换成功；自动/手动方向和主题切换已通过截图检查。E00 只代表当时模拟器验收范围，不代表长期音频后端或硬件 HiFi 验收完成。

主要实现：

- Compose + Miuix 四页 UI（音乐、视频、播放、设置），SYSTEM/LIGHT/DARK 主题和 reduce-motion 偏好持久化。
- SAF 多选音频/视频，保存 URI、展示名、MIME 和类型；不申请全盘访问权限，授予可持续 URI 权限；应用更新/重启后仍能读取导入条目。
- `PlaybackBackend` 与 `PlaybackSnapshot` 状态/命令契约；当前 `AndroidMedia3PlaybackBackend` 以 ExoPlayer 做本地音视频播放、进度、队列、变速和 decoder analytics。
- MediaCodec 策略 Auto / PreferHardware / StrictHardware / Software；切换时保留当前媒体、进度和播放意图。StrictHardware 只给硬件 decoder，关闭 decoder fallback。MuMu 对 `OMX.google.h264.decoder` 同时报 hardwareAccelerated=false、softwareOnly=false，因此软件策略将 Android 标准 `OMX.google.*` / `c2.android.*` 名称作为明确 fallback；UI 显示“平台软解（系统标记不完整）”，不将系统 false flag 说成系统标记软件。
- 视频 TextureView surface 显示实际 decoder；自动从有效视频比例选择方向，提供手动竖屏/横屏/自动；双指缩放/旋转/平移矩阵、0.5–5×范围、长按临时 3×已实现。
- 已提交纯策略单测；新增 `scripts/Generate-MumuFixtures.ps1` 以 FFmpeg 生成并可选推送测试素材。

环境和命令：

- Windows；MuMu adb serial `emulator-5554`；Android 15 / API 35；系统枚举 Xiaomi 2304FPN6DC；显示物理尺寸 1080×1920，activity 可切横/竖。
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4`：BUILD SUCCESSFUL；5 tests、0 failures；lint 通过。编译剩一项 Media3 弃用 warning：`VideoSize.unappliedRotationDegrees`。
- `.\scripts\Generate-MumuFixtures.ps1 -PushToEmulator` 完成。测试素材 SHA256：WAV `64F80E999CE6B3D2805FD5A2466F248BC59B900543BD83F6F082A9B6161918DB`；横屏 MP4 `15752209EE46CDA0271949C2F806CE19C7554181B89066A8F19B1588DA5838B7`；竖屏 MP4 `2830840578CD76AE4A8867928EFC33FB3B40B33C9D2C1FAB12397CAC70F562E7`。

MuMu 实测和证据：

- SAF 导入 44.1 kHz / 2 声道、120 秒 WAV；播放页采样率/声道/进度与实际 Media3 输入轨道相符。
- 横屏 H.264/AAC 使用 `OMX.qcom.video.decoder.avc` 严格硬解播放；切换 Software 后使用 `OMX.google.h264.decoder` 播放；两个模式的画面持续更新，快照显示当前实际 decoder。
- 竖屏 360×640 H.264 在 Auto 下保持竖屏；对同一媒体手动横屏，再切回 Auto 会回竖屏；横屏素材手动竖屏也生效。
- 用户可见主题浅/深切换生效。截图在 `docs/validation/`：`mumu-home.png`、`mumu-audio.png`、`mumu-video-hardware.png`、`mumu-video-software.png`、`mumu-video-portrait-auto.png`、`mumu-settings-light.png`、`mumu-settings-dark.png`。

仍未验：真实小米/红米和 USB DAC；USB BIT_PERFECT、AAudio Exclusive、AudioTrack 实际输出格式/位深；C++/JNI/libmpv/FFmpeg/Oboe；API 26；API37 大屏/折叠/多窗口；后台 MediaSession；多指/长按物理输入自动化；字幕/多音轨/codec 格式矩阵；release 签名/16 KB native 装载/HDR。MuMu 上的音频轨道格式不是设备输出格式，不能作为 HiFi 证明。手势在 UI 已实现，但未以真实双指设备做验收。

下一具体阶段：S02 native 依赖供应链与 JavaVM/TextureView gate；E00 Media3 后端保留作 UI/播放对照，native 构建/装载证据到位前不替换。

## 2026-10-04：需求调研与技术规划

状态：**规划基线（历史记录，已被上面的 E00 实现记录 supersede）。** 当时应用实现尚未开始；本条保留调研时环境/范围快照。

已确认的产品范围：

- 主要HiFi组合为小米/红米手机 + USB DAC；兼容内置输出、扬声器、蓝牙。
- 本地音视频优先，参考哔哩哔哩的播放功能与手势；首版不接其内容服务。
- Compose + Miuix的HyperOS风格、主题和充分动画；核心与UI分离，保留其他移动OS适配空间。
- 视频必须软硬解切换、自动/手动横竖屏、长按临时3×、双指连续缩放/旋转/平移；不能删需求来降低实现难度。

实际检查：

- Android工程为Hello Android模板，只有`:app`；当前master无提交，大部分模板文件未跟踪；本轮没有修改业务源码、Gradle配置或manifest。
- 当前catalog：AGP9.4.1、Gradle9.6.0、Compose compiler2.2.10、BOM2026.02.01，API26–37；包名`yy.daniel.yyplayerforandroid`。
- 本机Java25.0.3、Rust1.99.0；SDK配置为`E:\DeveloperTools\AndroidSDK`，已发现NDK28.2.13676358与CMake3.22.1等版本。Rust版本只是环境记录，Android首版不依赖它。
- Rust参考项目`E:\SourceFiles\rust\yyplayer`位于dev、`ec55789`，工作区检查无变更；读取README/AGENTS/STATUS/core模型/音频策略/worker清单/runtime manifest。未修改参考仓库。
- 查阅官方Android文档、Miuix/mpv/mpv-android/Oboe/libusb源码及发布资料；完整来源和候选版本见RESEARCH_SOURCES。

当时交付：README.md、AGENTS.md、docs/TECHNICAL_DESIGN.md、docs/IMPLEMENTATION_PLAN.md、docs/RESEARCH_SOURCES.md、本文件。除后续 E00 明确列出的实现外，规划接口/模块/脚本仍不是已创建的软件实现。

选择：Kotlin/Compose/Miuix + C++17 portable core；libmpv视频；FFmpeg音乐PCM；AudioTrack系统USB/Oboe独占；Media3系统会话；默认SDR TextureView连续变换。完整理由/数据契约/生命周期/质量策略/验收见TECHNICAL_DESIGN。

本轮检查与限制：

- 默认shell/Node沙箱启动返回`setup refresh had errors`；使用获准的沙箱外只读命令完成目录/环境检查，apply_patch完成工作区文档写入。没有因工具错误修改系统配置。
- PowerShell严格UTF-8读取、无替代字符/行尾空白、Markdown围栏配对、8个本地文档链接、S00–S14共15个阶段与STATUS对应检查通过；`git diff --check`通过。由于模板及文档尚未跟踪，另外直接检查了新增文档文本，未把空diff当文档检查。
- 没有运行Gradle编译、安装工具、下载native二进制、连接/操作DAC、创建提交或发布。版本为查证过的候选，组合兼容性仍需S00/S02。
- 未验证：具体手机/ROM/DAC能力、系统BIT_PERFECT、AAudio实际Exclusive、TextureView硬解任意角、16KB装载、数字捕获/HDR。没有把这些标为支持。

当时下一任务：S00 工具链/Miuix 最小接入；实际当前下一任务已更新为上方 S02。

## 阶段状态

| 阶段 | 状态 | 尚需证据 |
| --- | --- | --- |
| U04 弹窗反馈/纯图标导航回退 | Done（实现/指定真机按压检查） | 用户视觉反馈 |
| U03 真机缩进/字重/图文导航 | Done（本轮实现/指定真机布局检查） | 用户视觉反馈；字体缩放/无障碍专项另验 |
| U02 Miuix窗口/图标/Activity/动画 | Done（本轮实现/模拟器检查） | 用户视觉反馈、HyperOS真机转场；片尾Surface恢复另列修复 |
| U01 HyperOS 前端重写 | Done（实现/模拟器检查） | 用户审美反馈、低 API/真机/R8 |
| E00 MuMu 实际播放/UI 纵切 | Done（仅本轮范围） | 物理双指/长按及真机硬件另验 |
| S00 工具链/Miuix | Partial | API26/API34真机启动、无障碍/多设备 |
| S01 契约/视觉骨架 | Partial | 可移植 C ABI/schema、完整预览/字体验收 |
| S02 原生技术gate | Planned | runtime闭包/JNI/任意角/音频probe |
| S03 URI/权限 | Partial | native FD/取消/失权/非seekable |
| S04 真实视频 | Partial | native Surface/字幕/多音轨/codec矩阵 |
| S05 系统服务 | Planned | 息屏/焦点/session/重建 |
| S06 FFmpeg音乐 | Partial | Media3 WAV已跑；FFmpeg PCM/sink 尚未实现 |
| S07 系统USB | Planned | 能力/严格失败/路由/真DAC |
| S08 Oboe独占 | Planned | 实际sharing/失败/长时 |
| S09 视频交互 | Partial | 方向已测；手势取消路径/物理多指未验 |
| S10 首版收口 | Partial | 基础 UI/主题已跑；无障碍/性能/功能完整度 |
| S11 DSP/无缝 | Planned | 样本/滤波/profile/连续输出 |
| S12 USB Direct | Planned | UAC/等时/白名单/数字capture |
| S13 后续专项 | Planned | PiP/网络/弹幕/HDR各自资格 |
| S14 正式发行 | Planned | 许可/源码/签名/ABI/真机闭包 |

## 后续任务记录模板

```text
日期：
阶段/子步骤：
状态：Planned / InProgress / Partial / Done
范围与前置条件：
改动文件：
检查命令、exit code、结果：
设备/ROM/DAC/固件/runtime/配置/素材hash：
证据文件：
未执行或未通过：
已知限制/可恢复动作：
下一具体步骤：
```
## E00 状态补充（历史，当前以顶部 U01 为准）

本节补充当时 E00 事实；当前工作以顶部 U01 为准。E00 已完成首个可运行的 Android 纵向切片：Jetpack Compose + Miuix UI、SAF 本地媒体导入、持久媒体列表、Media3 音视频播放、软解/硬解策略、基础旋转与主题切换。

在 MuMu Android 15/API 35（小米 2304FPN6DC 模拟设备）上，`:app:assembleDebug`、`:app:testDebugUnitTest`（5 项通过）与 `:app:lintDebug` 均通过。脚本生成的 H.264/AAC 样片实际分别使用 `OMX.qcom.video.decoder.avc` 硬件解码和 `OMX.google.h264.decoder` 平台软解；后者在 MuMu 上的系统 hardware/software 标记均为 false，界面已明确标注系统标记不完整。WAV 播放链路显示 44.1 kHz、2 声道输入格式。

后续从 S02 原生核心供应链与 JNI/Surface 门禁开始。S00、S01、S03、S04、S06、S09、S10 仍有部分或真机验证工作；S02、S05、S07、S08、S11–S14 尚未完成。MuMu 结果不代表 USB DAC 独占、bit-perfect、真实设备音频路由或物理多点手势通过。完整命令、样片哈希、截图和细分边界见 README 与 IMPLEMENTATION_PLAN。
