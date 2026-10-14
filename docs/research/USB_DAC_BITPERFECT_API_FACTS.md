# Android USB DAC 独占输出 / BIT_PERFECT 混音器属性 —— API 事实核查（官方文档 + AOSP 源码 + 本机 SDK 校验）

核查日期：2026-10-05
核查方法（重要）：
- 官方文档页：本机网络无法直连 developer.android.com，改用官方同一内容的镜像域 developer.android.google.cn / source.android.google.cn，
  经「连接设备 adb shell curl」抓取整页 HTML 后在本地转纯文本，逐行核对原文（缓存见 `.docs-probe/cache/*.txt`）。引用的官方 URL 均为 developer.android.com / source.android.com。
- AOSP 源码：本机 SDK `sources/android-35 / 36 / 36.1` 的 `android/media/*.java`（framework 公开注解与 javadoc 原文）。
- 公开 API 面：`javap` 反汇编 `platforms/android-34/35/36/36.1/android.jar` 与 AGP 下载的 API 37 android.jar（Gradle transforms 缓存）。
- 形式化规范：官方 API diff 页面（`/sdk/api_diff/37/changes/...`）。
- 未能证实项一律标注「未能证实」，未凭记忆补签名。

---

## 0. 先看三条会直接影响实现的结论（与常见说法不同）

1. **AudioTrack / AudioTrack.Builder 上没有 `setPreferredMixerAttributes`，AudioAttributes 上也没有**
   `AudioTrack.Builder`（API 37）公开方法全集只有：`setAudioAttributes`、`setAudioFormat`、`setBufferSizeInBytes`、
   `setCodecProvenance`(37)、`setContext`、`setEncapsulationMode`、`setOffloadedPlayback`、`setPerformanceMode`、
   `setSessionId`、`setTransferMode`、`build()`。既无 `setPreferredMixerAttributes`，**也无 `setPreferredDevice`**。
   官方 AudioTrack 页面对 `AudioMixerAttributes` 只出现在 “See Also / 相关类” 里，正文 0 处提及。
   → 34+ 的 bit-perfect 入口是 **AudioManager.setPreferredMixerAttributes(attributes, device, mixerAttributes)**，不是 AudioTrack。
2. **AudioTrack 也没有 `getMixerAttributes()`**；“读实际 mixer attributes” 只能用 `AudioManager.getPreferredMixerAttributes(...)`
   （读的是“你之前设置且仍生效”的偏好），以及 `AudioTrack.getRoutedDevice()` / `getFormat()` 做间接校验。
3. **Api 34+ 的 preferred device 只有一个入口：`AudioTrack.setPreferredDevice(AudioDeviceInfo)`（绑定在实例上，非 Builder），返回 boolean。**

---

## 1. AudioMixerAttributes / Builder 完整公开 API（API 34）

**结论（可直接编码的事实）**

- `public final class android.media.AudioMixerAttributes implements Parcelable`（API 34 新增）。
- **没有公开构造函数**：包私有 `AudioMixerAttributes(AudioFormat format, int mixerBehavior)`。唯一公开构造途径是 Builder。
  `new AudioMixerAttributes.Builder(format).setMixerBehavior(...).build()`。
- 常量：`MIXER_BEHAVIOR_DEFAULT = 0`、`MIXER_BEHAVIOR_BIT_PERFECT = 1`（`@IntDef` 注解 `MixerBehavior` 为 `@hide`）。
- 实例方法：`public AudioFormat getFormat()`（`@NonNull`）、`public int getMixerBehavior()`，
  以及 `equals/hashCode/toString/describeContents/writeToParcel` 和 `public static final Parcelable.Creator<AudioMixerAttributes> CREATOR`。
- `public static final class AudioMixerAttributes.Builder`：
  - `public Builder(@NonNull AudioFormat format)` —— 参数不能为 null（源码 `Objects.requireNonNull(format)`）；
  - `public @NonNull AudioMixerAttributes build()`；
  - `public @NonNull Builder setMixerBehavior(@MixerBehavior int mixerBehavior)` —— 只接受 `MIXER_BEHAVIOR_DEFAULT` 或
    `MIXER_BEHAVIOR_BIT_PERFECT`，其他值 `throw new IllegalArgumentException("Invalid mixer behavior " + mixerBehavior)`。
  - **没有** `setFormat` / `setChannelMask` / `setSampleRate` / `setAllowedMixingRule` / `getAllowedMixingRule`（官方页面与源码均无）。
    格式三要素（encoding、sample rate、channel mask/index mask）**全部通过构造参数 AudioFormat 传入**，Builder 不可改。
- 官方文档示例（improve-audio-playback）用的是：
  `AudioManager.getSupportedMixerAttributes(usbDevice)` → 过滤 `EXPECTED_FORMAT.equals(mixerAttr.format)` →
  `AudioManager.setPreferredMixerAttributes(attr, usbDevice, mixerAttributes)` →
  用 `AudioTrack.Builder().setAudioAttributes(attr).setAudioFormat(mixerAttributes.format).build()` 播放 →
  播放结束 `AudioManager.clearPreferredMixerAttributes(attr, usbDevice)`。
  注意官方示例**没有**调用任何 AudioTrack 侧 mixer API —— 因为不存在。

**官方 URL**
- https://developer.android.com/reference/android/media/AudioMixerAttributes
- https://developer.android.com/reference/android/media/AudioMixerAttributes.Builder
- https://developer.android.com/media/platform/improve-audio-playback
- AOSP 源码（同签名）：`frameworks/base/media/java/android/media/AudioMixerAttributes.java`

---

## 2. API 34 的 mixer attributes 全套（含权限与返回值）

**结论**

| API | 精确签名 | 级别 | 关键语义 |
|---|---|---|---|
| `AudioManager.getSupportedMixerAttributes` | `public List<AudioMixerAttributes> getSupportedMixerAttributes(@NonNull AudioDeviceInfo device)` | 34 | 返回该设备可用的 mixer attributes；**"only USB devices are guaranteed to expose configurable mixer attributes. An empty list may be returned for all other types of devices"**；非 null |
| `AudioManager.setPreferredMixerAttributes` | `public boolean setPreferredMixerAttributes(@NonNull AudioAttributes attributes, @NonNull AudioDeviceInfo device, @NonNull AudioMixerAttributes mixerAttributes)` | 34 | 需要 `Manifest.permission.MODIFY_AUDIO_SETTINGS`；**目前只支持 `AudioAttributes.USAGE_MEDIA`**；**"Currently, only USB device will be allowed"**；返回 **"true only if the preferred mixer attributes are set successfully"**；偏好按 **uid** 归属 |
| `AudioManager.clearPreferredMixerAttributes` | `public boolean clearPreferredMixerAttributes(@NonNull AudioAttributes attributes, @NonNull AudioDeviceInfo device)` | 34 | 同权限；返回 true 仅当成功移除 |
| `AudioManager.getPreferredMixerAttributes` | `public AudioMixerAttributes getPreferredMixerAttributes(@NonNull AudioAttributes attributes, @NonNull AudioDeviceInfo device)` | 34 | **可返回 null**：未设置或已清除时返回 null；底层失败时也是 null（源码 log 后 return null） |
| `AudioManager.addOnPreferredMixerAttributesChangedListener` | `public void addOnPreferredMixerAttributesChangedListener(@NonNull Executor executor, @NonNull AudioManager.OnPreferredMixerAttributesChangedListener listener)` | 34 | 回调在给定 Executor 上执行 |
| `AudioManager.removeOnPreferredMixerAttributesChangedListener` | `public void removeOnPreferredMixerAttributesChangedListener(@NonNull AudioManager.OnPreferredMixerAttributesChangedListener listener)` | 34 | — |
| 监听接口 | `interface AudioManager.OnPreferredMixerAttributesChangedListener { void onPreferredMixerAttributesChanged(AudioAttributes, AudioDeviceInfo, AudioMixerAttributes); }` | 34 | 第三个参数可为 null（Kotlin 示例里是 `AudioMixerAttributes?`） |

