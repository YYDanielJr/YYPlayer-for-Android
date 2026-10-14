# H01–H05 证据：音频核心、真实 PCM 输出与媒体会话（dev-hifi）

## H05 全量审计与歌曲详情输出控制（2026-10-08）

最新设备为小米 17 Pro Max / 2509FPN0BC，adb `192.168.1.246:33103`。下面的旧地址与 H01–H04 记录保留为历史。当前 USB 919（lifeme HiFi Audio Pro）、BT A2DP 903（OPPO Enco Free4）；ID 随重接变化。DAC 的 42 个 mixer 组合仍全为 DEFAULT，不具备 BIT_PERFECT。

审计范围、发现及修复见 [HIFI_CODE_AUDIT](../../HIFI_CODE_AUDIT.md)，架构决定见 ADR0006 H05。歌曲详情新增同页“音频输出”入口，内含“输出方式／输出设备”，直接控制唯一 runtime；默认兼容系统路径与自研路径都能选设备。自研路径名“自研输出（原样优先）”，不能从名称推断流独占。

| 检查 | 本版证据／结果 |
| --- | --- |
| 最终构建／JVM／lint | [h05_oct08_gate_checks](h05_oct08_gate_checks.txt)：BUILD SUCCESSFUL / exit0（1m3s）；JVM 28 项 0 失败；lint 0 error / 1 warning / 0 hint，warning 为 ExportedService |
| 核心主机与 arm64 真机 | [host](h05_oct08_core_test.txt)、[device](h05_oct08_device_core_test.txt)：两侧 checks=6390 failures=0 RESULT OK；[NDK build](h05_oct08_ndk_core_build.txt) exit0 |
| 修复前短尾部失败 | [首次29项](h05_oct08_device_test.txt)：28 过／1 失败，暂停/flush 后 1024 帧不足 AudioTrack 启动门槛，导致等待旧尾部卡住；保留失败，不计为通过 |
| 修复后审计类 | [h05_oct08_audit_retest](h05_oct08_audit_retest.txt)：OK (7 tests)，18.282s；含格式重配及 flush、自检互斥／静音／位置、实际路由／速度、JNI 与证据负例 |
| 修复后完整组合 | [h05_oct08_regression_test](h05_oct08_regression_test.txt)：**OK (29 tests)**，102.031s；[仅本应用音频日志](h05_oct08_regression_logcat_audio.txt) |
| 最终自检 gate 后完整组合 | [h05_oct08_final_regression_test](h05_oct08_final_regression_test.txt)：**OK (29 tests)**，103.102s；[音频日志](h05_oct08_final_logcat_audio.txt)，新增高级策略／backend rebuild 不得重建歌曲输出的断言 |
| 固定 USB 的整曲 | [test](h05_oct08_usb_full_test.txt)、[audio log](h05_oct08_usb_full_logcat_audio.txt)：**OK(1)**，280.214s；s24_3/44100Hz、requested=919 routed=[919]，正常ENDED，decoded=submitted=played=12109193，buffered=0 partial=0；metadata12109198差5帧为1ms舍入范围 |
| 歌曲详情 UI | [操作与版本记录](h05_ui_receipt.md)、[截图](shots/h05_music_output.png)：真实库／真实播放，方式和USB/A2DP设备切换，MediaSession仍PLAYING／215218ms／无错误，恢复原偏好后暂停 |
| 2 ABI so／ZIP 16KB | [ELF](h05_oct08_elf.txt)：arm64-v8a / x86_64 各 libyyplayer_jni、libc++_shared、libandroidx.graphics.path 共6个 so，LOAD 全为0x4000；[zipalign](h05_oct08_final_zipalign.txt) 验证成功 |

USB／BT 真机切换以 **实际 AudioTrack route** 断言，SYSTEM 使用测试反射读取锁定 Media3 1.11.1 的 AudioTrack，自研读取 sink 的报告；pin 返回值不当证据。四组合 position 均增长（SYSTEM USB 919／BT903，NATIVE USB919／BT903），系统2× → 自研1× → 系统2× 保留意图。真实 service/controller 仍验证 pause/resume/seek/next、player 改绑与 UI owner 释放后的后台播放。USB 24packed pin 后回旧BT的早期失败见 `h05_device_initial_failed_test.txt`／`h05_route_logcat.txt`；开 track 前请求匹配 DEFAULT mixer 后 `h05_audit_mixer_test.txt` OK(5)，最新7项及29项继续通过；DEFAULT 不增加原样／独占资格。

