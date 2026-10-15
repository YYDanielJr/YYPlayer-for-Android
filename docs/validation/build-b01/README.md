# B01：0.1.0、ABI 分包与 release 优化

2026-10-14，dev 基线 `f24008b`，本轮未提交。源码规则与复现流程见 [编译与签名](../../BUILD_AND_SIGNING.md)。

## 产物

每个主变体仅两个 APK，各含对应 ABI 的 12 个 so，无 universal／32 位包。完整文件名、字节数、SHA-256 和 dex 大小见 [artifacts.json](artifacts.json)。下表 release 为未签名原件；正式签名会略增体积。

| ABI | Debug | Release |
| --- | ---: | ---: |
| arm64-v8a | 45,259,494 B（43.16 MiB） | 14,276,660 B（13.62 MiB） |
| x86_64 | 50,188,426 B（47.86 MiB） | 15,333,852 B（14.62 MiB） |

四个包均为 versionName `0.1.0`、versionCode `2`，符合 `YYPlayer_for_Android_<version>_<arch>_<debug/release>.apk`。Release dex 解压总大小 4,851,900 B，debug 为 49,967,888 B；两者包含不同调试依赖，不把该差值当成单独 R8 效果。Release 全部 so 压缩且 manifest `extractNativeLibs=true`，所有 ELF 16KB 对齐；debug 未压缩库 ZIP 16KB 对齐。压缩库 ZIP mmap 对齐不适用。

## 命令与结果

- `./gradlew.bat assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintRelease --console=plain`：exit 0，见 [build.txt](build.txt)。37 项 JVM，0 failure／error；release lint 0 error、2 既有 warning。初次配置把 ndk.abiFilters 与 splits 同时设置被 AGP 拒绝，已改成 CMake 编译过滤＋splits 打包过滤，最终通过。
- `python scripts/Verify-MpvArtifact.py <四个 APK 分别执行> <JSON>`：全部 exit 0；每包 12 so、仅目标 ABI，ELF／未压缩 ZIP 16KB 通过；完整本地审计在忽略目录 `build/b01-audit/`。metadata 核对脚本断言两 ABI、文件名、版本、压缩方式均通过。
- `aapt2 dump xmltree <release APK> --file AndroidManifest.xml`：exit 0，extractNativeLibs=true、versionCode 2／versionName 0.1.0；设备 dumpsys 确认 release 没有 DEBUGGABLE。R8 configuration.txt 包含 NativeAudioCore$Bridge 保留规则，MpvNative 使用 @Keep。
- `apksigner sign` 对 x86_64 release **副本**使用 signingReport 确认的现有调试证书；`apksigner verify --verbose` 与 `zipalign -c -P 16 4` 均 exit 0，v2／v3 验签通过。QA 副本为 15,356,975 B，SHA-256 `4a0f498c62c284da495b938e1d8e81ecba2b2d81248ee2d7892841e4b6e593cc`；未替换原件或配置默认正式证书。
- `assembleDebugAndroidTest -PreleasePackagingQa=true`、应用／测试 `adb install --no-incremental -r`、`am instrument -w -r ...ReleasePackagingInstrumentation`：exit 0，**3 项 release 专项通过**，见 [release-device.txt](release-device.txt)。检查非 debug 包、版本／Activity 启动、音频 JNI 注册、mpv 加载自制 320×240 视频与解码参数。专用 runner 只依赖 Android API 和稳定 JNI 名称。
- 恢复普通 `assembleDebugAndroidTest` 及 debug 应用后，`am instrument -w -r -e class yy.daniel.yyplayerforandroid.VideoPlayerUiTest,yy.daniel.yyplayerforandroid.MpvVideoDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner`：exit 0，**14 项通过**，见 [debug-device.txt](debug-device.txt)。包括倍速、横屏抽屉、滚动返回、libmpv 轨道／章节／同步／逐帧。
- Test-MumuUi.ps1 PowerShell 语法检查、metadata 按设备 ABI 选包、`git diff --check`：exit 0。未运行其完整全套设备 suite；本轮按以上类直接 instrumentation。

## 设备、异常与限制

先枚举 adb 为空；MuMuManager 当前实例 0 未启动，启动后读取实时端口并连接 `127.0.0.1:16384`，MuMu Android15／API35／x86_64。素材沿用测试 APK 的自制 v15／v16 视频，临时缓存测试后清理。全部覆盖安装保留用户库／偏好，最终恢复正常 debug 0.1.0 和 JUnit runner 并启动主页。

首次用主目录 debug.keystore 签署 QA 被系统拒绝覆盖；根据 signingReport 改用 Gradle 实际 SDK 目录证书后通过，未卸载数据。普通 debug JUnit runner 直接用于混淆包时因 Kotlin Intrinsics 被混淆而崩溃，不能算 release 应用失败或测试通过；专用平台 runner 已解决，不为此扩大 keep。AGP 会重写测试 manifest 的 runner，因此专用 runner 通过显式 QA Gradle 属性选择。

未连接实体 arm64 手机，arm64 APK 完成静态审计，未执行本轮 arm64 真实装载；API26–32、16KB 页设备运行、完整 release 功能／性能和 Android Studio 正式私钥签名流程仍待验。MuMu 结果不替代这些资格。下一步用用户正式签名配置构建并在目标 arm64 真机覆盖验证；本轮不发布、不提交，不改变 ADR0007 的 native 发布 gate。