- 所有参数均为 `@NonNull`；源码是 `Objects.requireNonNull(...)`，传 null 抛 `NullPointerException`。
- 官方 javadoc **没有**为这些方法声明任何 `Throws`（没有文档化的 `UnsupportedOperationException` / `IllegalArgumentException`）。
  失败以 **返回 false** 表达。
- `AudioManager.OnPreferredMixerAttributesChangedListener` 是 **AudioManager 的嵌套接口**，不存在于 AudioTrack/AudioAttributes。
- 权限 `MODIFY_AUDIO_SETTINGS` 是 manifest 声明的普通权限（非运行时危险权限）。

**官方 URL**
- https://developer.android.com/reference/android/media/AudioManager#getSupportedMixerAttributes(android.media.AudioDeviceInfo)
- https://developer.android.com/reference/android/media/AudioManager#setPreferredMixerAttributes(android.media.AudioAttributes,%20android.media.AudioDeviceInfo,%20android.media.AudioMixerAttributes)
- https://developer.android.com/reference/android/media/AudioManager#clearPreferredMixerAttributes(android.media.AudioAttributes,%20android.media.AudioDeviceInfo)
- https://developer.android.com/reference/android/media/AudioManager#getPreferredMixerAttributes(android.media.AudioAttributes,%20android.media.AudioDeviceInfo)

---

## 3. “BIT_PERFECT” 怎么表达；encoding / 采样率 / 声道掩码 的要求

**结论**

1. **行为位**：`AudioMixerAttributes.Builder.setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)`（值 = 1）。
   官方定义原文：**“Constant indicating the audio mixer behavior is bit-perfect, which indicates there will not be mixing happen,
   the audio data will be sent as is down to the HAL.”** 默认 `MIXER_BEHAVIOR_DEFAULT`（值 = 0）＝“mixing all audio sources in the mixer”。
2. **encoding 与 AudioMixerAttributes 的关系**：`AudioMixerAttributes` **本身没有 encoding 字段**，encoding 是
   `getFormat()` 返回的 `AudioFormat` 的一部分。源码 javadoc：*“Return the format of the audio mixer. The format is an AudioFormat object,
   which includes encoding format, sample rate and channel mask or channel index mask.”*
   → 表达 bit-perfect = **AudioFormat(encoding, sampleRate, channelMask) 作为 Builder 构造参数** + `setMixerBehavior(MIXER_BEHAVIOR_BIT_PERFECT)`。
3. **是否需要 ENCODING_PCM_24BIT_PACKED / ENCODING_PCM_32BIT**：**官方没有“必须”要求**。官方唯一示例用
   `AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_24BIT_PACKED).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setSampleRate(44100).build()`。
   API 34 起同时存在 `ENCODING_PCM_24BIT_PACKED`、`ENCODING_PCM_32BIT`、`ENCODING_DSD`、`ENCODING_PCM_FLOAT` 等（本机 android-34/37 jar 均含）。
   **正确做法是不要写死**：用 `AudioManager.getSupportedMixerAttributes(device)` 取回列表，挑与源格式一致的那一项。
4. **判定/匹配条件（官方原文措辞）**：
   - improve-audio-playback：*“When your app playback matches the encoding format, channel mask, and sample rate of the preferred mixer attributes,
     the playback is attached to the audio output stream whose mixer is configured with the preferred mixer attributes.”*
   - `setPreferredMixerAttributes` 参数说明：*“When playing audio targeted at the given device, use the same AudioFormat for both playback and the mixer attributes.”*
   - AOSP source.android.com：*“If the mixer behavior uses BIT_PERFECT mode, the system sends the audio content without modification through the audio framework,
     HAL and optionally digital signal processor (DSP), from the API down to the USB device. The BIT_PERFECT mode supports encoded formats, such as MQA or DSD,
     on which any volume scaling or mixing can destroy the meaning of the data.”*
5. **BIT_PERFECT 是设备端（vendor）可选能力，不是平台保证**：AOSP 原文 *“The bit-perfect playback attribute is optional and is supported only in the AIDL
   implementation of the Audio HAL. To support bit-perfect playback, vendors must add the bit-perfect output flag `AUDIO_OUTPUT_FLAG_BIT_PERFECT` to the dynamic
   mix port that can be routed to the USB device.”* 且 *“If the bit-perfect playback attribute is specified, the HAL must ensure that the audio stream is sent to the
   USB device without any modification, that is, the audio stream must not have any volume scaling, sample rate conversion, or audio processing effects and must not
   be mixed in the DSP. In this case, the hardware controls the volume as the framework doesn’t provide any software volume control.”*
   → 因此 `getSupportedMixerAttributes()` 返回的列表里**是否含 BIT_PERFECT 项，是唯一可靠的“该机支持”判据**；小米/MTK 机型是否加了该 flag 必须实机用该 API 探测。
   AOSP 还给出验证方法：用 CTS 的 `PreferredMixerAttributesTestActivity.java`，并用支持 MQA/DSD-over-PCM 的 USB DAC。

**官方 URL**
- https://source.android.com/docs/core/audio/preferred-mixer-attr （Bit-perfect playback attribute / Validation 两节）
- https://developer.android.com/media/platform/improve-audio-playback
- https://developer.android.com/reference/android/media/AudioMixerAttributes#MIXER_BEHAVIOR_BIT_PERFECT
- https://developer.android.com/reference/android/media/AudioFormat#ENCODING_PCM_24BIT_PACKED

---

## 4. 设备选择：setPreferredDevice 语义 + 设备枚举 + 身份字段

**结论**

