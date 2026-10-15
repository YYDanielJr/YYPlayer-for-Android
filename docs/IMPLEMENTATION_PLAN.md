# 分阶段实施手册

2026-10-15 U26／0.1.5：将原主页共享封面动效提取并应用到所有带控制条页面，包含搜索流播放、同页返回、重建保留和联网歌词页内切歌。以 [U26](validation/music-u26/README.md) 和 STATUS 记录的构建／真机证据为准；不扩展为全应用单 Activity 导航重写。

2026-10-15 U25／0.1.4：按用户反馈将 U24 底部占位播放栏改为覆盖真实页面内容的悬浮层；列表末尾可滚出遮挡区域，固定操作按钮单独避让。验证覆盖实际内容重叠、手指滚动、触摸不穿透、键盘及既有封面回归，详见 STATUS 和 [U25](validation/playback-u25/README.md)。

2026-10-15 U24：在 U23 搜索返回栈上补充所有二级页面的共享播放栏与重新设置入口，页内动画与播放栏分离，键盘／底部按钮避让。版本保持 0.1.2，代码与本轮验证见 [U24](validation/playback-u24/README.md) 和 STATUS；跨 Activity 仍保留 ROM 转场，不扩展为全应用单 Activity 重构。

2026-10-15 U22／0.1.2：横屏音乐左列封面与控制、右列完整歌词，去除重复小封面并保留 U14 动效；侧栏设置增加 Miuix 图标。libmpv 输出参数按元数据旋转换算显示宽高，VO 内容不重复旋转。当前 dev 基线为用户提交的 `31fece7`，U22 尚未提交；一加平板 Pro 实测与边界见 [U22 验证](validation/tablet-u22/README.md)。以下带日期章节记录历史状态。

2026-10-14 U21：实体机 libmpv 输入改为授权 `fd://` 租约；视频控制器精简，新增可配置单指手势与后台有限预览。Media3 视频短缓冲／低延迟模式、mpv AAudio 共享输出决定与限制见 [ADR0009](adr/0009-video-rate-response.md)，实测见 [U21](validation/video-u21/README.md)。音乐路径未变；本轮未提交，不能将下面早期计划覆盖当前工作区。

2026-10-14 U20：工具内视频倍速统一 0.1×–8×／步进 0.1×，外侧快捷档位保留。Media3 与 libmpv 范围一致，移除 U19 的超过 8× 自动切换和仅硬解专用范围。用户授权提交到 dev；见 [ADR0008](adr/0008-video-speed-range.md) 和 [U20 验证](validation/video-u20/README.md)。

2026-10-14 U16 更新：用户授权继续桌面视频功能对齐，已接入可选 libmpv，补章节、逐帧、时间偏移、libass 与 deband；见 [U16 验证](validation/video-u16/README.md)。这不是完整 S02／native 默认切换完成，音乐 FFmpeg／Oboe 与物理设备资格仍按原计划待验。

## 当前基线与接续（2026-10-14）

本轮新增未提交 U15 视频体验：全窗口变换／还原、3× token 与触感、轨道／外置字幕、比例／截图、A–B／视频播放顺序。37 项 JVM 与 MuMu 17 项专项及音乐回归已通过；仍有桌面后端能力与物理设备待验，见 [视频体验与差异](UI_VIDEO_EXPERIENCE.md)。这不代表 S02／S09 整体完成。

当前分支 `dev`，功能提交 `607ceb3`（U11–U14）；此前 HiFi 升级 `c75b34d` 已合入。所有新修改留在 dev，master 仅接受授权合入。先读 [STATUS](STATUS.md) 最新记录和 [AGENTS](../AGENTS.md)，不能直接从旧 S02 建议开始下一轮。

| 范围 | 已实现／证据 | 后续边界 |
| --- | --- | --- |
| H01–H05 | C++ PCM 核心、JNI、自研 AudioTrack sink、真实 MediaSessionService；[交接](HANDOFF_HIFI.md) | Media3／MediaCodec 仍为解码后端，FFmpeg／libmpv／Oboe 未接入；独占／位准确仍需资格验证 |
| U11 | 分类与歌单、三种队列模式、同路由宽松回退、双语歌词样式；[验收](validation/music-u11/README.md) | 严格模式不能静默回退，断连不能转扬声器 |
| U12–U13 | 独立队列、多选／拖动、整组圆角、52dp／50dp 按钮、精简首页、透明控件误触修复；[验收](validation/ui-u13/README.md) | 保留固定页头、唯一播放器与既有数据 |
| U14 | 收回弹簧、共享层停留底栏、快速重开；[验收](validation/ui-u14/README.md) | 覆盖 U13 无回弹曲线；录屏不代替 release 帧率，手感待用户复核 |

