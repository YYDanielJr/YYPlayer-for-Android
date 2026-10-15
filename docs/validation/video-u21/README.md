# U21 小米 13 Ultra 视频修复验证

日期：2026-10-14。分支 dev；累计 B01 与本轮 U21 未提交。用户原签名 release 及数据保留，测试安装 `yy.daniel.yyplayerforandroid.qa`（YYPlayer QA）。结束时恢复 QA debug，正式 applicationId 的 debug／unsigned release 输出仍保留在 app/build/outputs/apk。

## 实现与根因

1. libmpv loading failed：手机日志证明 `/proc/self/fd/N` 二次打开返回 Permission denied；改为 `fd://N` 借用已授权 FD。跨 UID 测试先断言旧路径无法读，再验证 mpv 读取、显示尺寸、播放时钟与跳转，mpv 关闭之后才关闭 FD。
2. 控制器：顶部返回／标题／工具，底部进度＋播放／时间／倍速／方向一行，其余收进工具；暂停也能单击隐藏，保留180ms淡入淡出和减少动态效果适配。横屏抽屉、字幕／音轨、8×、变换还原、锁定等继续可达。
3. 单一仲裁新增双击暂停恢复、横拖松手跳转与真实目标帧预览、左右侧竖拖窗口亮度／播放器音量；五个持久开关含长按。预览独立 FD／后台单队列／合并请求／缓存上限，不从视频纹理逐帧读取。系统亮度与音量未修改。
4. Media3 使用视频专用40ms目标PCM缓冲＋AudioTrack低延迟请求，保留Sonic 0.1–8×；平台实际速率固定1×。libmpv 用内建共享AAudio、30ms额外队列、常驻保音调滤镜，初始化失败可回退AudioTrack。实际驱动读回显示在媒体信息。音乐独占／native sink路径不改。决定与候选证据见 [ADR0009](../../adr/0009-video-rate-response.md)。

文件：VideoSurface、VideoPlayerPage、VideoGestures、VideoSeekPreview、AndroidMedia3PlaybackBackend、OutputPathRenderersFactory、MpvVideoBackend、PlaybackBackend、PlayerScreens、mpv_worker.cpp；新增真实Activity／MotionEvent测试、跨UID固定fixture provider，适配既有UI测试到工具菜单和小米前台启动规则。README／AGENTS／技术设计／实施计划／视频体验／编译签名文档同步。

## 设备与素材

- 先执行 adb devices -l：有线 `c9e352e3`，小米13 Ultra／2304FPN6DC／API36／OS3.0.308.0.WMACNXM，页大小4096。17 Pro Max未连接；MuMu未用于本轮证据。
- 320×240／24fps／12秒 H.264＋AAC，自制 v15-video.mp4，SHA256 `886201dde13b688fc5d8de83600d80af4aeb03d75df13b46b73f78ac259ccb99`；测试provider独立UID，只允许固定fixture只读。
- 额外1920×1080／30fps／12秒自制运动色块＋440Hz，SHA256 `62ae8c69471f21057b7cf2cf734cf3b79560cb1734bd502bd01111f4a1be066c`。生成：`ffmpeg -f lavfi -i testsrc2=size=1920x1080:rate=30 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 12 -c:v libx264 -preset fast -crf 26 -pix_fmt yuv420p -c:a aac -b:a 128k -movflags +faststart build/u21-1080p.mp4`。保存在忽略的build和QA独立目录，不使用个人视频。
- 界面证据仅自制素材：[拖动预览与精简控制器](seek-preview.png)、[暂停时隐藏控制器](hidden-controls.png)。截图不是用户满意度或帧率证明。

## 命令与结果

所有Gradle命令设置本进程 JAVA_HOME=E:/DeveloperTools/openjdk。adb每次明确 `-s c9e352e3`，安装均 `install --no-incremental -r`，没有卸载／清数据／更改ROM权限。