- `public boolean setPreferredDevice(AudioDeviceInfo deviceInfo)`（**API 23**，实例方法，不在 Builder 上）。
  官方语义原文：*“Specifies an audio device (via an AudioDeviceInfo object) to route the output from this AudioTrack.
  If deviceInfo is null, default routing is restored.”* 返回：*“true if succesful, false if the specified AudioDeviceInfo is non-null and
  does not correspond to a valid audio output device.”*
  源码实现证实：`if (deviceInfo != null && !deviceInfo.isSink()) return false;` 然后 `native_setOutputDevice(id)`。
  → **它是“偏好/尽力而为”，不是硬性绑定**：成功只表示“请求被接受”，不代表实际路由到该设备（见第 6 节校验）。
- 配套读取：`public AudioDeviceInfo getPreferredDevice()`（API 23）：*“Returns the selected output specified by setPreferredDevice.
  Note that this is not guaranteed to correspond to the actual device being used for playback.”*
- 枚举输出设备（API 23+，minSdk 26 安全）：
  `public AudioDeviceInfo[] getDevices(int flags)`，`flags` 取 `GET_DEVICES_INPUTS` / `GET_DEVICES_OUTPUTS`（可组合），
  返回“当前已连接且满足条件的设备数组（可能长度为 0）”；非法 direction 抛 `IllegalArgumentException`。
- 设备热插拔监听：`public void registerAudioDeviceCallback(AudioDeviceCallback callback, Handler handler)`（API 23），
  `AudioDeviceCallback` 回调 `onAudioDevicesAdded(AudioDeviceInfo[])` / `onAudioDevicesRemoved(AudioDeviceInfo[])`。
  （AudioManager 另有 `addOnDevicesChangedListener`，本项目无需用它。）
- 能力预判：`public static int AudioManager.getDirectPlaybackSupport(AudioFormat format, AudioAttributes attributes)`（API 33），
  返回 bitfield，*“Direct playback means that the audio stream is not altered by the framework. The audio stream will not be resampled, volume scaled,
  downmixed or mixed with other content by the framework. But it may be wrapped in a higher level protocol such as IEC61937 for passthrough.”*
  → 这是 26–33 唯一官方的“框架不加工”查询，但**不等于 BIT_PERFECT**（后者还要求 HAL 的 BIT_PERFECT mix port）。
- `AudioDeviceInfo` 身份字段（全部 API 23 起，除注明）：
  - `getId()` → `int`，官方只有一句 *“The internal device ID.”*（**文档没有说明跨会话/跨重启是否稳定**，见第 10 节）。
  - `getType()` → `int`，*“The device type identifier of the audio device (i.e. TYPE_BUILTIN_SPEAKER).”*
    相关常量：`TYPE_USB_DEVICE`、`TYPE_USB_HEADSET`、`TYPE_USB_ACCESSORY`。
  - `getAddress()`（**API 28**）→ `String`，*“The 'address' string of the device. This generally contains device-specific parameters.”* 非 null。
  - `getProductName()` → `CharSequence`，*“The human-readable name of the audio device.”*（面向人，未承诺唯一/稳定）。
  - 能力查询：`getAudioProfiles()`（API 31）→ `List<AudioProfile>`；`getAudioDescriptors()`（API 31）→ `List<AudioDescriptor>`；
    `getSampleRates()` / `getChannelMasks()` / `getChannelIndexMasks()` / `getChannelCounts()` / `getEncodings()`（API 23；空数组表示“任意”）；
    `isSink()` / `isSource()`（API 23）；`getSpeakerLayoutChannelMask()`（**API 36**）。
  - `AudioProfile`：`getFormat()`、`getSampleRates()`、`getChannelMasks()`、`getChannelIndexMasks()`、`getEncapsulationType()`
    （`AUDIO_ENCAPSULATION_TYPE_NONE / PCM / IEC61937`）。
  - `getEncodings()` 的一条实用原文：*“As there is no AudioFormat constant specifically defined for 24-bit PCM, the value ENCODING_PCM_FLOAT indicates that
    AudioTrack or AudioRecord can preserve at least 24 bits of integer precision to that device.”*
- 稳定身份建议（实现取舍）：**不要把 `getId()` 持久化当作跨会话主键**（官方未承诺稳定性）；建议
  `type ∈ {USB_DEVICE, USB_HEADSET}` + `getAddress()` + `getProductName()` 组合做“上次选择”的软匹配，启动时用**本次会话重新枚举**的 `AudioDeviceInfo` 对象去调用 API；
  `productName` 只用于 UI 展示。`getId()` 只在**同一会话/同一次连接内**当作 key 使用。
- 新的设备类型查询：`AudioManager.getSupportedDeviceTypes(int direction)`（API 35）：返回**潜在可连接**的设备类型集合，
  *“Examples include ... TYPE_USB_DEVICE if the Android device supports a USB host-mode port. These are generally a superset of device type identifiers
  associated with the AudioDeviceInfo objects returned from AudioManager.getDevices().”* → 适合“本机是否可能有 USB DAC”的功能门控。

**官方 URL**
- https://developer.android.com/reference/android/media/AudioTrack#setPreferredDevice(android.media.AudioDeviceInfo)
- https://developer.android.com/reference/android/media/AudioTrack#getPreferredDevice()
- https://developer.android.com/reference/android/media/AudioManager#getDevices(int)
- https://developer.android.com/reference/android/media/AudioManager#GET_DEVICES_OUTPUTS
- https://developer.android.com/reference/android/media/AudioManager#registerAudioDeviceCallback(android.media.AudioDeviceCallback,%20android.os.Handler)
- https://developer.android.com/reference/android/media/AudioDeviceCallback
- https://developer.android.com/reference/android/media/AudioDeviceInfo
- https://developer.android.com/reference/android/media/AudioDeviceInfo#getAddress() （API 28）
- https://developer.android.com/reference/android/media/AudioDeviceInfo#getAudioProfiles() （API 31）
- https://developer.android.com/reference/android/media/AudioProfile
- https://developer.android.com/reference/android/media/AudioManager#getDirectPlaybackSupport(android.media.AudioFormat,%20android.media.AudioAttributes) （API 33）
- https://developer.android.com/reference/android/media/AudioManager#getSupportedDeviceTypes(int) （API 35）

---

## 5. 请求不被满足时的实际行为 & 必须检查的返回值

**结论（逐条）**

1. `AudioManager.setPreferredMixerAttributes(...)`：**不抛异常，返回 boolean**。javadoc 原文
   *“Returns boolean: true only if the preferred mixer attributes are set successfully.”*
   源码等价于 `return getService().setPreferredMixerAttributes(attributes, device.getId(), mixerAttributes) == AudioSystem.SUCCESS;`
   → **false 就是“没设上”**（非 USB 设备、非 `USAGE_MEDIA`、格式不在支持列表、被策略拒绝等）。
   `clearPreferredMixerAttributes` 同理返回 boolean。
2. `AudioTrack.setPreferredDevice(...)`：**返回 boolean**（非 null 且不是合法输出 sink 时为 false）；不抛异常。
   **返回 true 也只是“请求受理”，官方明确 `getPreferredDevice()` 不保证等于实际使用设备。**