最近验收：有线小米 13 Ultra `c9e352e3`／API36／OS3.0.308.0.WMACNXM。U14 构建、34 项 JVM、lint 通过（2 既有警告），U13 5 项真实播放 instrumentation 通过；本轮 USB 被有线调试占用，无 DAC。Compose Activity suite 在该 ROM 上未完成，API26–32、物理多指、横屏／大字体／TalkBack／深色与 release 性能仍待专项。

下一动作以用户新指令为准。设备测试先枚举连接、install -r 保留数据，用直接 instrumentation，不使用会卸载应用数据的 connectedDebugAndroidTest；MuMu 仅在相应专项中补验。下方 E00、S00–S14 是早期纵切记录和长期计划，完成状态以本节和 STATUS 为准，不能重建第二套 service 或把旧 UI 恢复回来。

## 历史实现覆盖：E00 模拟器纵切（2026-10-04）


E00 跨接 S00/S01 与部分 S03/S04/S06/S09，只用来达到本轮真实播放和 UI 验收：

1. 锁定 Miuix 0.9.4、Kotlin/Compose compiler 2.4.20、Compose BOM 2026.09.00、Media3 1.11.1；对 Android app 用官方 `miuix-ui-android` 坐标。不得再引用旧 `miuix` 坐标。
2. 保留唯一 `PlaybackBackend`，接入 `AndroidMedia3PlaybackBackend`。播放器实际消费 SAF 授权 `content://` URI；不要求用户全盘存储权限。UI 从 StateFlow 显示播放状态、文件名、进度、输入采样率/声道和系统实际报告的视频 decoder 名称。
3. Video MediaCodec Selector 依据模式过滤真实 decoder：Auto 保持系统候选顺序；PreferHardware 硬解优先且可回落；StrictHardware 仅硬解并关闭 decoder fallback；Software 使用系统 softwareOnly 标记，并兼容名称明确属于 Android 平台软件 decoder 的 OMX.google.* / c2.android.*。不要把选择策略当作 Surface/芯片加速正确性的证据；运行时显示实际 decoder 与可用标志。
4. UI 使用 Miuix `NavigationBar`、`Surface`、`Slider`、`Button`、`Text`；四页为音乐/视频/播放/设置。主题 SYSTEM/LIGHT/DARK 和 reduce-motion 持久化。Miuix 过场不等于完整无障碍验收。
5. 视频页面提供每媒体方向自动判断与手动方向覆盖；TextureView 使用单一手势循环处理长按临时 3×、双指缩放/旋转/平移，倍率限制 0.5–5×；松手/取消/多指切换要恢复基础速度。模拟器可以验证触屏事件和窗口方向，不能替代真机画质/耗电验收。
6. 用程序生成的合成 WAV、H.264/AAC 横屏与竖屏样本，从 MuMu SAF picker 导入播放；记录 SHA256、app build、SDK、系统版本、安装/启动和画面截图。
7. README/AGENTS/STATUS 声明 USB BIT_PERFECT、AAudio exclusive、FFmpeg PCM、后台 Session、JNI/libmpv 尚未实现。当前 AudioFormat 仅显示 Media3 input format；API/hardware output format 是 Unknown。

### E00 验收记录（2026-10-04）

