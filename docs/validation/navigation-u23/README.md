# U23：在线流播放返回搜索栈（2026-10-15）

版本保持 0.1.2／versionCode 3，基于同工作区 U22，修改在 dev，未提交／推送。原有未提交／未跟踪文件均保留。

## 行为与实现

旧路径在解析流地址后调用 `openPlayer(); closePage()`；非主页的 `openPlayer` 带 CLEAR_TOP／SINGLE_TOP 回到已有主页，搜索页被清理并主动结束，收起封面只能回库。

新路径通过 `openMusicOnBackStack` 压入一个 MainActivity 音乐入口，保留原搜索 Activity。入口的 `music_on_back_stack` Intent 标记决定收起／系统返回执行 finish；普通主页入口仍折叠音乐覆盖层。跨 Activity 入口首帧直接显示完整封面，并使用 ROM 转场，避免先显示或收回到重复主页。退出不停止、不重建、不重新加载播放；共享 Application runtime 的唯一后端。页面重建后 Intent 标记仍有效，队列等子页面继续逐层返回。

改动文件：MainActivity、PlayerHostActivity、OnlineSearchActivity、YYPlayerApp；OnlineSearchScreen 仅添加列表测试标记；OnlineUiTest 和 Serve-OnlineFixtures.py 增加受控流播放返回验证。文档更新 README、AGENTS、STATUS、音乐体验、在线 API 与 ADR0004。

## 真机验证

- 一加平板 Pro：`bdba9db1`／OPD2404／API36／ColorOS16，ROM `OPD2404_16.0.5.1201(CN01)`。
- 使用 `.qa` 共存包，保留用户正式 release 和数据；所有安装均 install -r，exit 0。
- 自制 HTTPS 服务只监听本机，通过 adb reverse 18443 接入设备；32 条原创搜索结果、自制 12 秒 WAV 与几何封面。测试构建仅为 localhost／127.0.0.1 添加受控证书，不更改系统信任、不关闭 TLS 校验。
- 测试结束恢复在线开关及 API 配置，移除 adb reverse，停止本轮服务器，重新安装**不含测试证书／网络配置的普通 QA debug**。版本仍为 0.1.2／3。

| 检查 | 结果 |
| --- | --- |
| `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PdeviceQa=true -PonlineQa=true` | exit 0，[初始构建](build-online.log) |
| `OnlineUiTest -e onlineQa true` | `OK (3 tests)`，[输出](navigation.txt)：联网开关入口、真实 HTTPS 下载／播放音质选择、流播放返回栈 |
| 新返回栈用例 | 同一搜索 Activity、查询“栈返回”、第 24 条结果及其屏幕 bounds 保持；3 次进入／返回；收起按钮／系统返回；第 3 次封面 Activity 重建后，队列 → 封面 → 搜索；播放代次不变并继续播放；最终搜索退出回原主页，从主页打开在线歌曲仍收回主页 |
| `MusicExperienceUiTest` 两项 | `OK (2 tests)`，[输出](home-regression.txt)：平板双栏／重复开合及原有换曲／按压／歌词切换 |
| 普通 QA 最终构建 | `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PdeviceQa=true` exit 0，[输出](build-qa.log)；39 项 JVM 全过，QA lint 0 error／3 warning（包括标签替换后的 app_name 未使用提示） |
| 正式产物 | `assembleDebug assembleRelease lintDebug` exit 0，[构建](build-production.log)；[lint](lint-production.txt) 0 error／2 warning。双 ABI／双构建类型共 4 包，每包 12 个 so 的 ELF／ZIP 16KB 审计通过（native-*.json） |

最终正式包与普通 QA 的版本、大小、SHA256 见 [artifacts.json](artifacts.json)，[JVM 统计](unit-results.json)为 39／0 failure。正式 release 未使用用户私钥签名；本次未重复完整 release UI suite。

测试不是伪造播放快照：实际 HTTPS WAV 请求成功，真实后端 `isPlaying` 为真，返回前后 mediaGeneration 一致，封面与搜索页使用同一个 runtime 后端实例。搜索 Activity 对象及第 24 条结果 bounds 同一性断言避免“重建空搜索页也算返回成功”。

## 重跑

```powershell
python -X utf8 scripts/Serve-OnlineFixtures.py --prepare
# 另一个终端启动，仅监听 127.0.0.1
python -X utf8 scripts/Serve-OnlineFixtures.py
./gradlew.bat assembleDebug assembleDebugAndroidTest -PdeviceQa=true -PonlineQa=true
adb -s bdba9db1 reverse tcp:18443 tcp:18443
# 按 output-metadata.json 选择 QA arm64 APK，与普通 androidTest APK 一并 install -r
adb -s bdba9db1 shell am instrument -w -e class yy.daniel.yyplayerforandroid.OnlineUiTest -e onlineQa true yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner
```

完成后移除本次 reverse、停止服务器并构建安装不带 `onlineQa` 的 QA debug。临时证书、私钥、素材均在忽略的 app/build/online-qa，不能提交。原 release 无需卸载。

## 边界

验证覆盖正常返回及封面 Activity 重建；搜索进程被系统完全回收后的完整结果恢复、其他设备／API26–32 和预测性返回动画帧率未做专项。当前返回栈不依赖歌曲是否为在线类型；只有从搜索压入的音乐入口弹回调用者。没有承诺跨进程回收持久化全部搜索结果。下一步为用户在现有服务配置下复测；本轮没有提交授权。