3. `AudioTrack.Builder.setAudioFormat / setAudioAttributes` 才声明 `throws IllegalArgumentException`；
   `build()` 声明 `throws UnsupportedOperationException`（例如不支持的 offload/封装组合）。
   **构造“失败”通常不是抛异常，而是对象处于 `STATE_UNINITIALIZED`**。
4. **必须检查**：
   - `public int AudioTrack.getState()` —— *“Returns the state of the AudioTrack instance. This is useful after the AudioTrack instance has been created to check
     if it was initialized properly. This ensures that the appropriate resources have been acquired.”* 必须 `== AudioTrack.STATE_INITIALIZED`；
     否则按创建失败处理（不要 start/write）。
   - `public AudioFormat AudioTrack.getFormat()`（API 23）—— *“Returns the configured AudioTrack format.”*
     ⚠️ 注意官方措辞是 **configured**（“配置的”），**不是**“硬件实际格式”；不要把它当作位深/采样率未被改写的证据。
   - `public AudioDeviceInfo AudioTrack.getRoutedDevice()`（API 23）—— 原文：*“Returns an AudioDeviceInfo identifying the current routing of this AudioTrack.
     Note: The query is only valid if the AudioTrack is currently playing. If it is not, getRoutedDevice() will return null.”*
     并新增提示 *“Audio may play on multiple devices simultaneously ..., so prefer using getRoutedDevices().”*
   - `public List<AudioDeviceInfo> AudioTrack.getRoutedDevices()`（**API 36**）—— *“Returns a List of AudioDeviceInfo identifying the current routing of this
     AudioTrack. Note: The query is only valid if the AudioTrack is currently playing. If it is not, getRoutedDevices() will return an empty list.”* 非 null。
     → 36+ 优先用它（`getRoutedDevice()` 只是取列表第 0 个；源码 `audioDeviceInfos.isEmpty() ? null : get(0)`）。
   - `AudioManager.getPreferredMixerAttributes(...)`：确认系统侧偏好仍在（可为 null）。
   - `AudioTrack.addOnRoutingChangedListener(...)`（API 23，两个重载：`AudioRouting.OnRoutingChangedListener` 与
     `AudioTrack.OnRoutingChangedListener`）用于在播放中侦测路由变化。
   - 回退检测：`AudioTrack.getUnderrunCount()`（API 24）只反映欠载，**不能**用来证明 bit-perfect。
5. **官方建议的“真的生效”验证链（文档可证部分）**：
   `getSupportedMixerAttributes(device)` 列表匹配 → `setPreferredMixerAttributes` 返回 true（注意需 `MODIFY_AUDIO_SETTINGS` 且在 foreground/有效生命周期）
   → 用**同一个 AudioAttributes + 同一个 AudioFormat** 构造 AudioTrack → `getState() == STATE_INITIALIZED` → 播放中
   `getRoutedDevices()/getRoutedDevice()` 确认包含目标 USB 设备 → `getPreferredMixerAttributes()` 复核偏好仍在。
   **官方没有提供“读取硬件实际 bit-perfect 状态”的 API**（第 6 节）。

**官方 URL**
- https://developer.android.com/reference/android/media/AudioManager#setPreferredMixerAttributes(android.media.AudioAttributes,%20android.media.AudioDeviceInfo,%20android.media.AudioMixerAttributes)
- https://developer.android.com/reference/android/media/AudioTrack#getState()
- https://developer.android.com/reference/android/media/AudioTrack#STATE_INITIALIZED
- https://developer.android.com/reference/android/media/AudioTrack#getFormat()
- https://developer.android.com/reference/android/media/AudioTrack#getRoutedDevice()
- https://developer.android.com/reference/android/media/AudioTrack#getRoutedDevices() （API 36）
- https://developer.android.com/reference/android/media/AudioTrack#addOnRoutingChangedListener(android.media.AudioTrack.OnRoutingChangedListener,%20android.os.Handler)
- https://developer.android.com/reference/android/media/AudioTrack.Builder

---

## 6. 能否读取“实际 mixer attributes”？26–33 用什么替代？

**结论**

- **AudioTrack 没有 `getMixerAttributes()`**（API 34–37 的 android.jar 与官方页面均无）。
- 能读到的最接近项：
  - `AudioManager.getPreferredMixerAttributes(attributes, device)`（34+）——**读的是系统侧的“偏好”**，未设置/已清除/底层失败都返回 `null`。
    **它不代表“当前正在跑的 mixer 实际属性”**。
  - `AudioMixerAttributes.getFormat()` / `getMixerBehavior()`：只能读你已经拿到的对象（来自 `getSupportedMixerAttributes` 或 `getPreferredMixerAttributes`）。
  - 官方没有 `AudioTrack.getActualMixerAttributes()` 之类的 API（本机 API 37 android.jar 反汇编确认不存在）。
- **API 26–33 可用替代（都是间接证据，需在文案上区分“推断”与“已验证”）**：
  1. `AudioTrack.getRoutedDevice()`（23+）：播放中确认输出落在目标 USB 设备；非播放态返回 null。36+ 用 `getRoutedDevices()`。
  2. `AudioTrack.getFormat()`（23+）：确认 AudioTrack 被配置成的 encoding/sampleRate/channelMask（**configured，非硬件实际**）。
  3. `AudioDeviceInfo.getAudioProfiles()`（31+）/`getSampleRates()`/`getChannelMasks()`/`getEncodings()`（23+）：确认设备声明支持该组合。
  4. `AudioManager.getDirectPlaybackSupport(format, attributes)`（33+）：查询“框架是否可不经重采样/音量缩放/混音/降混直接播放”。
  5. `AudioTrack.getAudioAttributes()`、`getSampleRate()`、`getBufferSizeInFrames()`、`getUnderrunCount()`：辅助诊断。
- 只能 34+ 的：“请求 BIT_PERFECT 混音器属性”本身。→ 代码分叉点：`Build.VERSION.SDK_INT >= 34 && 目标设备为 USB && 支持列表含 BIT_PERFECT` 才启用；
  否则降级为 “preferred device + direct playback 查询 + 明确标注未验证”。

**官方 URL**
- https://developer.android.com/reference/android/media/AudioManager#getPreferredMixerAttributes(android.media.AudioAttributes,%20android.media.AudioDeviceInfo)
- https://developer.android.com/reference/android/media/AudioTrack （方法总表：无 getMixerAttributes）
- https://developer.android.com/reference/android/media/AudioTrack#getFormat()
- https://developer.android.com/reference/android/media/AudioDeviceInfo#getAudioProfiles()
- https://developer.android.com/reference/android/media/AudioManager#getDirectPlaybackSupport(android.media.AudioFormat,%20android.media.AudioAttributes)

---

## 7. Android 15 / 16 / 17(API 37) 与 bit-perfect / 独占 / HiFi 相关的公开 API 变化

