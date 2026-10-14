# U13 按钮比例、封面返回与透明控件误触验收

2026-10-14，`dev` 工作区，基于 `c75b34d`，保留 U11／U12 未提交修改。本轮没有提交／推送，没有修改 master 或 Rust。设备：有线小米 13 Ultra `c9e352e3`、`2304FPN6DC`、Android 16／API36、`OS3.0.308.0.WMACNXM`；1440×3200、density 560（3.5 px/dp）、font_scale 1.0。

## 参考图与按钮测量

用户最新两张参考来自 `Screenshot_2026-10-14-08-46-04-259_com.miui.securitycore.jpg` 和 `Screenshot_2026-10-14-08-46-22-916_com.xiaomi.smarthome.jpg`，只保存在忽略的 build 目录。

| 样本 | 按钮高度 | 字体分析与采用值 |
| --- | --- | --- |
| 企业模式页面 | 183px，约 52.3dp；纵向间隔约 16dp | 六字可见字形约 365×57px，接近 18sp |
| 米家确认弹窗 | 175px，50dp；并排间隔约 12dp | 两字可见字形约 111×54px，接近 17sp |
| 新版队列页面 | 182px，52dp，直边 y2878–3059 | 18sp、Medium、24sp 行高 |
| 新版确认弹窗 | 175px，50dp；主题色 y2612–2786、灰色 y2843–3017 | 17sp、Medium、22sp 行高 |

字形像素边界受字体与抗锯齿影响，截图不能唯一确定原始 sp 或字重。采用继承系统字体的 Medium 500，替换旧版 SemiBold 600；统一 16dp 圆角、内容水平 20dp／竖向 12dp。最小高度允许随大字体增长。保留页面对齐边距、并排等宽和 12dp 间隔，图标触控目标保持 48dp。取消／次要灰底，确认主题色。

## 实际操作

- **误触成功复现**：U12 旧版展开封面，点击收起，等待约 350ms，再点击底栏 `(800,2970)`。20fps 抽帧看到“歌词与播放”弹窗短暂出现后消失。原因是详情透明后仍参与点击测试，顶部菜单随位移到达底栏；独立窗口不继承父页面透明度，详情树随后销毁又关闭窗口。见 [修复前录屏](popup-before.mp4)。
- **同操作对照**：最终 U13 同坐标、同等待时间，正常重新打开大封面，未出现菜单弹窗，也未误触播放。见 [修复后录屏](popup-after.mp4)。
- **收回**：大封面连续缩回，阴影和圆角一起缩放；共享层静止时预留，避免退出时临时创建。收回改为有界单调曲线，没有小封面过冲与弹簧长尾。歌词 44dp 小封面向左下角移动并变圆；歌词收起后约 180ms 点击仍被过渡拦截，未出现弹窗。见 [歌词返回录屏](lyrics-return.mp4)。录屏抽帧是视觉检查，不是 release 帧率基准。
- **按压**：右上角 `(1230,700)` 持续按压 2s，最大倾斜参数 9°，临界阻尼与慢跟随保持；截图见 [按压](tilt.png)。
- **首页**：两张快捷卡片已移除；4 首歌曲保持一整组圆角。`歌单 → 我的收藏` 能打开原收藏列表（原来为 0 首），返回分类与歌曲切换正常；歌单 `QA-Playlist` 保留。
- **按钮**：队列、清空确认弹窗、引导首屏实机截图复核；清空只打开确认后取消，没有实际清空。引导“开始设置 → 上一步 → 稍后设置”正常；保留完成标记和联网／外观偏好。

截图：[首页](home.png)、[队列](queue.png)、[确认弹窗](dialog.png)、[引导](setup.png)、[歌词](lyrics.png)、[收藏入口](favorites.png)。证据只包含原有自制低幅 FLAC／纯色封面／双语 LRC，已裁去系统状态栏。没有保存个人媒体画面。

## 构建与回归

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
adb -s c9e352e3 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s c9e352e3 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s c9e352e3 shell am force-stop yy.daniel.yyplayerforandroid
adb -s c9e352e3 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.MusicUpgradeDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
```

- [最终构建](checks.txt)：exit 0，BUILD SUCCESSFUL 1m15s；JVM 34 项、0 失败／错误；lint 0 error／2 原有 warning（NewerVersionAvailable、ExportedService）。
- 两次 install -r 均 Success，没有卸载、清除应用数据或修改全局设置。
- [真实播放回归](device-tests.txt)：adb exit 0，OK (5 tests)，3.148s；使用独立测试偏好验证队列、EOF、回退、持久化和歌词迁移，不代替视觉检查。
- APK SHA256：`CD9250290E7FAE111F71678A9C317C425D261B715C38E49B9D9711C6BCAC0FEE`。
- `git diff --check` exit 0。`LibraryTabsUiTest` 的页面断言由已删除卡片改为“本地音乐”；已编译，没有重跑该 ROM 上既有挂起的 Compose suite。

未验：不同刷新率的 release 性能、横屏／200% 字体／TalkBack／深色专项、API26–32、物理多指；本轮未改 native、未重跑 CTest，有线占用 USB，不新增 DAC 或独占输出资格。下一步由用户复核按钮比例和动画手感。
