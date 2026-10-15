# U26：所有控制条页面共享封面展开／收回（2026-10-15）

验收补充：2026-10-15 用户明确已验收，并授权本次 dev 提交。以下“未提交／视觉待确认”描述验证当时状态；当前已验收，不扩大列明的设备／性能验证范围。

版本 **0.1.5／versionCode 6**，dev 工作区基于 `31fece7` 继续修改，保留 U22–U25 及用户文件，未提交／推送。

## 实现

原主页动画提取为 MusicExpansionHost，所有有播放栏的页面直接在当前 Activity 内展开。主页和二级页共用相同的弹簧、输入门禁和共享绘制层：展开 stiffness180／damping0.86，收回230／0.8，保留带符号回弹、快速反向锚点及收回后同层绘制小封面。播放栏材质仍是原主页磨砂，无新增阴影；大封面原有阴影不变。

PlayerHostActivity 的音频 openPlayer 改为消费一次的当前页命令，在线搜索解析流后也走此入口。关闭按钮和 Back 优先收起详情，底层页面保持 Composition；设置／队列等真正子 Activity 仍由 ROM 转场，返回时父页详情保持展开。旧 PlayerActivity 转向音频的兼容入口保留独立 MainActivity。

展开状态 rememberSaveable；重建时直接恢复完整详情。键盘展开时隐藏，收回使用原页更新后的控制条锚点。OnlineSearchPageState ViewModel 保留配置重建期间的结果／页码，查询和滚动位置沿用 Compose 保存，不将完整结果放入 Bundle。联网歌词页内切歌刷新当前歌曲查询，不结束承载详情的页面；旧歌词任务取消并校验 mediaGeneration。

首次逐帧验证暴露锚点时序问题：大封面先报告 bounds，启动 effect 随后清空，导致中间帧仍在小图处。删除迟到的清空，并在新详情首帧接纳目标锚点，再冻结动画中的目标。原“等待更多测试布局帧”未解决问题，不计作修复；最终以实际源代码修正和七类页面的中间几何断言确认。

文件：MusicExpansionHost、PlaybackPageHost、YYPlayerApp、PlayerHostActivity、MainActivity、OnlineSearchActivity、OnlineSearchScreen、OnlineSearchPageState；版本与 release 断言；OnlineUiTest、MusicExperienceUiTest；README、AGENTS、STATUS、ADR0004、音乐体验、在线 API、技术设计、实施计划和编译说明。

## 设备与验证

一加平板 Pro `bdba9db1`／OPD2404／API36／ColorOS16／`OPD2404_16.0.5.1201(CN01)`，横屏 3000×2120。所有安装为 QA 共存包 `install --no-incremental -r`，原正式包及数据保留。素材仅为自有 WAV／几何封面／LRC、受控 localhost HTTPS 32 条结果，未保存用户视频帧。

| 检查 | 结果 |
| --- | --- |
| onlineQa 构建与 JVM | `assembleDebug assembleDebugAndroidTest testDebugUnitTest -PdeviceQa=true -PonlineQa=true` exit 0，[日志](build-online.log) |
| OnlineUiTest 三项 | 真实 HTTPS 音质选择／入口开关；搜索页自身展开、三次按钮／系统返回，查询／结果 bounds 保持；搜索 Activity 配置重建后仍展开、结果与位置恢复；队列子页返回仍是详情；键盘升起时点栏可展开并关闭 IME，收回原栏；悬浮与末项可达性保留 |
| 七类二级页逐帧专项 | 设置／方向／歌词样式／音频输出／队列／在线搜索／联网歌词：当前 Activity 始终相同；中间帧封面大于小封面、小于终点，收回中间帧亦在两者之间；收回 bounds 与原小封面一致，同一后端与 mediaGeneration，实际播放／暂停，清队列后隐藏 |
| 页内动画／重新设置 | 主页分页与设置步骤不带动控制条；引导页封面展开／收起返回同一步骤 |
| 中断／重建／切歌 | 三次展开途中 Back 不退出原页或误弹菜单；设置 Activity 重建仍展开；减少动态效果立即呈现并正常返回；联网歌词详情切下一首后仍处于同一 Activity，收起显示新曲目的搜索词 |
| 主页两项回归 | 平板双栏布局、重复开合、换曲与按压形状、歌词切换保持 |
| 最终 instrumentation | **OK (8 tests)**，[日志](device.txt) |
| 普通 QA 构建 | `assembleDebug assembleDebugAndroidTest -PdeviceQa=true` exit 0，[日志](build-qa.log)；已安装普通 QA 0.1.5，不含 onlineQa 测试信任配置 |
| 正式最终构建 | `assembleDebug assembleRelease testDebugUnitTest lintDebug` exit 0，[日志](build-production.log)；arm64-v8a／x86_64 debug 与 release，版本 0.1.5／6；release 未用用户私钥签名 |
| JVM／lint | 39 项／0 失败／0 错误／0 跳过，[统计](unit-results.json)；lint 0 error／2 既有 warning，[报告](lint-production.txt) |
| 最终 QA 刷新 | 补局部 API 注解后普通 QA debug／androidTest exit 0，[日志](build-qa-final.log)；平板 install --no-incremental -r 成功并启动，不卸载／清数据 |
| 产物 | 四个正式 APK 与实际安装普通 QA 的版本、大小和 SHA256：[artifacts.json](artifacts.json) |

最终正式构建首次 lint exit 1：PlaybackPageHost 调用 Media3 后端缺少 UnstableApi opt-in，见 [初轮日志](build-production-initial.log)。补充与 MusicExpansionHost 一致的局部注解后重跑全部正式命令通过；无运行逻辑变化，八项设备测试不重复。LibraryMiniPlayer 函数全文与 dev HEAD 比对一致，git diff --check exit 0。

原创截图：[设置页展开中间帧](settings-opening.png)、[设置控制条](settings-collapsed.png)、[搜索收回后](search-collapsed.png)。画面中间帧结合真实几何断言，不以“已出现完整详情”代替动画验证。

```powershell
python -X utf8 scripts/Serve-OnlineFixtures.py --prepare
# 单独运行 HTTPS 脚本（仅 localhost），adb reverse tcp:18443 tcp:18443
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest -PdeviceQa=true -PonlineQa=true --console=plain
# 依据 output-metadata.json 选择 arm64 QA，应用及 androidTest 都 install -r
adb -s bdba9db1 shell am instrument -w -e class 'yy.daniel.yyplayerforandroid.OnlineUiTest,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#playbackDockPersistsAcrossSecondaryPagesAndReturnsToCaller,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#playbackDockStaysOutsidePageAnimationsAndSetupReturns,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#inlineCoverSurvivesInterruptedOpenRecreationAndLyricPageNext,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#tabletCoverKeepsControlsLeftAndLyricsFullHeight,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#coverKeepsItsShapeDuringNextAndClickOpensLyrics' -e onlineQa true -e u07Evidence true yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner
./gradlew.bat assembleDebug assembleDebugAndroidTest -PdeviceQa=true --console=plain
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain
```

测试后在线偏好／API 配置恢复，移除 reverse 18443、停止本轮本地 HTTPS 服务。没有关闭 TLS 检验或修改系统设置。

## 限制

平板横屏 debug 动画几何与返回通过，不等同 release 帧率证据。手机竖屏、API26–32、200% 字体、完整 TalkBack／深色／分屏未重测。搜索 ViewModel 只跨配置重建，不承诺进程完全回收后的完整结果恢复。未更换输出／native 后端，不重复硬件资格。正式 release 需用户原私钥签名；视觉满意度仍待用户复核。
