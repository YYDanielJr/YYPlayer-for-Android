# YYPlayer for Android

面向本地音视频的 Android HiFi 播放器。产品方向是：尽可能保留音频源信息、可核实的设备输出、接近哔哩哔哩的播放交互，以及使用 Jetpack Compose + Miuix 构建的 HyperOS 风格界面。

**当前界面已接入 U07 七项新需求，并按 U08 完成媒体库分页与页头收尾（用户已复核满意）。** 首次安装引导通过 SAF 文件选择、两个独立联网选项、JSON导入和主题设置完成配置；竖屏库的页头（大标题、右侧图标、40dp 轮廓 TabRow）固定在分页之外——切换时只有 TabRow 下方的内容左右滑动，标题与图标按 pager 偏移量跟手渐隐渐显并在两页交界换字，滚动时大标题收成栏内小标题；底部只放玻璃播放胶囊、环形真实进度与Sink反馈。音乐详情保留圆角阴影切歌、Tilt封面点按和约500ms歌词切换；设置支持选中／非选中歌词样式，本地歌曲支持长按批量管理。行为与验证见 [U07/U08说明](docs/UI_SETUP_AND_LIBRARY.md)、[STATUS](docs/STATUS.md)。U05标签、封面取色与同步歌词及U04弹窗缩进规则继续保留；音乐仍在MainActivity内展开，视频／设置使用原生Activity。当前 Media3 是真实播放适配器；USB独占／位准确、C++/libmpv/FFmpeg/Oboe与后台MediaSession尚未实现，物理设备与API26–32专项待验。

## 文档入口

`master` 已保存 U05 基线（`80538db`）；当前 `dev-api` 增加在线音乐／歌词服务，设计与使用见 [在线 API](docs/ONLINE_API.md)。联网歌曲与联网歌词开关独立、默认关闭；GD 示例按用户提供文档包含五档音质和专辑图；默认 Music、自选目录、实际格式命名与可用音质检查已实现，具体验证结果见 STATUS。

| 文档 | 用途 |
| --- | --- |
| [技术设计](docs/TECHNICAL_DESIGN.md) | 选型理由、可行性边界、模块接口、音视频算法、UI 与平台适配 |
| [实施计划](docs/IMPLEMENTATION_PLAN.md) | S00–S14 的具体文件、操作顺序、前置条件和验收方法 |
| [AGENTS.md](AGENTS.md) | 后续代码代理必须遵循的执行规则与禁区 |
| [进度记录](docs/STATUS.md) | 实际完成情况、证据和下一任务；接手先读 |
| [U07 引导／库／控件](docs/UI_SETUP_AND_LIBRARY.md) | 首次设置、顶部标签、播放玻璃胶囊、歌词样式与长按批量管理 |
| [U05 音乐展示／歌词／动画](docs/UI_MUSIC_EXPERIENCE.md) | 曲目标签、同页详情、歌词规则、取色与 MuMu 实际证据 |
| [ADR 0004](docs/adr/0004-inline-music-player.md) | 音乐使用主页内动画覆盖层，视频保留 Activity |
| [U04 按压反馈/导航回退](docs/UI_DIALOG_FEEDBACK.md) | 反馈宽度规则、纯图标回退与真机证据 |
| [U03 真机界面修正](docs/UI_REAL_DEVICE_REFINEMENTS.md) | 实际设置缩进、方向字重、图文导航与真机证据 |
| [U02 窗口/图标/动画](docs/UI_COMPONENTS_AND_MOTION.md) | 新窗口参考、官方组件对应、Activity与动画审核 |
| [ADR 0003](docs/adr/0003-activity-stack-runtime.md) | Activity栈与共享引擎生命周期 |
| [U01 前端重写](docs/UI_REWRITE.md) | HyperOS 参考对应、界面结构、交互与新截图 |
| [ADR 0002](docs/adr/0002-api33-blur-facade.md) | Miuix API33 磨砂与低版本界面的隔离边界 |
| [调研来源](docs/RESEARCH_SOURCES.md) | 官方资料、上游源码、版本依据和未验证项 |
| [ADR 0001](docs/adr/0001-android-media3-vertical-slice.md) | 解释当前模拟器纵切为何暂用 Media3，并明确替换为 native 后端的条件 |

建议接手顺序：本文件 → STATUS → AGENTS → 技术设计 → 当前阶段的实施计划。每次只完成一个阶段或一个明确子步骤，持续更新 STATUS。

## 已决定的技术路线

