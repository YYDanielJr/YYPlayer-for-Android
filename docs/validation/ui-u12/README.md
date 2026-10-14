# U12 前端与动效验收

2026-10-13，`dev` 工作区，基于 `c75b34d`；U11／U12 均尚未提交。本轮按用户两张圈选截图修改，没有修改 master 或 Rust。设备为有线小米 13 Ultra `c9e352e3`／`2304FPN6DC`／API36／`OS3.0.308.0.WMACNXM`。

## 实际验证

| 项目 | 结果 |
| --- | --- |
| 大封面阴影 | 随过渡一起出现，完整等待弹簧收尾再交给静态封面，录屏未再看到到位后阴影闪现 |
| 歌词小封面收回 | 从歌词页原小封面连续平移至首页左下角，圆角逐渐变圆，无底栏封面闪现；快速反向能回到库页 |
| 相邻页绘制 | 展开封面时不再漏出屏幕外歌词页文字 |
| 播放页四项操作 | 倍速、收藏、播放顺序、队列排在同一操作栏，保留前后曲与播放主控；没有单独顺序胶囊 |
| 队列独立页面 | 新 Activity 正常打开／返回，仍为同一播放服务，返回保持原曲目和暂停位置 |
| 长按排序 | QA Song 4 从第 4 位拖到第 1 位，再拖回第 4 位；其余条目让位，QA Song 1 始终仍为当前曲目 |
| 单／多选移除 | 单选显示 1，多选显示 2；移除 Song 3／4 后仅剩 Song 1／2；媒体库仍有 4 首 |
| 歌曲组圆角 | 第一行顶部与最后一行底部圆角，中间行连续，首页和队列一致 |
| 歌词偏移菜单 | “延后 0.5 秒／提前 0.5 秒／重置”均为整行设置，保留全宽灰色取消；偏移值未更改 |
| 使用引导 | 首屏“稍后设置／开始设置”、下一页“上一步／继续”都是大按钮；灰色次要操作、主题色确认；前进、返回、稍后设置通过 |

使用 U11 自制的 4 首 30s 低幅 FLAC、纯色封面和双语 LRC。没有读取或截图用户私人媒体；圈选参考截图只留在忽略的 build 目录。安装一律 `install -r`，未卸载、未清除导入／偏好。测试后恢复 4 首原顺序队列，QA Song 1 暂停，停留音乐库。

## 构建与测试

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
adb -s c9e352e3 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s c9e352e3 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s c9e352e3 shell am force-stop yy.daniel.yyplayerforandroid
adb -s c9e352e3 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.MusicUpgradeDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
```

- [最终构建](checks.txt)：exit 0，BUILD SUCCESSFUL 1m13s；34 项 JVM 测试、0 失败；lint 0 错误、2 个原有警告（ExportedService／NewerVersionAvailable）。
- [真实播放回归](device-tests.txt)：adb exit 0，OK (5 tests)，2.934s。它验证播放／队列／输出回退／持久化，不替代 UI 手势验收。
- 长按拖动使用 Android `input touchscreen draganddrop` 与实际 UI 检查；早期发现滚动抢占，修正 Initial pass 事件接管后已在最终 APK 重验双向拖动。普通页面操作使用 UIAutomator 层级定位和 adb 输入，未使用假队列或假播放进度。
- 主 APK SHA256：`BC128A9E1500488DB884015D451A334E2C3815AA6007410694985FADDD789C95`。

## 视觉证据

[音乐库](library.png)、[封面页](cover.png)、[歌词页](lyrics.png)、[队列](queue.png)、[排序后](reordered.png)、[多选](selection.png)、[偏移菜单](options.png)、[向导首屏](setup.png)、[向导下一步](setup-next.png)。截图裁去系统顶部并缩小，内容为自制素材。

[最终转场录屏](motion.mp4) 包含封面收回／展开、翻到歌词后收回，以及展开过程立即返回；[录屏抽帧](motion.png) 用于对比位置和阴影。录屏裁去系统栏，不把抽帧或主观观感当作帧率／功耗证明。

未重新运行已知在本 ROM 上挂起的 Compose Activity suite；不计其通过。未新增 native／DAC 资格，API26–32、横屏、200% 字体、TalkBack、深色专项、长队列靠边滚动与多指取消尚未单独设备验收。动画手感仍待用户复核。
