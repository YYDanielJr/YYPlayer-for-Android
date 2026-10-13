# ADR 0006：HiFi 音频输出在可移植 C++ 核心与 Android 平台层之间的分工

- 状态：已接受（H01–H04 逐步实现；硬件独占／位完美资格仍待验）
- 日期：2026-10-05（H02 补充 2026-10-06）
- 背景：用户要求在 dev-hifi 分支对照 `docs/TECHNICAL_DESIGN.md` §7 与 `IMPLEMENTATION_PLAN` S06–S08 实现独占输出与 API 34+ 位完美（BIT_PERFECT）播放，并让播放核心可以跨平台复用；同时要求软件内可自由选择输出设备。设备侧只有一台小米 17 Pro Max（Android 17 / API 37），没有 USB DAC 与蓝牙音频设备。
- 事实（已核对官方文档与 SDK，见 `docs/research/USB_DAC_BITPERFECT_API_FACTS.md`）：
  1. `AudioTrack` / `AudioTrack.Builder` **没有** mixer attributes API，`AudioAttributes` 也没有；API 34+ 的位完美入口**只有** `AudioManager.getSupportedMixerAttributes` / `setPreferredMixerAttributes` / `getPreferredMixerAttributes` / `clearPreferredMixerAttributes` 与 `OnPreferredMixerAttributesChangedListener`，且只对 USB 设备、只对 `USAGE_MEDIA` 生效，需要 `MODIFY_AUDIO_SETTINGS`。
  2. `AudioMixerAttributes` 只能由 `AudioMixerAttributes.Builder(AudioFormat)` 构造，行为位由 `setMixerBehavior(MIXER_BEHAVIOR_BIT_PERFECT)` 表达；编码/采样率/声道掩码全部来自那个 `AudioFormat`，播放必须用**同一** `AudioFormat` 才会被挂到该混音器上。
  3. BIT_PERFECT 是厂商可选能力：AOSP 要求 AIDL Audio HAL 在 dynamic mix port 上声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT`，并由 HAL 保证不缩放音量、不重采样、不做音效、不混入 DSP。因此“请求成功”与“真的位准确”不是同一件事。
  4. `AudioDeviceInfo.getId()` 官方只说明是内部 ID，没有跨会话稳定性；`getRoutedDevice()` / `getRoutedDevices()` 只在播放中才有意义，`getFormat()` 是 configured format，不是硬件实际格式。
  5. Media3 1.11.1 没有任何 mixer attributes 通路，且自带 `AudioProcessor`/float 输出链，与 BIT_PERFECT 的“无混音、无音量调整、无音效”要求冲突，因此**不能**用它实现位完美。
  6. AAudio/Oboe 可以请求独占并用 `AAudioStream_getSharingMode` 读回实际值，但它没有自己的设备枚举，也不能请求 BIT_PERFECT。
  7. Android 17 的 “Background audio hardening” 让后台/非有效生命周期下的播放与音量 API 静默失败。
- 选项：
  - A. 全部用 Kotlin 写：接入 ExoPlayer/Media3 现成管线，实现快，但没有 mixer attributes 通路，位完美无法实现，也无法跨平台复用。
  - B. 全部用 C++ 写（含解码与设备选择）：跨平台最彻底，但设备枚举与 mixer attributes 只能经 Java API，等于把平台层硬塞进 native，且需在 C++ 里回调 JVM，违反“回调不做重活”的边界。
  - C. 分成两层：与平台无关的播放逻辑放可移植 C++ 核心，平台输出能力与设备选择留在 Android adapter。
- 决定：采用 C。
  1. `native/core` 是可移植 C++17 静态核心，只依赖标准库，拥有：PCM 格式描述与字节/帧运算、原样（不丢有效位）样本转换、无锁单生产/单消费 PCM 环、输出策略判定（sharing 与 transparency 的 AND、拒绝原因）、控制桥用的 JSON 读写。这些都能在主机和真机上跑同一份测试。
  2. `native/jni/yy_jni.cpp` 是薄 JNI 桥：控制面走 JSON，PCM 走 DirectByteBuffer 与预分配环，不暴露 mpv/FFmpeg/Android 类型，不在 realtime 回调里分配。
  3. Android 侧 `audio/` 负责所有平台能力：`AudioDeviceProbe`（设备枚举、API 34+ 混音器属性，隔离在嵌套对象里以免 API 26–33 加载到新类）、`AudioOutputController`（决定 → 平台请求 → 只发布平台回读的事实）、`NativeAudioCore`（唯一 JNI 入口）。API 34 之前只有兼容路径。
  4. 位完美状态只有 `unknown/unavailable/requested/system_reported/capture_verified` 五档；应用最多把“系统接受了混音器契约并按该格式输出”标成 `system_reported`，`capture_verified` 只能由数字捕获证据给出，代码不会自行设置。
  5. 严格策略不静默降级：`strict_exclusive` 在系统 AudioTrack 路径上直接拒绝（`exclusive_not_proven_by_audiotrack`），`strict_unprocessed` 在没有匹配 BIT_PERFECT 契约、或存在软件音量/EQ/ReplayGain/变速时拒绝。
- 影响：
  - 独占与位完美只在 USB 设备上可能；内置扬声器/蓝牙始终显示 `unavailable` 并给出原因，不会伪造。
  - 设备选择是本会话内的设备 ID；跨会话身份用 `type + address` 做软匹配（`AudioOutputChannel.stableKey`），不把会话 ID 当永久主键。
  - Media3/ExoPlayer 仍是当前唯一播放后端（ADR 0001），本轮没有让它承担位完美；真正的 PCM 输出通路留给 S06 的 FFmpeg 解码 + AudioTrack/Oboe sink。
  - 未接 USB DAC 时无法取得任何 `system_reported` 证据，因此本轮位完美的真机资格记为未验，不写成已达成。
- 退出条件：S06 的 FFmpeg 解码与 PCM sink 接入后，本 ADR 的平台层保持不变，只把 PCM 源从 Media3 换成核心；S07/S08 的验收仍需实体小米/红米 + USB DAC 的证据。
- 验证（H01 首轮实际执行）：主机 CTest `native/build-host/yy_core_tests` 2345 项断言通过；同一二进制推送到 `ac07514c` 真机运行同样 `checks=2345 failures=0`；Gradle 交叉编译 arm64-v8a 与 x86_64 的 `libyyplayer_jni.so` 并打入 APK；真机 `AudioOutputDeviceTest` 7 项通过（原生核心装载与版本、策略拒绝路径、设备能力记录、严格独占/严格原样拒绝）。未验证：任何 USB DAC 的 BIT_PERFECT 契约、Oboe 独占、数字捕获、蓝牙输出。
- 验证（H02 补充，2026-10-06，小米 2509FPN0BC + USB DAC「lifeme HiFi Audio Pro」+ 蓝牙「OPPO Enco Free4」）：
  1. **第 3 条的 Android 侧落地**：`PcmSink` 接口 + `AudioTrackPcmSink` 实现，泵线程从核心的 `PcmRing` 取帧写入平台输出，绝不替换调用方要求的格式、不做软件混音或音量、不转换样本。
  2. **第 5 条“请求 → 只发布回读事实”**已按真实硬件走通：336000 帧参考信号全部送出（`dropped=0`、`consumed=336000`），`AudioTrack` 实际打开 48000 Hz / s16 / mask 0x3 / 7688 帧缓冲，`getRoutedDevices()` 回读为 USB DAC（`routedIsUsb=true`），平台 `getUnderrunCount()=0`。
  3. **该 USB DAC 不提供任何 BIT_PERFECT 组合**：`getSupportedMixerAttributes` 返回 42 个组合（7 种采样率 × s16/s24_3/s32 × 2 种掩码），全部 `MIXER_BEHAVIOR_DEFAULT`。核心因此判定 `requestBitPerfect=false`，应用不请求、不显示契约，界面显示“不可用 / 该设备组合没有系统位完美契约”。这同时说明：**位完美资格取决于厂商 HAL 是否声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT`，不是应用能争取到的**。
  4. **`setPreferredDevice` 的返回值不可作为路由证据**：小米上返回 `false`，但平台仍路由到该设备。记录 `pinAccepted` 仅作参考，路由一律以 `getRoutedDevices()` 回读为准。
  5. **`system_reported` 级位完美在本机无法取得**（无 BIT_PERFECT 组合），因此该级别仍记为未验；代码路径（请求 + 回读 + 判定）已就绪，换一台声明该 flag 的 DAC 即可取证。
  6. 本轮修掉 H01 的真实缺陷：`yy_jni.cpp` 中 7 个环/转换入口的导出名缺少 `_00024Bridge_`，而 Kotlin 把全部 10 个 native 声明都编译在嵌套 `Bridge` 类上，因此这些入口从未解析成功，调用被 Kotlin 包装吞成“原生不可用”。现改为显式 `RegisterNatives`，签名或宿主类不匹配会让库装载直接失败；宿主侧 `NativeAudioCoreContractTest` 锁定声明位置与形状。
  7. 端到端位准确仍需数字捕获：`OutputSelfTestResult.digestHex` 记录进入环的字节的 SHA-256，作为捕获比对的参照值，本轮未做捕获。
