# U25：真正悬浮于内容上方／0.1.4（2026-10-15）

## 后续视觉恢复（当前交付）

用户要求所有页面恢复原主页播放栏视觉。已移除共享 LibraryMiniPlayer 新增的 6dp shadow 及无用 import；函数全文与 dev HEAD `31fece7` 中原主页实现一致（脚本提取比对断言通过），HyperBackdrop／Api33MiuixBlur 无 diff。原磨砂、透明度、描边、按压反馈完整保留，真实内容悬浮布局不变，版本仍为 0.1.4／5。下面“6dp 阴影”及原截图／artifacts.json 属于恢复前证据。

普通 QA `assembleDebug assembleDebugAndroidTest -PdeviceQa=true` exit 0（[日志](restore-build-qa.log)）；平板 install --no-incremental -r 更新成功。复用 `playbackDockPersistsAcrossSecondaryPagesAndReturnsToCaller` 和 `tabletCoverKeepsControlsLeftAndLyricsFullHeight`，**OK (2 tests)**（[日志](restore-device.txt)），覆盖七类二级页及主页封面；[恢复后设置截图](restore-settings.png)仅含自有素材。正式 `assembleDebug assembleRelease` exit 0（[日志](restore-build-production.log)）；[最新产物哈希](restore-artifacts.json)。原正式包与数据保留，release 未用用户私钥签名，未提交／推送。

本次仅删除附加阴影，没有新增测试或重复后端／JVM／完整 lint 专项；上一轮结果限定于对应产物。其他设备、深色／大字体与 release 帧率仍未重测，视觉满意度待用户确认。

用户指出 U24 底部行并非悬浮效果，本轮改为覆盖内容的独立播放栏，并将版本更新为 **0.1.4／versionCode 5**。所有修改在 dev，未提交／推送；保留 U22–U24 及用户原有文件。

## 实现差异

- PlaybackPageHost 从纵向占位改为 Box 叠放：内容和磨砂捕获层保持全尺寸，播放栏后绘制在其上。捕获真实列表，不捕获自身，也不使用独立的纯色底托。
- 播放栏加入 6dp 轻阴影。列表视口延伸到栏下方，LocalPlaybackBottomSpace 仅增加 contentPadding 末尾，让最后一项能滚到栏上方；设置／音频输出／方向／歌词样式／在线搜索同步接入。队列和引导固定操作按钮单独避让。
- 保留空队列隐藏、键盘上移、唯一后端、原页返回栈、主页共享封面，以及页内动画与播放栏分离。完整音乐／视频页面不重复叠加，跨 Activity 仍使用 ROM 转场。
- 更新 app/build.gradle.kts 和 release instrumentation 的版本断言；构建命名仍由 variant versionName＋ABI 生成。

## 真机验证

一加平板 Pro `bdba9db1`／OPD2404／API36／ColorOS16／`OPD2404_16.0.5.1201(CN01)`，横屏 3000×2120。独立 QA 共存包，均为 install --no-incremental -r；原正式 release 与数据保留。

自有 WAV、几何封面、双语歌词及 localhost HTTPS 32 条结果。未保存用户媒体画面。测试配置仅在显式 onlineQa debug 构建使用受控证书；结束后恢复普通 QA、偏好与 API 配置，移除 reverse 18443 并停止本轮服务。

| 项目 | 结果 |
| --- | --- |
| onlineQa debug／androidTest／JVM | exit 0，[日志](build-online.log) |
| OnlineUiTest 三项 | 真实 HTTPS 搜索／音质／开关／返回栈；搜索视口和真实行 bounds 与播放栏重叠，暂停／恢复不穿透到行（mediaGeneration 不变）；实际 swipeUp 后栏 bounds 不变，末条结果能滚到栏上方；真实 IME 上移和恢复 |
| 二级页面专项 | 设置、方向、歌词样式、音频输出、队列、在线搜索、联网歌词均有一份播放栏；内容区域覆盖栏底；队列固定按钮保持可操作；真实播放／暂停、详情往返保持调用者和播放代次，清空队列隐藏 |
| 页内动画与引导 | 主页分页和引导步骤动画逐帧检查栏位置不变；引导 → 封面 → 同一步骤返回 |
| 原音乐回归两项 | 平板双栏／多次开合／换曲／封面按压与歌词切换 |
| instrumentation 合计 | **OK (7 tests)**，[日志](device.txt) |
| 普通 QA 构建 | exit 0，[日志](build-qa.log)；平板安装 YYPlayer QA 0.1.4／5，无 onlineQa 测试网络配置 |
| 正式双 ABI debug／release、JVM、lint | `assembleDebug assembleRelease testDebugUnitTest lintDebug` exit 0，[日志](build-production.log)；[JVM](unit-results.json) 39 项全过，[lint](lint-production.txt) 0 error／2 既有 warning |

四个正式 APK metadata 均为 versionName 0.1.4／versionCode 5，[产物哈希](artifacts.json) 同时记录普通 QA。正式 release 未用用户私钥签名；未覆盖设备上原 0.1.1／2 正式包。git diff --check exit 0。

截图：[在线搜索悬浮于第 26／27 行上方](u25-online-search.png)、[设置](u25-SettingsActivity.png)、[队列操作避让](u25-MusicQueueActivity.png)。截图结合真实行与栏的 bounds 重叠断言，避免把单独底部占位误当悬浮。

```powershell
python -X utf8 scripts/Serve-OnlineFixtures.py --prepare
# 在单独终端启动仅绑定 localhost 的脚本，adb reverse tcp:18443 tcp:18443
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest -PdeviceQa=true -PonlineQa=true --console=plain
# 按 output-metadata.json filters.value 选择 arm64-v8a QA 包，安装应用及 androidTest，均 install -r
adb -s bdba9db1 shell am instrument -w -e class 'yy.daniel.yyplayerforandroid.OnlineUiTest,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#playbackDockPersistsAcrossSecondaryPagesAndReturnsToCaller,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#playbackDockStaysOutsidePageAnimationsAndSetupReturns,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#tabletCoverKeepsControlsLeftAndLyricsFullHeight,yy.daniel.yyplayerforandroid.MusicExperienceUiTest#coverKeepsItsShapeDuringNextAndClickOpensLyrics' -e onlineQa true -e u07Evidence true yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner
./gradlew.bat assembleDebug assembleDebugAndroidTest -PdeviceQa=true --console=plain
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain
```

## 边界

本轮为平板横屏 debug 验证，不代表 release 帧率资格；API26–32、手机竖屏、分屏、200% 字体、TalkBack、深色未重测。原视频／音频引擎和 native 库不变，未重复其硬件资格。正式 release 需用户原私钥签名；未自动提交／推送。视觉满意度仍待用户复核。