- 环境：Windows 工作站；MuMu adb serial emulator-5554；Android 15 / API 35；设备枚举 Xiaomi 2304FPN6DC；显示物理尺寸 1080×1920，截图随当前方向为横/竖屏。
- 文件变更：Miuix 页面、SAF 文件库/持久 URI、Media3 playback adapter、硬软 codec policy、TextureView 播放 surface、自动/手动方向、主题与动效偏好；增加 scripts/Generate-MumuFixtures.ps1。
- 构建检查：.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4，BUILD SUCCESSFUL；5 项 decoder selection 单测通过；lint 通过。Kotlin 尚有 Media3 unappliedRotationDegrees 弃用 warning，不影响本轮功能。
- MuMu 实测：从 SAF picker 导入 44.1 kHz / stereo WAV，播放页显示输入采样率、声道、真实进度；同一 H.264/AAC 横屏片段分别启动 OMX.qcom.video.decoder.avc（硬件）与 OMX.google.h264.decoder（软件），播放器显示匹配 decoder，画面持续更新。MuMu 给 Google decoder 同时报告 hardwareAccelerated=false、softwareOnly=false；实现用准确名称兼容并在 UI 显示“平台软解（系统标记不完整）”，不把 false 伪装为系统软件标记。
- 方向/主题：竖屏样片 360×640 自动保持竖屏；手动方向可从横转竖或竖转横；手动横屏后恢复 Auto 能根据竖屏比例回竖屏。浅色、深色切换即时生效；设置/媒体授权保留在应用重启后。
- 可复现样片由 fixture 脚本生成并推送：WAV 64F80E999CE6B3D2805FD5A2466F248BC59B900543BD83F6F082A9B6161918DB；横屏 MP4 15752209EE46CDA0271949C2F806CE19C7554181B89066A8F19B1588DA5838B7；竖屏 MP4 2830840578CD76AE4A8867928EFC33FB3B40B33C9D2C1FAB12397CAC70F562E7。
- 截图位于 docs/validation/：mumu-home.png、mumu-audio.png、mumu-video-hardware.png、mumu-video-software.png、mumu-video-portrait-auto.png、mumu-settings-light.png、mumu-settings-dark.png。
- 未验：USB DAC / Android BIT_PERFECT / AAudio Exclusive / AudioTrack 实际输出 format；C++/JNI/libmpv/FFmpeg/Oboe；API26/实体小米手机；多指旋转和长按事件的物理 MuMu 自动化；字幕/多音轨/codec 格式矩阵/后台播放；release 签名与 16 KB native 装载。当前界面明确显示 native/USB 输出能力未知。
- 下一阶段：S02 native 供应链和 JavaVM/TextureView gate；E00 adapter 保留作功能对照，在 native 证据到位前不移除。

## 0. 执行方式与依赖图

用户已明确首版优先本地音视频，主要设备为小米/红米 + USB DAC。未给 DAC 型号/ROM/设备的能力用 Unknown，不因此停止可以独立完成的工作。

规划基线依赖顺序（E00 已先行完成，详见下方验收记录）：

```text
S00 工具链/Miuix
 → S01 契约与样例界面
 → S02 native 构建/视频与输出技术 gate
 → S03 URI 真实访问
 → S04 真实基本视频
 → S05 系统服务/生命周期
 → S06 FFmpeg 真实音乐
 → S07 USB 系统位完美与输出策略
 → S08 Oboe 独占
 → S09 全部视频交互
 → S10 媒体库/主题/性能：首版验收
 → S11 DSP/无缝
 → S12 USB Direct
 → S13 网络/PiP/弹幕/HDR专项
 → S14 正式发行资格
```

S09 的手势纯逻辑和 S10 视觉工作可以在 native 真机等待期间进行，但界面不得显示假播放或假质量状态。没有硬件时 S07/S08 的自动化逻辑可完成，设备资格仍待验证。S12 不得被“优先独占申请成功”提前替代。

每个阶段开工时写 STATUS：任务 ID、输入条件、要改的文件、验收；做完记录命令/结果/素材hash/限制/下一步。分成 1–3 个可检查子步骤，每个子步骤都能编译或有清晰的独立 gate。不要为了赶阶段把必需功能替换为 TODO/mock。

## S00：可复现工具链与 Miuix 最小接入

目标：在现有模板上得到可运行的真正 Miuix 界面，冻结依赖矩阵，确认后续 native 的环境。

操作：

1. 查看 `git status --short`、现有 catalog/Gradle/manifest；保护未跟踪模板与用户修改。保持包名、minSdk26、target/compile37，不创建新项目替换原工程。
2. 执行 `gradlew --version` 与模板 assemble，记录实际 JVM/Gradle。JAVA_HOME 如果不适配只改本次进程/项目配置；不改系统环境、不安装未知 JDK。当前本机 Java25.0.3，SDK为 E:\DeveloperTools\AndroidSDK。
3. 查询 Miuix v0.9.4 Android variant 和 sources，先把 Kotlin compiler/Compose compiler 对齐到候选2.4.20；按 AGP9 built-in Kotlin 文档处理实际 KGP，禁止额外添加旧 kotlin-android 插件。
4. 查官方 BOM/真实 Maven metadata，选与 Miuix Android 依赖兼容的 Compose稳定组合，完整固定到 catalog；更新过旧 activity/lifecycle/core/test依赖，记录版本依据。不从“2026-10”猜出 BOM 编号。
5. 添加 `miuix-ui` 与必要 icons/preference 同版本，创建 `ui/theme/YYTheme.kt` 与 `ui/components/MiuixSmokeScreen.kt`（此时先在 app 包内）；实际 theme/card/button/switch/settings row 都从 sources 确认 import和参数。
6. MainActivity 换成该 smoke screen；使用真实 Miuix light/dark colors，不依赖 miuix-blur、不写播放伪状态。以普通 Toggle切明暗并验证系统跟随，不在产品中展示工具链说明。
7. `dependencyInsight` 确认 Compose runtime、foundation、Kotlin stdlib实际解析版本；编译与截图通过后把最终矩阵写入 `docs/validation/toolchain.md`。保留 wrapper checksum，未来补 Gradle dependency verification/lock。