- 验证（H03 补充，2026-10-06 同日）：
  1. **加宽是“保电平”而不是“保数值”**（核心修正，最重要的实质缺陷）：`Convert()` 原本只做符号扩展，把 s16 样本原样放进更宽的容器。整数 PCM 的满刻度是 `2^(bits-1)`，样本是容器量程里的位置而不是绝对幅度，所以 16-bit 满刻度值 `32767` 放进 24-bit 容器只有 −48 dB（几乎听不见）。修正为按位深差左移并补零：`−12345 → s24_3 00 C7 CF / s24_in32 00 C7 CF FF / s32 00 00 C7 CF`。这是精确整数运算，新增低位全为零，不丢有效位、不经浮点，仍属 pristine 路径；同宽容器互转位移为 0，数值不变；降位深仍按 `kLossy` 拒绝。主机核心断言 2345 → **6339**。
  2. **格式比对原本是同义反复**：`AudioTrack.getSampleRate()`/`getFormat()` 会把构造参数回显，所以拿它们和“请求”比对必然一致，平台替换了格式也发现不了。现在 sink 单独记 `platformFormat`，`formatMatchesTrack` 与之一致，界面在发生替换时显示“平台已替换”并列出平台实际接受的格式。证据：`s24_3` 与 `s32` 均按请求被接受（`matches=true substituted=false`），336000 帧全部送达 USB DAC。
  3. **`s24_in32` 没有平台容器**：`AudioFormat` 只有 3 字节 packed（`ENCODING_PCM_24BIT_PACKED`）与 32-bit（`ENCODING_PCM_32BIT`），没有“24 位有效放 4 字节容器”的编码。sink 对核心的 `s24_in32` 返回 `sink_bad_format` 并带原因，不静默换成别的容器；核心仍能把 `s24_in32` 无损加宽到 `s32`，该映射留给调用方显式决定（尚未接入策略）。
  4. **非 USB 路由的对照证据**：蓝牙 A2DP 与内置扬声器跑同一 sink 路径，336000 帧全部送达，但 `routedIsUsb=false` 且 `systemReportedBitPerfect=false`——能出声，绝不声称位完美。
  5. **参考信号必须是直接缓冲**：`renderS16()` 原用 `ByteBuffer.allocate`（堆缓冲），而 JNI 用 `GetDirectBufferAddress` 取样本内存，堆缓冲返回 null 使 `convertPcm` 返回 −1，表现成“核心拒绝无损转换”。已改为 `allocateDirect`。
  6. 未验：96k/192k/384k 高采样率实机输出、`s24_in32`→`s32` 映射、Oboe 独占、数字捕获。