| 检查 | 命令要点 | 结果 |
| --- | --- | --- |
| QA构建 | gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PdeviceQa=true | exit0 |
| 真机28项 | am instrument -w -r -e class VideoPhoneDeviceTest,VideoSurfaceGestureTest,VideoPlayerUiTest,VideoUpgradeDeviceTest,MusicUpgradeDeviceTest yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner（class带完整包名前缀） | exit0，OK(28)，[日志](phone-tests.txt) |
| 1080p升降速 | 相同runner，-e fixture 1080p -e class yy.daniel.yyplayerforandroid.VideoPhoneDeviceTest#pressAndReleaseRestoreBothBackendClocks | exit0，OK(1)，[日志](1080p-test.txt) |
| 实际R8 release | assembleRelease :app:assembleDebugAndroidTest -PdeviceQa=true -PreleasePackagingQa=true；QA副本用本机debug证书签名，install-r；am instrument -w yy.daniel.yyplayerforandroid.qa.test/yy.daniel.yyplayerforandroid.ReleasePackagingInstrumentation | exit0，启动／音频JNI／mpv FD解码与AAudio回读3项通过，[日志](release-test.txt) |
| 正式包与静态检查 | gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug | exit0，[构建日志](build.txt)；37项JVM无失败／跳过，lint0error／2既有warning |
| 原生审计 | python scripts/Verify-MpvArtifact.py APK report.json，正式两ABI×两variant | 全部exit0，每APK12个so；ELF16KB与未压缩ZIP16KB检查通过；release压缩so按解压安装 |
| 文件检查 | git diff --check | exit0 |

实际四个正式包与两个QA包的字节数、SHA256、身份见 [artifacts.json](artifacts.json)。正式release未使用用户私钥签名；QA release签名只为验证，不作为正式发布签名。手机最终留有已测QA debug与原用户release。28项包括：新增6、双指／取消4、播放器／抽屉8、视频后端5、音乐5。release的3项原生检查不替代全部debug手势套件的release重跑。

## 响应测量口径

真实MotionEvent长按，按下到3×含系统约300ms识别阈值；松手ACK指后端回读倍速恢复，并不是声学输出延迟。另检查实际媒体时钟在400ms窗口内的推进，防止仅改标签。

| 1080p／30fps | 按下到3×回读（含识别） | 松手到1×回读 | 3×下400ms时钟推进 | 松手恢复后400ms时钟推进 |
| --- | --- | --- | --- | --- |
| Media3 | 370ms | 32ms | 1030ms | 601ms |
| libmpv | 454ms | 79ms | 1265ms | 451ms |

见 [1080p原始测量](1080p-clock.txt)。320素材复验Media3松手24ms、mpv82ms；400ms的恢复时钟分别630ms／412ms，[日志](clock.txt)。这表明原先0.5–1秒拖延明显缩短，但仍有音频缓冲与时钟平滑，不能声称零延迟。mpv另有80ms间隔原生time-pos与current-ao断言，升降速无原先约300ms停顿／1秒追赶。

## 排查中的失败与修正

- libmpv起初audiotrack输出时变速属性已为3，但实际视频时钟停约300ms；只缩软件队列／固定tempo滤镜不足。改AAudio后实际时钟通过，未禁用同步／音调处理。
- Media3平台变速候选对常用3×快，但设备拒绝7.4×；退回Sonic完整范围。仅40ms缓冲、未请求低延迟的候选仍在恢复400ms内前进965ms，未通过断言；最终短缓冲＋低延迟模式通过。
- 小米奇怪的Compose宿主挂起来自后台Activity启动拒绝；用QA专用RuleChain在Compose Activity规则之前通过正常shell启动前台，未放宽系统安全策略。真实业务Activity测试也使用此顺序。
- 单击隐藏的截图已隐藏，但UiAutomation旧缓存仍保留按钮；刷新无障碍缓存后通过。菜单增加与小米短高度下，旧测试须真实滚动到字幕／快捷倍速项，未删除功能断言。
- 曾有一次自动审批超时，检查未落盘后重试成功，不存在未完成的审批阻塞。

## 限制与下一动作

13 Ultra上的实现与上述专项完成。17 Pro Max／API26–32／蓝牙与USB路由／原生16KB页设备未验；4K/HDR重负载、音频欠载长期统计、声学／数字采集、release帧率和完整TalkBack仍需专项，不能从当前短片外推。AAudio不可用回退AudioTrack时响应可能较慢；Media3低延迟请求是否生效也依设备而定。用户本轮没有附上所述最新截图，控制器按照减少遮挡优化；视觉与手感需用户在手机最终复核。下一步可直接打开YYPlayer QA试播，确认后用原签名重新编译正式release覆盖安装。
