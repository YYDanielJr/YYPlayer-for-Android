# dev-hifi 交接说明（新窗口从这里接手）

日期：2026-10-08　　分支：`dev-hifi`　　H01 提交：`5dc5928`（H02/H03/H04/H05 改动**全部未提交**，见第 2 节）

## 接续点（H05 2026-10-08）

用户追加“完全核验所有未提交输出代码”和“把输出方式／设备切换做到歌曲详情”，已完成源码修复和详情页同页弹窗，详细发现见 [HIFI_CODE_AUDIT](HIFI_CODE_AUDIT.md)，最新结果以 STATUS 顶部 H05 和 validation README 为准。当前设备地址 **`192.168.1.246:33103`**，旧 `192.168.114.116:33077` 只属于下方历史记录。USB 与 OPPO 蓝牙都在线，DAC 仍无 BIT_PERFECT。

H05 修正 JNI lease/clear 并发、buffer 窗口、PCM C++17 溢出、profiles 与 mask；SYSTEM 实际设备选择；只投影 live evidence；自检与音乐输出互斥并保留静音／位置；旧回调、速度意图、视频 codec 偏好误筛音频；格式重配等待尾部、flush 保留下个格式、API31 排空门槛；BIT 下音量安全和启动路由确认。USB 24packed 首帧会从 pin 回旧蓝牙的问题已用匹配 DEFAULT mixer 解决，不能拿这个共享请求冒充位完美。

歌曲详情右上“音频输出”图标／选项行打开 WindowDialog，内有“输出方式／输出设备”，设置页保持同步；选项使用唯一 runtime，无第二引擎。`NATIVE_EXACT` 文案“自研输出（原样优先）”，实际流独占仍 Unknown；自研当前 1×，严格原样无合格契约则拒绝。最终构建／JVM28／lint exit0，host 与手机核心6390断言通过，最终组合真机 **OK (29 tests)**（103.102s），USB 整曲 **OK(1)**（280.214s、12109193帧），歌曲详情普通 adb/UI 操作和截图已取证。最终安装 APK SHA256 `1219906f9f6b00dcfe21e9c904322a89b85228eaacdd787e94dd4898bc7d8734`；整曲在补自检 gate 前的 d30234... 构建，PCM/sink 未变，详细版本证据见 STATUS。

复验（保留应用数据）：

```powershell
$adb='E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe'
$serial='192.168.1.246:33103'
& $adb connect $serial
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
& $adb -s $serial install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s $serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb -s $serial shell am instrument -w -e class yy.daniel.yyplayerforandroid.OutputAuditDeviceTest,yy.daniel.yyplayerforandroid.NativeOutputPlaybackTest,yy.daniel.yyplayerforandroid.PlayerSessionServiceDeviceTest,yy.daniel.yyplayerforandroid.AudioOutputDeviceTest,yy.daniel.yyplayerforandroid.AudioOutputSinkDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
# USB ID 是本会话的 919；重接后先从 AudioOutputDeviceTest 日志或应用设备列表查新 ID。
& $adb -s $serial shell am instrument -w -e fullPlayback true -e pinnedDeviceId 919 -e class 'yy.daniel.yyplayerforandroid.NativeOutputPlaybackTest#nativeSinkPlaysALocalFileThroughExoPlayer' yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
```

不要运行 connectedDebugAndroidTest（会卸载数据），不要把 shell exit0 或旧失败日志当通过。API26–32、API26–30 短尾部、物理拔插／焦点、锁屏、高采样率、独占／BIT 正面硬件与数字捕获仍待专项。以下 H04/H03 内容为历史记录。

## 历史接续点（H04 2026-10-06）

H04 4.2 秒停摆已经修复，真实 24-bit FLAC 整曲正常结束（12109193 帧，280 秒测试通过），真实 MediaController/service 的输出切换、暂停续播、seek、next 和生产 UI 引用释放后后台播放均已通过。当前实际结果以 `docs/STATUS.md` 顶部 H04 和 validation README 为准；H02/H03/H04 全部未提交。

**前轮暂停点的时钟推断已被纠正**：12:50 APK 的写入位置方案仍停在 186030 帧、负位置。真正问题是 sink 没检查 `audio/raw`，FLAC 元数据的 pcmEncoding 触发压缩旁路；另一个问题是忽略 presentationTimeUs 的 renderer 偏移。现在只接受解码后 PCM、明确请求源整数位深、用首输入时间戳 + 实际播放头作时钟，写入使用非阻塞并重试短写。新测试有实际失败断言，旧日志测试的“OK”不再作为验收。

