# U27：buglist 四项修复／0.1.6

2026-10-15，dev 基线 `9392e2b`（用户新版 README）。版本 0.1.6／7，未提交或推送。用户的 `buglist.txt` 和 `app/release/` 原签名产物保留。

## 实现与根因

1. 快速展开后横滑：原详情在进度 0.96 即开放输入，而共享封面仍在冻结终点完成回弹；页面先滑走就留下悬空封面。现在共享层交接完成才开放输入，保留原展开／收回弹簧和终点同层绘制。
2. 超级岛：仅构造 MediaSession 不等于已注册到 MediaSessionService。应用内直接操作引擎时，没有 MediaController 绑定触发 onGetSession，实测会话为 PLAYING 而服务 `startForegroundCount=0`，没有媒体通知。现在在 onCreate 显式 addSession，让 Media3 管理标准 MediaStyle 通知与前台生命周期。没有第二个播放器、伪进度或私有系统接口。
3. 队列 Activity 使用 `AppTheme(playbackDock = false)`，向导直接绘制；其他二级页仍默认获得完整 PlaybackPageHost／MusicExpansionHost。新页面可直接使用 AppTheme，特殊页面显式排除。
4. 分页删除倾角，只做水平跟随。封面、标题、副标题依次运动；歌词按行跟随，拖动以距手指最近的行先行，点按以顶部可见行先行。9° 角落按压保留，分页期间角度归零。

封面读取仓库从 UI 文件提取到 `playback/MediaArtworkRepository.kt`，UI 与 service 共用已有有界缓存。服务在后台读取并压缩最长边 384px 的封面，把字节写入真实 MediaItem 元数据，避免 SystemUI 读取私有路径；按媒体／generation／Player／封面 revision 取消旧请求。在线封面完成后也能更新；缺图不停止播放。通知点击携带 `open_music` 回到详情。

公开依据：[小米开发指南第六节（媒体通知自动上岛）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)、[HyperOS 媒体通知规范](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2161)、[Media3 1.11.1 服务注册实现](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionService.java)。本轮按标准媒体路径修复，没有提交普通焦点通知申请。

## 设备与素材

先 `adb devices -l` 枚举：小米 13 Ultra `c9e352e3`，2304FPN6DC／API36／HyperOS `OS3.0.308.0.WMACNXM`，1440×3200。共存 YYPlayer QA，全部使用 `install --no-incremental -r`，无卸载／清数据／全局设置修改。测试使用缓存内自制 30 秒 WAV、双色圆形封面、四组原创双语 LRC；没有读取用户音乐库作为 fixture。

## 验证

- 8 项不同真机测试通过：六类二级页同页开合及返回；主页分页／向导无栏；中断／重建／减少动态效果／联网歌词页切歌；换曲封面与按压；环形进度与真实播放／切歌；早期 160／320／480ms 横滑后正常翻页／收回；逐行水平跟随与封面无倾斜；系统媒体通知与后台控制。
- [回归日志](device-regression.txt)：5 项中 4 项通过。旧环形控制测试仍查找已在 U13 删除的底栏“下一首”，修正为先展开详情；[最终专项日志](device-final.txt) **OK (4 tests)**，包含修正后的环形控制和三项 U27 专项。因此不同用例合计 8 项全部通过。
- 系统通知测试不先绑定 Media3 Controller，直接断言 NotificationManager 中出现带 session token、封面、MediaStyle 与 foreground 标记的通知。通过平台 MediaController 验证后台进度、连续三轮暂停／继续、切歌与元数据更新。通知目标 Intent 在测试调用者回到前台后发送，避免 Android 后台启动限制；不把它描述成自动点击了系统岛内的按钮。
- [超级岛实机顶部](u27-system-island.png)：后台真实播放时出现原创封面和波形。只保留必要状态栏区域。[点击跟随中间帧](u27-click-stagger.png)、[手势跟随中间帧](u27-drag-stagger.png)及[快速横滑后的歌词页](u27-lyrics-after-early-320.png)均为原创素材。位移断言验证点按时顶部先行、底部手势时近手指行先行，封面宽高差不超过 1dp。
- JVM：[39 项全部通过](unit-results.json)。正式双 ABI debug／release 构建与 lint exit 0，lint **0 error／2 既有 warning**，见 [最终构建日志](build-production-final.log)与 [lint](lint.txt)；最终 QA 构建 exit 0，见 [日志](build-qa-tests.log)。
- 4 个正式 APK 的 `Verify-MpvArtifact.py` 均 exit 0，各 12 个 so 的 ELF／未压缩 ZIP 16KB 对齐。版本、大小、SHA256 见 [artifacts.json](artifacts.json)，逐库审计见本目录 native-*.json。手机 package 回读 versionName=0.1.6／versionCode=7，最后普通 QA 冷启动 Status ok；git diff --check exit 0。

主要命令：

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest -PdeviceQa=true --console=plain
# 按 output-metadata.json 中 arm64-v8a 选择 APK，应用和测试 APK 均 install -r
adb -s c9e352e3 shell am instrument -w -e class '<MusicExperienceUiTest 指定方法>' -e u07Evidence true yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain
```

初轮环境中 ADB 启动和 Gradle 缓存写入受沙箱限制，使用授权工具路径执行后恢复。测试首次编译的缺失 UiAutomator 依赖、平台 metadata 可空类型、DpRect 尺寸读取已分别修正，保留对应构建日志。媒体专项初轮无通知超时复现根因；注册后通知与控制通过，测试直接从后台发送 PendingIntent 的返回断言受平台限制，改为前台验证目标 Intent 后最终全过。

## 范围

本机 HyperOS 3 的媒体通知／上岛已实测；不保证其他 ROM、被用户关闭的通知渠道或长期被系统回收后的自动恢复。未重测 API26–32、平板／横屏、200% 字体／完整 TalkBack／深色、release 帧率、DAC 独占或端到端位准确。没有更换 native 后端。正式 release 未用用户私钥签名；视觉手感仍由用户最终验收。
