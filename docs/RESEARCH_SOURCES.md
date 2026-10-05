# 调研依据与未决技术关卡

调研日期：2026-10-04。下面优先引用Android官方文档、库作者仓库和发布资料。选型/产品默认/实现接口/阶段预算是本项目工程判断，不是上游对所有设备的保证。版本冻结时继续读取对应tag源码，main/stable链接只能帮助理解。

## U03 真机布局补充依据

2026-10-05，用户授权读取小米17 Pro Max的系统设置/相册UI并调试已安装debug包；设备ADB型号2509FPN0BC，Android17/API37，density480。实际系统条目左边界与Miuix BasicComponent默认16dp核对；相册取材只保留底栏局部。准确行边距、字重、FloatingNavigationBar与NavigationBarItem组合方式见 [U03说明](UI_REAL_DEVICE_REFINEMENTS.md)。这只是指定真机布局证据，不推断USB HiFi或全部OEM转场能力。

## U02 窗口/动画补充依据

用户提供5张HyperOS窗口截图及完整只读 `references/miuix-0.9.4/`。本轮直接对照该版本中文WindowDialog、WindowSpinnerPreference、icons文档，AppContent悬浮栏和DialogSection示例，并核对WindowDropdownPopup、Dropdown、DialogContentLayout、ListPopupLayout、PagerGestureUtils、NavigationBar与IconButton源码。准确API是WindowDialog，不能拼接旧版SuperDialog示例。新增Android icons/preference模块同为0.9.4，实际构建成功。

组件/原生参数/截图对应见 [U02说明](UI_COMPONENTS_AND_MOTION.md)。独立Activity是用户明确要求；U02 MuMu只验证栈和前台播放生命周期，没有证明HyperOS厂商动画。具体结果见STATUS中的U02历史记录，当前反馈见顶部U03。

## U01 前端补充依据

