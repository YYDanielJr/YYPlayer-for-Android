# U18 抽屉真实滑动与逐级返回（2026-10-14）

用户在 MuMu 反馈无法滚动设置、子菜单没有画面内返回上一级按钮。本轮在 dev 修复，未提交／推送；既有修改和用户数据保留。

## 原因和修改

- `VideoSettingsDrawer.kt` 的父级 pointerInput 在 Final pass 无条件 consume，每次按下即让子级滚动手势取消。改为仅退出动画期间在 Initial pass 拦截；打开时不消耗子级事件。父级仍占有命中路径，空白区域不会穿透到视频。
- `VideoPlayerPage.kt` 增加菜单历史：工具进入各设置、字幕进入同步均记录实际父级；底栏快捷进入设置时父级为工具。子菜单标题左侧显示 48dp Miuix 返回箭头，系统返回键使用相同层级；根菜单返回关闭，完成／取消和左侧遮罩仍直接关闭抽屉。快速连续返回有空栈保护。
- `VideoPlayerUiTest.kt` 新增触摸 swipeUp／swipeDown，断言真实滚动偏移；在 360dp 高／200% 字体下连续滑动到底，进入媒体信息，再点画面内返回。`MpvVideoDeviceTest.kt` 补工具 → 字幕与音轨 → 字幕同步 → 字幕与音轨 → 工具的完整路径，仍验证真实字幕偏移。
- U17 采用 performScrollTo 语义滚动，绕过 pointerInput，未证明实际手指滚动；其通过记录不能当作真实拖动已通过。本轮用实际触摸事件补足。

## 验证

```text
git branch --show-current：exit 0，dev
adb devices -l：exit 0，127.0.0.1:16384 MuMu
./gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug --console=plain
  exit 0，BUILD SUCCESSFUL in 31s；lint 0 error、2 既有 warning
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  均 exit 0／Success，未卸载／清数据
adb -s 127.0.0.1:16384 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.VideoPlayerUiTest,yy.daniel.yyplayerforandroid.MpvVideoDeviceTest#expandedToolsAndSyncControlsDriveRealEngine yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
  exit 0，OK (8 tests)，14.43s；见 device-tests.txt
git diff --check：exit 0
```

最终 APK SHA256：`8afeefaea8b5bcfffb4c5e5f52e5bf3da6ff29731f941658b9f33fd78b8b96f4`。

设备为 MuMu API35／x86_64、1920×1080 横屏；测试内使用 360dp 高／200% 字体约束。沿用 U15 自制 testsrc2 和 U16 自制 MKV／ASS，独立测试偏好，无用户视频帧或全局设置改动。[子菜单返回按钮截图](submenu-back.png) 的下方空白为测试宿主。

实体小米触摸、完整 TalkBack／API26–32／release 性能仍待验；本轮未改变媒体后端，不重复无关硬件资格测试。下一步是用户在 MuMu 复核滚动及返回手感，未声明视觉已满意。
