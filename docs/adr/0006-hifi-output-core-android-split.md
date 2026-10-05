# ADR 0006：HiFi 音频输出在可移植 C++ 核心与 Android 平台层之间的分工

- 状态：已接受（dev-hifi 首轮实现）
- 日期：2026-10-05
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
- 验证（本轮实际执行）：主机 CTest `native/build-host/yy_core_tests` 2345 项断言通过；同一二进制推送到 `ac07514c` 真机运行同样 `checks=2345 failures=0`；Gradle 交叉编译 arm64-v8a 与 x86_64 的 `libyyplayer_jni.so` 并打入 APK；真机 `AudioOutputDeviceTest` 7 项通过（原生核心装载与版本、策略拒绝路径、设备能力记录、严格独占/严格原样拒绝）。未验证：任何 USB DAC 的 BIT_PERFECT 契约、Oboe 独占、数字捕获、蓝牙输出。