设备更新均 install -r，两 APK 安装 Success。最终 app APK SHA256：**`1219906f9f6b00dcfe21e9c904322a89b85228eaacdd787e94dd4898bc7d8734`**，最终 ZIP 检查 [h05_oct08_gate_zipalign](h05_oct08_gate_zipalign.txt)。整曲使用前一个 `d30234def5969aea83adab453e295eeecc2dc9b8039244ed87d042f169c3fb0b` 构建；其后只补 runtime/backend 自检 rebuild gate 与断言，未改 PCM/sink，最终版再跑完整29项及真实UI，不混写哈希。完整复现命令在 HANDOFF 顶部 H05；不要 connectedDebugAndroidTest 或清库。

边界：API37 真机与 API16KB ELF/ZIP 检查不能替代 API26–32 类加载、API26–30 短文件排空、物理拔插／焦点、200% 字体／TalkBack、锁屏长测、高采样率或 Oboe 独占。配置 API 格式不等于 HAL／hardware；独占、BIT 正面契约、数字 capture 仍未验。普通播放 MediaCodec → 自研 AudioSink → AudioTrack，core 负责精确整数加宽，核心环仅用于参考信号。自研不提供浮点源、变速、encoder delay-padding 修剪、声道重排、tunneling／aux effect 或额外 session/attributes 定制。

日期：2026-10-06（H01 首轮为 2026-10-05，H02 同日，H03 同日）
设备：小米 2509FPN0BC（Wi-Fi adb `192.168.114.116:33077`），Android 17 / SDK 37，HyperOS OS4.0.0.44.XPBCNXM，ABI 仅 `arm64-v8a`
外接：**USB DAC「Synaptics lifeme HiFi Audio Pro」**（`/dev/bus/usb/001/002`，VID 1739 / PID 5525，ALSA card 1，UAC2 带 3 个播放 alt setting）与**蓝牙耳机「OPPO Enco Free4」**（A2DP + SCO）同时在线。

## H04 接续验收（2026-10-06）

本轮首先重装 12:50 的未验证 APK，原停摆仍复现：186030 帧（约 4.2 s）、位置 −999995781 ms，释放也阻塞。因挂住而 force-stop 本应用，`h04_initial_playback_test.txt` 的 Process crashed 不能计为通过。已纠正交接根因：原 sink 把 FLAC 的 pcmEncoding 元数据当作 buffer 格式，Media3 走压缩 bypass；位置忽略约 10^12 µs 的 renderer 时间偏移。现在只接受 audio/raw，整数源显式请求整数解码，并用首输入时间戳 + 实际播放头，非阻塞写入和短写重试。

| 检查 | 证据与结果 |
| --- | --- |
| 首次修复尝试失败 | [h04_initial_logcat_audio](h04_initial_logcat_audio.txt)；[instrumentation](h04_initial_playback_test.txt) |
| 连续播放和 SYSTEM 对照 | [h04_playback_test](h04_playback_test.txt)：OK (2 tests)，修复时钟与 MIME 后超过 16 s；此过渡构建接受 float，保留为诊断证据 |
| 24-bit FLAC 真实整数解码 | [h04_integer_playback_test](h04_integer_playback_test.txt)：OK (1 test)；[h04_integer_logcat_audio](h04_integer_logcat_audio.txt)：`s24_3/44100Hz`、16 s 时 position=15560、decodedFrames=693268、state=3、isPlaying=true、partial=0 |
| 整曲 | [h04_full_playback_test](h04_full_playback_test.txt)、[h04_full_logcat_audio](h04_full_logcat_audio.txt)：**OK (1 test)**（280.196 s）；最终 state=4，decodedFrames=submitted=played=12109193、buffered=0、partial=0，与 metadata 估计相差 5 帧 |
| 最终组合真机回归 | [h04_regression_test](h04_regression_test.txt)：**OK (22 tests)**（78.68 s）；3 个播放／严格拒绝 + 1 个真实 service/controller + 7 个策略 + 11 个 sink 用例 |
| 最终构建、单测、lint | [h04_final_checks](h04_final_checks.txt)；JVM 28 项 0 失败；BUILD SUCCESSFUL / exit0；lint 0 errors、1 warning（系统可绑定的 ExportedService）、1 hint（既有 tick） |
| 普通启动 | [h04_normal_launch](h04_normal_launch.txt)：无 extra 的 MainActivity 为 topResumedActivity；最终 app APK SHA256 `13194d6b6d812ceb3ed892589af556a8ea03141e1a0ca2adafb6eb8dd9f40c8f`，已 install -r |
| 可移植核心 | [h04_core_test](h04_core_test.txt)：checks=6339 failures=0 RESULT OK，cmake build exit0 |

