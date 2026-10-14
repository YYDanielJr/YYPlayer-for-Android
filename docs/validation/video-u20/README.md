# U20：视频倍速统一最高 8×（2026-10-14）

用户要求上限降至 8×，删除特殊适配，修改文档并提交。本轮基于 dev `d551f38`；初始工作区干净，按授权提交 dev，不推送／合入 master。

## 修改

- `VideoSpeedSettings.kt`／`VideoPlayerPage.kt`：统一 0.1×–8×，步进 0.1×；外侧快捷档位不变。滑杆不再接收引擎／仅硬解状态，移除超过 8× 自动换后端的提示和仅硬解例外文案。
- `AndroidMedia3PlaybackBackend.kt`：删除 `setVideoSpeed` 路由包装与引擎切换的 requestedSpeed 参数，UI 直接调用原有 setSpeed。保持原手动后端选择。
- `MpvVideoBackend.kt`：上限 10 改为 8，与 Media3 视频一致；越界请求也限制为 8。
- 更新两套设备测试、README、AGENTS、技术设计、实施计划、视频体验和 ADR0008；U19 的 10× 作为历史决定保留并明确已覆盖。

## 验证

```text
git branch --show-current / git status --short
  exit 0，dev，修改前干净
adb devices -l
  exit 0，127.0.0.1:16384 MuMu
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain
  exit 0，BUILD SUCCESSFUL in 31s
  37 JVM tests，0 failures／0 errors；lint 0 error、2 既有 warning
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  两次 exit 0／Success，无卸载／清数据
adb -s 127.0.0.1:16384 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.VideoPlayerUiTest,yy.daniel.yyplayerforandroid.MpvVideoDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
  exit 0，OK (14 tests)，31.806s；见 device-tests.txt
git diff --check
  exit 0
```

实测 UI 拖到 8×，仍保持 Media3；UI SetProgress(10) 和直接 setSpeed(10) 都限制到 8。MPV 回读 0.1／0.2／1.25／7.9／8，越界限制为 8，重建保持 8。保留 0.1 步进、1.25 快捷、长按绝对 3× 恢复 8／0.1、抽屉真实滑动和返回回归。

APK SHA256：`c335cd42dcac30d74e496bd9d3fa646835dae124e88939bba3add16fa72f892f`；已覆盖安装。[8× 设置截图](custom-speed.png)。

设备 MuMu API35／x86_64／1920×1080，独立测试偏好和 U15／U16 自制素材，无用户视频帧。实体小米、高速解码帧率／听感、API26–32、完整无障碍仍待验；本轮未改原生库或音频输出，不重复 native 对齐／DAC／无关回归。下一步用户复核统一范围；无视觉最终确认声明。