已清除 NativeOutputAudioSink 的临时 HANDLE_BUFFER／EOS／IS_ENDED／DISCONTINUITY／TRACK_FORMAT／TRACE 日志。`framesAccepted`、sink counters 保留供真机验收。普通播放为 MediaCodec → AudioSink → AudioTrack，核心用于无损加宽；H02 自检仍经核心环泵，不要把两条投递方式混写。

服务修正包括 session.setPlayer 随重建改绑、service 持有唯一引擎引用、退到后台不再沿用 Activity 500ms 暂停规则（视频保留暂停）。严格独占／无 BIT_PERFECT 的严格原样在自研路径拒绝，宽松模式保留软件音量，自研暂为 1×。

复验命令：

```powershell
$adb='E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe'
$serial='192.168.114.116:33077'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
& $adb -s $serial install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s $serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb -s $serial shell am instrument -w -e class yy.daniel.yyplayerforandroid.NativeOutputPlaybackTest,yy.daniel.yyplayerforandroid.PlayerSessionServiceDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
# 整曲约 280 秒；预期最终 STATE_ENDED，帧数与时长元数据允许 1ms 舍入差。
& $adb -s $serial shell am instrument -w -e fullPlayback true -e class 'yy.daniel.yyplayerforandroid.NativeOutputPlaybackTest#nativeSinkPlaysALocalFileThroughExoPlayer' yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
```

实体机 Compose Activity instrumentation 启动仍挂起，service 测试直接调用生产 UI 生命周期引用钩子并连接真实 service/controller，不能写成 UI 自动化通过。下一独立任务为物理拔插／焦点与锁屏长期播放；Oboe、合格 BIT_PERFECT DAC、数字捕获、高采样率、API26 实机仍未验。下方 H01–H03 记录保留为历史；遇到阶段描述不一致，优先本段和最新 STATUS。

## 0. 给新窗口的启动指令（可直接粘贴）

> 项目：`E:\SourceFiles\androidstudio\YYPlayerforAndroid`（Android 音乐/视频播放器，Kotlin + Compose + Miuix + 可移植 C++17 核心）。
> 先读 `AGENTS.md`（执行规则）与 `docs/HANDOFF_HIFI.md`（本文件），再读 `docs/STATUS.md` 顶部 H05 记录。
> 当前任务：继续 `dev-hifi` 分支的 HiFi 输出工作。**H05 全量审计与歌曲详情输出控制见本文件顶部**；第 7 节为历史建议。
> 设备：小米 2509FPN0BC（**Wi-Fi adb `192.168.1.246:33103`**，Android 17／SDK 37，HyperOS OS4.0.0.44），
> **已接入 USB DAC「Synaptics lifeme HiFi Audio Pro」与蓝牙「OPPO Enco Free4」**。
> 关键硬件事实：**该 USB DAC 没有任何 BIT_PERFECT 组合**（42 个组合全是 `MIXER_BEHAVIOR_DEFAULT`），
> 因此 `system_reported` 级位完美在本机无法取证；端到端位准确也没有数字捕获条件——不要写成已达成。
> 平台能接受 `s24_3` 与 `s32` 容器，**没有 24-in-32（`s24_in32`）容器**。
> 用户已授权：本会话 DSH 文件策略为完全访问；不自动提交/推送新改动，不清理用户文件。

## 1. 必读文档（按顺序）

