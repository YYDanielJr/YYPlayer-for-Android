# Android 技术设计与选型

2026-10-14 U20：工具内视频倍速统一 0.1×–8×／步进 0.1×，外侧快捷档位保留。Media3 与 libmpv 范围一致，移除 U19 的超过 8× 自动切换和仅硬解专用范围。用户授权提交到 dev；见 [ADR0008](adr/0008-video-speed-range.md) 和 [U20 验证](validation/video-u20/README.md)。

2026-10-14 U16 更新：视频增加固定 libmpv-android AAR 的可选路径，使用独立 C++ worker／JNI／真实 SimpleBasePlayer 会话适配器；音乐仍走原有 Media3 与可选 native PCM sink。默认保持 Media3，最终供应链与物理资格仍未完成。具体决定见 [ADR0007](adr/0007-libmpv-video-adapter.md)，下文关于未引入 FFmpeg／libmpv 的早期纵切叙述属于历史基线。

## 当前实现边界（2026-10-14，dev 607ceb3）

- Android 解码／播放仍为 Media3 ExoPlayer／MediaCodec；`PlayerSessionService` 已提供真实 MediaSession，持有 runtime 唯一引擎引用。Activity 和队列页共用它，不再按早期 U02 的纯前台生命周期接手。
- 已有 C++17 PCM 核心、C ABI／JNI 和可选自研 AudioTrack sink；完整 FFmpeg／libmpv／Oboe runtime 未集成。输出策略、同路由宽松回退、严格拒绝与已知 native gapless 限制以 [ADR0006](adr/0006-hifi-output-core-android-split.md)、[HiFi 交接](HANDOFF_HIFI.md) 为准。
- U11–U14 的音乐分类／歌单／队列、双语歌词和封面动效以 [音乐体验](UI_MUSIC_EXPERIENCE.md) 最新章节为准。音乐详情仍在 MainActivity；队列是独立 MusicQueueActivity，和视频／设置一样采用系统 Activity 转场。
- 当前仓储使用版本化 JSON／偏好与 SAF 身份；下方 Room／DataStore、模块拆分和 FFmpeg 等是长期设计，不应描述为已部署。
- 验收为有线小米 13 Ultra／API36，见 [STATUS](STATUS.md)；历史 MuMu 与 DAC 证据限定于各自版本／设备。本文下方早期接口和 S 阶段保留为设计背景，不能覆盖当前基线。

## 早期设计背景

版本：规划基线与E00实测补充，2026-10-04；音乐重构及真机反馈2026-10-05补充。**当前音乐标签、主页内详情、封面色背景、歌词和动画以 [UI_MUSIC_EXPERIENCE](UI_MUSIC_EXPERIENCE.md) 与 [ADR0004](adr/0004-inline-music-player.md) 为准。音乐不再新开 Activity，视频／设置保留原生 Activity。弹窗反馈与导航回退以 [UI_DIALOG_FEEDBACK](UI_DIALOG_FEEDBACK.md) 为准；U03设置/字重历史见 [UI_REAL_DEVICE_REFINEMENTS](UI_REAL_DEVICE_REFINEMENTS.md)。U02窗口/图标/Activity及动效见 [UI_COMPONENTS_AND_MOTION](UI_COMPONENTS_AND_MOTION.md) 和 [ADR0003](adr/0003-activity-stack-runtime.md)；U01视觉基线见 [UI_REWRITE](UI_REWRITE.md)。本文早期骨架/接口仍属于规划。** 事实依据见 [调研来源](RESEARCH_SOURCES.md)，执行顺序见 [实施计划](IMPLEMENTATION_PLAN.md)。第三方API必须对照锁定版本源码实现。

2026-10-05 U07 用户新视觉与引导要求见 [UI_SETUP_AND_LIBRARY](UI_SETUP_AND_LIBRARY.md)。首次标记独立于偏好，SAF授权可选；竖屏库切换置顶，底栏仅真实播放；歌词样式与选中集属于UI数据投影。API33磨砂隔离与单一播放引擎保留。同日 U08 细化库页结构（用户已复核满意）：`HyperPage` 用 `headerAlpha: () -> Float` 延迟读取驱动顶栏淡入淡出、用 `tabs` 槽把 TabRow 放进 `TopAppBar.bottomContent`，页头因此留在 pager 之外、只有内容区横向切换；库页自持 `MiuixScrollBehavior`（把 `TopAppBarState` 提到共享会让滚动后栏内小标题不再出现），音乐／视频各持控制器与列表状态。

## 1. 需求转成可验收目标

2026-10-05 用户将联网音乐／歌词提前到 U06：JSON provider 请求／响应映射、独立在线搜索 Activity、音质可用性检查／默认 MediaStore Music 与自选 SAF 下载／自动专辑图／文件签名命名、两个默认关闭的联网开关和歌词候选。界面／平台服务仍 Kotlin，唯一共享 Media3 后端保持；详细接口与范围见 [ONLINE_API](ONLINE_API.md)、[ADR0005](adr/0005-configurable-online-music.md)。S13 早期网络规划不覆盖或限制这次授权；native／USB路线未改变。

| 用户目标 | 实现定义 | 不接受的替代品 |
| --- | --- | --- |
| 独占、最佳音质 | 能力探测、尽力/严格策略、源/API/设备格式分层、无额外 DSP 的通路、真机记录 | AudioFocus 当独占；固定升采样；请求成功即标位准确 |
| 软硬解自由切换 | Auto / PreferHardware / StrictHardware / Software；保存位置、暂停、轨道、速度后切换 | 开关只改文字；硬件失败却仍显示硬解 |
| 自动横竖屏 | 根据有效显示比例和元数据一次判断；手动选择优先；大屏自适应 | 单看文件宽高；每帧重新设置 Activity 方向 |
| 长按 3× | 仅按住期间覆盖为绝对 3.0 倍，所有结束/取消路径恢复原速度 | 原来 2×变成 6×；松手恢复 1×；失焦后一直 3× |
| 双指缩放旋转 | 连续 0–360°、缩放 0.5–5×、平移、重置、锚点稳定 | 仅支持 90°；仅旋转按钮；CPU 逐帧图片 |
| 小米风格与充分动效 | Miuix 真实组件、统一令牌、主题、弹性转场、减少动画模式 | 普通 Material3 全套换蓝色；视频背景逐帧截图模糊 |
| 后端可移植 | C++ 核心不依赖 Android/JNI/Compose，C ABI 管理身份和生命周期 | 所有播放逻辑放进 Activity；迁移时重写解码器 |

首版以本地文件为中心；网络、本地弹幕、USB Direct 和 HDR 都有后续具体阶段，不因分期而把它们标记为已实现。用户补充范围后更新表和阶段，不破坏已确认的首版必需项。

## 2. 已有项目审计与复用策略

Android 根目录仅有 `:app`，`MainActivity.kt` 显示 Greeting，Material3 模板主题；没有播放器、服务、媒体库或 JNI。Git 当前 master 尚无提交，模板文件大多未跟踪。实现时不 reset、不删除模板以外用户改动，不自动提交。

桌面项目：`E:\SourceFiles\rust\yyplayer`，调研提交 `ec55789`、分支 dev。workspace 分为 player-core / player-mpv / player-platform / player-ui / yyplayer-app。player-core 只依赖 serde/serde_json，但其媒体身份仍是 PathBuf，部分配置和快捷键带桌面语义；不是可以直接把 URI 字符串塞进去的 Android 公共契约。

