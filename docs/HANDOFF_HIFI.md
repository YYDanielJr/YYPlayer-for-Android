# H01 交接说明（新窗口从这里接手）

日期：2026-10-05　　分支：`dev-hifi`　　提交：`5dc5928`　　工作区：干净（0 改动）

这份文件是新窗口的唯一入口。下面按顺序说明读什么、当前状态、怎么跑测试、哪些坑已经踩过。
把它连同 `AGENTS.md` 一起交给新窗口即可。

## 0. 给新窗口的启动指令（可直接粘贴）

> 项目：`E:\SourceFiles\androidstudio\YYPlayerforAndroid`（Android 音乐/视频播放器，Kotlin + Compose + Miuix + 可移植 C++17 核心）。
> 先读 `AGENTS.md`（执行规则）与 `docs/HANDOFF_HIFI.md`（H01 交接说明），再读 `docs/STATUS.md` 顶部 H01 记录。
> 当前任务：继续 `dev-hifi` 分支的 HiFi 输出工作（下一步 H02：把 C++ 核心的 PCM 环接到真实输出 sink）。
> 设备：小米 2509FPN0BC（adb serial `ac07514c`，Android 17／SDK 37），**没有 USB DAC 与蓝牙音频设备**，位完美/独占资格未验，不要写成已达成。
> 用户已授权：本会话 DSH 文件策略为完全访问；不自动提交/推送新改动，不清理用户文件。

## 1. 必读文档（按顺序）

| 顺序 | 文件 | 为什么读 |
| --- | --- | --- |
| 1 | [AGENTS.md](AGENTS.md)（114 行） | 执行规则、架构与线程硬规则、音频真实性红线（焦点≠独占≠未处理≠位准确） |
| 2 | 本文件 | 交接状态、命令、已知坑 |
| 3 | [docs/STATUS.md](docs/STATUS.md)（339 行，**只读顶部 H01 一节**） | 本轮做了什么、证据、未验事项、下一步 |
| 4 | [docs/adr/0006-hifi-output-core-android-split.md](docs/adr/0006-hifi-output-core-android-split.md)（30 行） | C++ 核心与 Android 平台层的分工决策（本轮的设计依据） |
| 5 | [docs/research/USB_DAC_BITPERFECT_API_FACTS.md](docs/research/USB_DAC_BITPERFECT_API_FACTS.md)（476 行） | API 34+ 位完美与独占的**官方事实核查**（含 19 项必须检查的返回值、10 项未能证实的点）——写代码前必读，避免用不存在的 API |
| 6 | [native/README.md](native/README.md)（55 行） | 核心目录结构、主机与 NDK 交叉编译命令 |
| 7 | [docs/validation/hifi-v1/README.md](docs/validation/hifi-v1/README.md)（57 行） | H01 实际证据与未验清单 |
| 参考 | [docs/TECHNICAL_DESIGN.md](docs/TECHNICAL_DESIGN.md) §7（406 行中的 7.1–7.5） | HiFi 目标、判定矩阵、系统 USB 七步、Oboe 与失联规则 |
| 参考 | [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) S06–S08（310 行中的 165–207） | 阶段任务与验收口径 |
| 参考 | [docs/adr/0001-android-media3-vertical-slice.md](docs/adr/0001-android-media3-vertical-slice.md)（19 行） | 当前临时播放后端（Media3）与退出条件 |

## 2. Git 状态

```
dev-hifi  5dc5928  feat(hifi): 可移植 C++ 音频核心 + Android 输出策略与设备选择 (H01)   ← 当前
dev-api   4fff9f1  feat: 在线音乐、首次引导、媒体库分页与音乐控件 (U05-U08)
master    836a920  merge: U05-U08 ... 并入 master
```

- 未推送、未打标签。`native/build*`、`docs/validation/acl-recovery/` 已在 `.gitignore` 中。
- 动手前先 `git status --short`；本轮没有任何未提交改动需要善后。

## 3. 设备

- 小米 2509FPN0BC，adb serial **`ac07514c`**，Android 17／SDK 37，HyperOS OS4.0，ABI 仅 `arm64-v8a`。
- **没有 USB DAC、没有蓝牙音频设备**：位完美契约、Oboe 独占、路由回读、数字捕获全部未验；界面此时应显示“不可用”并给出原因。
- 坑：小米会在新 Activity 首次启动时弹「运行申请」确认；**锁屏无人值守时该弹窗会挡住前台应用**，任何 UI 自动化都可能挂住。跑前台测试前先解锁并保持亮屏。
- 坑：`AudioOutputActivity` 与其余二级页都是 `exported=false`，`adb shell am start` 会报 Permission Denial；只能从应用内 UI 路径进入（设置 → 播放 → 音频输出）。

## 4. 可复现命令