**结论：没有任何新增的 bit-perfect / 独占输出 / HiFi 公开 API。** 逐版本核对（本机 android-34/35/36/36.1 jar 的 javap 差异 + API 37 android.jar + 官方 API diff）：

- **API 34（Android 14）**：新增本主题全部 API —— `AudioMixerAttributes`(+Builder)、
  `AudioManager.getSupportedMixerAttributes/setPreferredMixerAttributes/clearPreferredMixerAttributes/getPreferredMixerAttributes/
  add|removeOnPreferredMixerAttributesChangedListener`。
- **API 35（Android 15）**：
  - `AudioManager.getSupportedDeviceTypes(int direction)`（35）——设备类型能力查询（含 “TYPE_USB_DEVICE if the Android device supports a USB host-mode port”）。
  - `AudioDeviceInfo.getSpeakerLayoutChannelMask()` 属 36，不在 35。
  - **AudioTrack / AudioAttributes / AudioMixerAttributes 无任何新增**（34→35 的 javap 对比为空）。
- **API 36（Android 16）**：
  - `AudioTrack.getRoutedDevices()` → `public List<AudioDeviceInfo> getRoutedDevices()`（36）——**多设备同时路由的读取**（本主题相关且实用）。
  - `AudioDeviceInfo.getSpeakerLayoutChannelMask()`（36）——仅内置扬声器布局。
  - AudioTrack 新增 `flushWrittenFramesFromPosition` 等属 37，不在 36。
  - `AudioFormat` 新增一批 IAMF 相关 `ENCODING_IAMF_*` 常量（35/36 引入，与 bit-perfect 无直接关系）。
- **API 37（Android 17）**：官方 API diff (36 → 37, 生成于 2026-03-25)：
  - `android.media.AudioManager`：新增字段 `MODE_ASSISTANT_CONVERSATION`、`STREAM_ASSISTANT`；
    多个 SCO/扬声器电话相关 API 标记为 **deprecated**（`setBluetoothScoOn`、`isBluetoothScoAvailableOffCall`、
    `ACTION_SCO_AUDIO_STATE_UPDATED`、`EXTRA_SCO_AUDIO_STATE*`、`SCO_AUDIO_STATE_*`、`ACTION_SPEAKERPHONE_STATE_CHANGED`）。
    **无 mixer attributes 相关变化。**
  - `android.media.AudioTrack`：新增 `flushWrittenFramesFromPosition(long, int)`、`getWrittenFramesCount()`、
    `getFlushWrittenFramesFromPositionSupport(AudioFormat, AudioAttributes)`、`getCodecProvenance()`、
    常量 `FLUSH_FROM_ACCURACY_BEST_EFFORT`/`FLUSH_FROM_ACCURACY_EXACT`/`FLUSH_WRITTEN_FRAMES_SUPPORTED`。
    **同样没有 mixer attributes / 独占输出相关新增。**
  - `UsbManager`：**没有**音频相关新增（javap 仅 `getDeviceList`、`hasPermission`、`openDevice`、`requestPermission` 等既有 API）。
  - **Android 17 的行为变更（对播放器影响很大）**：“Background audio hardening”，自 Android 17 起音频框架对后台音频交互
    （播放、音频焦点请求、音量 API）加限制；**若 app 不处于有效生命周期状态，音频播放与音量 API 会静默失败（不抛异常、无失败信息）**，
    音频焦点返回 `AUDIOFOCUS_REQUEST_FAILED`；对 targetSdk 37 的 app 更严：前台服务必须具备 while-in-use (WIU) 能力
    （除非持有 exact alarm 权限且操作 `USAGE_ALARM` 流）。
    → 本项目的“前台 service + 有效生命周期”是 bit-perfect 能生效的前提之一，不是可选项。
- 说明：本机 SDK 未安装 android-37 platform（仅有 android-36.1），API 37 结论来自 AGP 下载的 API 37 android.jar 反汇编 + 官方 API diff 页面。

**官方 URL**
- https://developer.android.com/sdk/api_diff/37/changes/android.media.AudioManager
- https://developer.android.com/sdk/api_diff/37/changes/android.media.AudioTrack
- https://developer.android.com/about/versions/17/behavior-changes-all （Background audio hardening）
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/reference/android/media/AudioManager#getSupportedDeviceTypes(int)
- https://developer.android.com/reference/android/media/AudioTrack#getRoutedDevices()

---

## 8. NDK 侧 AAudio（API 26+，NDK 头文件为准）

**结论（签名取自本机 `ndk/28.2.13676358/.../aaudio/AAudio.h`，官方 NDK 参考：/ndk/reference/ 与 /ndk/guides/audio/aaudio/aaudio）**

| 目的 | 精确 API | 引入 |
|---|---|---|
| 共享模式枚举 | `AAUDIO_SHARING_MODE_EXCLUSIVE`（独占：*“This will be the only stream using a particular source or sink. This mode will provide the lowest possible latency. You should close EXCLUSIVE streams immediately when you are not using them.”*）/ `AAUDIO_SHARING_MODE_SHARED`；类型 `aaudio_sharing_mode_t`(=int32_t) | 26 |
| 请求独占 | `void AAudioStreamBuilder_setSharingMode(AAudioStreamBuilder*, aaudio_sharing_mode_t)` —— 默认 `AAUDIO_SHARING_MODE_SHARED`；原文 *“The requested sharing mode may not be available. The application can query for the actual mode after the stream is opened.”* | 26 |
| 读实际共享模式 | `aaudio_sharing_mode_t AAudioStream_getSharingMode(AAudioStream*)` | 26 |
| 选设备 | `void AAudioStreamBuilder_setDeviceId(AAudioStreamBuilder*, int32_t deviceId)` —— 默认 `AAUDIO_UNSPECIFIED`(=0)，此时“primary device”；原文 *“The ID could be obtained from the Java AudioManager. AudioManager.getDevices() returns an array of AudioDeviceInfo, which contains a getId() method. That ID can be passed to this function. It is possible that you may not get the device that you requested. So if it is important to you, you should call AAudioStream_getDeviceId() after the stream is opened to verify the actual ID.”* | 26 |
| 读实际设备 | `int32_t AAudioStream_getDeviceId(AAudioStream*)` | 26 |
| 格式 | `void AAudioStreamBuilder_setFormat(AAudioStreamBuilder*, aaudio_format_t)` / 读 `aaudio_format_t AAudioStream_getFormat(AAudioStream*)` —— *“If an exact value is specified then an opened stream will use that value. If a stream cannot be opened with the specified value then the open will fail.”* | 26 |
| 硬件实际格式 | `aaudio_format_t AAudioStream_getHardwareFormat(AAudioStream*)` —— *“There may be data format conversions in the Audio framework. The data format set in the stream builder may not be actual format used in the hardware. This returns the audio format used by the hardware.”*；`AUDIO_FORMAT_PCM_8_24_BIT` 会报为 `AAUDIO_FORMAT_PCM_I24_PACKED`；不认识的硬件格式返回 `AAUDIO_FORMAT_INVALID` | 34 |
| 采样率 | `void AAudioStreamBuilder_setSampleRate(AAudioStreamBuilder*, int32_t)` / 读 `int32_t AAudioStream_getSampleRate(AAudioStream*)` —— 指定则“exact value”，打不开就 open 失败 | 26 |
| 声道数 / 声道掩码 | `setChannelCount` / `getChannelCount`（26）；`setChannelMask(AAudioStreamBuilder*, aaudio_channel_mask_t)`（**32**，最后调用的那个生效） | 26 / 32 |
| 性能模式 | `AAudioStreamBuilder_setPerformanceMode` / `AAudioStream_getPerformanceMode`（`AAUDIO_PERFORMANCE_MODE_NONE=10 / POWER_SAVING / LOW_LATENCY …`） | 26 |
| 打开/释放 | `aaudio_result_t AAudioStreamBuilder_openStream(builder, &stream)`；`AAudioStream_release()`（30）、`AAudioStream_close()`（26） | 26 |
| 错误码转文本 | `const char* AAudio_convertResultToText(aaudio_result_t)` | 26 |
| 错误回调 | `AAudioStreamBuilder_setErrorCallback(builder, cb, userData)`（头文件中该组函数）/新增 `AAudioStreamBuilder_setPresentationEndCallback`（**36**，offload 播放结束回调） | 26 / 36 |