验收：Debug编译、unit/lint通过；API26与API34+能启动；截图显示Miuix组件、中文不截断、切明暗有效；未调用native时无UnsatisfiedLinkError。无法连接设备则明确“编译通过，启动待验”，阶段设备部分保留未完成。

## S01：协议、视觉骨架与设计系统

目标：UI 与引擎契约稳定，让后续接入真实引擎不改每个页面。

文件：先在 app 内增加 `model/`、`playback/`、`ui/designsystem/`、`feature/`；接口稳定后抽 `core:model` / `core:designsystem`。没有功能的空模块暂不建立。

操作：

1. 实现MediaRef、Command、Snapshot、AudioRequest/Report、OutputPlan、VideoTransform与分类错误；duration/未知格式使用nullable，字段说明与单位齐全。
2. 定义 `PlaybackGateway`：StateFlow不可变快照 + suspend/非阻塞submit，返回requestId/Accepted，不暴露mpv Player或Context。UI发意图，不能自己更新Playing/Exclusive。
3. 仅Preview/测试可使用 `PreviewPlaybackGateway`，数据清楚标Preview。应用正式入口没有引擎时显示未连接/空库，而不是模拟进度。
4. 完成音乐库、视频库、音乐大页、视频控制层、设置的预览；使用统一YYTheme/Spacing/Shape/Motion，Miuix imports集中在designsystem，strings.xml收中文文案。
5. 设置schema区分sharing/transparency/主题/动画/手势；用DataStore稳定类型/JSON版本化方案，校验范围、旧数据默认、坏数据备份恢复。
6. 定义C ABI v1文档与JSON golden fixture；包含requestId/mediaGeneration/revision，字段映射不要先写生成器。fixture与Kotlin/C++各自解码兼容。

验收：模型无Android/播放器依赖；schema迁移、未知duration、严格条件AND、有损源标签等测试；五个页面360/600/840dp、明暗/大字体可预览；现有app仍能编译。

## S02：原生供应链与高风险技术 gate

目标：先证实“Android libmpv + TextureView + 任意旋转”和“PCM sink + 真实能力探测”可以工作；不先做一整套漂亮UI再发现内核不可接入。

文件：`native/`、`playback/native/`、`third_party/runtime/manifest.json`、`scripts/`、`docs/validation/native-gates.md`。脚本是本阶段要新建的，不是当前已有命令。

操作：

1. 建纯C++17静态core和最小JNI shared target；core只依赖标准库/本项目contracts。主机CTest先只验证handle registry/queue/释放，JNI smoke验证create/getVersion/close/重复close。
2. 冻结NDK28.2.13676358、CMake3.22.1、arm64-v8a/x86_64、API26、libc++_shared；runtime独立Linux构建。上游mpv-android脚本作为研究资料，不自动执行其安装脚本，不使用Windows/WSL运行它。
3. 创建本项目 `scripts/native/build-runtime.sh`，在固定Linux runner/container中按依赖拓扑编译FFmpeg、字幕/字体依赖、mpv与Oboe；固定source SHA/下载hash/patch。候选mpv0.41.0+FFmpeg8.1.3若不兼容先记录编译错误与处理，不引入两个FFmpeg runtime。
4. 产物每ABI包含headers/libs/notices/source-manifest，并列出SONAME、DT_NEEDED、SHA256。创建 `scripts/Get-NativeRuntime.ps1`：下载到工程缓存、校验hash再解压、防路径穿越、原子替换；不依赖全局PATH中的播放器库。
5. 创建CMake IMPORTED targets与Gradle externalNativeBuild，确认所有`.so`来源唯一。readelf检查ELF页面对齐、依赖闭包，APK zipalign与16KB装载另验；不能仅检查libyyplayer.so。
6. 实现JavaVM/Surface GlobalRef租约的最小mpv video probe：播放自有H264视频，software与mediacodec分别运行；显示实际hwdec-current。TextureView.setTransform试15°/45°/90°、0.5/2/5×，核对画面/字幕/颜色和触点矩阵，不做CPU回读。
7. 实现AudioTrack PCM tone probe和Api34 mixer查询页面（仅debug）；试源44.1/48/96支持组合、set/query/routing、拒绝后清理。实现Oboe exclusive probe并读取实际sharing/format/device；不存在设备不造数据。
8. 技术gate发现硬解/TextureView不兼容时，先试GPUcontext/mediacodec-copy受控降级并记录证据；不能把连续旋转取消。确需Render API方案写ADR、独立GL线程/FBO，重新验证硬解映射后再改默认。