用户提供的 `references/` 11 张 HyperOS4 截图，以及只读 Raidian 的 `UiKit.kt`、`FloatingNavBar.kt`、页面/主题/磨砂源码，决定了本轮视觉。官方 [Miuix 仓库](https://github.com/compose-miuix-ui/miuix) 与本机锁定的0.9.4 source JAR 用于核对 TopAppBar、Scaffold、Slider 和 LayerBackdrop/textureBlur 签名，不用最新 main 的 API 猜测当前版本。

实际读取0.9.4 blur AAR manifest 与 `LayerBackdrop`、`RenderEffectCompat.android`、RuntimeShader 能力探测源码：manifest min33，需要 [ADR0002](adr/0002-api33-blur-facade.md) 的隔离方案。所读 `miuix-blur-android-0.9.4-sources.jar` SHA256 为 `6E260B8FEC768315F5B689AE4A5F944AADABDC76437F4CAA10D5B4F93ED90A0A`；版本没有升级。以下初始工程事实保留为规划时快照，当前实现以STATUS顶部U03为准。

## 1. 本地事实来源

| 来源 | 查明事实 |
| --- | --- |
| Android `gradle/libs.versions.toml` / wrapper / `app/build.gradle.kts` | AGP9.4.1、Gradle9.6.0、Compose compiler2.2.10、BOM2026.02.01、min26/target37、Java源码11 |
| Android `MainActivity.kt` / `ui/theme/Theme.kt` / manifest | Greeting + Material3模板，没有实际播放/服务/存储权限 |
| Android git status/branch | master无初始提交，模板未跟踪；文档新增不会覆盖既有源码 |
| Rust README/AGENTS/STATUS/Cargo manifests | Rust/Slint/libmpv、GPL3-only、Windows开发预览、USB位准确/HDR未合格 |
| Rust `player-core/src/` / `player-mpv/src/worker/` | 纯模型/EQ/LRC可借鉴，但PathBuf、WASAPI、render lease需Android适配 |
| Rust `third_party/mpv/manifest.json` | Windows git runtime e470f8986e / libmpv-2.dll，与Androidnative工件不能互换 |
| 本机只读环境检查 | SDK E:\DeveloperTools\AndroidSDK，Java25.0.3，NDK28.2.13676358/CMake3.22.1已安装 |

参考Rust提交：dev / `ec55789`；参考仓库仅读取，未编译或修改。其文档有历史版本环境信息，本机rustc实读1.99.0，不把旧注释当当前工具链。

## 2. Android 音频

| 主来源 | 用于本项目的结论 |
| --- | --- |
| [AAudio官方指南](https://developer.android.com/ndk/guides/audio/aaudio/aaudio) | 独占可请求且可能拒绝/断开；实际sharing要读回；不等于所有DSP旁路 |
| [AudioMixerAttributes API](https://developer.android.com/reference/android/media/AudioMixerAttributes) | API34引入，DEFAULT/BIT_PERFECT需要独立能力判断 |
| [Improve audio playback](https://developer.android.com/media/platform/improve-audio-playback) | USB mixer配置与播放format必须匹配；位完美不进行软件混音/音量/效果 |
| [AOSP preferred mixer attributes](https://source.android.com/docs/core/audio/preferred-mixer-attr) | BIT_PERFECT是可选厂商HAL能力，不因Android14版本就保证有 |
| [Oboe仓库](https://github.com/google/oboe) | Android C++音频封装，作为系统原生sink选择 |
| [Oboe1.11.0发布](https://github.com/google/oboe/releases/tag/1.11.0) | 候选版本，尚未在本工程编译 |
| [UsbRequest API](https://developer.android.com/reference/android/hardware/usb/UsbRequest) | 公共UsbRequest处理bulk/interrupt，不是UAC等时驱动 |
| [USB host](https://developer.android.com/develop/connectivity/usb/host) | UsbManager权限/connection是USB原生访问入口 |
| [libusb Android说明](https://github.com/libusb/libusb/wiki/Android) | 授权FD wrap可作为无root入口，不能推导任意内核claim都成功 |
| [FFmpeg resampler](https://ffmpeg.org/ffmpeg-resampler.html) | 固定SRC profile需核对AVOptions并测delay/drain和质量，不自动加SRC |
| [W3C EQ Cookbook](https://www.w3.org/TR/audio-eq-cookbook/) | RBJ滤波公式/系数与Q/shelf语义的主来源，性能与数值稳定性需本项目测试 |

工程判断：系统USB路径先做，Oboe独占次之，USBDirect白名单作为专业后续阶段。未处理PCM和端到端capture两层证据、严格条件AND、失联暂停策略都是本项目要求。

## 3. 视频、渲染与系统会话

| 主来源 | 用于本项目的结论 |
| --- | --- |
| [libmpv/mpv仓库](https://github.com/mpv-player/mpv) | 跨平台媒体库、FFmpeg/libass等依赖及GPL/LGPL构建方向 |
| [mpv0.41.0发布](https://github.com/mpv-player/mpv/releases/tag/v0.41.0) | 候选stable，Android AAudio与默认renderer变动需要显式配置核对 |
| [mpv stable manual](https://mpv.io/manual/stable/) | hwdec/mediacodec/copy与常规控制语义；实现必须用冻结版本对应文档 |
| [mpv video-rotate](https://mpv.io/manual/stable/#options-video-rotate) | 不把无copy硬解任意角旋转当必然；TextureView变换是本项目选择 |
| [mpv-android README](https://github.com/mpv-android/mpv-android/blob/master/README.md) | 它是应用参考，不是可导入的AAR模块；支持手势/软硬解/字幕的实际先例 |
| [mpv-android BaseMPVView](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt) | Surface生命周期接入参考，同时源码有detach竞态FIXME，不能盲抄 |
| [mpv-android MPVView](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVView.kt) | Android GPUcontext/手机profile/实际hwdec-property的参考 |
| [mpv-android JNI render.cpp](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/jni/render.cpp) | Java Surface GlobalRef作为Androidwid的实际参考，需要lease和版本验证 |
| [mpv-android JNI main.cpp](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/jni/main.cpp) | JavaVM/FFmpeg Android上下文注册的参考，API按锁定版本核对 |
| [Media3支持格式](https://developer.android.com/media/media3/exoplayer/supported-formats) | 平台codec/软件extension边界；ExoPlayer并不自动涵盖所有软件视频解码 |
| [Media3自定义组件](https://developer.android.com/media/media3/exoplayer/customization) | 可定制renderer/sink，但本项目首版减少第二套解码器 |
| [SimpleBasePlayer API](https://developer.android.com/reference/androidx/media3/common/SimpleBasePlayer) | 用真实native状态适配标准Player/systemsession |
| [MediaSessionService后台播放](https://developer.android.com/media/media3/session/background-playback) | service/session/manifest与系统集成路径 |
| [Media3稳定发布](https://developer.android.com/jetpack/androidx/releases/media3) | 调研时1.11.1候选，2026-09-10更新；不是本项目构建通过证据 |
| [系统PiP](https://developer.android.com/develop/ui/views/picture-in-picture) | 原生PiP生命周期/窗口流程需要专项实现 |
| [Android16大屏变化](https://developer.android.com/about/versions/16/behavior-changes-16) | target36起大屏方向限制改变，target37不能依赖临时opt-out |
| [Android15目标行为](https://developer.android.com/about/versions/15/behavior-changes-15) | foreground/focus/boot相关限制需处理 |

工程判断：首版SDR默认TextureView以保障双指任意角和UI overlay；SurfaceView另做功耗/HDR资格。官方资料与上游先例不能代替本工程Surface/JNI/硬解组合实测。

## 4. Miuix 与工具链

| 主来源 | 用于本项目的结论 |
| --- | --- |
| [Miuix仓库](https://github.com/compose-miuix-ui/miuix) | Apache2、实验API、主题与模块，依赖包装集中 |
| [Miuix文档](https://compose-miuix-ui.github.io/miuix/) | 组件用法入口；需要按版本查具体参数 |
| [Miuix0.9.4发布](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4) | 模块/导航/API变化，minSdk及blur能力与版本要求 |
| [v0.9.4 version catalog](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml) | Kotlin2.4.20、ComposeMultiplatform1.12.0、AGP9.4.1的上游矩阵 |
| [v0.9.4 miuix-ui构建](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/build.gradle.kts) | KMP artifact依赖关系；Androidvariant解析必须单独核对 |
| [AGP/Gradle配套表](https://developer.android.com/build/releases/about-agp) | AGP9.4最低Gradle9.6.0，模板无需无故升级wrapper |
| [AGP9内置Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes) | built-in Kotlin与更高KGP对齐；旧kotlin-android插件规则不可照搬 |
| [Kotlin/AGP兼容](https://developer.android.com/build/kotlin-support) | Kotlin新版本需对应D8/R8能力，最终依赖矩阵需编译 |
| [Compose BOM映射](https://developer.android.com/develop/ui/compose/bom/bom-mapping) | AndroidCompose版本需要实际BOM映射，不以CMP插件版本猜编号 |
| [Compose compiler/Kotlin](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) | Kotlin2+使用匹配compiler插件，不能照旧compilerExtensionVersion教程 |
| [16KB页面](https://developer.android.com/guide/practices/page-sizes) | 全native依赖与打包都需匹配，NDK28只是工具链起点 |
| [nlohmann/json3.12.0](https://github.com/nlohmann/json/releases/tag/v3.12.0) | C++控制协议候选header-only库和产物hash，严守版本与解析边界 |

网页工具未能读取Maven POM/metadata端点；因此本轮没有声称某个新BOM/Miuix实际artifact依赖解析已通过。S00用Gradle和实际artifact完成冻结。此限制不改变已确定的Miuix库选择。

## 5. 原生构建与发行

- [mpv-android buildscripts说明](https://github.com/mpv-android/mpv-android/blob/master/buildscripts/README.md)：原生构建独立于Gradle，当前说明Linux/macOS支持、Windows/WSL不支持。本项目选择Linux受控native工件与Windowsapp开发。
- [FFmpeg发布](https://ffmpeg.org/download.html)：调研时9.0.2与8.1.3等维护分支存在；选择8.1.3作为较保守候选，并需与mpv0.41.0实际编译验证，不能由最低FFmpeg要求推断所有后续版本兼容。
- [FFmpeg许可](https://ffmpeg.org/legal.html)：构建选项决定许可闭包；不使用nonfree构建，发行需要实际组件notices和源码安排。

Android许可证方向暂建议GPL3-only，与参考Rust已选择许可一致；没有在本轮添加正式LICENSE或实施发布。C++核心分层只是保留移植空间，不是跨平台资格证明。

## 6. 必须保留的未验证清单

1. S00：实际Miuix Androidartifact、KGP/Compose/BOM/Room/KSP稳定依赖、AGP与JDK本机/CI组合。
2. S02：libmpv/FFmpeg版本与TLS/字幕/native依赖闭包、JavaVM/Surfacebridge、TextureView任意角硬解、所有so16KB装载。
3. S07/S08：具体小米/红米ROM与USB DAC的BIT_PERFECT/实际Exclusive、rate/bits、焦点/息屏/拔插/竞争。
4. S09：物理多指手势、取消路径、内容方向元数据、API37大屏/折叠/多窗口。
5. S11/S12：gapless样本、DSP正确、UAC等时driver/feedback/长期drift、数字capture。
6. S13/S14：HDR实际输出、网络TLS/PiP/弹幕、正式发行的源码/许可/签名/ABI闭包。

“未验证”必须保留至实测证据到位；不靠更强模型、更多注释或UI截图变为“支持”。
## E00 MuMu 运行时观察（2026-10-04）

同一脚本生成的 H.264 样片在 MuMu Android 15/API 35 上成功走过 `OMX.qcom.video.decoder.avc` 与 `OMX.google.h264.decoder` 两条路径。MuMu 对 Google decoder 的 `hardwareAccelerated`/`softwareOnly` 标记均为 false，应用因此以标准软件 codec 名称前缀作为环境兼容回退，并在界面提示系统标记不完整。Media3 文档已提示硬件/软件能力标记可能是近似值；运行时 decoder 名称可帮助本次诊断，但依然不能证明实体设备上的硬件加速或最终输出链路。此条是本项目模拟器观察，不是 Android 平台普遍保证。
