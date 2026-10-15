# YYPlayer for Android

2026-10-15：用户已验收 **0.1.5**，U22–U26 累计实现与必要源文件纳入本次 dev 提交；下方各阶段“未提交”为历史记录。正式签名产物仍仅保留本地，不纳入源码提交。

U26／**0.1.5（versionCode 6）**：主页封面从控制条展开／收回的共享动画现用于所有带播放栏的页面。搜索、设置、队列、联网歌词和重新设置均在当前页展开，收回到当前控制条并保留页面；原磨砂视觉不变。见 [U26](docs/validation/music-u26/README.md)。

U25／**0.1.4（versionCode 5）**：播放栏改为真正覆盖在页面内容上的悬浮层。列表可从磨砂播放栏下方滚过，仅末尾增加避让留白；保留键盘上移、暂停／恢复和返回原页面。U24 的底部占位布局已替换，验证见 [U25](docs/validation/playback-u25/README.md)。

按后续反馈，所有页面播放栏统一恢复原主页磨砂视觉，已撤销新增阴影；真正悬浮的布局继续保留。

U24 将播放栏接入在线搜索、联网歌词、设置／方向／歌词样式／音频输出、播放队列与重新设置页。有当前媒体时可直接播放／暂停、查看进度、打开详情并返回原页面；完整封面和视频使用自身控制器。同 Activity 页内切换不带动播放栏，跨 Activity 保留 ROM 转场。版本仍为 0.1.2，见 [U24 验证](docs/validation/playback-u24/README.md)。

U23 追加在线搜索的返回栈：流式播放进入封面后，收起或系统返回会回到原搜索页，保留搜索词、结果和滚动位置，歌曲继续播放。主页打开的封面仍收回到主页。版本保持 0.1.2，详见 [U23 验证](docs/validation/navigation-u23/README.md)。

U22／**0.1.2（versionCode 3）** 修复一加平板横屏音乐重复小封面：左列为封面、歌曲信息与控制栏，右列完整留给歌词；侧栏设置入口增加图标。libmpv 修正旋转元数据对应的显示尺寸，解决横屏录屏被压窄。已在一加平板 Pro 验证，见 [U22 记录](docs/validation/tablet-u22/README.md)。

用户已将 0.1.1 提交至 `dev` 的 `31fece7`，并合入 `master` 的 `18fcb28`；本轮 U22 在 `dev` 修改，尚未提交。原有未跟踪 U21 文件与用户 release 归档保留。debug／release 继续按 arm64-v8a、x86_64 分包，release 启用全应用代码／资源优化与原生库压缩；命名、签名和验证说明见 [编译与签名](docs/BUILD_AND_SIGNING.md)。

视频已有全窗口双指变换／还原、3× 触感、字幕与音轨、A–B、截图和可选 libmpv 的章节／逐帧／libass／音画同步。横屏设置使用半透明右侧抽屉，支持实际上下滑动和屏幕内逐级返回。新增「播放工具 → 播放倍速」0.1×–8×，每次 0.1×，外侧快捷档位不变。Media3 与 libmpv 统一最高 8×，调节倍速不切换后端。见 [视频体验](docs/UI_VIDEO_EXPERIENCE.md)、[U20 验证](docs/validation/video-u20/README.md)、[ADR0008](docs/adr/0008-video-speed-range.md)。

基于 Kotlin、Jetpack Compose 和 Miuix 的 Android 本地音乐／视频播放器。U21 的 FD 输入、精简视频控制器、可配置手势与双后端变速继续保留，13 Ultra 历史证据见 [U21 验证](docs/validation/video-u21/README.md)。桌面 Rust 工程只读参考；下文带日期的旧版本与“未提交”描述属于当轮状态。

当前音乐体验包括歌曲／歌手／专辑／歌单浏览，顺序／单曲／随机播放，独立队列页面的单选／多选移除与长按排序，以及双语歌词字号、字重和原译间距。主页保留固定页头、顶部标签和仅有播放／暂停的底栏；“全部音乐／收藏”快捷卡片已移除，收藏从“歌单 → 我的收藏”进入。

