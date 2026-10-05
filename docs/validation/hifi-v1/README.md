# H01 证据：可移植音频核心与 Android 输出策略（dev-hifi 首轮）

日期：2026-10-05
设备：小米 2509FPN0BC（`ac07514c`），Android 17 / SDK 37，HyperOS OS4.0，ABI 仅 `arm64-v8a`
无 USB DAC、无蓝牙音频设备。

## 1. 主机与真机跑同一份 C++ 核心测试

```
cmake -S native -B native/build-host -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_COMPILER=E:/DeveloperTools/LLVM/bin/clang++.exe        exit 0
cmake --build native/build-host                                       exit 0
native/build-host/yy_core_tests.exe   -> checks=2345 failures=0  RESULT OK
ctest --test-dir native/build-host    -> 1/1 Passed
adb push native/build-android-arm64-v8a/yy_core_tests /data/local/tmp/
adb shell /data/local/tmp/yy_core_tests -> checks=2345 failures=0  RESULT OK
```

覆盖：PCM 格式与字节/帧运算（含 64 位溢出）、原样转换矩阵（s16/s24_3/s24_in32/s32 加宽与 24 位重排，丢位与 float 一律拒绝）、环的丢弃/欠载计数与并发读写一致性、输出策略判定矩阵（严格独占、严格原样、位完美契约、软件音量、同路由回退）、JSON 点路径读写与畸形输入拒绝。

## 2. 构建与打包

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest   -> BUILD SUCCESSFUL
APK: lib/arm64-v8a/libyyplayer_jni.so (62608 B), lib/x86_64/libyyplayer_jni.so (61520 B)
llvm-nm -D --defined-only libyyplayer_jni.so | grep Java_
  Java_..._NativeAudioCore_00024Bridge_nativeBridgeVersion
  Java_..._NativeAudioCore_00024Bridge_nativeEvaluateOutputPolicy
  Java_..._NativeAudioCore_createRing / ringWrite / ringRead / ringClear / ringStats / releaseRing / convertPcm
```

## 3. 真机 instrumentation

```
adb shell am instrument -w -e class yy.daniel.yyplayerforandroid.AudioOutputDeviceTest \
  yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
-> OK (7 tests)
logcat -s YYPlayerAudio:  YYPLAYER_CORE_VERSION=1
```

用例：原生核心可装载并报告版本；无契约路由拒绝严格原样（`strict_unprocessed_*`）；软件音量拒绝严格原样（`strict_unprocessed_dsp_active`）；系统 AudioTrack 路径拒绝严格独占（`exclusive_not_proven_by_audiotrack`）；设备能力记录并要求**非 USB 输出不得报告任何位完美组合**。

主机单测另锁定 JNI 线格式：`AudioBridgeJsonTest`（请求/响应文档形状、点路径、畸形输入）、`AudioPolicyWireTest`（字段名与枚举拼写），`:app:testDebugUnitTest` 24 项 0 失败。

## 4. 本机实际输出能力

系统只报告内置输出（扬声器/听筒/远程混音等），没有 USB 音频设备，因此：

- `AudioManager.getSupportedMixerAttributes` 没有可查询的 USB 设备；
- 本机不存在任何 `system_reported` 级别的位完美证据；
- `strict_exclusive` 与 `strict_unprocessed` 在本机必然拒绝，界面显示原因而不是伪造成功。

## 5. 未验事项

USB DAC 的 BIT_PERFECT 契约与 `setPreferredMixerAttributes` 真实结果、USB 路由回读（`getRoutedDevices`）、Oboe/AAudio 独占与实际 `SharingMode`、数字捕获验证、蓝牙输出、耳机/USB 插拔与焦点策略、Android 17 后台音频收紧；Compose UI instrumentation 在实体机上仍会挂起（既有问题，U08 已记录），本轮输出页只做编译与控制器级验证。

设备方面补充：小米在首次启动新 Activity 时会弹出“运行申请”确认，锁屏无人值守时该弹窗会遮挡应用，这会影响任何需要前台 UI 的自动化。