| 参考文件 | 可以借鉴 | Android 处理 |
| --- | --- | --- |
| `crates/player-core/src/{engine,state,types}.rs` | 命令/快照、稳定队列 ID、错误分类 | 在 C++ 契约和 Kotlin  DTO 中重建；新增 URI/FD、输出证据和 surface generation |
| `audio.rs` | 严格/优先独占、EQ 校验、预留余量 | 提取规格与 fixture；不要把 `@yy-eq:lavfi` 字符串当通用 DSP 实现 |
| `lyrics.rs` | LRC 时间语义 | 等价移植纯解析，复用测试数据并记录来源 |
| `appearance.rs`、`settings.rs` | 明暗/主题色/减少动态效果独立、配置版本化 | Android DataStore/主题令牌；不移植 Slint 字体/窗口配置 |
| `player-mpv/src/worker.rs`、`worker/video_gate.rs` | 有界命令、Stop 保留槽、旧事件隔离、视频 gate | 重新实现 Android worker 与 surface token；不加载 Windows DLL |
| `player-mpv/src/worker/audio.rs` | 输出证据分层、EQ 回滚 | WASAPI 日志不能用于证明 AAudio 独占 |
| player-platform、player-ui、Windows presenter | 平台隔离思想 | 不复制 HWND、DWM、WASAPI、Slint/FemtoVG 纹理接入 |

桌面版独占已有开发预览；其 STATUS 明确 USB/数字捕获/HDR/跨平台尚未合格。不能从桌面“播放成功”推导 Android 能力。

首版选择 **Kotlin + C++17**，Rust 不进入构建依赖。原因：mpv/FFmpeg/Oboe 本身是 C/C++，增加 Rust 并不会让系统输出更位准确，反而多一套工具链和 FFI 生命周期。未来纯状态/解析模块可以用 Rust staticlib 替换 C++ 实现；保留 C ABI、错误码和 fixture，不把 serde 布局暴露出去。若以后明确要求共享 Rust 源码，先对 desktop core 去平台语义，单独写 ADR，再引入 cargo-ndk；本轮不修改参考仓库。

## 3. 候选方案与决定

| 方案 | 长处 | 对本任务的限制 | 决定 |
| --- | --- | --- | --- |
| Media3 ExoPlayer 为全部内核 | Android 集成、流媒体、硬解和省电成熟 | 软件视频解码覆盖取决于扩展/平台；复杂字幕/滤镜需要扩展；不会自动提供通用 HiFi 独占 | 保留为未来 DRM/流媒体专用适配器，首版不用它解码 |
| libmpv 为全部内核 | 桌面已用；格式、libass、软硬解和视频设置完整 | 公开 client API 不提供任意 PCM sink；Android AO 对输出格式/混音属性的闭环需另做 | 选为视频内核与兼容音乐原型 |
| 自建 FFmpeg + MediaCodec 视频引擎 | 控制强、可移植 | 需要自己做同步、字幕、HDR、GPU 生命周期，首版风险过大 | 不选 |
| libVLC / 旧 ijkplayer | 成熟播放器/已有移动方案 | 增加不同架构和发行依赖，未明显解决 HiFi；旧项目维护需额外审查 | 不选 |
| libmpv 视频 + FFmpeg 音乐 | 视频沿用成熟同步；音乐能控制 PCM 与 sink；同源 FFmpeg | 后端切换、native 构建较复杂 | 最终选择；互斥运行，不做视频音轨双时钟拆分 |