页面操作按钮统一 52dp／18sp，弹窗 50dp／17sp、中等字重；歌曲列表仅整组外侧圆角。封面从左下角展开／收回，均保留轻微回弹；收回后共享绘制层继续显示底栏封面，避免终点换图。9° 角落按压缓慢跟随且无触底回弹，透明详情不再误触弹窗。最新行为见 [音乐体验](docs/UI_MUSIC_EXPERIENCE.md)，录屏见 [U14 验收](docs/validation/ui-u14/README.md)。

播放仍使用 Media3／MediaCodec，已有真实 MediaSessionService、可移植 C++ PCM 核心、系统与自研 AudioTrack 输出路径。非严格自研输出故障可在同一设备仍连接时回退 SYSTEM；严格模式拒绝静默降级，断连不回退扬声器。视频可选 libmpv／FFmpeg／libass 按 ADR0007 实现；音乐 FFmpeg PCM／Oboe 和端到端位准确仍未完成，见 [HiFi 交接](docs/HANDOFF_HIFI.md) 与 [ADR0006](docs/adr/0006-hifi-output-core-android-split.md)。

最近验收设备为**一加平板 Pro `bdba9db1`／API36／ColorOS16／OPD2404_16.0.5.1201(CN01)**；使用 `.qa` 共存包，保留用户 0.1.1 release 与数据。13 Ultra 的 U21 结果不自动覆盖其他设备。API26–32、完整无障碍／大字体、release 帧率和音频硬件资格仍待验；最新命令、结果和限制见 [STATUS](docs/STATUS.md)。

## 文档入口

在线音乐／歌词服务沿用两个默认关闭的独立开关，包含配置、搜索、歌词候选和下载路径，见 [在线 API](docs/ONLINE_API.md)。历史 U01–U10 文档保留背景与证据，若和最新行为冲突，以 STATUS、AGENTS 和音乐体验最新章节为准。

| 文档 | 用途 |
| --- | --- |
| [技术设计](docs/TECHNICAL_DESIGN.md) | 选型理由、可行性边界、模块接口、音视频算法、UI 与平台适配 |
| [实施计划](docs/IMPLEMENTATION_PLAN.md) | 当前 U11–U14 基线、待验事项及长期 S00–S14 计划 |
| [AGENTS.md](AGENTS.md) | 后续代码代理必须遵循的执行规则与禁区 |
| [进度记录](docs/STATUS.md) | 实际完成情况、证据和下一任务；接手先读 |
| [编译与签名](docs/BUILD_AND_SIGNING.md) | 0.1.5、双 ABI 独立 APK、release 体积优化与正式签名 |
| [U07 引导／库／控件](docs/UI_SETUP_AND_LIBRARY.md) | 首次设置、顶部标签、播放玻璃胶囊、歌词样式与长按批量管理 |
| [音乐体验 U05–U14](docs/UI_MUSIC_EXPERIENCE.md) | 当前歌单／队列／双语歌词／按钮与封面动效，以及历轮证据 |
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

## 当前实现与长期技术路线

| 层 | 选择 | 职责 |
| --- | --- | --- |
| Android 界面 | Kotlin + Jetpack Compose + Miuix | HyperOS 风格、动画、主题、播放手势、无障碍 |
| 系统集成（已实现） | Media3 Session + 共享 ExoPlayer | 前台播放服务、通知、锁屏、耳机按键、媒体控制 |
| 当前可运行适配器 | `PlaybackBackend` + Media3 ExoPlayer | 本地文件真实播放、状态投影、MediaCodec 解码策略；临时纵切，详见 ADR 0001 |
| 可移植核心 | C++17 + 稳定 C ABI + JNI 薄桥 | 播放状态、命令队列、后端协调、PCM、质量策略 |
| 视频（长期目标） | libmpv + FFmpeg + MediaCodec + libass | 硬件/软件解码、同步、字幕、倍速和渲染 |
| HiFi 音乐（长期目标） | FFmpeg 解码 + 可旁路的 PCM 处理链 | 控制源格式，避免多余处理；与视频后端互斥运行 |
| Android 音频输出 | AudioTrack；Oboe/AAudio；后续 USB Direct | 系统 USB 位完美、共享兼容、尽力申请独占 |
| 本地数据 | 当前仓储＋版本化 JSON／偏好＋SAF/MediaStore；Room/DataStore 为长期规划 | 媒体库、歌单、设置、合法的 URI 文件访问 |