整曲判据与交接修正：该 FLAC metadata 为 274585 ms / 44100 Hz，估计 12109198 帧；毫秒 metadata 会舍入，测试允许 1 ms 内差值。播放途中应为 READY/isPlaying，**完成整曲应为 ENDED，而不是继续要求 isPlaying=true**。普通短测只有 16 秒，不能用它证明整曲。

复现使用 `adb install -r` 两 APK 和直接 `am instrument -w`，不用会卸载应用的 connectedDebugAndroidTest。整曲加 `-e fullPlayback true -e class 'yy.daniel.yyplayerforandroid.NativeOutputPlaybackTest#nativeSinkPlaysALocalFileThroughExoPlayer'`；组合回归使用 NativeOutputPlaybackTest、PlayerSessionServiceDeviceTest、AudioOutputDeviceTest、AudioOutputSinkDeviceTest。完整命令见 [HANDOFF](../../HANDOFF_HIFI.md) 顶部。

服务检查用真实 MediaController 绑定 MediaSessionService，测试暂停／续播、60 s seek、SYSTEM ↔ NATIVE_EXACT 后改绑、队列切换归零、生产 UI stop/release 引用释放后的后台连续播放。[h04_session_test](h04_session_test.txt) **OK (1 test)**，9.496 s，日志 uiOwnersReleased=true；Compose Activity 启动挂起的记录单独保留，本测试没有 UI 自动化资格。最终 package 更新后重跑短测和 service，且不把控制器通过写成已目检 HyperOS 灵动岛的所有形态。

限定：普通播放由 renderer 直接非阻塞投递到 AudioTrack，核心承担整数加宽；H02 自检仍走核心环泵。AudioTrack 配置回报不证明 HAL 硬件格式、未处理或流独占；此 DAC 无 BIT_PERFECT，端到端位准确亦无数字捕获证据。物理拔插／焦点、锁屏长测、API26 实机、96kHz 以上和 Oboe 尚未验。自研通道实际固定 1×／不跳过静音；SYSTEM 保留原 Media3 能力。旧 H01–H03 下方为历史记录，资格以本段边界为准。

| 问题 | 实测结果 |
| --- | --- |
| 16-bit 源能否以 24-bit 容器输出 | **能**。`s24_3` 被平台接受并按 `s24_3/48000Hz` 打开，336000 帧全部送达 USB DAC |
| 32-bit 容器 | **能**。`s32` 被平台接受并按 `s32/48000Hz` 打开，336000 帧全部送达 |
| 24-in-32 容器 | **平台没有这个编码**。`AudioFormat` 只有 3 字节 packed 与 32-bit 两种，`s24_in32` 被 sink 以 `sink_bad_format` 明确拒绝（见第 4 节） |
| 核心的“无损加宽”是否正确 | **原实现有真实缺陷**：只做符号扩展、不左移，16-bit 源进 24-bit 容器会落在 **−90 dBFS**（低 48 dB）。已修正为按位深差左移并补零，是精确整数运算、不丢位、不经浮点 |
| 非 USB 路由能否报告位完美 | **不能**，且已实测三种路由：USB DAC（`usb=true`）、蓝牙 A2DP（`usb=false`）、内置扬声器（`usb=false`）全部 336000 帧送达，非 USB 两种一律 `systemReportedBitPerfect=false` |
| 端到端位准确 | **仍未取得**（无数字捕获） |
| `system_reported` 级位完美 | **本机仍取不到**（该 DAC 无 BIT_PERFECT 组合） |


## 0. H03 结论速览（本轮实测）

`Convert()` 把窄容器样本搬进宽容器时只做符号扩展，等于把数值当电平原样保留：

```
s16 32767（满刻度） ──符号扩展──> s24 32767 = 满刻度的 1/256 = −48 dB
```

整数 PCM 的满刻度是 `2^(bits-1)`，样本是“在容器量程里的位置”，不是绝对幅度，所以同一数值在更宽的容器里是更小的信号。
修正后按位深差左移并补零：