验收：Debug/release JNI装载、两ABI构建、真实视频/任意角和音频probe；native首轮许可证/版本闭包；Surface连续attach/detach20次不崩；没有硬件则gate记Partial，不写已通过。

## S03：URI、文件权限与原生源租约

目标：真实本地文件能经Android授权进入native，不依赖外部存储绝对路径。

操作：

1. ActivityResultContracts.OpenDocument/OpenDocumentTree入口，按返回实际flags持久授权；拒绝/取消不覆盖当前播放。
2. `MediaAccessRepository`在IO线程打开ParcelFileDescriptor，probe seek/length/offset；`NativeSourceLease`按owned FD明确生命周期。native重复open或pread处理probe，不能用dup共享offset进行并行seek。
3. FFmpegAVIO实现read/seek/AVSEEK_SIZE、length未知、取消和provider错误；mpv选择经过gate的fd://或mpv_stream_cb_add_ro；清楚区分API原名与本项目包装。
4. 测试文件名空格、中文、emoji、引号、长名；所有native命令用argv数组，不拼shell字符串。URL/路径只作数据。
5. 失权/删除/云端非seekable分别返回错误；非seekable首先顺序播放，不自动把整文件复制到cache。用户选择缓存导入才启动有上限任务。
6. Manifest只加当前需要权限与ACTION_VIEW MIME入口，外部intent验证scheme/授权；不申请全盘/录音/全局设置权限。

验收：SAF本地、可seek provider、pipe fixture、撤销授权、关闭时读阻塞取消；FD在100次打开/关闭后无持续增长；损坏源不拖住主线程。

## S04：真实视频、解码政策与可恢复切换

目标：可以完整播放本地视频，具备真实软硬解和正确状态。

操作：

1. `MpvVideoBackend`只在worker做初始化/command/event投影，callback只wakeup；日志分类，不把某条warning当初始化结果。
2. 监听实际pause/time-pos/duration/EOF/buffering/tracks/hwdec/decoder/video aspect；统一映射状态。Ended→seek0→播放能再pause，避免桌面已出现过的EOF重播状态漏洞。
3. 实现播放/暂停/stop/seek/基础速度/轨道与字幕；seek只有一个当前request，EOF由明确backend事件驱动，不能UI位置达到duration就next。
4. Auto/PreferHardware/StrictHardware/Software及copy许可完整模型；切换事务保存/恢复位置、playIntent、轨道identity、速度、字幕延迟和变换。实际降级原因可见。
5. TextureView与surface generation接入，在surface有效后load；重建确认旧VO停止再释放GlobalRef/SurfaceTexture，不照抄上游FIXME竞态。
6. 取消快速打开：A→B→Stop、无音轨、音频附图、0时长/未知时长、坏视频都不残留旧画面/旧状态。

验收：H264/HEVC/VP9/AV1按设备实际支持记录；软硬切换20次位置误差目标<500ms、paused保持；StrictHardware不偷偷soft；EOF重播后暂停位置冻结；静态彩条/网格截图视觉核对（仅自有fixture）。

## S05：后台服务、系统控制与生命周期

目标：引擎与Activity分离，息屏音乐/通知/锁屏/耳机按键正确。

操作：

1. `YYPlaybackService : MediaSessionService`在Application容器创建唯一EngineController/session；活动只连接MediaController/PlaybackGateway，不创建第二个引擎。
2. `NativeSessionPlayer : SimpleBasePlayer`只在applicationLooper操作；state从真实快照构建，命令future在匹配Ack时完成/失败；只宣告实际支持Player.Commands。
3. 明确queue和next唯一归native controller，Media3 adapter不另做自动next；自定义命令如decode/audio策略验证controller权限与输入。
4. 接入焦点、becoming noisy、device callback、服务前台资格与错误；申请焦点失败不继续出声。StrictUnprocessed遇duck暂停；视频/音乐切换时不重复焦点管理。
5. 更新manifest foregroundServiceType/权限；实现通知metadata/封面/控制，通知权限拒绝/Android15服务限制可恢复。
6. Activity recreation、导航、息屏不destroy service引擎；视频无surface的pause/用户显式音轨后台策略统一；onTaskRemoved/onDestroy关闭顺序真实执行。
7. partial wake lock按需、keepScreenOn仅视频，焦点/错误/停止释放；不通过忽略电池优化权限强迫用户保持后台。