选择 C++ 是为了直接接入现有媒体库，首版不新增 Rust/JNI/C++ 三语言边界。未来可以把纯策略模块换成 Rust staticlib，前端与 C ABI 不需要改变。桌面版的 Rust 并不是自研解码器，它实际封装了 libmpv；Android 会参考它的状态机和策略，重写平台部分。具体复用范围见技术设计第 2 节。

长期架构仍由 Media3 Session 负责系统集成，ExoPlayer 不作为最终 HiFi/native 播放实现；libmpv 的公开客户端接口也不被当作任意 PCM 输出回调，因此目标音乐后端仍单独使用 FFmpeg。为先在现有 Windows + MuMu 环境真实跑通端到端播放，本轮经 ADR 0001 批准临时使用 ExoPlayer 作为 Android 后端。E00 中播放命令/快照采用 `PlaybackBackend`，Android 视频 surface 仍由 Media3 PlayerView 绑定；native/JNI gate 通过后，替换引擎及这层 Android 渲染 glue，不能把过渡实现误当成 USB 位完美或跨平台核心。

## 音质目标与真实限制

“最佳音质”定义为：优先匹配源采样率和有效位深，减少不必要的重采样与 DSP，不削波，报告实际输出路径，并在设备不能满足要求时清楚说明原因。强制 192 kHz/32 bit 或申请低延迟并不自动提高音质。