## H04 补充：Media3 解码、自研 AudioSink 与 MediaSession（2026-10-06）

为让设置中的输出通道立即影响真实播放，采用可逆的 `SYSTEM / NATIVE_EXACT` 选择。SYSTEM 保持 Media3 默认 sink，音乐的 NATIVE_EXACT 使用 MediaCodec 解码 → 自研 `NativeOutputAudioSink` → `AudioTrackPcmSink`；可移植核心仍负责整数无损加宽，H02 的核心环泵用于输出自检。普通播放由 renderer 非阻塞投递，不另建解码器／输出线程竞争该 track；长期 FFmpeg 解码目标及 ADR0001 退出条件保留。视频输出选择 SYSTEM。

本轮用真实 24-bit FLAC 发现：压缩格式携带 `pcmEncoding` 不等于 buffer 是 PCM。sink 必须只接受 `audio/raw`，否则 Media3 绕过 MediaCodec。时间基准采用 renderer 输入时间戳 + 实际播放头，区分 `AudioSink.CURRENT_POSITION_NOT_SET` 与 `C.TIME_UNSET`；不得用写入进度充当听到的位置。解码器明确请求源整数容器并检查回报位深，不把 32-bit 整数统一转 float32，不静默缩为 s16。

`PlayerSessionService` 只发布唯一真实引擎的 session，并持有该引擎的生命周期引用；播放器重建时先改绑 session，再释放旧 player，随后准备新输出。音乐的最后 Activity 停止或销毁不暂停／释放 service 正在播放的引擎；视频仍在离开前台后暂停。没有空 Player 或第二套通知引擎。