```
s16 −12345  ──>  s24_3   00 C7 CF        (−12345 << 8)
            ──>  s24_in32 00 C7 CF FF     (−12345 << 8)
            ──>  s32     00 00 C7 CF     (−12345 << 16)
```

这是精确整数运算：新增的低位全是零，不丢有效位、不经浮点，所以仍在“原样（pristine）”路径上，不需要舍入或抖动。
同宽容器互转（`s24_in32 ↔ s24_3`）位移为 0，是纯搬移，数值完全不变。
**用户可听后果**：修好之前，任何 16-bit 音源在 24/32-bit 输出路径上都会低 48 dB（几乎听不见）；修好后满刻度仍是满刻度。

主机核心断言从 2345 增到 **6339**（新增的都是电平不变量：满刻度比例、位移后字节、48-bit 边界等）。
测试同时确认**降位深仍被拒绝**（`kLossy`），没有被这次修改放宽。

## 1. 本轮修掉的真实缺陷：加宽改变电平

| 问题 | 实测结果 |
| --- | --- |
| USB DAC 是否提供 BIT_PERFECT 混音器组合 | **否**。系统报告 42 个组合（16000/32000/44100/48000/96000/192000/384000 Hz × s16/s24_3/s32 × mask 12/0），**全部为 `MIXER_BEHAVIOR_DEFAULT`** |
| 应用是否伪造位完美 | 不伪造。核心判定 `requestBitPerfect=false`，界面显示“不可用 / 该设备组合没有系统位完美契约” |
| 核心 PCM 环是否接到真实输出 | 是。336000 帧参考信号经环 → AudioTrack 全部送出，`dropped=0`，`consumed=336000` |
| 决策格式是否真正作用到播放 | 是。`getRoutedDevices()` = **id 7927「USB-Audio - lifeme HiFi Audio Pro」**，`routedIsUsb=true` |
| 平台播放 xrun | `AudioTrack.getUnderrunCount()` = **0** |
| `setPreferredDevice` 是否可信 | **不可信**。小米返回 `false`，但平台仍路由到该设备（`pinAccepted=false, matches=true`） |
| 端到端位准确 | **未取得**。无数字捕获，状态最高只能是 `system_reported`，本机连该级别也不成立 |
## 2. H02 结论速览（上一轮实测，保留）

## 3. H02 修掉的 H01 遗留缺陷：七个 JNI 入口从未解析成功


`native/jni/yy_jni.cpp` 里 7 个函数的导出名少了 `_00024Bridge_`：

```
Java_..._NativeAudioCore_createRing              ← 错误（H01）
Java_..._NativeAudioCore_00024Bridge_createRing  ← 正确
```

Kotlin 把嵌套 `Bridge` 对象编译成独立顶层类，**全部 10 个 native 声明都在它上面**，因此
`createRing` / `ringWrite` / `ringRead` / `ringClear` / `ringStats` / `releaseRing` / `convertPcm`
这 7 个入口**从来没有解析成功过一次**：`System.loadLibrary` 照常成功，只有调用抛
`UnsatisfiedLinkError`，而 Kotlin 包装把它们吞成“原生不可用”。H01 的 7 项真机测试没有一项调用到环，
所以这个缺陷当时不可见。

修法不只是改名字：现在改用显式 `RegisterNatives` 表，签名或所在类不匹配会让
`JNI_OnLoad` 直接失败、库装载失败，而不是某个功能静默变成“此设备不支持”。
宿主侧 `NativeAudioCoreContractTest`（4 项）锁定这 10 个声明的位置与形状，不需要真机就能发现同类错误。

`javap -p 'NativeAudioCore$Bridge'`：10 个 `public final native` 全在该类，外层 `NativeAudioCore` 一个 native 都没有。
`llvm-nm -D libyyplayer_jni.so | grep Java_`：10 个导出全部带 `_00024Bridge_`，且 `JNI_OnLoad` 存在（库装载即校验全部签名）。


## 4. H03 修掉的缺陷：格式比对是同义反复

`sink.routeReport()` 里的 `trackSampleRate` / `trackEncoding` 读的是 `AudioTrack.getSampleRate()` 与 `getFormat()`，
而这两个值**只是把构造时传入的参数回显**——平台若把流换成别的格式，比对仍然“一致”，等于什么都没验证。
现在 sink 在打开时把 `getFormat()` 的结果单独记为 `platformFormat`，`formatMatchesTrack` 比对这个值；
一旦平台替换格式，界面显示“平台已替换”，并把平台实际接受的格式单独列出，应用不会把请求格式当成实际格式。


