# 编译、分包与签名

当前版本为 **0.1.7（versionCode 8）**。B01 起 debug／release 均只编译并分别打包 `arm64-v8a` 和 `x86_64`，不生成 universal APK。每个 APK 可独立安装，无需同时安装两个架构。

## 构建与命名

在工程根目录运行：

```powershell
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
```

产物位于 `app/build/outputs/apk/debug/` 和 `app/build/outputs/apk/release/`：

```text
YYPlayer_for_Android_0.1.7_arm64-v8a_debug.apk
YYPlayer_for_Android_0.1.7_x86_64_debug.apk
YYPlayer_for_Android_0.1.7_arm64-v8a_release.apk
YYPlayer_for_Android_0.1.7_x86_64_release.apk
```

规则为 `YYPlayer_for_Android_<version>_<arch>_<debug/release>.apk`。版本从 `app/build.gradle.kts` 的 `versionName` 读取；发布新版本时同时维护递增的 `versionCode`。arm64 手机选择 `arm64-v8a`，x86_64 模拟器选择 `x86_64`。自动化应读取各目录的 `output-metadata.json`，按设备 `ro.product.cpu.abilist` 顺序选择匹配 ABI，不再硬编码旧 `app-debug.apk`。

`assembleDebugAndroidTest` 的 instrumentation APK 保持 Android Gradle Plugin 默认命名，不作为用户安装包发布。`scripts/Test-MumuUi.ps1` 已按 metadata 自动选择应用 APK，继续使用 `install -r` 保留数据。

## Release 体积优化

- AGP 9.4 的 `optimization { enable = true }` 对整个应用执行 R8 代码压缩、优化、混淆和资源压缩，不再仅优化部分依赖包。
- 保留必要 JNI 契约：`src/release/keepRules/native.keep` 保留 `NativeAudioCore$Bridge`；`MpvNative` 使用既有 `@Keep`。不要用全包 keep 或忽略所有警告来绕过 release 故障。
- Release 原生库在 APK 内压缩，manifest 的 `extractNativeLibs=true`，由 Android 安装时解压。这样减小 APK 文件，但需要额外的安装解压空间。Debug 原生库仍不压缩，方便调试。
- 原生库继续由构建流程剥离调试符号，保留冻结的媒体能力和许可证资源，不用随意删库、裁剪语言或更换解码器缩小体积。编译工具链与 minSdk 不变。
- 各 ABI 的 ELF `PT_LOAD` 仍须至少 16 KB 对齐。APK 中未压缩的库还须满足 ZIP 16 KB 对齐；压缩库安装后从文件加载，不用其压缩数据的 ZIP 偏移冒充 mmap 对齐验证。使用 `scripts/Verify-MpvArtifact.py <APK> <report.json>` 分别审计每个 APK。

已核对锁定 AGP 源码及官方 [ABI 分包](https://developer.android.com/build/configure-apk-splits)、[应用优化](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization?hl=en) 文档。输出命名和 release JNI 压缩使用公开 Variant API。

## 正式签名

在 Android Studio 选择 **Build → Generate Signed App Bundle / APK → APK**，选择自己的 keystore／alias 和 release 变体。两个架构分别产出，仍遵循上述命名和优化规则；保存同一次构建的 `app/build/outputs/mapping/release/mapping.txt`，用于反混淆崩溃堆栈。

工程不保存 keystore、密码或默认正式签名配置；直接执行 `assembleRelease` 在未注入签名配置时得到未签名 APK。文件名中的 `release` 表示构建类型，不代表已经签名。正式产物应使用 `apksigner verify --verbose <APK>` 核验，并保留签名证书身份以便后续覆盖升级。

B01 的运行验证使用现有调试证书签署 **独立 release QA 副本**，不将其作为正式签名包，也不把 debug signingConfig 写入 release。已有设备一律 `install -r`；签名不匹配时停止覆盖安装，不卸载清空用户数据。第三方 native 完整源码／许可与可复现发布 gate 仍按 ADR0007 执行，构建成功不等于发布 gate 已关闭。

## 优化包冒烟检查

普通 debug JUnit runner 依赖未混淆的 Kotlin 类，不能直接运行在完整 R8 包上。用 `./gradlew.bat assembleDebugAndroidTest -PreleasePackagingQa=true` 生成仅依赖 Android API 的专用 runner；它不改变正式应用的压缩／签名配置。

1. 用 `./gradlew.bat :app:signingReport` 确认现有调试证书路径，不假定一定在用户主目录。用该证书签署 release 的独立 QA 副本，先 `apksigner verify --verbose` 验签。
2. 对 QA 应用和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 使用 `adb -s <serial> install --no-incremental -r <APK>`；同时检查命令结果与设备实际 versionName／debuggable，不能只看 adb 进程 exit。
3. 运行 `adb -s <serial> shell am instrument -w -r yy.daniel.yyplayerforandroid.test/yy.daniel.yyplayerforandroid.ReleasePackagingInstrumentation`。必须返回 `PASS: 3 release checks`，包含版本／启动、PCM JNI 契约、mpv 自制视频解码；它不修改用户库／偏好或全局设置。
4. 结束后不带 QA 属性重新 `assembleDebug assembleDebugAndroidTest`，通过 `install -r` 恢复正常 debug 应用与 JUnit runner。不要使用会卸载用户数据的 connectedAndroidTest。

本轮结果、大小与 SHA-256 见 [B01 验证](validation/build-b01/README.md)。

## 签名不同的实体机调试（U21）

手机已安装用户签名 release 时，不卸载／清数据。显式 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PdeviceQa=true` 构建 `yy.daniel.yyplayerforandroid.qa`，标签为 `YYPlayer QA`，使用独立偏好和测试素材，与正式包共存。该参数不是正式发布配置；不带参数仍为正式 applicationId。输出文件命名规则相同，切换参数之前保存需要保留的 QA 产物到独立 build 子目录。

同样可用 `-PdeviceQa=true` 构建 minified release，并以本机调试证书签名 QA 副本测试；不伪装成正式发布签名。`-PreleasePackagingQa=true` 仅选择 Android 平台原生测试 runner，用于检查实际 R8 release 的启动、JNI 和 mpv FD 解码。正常 AndroidJUnit 测试包不能用于依赖类名已混淆的 release。测试完用 `install -r` 换回共存 debug，不动正式包。
