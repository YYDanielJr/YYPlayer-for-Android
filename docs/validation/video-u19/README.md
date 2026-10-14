# U19 自由倍速及 dev 提交（2026-10-14）

用户要求设置内以 0.1× 为单位自由调节 0.1×–10×，外侧保留快捷档位；更新文档和 AGENTS.md 后提交 dev。本次集中提交此前累计 U15–U18 与 U19 的视频代码、测试、自制素材及文档，另含已有当前／历史基线文档整理；不推送、不合入 master。提交身份以 git log 为准。

## 实现

- 新 `VideoSpeedSettings.kt`：播放工具中的自由倍速页，滑杆吸附 0.1×／松手生效，加减按钮与恢复 1×；边界禁用，SetProgress 可直接应用。原 SpeedDialog 的 0.5／1／1.25／1.5／2／3 不变。
- `VideoPlayerPage.kt` 接入子菜单，继续使用 U18 抽屉返回和真实滚动。`AndroidMedia3PlaybackBackend.kt`／`MpvVideoBackend.kt` 扩展范围、拒绝非有限值；Media3 实际 0.1–8，8 以上通过既有切换流程进入 libmpv，保留位置／暂停／音量；仅硬解不自动换后端，最高 8。见 ADR0008。音乐原有精确输出保护保留。
- `VideoPlayerUiTest` 真实滑到 10，检查状态来自 MPV；验证 0.1／0.2／7.4、1.25 快捷不变、加减量化、恢复正常和 3× 松手恢复 10。`MpvVideoDeviceTest` 回读 0.1／0.2／1.25／8.1／10，重建保留 10、临时 3× 恢复 0.1。
- 当前 README、AGENTS、技术设计、实施计划、视频体验和 STATUS 更新，U15–U18 的“未提交”保留为历史记录。AGENTS 补真实滑动证据规则、菜单父级、后端限速事实与视频 native 已实现／待验边界。

## 命令和结果

```text
git branch --show-current：exit 0，dev
adb devices -l：exit 0，127.0.0.1:16384 MuMu
javap -c -p androidx.media3.exoplayer.audio.DefaultAudioSink（classpath 为锁定 1.11.1 classes.jar）
  exit 0：软件路径 speed 使用 constrainValue(0.1f, 8.0f)
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain
  exit 0，BUILD SUCCESSFUL in 35s；37 JVM tests / 0 failures / 0 errors
  lint 0 error、2 既有 warning：NewerVersionAvailable、ExportedService
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  均 exit 0／Success；用户数据保留
adb -s 127.0.0.1:16384 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.VideoPlayerUiTest,yy.daniel.yyplayerforandroid.MpvVideoDeviceTest,yy.daniel.yyplayerforandroid.VideoUpgradeDeviceTest,yy.daniel.yyplayerforandroid.VideoSurfaceGestureTest,yy.daniel.yyplayerforandroid.MusicUpgradeDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
  exit 0，OK (28 tests)，39.78s；见 device-tests.txt
python scripts/Verify-MpvArtifact.py app/build/outputs/apk/debug/app-debug.apk docs/validation/video-u19/native-audit.json
  exit 0，24 so、两 ABI 的 ELF／stored ZIP 16 KB 对齐通过；见 native-audit.json
git diff --check：exit 0
```

首次 28 项中 1 个既有短横屏用例点击失败：新增菜单行后字幕项被底部按钮遮住，测试未先滚入可见范围。保留实际双向 swipe 位移断言后，将该点击前滚到目标，再完整复跑 28 项通过；新倍速用例两次均通过。

APK SHA256：`dabb082e863553bbe30aa78a57e00466f62ea7ea7e4e4391d17d50a537690ebd`。最终 APK 已 install -r。

## 设备／证据／限制

MuMu `127.0.0.1:16384`，API35／x86_64／1920×1080 横屏（不是实体小米）。测试沿用 U15 自制 testsrc2／双音轨、U16 自制 MKV／ASS／PCM WAV，独立偏好。UI setup 明确恢复 Media3，避免新自动切换测试影响其他测试初态。[10× 设置截图](custom-speed.png) 只含自制视频。

实体 arm64、API26–32、极限速度解码帧率／音质／完整 TalkBack 仍待验；原生源码／许可证交付与可复现发布 gate 沿用 ADR0007，提交源码不等于发布资格完成。本轮未重跑未改动的主机 CTest／DAC 测试。下一步由用户在设备复核倍速体验；未声明视觉满意。