## 5. 平台没有 24-in-32 容器（明确拒绝，不静默替换）

`AudioFormat` 定义的是：
- `ENCODING_PCM_24BIT_PACKED` — 24 位有效、**3 字节**容器 → 对应核心 `s24_3`
- `ENCODING_PCM_32BIT` — **32 位**有效 → 对应核心 `s32`
- 没有“24 位有效放在 4 字节容器里”的编码 → 核心的 **`s24_in32` 无平台对应物**

因此 `AudioTrackPcmSink.open()` 对 `s24_in32` 返回 `sink_bad_format` 并带上原因，而不是悄悄按别的容器打开。
测试 `twentyFourInThirtyTwoHasNoPlatformContainerAndIsRefused` 锁定该行为，并同时确认核心**仍能**把
`s24_in32` 无损加宽到 `s32`（低位是填充，加宽不丢位）。
**遗留**：若解码器真的产出 `s24_in32`（例如某些 24-bit FLAC 路径），需要由调用方显式决定“加宽到 s32 再打开”；
这条映射目前尚未接到策略里，记为 H04 待办。


## 6. H03 修掉的缺陷：参考信号是堆缓冲

`AudioVerificationTone.renderS16()` 原本用 `ByteBuffer.allocate`（堆缓冲）。
JNI 用 `GetDirectBufferAddress` 取样本内存，堆缓冲返回 null，于是 `convertPcm` 返回 −1，
表现成“核心拒绝无损转换”，而真正原因是缓冲区种类不对。已改为 `allocateDirect`。
这正是 H02 交接里记的第 5 条坑，本轮由真实用例撞上并修掉。


## 7. 主机与真机跑同一份 C++ 核心测试

```
cmake --build native/build-host                                     exit 0
native/build-host/yy_core_tests.exe  -> checks=6339 failures=0  RESULT OK
adb push native/build-android-arm64-v8a/yy_core_tests /data/local/tmp/
adb shell /data/local/tmp/yy_core_tests -> checks=6339 failures=0  RESULT OK
```

两侧跑的是同一份核心逻辑与同一套断言（6339），所以加宽语义的修正在 arm64 真机上同样成立，H01 行为也无回退。
交叉编译用 NDK 28.2.13676358 + `android-26` + `c++_shared`。


```
adb -s 192.168.114.116:33077 shell am instrument -w \
  -e class yy.daniel.yyplayerforandroid.AudioOutputDeviceTest,\
yy.daniel.yyplayerforandroid.AudioOutputSinkDeviceTest \
  yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
-> OK (18 tests)     连续 2 次全绿
```

`AudioOutputSinkDeviceTest` 单独跑为 **OK (11 tests)**；日志原文见 `h03_logcat_audio.txt`，测试输出见 `h03_sink_test.txt`。

三种路由的同一套证据（都是 336000 帧 = 7 秒 48 kHz 双声道）：

```
YYPLAYER_WIDENED encoding=s24_3 usb=true  started=true platform=s24_3/48000Hz matches=true
  fed=336000 dropped=0 consumed=336000 duringSignal=0    routed=[USB-Audio - lifeme HiFi Audio Pro]
YYPLAYER_WIDENED encoding=s32   usb=true  started=true platform=s32/48000Hz   matches=true
  fed=336000 dropped=0 consumed=336000 duringSignal=1078 routed=[USB-Audio - lifeme HiFi Audio Pro]
YYPLAYER_WIDENED encoding=s16   usb=false started=true platform=s16/48000Hz   matches=true
  fed=336000 dropped=0 consumed=336000 duringSignal=131  routed=[OPPO Enco Free4]        ← 蓝牙 A2DP
YYPLAYER_WIDENED encoding=s16   usb=false started=true platform=s16/48000Hz   matches=true
  fed=336000 dropped=0 consumed=336000 duringSignal=0    routed=[2509FPN0BC]             ← 内置扬声器
YYPLAYER_S24IN32_REFUSED started=false reason=sink_bad_format
YYPLAYER_WIDEN_SIZE encoding=s24_3   srcBytes=1344000 srcFrames=336000 dstBytes=2016000 converted=336000
YYPLAYER_WIDEN_SIZE encoding=s24_in32 srcBytes=1344000 srcFrames=336000 dstBytes=2688000 converted=336000
YYPLAYER_WIDEN_SIZE encoding=s32     srcBytes=1344000 srcFrames=336000 dstBytes=2688000 converted=336000
```