验收：耳机播放暂停、锁屏seek/next、10min息屏、来电/其他应用焦点、划掉app、Activity重建50次；只有一个session/engine，无ANR或重复音频；OEM限制记录型号/ROM。

## S06：独立 FFmpeg 音乐与兼容 AudioTrack

目标：音乐无需视频render即可播放，拥有可核实PCM输出的基础。

操作：

1. `FfmpegAudioBackend`做probe/send/receive/drain/seek/cancel；只为音乐使用，不从视频拆轨。metadata+kind识别真实audio/attached_pic。
2. 实现PcmFormat、ring、AudioTrack writer与timestamp clock反馈；DirectByteBuffer预分配，短写不丢样本，frame/byte换算有溢出检查；seek generation清旧buffer。
3. 普通AudioTrack sink稳定播放WAV/FLAC/ALAC/MP3/AAC；源/API/硬件格式分栏，硬件不可得为Unknown。
4. 建integer passthrough pack与DSP入口旁路；先通过PCM边界fixture再尝试真机24bit。没有必要不调用swresample。
5. EngineController video→audio切换先确认mpv AO释放，再开启音乐sink；audio→video反向。确认音乐状态不创建mpv render/context。
6. 音乐基本控件、后台、队列/循环/歌词接口接真实快照；暂时没有音乐变速/DSP/gapless资格不显示完成。

验收：源PCM与sink前样本逐样本相等；16/24/32整数packing边界、planar/interleaved、EOF尾帧、seek准确、时间回绕、ring欠载/取消；音乐30min不崩且与视频互切无重叠出声。

## S07：系统 USB BIT_PERFECT 与严格音频策略

目标：主要小米/红米 + DAC组合按真实能力选择源格式，不伪造独占/位准确。

操作：

1. `AudioCapabilityProbe`输出revision、route/format/mixer列表；所有API34调用隔离。路径不存在或HAL无BIT_PERFECT时记Unavailable并解释。
2. `OutputPlanner`实现sharing与transparency条件AND、同route fallback、rate/有效bits优先；对每个拒绝原因写可读错误。
3. 系统USB配置按技术设计第7.2节完整顺序执行set→build→route/attributes验证→开始→监听→cleanup。
4. 普通软件音量/EQ/ReplayGain/变速与strict raw互斥。用户启用原样模式时显示具体限制/当前音量处理，不能自动setVolume1造成突然变响。
5. 路由改变/拔出立刻暂停flush，撤销evidence；失权/忙/不支持严格时不转扬声器或静默降grade。
6. 输出页显示Requested/SystemReported/CaptureVerified/Unknown等状态，与独占sharing各自独立；媒体主页面只给简洁状态和详情入口。

验收：模拟能力矩阵包含API33/API34无bitperfect/API34合法/route改变/set返回false/StrictExclusive+StrictUnprocessed不可兼容；真机44.1↔48↔96、24/32容器与DSP限制；dumpsys辅助记录，不把它当数字捕获。

## S08：Oboe/AAudio 尽力独占

目标：设备允许时真实Exclusive，拒绝时按策略解释/暂停/同设备回退。

操作：

1. `OboeAudioSink`实现open/start/pause/flush/close，复用ring；无JNI/alloc/mutex callback；onErrorAfterClose交由worker处理。
2. 设置sharing/sample format/device后读取实际结果；严格Exclusive未得到Exclusive必须拒绝，不能因为open成功就显示独占。
3. 输出planner只为匹配能力选择Oboe；未知route/转换结果拒绝严格原样。允许回退时保持同route，格式或processing变化更新report。
4. 输出时间戳/underrun/断开统计接快照；长暂停释放Exclusive后resume重新probe，保证lease不占着设备不用。
5. 做竞争、通知焦点、屏幕关闭、断开重试；所有restore都检查generation/playIntent。

验收：实际SharingMode/format/device日志和设备报告；Exclusive被拒绝的宽松/严格行为；普通/实时线程检查；30min和100次close/reopen；没有真机无法标Exclusive资格Done。