需要分清四件事：音频焦点、输出流独占、PCM 未处理、端到端位准确。它们不能互相替代。AAudio 独占是可拒绝的请求；Android 14 的 USB BIT_PERFECT 依赖设备/厂商支持，不能保证所有手机内置 DAC 或蓝牙输出位准确。[Android AAudio](https://developer.android.com/ndk/guides/audio/aaudio/aaudio)、[Android USB 混音器属性](https://source.android.com/docs/core/audio/preferred-mixer-attr)。

当前已提供兼容播放、优先／严格独占、优先／严格原样等策略判定与输出选择，但选择策略不代表硬件满足要求。严格模式不静默降级；USB/耳机失联默认暂停，禁止突然从扬声器出声。系统请求成功显示“系统报告位完美”，只有数字捕获验证过的设备组合才能显示“位准确已验证”。

USB Direct 是针对系统路径不支持时的进阶工程，需原生等时传输和 UAC 驱动；不能用几行 bulkTransfer 冒充。首版先完成系统 USB 和 Oboe 的能力探测，完整产品阶段再实施有白名单和真机证据的 USB Direct。DSD/DoP、MQA、蓝牙无损和原生 HDR 均不是默认保证。

## 产品功能范围

| 优先级 | 内容 |
| --- | --- |
| 首版必须完成 | 本地音乐/视频、媒体库、后台音乐、HiFi 输出探测与严格策略、软硬解切换、横竖屏自动判断/手动切换、长按临时 3×、双指连续缩放/旋转/平移、字幕与音轨选择、主题与动画、自定义手势和画面设置 |
| 完整产品后续阶段 | 合格 USB Direct、EQ/ReplayGain/无缝专项、PiP、网络 URL/HLS/DASH、自有本地弹幕导入、高级字幕/画面配置、HDR 专项资格 |
| 尚未纳入 | 哔哩哔哩登录/内容 API/抓取/DRM 绕过、在线账号同步、直播推流、移动端系统全覆盖 |

“参考哔哩哔哩”默认指播放器交互，并不意味着接入其内容服务。双指连续旋转是必需功能：默认 SDR 呈现采用可变换的 TextureView；原生 SurfaceView 保留为功耗/HDR 专项路径，不能依靠未经验证的 SurfaceView graphicsLayer 实现旋转。

已由用户确认：普通小米/红米手机配 USB DAC、本地音视频优先。Android 8.0/API 26 起，arm64-v8a 首发，x86_64 用于模拟器；当前 UI 真机和历史 DAC 组合分别记录于 STATUS／HiFi 交接，不跨设备继承资格。UI 不使用小米私有系统 API，也不复制其商标或受限字体。

## 当前工程与构建

当前包名与 applicationId 均为 `yy.daniel.yyplayerforandroid`，不因重构随意更改。当前只包含 `:app`，使用 AGP 9.4.1、Gradle 9.6.0、Kotlin/Compose compiler 2.4.20、Compose BOM 2026.09.00、Miuix 0.9.4、Media3 1.11.1，minSdk 26、compileSdk/targetSdk 37。源码目标 Java 11；本机运行 Gradle 的 Java 为 25.0.3，二者不是同一概念。

这些版本已从候选落到当前 Android 纵切依赖；小米真机与历史 MuMu 安装／运行结果写入 STATUS。它们不代表最终 native 依赖、设备兼容性或 HiFi 资格通过。Miuix 官方 Android artifact 使用 `miuix-ui-android`，Compose BOM 选稳定 2026.09.00；Gradle 9.6.0 保持不变。

当前构建与检查命令：

```powershell
Set-Location E:\SourceFiles\androidstudio\YYPlayerforAndroid
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
adb devices -l
# 确认枚举仍是本轮有线手机后执行；不清除应用数据。
adb -s c9e352e3 install -r app\build\outputs\apk\debug\YYPlayer_for_Android_0.1.0_arm64-v8a_debug.apk
adb -s c9e352e3 shell am start -n yy.daniel.yyplayerforandroid/.MainActivity
```

本机 `local.properties` 指向 `E:\DeveloperTools\AndroidSDK`，不要把机器路径提交为公共配置。已发现 NDK 28.2.13676358 与 CMake 3.22.1；规划以它们为候选锁定。引入 native 后，全部依赖必须支持 16 KB 页面，不能只重编 JNI 外壳。[Android 16 KB 页面要求](https://developer.android.com/guide/practices/page-sizes)。

后续 native 库由受控 Linux CI 构建，Windows 下载并校验本项目 runtime 包，再使用 Android Studio/Gradle 编译应用。mpv-android 上游明确不提供可直接依赖的 AAR，而且其原生脚本不支持 Windows/WSL；不能让接手者照抄一个不存在的 Maven 坐标。[上游集成说明](https://github.com/mpv-android/mpv-android/blob/master/README.md)、[原生构建说明](https://github.com/mpv-android/mpv-android/blob/master/buildscripts/README.md)。第三方完整媒体 runtime 构建仍未实现；已有自研 C++ PCM／JNI 构建入口见 native/README.md，不把两者混为一谈。

MuMu 验收样片由 scripts/Generate-MumuFixtures.ps1 生成；执行命令为 powershell -ExecutionPolicy Bypass -File .\scripts\Generate-MumuFixtures.ps1 -PushToEmulator。脚本需要 PATH 中支持 lavfi、libx264 和 AAC encoder 的 FFmpeg，以及已连接的 adb。默认将文件写入 %TEMP%\yyplayer-mumu-fixtures，并推到 /sdcard/Movies/YYPlayerQA/；系统选择器中打开 MuMu 内部卷 V2364A > Movies > YYPlayerQA。脚本只操作这个测试目录，不扫描用户媒体。

U02 历史 UI 证据：[音乐库](docs/validation/ui-v2/music-light.png)、[视频库](docs/validation/ui-v2/video-library.png)、[浅色设置](docs/validation/ui-v2/settings-light.png)、[深色设置](docs/validation/ui-v2/settings-dark.png)。其余实测证据与操作结果见 STATUS。E00 截图保留为历史播放证据，不代表新版布局。

## 交付与许可证方向

桌面参考项目位于 `E:\SourceFiles\rust\yyplayer`，调研时是 `dev` / `ec55789`，采用 GPL-3.0-only。Android 首版建议沿用 GPL-3.0-only，便于复用相关逻辑和选择 libmpv 的 GPL 构建；这是规划建议，Android 当前尚未添加正式 LICENSE。若未来改变发行方式，先审查实际代码和 native 依赖许可证，再作决定。

正式 APK/AAB 交付必须包含可复现构建记录、第三方 notices、对应源码安排、原生依赖闭包和真机结果。源码编译成功、UI 截图、DAC 屏幕上的采样率，都不能代替位准确或 HDR 证明。当前功能已本地提交到 dev，并有小米真机及历史 MuMu 证据；没有因此自动推送或发布。