| 顺序 | 文件 | 为什么读 |
| --- | --- | --- |
| 1 | [AGENTS.md](AGENTS.md)（114 行） | 执行规则、架构与线程硬规则、音频真实性红线（焦点≠独占≠未处理≠位准确） |
| 2 | 本文件 | 交接状态、命令、已知坑 |
| 3 | [docs/STATUS.md](docs/STATUS.md)（顶部 H03 一节 + H02/H01 各一节） | 各轮做了什么、证据、未验事项、下一步 |
| 4 | [docs/validation/hifi-v1/README.md](docs/validation/hifi-v1/README.md) | **H01+H02+H03 全部实测证据**（含截图、instrumentation 输出、logcat 原文摘要、四个已修缺陷） |
| 5 | [docs/adr/0006-hifi-output-core-android-split.md](docs/adr/0006-hifi-output-core-android-split.md) | C++ 核心与 Android 平台层的分工决策（H02/H03 已补充实测结论） |
| 6 | [docs/research/USB_DAC_BITPERFECT_API_FACTS.md](docs/research/USB_DAC_BITPERFECT_API_FACTS.md)（476 行） | API 34+ 位完美与独占的**官方事实核查**——写代码前必读 |
| 7 | [native/README.md](native/README.md)（55 行） | 核心目录结构、主机与 NDK 交叉编译命令 |
| 参考 | [docs/TECHNICAL_DESIGN.md](docs/TECHNICAL_DESIGN.md) §7 | HiFi 目标、判定矩阵、系统 USB 七步、Oboe 与失联规则 |
| 参考 | [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) S06–S08 | 阶段任务与验收口径 |
| 参考 | [docs/adr/0001-android-media3-vertical-slice.md](docs/adr/0001-android-media3-vertical-slice.md) | 当前临时播放后端（Media3）与退出条件 |

## 2. Git 状态

```
dev-hifi  1daef67  docs: H01 交接说明                                    ← 当前 HEAD（H01 最后一个提交）
          5dc5928  feat(hifi): 可移植 C++ 音频核心 + 输出策略与设备选择 (H01)
          4fff9f1  feat: 在线音乐、首次引导、媒体库分页与音乐控件 (U05-U08)
dev-api   4fff9f1
master    836a920  merge: U05-U08 并入 master
```

- **H02/H03/H04 的全部改动仍是未提交的工作区改动**（用户未要求提交，不要自动提交/推送）。动手前先 `git status --short`。
- H03 改动的文件：`native/core/src/pcm.cpp`、`native/core/include/yy/core/pcm.h`、`native/core/tests/pcm_test.cpp`、`audio/PcmSink.kt`、`audio/AudioTrackPcmSink.kt`、`audio/OutputSelfTest.kt`、`audio/AudioVerificationTone.kt`、`ui/AudioOutputScreen.kt`、`app/src/androidTest/.../AudioOutputSinkDeviceTest.kt`、`docs/validation/hifi-v1/`（`h03_*` 证据）、`docs/STATUS.md`、`docs/adr/0006-*.md`、本文件。
- `native/build*`、`docs/validation/acl-recovery/` 已在 `.gitignore` 中。

## 3. 设备

- 小米 2509FPN0BC，**Wi-Fi adb `192.168.114.116:33077`**（`adb connect 192.168.114.116:33077`），Android 17／SDK 37，HyperOS OS4.0.0.44.XPBCNXM，ABI 仅 `arm64-v8a`。
- 外接：USB DAC「Synaptics lifeme HiFi Audio Pro」（VID 1739／PID 5525，`/dev/bus/usb/001/002`，ALSA card 1，支持 16k/32k/44.1k/48k/96k/192k/384k × s16/s24_3/s32），蓝牙「OPPO Enco Free4」（A2DP 44.1k + SCO 8k/16k）。
- 平台已接受的核心容器：`s16`、`s24_3`、`s32`；**`s24_in32` 无平台编码**。
- 坑：小米会在新 Activity 首次启动时弹「运行申请」确认；**锁屏无人值守时该弹窗会挡住前台应用**。跑前台测试前先解锁并保持亮屏。
- 坑：`AudioOutputActivity` 与其余二级页都是 `exported=false`，`adb shell am start` 会报 Permission Denial；只能从应用内 UI 路径进入（设置 → 播放 → 音频输出）。
- 坑：**小米会把应用自身的 `android.util.Log` 从 logcat 过滤掉**（同一次运行里系统日志正常）。所以应用侧证据要看界面回读与 instrumentation 输出，不要因为 `logcat -s YYPlayerAudio:*` 为空就以为功能没跑；不过 instrumentation 进程（`androidx.test.runner`）里的同名 `Log.i` **能**正常输出，这是主要取证通道。
- 坑：UI 自动化点击建议先 `uiautomator dump` 拿真实 bounds（页面滚动后坐标会变），`input tap` 的坐标是屏幕像素。

## 4. 可复现命令

