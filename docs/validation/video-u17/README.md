# U17 横屏视频设置抽屉（2026-10-14）

后续更正：用户在 MuMu 发现真实拖动失效；本轮的 performScrollTo 只验证了语义滚动，未覆盖触摸。根因与修复、真实 swipe 测试见 [U18](../video-u18/README.md)。

用户要求横屏设置从右侧抽屉式弹出、背景略透明。本轮在 dev 修改，未提交／推送，保留已有 D01／U15／U16 改动。

## 实现与文件

- `ui/VideoSettingsDrawer.kt`：按实际播放器布局宽高决定横竖；横屏右侧抽屉宽 320–420dp（窄窗口留出至少 48dp 左侧空间），260ms 滑入／滑出，减少动态效果时立即切换。Miuix surface 90% 不透明度、左侧圆角 24dp，屏幕遮罩 22% 黑色；不读取视频帧实现模糊。
- `ui/VideoPlayerPage.kt`：工具、字幕／音轨、画面、倍速、队列、章节、同步、A–B、媒体信息复用同一抽屉；切换子页不叠加抽屉。设置期间隐藏播放控制层，屏蔽底层无障碍节点。媒体／播放 Surface 不调整尺寸。
- `ui/HyperDesign.kt`／`ui/PlayerScreens.kt`：仅在视频横屏 composition scope 改用抽屉内容容器；竖屏和音乐仍是既有 Miuix WindowDialog。滚动区域和底部完成／取消按钮分离，选择后执行真实后端命令并关闭。
- 返回键、点击左侧空白、完成按钮关闭。退出时保留绘制内容直到动画结束，同时取消该内容的指针／语义交互，遮罩阻止穿透；保留系统安全边距。
- `VideoPlayerUiTest.kt`：新增右侧几何／选择／关闭、窗口宽高切换及 200% 字体、滑入位置及退出输入阻挡三项；原三个视频 UI 用例保留。

## 命令和结果

```text
git branch --show-current                         exit 0：dev
adb devices -l                                    exit 0：127.0.0.1:16384 MuMu
./gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug --console=plain
                                                  exit 0，BUILD SUCCESSFUL in 36s
                                                  lint 0 error、2 既有 warning
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 127.0.0.1:16384 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
                                                  两次 exit 0 / Success，保留数据
adb -s 127.0.0.1:16384 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.VideoPlayerUiTest,yy.daniel.yyplayerforandroid.MpvVideoDeviceTest#expandedToolsAndSyncControlsDriveRealEngine yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
                                                  exit 0，OK (7 tests)，11.872s
adb -s 127.0.0.1:16384 shell am start -W -n yy.daniel.yyplayerforandroid/.MainActivity
                                                  exit 0，Status: ok
git diff --check                                  exit 0
```

首次运行 6 项中 1 项失败：测试点击“画面”后立即从 instrumentation 发 BACK，没有等待 Compose 注册抽屉返回回调，测试 Activity 被关闭。增加抽屉显示断言与 waitForIdle 后，实际系统返回键验证通过；不是将失败项删除。最终 7 项日志见 [device-tests.txt](device-tests.txt)。

APK SHA256：`e0f01b66da48d9d127cf96e502d7b1727c674abdfed8f819edf1cb7ba7c26ddc`。

## 设备、素材与截图

MuMu API35／x86_64，当前 1920×1080 横屏、280dpi（报告型号 2304FPN6DC，不是实体小米）。测试沿用 U15 自制 testsrc2 视频和 U16 自制 MKV／ASS，独立测试偏好，无用户素材／全局系统设置改动。所有安装均 install -r。

- [横屏工具](landscape-tools.png)：轻微透出视频色块，标题与操作清晰，底层控制按钮隐藏。
- [横屏字幕](landscape-tracks.png)：滚动到下方设置。
- [360dp 高横屏／200% 字体](short-large-text.png)：实际约束布局，固定完成按钮仍可见；截图下方空白属于测试宿主。
- [竖向布局弹窗](portrait-dialog.png)：360dp 宽的实际内容约束，保留 Miuix 弹窗。它验证宽高适配，不是物理旋转截图。

## 限制和下一动作

本轮为 UI 修改，未重跑与之无关的 native／音频硬件／JVM 逻辑测试；U16 记录仍仅代表当轮证据。物理小米、完整 TalkBack、API26–32 与 release 帧率未验；没有声称视觉已获用户确认。下一步为用户在横屏实播中复核宽度、透明度和手感。
