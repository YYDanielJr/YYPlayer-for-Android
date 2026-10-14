# U11：2026-10-13 小米 13 Ultra 验证

Android 分支 `dev`，基于 `c75b34d`（已提交 dev-hifi 并 fast-forward 合入）；本轮新修改未提交。Rust `e6a8456` 只读参考。设备 `2304FPN6DC`／Android16 API36／`OS3.0.308.0.WMACNXM`，有线 adb `c9e352e3`，没有连接 USB DAC。

## 构建与专项测试

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
adb -s c9e352e3 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s c9e352e3 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s c9e352e3 shell am force-stop yy.daniel.yyplayerforandroid
adb -s c9e352e3 shell am instrument -w -r -e class yy.daniel.yyplayerforandroid.MusicUpgradeDeviceTest yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
```

- [最终构建](checks.txt)：exit 0、1m22s；34 项 JVM 测试全部通过；lint 0 错误／2 警告（ExportedService、NewerVersionAvailable）。不更改锁定依赖版本。
- [最终设备测试](device-tests.txt)：exit 0、OK (5 tests)、3.072s。使用实际 Media3 解码／播放位置／EOF，覆盖顺序结束、单曲循环、随机切歌、手动前后切歌、插入／重排／移除和清空；隔离偏好验证歌单持久化及歌词设置迁移；真实 AAC sink 拒绝后恢复 SYSTEM，并验证 strict 拒绝、PreferExclusive 降级且没有虚报 native 协商。
- 主 APK SHA256：`825F747F145C8475C170817906987BCA1A7F45B029C6B0B9CF9749E2AE9C3856`。
- 自制测试素材 `app/src/androidTest/assets/u11-gapless.m4a`：6s／48kHz 低幅 440Hz 正弦 AAC，FFmpeg 生成，含 encoder delay；SHA256 `B30EF7E77CC0261229A40DFB913D134C3E084FFA437606C28EC7EA7878203BB5`。其用途是检验现有 native 不支持 gapless trimming 时的真实回退，不能用它宣称 native gapless 或音质资格通过。

另一次组合执行在上述 5 项通过后卡在 `LyricStyleUiTest`，无进展后主动结束测试进程；[日志](compose-ui-incomplete.txt) 中的 `Process crashed` 是这次未完成运行的结果。新增双语设置 UI 用例已编译，LyricStyleUiTest／LibraryManagementUiTest／LibraryTabsUiTest 本轮不计通过。未观察到普通应用未捕获的 AndroidRuntime 崩溃。

## 普通应用实际操作

通过 SAF 授权自制 `Music/YYPlayerQA-U11` 文件夹：4 首各 30s 的低幅 FLAC、内嵌纯色封面、双语 LRC；歌手 QA Artist A/B，各自专辑都叫 Shared Album。没有导入用户私人素材或清空应用数据。

| 操作 | 实际结果与证据 |
| --- | --- |
| 歌手／专辑浏览 | 两个歌手、两个独立同名专辑，分类播放作用于组内歌曲 |
| 歌曲菜单→新建歌单→添加 | `QA-Playlist` 保存 1 首，安装更新后保留，歌单播放生成 1 首队列 |
| 音乐库底栏 | 只保留播放／暂停及真实环形进度，[音乐库](library.png) |
| 播放队列 | 当前曲目、模式、移动／移除／清空入口，[队列](queue.png)；行为另由真实播放器专项测试验证 |
| 译文设置 | 当前译文 24sp、原译间距 20dp；原文 28sp／原行距 14dp不变，[设置](bilingual-settings.png) |
| 双语歌词 | 设置返回立即生效，整组点句 seek 保持暂停；AC/DC／日期不误拆，[歌词](bilingual-custom.png) |
| 封面展开收回 | 左下角小封面到大封面，再收回；快速反向能回到库页，[录屏抽帧](motion.png) |
| 封面角落按压 | 右上角持续按压有平缓透视倾斜，[按压中](cover-pressed.png)；松手恢复，点击正常切换歌词页 |

结束状态：停留音乐库、播放暂停；歌词恢复默认值，原文 28sp／原行距 14dp，当前／其他译文 20sp／原译间距 8dp。自制 QA 素材与 `QA-Playlist` 留在设备上供复核。截图和动画抽帧均已裁去系统顶部并缩小；所有音乐内容均为自制素材。

## 边界

本轮验证手机内置输出和系统路径，没有 USB DAC／独占／位准确资格验证。严格输出模式仍按既有规则拒绝降级，失联输出不自动转扬声器。API26–32、暗色专项、200% 字体、TalkBack、物理多指尚未补验；Compose UI instrumentation 仍需单独排查或换设备。动画已实机操作和录屏核对，最终手感待用户复核。