```powershell
Set-Location E:\SourceFiles\androidstudio\YYPlayerforAndroid
$adb='E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe'; $s='192.168.114.116:33077'
& $adb connect 192.168.114.116:33077

# 1) 主机 C++ 核心测试（6339 断言）
& 'E:\DeveloperTools\cmake\bin\cmake.exe' -S native -B native\build-host -G Ninja `
  -DCMAKE_MAKE_PROGRAM='E:/DeveloperTools/ninja/ninja.exe' -DCMAKE_BUILD_TYPE=Release `
  -DCMAKE_CXX_COMPILER='E:/DeveloperTools/LLVM/bin/clang++.exe'
& 'E:\DeveloperTools\cmake\bin\cmake.exe' --build native\build-host
& 'native\build-host\yy_core_tests.exe'        # 期望 checks=6339 failures=0 / RESULT OK

# 2) 真机跑同一份核心测试（6339 项；H03 已重新交叉编译并推送，两侧断言一致）
& 'E:\DeveloperTools\AndroidSDK\cmake\3.22.1\bin\cmake.exe' -S native -B native\build-android-arm64-v8a -G Ninja `
  -DCMAKE_MAKE_PROGRAM='E:/DeveloperTools/ninja/ninja.exe' `
  "-DCMAKE_TOOLCHAIN_FILE=E:\DeveloperTools\AndroidSDK\ndk\28.2.13676358\build\cmake\android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release
& 'E:\DeveloperTools\AndroidSDK\cmake\3.22.1\bin\cmake.exe' --build native\build-android-arm64-v8a
& $adb -s $s push native\build-android-arm64-v8a\yy_core_tests /data/local/tmp/
& $adb -s $s shell chmod 755 /data/local/tmp/yy_core_tests
& $adb -s $s shell /data/local/tmp/yy_core_tests   # 期望同样是 6339/0

# 3) App 构建 + 安装（保留数据）
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=4
& $adb -s $s install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s $s install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk

# 4) 音频输出真机测试（H01 的 7 项 + H02/H03 的 11 项 = 18 项）
& $adb -s $s shell am instrument -w -e class yy.daniel.yyplayerforandroid.AudioOutputDeviceTest,yy.daniel.yyplayerforandroid.AudioOutputSinkDeviceTest `
  yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner

# 5) 宿主单测（JNI 契约 + 线格式，28 项）
.\gradlew.bat :app:testDebugUnitTest --no-daemon