非 USB 两种路由都**没有**出现在位完美断言里失败：它们 `routedIsUsb=false`，且
`systemReportedBitPerfect=false`——即“能出声，但绝不声称位完美”。

`duringSignal` 是泵块内的节拍余量（每轮最多交一个缓冲块 = 4800 帧，约 100 ms）：
它不是丢数据（写入与取走都是 336000，平台 xrun=0），断言写成“不得被整整一个块饿到”。

## 8. 真机 instrumentation

## 9. 界面取证（截图见 `shots/`）

设置 → 播放 → 音频输出，全程真机操作：

| 截图 | 内容 |
| --- | --- |
| `shots/h02-settings.png` | 设置页音频输出行：`不可用`（位完美）+“可以按请求输出” |
| `shots/h02-device-list2.png` | 实际状态：位准确状态 `不可用` / “该设备组合没有系统位完美契约” |
| `shots/h02-selftest-running.png` | 自检进行中：`正在用决策格式打开输出并回读系统报告…`、`进行中…` |
| `shots/h02-selftest-final.png` | 自检完成：路由 `USB-Audio - lifeme HiFi Audio Pro`（USB）、`48.0 kHz · 16 bit · 缓冲 7688 帧`、`与决策一致` |
| `shots/h02-device-list.png` | USB 设备行：“USB 耳机 · 16.0k/32.0k/48.0k/96.0k/192.0k/44.1k/384.0k · **无位完美组合**” |


## 10. 未验事项（不得写成已达成）

- **端到端位准确**：需要外接数字录音（或 DAC 侧数字输出）捕获并与 `digestSha256` 比对。本机无此条件。
- **`system_reported` 级位完美**：本机 USB DAC 无任何 BIT_PERFECT 组合，因此**无法在此设备上取到该级别证据**。
  要验证该路径需要换一台 HAL 声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT` 的 USB DAC；代码路径已就绪。
- **`s24_in32` → `s32` 的显式映射**：核心支持该无损加宽，但策略层还没有接（第 4 节遗留）。
- **高采样率（96 kHz 及以上）实机取证**：本轮尝试过 96 kHz，测试进程因反复渲染多兆字节缓冲陷入 GC 风暴而挂住，
  因此把加宽用例固定在 48 kHz。加宽是位深操作、与采样率无关，但**96k/192k/384k 的实机输出仍未验**。
- **Oboe/AAudio 独占与实际 `SharingMode`**：未实现，H02/H03 只做系统 AudioTrack 共享路径。
- **插拔、焦点、Android 17 后台音频收紧**：未验。
- 实体机上 Compose UI instrumentation 仍会挂起（U08 既有问题）；UI 取证改用 adb 点击 + `uiautomator dump` + 截图。

另外两条只读事实：小米会把**应用自身**的 `android.util.Log` 从 logcat 里过滤掉（同一次运行里
`AudioTrack`／`AudioPolicyManager` 等系统日志正常出现），所以应用侧证据以界面回读与 instrumentation 输出为准；
`system_reported` 级别的位完美需要 HAL 声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT`，这台 DAC 没有。



## 11. 复现命令

```powershell
Set-Location E:\SourceFiles\androidstudio\YYPlayerforAndroid
$adb='E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe'; $s='192.168.114.116:33077'

# 主机核心测试（6339 断言）
& 'E:\DeveloperTools\cmake\bin\cmake.exe' --build native\build-host
& 'native\build-host\yy_core_tests.exe'          # checks=6339 failures=0

# 宿主单测（含 JNI 契约）
.\gradlew.bat :app:testDebugUnitTest --no-daemon

# 真机
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=4
& $adb -s $s install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s $s install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb -s $s logcat -c
& $adb -s $s shell am instrument -w -e class yy.daniel.yyplayerforandroid.AudioOutputSinkDeviceTest `
  yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s $s logcat -d -s YYPlayerAudio:*
```

界面路径：设置 → 播放 → 音频输出 → “输出自检 / 用当前决策格式试听 → 开始”。
（`AudioOutputActivity` 是 `exported=false`，`adb shell am start` 会被拒；必须走应用内路径。）