- **AAudio 没有自己的设备枚举 API**（头文件与官方 AAudio 指南都指向 Java）：指南原文
  *“You can use the AudioManager method getDevices() to discover the audio devices that are available on your Android device.”*、
  *“Each audio device has a unique ID on the Android device. You can use the ID to bind an audio stream to a specific audio device.”*
  （在纯 native 层没有 `AAudio_getDevices()` 之类接口 —— AAudio.h 全文只引用 `AudioManager#getDevices(int)`。）
- **AAudio 与 AudioTrack 在设备选择上的差异（事实级）**：
  - AudioTrack：`setPreferredDevice(AudioDeviceInfo)` + `getPreferredDevice()` + `getRoutedDevice()/getRoutedDevices()`；是**偏好**，可被策略改写。
  - AAudio：`setDeviceId(int)` 只接受**整数 device id**（来源于 Java `AudioDeviceInfo.getId()`），且**不能用 AudioDeviceInfo 对象**；
    文档明确“可能拿不到你请求的设备”，必须用 `AAudioStream_getDeviceId()` 复核。
  - 独占语义：AAudio 有 `AAUDIO_SHARING_MODE_EXCLUSIVE`（共享模式层）；**AudioTrack 的 Java 公开 API 没有 sharing mode 设置项**
    （平台侧的 exclusive 由 mixer/direct/offload 等策略决定），因此“Android 上的独占输出”在两个 API 上不是同一个维度：
    AAudio 是“stream 独占设备”，AudioTrack 路线是“BIT_PERFECT 混音器属性 + 音频策略选到 direct/bit-perfect mix port”。
  - AAudio 没有 mixer attributes / `USAGE_MEDIA` 偏好配置 API，**不能通过 AAudio 请求 BIT_PERFECT**（34+ 亦然）。

**官方 URL**
- https://developer.android.com/ndk/guides/audio/aaudio/aaudio （Audio device / Sharing mode / Creating an audio stream / Disconnected audio stream 各节）
- https://developer.android.com/ndk/reference/group/aaudio （函数索引；本机核查时该路径返回 404，函数签名以上面 NDK 头文件为准）
- https://developer.android.com/reference/android/media/AudioDeviceInfo#getId()
- https://developer.android.com/reference/android/media/AudioManager#getDevices(int)

---

## 9. Media3 / ExoPlayer：能否设置 preferred device 或 mixer attributes；能否 bit-perfect

**结论（以本机 media3-exoplayer 1.11.1 的 classes.jar 反汇编 + 官方 javadoc 为准）**

- **有**：`androidx.media3.exoplayer.audio.AudioSink` 有
  `public default void setPreferredDevice(@Nullable AudioDeviceInfo audioDeviceInfo)` —— 官方 javadoc 原文
  *“Sets the preferred audio device.”*，参数 *“The preferred audio device, or null to restore the default.”*
  另有 `public default void setVirtualDeviceId(int virtualDeviceId)`。
- **实现路径（反汇编证实）**：`DefaultAudioSink.setPreferredDevice(AudioDeviceInfo)` 只是
  `this.preferredDevice = audioDeviceInfo; if (audioOutput != null) audioOutput.setPreferredDevice(preferredDevice);`
  → 最终落到 `AudioTrack.setPreferredDevice`（即第 4 节的“偏好”语义）。
- **ExoPlayer.Builder 在 1.11.1 已没有 `setAudioSink(...)`**（反汇编 ExoPlayer$Builder 无该方法）。
  定制 audio sink 的公开入口是 `ExoPlayer.Builder.setAudioOutputProvider(AudioOutputProvider)`（在 Builder 的 audio-sink provider 体系里，
  另有 `AudioSink.AudioSinkConfig`、`AudioTrackAudioOutputProvider`、`ForwardingAudioSink` 等新 API 类型出现在 1.11.1 javadoc 中），
  以及 `DefaultAudioSink.Builder` 的 `setAudioProcessorChain` / `setAudioProcessors` / `setEnableFloatOutput` /
  `setAudioTrackBufferSizeProvider` / `setAudioOffloadSupportProvider` / `setAudioTrackProvider` / `setAudioOutputProvider`。
  官方 javadoc：https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder
- **没有**：Media3 1.11.1 的 `AudioSink` / `DefaultAudioSink` 及其 Builder **完全没有 mixer attributes 相关 API**
  （反汇编中 "Mixer" 零命中；无 `setPreferredMixerAttributes`、无 `AudioMixerAttributes` 参数）。
- **因此 Media3 无法做到 BIT_PERFECT 输出**（严格说：无法**请求** platform 的 BIT_PERFECT mix port），原因：
  1. BIT_PERFECT 请求必须由 app 调 `AudioManager.setPreferredMixerAttributes(USAGE_MEDIA, usbDevice, mixerAttributes)` 且要求播放的
     `AudioFormat` 与 mixer attributes 完全一致（encoding/channelMask/sampleRate）——Media3 无此调用，也不会把 mixer attributes 传给 AudioTrack。
  2. Media3 的音频管线会插入自己的处理链：`DefaultAudioSink` 默认启用 AudioProcessor 链（含音量/静音/变速等），
     并且可启用 float 输出（`setEnableFloatOutput`）；即使不改动样本，管线也不承诺“原样直送 HAL”。
     官方文档对 BIT_PERFECT 的要求是 *“no audio mixing, volume adjustment, or audio processed effect is applied to the playback”*，
     与播放器库自有增益/处理链天然冲突。
  3. Media3 走 `AudioTrack` 常规路径，最终是否选到 direct/offload 由平台策略决定；Media3 提供的 compensations（offload、direct）不等价于 BIT_PERFECT。