系统控制仍使用 Media3 Session；借助 `SimpleBasePlayer` 适配真正的 native 状态，不创建空 ExoPlayer 来骗取通知。[Media3 自定义 Player](https://developer.android.com/reference/androidx/media3/common/SimpleBasePlayer)。

**E00 时期的纵切例外见 [ADR 0001](adr/0001-android-media3-vertical-slice.md)：**现有 Windows 工程没有 native runtime，mpv-android 也没有可消费的 AAR。为了用户本轮“MuMu 前后端跑通 + UI 验收”，临时以 Media3 ExoPlayer 同时播放本地音频/视频，`PlaybackBackend` 为页面提供不暴露 ExoPlayer 类型的命令和 `StateFlow` 快照；由实际 `MediaCodecSelector` 策略允许 Auto、优先硬解、仅硬解、软件解码。严格硬解无候选时由真实播放错误反映失败。本阶段的后端只是 Android 适配器，不完成跨平台核心或 HiFi 输出资格。Linux native runtime、JNI/Surface gate 通过后，再按既定路线更换后端实现。

## 4. 工具链、依赖候选与冻结规则

所有“候选”都必须经过 S00/S02 构建，不代表本轮已验证二进制兼容性。已有版本和建议版本分别记录。

| 项目 | 当前/候选 | 锁定动作 |
| --- | --- | --- |
| applicationId/namespace | `yy.daniel.yyplayerforandroid` | 保持 |
| Android | minSdk 26、compileSdk/targetSdk 37 | 保持现有；API 34 音频属性用版本隔离；验证 37 大屏行为 |
| AGP / Gradle | 当前 9.4.1 / 9.6.0 | 保持，9.6 满足 AGP 9.4 最低要求；保留 wrapper SHA256 |
| Kotlin/Compose compiler | 已锁 2.4.20 | 保持 catalog 与 Compose 插件一致；AGP9 内置 Kotlin，不重复添加 kotlin-android |
| Compose runtime/foundation/ui | 已锁 BOM 2026.09.00 | 保持 catalog 与实际依赖一致，不凭日期编造版本 |
| Miuix | 已锁 0.9.4 | 使用 catalog 中官方 Android artifact `miuix-ui-android`；blur 按 ADR0002 隔离，不混旧坐标 |
| Media3 | 已锁 1.11.1 | common／session／exoplayer 等统一版本；当前实际依赖 ExoPlayer，UI 自绘 |
| NDK / CMake | 本机已安装 28.2.13676358 / 3.22.1 | Gradle、CI、全部第三方统一；不得临时混入旧 libc++ |
| C++ | C++17、libc++_shared | 一套 runtime，异常不穿越 C/JNI；业务错误用分类 Result |
| libmpv | 候选 v0.41.0 | 冻结 tag 对应完整 SHA、client API 2、头文件和编译选项 |
| FFmpeg | 候选 8.1.3 | 音乐和 mpv 链接同一次构建产物；若不兼容先修/记录 ADR，不装两套同 SONAME 的库 |
| Oboe | 候选 1.11.0 | 冻结版本、通过 Prefab 或受控源码集成；不与另一套 AAudio 循环混用 |
| 控制协议 JSON | C++ nlohmann/json 3.12.0；Kotlin kotlinx.serialization | header-only版本/hash固定，MIT notices；Kotlin插件与compiler对齐，禁止手写不完整JSON解析器 |
| Room/DataStore/Coroutines/Coil/导航 | S00 查询稳定发布与 minSdk | 在 version catalog 固定完整版本；Navigation Compose 优先，手势播放器不交给第三方导航手势 |

AGP 9 已内置 Kotlin，不照抄旧教程添加 `org.jetbrains.kotlin.android`；需要更高 KGP 时按官方 built-in Kotlin 方法调整实际 compiler 依赖。Compose compiler 插件与实际 Kotlin compiler 对齐。Android app 不因为引用 Miuix KMP artifact 就改成全项目 KMP；也不把 Compose Multiplatform plugin 版本直接当 Android BOM 编号。[AGP Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes)、[Miuix 0.9.4 版本目录](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/gradle/libs.versions.toml)。

Miuix APIs 处于实验阶段，项目只在 designsystem 中直接依赖/包装它。0.9.4 与旧版本模块/API 有变化，不能拼接旧 `miuix` artifact 示例和新 `miuix-ui` imports。先建立主题、按钮、卡片、设置项的最小可编译样例，再铺页面。首版不依赖 minSdk 33 的 miuix-blur，使用半透明渐变和预先模糊封面；以后确需真实 blur 时独立 API33 flavor/module，不以运行时 if 掩盖 manifest minSdk 冲突。[Miuix release](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4)。

## 5. 模块和目录

先在现有 app 内用包隔离，再按 S01/S05 抽模块，不在第一步创建几十个空模块。

```text
app/                        Activity、权限入口、导航、依赖装配
core/model/                 Kotlin DTO/命令/快照；无 Context 和第三方播放器类型
core/designsystem/          Miuix 包装、YYTheme、色彩/形状/动效令牌
core/data/                  Room、DataStore、MediaStore/SAF、封面与歌词
playback/android/           MediaSessionService、Player adapter、音频焦点/路由
playback/native/            JNI Kotlin 外观、Android C++ adapters、CMake
feature/library/            音乐/视频库及搜索
feature/music/              音乐大页、歌词、播放队列、输出卡片
feature/video/              视频控件、GestureArbiter、OrientationPolicy
feature/settings/           普通设置、设备设置和高级诊断
native/include/yyplayer/    public C ABI 与纯 C++ contracts
native/core/                EngineController、策略、队列、时钟状态
native/audio/               FFmpeg decoder、PCM ring、pack、可选 DSP
native/video/               libmpv backend；无 Android Surface 类型
native/platform/android/    JNI、Java Surface lease、Oboe sink、路由消息
native/tests/               不依赖设备的 CTest 测试与 fixtures
third_party/runtime/        manifest 与 notices；下载二进制忽略
scripts/                    受控获取、native 编译、依赖/16KB验证
docs/validation/            设备与功能验收报告
```

依赖方向：feature → core/model + designsystem；app 装配 feature/data/playback；playback/android → model + native；native/core → 抽象接口；Android API 只在 adapter。C++ portable target 在 Windows/Linux 主机能编译测试，Android 编译定义不能渗入 core。首版不用 Hilt，采用 Application 内明确的 AppContainer/构造器注入，降低 AGP/KSP 兼容变量；Room 所需 KSP 单独锁定。

## 6. 状态、接口与线程约定

### 6.1 单一状态源

Native EngineController 是播放队列、媒体 generation 和播放状态的唯一执行方；Android service 持有它，UI/ViewModel 不拥有引擎。平台输出能力/焦点通过消息输入，播放器真实状态通过不可变快照输出。

必须定义以下模型，字段可增加但语义不改：

```text
MediaRef: stableMediaId, kind(AUDIO/VIDEO/AUTO), displayName,
          sourceToken, seekable, knownLength?, accessRevision
PlaybackSnapshot: revision, mediaGeneration, queueRevision, activeBackend,
  phase(Idle/Opening/Ready/Playing/Paused/Buffering/Seeking/Ended/Error),
  playIntent, positionUs, durationUs?, speed, baseSpeed, tracks, selectedTracks,
  actualDecoder?, surfaceGeneration?, audioReport?, structuredError?
AudioRequest: sharing(Shared/PreferExclusive/StrictExclusive),
  transparency(Compatible/PreferUnprocessed/StrictUnprocessed),
  preserveRate, routeToken?, allowSameRouteFallback, dspProfile
AudioReport: sourceFormat, decoderFormat, apiFormat?, hardwareFormat?,
  routeToken?, sinkType, requestedSharing, actualSharing(Shared/Exclusive/Unknown),
  bitPerfect(Requested/SystemReported/CaptureVerified/Unavailable/Unknown),
  processing[], resampler?, underruns, evidenceId?, fallbackReason?
OutputPlan: capabilityRevision, routeToken, sinkType, sampleRate,
  channelLayout, sampleEncoding, processingPlan, policyResult
VideoTransform: scale, rotationDegrees, translationX/Y, anchor; 与媒体方向分离
```

未知 duration 是 null，不用 0 冒充直播或读取失败。播放位置用 64 位微秒；API 适配处明确转为毫秒/秒。队列 ID、数据库 ID、libmpv playlist ID 不混用。错误至少有 Unsupported / AccessRevoked / SourceGone / DecoderFailed / OutputUnavailable / StrictPolicyRejected / SurfaceLost / QueueFull / Cancelled / NativeFailure，附可恢复动作。

命令：Open、Play、Pause、Stop、Seek、SetBaseSpeed、BeginTemporarySpeed(token,3)、EndTemporarySpeed(token)、SetDecodePolicy、SelectTrack、ApplyAudioRequest、Attach/DetachSurface(token)、SetQueue、ApplyDsp、Close。每个命令有 requestId 和必要 generation；输入立即返回 Accepted/Rejected，真正完成以 Ack/Error 事件返回，Accepted 不是播放成功。

| 输入/实际反馈 | 状态处理 |
| --- | --- |
| Open接受 | generation递增，Opening；清旧轨道/错误/temporary override，保留新命令playIntent |
| probe/准备完成，playIntent=false | Ready/Paused；禁止UI自行Play |
| 已准备且输出确认运行 | Playing；位置以真实时钟为准 |
| Pause请求/内核确认 | playIntent=false；确认后Paused、外推时钟停止 |
| cache不足 | Buffering，保留playIntent，缓存恢复后按它恢复 |
| Seek接受/确认 | Seeking；清音频buffer/旧弹幕，完成后按playIntent返回Playing/Paused |
| 实际EOF | Ended；原队列存在下一项才由controller推进一次，不因下一tick再次next |
| Ended中Play | Seek0/reopen事务后进入Playing，之后Pause仍能生效 |
| Stop完成 | Idle、无输出/无surface使用；Stop前没完成的旧Open取消 |
| 严格输出失败/源失权/解码错误 | Error/Paused并携带原因和恢复动作，保留可续播位置 |

### 6.2 C ABI 和 JNI

- 对外暴露 opaque handle、版本号和固定宽度数字；不暴露 STL、mpv_handle、AVFrame、JNIEnv、C++ vtable 或 Rust 类型。
- 控制命令/快照第一版可用版本化 UTF-8 JSON（上限 256 KiB），PCM 用预分配 DirectByteBuffer，禁止用 JSON/byte[]逐帧传音视频。
- JSON解析只在控制worker进行，限制嵌套64层/字符串长度/数组元素；拒绝NaN/Infinity和未知必需枚举，requestId/generation按64位整数读，不经double。大型队列分批写入事务，不提高单条快照上限；输出轨道/队列列表使用revision和分页。
- 返回内存由谁分配就由谁释放：明确 `yy_buffer_free`；输入在函数返回前复制/取得所有权；句柄使用 registry/token 校验，避免 release 后从 jlong 解引用旧指针。
- JNI RegisterNatives 或显式 names 二选一后固定；只保留 JNI 桥的必要 R8 规则。JNI_OnLoad 保存 JavaVM，不跨线程保存 JNIEnv。
- 回调数据复制后再离开 mpv/FFmpeg 作用域。GlobalRef 与源 FD 使用 RAII；后台线程需要时 attach/detach JVM；不存在 Activity 强引用。
- 所有 native 异常/panic 在边界变成错误，不 abort、不用 exit；bridge close 可重复，且销毁等待在后台线程执行。

拟定义的最小C接口如下；这些是本项目契约，**不是mpv/FFmpeg已有API**。S01固定签名及JSON schema，S02实现，不直接复制缺少错误处理的外部wrapper。

```c
#include <stdint.h>
#include <stddef.h>
typedef uint64_t yy_handle;
typedef struct yy_buffer { uint8_t *data; size_t size; } yy_buffer;
uint32_t yy_api_version(void);
int32_t yy_create(const uint8_t *config_json, size_t size, yy_handle *out);
int32_t yy_submit(yy_handle h, const uint8_t *command_json, size_t size);
int32_t yy_poll_event(yy_handle h, yy_buffer *out); /* nonblocking */
int32_t yy_snapshot(yy_handle h, yy_buffer *out);   /* latest immutable copy */
int32_t yy_close(yy_handle h);                     /* worker-side cleanup */
void yy_buffer_free(yy_buffer *buffer);
```

头文件补include guard与C++下的extern "C"，导出可见性宏由平台定义，接口只导出规定符号。返回枚举定义YY_OK=0/YY_ACCEPTED=1/YY_EMPTY=2，负值为分类错误，完整枚举进头文件；不能统一用-1。out在进入函数时清零，失败也能安全free。poll事件有限队列，高频position走latest snapshot；Ack/Error在接受命令时预留结果slot，slot不足拒绝新普通命令，Stop/Shutdown独立保留，不用阻塞worker保住Ack。yy_close禁止主线程调用，bridge先撤销UI回调再后台执行；旧handle重复close返回已关闭，旧submit返回InvalidHandle而非访问悬空指针。

Android JNI另提供 `registerSourceLease`、`attachSurfaceLease`、`readPcmInto`、`reportSinkClock`、`reportRouteCapabilities`：在adapter注册token/FD/GlobalRef并交worker，这些平台参数不进入portable C头。portable C++接口为 `ISourceReader(read/seek/size/cancel)`、`IAudioSink(open/start/pause/stop/report)`、`IPresenterGate(ready/detachAck)`，用标准类型/稳定token，Android与未来OS各自实现。Kotlin只在writer线程调用readPcmInto，传DirectByteBuffer/合法capacity，返回字节数/formatGeneration；UI永不拉PCM。

控制链：UI → Gateway.submit → Service Looper校验 → JNI非阻塞submit → native worker → Ack/event → bridge复制并post Looper → Player.invalidateState/StateFlow → UI。音频数据不经过这条控制事件通道。

### 6.3 线程和缓冲

| 执行位置 | 允许 | 禁止 |
| --- | --- | --- |
| Compose/UI 主线程 | 交互、变换矩阵、快照投影 | 解码、扫描、阻塞 mpv/JNI、等待 native join |
| Service application Looper | Player API、焦点/路由、命令投递 | 音频写入、重建引擎时阻塞 |
| native engine worker | 普通 mpv API、状态机、切后端、资源控制 | 持 UI/音频回调要用的锁等待 |
| decode worker | FFmpeg packet/frame、可取消 I/O、转换 | 更新 UI、访问 Room |
| AudioTrack writer | 从 PCM ring 拉块、处理短写、上报 timestamp | 主线程运行；持锁做阻塞 write |
| Oboe realtime callback | 无锁读预分配 ring、补零并计数 | malloc、JNI、日志、磁盘、mutex、流重开 |
| mpv callback | 唤醒 worker | command、析构、等待、UI |

命令队列普通容量 64，Stop 有保留路径，Shutdown 原子唤醒；Seek/变换采用 latest slot；设置有 requestId，不能随意丢弃。事件高频位置最多 10 Hz，状态立即发布；UI 播放中可按 monotonic time 外推位置，暂停/seek/EOF 必须纠正，不能用动画时钟模拟真实进度。PCM ring 初始 250 ms，上限 1 s；格式切换前清空并递增 audio generation，内存按实际声道/位宽计算且有硬上限。

后端切换顺序：保存状态 → 暂停旧后端 → 停止/flush 输出并确认释放 → 关闭旧 decoder → 探测新源与计划 → 准备新后端 → 恢复位置/轨道/基础速度 → 根据原 playIntent 恢复。失败进入可恢复 Error，不让两个输出短暂混播。打开 B 后，A 的 EOF/封面/权限结果不得覆盖 B。

## 7. HiFi 音频实现

### 7.1 能力边界与策略

| 路径 | 可做 | 必须保留的限制 |
| --- | --- | --- |
| 普通 AudioTrack | 兼容播放、格式/设备请求、timestamp | API format 不代表物理 DAC format；通常可混音 |
| API34+ USB AudioTrack + mixer attributes | 查询支持组合并请求 BIT_PERFECT | HAL 选项可缺失；仅 USB 指定组合；不是通用流独占证明 |
| Oboe/AAudio | 请求 Exclusive、读取实际 SharingMode、原生回调 | 可退为 Shared、断开、系统 DSP 未知；低延迟与位准确分离 |
| USB Direct（S12） | 自行 UAC 控制与等时传输，绕过系统混音路径 | 内核/厂商/端点支持不同；需要权限、驱动与真机；不保证任意 DAC |
| 蓝牙/内置输出 | 高质量兼容、尽力协商 | 不承诺位准确；读取 codec/格式信息只能展示可得事实 |

无厂商私有 API、root、全局音效强关或修改系统混音配置。所谓 HiFi 是可验证的数据路径和设备适配，不是主观音质排名。

sharing 和 transparency 是两个独立条件，判定为 AND：StrictExclusive + StrictUnprocessed 要同时满足；BIT_PERFECT AudioTrack 不得被写成 Exclusive 来凑成功。默认 Shared + PreferUnprocessed、DSP 关闭、源格式优先、软件音量保留正常播放设置。用户选择严格原样输出时，先明确 DSP/音量的限制，禁止悄悄把原来较低音量直接设满。

选择计划：先过滤严格条件 → 在目标 route 查询合法格式 → 源采样率与声道优先 → 整数有效位深不降低优先 → 无 DSP/无需 SRC 优先 → 已验证设备路径优先 → 选择稳定缓冲。没有合法计划时严格策略暂停并显示原因；宽松策略只能回退同设备的已列明通路。多次设备失败不无限重试，保留显式重试按钮。

这里的StrictUnprocessed不能仅凭“App没有EQ”成功：至少有合法的系统BIT_PERFECT契约且实际route/format匹配，或合格Direct通路且整个软件/传输处理链原样；只有Oboe Exclusive但HAL处理未知时拒绝StrictUnprocessed。即使满足严格策略，UI仍区分系统/传输契约和数字capture实测，不显示未经capture的端到端Verified。

| 条件 | 确定结果 |
| --- | --- |
| USB有匹配BIT_PERFECT，Shared + StrictUnprocessed，无gain/DSP | 可选系统USB；独占状态仍Unknown，位完美显示SystemReported |
| USB有BIT_PERFECT，StrictExclusive + StrictUnprocessed，仅系统AudioTrack候选 | 不够证明Exclusive，拒绝；不伪造sharing |
| Oboe实际Exclusive，但HAL处理未知，StrictExclusive + Compatible | 可播放，显示Exclusive/原样未知 |
| Oboe实际Shared，StrictExclusive | 停止/暂停并报严格策略拒绝 |
| 有DSP/gain或需SRC，StrictUnprocessed | 拒绝并给关闭处理/换模式动作；不自己修改用户音量 |
| API33或USB无BIT_PERFECT，PreferUnprocessed | 同route兼容并显示原因；StrictUnprocessed拒绝 |
| 原始目标route已失联 | 暂停，无自动speaker fallback |

PreferUnprocessed不会自动取消用户的软件音量/EQ；存在这些处理就保留并解释“未启用原样”，由用户明确选择切换。初次接DAC提供“检测并设置”向导，列出支持的源格式/系统原样/独占能力和实际限制；确认后的每设备策略持久化，不每次播放重复询问。不把最高额定采样率当质量首选。

### 7.2 AudioTrack 系统 USB 路径

1. API34 前只进入兼容路径，所有 AudioMixerAttributes 引用放到 Api34 adapter，避免旧系统类加载。
2. AudioDeviceCallback 获取 USB 输出 AudioDeviceInfo，记录 capabilityRevision。设备 ID 是本次会话 token；持久化用设备描述的可用稳定字段，并允许多个同型号设备，不把会话 ID 存成永久身份。
3. 用 USAGE_MEDIA/CONTENT_TYPE_MUSIC 查询 `getSupportedMixerAttributes(device)`；按编码、采样率、声道布局和 BIT_PERFECT 筛选；不能自造设备没返回的组合。
4. `setPreferredMixerAttributes` 检查 Boolean；注册变更监听；用相同 AudioAttributes 与 AudioFormat 建立 AudioTrack，检查 STATE_INITIALIZED；请求 preferred device。
5. 开始后检查实际 routedDevice、实际 track format、属性状态；不同 route/属性变更立即撤销“系统报告位完美”。设置成功只是请求证据，待实际路径匹配后再升级状态。
6. Kotlin writer 用 DirectByteBuffer 读 native PCM，write 处理短写/错误/取消；只推进已经成功写入帧数。getTimestamp 可用时报告 framePosition/nanoTime，降级 playbackHeadPosition 时处理 32 位回绕和重建归零；不能拿“已解码帧数”当已听到的位置。
7. 停止按 pause/flush/release 清理；清除属于本播放器的 preferred attributes，解绑监听，不覆盖其他会话的新配置。

BIT_PERFECT 不做软件 gain/EQ/混音/SRC，硬件音量由 DAC 能力决定，UI 显示“设备调节”或不可用；不假造通用 USB 硬件音量 API。[平台实现边界](https://developer.android.com/media/platform/improve-audio-playback)。

### 7.3 FFmpeg 与 PCM

使用同一 FFmpeg 构建的 libavformat/avcodec/avutil/swresample；业务使用现代 send_packet/receive_frame 和 AVChannelLayout，不复制过时字段。packet/frame RAII；EAGAIN/EOF/drain/seek flush 分开处理，interrupt_callback 响应取消和超时。

无处理通路与 DSP 通路分开：无处理保留整数解码结果，只做有证明的等价 packing/interleave/扩展；DSP 在 float64/float32 中计算，随后显式量化输出。32 位整数源不能为了方便全转 float32；planar/interleaved 与 24-in-32/24-packed 不同，严禁按 bitsPerSample 猜排列。

packing fixture 至少覆盖零、±1、最小/最大值、正负随机样本、左右声道交替；验证 endian、符号扩展、FFmpeg 对齐规则以及负数位移/溢出的 C++ 定义，使用无符号位操作/显式转换。提升容器位宽不创造有效精度；降低位深才考虑 TPDF dither，且撤销未处理/位准确资格。

只在输出不接受源格式时用 swresample，记录输入/输出/算法与原因；strict preserveRate 失败不重采样。声道重排可以等价；downmix 不等价且必须报告。首版 strict PCM 验证覆盖无损整数 WAV/FLAC/ALAC，其他格式先做解码正确性；有损音频可称“解码后未处理”，不能称恢复无损源。

首次SRC实现仅使用swr高质量固定profile，候选filter_size=64、phase_shift=10、exact_rational=1、cutoff=0.97、linear_interp=1，逐项按锁定FFmpeg AVOptions核对返回值；相同rate/layout/packing可等价时旁路。测脉冲响应/扫频/alias/CPU/延迟并调整profile，不声称该profile主观上绝对最佳。必要时后续引入soxr质量profile做有口径对比，先锁库与许可再扩依赖；处理完尾部delay必须drain，seek/pause不把残留样本串入新媒体。[FFmpeg resampler](https://ffmpeg.org/ffmpeg-resampler.html)。

DSP 默认全关闭；EQ、ReplayGain、音量、crossfade、limiter、变速都改变样本。EQ 采用 named fixed chain，全平直直接旁路；选择优先级为曲目 > 设备 > 全局，完整对象覆盖、有限数校验、频率 < Nyquist、增益/预留余量与峰值检测。应用失败回滚整个 profile，不累加 filter。DSP 开启显示处理链并撤销原样资格；必须保持独占与 DSP 可以独立组合。

EQ实现采用RBJ biquad的Peak/LowShelf/HighShelf，系数按a0归一化、double计算、每声道独立状态；配置中的Q与shelf slope语义分开，不能把桌面lavfi的w字段盲当新库参数。后台计算完整新系数，音频块边界交换，增益改变做短时平滑以避免爆音；旁路不残留滤波器tail。用频率响应/稳定性/极值和已知fixture校验，不逐字照抄未经许可的实现。[W3C Audio EQ Cookbook](https://www.w3.org/TR/audio-eq-cookbook/)。

同格式 gapless 需按编码 delay/padding 准确裁剪并无额外零帧；不同采样率保持源格式会重开 sink，明确可能有间隙；crossfade 不是 gapless 证明。S11 完成专项前不展示“无缝已支持”。native 音乐首版 speed=1；若以后加入音乐变速必须用验证过的时伸缩实现，不能改变 sampleRate 冒充保调倍速。

### 7.4 Oboe 和失联

Builder 请求 AAudio、指定 route/sample rate/channel/format/Exclusive；实际结果从已打开 stream 读取 sharing/format/device。允许的格式转换与 Oboe SRC 由 plan 控制，无处理模式关闭转换或拒绝。音乐播放优先稳定缓冲，PerformanceMode::LowLatency 不是强制质量条件；实际 MMAP/AAudio path 不可获取时显示未知。

onErrorAfterClose 只提交恢复事件，不能在 realtime callback 关闭重开。route/格式发生变化先暂停、失效 audio generation、丢弃旧 PCM/临时速度，再决定新计划。USB/耳机拔出或焦点丢失暂停；严格原样模式遇 duck 请求选择暂停，不能靠缩放 PCM 实现 duck。普通模式可按焦点类型 pause/duck，恢复只有原 playIntent 且策略允许时执行。

系统可能自动重路由已排队音频；公开 API 不能保证插拔瞬间物理上零样本泄漏。实现必须第一时间 pause/flush，阻止继续向扬声器写入，并测量具体设备；不要把应用层默认暂停策略写成系统级绝对隔离保证。

### 7.5 USB Direct 的具体后续边界

先完成系统路径，S12 再引入 `IUsbTransport` 和 `UacOutputSink`。UsbRequest 官方只支持 bulk/interrupt；USB Audio 等时传输需 libusb/native usbfs，不能用 bulkTransfer。[UsbRequest](https://developer.android.com/reference/android/hardware/usb/UsbRequest)。

无 root 入口是 UsbManager 权限 → UsbDeviceConnection → 受控 FD → libusb_wrap_sys_device；在 JNI 层管理 connection 与 FD 租约。是否能 detach/claim 内核音频接口受设备限制，失败不要求用户改系统开关，也不自动接管所有 USB 类接口。

按 UAC1 同步/自适应基础 → UAC2 异步显式 feedback 的顺序实现：安全解析 descriptor、altsetting、clock source/entity、支持 rate/slot/effective bits/channel、endpoint interval/maxPacket、反馈精度和异常值、长期分数帧累加、异步 transfer 池、cancel/drain/release。44.1kHz 包长交替不能固定截断；长期 clock drift、欠载和包完成状态必须有计数。初始仅 PCM stereo 16/24/32 与白名单 DAC，扩展隐式反馈/多声道前另验。S12 是专业 native 子项目，单次回复不宣称完成通用驱动。

DSD over PCM/native DSD 延后；只有 DAC 声明/实测、精确 marker/位宽/速率、绝无 gain/SRC 的路线才能启用。不可把 DoP 噪声送往普通 PCM 路径。MQA 不承诺解码/认证。

## 8. 视频后端与呈现

### 8.1 libmpv 接入

首个 gate 使用 libmpv + Android GPU context，显式选择 `vo=gpu`、`gpu-context=android`、`opengl-es=yes`、手机 fast profile；不继承 desktop HDR/shader 默认。初始化 option 所有返回值检查 Required/Optional，缺必需能力中止并报错；禁用 mpv OSC/input 自带手势避免和 Compose 冲突。字幕由 libass，音频由 mpv 的一个 AO，时钟/speed/seek 交给 mpv，不自建音视频同步。

Android 注册 JavaVM/必要 application context 的路径依据冻结 FFmpeg/JNI API 核对。Surface 的 `wid` 采用匹配版本 Android context 要求的 **Java Surface GlobalRef**，不是 HWND、SurfaceTexture ID 或盲转 ANativeWindow。上游 render.cpp 是参考而不是安全规范，所有 release 顺序要自己验证。[上游 bridge](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/jni/render.cpp)。

默认 `hwdec=mediacodec`，仅允许 build 中有的列表；自动失败可先尝试 mediacodec-copy，再 Software。StrictHardware 不准回到 Software，允许不允许 copy-back 另设选项。`hwdec-current`/实际 decoder 是反馈源，设置为 mediacodec 不代表实际硬解。禁止默认 auto-unsafe。

切解码保存媒体/position/selected track identity/baseSpeed/pause/A-B/字幕延迟/transform。先验证锁定版本在线重配能保持状态；否则统一 reload transaction。重载完成后按 stable track identity 重新匹配，不能假定轨道 ID 永不变。timeout/硬解失败可回滚旧设置，连续请求只保留最后 policy，旧 Ack 不覆盖。

### 8.2 两种 presenter

**首版默认 TextureView + libmpv Android GPU 输出。** 由 AndroidView 宿主，只对 TextureView.setTransform 的矩阵做显示缩放/平移/连续旋转；Compose 控制层保持直立。此方案有额外 GPU 合成成本，但不把图像读回 CPU；在 S02 先验证硬解画面、libass 与 15°/45° 任意角，再扩展控件。Native 渲染只缩放到 viewport 的基础 fit；手势变换只由 TextureView 执行一次，不叠加 mpv zoom/rotate 同时处理。

**SurfaceView 是独立可选路径**，用于验证更低功耗/原生色彩/HDR；一般不把 graphicsLayer 当 Surface 变换保证。若用户开启连续旋转/缩放，切回 TextureView，或在高级页面明确只支持合格的 90° native 变换，不能静默吞掉手势。mpv 的无 copy 硬解旋转能力有限，不能假定 `video-rotate` 随便支持任意角；首版不依赖它实现双指旋转。[mpv video-rotate](https://mpv.io/manual/stable/#options-video-rotate)。

surface lease：mediaGeneration + surfaceGeneration；新 surface ready 才 Load 视频。Detach 走专用 worker handshake：暂停需要的视频 → 设置无 VO/停止使用旧 Surface → 确认匹配版本实际 VO 已退出 → 清 wid/GlobalRef → 回 Ack → UI 才释放 Surface/SurfaceTexture。TextureView destruction 在未 Ack 时暂保留拥有的 texture，Ack 后 release；超时进入受控销毁，不能早释放后继续渲染。不得在主线程同步等 detach。

Activity recreation/屏幕旋转只重建 presenter/控件，service 引擎继续存在。无 surface 默认暂停视频（后台音轨播放需明确设置）；无音轨视频不能靠 `vid=no` 把状态改成 EOF。退出顺序为停止写入/取消 I/O → VO/render detach → 解绑回调 → join worker → mpv destroy → 释放 GlobalRef/FD；实际顺序由版本证据补全。

默认视频音质走 mpv 兼容输出，显示其真实能力；HiFi strict 音频策略首版只用于音乐。视频长按 3×与原样 PCM/压缩直通冲突，不把视频音轨拆到 HiFi 引擎；passthrough 首版关闭。

### 8.3 横竖屏

计算有效 display aspect：codec crop + pixel aspect ratio + 文件 rotation；优先用内核报告的展示比例并核对是否已含旋转，防止旋转两次。宽高/比例无效时保留当前方向。附图封面不当视频轨。

默认进入全屏时 ratio >1.10 请求横屏，<0.90 请求竖屏，0.90–1.10 保留当前；每个 mediaGeneration 仅自动决定一次。手动 Portrait/Landscape/FollowDevice/AutoContent 对本视频优先并保留；用户旋转画面的手势不改变 Activity 自动方向。离开全屏恢复库页方向策略。

手机使用 Activity requestedOrientation adapter；平板/折叠/多窗口按当前 window bounds 自适应，不假定请求会被系统执行。target37 在 sw600dp 方向限制会被忽略，UI 仍要支持视频横向/纵向布局和手动画面旋转。[Android 大屏规则](https://developer.android.com/about/versions/16/behavior-changes-16)。

## 9. 播放手势的确定行为

一个 GestureArbiter 状态机统一决定 pointer consumption，不能简单并排三个互相竞争的 pointerInput detector。状态为 Idle/PendingTap/LongPressBoost/Seeking/Adjusting/Transforming/Locked。控件区域优先于背景手势，触及系统返回边缘留给系统；控件锁定只保留解锁/必要返回。

| 手势 | 默认行为 | 可设置 |
| --- | --- | --- |
| 单击空白 | 显示/隐藏控件，3 s 自动隐藏 | 自动隐藏时间/点击行为 |
| 双击左右 1/3 | ±10 s；夹到 [0,duration] | 5/10/15/30 s；关闭 |
| 双击中间 | 播放/暂停 | 关闭/播放暂停 |
| 单指长按非控件 | 达平台 longPressTimeout 后临时绝对 3× | 倍速 1.5–4×，默认 3× |
| 单指水平拖动 | 相对触点初始位置预览 seek，松手提交 | 灵敏度；即时 seek 可选 |
| 左半竖拖 | 窗口内亮度 | 换边/关闭；不得修改全局亮度 |
| 右半竖拖 | 媒体音量 | 换边/关闭；硬件/原样路径不可用时解释 |
| 双指 | 连续缩放 + 旋转 + 平移 | 每项独立开关、范围、吸附 |
| 重置按钮 | scale=1/rotation=0/pan=0，保留文件原始方向 | 不重置 Activity 方向 |

第二根手指按下，立即取消 LongPressBoost/Seek，恢复基础速度并进入 Transforming。一次 pointer 序列只产生一种最终操作；touchSlop 后再抢拖动，不因为小抖动取消点击。ACTION_CANCEL、松开、焦点丢失、进入 PiP、导航、暂停、切媒体、Surface 丢失、lock 都结束临时速度。使用 token + mediaGeneration，旧松手不能恢复新媒体速度。原 baseSpeed=2×时按住=3×，释放=2×；变更基础速度时有效值为 temporary override 优先，移除 override 后用最新 baseSpeed。

变换定义在 viewport 像素坐标，保存无单位归一化 pan。计算 centroid c、增量 scale z、rotation R，旧变换 M：`M_new = T(c + panDelta) * R(delta) * S(z) * T(-c) * M_old`，触点和旧矩阵必须同坐标系；基础 fit/文件方向另算。每步将总 scale 限制 0.5–5、旋转归一化，bounding box 限制至少部分画面可见；抬手时靠近 90°倍数 4°内且角速度低才吸附。resize 后重算 baseFit 和 pan bounds。禁止让字幕/控件坐标当视频像素坐标。

第一版 libass 字幕随 TextureView 画面变换；控制层固定。需要“字幕永远直立”时，S13 可为纯文本轨加 Compose overlay，ASS 定位/特效仍保留 libass 行为，不能假装所有字幕都可无损转文本。

## 10. HyperOS 风格 UI 与自定义

页面：音乐库、视频库、正在播放、设置。手机底部导航配浮动 mini player；音乐大页为封面、标题、艺术家、歌词切换、进度和播放组、简洁输出状态入口；视频全屏控制覆盖画面，不挤占视频尺寸。设备能力与采样率放“输出详情”，日常播放页不堆 mpv/FFmpeg 参数。

设计令牌集中定义：8 dp 间距网格（4/8/12/16/24/32）、页面 padding 20 dp、分组卡片圆角 24 dp、sheet 28–32 dp、触控至少 48 dp；标题 28–32sp/正文16sp/说明13sp，适配系统字体放大至 200%。浅色背景 #F5F5F5 / 卡片 #FFFFFF；深色背景 #0D0D0D / 卡片 #1C1C1E；默认强调蓝 #3482FF。颜色只是候选视觉基线，最终对比度需截图和无障碍检查；不复制小米商标、MiSans 字体文件或系统资源。

主题维度独立：System/Light/Dark；Classic/SoftGlass；Fixed/SystemDynamic/AlbumAccent；reduceMotion、contrast、haptics。AlbumAccent 从后台解码的小封面提取，限制色域/对比度，只有曲目封面 revision 变化更新；不能每帧改色。对 UI 权限/质量错误保证文字可读，状态不只依赖红绿颜色。视频页面 chrome 深色不强制修改全 App 主题。

动效规格：按钮按压 scale 0.96 /120 ms；控件显示 160 ms；sheet 240–320 ms + moderate spring；mini player 到大页共享封面/容器 320–420 ms，先实现可中断普通转场再加共享元素；歌词行 180 ms；主题色 250 ms。减少动画遵守系统 animator scale 0 和应用开关，持续封面/背景动画立刻停，所有操作仍立即有效。正在 seek/长按/打开菜单/控制层焦点时暂停自动隐藏；视频控件隐藏不改变 native surface 尺寸。

SoftGlass 只模糊预生成封面/背景小图，不对视频每帧 PixelCopy/readback。播放视频时背景动画停；后台音乐仅保留必要 service，不持续 Compose timer。手势变化只更新图层矩阵/必要文本，不让整个媒体库重组。

自定义设置采用类型化 schema：手势映射、跳转秒数、长按速度、缩放范围/吸附、控件位置与隐藏时间、fit/crop/原比例、字幕字号/语言/延迟、音轨语言、解码策略/降级、主题/动画、设备配置与 EQ。安全的高级 mpv 选项用白名单、范围和恢复默认；不支持任意脚本、无限缓存、任意 shell/URL options 注入。配置版本化迁移，损坏/未知字段有明确恢复，不丢用户原备份。

## 11. Android 文件、服务和数据

- `ACTION_OPEN_DOCUMENT` 打开单文件、`ACTION_OPEN_DOCUMENT_TREE` 选目录；取得实际授予的 URI flags 后 persist；MediaStore 批量浏览才请求相应读媒体权限。API33 分开 READ_MEDIA_AUDIO/VIDEO，旧系统 READ_EXTERNAL_STORAGE 设 maxSdk32；不申请 MANAGE_EXTERNAL_STORAGE。
- URI 是身份，displayName 只是展示。ContentResolver 在 I/O worker 打开 FD，native 获得明确 owned duplicate；dup 共享 open-file offset，扫描/probe 与播放不能同时 lseek 同一描述；必要时重新 open 或用 pread。FD 无 seek/长度不可知必须探测并标记，不从 `/proc/self/fd` 推导公共路径。
- FFmpeg custom AVIO 读/seek 支持 offset、length、AVSEEK_SIZE 和取消；mpv 使用经版本 gate 的 fd:// 或 stream callback lease。不能直接传 content:// 给不支持它的 native protocol。可 seek 文件零复制内容；pipe/cloud provider 非 seekable 时顺序播放或经用户选择导入应用缓存，有进度和磁盘上限（初始 512 MiB），不悄悄复制整库。
- Room 存 stable media ID、URI、权限状态、轻量标签、队列/历史/收藏；音乐和视频库独立索引，时长/封面未知可为空。目录移除只移除索引，不删除媒体；MediaStore/SAF 失权缓存保留并提供重新授权。
- scanner 单 worker、取消 generation；初始上限 20000 媒体/200000 条目录项/深度64，每批100更新；封面压缩数据上限16 MiB/原图2500万像素，列表缩略图128px和播放页1024px分别 LRU，失败也缓存。列表 LazyColumn/Grid 以 stable key 分页，不在组合期间读 tag/DB。
- service 使用 MediaSessionService，manifest FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK/type=mediaPlayback；系统播放器绑定真正的 Player adapter，全部 API 在指定 application Looper；custom commands 只对许可 controller 开放，未知 controller 不开放文件访问/高级配置。
- 用户操作启动 foreground playback；Android15+ 音频焦点需前台 app/前台服务资格，处理服务启动和焦点申请失败。默认不从 boot 启动播放。[后台播放](https://developer.android.com/media/media3/session/background-playback)、[Android15 行为](https://developer.android.com/about/versions/15/behavior-changes-15)。
- 通知权限按需要在合适时机请求，不因拒绝权限阻塞本地打开；媒体会话通知豁免与 OEM 表现分别验。音频无需 RECORD_AUDIO；窗口内亮度无需 WRITE_SETTINGS；播放手势无需无障碍服务/悬浮窗权限。
- 视频使用 FLAG_KEEP_SCREEN_ON；音乐息屏正常播放，partial wake lock 只在确需非 offload 写入且正在播放时持有，所有退出/错误/暂停释放。Wi-Fi lock 延后到网络场景。
- 历史保存每5 s合并/暂停/seek结束/退出，不把 tick 每次写 DB；重启只恢复队列和位置，默认不自动出声。service/Activity 重建不得重复创建/叠加 session。
- PiP 使用系统功能，进入前恢复临时倍速、调整源矩形/比例、隐藏 overlay、保留 surface lease；不额外启动第二个播放器。后台视频默认暂停，用户设置音轨继续时保留现有同一 mpv 内核。

## 12. 原生构建、许可和未来移植

mpv-android 是应用参考，不是可直接 import 的 AAR；其脚本当前说明仅支持 Linux/macOS、不支持 Windows/WSL。本项目在 Linux CI 构建固定依赖，Windows 仅构建 app/自有 JNI 与导入包。S02 需要自己提供可复现脚本，不让上游 download.sh 自动安装一整套未知依赖。[构建说明](https://github.com/mpv-android/mpv-android/blob/master/buildscripts/README.md)。

native runtime manifest 必须记录：schemaVersion、ABI/minApi/NDK/STL、每个 source URL/tag/完整 SHA/archive hash、patch hash、编译参数、header 版本、依赖 SONAME、产物 SHA256、license/notices/source artifact。初始 arm64-v8a + x86_64，各自完整闭包；暂不做 armeabi-v7a/x86。不从用户 PATH 或 mpv-android APK 抽取未知 `.so`。

使用共享 FFmpeg 构建闭包：libmpv 和 HiFi engine 链接同一 FFmpeg shared libraries，全部动态依赖必须随包或明确为 Android public system library；不启用编码器/采集设备/无关协议，按 fixture 确认 demuxer/decoder/parser 没被裁掉。GnuTLS/OpenSSL 等 TLS 库和 CA/证书验证若用于后续网络必须一起锁定，禁止 tls-verify=no。Windows DLL 不能转换为 Android .so。

NDK28+ 默认16KB对齐仍不能替第三方预编译库保证；检查每一个 ELF LOAD/SONAME/依赖、APK ZIP 对齐、4KB/16KB设备。C ABI export 最小、JNI必要 R8 keep；release native 符号单独保存供 tombstone symbolication，包内不塞全 debug symbols。[16KB官方要求](https://developer.android.com/guide/practices/page-sizes)。

许可证建议 GPL-3.0-only，实际 COPYING/notices/源码包由锁定组件形成闭包；FFmpeg 不使用 nonfree build。若未来需要不同授权，不直接改根 LICENSE，应审计复用来源和实际构建。[FFmpeg license](https://ffmpeg.org/legal.html)。这不是正式法律审查的替代，S14 只对实际发行工件给结论。

可移植的是状态机、解码/DSP/队列/测试/C ABI；不是 Android Surface/MediaSession/UsbManager。iOS 要替换 AVAudioSession/AudioUnit/Metal presenter/权限；其他移动 OS 要替换其音频、解码、文件和窗口 adapter，并核查 mpv/FFmpeg 的可用性与发行要求。当前不承诺 iOS/HarmonyOS/OHOS 已支持，也不提前增加未测试 target。

## 13. 验证矩阵与非功能预算

功能验收 fixture 自有/可授权：16/24/32位 PCM WAV、44.1/48/96/192kHz FLAC、ALAC、MP3/AAC gapless、损坏/截断文件、H264/HEVC8/10/VP9/AV1、无音轨/多音轨/ASS/SRT、rotation90/SAR/portrait/square、4K30/1080p60。每个素材记录 hash 与生成方法，不提交用户媒体。

| 组合 | 必验 |
| --- | --- |
| API26模拟器 / x86_64 | 文件/权限/配置/JNI/软解、旧 API 类加载；不验物理 DAC |
| 小米/红米 API34+ + USB DAC | mixer能力、44.1↔48↔96、实际路由、拒绝/退级/拔插/息屏/焦点 |
| 至少一个非小米设备 | fallback、OEM service/AAudio、同源 fixture |
| 耳机口/扬声器/蓝牙 | 正常播放、没有假位准确、设备切换策略 |
| sw600dp/折叠/多窗口 API36/37 | 强制方向被忽略仍可用、Activity重建不丢播放 |
| 4KB与16KB设备 | 全 `.so` 装载、JNI、硬解、音频回调 |

初始工程预算是优化目标而非已测结果：release音乐页面无视频 decoder/render context；本地首声/首帧目标 <1 s（固定基准设备和冷/热口径）；正常 seek目标<500 ms，软硬切换恢复目标<1 s；20k库滚动主线程无I/O、60Hz界面P95<16.7ms、120Hz另测；30min播放无非预期 underrun/崩溃；重复打开/seek/横竖切换各100次不持续泄漏。高码率/软解4K超硬件能力必须可解释，不能用删帧掩盖质量。

位准确证据分两层：程序 PCM packing与数据 hash相等只证明 sink 前未改样本；数字 loopback/capture 对已播放区间同步校准、去已知前后padding后比较样本，才证明被测设备组合端到端。DAC采样率显示、模拟录音、主观听感都不是位准确证据。capture验证记录 app/runtime/ROM/DAC/firmware/格式/route/音量与DSP/素材hash，不能把一组结果外推所有设备。

HDR 源识别、HDR→SDR tone mapping、原生 HDR 输出三个状态独立。首版默认合格 SDR；TextureView 的普通8bit surface不能证明HDR，SurfaceView和色彩metadata/显示模式/实际测量要 S13 专项。显示 HDR 标签只表示源格式时明确标“素材 HDR”。
## E00 实施状态与当前事实（2026-10-04）

本节更新本文较早的规划描述；版本及行为以当前 Gradle 配置、源码和 MuMu 验收为准。

E00 已在 MuMu Android 15/API 35 构建运行。当前依赖为 Kotlin/Compose 插件 2.4.20、Compose BOM 2026.09.00、Miuix 0.9.4（`miuix-ui-android`）和 Media3 1.11.1。以上组合已通过 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`；这只验证当前模拟器/SDK 构建，不保证 Android API 26 或实体机兼容性。

当前播放内核是 `AndroidMedia3PlaybackBackend`，以 ExoPlayer 实现 `PlaybackBackend` 的播放控制和状态快照。音乐与库页面通过该边界操作本地内容；视频渲染目前由 `VideoSurface` 中的 Media3 `PlayerView`/`TextureView` 完成。因此播放命令边界已有抽象，但整个视频显示链路尚未达到纯跨平台/纯 C ABI 边界。播放器速度、选曲与音频会话等仍由 Android 适配层管理。未来若采用 Rust/C++ 引擎，应以独立 `NativePlaybackBackend` 和窄 JNI/C ABI 逐步替换，并在 Surface、FD 生命周期和取消语义通过设备验收后再移除 Media3 实现。

视频策略提供 AUTO、PREFER_HARDWARE、STRICT_HARDWARE、SOFTWARE 四档。严格硬解在创建替代播放器前预检候选 codec；找不到候选时给出可恢复错误并保持当前播放器实例继续播放。MuMu 在同一 H.264 样片上已实测 `OMX.qcom.video.decoder.avc` 硬解和 `OMX.google.h264.decoder` 平台软解。该模拟器把 Google codec 的 `hardwareAccelerated` 与 `softwareOnly` 都报告为 false，应用因此结合标准 `OMX.google.`/`c2.android.` 名称前缀标为“平台软解（系统标记不完整）”。这项前缀规则只是模拟器兼容回退，codec 选择和名称不可当成实体设备的绝对硬件保证。

E00 的 UI、主题切换、SAF 导入、横竖屏自动判断及手动方向切换、临时长按 3×、双指缩放/平移/旋转有可运行实现；物理多点手势和长按时序尚未在真实触屏设备验收。44.1 kHz/2 声道是当前 Media3 输入轨道报告，不证明输出采样率、独占通路或 bit-perfect。USB DAC 路由/独占与原生 Oboe 输出仍属于后续实现及实体机验收范围。
