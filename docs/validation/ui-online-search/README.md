# U09 在线音乐搜索 UI 验收

2026-10-08，dev-hifi；本轮与此前 H02–H05 均未提交。小米17 Pro Max / 2509FPN0BC，SDK37、OS4.0.0.44.XPBCNXM，adb `192.168.1.246:33103`。

本轮实现：本地搜索历史保留开关／单条删除／全部清除／点击重新搜索；内容区居中 InfiniteProgressIndicator；真实专辑封面；整条点击直接播放；单个“…”含播放音质和下载。来源、文字样式、共享引擎与独立联网开关保留。

| 验收 | 证据与结果 |
| --- | --- |
| 普通完整构建／单测／lint | [checks-final](checks-final.txt)，四任务 exit0 / BUILD SUCCESSFUL（59s）；JVM30项0失败，含2项MRU／关闭历史契约；lint0 error／1 warning，仍为系统媒体控制器需绑定的 ExportedService |
| 真机专项 | [device-tests-final](device-tests-final.txt)：OK(4 tests)，0.089s；实际SharedPreferences重建后的历史／关闭／删除／清除／版本恢复，默认适用音质与独立联网 gate |
| 首次测试签名失败 | [device-tests](device-tests.txt)：2项历史过，新增runBlocking用例推断非void导致JUnit初始化错误；改显式Unit后通过，保留失败不计通过 |
| 搜索加载 | [loading-from-history](loading-from-history.png)：真实GD搜索请求，键盘关闭状态下内容区居中无限环形指示器；[loading](loading.png)为首次搜索隐藏键盘中的帧 |
| 真实搜索结果 | [results](results.png)：GD音乐台／网易云，关键词123；可见7个真实专辑封面与7个独立“…”菜单，歌名17sp与歌手／专辑13sp不变 |
| 历史与清除 | [history](history.png)：应用进程重启后仍显示123，点击能重新搜索；[history-cleared](history-cleared.png)：关闭保留并清除后无记录；验收后恢复默认保留开关 |
| 点击实际播放 | [direct-play](direct-play.txt)：普通生产UI点整条，不弹音质选择，SYSTEM下真实MediaSession PLAYING、position=24604、speed=1.0、error=null；[详情截图](direct-play.png)为随后暂停状态 |

默认播放规则是配置顺序中首个适用档位，本机该源为128。点击“…”独立打开“选择播放音质／下载歌曲”，不误触整条播放。播放不再等待图片下载；图片在唯一 runtime 中异步补齐，旧媒体结果不能改新媒体。没有下载歌曲文件或修改原API配置／联网开关，安装均使用install -r。

重要现有限制：本机原 NATIVE_EXACT 路径对这首带 encoder delay/padding 的流媒体返回 `gapless_trimming_requires_system_output`（H05已有明确限制）。本轮以SYSTEM验证正面播放，暂停后恢复原NATIVE_EXACT／跟随系统选择；没有自动改变输出路径或绕过严格策略。此限制不是搜索条目事件未触发，未扩大成本轮原生播放能力。

自动审批拒绝了拟备份／替换设备providers配置、启动本地服务器及adb reverse的一组QA操作，理由是配置可能含凭据及额外副作用；该命令没有执行。改用隔离Context的测试和设备已有GD配置的普通UI验收。只生成过ignored app/build下的自有QA资源，未装QA包、未读取／导出／替换用户配置、未启动服务器、adb reverse列表为空。普通APK online_qa资源数量0。

最终APK SHA256：`aafd67b65502864398a1da2a699d973a0db14f8f5fca14e38034703f290115cf`，已install -r；测试签名修正仅更新测试APK，主APK不变。`git diff --check` exit0。普通MainActivity／OnlineSearchActivity已启动，停留于新搜索结果页。

未执行：Compose Activity instrumentation（该小米既有挂起问题）、200%字体／TalkBack、API26–32、暗色专项、所有provider／来源／音质的供给检查、SAF/MediaStore新下载（本轮没有改下载链路）。当前真机结果不扩大到这些条件。

复验命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
$adb='E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe'
$serial='192.168.1.246:33103'
& $adb -s $serial install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s $serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s $serial shell am instrument -w -e class 'yy.daniel.yyplayerforandroid.OnlineSearchHistoryDeviceTest,yy.daniel.yyplayerforandroid.OnlineApiTest#directPlaybackUsesFirstApplicableConfiguredQuality,yy.daniel.yyplayerforandroid.OnlineApiTest#independentGatesRejectBeforeAnyNetwork' yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
```