- **可用的官方扩展点（若必须留在 Media3）**：自定义 `AudioSink`（经 `setAudioOutputProvider`/`ForwardingAudioSink` 包装 `DefaultAudioSink`），
  在其创建 `AudioTrack` 后自行调用 `AudioManager.setPreferredMixerAttributes` 并**关闭所有会改动样本的处理**（音量、float 输出、DSP）。
  但这不是“官方保证 bit-perfect”，官方文档没有任何“Media3 支持 bit-perfect”的表述 —— 需要自行做端到端验证。

**官方 URL**
- https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioSink#setPreferredDevice(android.media.AudioDeviceInfo)
- https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink
- https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder
- https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder
- https://developer.android.com/media/platform/improve-audio-playback （BIT_PERFECT 的官方要求）

---

## 10. AAudio 独占失败时的返回码与官方回退/检测策略

**结论（错误码取自本机 NDK `AAudio.h` 原文）**

- `typedef int32_t aaudio_result_t;` 成功为 `AAUDIO_OK`(=0)，错误为负值；**基址 `AAUDIO_ERROR_BASE = -900`**。相关枚举原文：
  - `AAUDIO_ERROR_DISCONNECTED`：*“The audio device was disconnected. This could occur, for example, when headphones are plugged in or unplugged.
    The stream cannot be used after the device is disconnected. Applications should stop and close the stream. If this error is received in an error callback
    then another thread should be used to stop and close the stream.”*
  - `AAUDIO_ERROR_UNAVAILABLE`：*“A resource or information is unavailable. This could occur when an application tries to open too many streams, or a timestamp is not available.”*
    → **“独占申请失败”最常见的就是它（设备已被别的 stream 占用）**。
  - 其他：`AAUDIO_ERROR_ILLEGAL_ARGUMENT`、`AAUDIO_ERROR_INTERNAL`、`AAUDIO_ERROR_INVALID_STATE`、`AAUDIO_ERROR_INVALID_HANDLE`、
    `AAUDIO_ERROR_UNIMPLEMENTED`、`AAUDIO_ERROR_NO_FREE_HANDLES`、`AAUDIO_ERROR_NO_MEMORY`、`AAUDIO_ERROR_NULL`、`AAUDIO_ERROR_TIMEOUT`、
    `AAUDIO_ERROR_WOULD_BLOCK`、`AAUDIO_ERROR_INVALID_FORMAT`、`AAUDIO_ERROR_OUT_OF_RANGE`、`AAUDIO_ERROR_NO_SERVICE`、`AAUDIO_ERROR_INVALID_RATE`。
- **头文件的明确警告**：*“Note that error return codes may change in the future so applications should generally not rely on specific return codes.”*
  → 不要按具体数值分支；用 `AAudio_convertResultToText()` 记日志，行为按“失败即回退/报错”分类。
- **官方推荐的检测/回退方式（原文级）**：
  1. **不要假设请求被满足**：*“The requested sharing mode may not be available. The application can query for the actual mode after the stream is opened.”*
     → 打开后必须 `AAudioStream_getSharingMode()`，若返回 `AAUDIO_SHARING_MODE_SHARED` 即“独占未生效”。
     同理设备用 `AAudioStream_getDeviceId()` 复核；格式/采样率/声道指定值不会变，但共享模式与 buffer capacity 可能变。
  2. **打开失败就是失败**：`AAudioStreamBuilder_openStream()` 返回非 `AAUDIO_OK` 时，不得继续使用 stream；
     setter 函数本身不报错（*“Note that these methods do not report errors, such as an undefined constant or value out of range.”*），所以只能靠 open 与后续查询。
  3. **断连必须停并关**：*“When a stream is disconnected, it has the state 'Disconnected' and any attempts to execute AAudioStream_write() or other functions will return an error.
     You must always stop and close a disconnected stream, regardless of the error code.”* 使用数据回调时不会收到返回码，必须
     `AAudioStreamBuilder_setErrorCallback()` 注册错误回调，并且**在另一个线程里 stop/close**（回调线程内直接关会死锁）。
     断连触发条件官方列了三条：设备不再连接、内部错误、该设备不再是 primary device。
  4. 独占 stream 用完立刻关闭（*“You should close exclusive streams as soon as you no longer need them”*），否则后续独占申请容易 `AAUDIO_ERROR_UNAVAILABLE`。
  5. 失败后的产品级回退（官方未规定“必须降级到共享”这一具体策略，只能自行定义并如实上报）：常见做法是
     独占失败 → 报“未能独占” → 由用户选择继续共享播放或停止；**不要在未告知用户的情况下静默降级**（本项目规则亦如此）。

**官方 URL**
- https://developer.android.com/ndk/guides/audio/aaudio/aaudio#disconnected （Disconnected audio stream）
- https://developer.android.com/ndk/guides/audio/aaudio/aaudio#creating （Creating an audio stream / 配置校验清单）
- 错误码枚举与原文：本机 NDK `aaudio/AAudio.h`（对应官方 NDK 参考的 aaudio 分组页 https://developer.android.com/ndk/reference/group/aaudio ）

---

## 11. 实现必须检查的返回值清单（可直接当 review checklist）