AudioTrack 路径仍不能证明 Exclusive；设置选择自研路径不是实际独占资格。StrictExclusive 明确拒绝；StrictUnprocessed 在没有匹配 BIT_PERFECT 契约或软件音量处理时拒绝。请求在创建 track 前完成，preferred mixer 独立回读，`bitPerfectActive` 还要求实际 USB route/format 匹配。软件音量在宽松模式保留，不突然提为 unity；自研通道只报告 1×、无跳过静音处理。实际 BIT_PERFECT 硬件、端到端捕获、物理拔插／焦点与锁屏长测仍未验证。

证据与具体运行结果见 [H04 STATUS](../STATUS.md) 及 [validation](../validation/hifi-v1/README.md)。API 事实以锁定 [MediaCodecAudioRenderer 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/MediaCodecAudioRenderer.java)、[AudioSink 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)、[DefaultAudioSink 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java) 与本地 artifact 字节码为准。

H04 实测：24-bit FLAC 280 秒整曲测试 OK (1 test)，12109193 帧投递并播放完毕，STATE_ENDED，非短测推断；最终组合 OK (22 tests)，包括真实媒体控制器与生产 UI 引用释放后的后台运行。JVM 28 项、核心 6339 断言通过，构建与 lint exit0。Compose Activity instrumentation 启动挂起，未当作 UI 自动化通过；物理拔插／焦点、锁屏长测和独占／位完美资格仍待专项。

## H05 审计决定与歌曲详情输出控制（2026-10-08）

用户要求完全核验 H02/H03/H04 未提交代码，并在歌曲详情页切换方式和设备。逐文件结论见 [HIFI_CODE_AUDIT](../HIFI_CODE_AUDIT.md)。保持单一 PlayerRuntime/session；SYSTEM 通过 Media3 的 preferred device 接口选设备，自研通过 AudioTrack pin，两条路径都先释放旧输出再恢复位置／播放意图／音量。歌曲详情的 WindowDialog 直接发同一 runtime 命令，设备列表在一次弹窗内保持 ID 索引稳定，拔插不把旧点选转换为另一台设备。

Android 控制器现在只做能力／策略投影；活跃 mixer 请求由持有 track 的 sink 设置和清除，规划结果不再冒充 actual sharing。USB 上仅 pin 不足以让本机 44.1kHz/24packed track 保持在 USB：实测首帧后会回到旧蓝牙。创建 track 前请求 DAC 报告的匹配 DEFAULT mixer 才使实际路由测试通过。这仍是共享混音器配置，不增加位完美或流独占证据。启动期确认实际路由后才投递 PCM；失联保留所选 ID，暂停／清空并拒绝自动扬声器回退。

BIT_PERFECT 只有匹配 USB 契约、API 格式、实际路由与无欠载等读回事实才显示 system_reported；HAL/hardware format 仍 Unknown。请求还须原有软件／系统音量已为 unity，绝不改满音量。活动 BIT 下音量变化停止并报错，避免忽略静音／ducking；普通共享请求排除 BIT profile。严格无处理拒绝加宽，宽松模式允许精确加宽但不得据此宣称端到端位准确。float 源／encoder delay-padding／声道重排使用 SYSTEM；自研实际 1×，系统速度意图保留。

JNI 使用 shared_ptr lease 与普通 worker 的 IO mutex，解决 release/Clear 并发与多生产者问题；这不是 realtime 回调锁。PCM remaining slice、可写目标、重叠区间、C++17 有符号位移、24-in-32 padding、profile 全量有界读取、异常边界均修正。普通播放仍由 renderer 投递；核心环只用于参考信号。自检先释放歌曲输出，保留音量并在结束恢复原位置为暂停状态；取消完成 IO finally 后才解锁。

格式重配待旧尾部排空，flush 保留待生效格式；API31+ 在排空短尾部时降低 AudioTrack start threshold，解决暂停后的门槛停摆。API26–30 的短尾部仍未取证，不能将 API37 通过扩大到低版本。证据、构建与限制以 STATUS 顶部 H05 和 validation README 为准。