| 层 | 选择 | 职责 |
| --- | --- | --- |
| Android 界面 | Kotlin + Jetpack Compose + Miuix | HyperOS 风格、动画、主题、播放手势、无障碍 |
| 系统集成 | Media3 Session + 自定义 Player 适配器 | 前台播放服务、通知、锁屏、耳机按键、媒体控制 |
| 当前可运行适配器 | `PlaybackBackend` + Media3 ExoPlayer | 本地文件真实播放、状态投影、MediaCodec 解码策略；临时纵切，详见 ADR 0001 |
| 可移植核心 | C++17 + 稳定 C ABI + JNI 薄桥 | 播放状态、命令队列、后端协调、PCM、质量策略 |
| 视频 | libmpv + FFmpeg + MediaCodec + libass | 硬件/软件解码、同步、字幕、倍速和渲染 |
| HiFi 音乐 | FFmpeg 解码 + 可旁路的 PCM 处理链 | 控制源格式，避免多余处理；与视频后端互斥运行 |
| Android 音频输出 | AudioTrack；Oboe/AAudio；后续 USB Direct | 系统 USB 位完美、共享兼容、尽力申请独占 |
| 本地数据 | Room + DataStore + SAF/MediaStore | 媒体库、历史、设置、合法的 URI 文件访问 |

选择 C++ 是为了直接接入现有媒体库，首版不新增 Rust/JNI/C++ 三语言边界。未来可以把纯策略模块换成 Rust staticlib，前端与 C ABI 不需要改变。桌面版的 Rust 并不是自研解码器，它实际封装了 libmpv；Android 会参考它的状态机和策略，重写平台部分。具体复用范围见技术设计第 2 节。

长期架构仍由 Media3 Session 负责系统集成，ExoPlayer 不作为最终 HiFi/native 播放实现；libmpv 的公开客户端接口也不被当作任意 PCM 输出回调，因此目标音乐后端仍单独使用 FFmpeg。为先在现有 Windows + MuMu 环境真实跑通端到端播放，本轮经 ADR 0001 批准临时使用 ExoPlayer 作为 Android 后端。E00 中播放命令/快照采用 `PlaybackBackend`，Android 视频 surface 仍由 Media3 PlayerView 绑定；native/JNI gate 通过后，替换引擎及这层 Android 渲染 glue，不能把过渡实现误当成 USB 位完美或跨平台核心。

## 音质目标与真实限制

“最佳音质”定义为：优先匹配源采样率和有效位深，减少不必要的重采样与 DSP，不削波，报告实际输出路径，并在设备不能满足要求时清楚说明原因。强制 192 kHz/32 bit 或申请低延迟并不自动提高音质。

