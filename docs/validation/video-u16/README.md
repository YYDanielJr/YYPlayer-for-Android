# U16：可选 libmpv 与桌面视频功能（2026-10-14）

用户要求继续第四项，并允许参考 libmpv-android、更换视频后端。本轮在 dev 实现可选路径，未提交／推送／发布；原 D01／U15 修改保留，Rust HEAD e6a8456 只读且 git status 干净。

## 已实现

- 「播放工具 → 视频后端 → libmpv · 扩展功能」，保存选择，Media3 仍为默认；切换保留时间、基础速度、暂停和音量。
- 真正的章节列表／跳转、mpv 双向逐帧、音频／字幕 ±10 秒偏移、libass ASS 样式／定位／动画、三种字体家族预设和样式覆盖、deband。原音轨、外字幕、A–B、3×、队列、全窗口变换保留。libass 直接绘入视频，截图包含字幕。
- `MpvVideoBackend`／`MpvNative`／`native/jni/mpv_worker.cpp`：后台打开 seekable content URI FD，native worker、64 命令队列、合并 seek／poll、UTF-8 字节边界、句柄注册表、白名单、真实 SimpleBasePlayer／MediaSession。Surface／FD 在 worker 销毁完成后释放；不会在主线程等待原生 join。
- `AndroidMedia3PlaybackBackend` 保持唯一运行时入口，先销毁旧输出才启动另一路；自检通过完成回调等待视频销毁。音乐仍使用原解码／输出后端。
- 固定 Linux CI AAR，app 与新旧 JNI 共用 AAR 的一份 libc++。没有运行上游下载／安装脚本或修改全局环境。架构与未关闭 gate 见 [ADR0007](../../adr/0007-libmpv-video-adapter.md)。

## 命令与结果

```text
gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain
exit 0；37 项 JVM，0 failure；lint 0 error、2 既有 warning。

adb devices -l
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
均 exit 0；无 uninstall／clear。

adb -s 127.0.0.1:16384 shell am instrument -w -r -e class
yy.daniel.yyplayerforandroid.MpvVideoDeviceTest,yy.daniel.yyplayerforandroid.VideoUpgradeDeviceTest,yy.daniel.yyplayerforandroid.VideoSurfaceGestureTest,yy.daniel.yyplayerforandroid.VideoPlayerUiTest,yy.daniel.yyplayerforandroid.MusicUpgradeDeviceTest
yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
exit 0；OK (22 tests)，27.632s。见 device-tests.txt。

最后补足 Session stop/prepare、队列满时的关闭顺序和清空队列的 Surface 销毁边界后，再执行上述构建／JVM／lint，
并直接 instrumentation MpvVideoDeviceTest：exit 0；OK (5 tests)，17.306s。
见 device-final-stop.txt；既有 17 项无相关新增修改，不重复扩展测试。

python scripts/Verify-MpvArtifact.py app/build/outputs/apk/debug/app-debug.apk docs/validation/video-u16/native-audit.json
exit 0；两 ABI 的全部 24 个 so 均 ELF／ZIP 16 KB 对齐，记录每个 SHA256／SONAME／DT_NEEDED。
git diff --check
exit 0。
```

全量 22 项对应 APK SHA256：`422d4cd72c2941f4239ebe1972a1670c23da6b3a6ae7e7f3e06ba070913e5d54`。最终包 SHA256 以 [native-audit.json](native-audit.json) 的 `apk_sha256` 为准；该最终包已 install -r。日志中的 `INSTRUMENTATION_CODE: -1` 为 Android instrumentation 正常结束码；通过依据是 `OK` 与各 case status 0。

## 设备与素材

MuMu：`127.0.0.1:16384`，API35／Android15，1080×1920（测试横屏），伪装型号 `2304FPN6DC`；实际系统与 app ABI 均 x86_64。没有把它记为实体小米设备。

- `v16-chapters.mkv`：由 U15 自制 testsrc2 320×240／24fps／12秒＋低幅双 AAC 重封装，三章 0／4／8秒，紫色描边 ASS `\pos`／`\t`／`\frz`；SHA256 `38d23d1bbd3c3a82592b405d5972efd413e02400c023ef2cf1875ac915e18c39`。
- `v16-tone.wav`：自制 330Hz／6秒／48kHz PCM16，volume 0.01；SHA256 `e7f30e3c76349816a8864664f8fb8f6da80820e255aba2b8c901319c155e5c18`。
- 测试独立 `v16_test_*` 偏好及缓存，没有导入／删除用户媒体。只保留自制画面 `ass-on.png`、`ass-off.png`、`tools.png`。

5 项 MPV 用例覆盖：真实章和双向一帧时间差、偏移／字体／样式／deband 实际属性、选择后解码重建；ASS 开关实际像素差（不是仅看属性）；Surface 分离／重建和双后端往返；同一个 MediaController 播放／暂停／seek／stop／prepare 后转 native PCM 音乐；A–B 两次回绕、绝对 3× 恢复 2×／旧 token；完整菜单点击驱动章节和字幕偏移。

修复过程中发现：NDK28 libc++ 缺失浮点 from_chars；样片 ASS 非默认轨道必须明确选择；纯测试后端需像生产 runtime 一样刷新音乐进度；seek ACK 不代表画面已定位，逐帧需等待真实 seeking 结束。最终均有回归覆盖。MuMu GL 驱动仍输出 INVALID_OPERATION 警告，视频与字幕像素正常；不据此宣称 GPU 性能或 HDR 资格。

## 未验与下一步

实体 arm64 装载／硬解、API26–32、HDR、物理多指／触感、release 帧率／耗电、系统 SAF 完整导入／保存流程和异常 provider 专项仍未验。字体仅三类预设，无自选字体文件；桌面全部高级渲染参数／shader 及平台窗口功能未迁入。libmpv 严格硬解暂不开放，视频跟随系统媒体输出，不提供独占／位准确承诺。

上游 FFmpeg 构建启用 GPL 与 version3；MIT 仅是包装器。固定 AAR、构建脚本、头文件和全部打包 so hash 已记录，完整依赖源码 SHA、相应源码／许可证交付及可复现构建 gate 仍需补齐，本轮不发布。下一步是用户使用扩展入口复核体验，以及在实体设备和低版本环境完成资格验证；没有写“视觉已满意”。