```powershell
Set-Location E:\SourceFiles\androidstudio\YYPlayerforAndroid

# 1) 主机 C++ 核心测试（2345 断言）
& 'E:\DeveloperTools\cmake\bin\cmake.exe' -S native -B native\build-host -G Ninja `
  -DCMAKE_MAKE_PROGRAM='E:/DeveloperTools/ninja/ninja.exe' -DCMAKE_BUILD_TYPE=Release `
  -DCMAKE_CXX_COMPILER='E:/DeveloperTools/LLVM/bin/clang++.exe'
& 'E:\DeveloperTools\cmake\bin\cmake.exe' --build native\build-host
& 'native\build-host\yy_core_tests.exe'        # 期望 checks=2345 failures=0 / RESULT OK

# 2) 真机跑同一份核心测试
& 'E:\DeveloperTools\AndroidSDK\cmake\3.22.1\bin\cmake.exe' -S native -B native\build-android-arm64-v8a -G Ninja `
  -DCMAKE_MAKE_PROGRAM='E:/DeveloperTools/ninja/ninja.exe' `
  "-DCMAKE_TOOLCHAIN_FILE=E:\DeveloperTools\AndroidSDK\ndk\28.2.13676358\build\cmake\android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release
& 'E:\DeveloperTools\AndroidSDK\cmake\3.22.1\bin\cmake.exe' --build native\build-android-arm64-v8a
adb -s ac07514c push native\build-android-arm64-v8a\yy_core_tests /data/local/tmp/
adb -s ac07514c shell chmod 755 /data/local/tmp/yy_core_tests
adb -s ac07514c shell /data/local/tmp/yy_core_tests   # 期望同样的 2345/0

# 3) App 构建 + 安装（保留数据）
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=4
adb -s ac07514c install -r app\build\outputs\apk\debug\app-debug.apk
adb -s ac07514c install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk

# 4) 音频输出真机测试（H01 新增，7 项）
adb -s ac07514c shell am instrument -w -e class yy.daniel.yyplayerforandroid.AudioOutputDeviceTest `
  yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner

# 5) 主机单测（锁定 JNI 线格式，24 项）
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

## 5. 已知的构建/工具坑（都会浪费时间，先看这里）

1. **`-DCMAKE_TOOLCHAIN_FILE=...` 必须加引号**，否则 CMake 会丢掉参数并报 `CMAKE_CXX_COMPILER not set`。
2. **Gradle 增量缓存会失效**：改了源文件后 `:app:checkDebugKotlin` 有时仍报 `UP-TO-DATE`，导致改动没进 APK、测试跑的还是旧代码。遇到“日志没变化/断言仍是旧值”时用 `--rerun-tasks`，或删 `app\build\intermediates\built_in_kotlinc`、`app\build\tmp\kotlin-classes` 再构建；必要时 `app\.cxx` 也要删（CMake 选项变更如 `YY_BUILD_JNI` 不会被识别）。
3. **JNI 符号名由 Kotlin 声明位置决定**：桥方法在嵌套对象 `NativeAudioCore.Bridge` 里，C++ 侧必须是
   `Java_yy_daniel_yyplayerforandroid_audio_NativeAudioCore_00024Bridge_nativeBridgeVersion` 与 `..._nativeEvaluateOutputPolicy`；
   改 Kotlin 方法名或所在对象后必须同步改 C++，否则 `System.loadLibrary` 成功但 `UnsatisfiedLinkError` 被吞成“native 不可用”。
4. **JNI 响应是对象体（不含外层花括号）**，Kotlin 侧 `parseDecision` 会补一层；改动时别双重包裹。
5. **本机 PATH 里的 cmake 是 4.4.0，AGP 用 SDK 的 3.22.1**：脚本里写绝对路径避免歧义。ninja 不在 PATH（`E:\DeveloperTools\ninja\ninja.exe`）。
6. **受限沙箱下 CMake 探测编译器会被强杀**（子进程管道限制）；本会话已是完全访问，正常。
7. **实体机上 Compose UI instrumentation（如 `LyricStyleUiTest`）会长时间挂起**，这是 U08 已记录的既有问题，不是本轮引入；`SetupPreferencesTest`(4) 与 `AudioOutputDeviceTest`(7) 可正常通过。

## 6. 当前实现边界（不要越界声明）

- 播放仍走 **Media3 ExoPlayer 共享输出**（ADR 0001 的临时后端）；H01 只实现了「输出策略判定 + 平台请求 + 设备选择 + 状态上报」，**还没有把核心的 PCM 环接到真实 sink**，所以位完美请求目前不会真正作用到播放。
- 位完美状态只有 `unknown/unavailable/requested/system_reported/capture_verified` 五档；应用最高只标 `system_reported`（系统接受混音器契约并按该格式输出），`capture_verified` 只能来自数字捕获，代码不会自行设置。
- 严格策略不静默降级：`strict_exclusive` 在系统 AudioTrack 路径直接拒绝；`strict_unprocessed` 在没有匹配 BIT_PERFECT 契约、或存在软件音量/EQ/ReplayGain/变速时拒绝。
- 设备身份：会话内用 `AudioDeviceInfo.id`；跨会话用 `type + address` 软匹配，不把会话 ID 当永久主键。

## 7. 下一步（H02 建议顺序）

1. 把 `native/core` 的 `PcmRing` 接到真实输出：先做 Kotlin 侧 AudioTrack sink（共享路径 + 34+ 混音器属性请求 + 播放中回读 `getRoutedDevices`/`getPreferredMixerAttributes`），再评估 Oboe/AAudio 独占。
2. 让策略决定真正作用到播放：当前 `AudioOutputController` 只做决定与上报，需要把选定格式（采样率/编码/声道掩码）传给 sink 并用同一 `AudioFormat` 建立输出。
3. 拿到 USB DAC 后按 ADR0006 退出条件取证：`getSupportedMixerAttributes` 是否有 BIT_PERFECT、`setPreferredMixerAttributes` 返回值、`getPreferredMixerAttributes` 回读、`getRoutedDevices` 是否为目标设备。
4. 顺带补：Android 17 后台音频收紧（后台/非有效生命周期下播放与音量 API 静默失败）对 service 设计的影响仍未验证。