| # | 检查项 | 期望 | 不满足时 |
|---|---|---|---|
| 1 | `Build.VERSION.SDK_INT >= 34` | true 才走 BIT_PERFECT 分支 | 26–33 走 preferred device + 明确标注“未验证 bit-perfect” |
| 2 | `AudioManager.getSupportedMixerAttributes(usbDevice)` | 非 null；含 `mixerBehavior == MIXER_BEHAVIOR_BIT_PERFECT` 且 `format` 与源格式一致的项 | 无该项 → 该机/该 DAC 不支持，UI 不得显示“bit-perfect 可用” |
| 3 | `AudioManager.setPreferredMixerAttributes(attr(USAGE_MEDIA), device, mixer)` | 返回 `true` | false → 提示原因（非 USB / 非 USAGE_MEDIA / 格式不支持），不静默继续 |
| 4 | manifest `MODIFY_AUDIO_SETTINGS` | 已声明 | 否则 set/clear 静默失败或异常 |
| 5 | 应用处于有效生命周期 / 前台（Android 17 后台音频加固） | 前台 service 且有 WIU 能力 | Android 17 起音频 API 静默失败，必须提前处理 |
| 6 | `AudioTrack.getState()` | `== AudioTrack.STATE_INITIALIZED` | 视为创建失败，不得 start/write |
| 7 | `AudioTrack.Builder.setAudioFormat(mixerAttributes.getFormat())` + `setAudioAttributes(同一 AudioAttributes)` | 格式与 attributes **完全一致** | 不一致 → 播放不会被挂到该 mixer 输出 |
| 8 | `AudioTrack.setPreferredDevice(device)` | 返回 `true`（且 `device.isSink()`） | false → 非法 sink；true 也只是“请求受理” |
| 9 | 播放中 `AudioTrack.getRoutedDevices()`（36+）/ `getRoutedDevice()`（23+） | 列表包含目标 USB 设备 / 非 null 且 id 匹配 | 不匹配 → 实际未走 DAC，按规则暂停并报错 |
| 10 | `AudioTrack.getFormat()` | 与请求一致（注意：configured，不等于硬件实际） | 不一致 → 至少说明 AudioTrack 侧未按预期 |
| 11 | `AudioManager.getPreferredMixerAttributes(attr, device)` | 非 null 且等于你设置的对象 | null → 偏好已被清除/被拒 |
| 12 | `AudioTrack.addOnRoutingChangedListener(...)` | 播放中路由变化能收到回调 | 设备失联 → 立即 pause/flush，不继续向扬声器写 |
| 13 | `AudioDeviceInfo.getAudioProfiles()` / `getEncodings()` | 声明的 sampleRate/channelMask/encoding 覆盖源格式 | 不覆盖 → 必然重采样/转换，撤销 bit-perfect 资格 |
| 14 | AAudio：`AAudioStreamBuilder_openStream()` | `== AAUDIO_OK` | 非 OK → 记 `AAudio_convertResultToText()`，停止使用 |
| 15 | AAudio：`AAudioStream_getSharingMode()` | `== AAUDIO_SHARING_MODE_EXCLUSIVE` | SHARED → 独占未生效，按策略降级并如实上报 |
| 16 | AAudio：`AAudioStream_getDeviceId()` | == 请求的 device id | 不一致 → 未拿到目标设备 |
| 17 | AAudio：`AAudioStream_getHardwareFormat()`（34+） | 与源位深一致（如 `AAUDIO_FORMAT_PCM_I24_PACKED`） | `AAUDIO_FORMAT_INVALID` / 不一致 → 硬件路径非原样 |
| 18 | AAudio：错误回调 + 断连处理 | `AAUDIO_ERROR_DISCONNECTED` 时另起线程 stop/close | 禁止在回调线程内 close（死锁） |
| 19 | 收尾 | `clearPreferredMixerAttributes()` 返回 true + `removeOnPreferredMixerAttributesChangedListener` | 否则偏好残留影响同 uid 后续播放 |

---

## 12. 文档未能证实 / 无法核实的点（不要写成已确认能力）

1. **`AudioTrack.Builder.setPreferredMixerAttributes(...)`、`AudioTrack.getSupportedMixerAttributes(AudioDeviceInfo)`、
   `AudioTrack.getMixerAttributes()`、`AudioAttributes.setPreferredMixerAttributes / clearPreferredMixerAttributes` —— 均未能证实存在。**
   官方 API 34–37 android.jar 反汇编、官方参考页、AOSP 源码三方一致表明**不存在**（不是“签名不确定”，是“没有这些成员”）。
   若某个第三方资料给了这些签名，属杜撰或与 `AudioManager` 版本混淆。
2. **`AudioTrack.getMixerAttributes()`（读取“实际 mixer attributes”）不存在** → “如何读取实际 mixer 属性”**未能证实**有公开 API。
   `AudioManager.getPreferredMixerAttributes` 读的是偏好，不是运行时实际值。
3. **BIT_PERFECT 的精确资格判定规则未能证实**：官方只写“encoding format / channel mask / sample rate 匹配”，
   **没有**列出“允许哪些 `ENCODING_*`”“是否禁止 24in32”“是否排除 32-bit float”“声道掩码是否必须与设备 profile 完全相等”。
   唯一官方示例是 `ENCODING_PCM_24BIT_PACKED + CHANNEL_OUT_STEREO + 44100Hz`，不能当成硬性要求。
4. **`setPreferredMixerAttributes` 具体失败原因的枚举未能证实**：javadoc 只承诺“成功返回 true”，
   没有 Throws 声明，也没有失败原因码；`false` 的具体成因需自行按“非 USB/非 USAGE_MEDIA/不在支持列表/策略拒绝”分类猜测并做实测。
5. **AudioTrack 请求 mixer attributes 或 preferred device 不被满足时是否“静默回退”到默认设备 —— 官方无明文**。
   文档只保证：`setPreferredDevice` 的返回值含义、`getPreferredDevice` 不保证等于实际设备、
   `getRoutedDevice()` 在非播放态返回 null。（“静默回退”是从这两句推断的行为，不是官方承诺。）
6. **`AudioDeviceInfo.getId()` 是否跨会话/跨重启稳定 —— 官方文档未说明**（原文只有 “The internal device ID.”）。
   因此“id 是会话内唯一、跨会话不稳定”属常见实践说法，**未能证实**为官方保证；
   `getAddress()` 官方只说是“generally contains device-specific parameters”，**不构成稳定标识符的承诺**。
   跨会话记住 USB DAC 只能做“软匹配 + 重新枚举”，不能依赖持久化 id。
7. **小米（HyperOS/MTK 平台）是否实现 `AUDIO_OUTPUT_FLAG_BIT_PERFECT` 与 `MIXER_BEHAVIOR_BIT_PERFECT` —— 文档无法证实**，
   必须在该机上调用 `AudioManager.getSupportedMixerAttributes(usbDevice)` 实测（AOSP 明确该能力为 vendor **可选**，仅 AIDL Audio HAL 支持）。
8. **是否存在硬件/驱动层“真正 bit-perfect 已生效”的可读取状态 —— 未能证实**有公开 API；
   端到端验证只能靠 AOSP 建议的方式（CTS `PreferredMixerAttributesTestActivity`、支持 MQA/DSD-over-PCM 的 DAC、外部采集与位比对），
   `getFormat()` / `getRoutedDevice()` / `dumpsys` / 听感**都不构成端到端证明**。
9. **Media3 官方是否有 bit-perfect 支持计划 —— 未能证实**；1.11.1 的公开 API 中无 mixer attributes 支持，官方文档亦无相关表述。
10. **Android 17 是否新增任何 bit-perfect / 独占 / HiFi 公开 API —— 未能证实有**；API 37 diff 中 media 相关新增只有
    AudioManager 的 assistant/SCO 变更与 AudioTrack 的 flush/codec-provenance 系列，均与本主题无关。
11. **本机核查路径的说明（供复核）**：verification 使用的文档镜像为 developer.android.google.cn / source.android.google.cn；
    developer.android.com 在本机网络不可直连（含手机侧亦不可达），但镜像内容与官方同源、并逐页与 URL 一一对应。
    API 37 的结论来自官方 API diff 页 + AGP 下载的 API 37 android.jar；本机 SDK 平台目录最高到 android-36.1（无 android-37 平台目录）。