## S09：完整视频手势与方向策略

目标：用户明确要求的四项交互全部真实生效。

操作：

1. `OrientationPolicy`纯函数计算有效display ratio（注意rotation是否已包含），阈值1.10/0.90；每media一次、manual优先，大屏/window bounds兼容。
2. `GestureArbiter`实现单一状态机、touchSlop、控件/边缘优先；单/双击、seek/亮度/音量基本行为先做可回放pointer测试。
3. temporarySpeed token：绝对3×、释放回最新base；第二指、cancel/导航/focus/PiP/media变化/surface loss统一结束。
4. `VideoTransformController`按viewport矩阵更新TextureView；限制scale/可见bbox、centroid锚点、归一化pan、resize恢复/吸附；不得直接每帧JNI set普通mpv参数。
5. 设置页保存长按倍率/手势开关/跳转长度/吸附/fit/隐藏时间；VideoScreen控件锁定、字幕/音轨/硬解按钮、恢复默认真实生效。
6. auto hide在拖动/长按/弹层/控件focus时暂停；遮罩透明度不改视频尺寸；系统返回优先退出sheet→unlock/exitfullscreen→navigation，尊重predictive back。

验收：base1×/2×/0.75×→hold3×→cancel恢复；hold后两指/快速换视频/松手旧token无影响；15°/45°连续旋转硬解画面正常；scale锚点漂移纯矩阵误差≤1px；横/竖/SAR/rotation/近方形/sw600/API37多窗口；物理双指必须真机确认。

## S10：媒体库、主题、动画与首版收口

目标：本地播放器可日常使用，界面符合Miuix/HyperOS方向，首版P0全部有证据。

操作：

1. `core:data`接Room/DataStore，MediaStore/SAF scanner有界/可取消；扫描不创建视频decoder；索引移除不删文件。权限失效单独标记并可再授权。
2. 封面/歌词后台读取，旧generation丢弃；20000媒体虚拟化和缓存上限；mini player、大页、库列表共用同一状态。
3. 完成Light/Dark/System与Classic/SoftGlass、强调色/系统动态色/封面色、reduceMotion/haptics；复核Miuix包装真实API，禁止主页面Material3替代全部组件。
4. 实现按压/控件/歌词/sheet/mini→大页转场，系统动画scale0立刻停持续动画；surface/video不参与CPU截图模糊。
5. 字体200%/TalkBack可操作、contentDescription/slider semantics/最低触控尺寸；没有媒体/权限/输出能力时空状态可恢复。
6. 做release性能与泄漏矩阵，具体设备/素材/配置记录。优化只有数据驱动；不为减少重组把真实状态更新断开。

验收：API26/34+/37、明暗/大字体360/600/840dp、物理视频与音乐完整路径、100次切换/Activity重建/30min播放、20k库滚动、息屏、拔插和严格失败；所有P0状态逐项列出。只有软件/模拟器完成可交“开发预览”，不能叫硬件合格首版。

## S11：DSP、ReplayGain、歌词与无缝专项

操作：

1. 校验并移植桌面EQ数据规格，C++固定滤波链/系数更新/平直旁路；调频受Nyquist约束、频段/增益有限、autoHeadroom/削波计数。
2. profile曲目>设备>全局完整覆盖，导入JSON/受限APO文本不执行Include或任意filter；验证后应用，失败回滚。
3. ReplayGain track/album模式和无标签fallback、峰值限制清楚；开启后report processing；strict raw禁用而不是偷偷修改样本。
4. LRC时间解析、offset/多时间戳/坏行/超大文件；无内嵌歌词时本地导入，权限lease与媒体generation一致。
5. MP3/AAC delay/padding与FLAC连续文件gapless fixture，sink前拼接无插零/重复样本；同格式留sink，不同格式透明重开。crossfade独立设置。

验收：滤波器稳定/峰值与平直恒等fixture、profile回滚、相邻源拼接样本/时间对比；真机gapless听感仅辅助，绝不代替PCM证据。

## S12：白名单 USB Direct

目标：补足系统USB路径不支持的组合，是单独的native驱动资格，不用占位USB开关代替。

操作：