需要分清四件事：音频焦点、输出流独占、PCM 未处理、端到端位准确。它们不能互相替代。AAudio 独占是可拒绝的请求；Android 14 的 USB BIT_PERFECT 依赖设备/厂商支持，不能保证所有手机内置 DAC 或蓝牙输出位准确。[Android AAudio](https://developer.android.com/ndk/guides/audio/aaudio/aaudio)、[Android USB 混音器属性](https://source.android.com/docs/core/audio/preferred-mixer-attr)。

计划提供兼容播放、优先独占、严格独占、优先原样输出、严格原样输出等可组合策略。严格模式不静默降级；USB/耳机失联默认暂停，禁止突然从扬声器出声。系统请求成功显示“系统报告位完美”，只有数字捕获验证过的设备组合才能显示“位准确已验证”。

USB Direct 是针对系统路径不支持时的进阶工程，需原生等时传输和 UAC 驱动；不能用几行 bulkTransfer 冒充。首版先完成系统 USB 和 Oboe 的能力探测，完整产品阶段再实施有白名单和真机证据的 USB Direct。DSD/DoP、MQA、蓝牙无损和原生 HDR 均不是默认保证。

## 产品功能范围

| 优先级 | 内容 |
| --- | --- |
| 首版必须完成 | 本地音乐/视频、媒体库、后台音乐、HiFi 输出探测与严格策略、软硬解切换、横竖屏自动判断/手动切换、长按临时 3×、双指连续缩放/旋转/平移、字幕与音轨选择、主题与动画、自定义手势和画面设置 |
| 完整产品后续阶段 | 合格 USB Direct、EQ/ReplayGain/无缝专项、PiP、网络 URL/HLS/DASH、自有本地弹幕导入、高级字幕/画面配置、HDR 专项资格 |
| 尚未纳入 | 哔哩哔哩登录/内容 API/抓取/DRM 绕过、在线账号同步、直播推流、移动端系统全覆盖 |

“参考哔哩哔哩”默认指播放器交互，并不意味着接入其内容服务。双指连续旋转是必需功能：默认 SDR 呈现采用可变换的 TextureView；原生 SurfaceView 保留为功耗/HDR 专项路径，不能依靠未经验证的 SurfaceView graphicsLayer 实现旋转。

已由用户确认：普通小米/红米手机配 USB DAC、本地音视频优先。Android 8.0/API 26 起，arm64-v8a 首发，x86_64 用于模拟器；具体手机、ROM 和 DAC 型号尚未给出，没有真机不能宣称通过硬件验收。UI 不使用小米私有系统 API，也不复制其商标或受限字体。

## 当前工程与构建

当前包名与 applicationId 均为 `yy.daniel.yyplayerforandroid`，不因重构随意更改。当前只包含 `:app`，使用 AGP 9.4.1、Gradle 9.6.0、Kotlin/Compose compiler 2.4.20、Compose BOM 2026.09.00、Miuix 0.9.4、Media3 1.11.1，minSdk 26、compileSdk/targetSdk 37。源码目标 Java 11；本机运行 Gradle 的 Java 为 25.0.3，二者不是同一概念。

这些版本已从候选落到当前 Android 纵切依赖；MuMu 实际安装/运行与可视验收结果写入 STATUS。它们不代表最终 native 依赖、设备兼容性或 HiFi 资格通过。Miuix 官方 Android artifact 使用 `miuix-ui-android`，Compose BOM 选稳定 2026.09.00；Gradle 9.6.0 保持不变。

当前构建与检查命令：

```powershell
Set-Location E:\SourceFiles\androidstudio\YYPlayerforAndroid
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell monkey -p yy.daniel.yyplayerforandroid 1
```

本机 `local.properties` 指向 `E:\DeveloperTools\AndroidSDK`，不要把机器路径提交为公共配置。已发现 NDK 28.2.13676358 与 CMake 3.22.1；规划以它们为候选锁定。引入 native 后，全部依赖必须支持 16 KB 页面，不能只重编 JNI 外壳。[Android 16 KB 页面要求](https://developer.android.com/guide/practices/page-sizes)。

后续 native 库由受控 Linux CI 构建，Windows 下载并校验本项目 runtime 包，再使用 Android Studio/Gradle 编译应用。mpv-android 上游明确不提供可直接依赖的 AAR，而且其原生脚本不支持 Windows/WSL；不能让接手者照抄一个不存在的 Maven 坐标。[上游集成说明](https://github.com/mpv-android/mpv-android/blob/master/README.md)、[原生构建说明](https://github.com/mpv-android/mpv-android/blob/master/buildscripts/README.md)。本项目构建脚本目前尚未实现，不把计划命令写成可用入口。

MuMu 验收样片由 scripts/Generate-MumuFixtures.ps1 生成；执行命令为 powershell -ExecutionPolicy Bypass -File .\scripts\Generate-MumuFixtures.ps1 -PushToEmulator。脚本需要 PATH 中支持 lavfi、libx264 和 AAC encoder 的 FFmpeg，以及已连接的 adb。默认将文件写入 %TEMP%\yyplayer-mumu-fixtures，并推到 /sdcard/Movies/YYPlayerQA/；系统选择器中打开 MuMu 内部卷 V2364A > Movies > YYPlayerQA。脚本只操作这个测试目录，不扫描用户媒体。

新版 UI 证据：[音乐库](docs/validation/ui-v2/music-light.png)、[视频库](docs/validation/ui-v2/video-library.png)、[浅色设置](docs/validation/ui-v2/settings-light.png)、[深色设置](docs/validation/ui-v2/settings-dark.png)。其余实测证据与操作结果见 STATUS。E00 截图保留为历史播放证据，不代表新版布局。

## 交付与许可证方向

桌面参考项目位于 `E:\SourceFiles\rust\yyplayer`，调研时是 `dev` / `ec55789`，采用 GPL-3.0-only。Android 首版建议沿用 GPL-3.0-only，便于复用相关逻辑和选择 libmpv 的 GPL 构建；这是规划建议，Android 当前尚未添加正式 LICENSE。若未来改变发行方式，先审查实际代码和 native 依赖许可证，再作决定。

正式 APK/AAB 交付必须包含可复现构建记录、第三方 notices、对应源码安排、原生依赖闭包和真机结果。源码编译成功、UI 截图、DAC 屏幕上的采样率，都不能代替位准确或 HDR 证明。当前实现仅在本地工作区与 MuMu 验证，不自动提交或发布。