# 6) 界面取证（自检出声并回读）
& $adb -s $s shell am start -n yy.daniel.yyplayerforandroid/.MainActivity
& $adb -s $s shell uiautomator dump /sdcard/ui.xml; & $adb -s $s shell cat /sdcard/ui.xml
```

## 5. 已知的构建/工具坑（都会浪费时间，先看这里）

1. **`-DCMAKE_TOOLCHAIN_FILE=...` 必须加引号**，否则 CMake 会丢掉参数并报 `CMAKE_CXX_COMPILER not set`。
2. **Gradle 增量缓存会漏编译**（H03 撞到两次）：新增/修改 Kotlin **测试**文件后 `:app:assembleDebugAndroidTest` 可能报 `UP-TO-DATE`，新测试类根本没进 APK，运行时报 `ClassNotFoundException`。**加 `--rerun-tasks`** 即可；必要时删 `app\build\intermediates\built_in_kotlinc`、`app\build\tmp\kotlin-classes`。
3. **JNI 名字是最大的坑**：Kotlin 把嵌套 `Bridge` 对象编译成**独立顶层类**，**全部 10 个 native 声明都在它上面**（`javap -p '...NativeAudioCore$Bridge'` 可验），所以所有导出名都必须带 `_00024Bridge_`。H01 漏了 7 个（环与 `convertPcm`），后果是 `System.loadLibrary` 成功、调用抛 `UnsatisfiedLinkError`、被 Kotlin 包装吞成“原生不可用”。现在改为显式 `RegisterNatives` + `JNI_OnLoad`：签名或宿主类不匹配会让**库装载失败**。改 Kotlin 声明后必须同步 `native/jni/yy_jni.cpp` 的注册表，并跑 `NativeAudioCoreContractTest`。
4. **JNI 响应是对象体（不含外层花括号）**，Kotlin 侧 `parseDecision` 会补一层；改动时别双重包裹。
5. **JNI 读样本内存走 `GetDirectBufferAddress`**：传**堆** `ByteBuffer` 会让 `ringWrite`/`convertPcm` 静默失败（后者返回 −1，看起来像“格式被拒绝”）。必须 `allocateDirect`。H03 就是被这个坑了一次（`renderS16` 用了 `allocate`）。
6. **`AudioTrack.getSampleRate()`/`getFormat()` 只是回显构造参数**，不能当作“平台实际接受的格式”来和请求比对，否则比对永远成立。要比对就用 sink 记录的 `platformFormat`。
7. **本机 PATH 里的 cmake 是 4.4.0，AGP 用 SDK 的 3.22.1**：脚本里写绝对路径避免歧义。ninja 不在 PATH（`E:\DeveloperTools\ninja\ninja.exe`）。
8. **受限沙箱下 CMake 探测编译器会被强杀**（子进程管道限制），Gradle 也写不了 `C:\Users\YYDaniel\.gradle`；本会话已是完全访问，正常。
9. **实体机上 Compose UI instrumentation（如 `LyricStyleUiTest`）会长时间挂起**，这是 U08 已记录的既有问题；音频的 18 项可正常通过。
10. **不要在测试里用高采样率反复渲染大缓冲**：H03 在 96 kHz 上跑三个加宽用例时，测试进程陷入持续 sticky GC（无 ANR）而挂住。用例固定在 48 kHz；需要高采样率取证时按需生成、用完释放。

## 6. 当前实现边界（不要越界声明）

- **播放本身仍走 Media3 ExoPlayer 共享输出**（ADR 0001 的临时后端）。H02/H03 接好的是**核心 PCM 环 → AudioTrack sink** 这条新通路，它目前由显式“输出自检”驱动，**还没有替换 Media3 的播放**（那是 S06）。
- 位完美状态只有 `unknown/unavailable/requested/system_reported/capture_verified` 五档；应用最高只标 `system_reported`，`capture_verified` 只能来自数字捕获，代码不会自行设置。
- **本机 USB DAC 没有 BIT_PERFECT 组合**，所以连 `system_reported` 都取不到；界面显示“不可用”并给出原因，这是正确行为，不是缺陷。
- `setPreferredDevice` 返回 `false` 不代表路由失败（小米实测 `false` 但仍路由正确）。**路由证据一律以 `getRoutedDevices()` 回读为准。**
- 严格策略不静默降级：`strict_exclusive` 在系统 AudioTrack 路径直接拒绝；`strict_unprocessed` 在没有匹配 BIT_PERFECT 契约、或存在软件音量/EQ/ReplayGain/变速时拒绝。
- 设备身份：会话内用 `AudioDeviceInfo.id`；跨会话用 `type + address` 软匹配，不把会话 ID 当永久主键。
- **加宽是保电平的**：`s16 → s24_3/s24_in32/s32` 会左移补零（满刻度仍是满刻度）。若改动 `pcm.cpp`，必须保持 `FullScaleFraction` 不变量与“降位深拒绝”两条主机断言。
- `s24_in32` 无平台容器，sink 明确拒绝；核心能把它无损加宽到 `s32`，但策略层尚未接入。
- `OutputSelfTest.digestHex` 是**进入环的字节**的 SHA-256（目标编码、小端交织），只是可复现的送出版本，不是端到端证明。

## 7. 下一步（H04 建议顺序）

1. **高采样率取证**：在 96 kHz（以及 192 kHz）上跑同一自检，确认平台与 HAL 的实际采样率路径与投递计数；注意第 5 节第 10 条的 GC 坑。
2. **`s24_in32` 源接入**：解码器若产出 24-in-32，由策略显式决定加宽到 `s32` 再打开（核心已支持无损加宽）。
3. **Oboe/AAudio 独占**（ADR0006 退出条件）：用 `AAudioStream_getSharingMode` 读回实际共享模式；AudioTrack 路径不得据此声称独占。这是当前唯一还没有任何实现的大块。
4. **换 DAC 取 `system_reported`**：若可换一台 HAL 声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT` 的 USB DAC，跑同一自检即可取证；随后做数字捕获比对 `digestHex` 争取 `capture_verified`。
5. **把决策格式接到真实播放**：S06 的 FFmpeg 解码 → `PcmSink`，替换 Media3 共享输出（ADR0001 退出条件）。
6. 顺带补：Android 17 后台音频收紧（后台/非有效生命周期下播放与音量 API 静默失败）对 service 设计的影响仍未验证；插拔与焦点策略也未验。
7. 真机核心测试二进制已与主机同步（都是 6339 项）。若之后又改了 `native/core`，记得重新交叉编译并推送，否则真机跑的是旧断言。
