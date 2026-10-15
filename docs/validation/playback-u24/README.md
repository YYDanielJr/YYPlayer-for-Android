# U24：跨页面共享播放栏（2026-10-15）

版本保持 0.1.2／versionCode 3，dev 工作区，基于 U22／U23 继续修改，未提交／推送。用户正式 release 和数据保留。

## 实现

- `PlaybackPageHost` 在内容区域之外绘制现有播放栏，实际测量高度为页面留位；统一处理 IME／导航栏 inset，搜索键盘弹出时播放栏上移。磨砂背景覆盖整个窗口，避免采样到短内容层之外造成灰块。
- `PlayerHostActivity.AppTheme` 为设置、方向、歌词样式、音频输出、队列、在线搜索、联网歌词统一接入。主页保持原封面共享层和弹簧，重新设置页也接入；完整音乐／视频页不叠加第二份迷你栏。没有媒体时不显示播放栏。
- `openPlayer` 的非主页音频路径使用 U23 压栈入口，关闭封面返回真实调用页，不清栈。显示状态和命令继续来自唯一 runtime／backend。
- 播放栏在滚动、Pager、AnimatedContent 之外，页内切换不带动它。Activity 窗口转场仍由 ROM 执行，播放栏随该窗口运动；用户已明确接受此边界。弹窗保持模态优先，没有跨应用悬浮窗权限。

主要代码：PlaybackPageHost.kt、PlayerHostActivity.kt、YYPlayerApp.kt；测试：OnlineUiTest、MusicExperienceUiTest。更新 README、AGENTS、STATUS、技术设计、实施计划、音乐体验和 ADR0004。

## 设备验证

一加平板 Pro `bdba9db1`／OPD2404／API36／ColorOS16／`OPD2404_16.0.5.1201(CN01)`，横屏 3000×2120。QA 共存包，全部 `install --no-incremental -r`，没有卸载／清数据／修改全局设置。

素材为本项目自有 WAV、几何封面、双语歌词、localhost HTTPS 32 条搜索结果；只在显式 onlineQa debug 构建信任自有测试证书。初次安装选择错误使用了不存在的 metadata `filters.identifier`，导致旧 QA 运行失败；改为实际 `filters.value` 并检查安装 exit 后重新验证，下面均为最新 APK 结果。初轮截图暴露磨砂灰块，修正背景捕获范围后重跑最终七项。

| 验证 | 结果／证据 |
| --- | --- |
| `assembleDebug assembleDebugAndroidTest testDebugUnitTest -PdeviceQa=true -PonlineQa=true` | exit 0，[构建](build-online.log) |
| OnlineUiTest 三项 | 真实 HTTPS 音质选择、入口开关、重复搜索返回；新增搜索页直接暂停／恢复、点击播放栏重新进入封面并原栈返回、真实 IME 上移／隐藏恢复、结果区域不与播放栏重叠 |
| 七类二级 Activity 专项 | 每页只有一份播放栏且边界位置相同；每页真实播放／暂停；打开封面再回到同一 Activity，mediaGeneration 不变；队列操作按钮不被遮挡；实际清空队列后播放栏消失且页面实例保持 |
| 页内动画／重新设置专项 | 主页音乐／视频切换和重新设置步骤动画逐帧测量播放栏 bounds 不变（每个转场 24×32ms）；重新设置 → 封面 → 同一步骤返回 |
| 原封面回归两项 | 平板左右双栏、三次开合、封面与歌词切换、换曲及按压形状回归 |
| instrumentation 总计 | **OK (7 tests)**，[最终输出](device.txt)；测试命令见下方 |
| 普通 QA 构建 | exit 0，[输出](build-qa.log)；最终已安装无 onlineQa 配置的 YYPlayer QA 0.1.2／3 |
| 正式 debug／R8 release、JVM、lint | `assembleDebug assembleRelease testDebugUnitTest lintDebug` exit 0，[构建](build-production.log)；[JVM](unit-results.json) 39 项／0 failure，[lint](lint-production.txt) 0 error／2 既有 warning |

正式双 ABI debug／release 及普通 QA 的 [APK 哈希](artifacts.json) 已记录；正式 release 未使用用户私钥签名，未安装覆盖用户正式包。git diff --check exit 0。

真实播放使用后端状态及 mediaGeneration 断言，并非定时模拟进度。截图分别为[在线搜索](u24-online-search.png)、[设置](u24-SettingsActivity.png)、[队列](u24-MusicQueueActivity.png)，只含自有素材。最终恢复在线功能偏好与 API 配置、移除 adb reverse 18443、停止本轮 HTTPS 服务；原包仍为 0.1.1／2。

```powershell
# 本地 HTTPS 准备和启动沿用 U23；只绑定 localhost，测试后恢复普通 QA。
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest -PdeviceQa=true -PonlineQa=true --console=plain
adb -s bdba9db1 shell am instrument -w -e class 'yy.daniel.yyplayerforandroid.OnlineUiTest,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#playbackDockPersistsAcrossSecondaryPagesAndReturnsToCaller,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#playbackDockStaysOutsidePageAnimationsAndSetupReturns,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#tabletCoverKeepsControlsLeftAndLyricsFullHeight,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#coverKeepsItsShapeDuringNextAndClickOpensLyrics' -e onlineQa true -e u07Evidence true yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner
./gradlew.bat assembleDebug assembleDebugAndroidTest -PdeviceQa=true --console=plain
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain
```

## 限制

本轮设备验收为横屏平板 debug；Compose 测试时钟逐帧 bounds 不等于 release 性能／帧率证据。API26–32、手机竖屏、大字体、TalkBack、深色和分屏未在本轮重测。跨 Activity 控制器不保持同一窗口像素位置，仍有 ROM 转场。没有更换后端／音频输出／native 库，本轮不重复 U22 的 native 装载与视频比例资格。下一步用户可在自己的歌曲／服务配置下复核视觉与手感；视觉满意度待用户确认。