1. 先读具体DACdescriptor，确认UAC版本/端点/clock/feedback；libusb固定版本、Android授权FD wrap gate，测试无root claim/release/拒绝权限。
2. `IUsbTransport`先mock descriptor/transfer，再UAC1 PCM stereo16/24；所有descriptor length/bounds/overflow检查，记录接口范围，不碰其他USB功能。
3. packet scheduler采用分数帧累加处理44.1kHz，解析bInterval/速度/maxPacket；UAC2显式异步feedback按设备规范实现范围/漂移限制，不能猜所有feedback都固定格式。
4. 预分配async transfer池、完成状态检查、cancel/drain、disconnect/timeout；stop后确认没有callback访问已释放内存，再release接口/closelease。
5. 增加24/32slot/clock和合法硬件volume控制；默认PCM白名单，不输出DoP。失败回系统路径必须遵守原strict设置并可见。
6. 30/120min播放、44.1/48/96/192切换、两个DAC、息屏/拔插/忙/竞争与数字capture；只有测过的ROM+DAC组合进入资格表。

验收：transfer/scheduler单测通过、权限/claim负例、真机与数字capture证据；无法运行时功能状态是Unavailable/Experimental，不能为满足界面将状态设为Exclusive/Verified。

## S13：PiP、网络、本地弹幕与 HDR 专项

按子任务逐个做，不混成一个“高级功能已完成”。

- PiP：系统入口/自动进入策略/比例/sourceRect/RemoteAction、temporary速度恢复、surface不双重持有、进出与方向重建。Android版本与OEM分别验。
- 网络：用户输入URL、http(s)白名单、TLS证书正确验证、超时/取消/headers类型化、range/seek/live未知duration；HLS/DASH做专项兼容，不宣称等同ExoPlayer所有流媒体能力。需要DRM以后增加隔离的ExoPlayerBackend ADR，不能绕过DRM。
- 本地弹幕：只导入用户文件，B站XML或明确JSON解析，关闭DTD/外部实体、文件上限16MiB/总条数200k、可取消；lane冲突、按真实position/speed重建、seek清屏、屏蔽词/透明度/密度/区域/字号。用Canvas批绘/文本缓存，不给每条弹幕创建独立无限Composable。默认60Hz，同屏上限200；无网络抓取。
- 高级字幕：字体授权/SAF、双字幕与延迟、SRT直立overlay和ASS行为分开；不拆除libass任意特效来假称完整支持。
- HDR：源metadata、HDR→SDR和原生输出分开，SurfaceView/EGL/实际display色彩gate；SDR参考图与HDR实际测量，不靠“hdr=yes”选项判断输出。

## S14：发行构建与闭包

操作：

1. GPL方向确定后加入完整LICENSE/THIRD_PARTY_NOTICES，对应source包/patch/build manifest可获取；实际runtime禁nonfree、完整组件许可闭包。
2. Debug与release行为一致，R8仅必要JNI/序列化keep；不写-dontwarn掩盖缺类，不对所有native/播放器包无限keep。
3. APK/AAB每ABI依赖/16KB/签名/schema升级/首次安装/升级/卸载验收；没有签名秘密进入Git或日志。
4. Linux native CI与Android app CI分别缓存hash键，上传symbol/source/runtime工件；dependency hash不符就失败，不自动用latest包补齐。
5. 功能资格表按设备组合公开已测/未测、保留开发预览标签，正式发布需用户指定发行渠道/操作，不擅自推送或上传商店。

## 检查命令与输出纪律

现有app的基础命令：

```powershell
Set-Location E:\SourceFiles\androidstudio\YYPlayerforAndroid
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
adb devices
```

新增模块后，执行 `gradlew projects` 核对真实任务名再写STATUS；不要复制当前尚不存在的模块任务。连接合适设备后用 `:app:connectedDebugAndroidTest`；release阶段加 `:app:assembleRelease` / `:app:lintRelease`，签名资格单列。

抽portable C++ target后，计划支持的主机检查入口（S02创建后才能用）：

```powershell
cmake -S native -B native/build-host -DYYPLAYER_BUILD_TESTS=ON
cmake --build native/build-host --config Debug
ctest --test-dir native/build-host -C Debug --output-on-failure
```

Android CMake通过Gradle传toolchain，不能用上述host命令当Android二进制验证。native报告可用ndk llvm-readelf/llvm-nm、zipalign、adb dumpsys media.audio_policy/media.audio_flinger/媒体会话与本项目结构化日志，先查真实工具路径。性能使用release和固定device；测试配置隔离，不改用户主题/音量/全局设置来造证明。

每次回归只跑与修改相关且必要的检查；文档改动只做UTF-8/链接/围栏/一致性/git diff检查，不为文档创建播放单测。代码/参数有变或未决风险才重复播放/硬件矩阵。
