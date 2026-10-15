# 进度与交接记录

## 2026-10-15：0.1.5 用户验收与提交

- 用户明确“我已验收，请提交”，本次在 dev 提交 U22–U26 累计实现、文档、验证记录及现有构建依赖的未跟踪 U21 源码／测试／native keep 规则；原 app/release APK、mapping 和 symbols 保留本地，不提交。下方未提交／待用户确认等文字保留为当轮历史。
- 验证沿用 U26 已通过的 8 项平板专项、39 项 JVM、双 ABI debug／release 构建和 lint（0 error／2 既有 warning），本次仅补验收交接文字，不重复构建或设备操作。提交前 git diff --check 与暂存检查通过；验证记录敏感配置模式扫描无匹配（rg exit 1 表示未发现匹配）。
- 用户已验收当前平板体验；API26–32、其他设备、release 帧率等未验范围不因此扩大。提交身份以 git log 为准；未推送、未合入 master，下一步按用户后续指令处理。

## 2026-10-15：U26 所有播放栏页面共享封面动画／0.1.5

**实现与一加平板专项完成，版本 0.1.5／versionCode 6，未提交。** dev 基线仍为 `31fece7`；保留 U22–U25 和用户未跟踪文件。

- 任务／实现：将主页共享封面绘制层、展开 180／0.86 与收回 230／0.8 弹簧、输入门禁、快速反向处理提取到 MusicExpansionHost。主页、搜索／联网歌词、设置／方向／歌词样式／输出、队列和引导页均在当前 Activity 内展开，收回回到本页播放栏，保留页面状态与原主页磨砂视觉。真正子 Activity 仍用 ROM 转场；旧 PlayerActivity 音频跳转兼容入口保留。
- 配套：展开时隐藏键盘；展开状态跨配置重建恢复，搜索结果用 ViewModel 保留，不写大 Bundle；联网歌词详情内切歌刷新当前查询且不关闭宿主。修复逐帧验证发现的目标锚点被迟到 effect 清空问题，保证中间帧真实移动／缩放。没有更改视频或媒体输出路径。
- 文件：MusicExpansionHost、PlaybackPageHost、YYPlayerApp、PlayerHostActivity、MainActivity、OnlineSearchActivity、OnlineSearchScreen、OnlineSearchPageState；版本／release 断言；OnlineUiTest、MusicExperienceUiTest；README、AGENTS、ADR0004、音乐体验、在线 API、技术设计、实施计划、构建说明与本记录。
- 设备／素材：每次枚举确认一加平板 Pro bdba9db1／OPD2404／API36／ColorOS16／OPD2404_16.0.5.1201(CN01)，横屏 3000×2120。使用自有 WAV／几何封面／LRC 和受控 localhost HTTPS 结果，未保存个人媒体画面。
- 命令／结果：onlineQa debug／androidTest／JVM exit 0；直接 instrumentation **OK (8 tests)**，包含七类二级页中间帧几何、展开／收回、三次途中返回、键盘、配置重建、子页返回、联网歌词切歌及主页回归。正式 `assembleDebug assembleRelease testDebugUnitTest lintDebug` 首轮 exit 1（新宿主缺 Media3 opt-in），补局部注解后重跑 exit 0，**39 JVM 全过，lint 0 error／2 既有 warning**。最终普通 QA debug／androidTest exit 0；注解不改变运行逻辑，未重复设备 suite。git diff --check exit 0。
- 证据／交付：[U26 验证](validation/music-u26/README.md) 含日志、原创中间帧与收回截图、四个正式 ABI／类型 APK 和普通 QA 的 SHA256。已 install --no-incremental -r 更新并启动普通 YYPlayer QA 0.1.5，无 onlineQa 信任配置；原 release 0.1.1／2 与数据保留。测试偏好／API 已恢复，reverse 和本轮 HTTPS 服务已清理。正式 release 未用用户私钥签名；未提交／推送／合入。
- 限制／下一步：手机竖屏、API26–32、200% 字体、完整 TalkBack／深色／分屏、release 帧率未重测；配置重建恢复不代表进程回收后完整搜索结果恢复。用户可在当前平板 QA 复核各页开合与原材质，视觉手感仍待用户确认。

## 2026-10-15：U25 后续——全部播放栏恢复原主页视觉

- 任务／实现：按用户要求删除 HyperDesign.LibraryMiniPlayer 新增的 6dp 阴影和 import，所有页面共用原主页磨砂／透明度／边框／按压反馈。脚本提取函数全文与 dev HEAD `31fece7` 比对一致，磨砂底层文件无 diff；保留 U25 真实内容悬浮、键盘避让和返回栈，版本保持 0.1.4／5。
- 文件：HyperDesign.kt、README、AGENTS、音乐体验、本 STATUS 和 U25 验证补充。旧 U25 阴影描述／截图保留为历史，新增恢复后记录明确覆盖。
- 命令／结果：普通 QA `assembleDebug assembleDebugAndroidTest -PdeviceQa=true` exit 0；正式 `assembleDebug assembleRelease` exit 0；平板直接 instrumentation 两项 `MusicExperienceUiTest` 回归通过（七类二级页真实播放／返回、空队列、主页平板封面多次开合）；git diff --check exit 0。仅删除视觉修饰，没有新增测试，未重复 JVM／完整 lint／后端专项。
- 设备／素材／交付：枚举一加平板 Pro bdba9db1／API36／ColorOS16，使用原有自制 WAV／几何封面／LRC；QA install --no-incremental -r 成功，原 release 与数据保留。四个正式 APK 已刷新，release 未用用户私钥签名。[U25 最新恢复记录](validation/playback-u25/README.md)含日志、截图与 restore-artifacts.json；未提交／推送。
- 限制／下一步：其他设备、深色、大字体、release 帧率未重测；用户可在当前 QA 0.1.4 复核原主页视觉恢复效果。

## 2026-10-15：U25 真悬浮播放栏／0.1.4

**实现与一加平板专项完成，版本 0.1.4／versionCode 5，未提交。** dev 基线仍为 `31fece7`，保留 U22–U24 和原有未跟踪文件。

- 任务／实现：修正用户指出的 U24 底部占位布局。PlaybackPageHost 使用全尺寸真实内容捕获层＋上层播放栏，列表在磨砂下方滚动，加入 6dp 轻阴影；不压缩内容视口。实际栏高仅通过 LocalPlaybackBottomSpace 增加列表尾部 contentPadding，队列／引导固定操作按钮单独避让。保留键盘上移、原页返回栈、唯一后端、主页共享封面和页内动画分离。
- 文件：PlaybackPageHost、HyperDesign、OnlineSearchScreen、SettingsScreen、AudioOutputScreen、LyricStyleScreen、OrientationPage、MusicQueueScreen、SetupScreen；app/build.gradle.kts 及 ReleasePackagingInstrumentation 版本；OnlineUiTest、MusicExperienceUiTest；README、AGENTS、构建说明、音乐体验、技术设计、实施计划、ADR0004、本记录和 U25 验证。
- 设备／素材：枚举一加平板 Pro bdba9db1／OPD2404／API36／ColorOS16／OPD2404_16.0.5.1201(CN01)，横屏 3000×2120。自有 WAV／几何封面／LRC／localhost HTTPS 32 条结果，不保存个人媒体画面；QA 共存 install --no-incremental -r，原正式 release 0.1.1／2 和数据保留。
- 命令／结果：onlineQa debug／androidTest／JVM、普通 QA debug／androidTest，以及正式 `assembleDebug assembleRelease testDebugUnitTest lintDebug` 均 exit 0。39 JVM 全通过；lint 0 error／2 既有 warning。直接 instrumentation **7 项通过**，覆盖真实结果行与栏重叠、实际 swipeUp 栏位置不动、最后一项滚出遮挡、点击不穿透、真实 IME、七类二级页和引导返回、原封面两项回归。git diff --check exit 0。
- 证据／交付：[U25](validation/playback-u25/README.md) 含命令、结果、原创截图和 APK hash。四个正式 ABI／类型 APK metadata 均为 0.1.4／5；release 未用用户私钥签名。平板已安装普通 YYPlayer QA 0.1.4，无 onlineQa 信任配置；测试偏好／API 恢复，reverse 和本轮 HTTPS 服务已清理。文档和 AGENTS 同步，无提交／推送／合并。
- 限制／下一步：API26–32、手机竖屏、分屏、200% 字体、TalkBack、深色和 release 帧率未重测；未改后端／native 库，不重复其硬件资格。跨 Activity 仍随 ROM 窗口过渡。用户可在自己的歌曲／在线服务上复核悬浮视觉与手感，不声称用户已满意。

## 2026-10-15：U24 跨页面共享播放栏

**实现与一加平板专项完成，保持 0.1.2／3，未提交。** dev 仍为用户提交的 `31fece7`；继续保留 U22／U23 与原有未跟踪文件。

- 任务／子步骤：将播放栏提升到二级 Activity 的 AppTheme 宿主，覆盖在线搜索／联网歌词、设置／方向／歌词样式／输出、队列和重新设置；实际高度为内容留位，IME／导航边距统一消费，键盘弹出上移。页内滚动／分页／步骤动画不带动播放栏，完整音乐／视频使用自身控制器。非主页音频入口统一压栈，收起回同一调用者。磨砂背景覆盖完整窗口，修正初轮短捕获层导致灰块。
- 文件：新增 PlaybackPageHost；PlayerHostActivity、YYPlayerApp；OnlineUiTest、MusicExperienceUiTest；README、AGENTS、技术设计、实施计划、音乐体验、ADR0004、本 STATUS、验证目录。
- 设备／素材：先枚举有线一加平板 Pro bdba9db1／OPD2404／API36／ColorOS16／OPD2404_16.0.5.1201(CN01)。自有 WAV／几何封面／LRC，受控 localhost HTTPS 搜索流。共存 QA 全部 install --no-incremental -r，原 0.1.1 release 与数据保留。
- 命令／结果：onlineQa `assembleDebug assembleDebugAndroidTest testDebugUnitTest` exit 0；普通 QA debug／androidTest exit 0；正式 `assembleDebug assembleRelease testDebugUnitTest lintDebug` exit 0，39 JVM 全过，lint 0 error／2 既有 warning。直接 instrumentation **7 项通过**：搜索真实暂停／恢复／返回与键盘、7 类二级页面的同一后端／相同底栏位置／逐页返回、清队列隐藏、页内动画逐帧稳定、重新设置往返、两项原封面回归。git diff --check exit 0。
- 证据／交付：[U24 验证](validation/playback-u24/README.md) 含命令、初轮安装选择纠正、日志、原创截图和 APK 哈希；平板已恢复不含 onlineQa 信任配置的普通 YYPlayer QA 0.1.2，测试偏好／API 恢复，reverse 和本地测试服务已清理。正式双 ABI debug／release 已刷新，release 未用用户私钥签名；未提交／推送／合并。
- 边界／下一步：跨 Activity 仍随 ROM 窗口转场运动，用户已接受；没有申请全局悬浮窗。测试时钟 bounds 不证明 release 帧率。本轮未重测 API26–32、手机竖屏、大字体／TalkBack／深色／分屏及视频 native 资格；未改后端或输出。下一步用户复核自己的音乐与在线服务，视觉手感仍待用户确认。

## 2026-10-15：U23 在线流播放返回搜索栈

**实现与一加平板专项完成，版本保持 0.1.2／3，未提交。** 基于同工作区 U22，dev 仍为用户提交的 `31fece7`；保留原有修改与未跟踪文件。

- 任务／实现：去除搜索流播放后的清栈／关闭搜索页路径，压入带 `music_on_back_stack` 标记的 MainActivity 音乐入口。收起按钮与系统返回弹出该入口，恢复同一个搜索页及查询／结果／滚动位置，流播放继续。队列子页逐级返回，封面 Activity 重建后仍按原栈弹出。主页入口保留 U14／U22 覆盖层和共享封面回弹，不按 isOnline 误判返回位置。
- 文件：MainActivity、PlayerHostActivity、OnlineSearchActivity、YYPlayerApp、OnlineSearchScreen 测试标记；OnlineUiTest、Serve-OnlineFixtures.py；README、AGENTS、ADR0004、音乐体验、在线 API、本 STATUS 和验证记录。
- 环境／素材：一加平板 Pro bdba9db1／OPD2404／API36／OPD2404_16.0.5.1201(CN01)。受控 localhost HTTPS、32 条自制结果、自有 WAV 与几何封面。测试使用共存 QA，原正式包与数据保留。只在显式 onlineQa debug 构建信任本地测试证书，未改系统信任或关闭 TLS 校验。
- 验证／命令：QA `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`（onlineQa 及普通两种）均 exit 0；39 项 JVM 全过。真实 `OnlineUiTest` 3 项通过，覆盖直接播放／音质选择、3 次进出、系统返回、同一搜索 Activity 与结果 bounds、队列往返、重建、回主页后正常收起；`MusicExperienceUiTest` 两项通过。正式 `assembleDebug assembleRelease lintDebug` exit 0，lint 0 error／2 warning；四个 APK 的 ABI／ELF／ZIP 16KB 审计通过。git diff --check exit 0。
- 交付／清理：已 install -r 更新普通 YYPlayer QA 0.1.2（不含 onlineQa 网络配置），测试在线开关与 API 配置恢复，移除 adb reverse 18443，停止本轮本地 HTTPS 服务。正式双 ABI debug／release 产物已刷新，release 未用用户私钥签名；没有自动提交、推送或合入。
- 证据／边界：[U23 验证](validation/navigation-u23/README.md) 含命令、5 项真机测试、39 项 JVM、构建与 APK 哈希。未验证搜索进程完全被回收后的完整结果恢复、其他设备／API26–32、预测性返回帧率；U23 release 做构建与打包审计，未重复完整 release UI suite。下一步用户可在自身服务配置下复测。

## 2026-10-15：U22 一加平板横屏修复／0.1.2

**本轮实现与设备专项完成，未提交。** 用户已将 0.1.1 提交至 dev `31fece7` 并合入 master `18fcb28`；接手时两分支内容相同，先切回 dev 再修改。接手时仍有未跟踪的 U21 源码／测试／文档和 app/release 用户产物，保留不清理；本轮没有提交／推送／合入授权。下面 U21／B01 的“未提交／0.1.0”只描述历史当轮状态。

- **音乐**：横屏左列按封面、歌名、歌手·专辑、进度与控制栏排列，右列完整用于歌词。去掉封面模式重复的 44dp 小封面；完整歌词页仍保留紧凑身份行。沿用 U14 展开／收回弹簧与共享封面层，淡出布局不覆盖当前锚点。
- **视频**：用户指定的录屏为 HEVC／1440×2038／90°。libmpv 已转正画面，旧代码仍按竖屏 dw/dh 布局。改为读取输出参数并按 rotate 换算显示宽高，不重复旋转／像素比例校正。自制相同旋转片修复前圆为 258×817，修复后为 650×650。
- **界面与版本**：侧栏“设置”前添加 Miuix Settings 图标；版本 0.1.2／versionCode 3。四个正式 ABI／构建类型 APK 按既有命名生成，正式 release 未签名；原用户包仍为 0.1.1／2，平板最终留有共存 YYPlayer QA 0.1.2／3 debug。
- **文件**：MusicPlayerScreen、HyperDesign、MpvVideoBackend／MpvVideoGeometry、mpv_worker、app/build.gradle.kts；MusicExperienceUiTest、VideoPhoneDeviceTest、MpvVideoGeometryTest、ReleasePackagingInstrumentation；Test-TabletAspect.py；README、AGENTS、音乐／视频体验、技术设计／实施计划、编译说明与本记录。
- **环境／素材**：一加平板 Pro bdba9db1／OPD2404／API36／OPD2404_16.0.5.1201(CN01)，横屏 3000×2120、density420。原创 WAV／几何封面／双语 LRC；H.264 0°／90°／270°及同规格 HEVC 旋转素材。用户视频仅流式读元数据，不保存个人画面；无全局系统设置改动、卸载或清数据。
- **命令与结果**：`assembleDebug assembleRelease testDebugUnitTest lintDebug` exit 0；39 项 JVM 全通过，正式 lint 0 error／2 warning。QA debug／androidTest、QA R8 release 构建均 exit 0。`Test-TabletAspect.py` exit 0，4 次 instrumentation／12 组实际画面全部通过；音乐双栏与原封面换曲／按压回归各 1 项通过。release 专用 instrumentation 3 项 PASS；安装全部 install -r／exit 0。四个正式 APK 各 12 个 so 的 ELF／ZIP 16KB 审计通过。`git diff --check` exit 0。
- **证据**：[U22 验证目录](validation/tablet-u22/README.md) 含根因、命令、初轮失败与修正、像素测量、原创截图、构建日志、APK 哈希与 native 审计。完整临时素材在忽略的 build 目录。
- **未验／限制／下一步**：用户个人录屏未作帧保存或画面截图，采用同尺寸／编码／旋转的原创片做自动像素验证。Compose 时钟测试不等于 release 帧率证明；完整无障碍／大字体／分屏／API26–32 和其他设备未覆盖，音频硬件资格不变。用户可在平板 QA 复看，再用原签名生成正式 0.1.2；视觉满意度仍由用户确认。未经授权不自动提交／推送。

## 2026-10-14：U21 13 Ultra 视频加载、手势与响应修复

- 任务／结果：修复 libmpv 在实体机重开 `/proc/self/fd` 被拒绝的问题，改授权 `fd://`；精简控制器并支持暂停时点击淡出；新增可开关双击／横拖目标时间与真实预览／左右侧亮度和播放器音量。保留双指旋转缩放、8×、字幕和抽屉等原功能。
- 音频响应：Media3仅视频40ms目标PCM缓冲＋低延迟AudioTrack，保持Sonic完整0.1–8×；mpv共享AAudio＋30ms软件队列与常驻tempo，回读current-ao。平台AudioTrack变速候选因小米拒绝7.4×而未采用，见ADR0009；音乐路径未变。
- 文件：VideoSurface／VideoPlayerPage／VideoGestures／VideoSeekPreview、三个播放后端／输出工厂、状态与媒体信息、mpv_worker；新增跨UIDprovider与真实触摸测试，更新既有UI测试、README、AGENTS、技术设计／实施计划／视频体验／签名文档。
- 命令／证据：两ABI debug＋release／37项JVM／lint exit0（0error、2既有warning）；13 Ultra最终28项真实手势与音视频回归＋1080p专项1项＋R8 release启动／JNI／mpv解码3项通过。四APK各12so的ELF／适用ZIP16KB检查全通过，git diff --check exit0。完整命令、日志、hash与自制截图见 [U21验证](validation/video-u21/README.md)。
- 设备／数据：先枚举有线 `c9e352e3`／2304FPN6DC／API36／4KB页；原用户签名release及数据保留，显式-PdeviceQa=true共存安装YYPlayer QA，全部install-r，未清数据／全局设置。1080p松手倍速回读Media3约32ms／mpv79ms；实际播放时钟亦检查，仍存在有限缓冲，不宣称零延迟。
- 未验／下一动作：17 Pro Max未连接，API26–32／其他路由／16KB页真机／4K HDR重负载／release帧率／完整无障碍未验。本轮最新参考截图未附，视觉手感待用户复核；手机已恢复QA debug供直接试用，正式release需用户原密钥签名。B01和U21均留在dev未提交／推送，app/release用户产物保留。

## 2026-10-14：B01 版本 0.1.0、双 ABI 独立 APK 与 release 体积优化

按用户要求更新编译规则，初始 dev `f24008b` 工作区干净；本轮没有新的提交／推送授权，改动保留在 dev 工作区。

- 文件／行为：`app/build.gradle.kts` 将版本设为 0.1.0／versionCode 2，仅编译 arm64-v8a、x86_64 并分别打包 debug／release，不生成 universal；用公开 Variant API 命名 `YYPlayer_for_Android_<version>_<arch>_<debug/release>.apk`。Release 全应用 R8 代码／资源压缩，native 库压缩安装时解压；`src/release/keepRules/native.keep` 精确保留 PCM JNI，已有 MpvNative @Keep 保留。工具链、媒体能力与正式签名身份不变。
- 脚本／文档：MuMu 脚本从 metadata 选择设备 ABI，保留数据并禁用增量安装；原生审计适配单 ABI／压缩库，继续检查 ELF 和未压缩 ZIP 16KB。更新 README、AGENTS，新增 [编译与签名](BUILD_AND_SIGNING.md)，明确正式签名、文件名、安装空间与 mapping 归档。
- 验证：最终 assembleDebug／assembleRelease／assembleDebugAndroidTest／37 项 JVM／lintRelease exit 0（0 error、2 既有 warning）；四个主 APK 的命名／版本／ABI／12 so／16KB 静态审计通过。未签名 release 为 arm64 13.62 MiB、x86_64 14.62 MiB。现有调试证书签署独立 x86_64 release QA，验签／zipalign exit 0；新增仅依赖 Android API 的 ReleasePackagingInstrumentation，非 debug 包启动／音频 JNI／mpv 解码 **3 项通过**。恢复正常 debug 后视频 UI＋libmpv **14 项通过**。命令、异常修正、hash、日志见 [B01 证据](validation/build-b01/README.md)。
- 设备／未验／下一步：当前 adb 为空后启动用户 MuMu，读取实际端口连接 API35／x86_64；自制 v15／v16 素材，全部 install -r，最终恢复 debug 0.1.0。首次 QA 证书误选和 debug runner 不适配混淆包均已修正。实体 arm64／API26–32／16KB 页设备／完整 release 性能与正式私钥签名流程未验，未宣称视觉满意或发布 gate 关闭。下一步使用正式签名配置及目标 arm64 真机验证；不因本轮构建任务扩展媒体功能。

## 2026-10-14：U20 倍速上限统一 8×，删除特殊路由

用户要求将最高倍速降至 8×，无需特殊适配，更新文档并提交。本轮在 dev `d551f38` 上实现，初始工作区干净，按授权提交到 dev，不推送／合入 master。

- 文件／行为：`VideoSpeedSettings.kt`、`VideoPlayerPage.kt`、`AndroidMedia3PlaybackBackend.kt`、`MpvVideoBackend.kt`；UI 和两个后端统一 0.1×–8×／步进 0.1×，外侧快捷不变。删除超过 8× 自动换后端、requestedSpeed 参数和仅硬解专用范围／文案，调用原 setSpeed；手动后端选择保留。
- 验证：assembleDebug／assembleDebugAndroidTest／37 项 JVM／lint exit 0（0 error、2 既有 warning）；MuMu 视频 UI 8＋libmpv 6 共 **14 项通过**。验证拖到 8仍是 Media3、越界限制8、MPV回读与重建、0.1步进／1.25快捷、3×恢复、抽屉真实滑动及返回。测试文件随之更新，详见 [U20 命令、APK hash 和证据](validation/video-u20/README.md)。
- 文档：README、AGENTS、技术设计、实施计划、视频体验和 ADR0008 更新；U19 10× 和自动路由仅作为历史保留。
- 设备／限制／下一步：先枚举 MuMu `127.0.0.1:16384`／API35／x86_64，自制 U15／U16 素材与独立偏好，全部 install -r。实体设备／API26–32／高速帧率听感／完整无障碍待验；未改 native 库／音频输出，不重复无关资格测试。用户下一步复核 8× 统一范围。

## 2026-10-14：U19 自由倍速、文档及 dev 提交

用户要求设置内新增 0.1×–10×／步进 0.1×、外侧档位不变，并更新文档和 AGENTS 后提交 dev。本轮按授权集中收录累计 U15–U19 视频实现、验证素材及当前／历史基线文档；不推送、不合入 master。具体提交身份以 git log 为准，以下旧章节“未提交”为当时记录。

- 文件／行为：新增 `VideoSpeedSettings.kt`，修改 `VideoPlayerPage.kt`、`AndroidMedia3PlaybackBackend.kt`、`MpvVideoBackend.kt`；工具中滑杆松手生效、加减 0.1×／恢复 1×，快捷 0.5／1／1.25／1.5／2／3 保留。查锁定 Media3 字节码确认实际软件音频上限 8×，更高速度使用已有 libmpv；仅硬解保留 8×，不静默放弃严格策略。见 ADR0008。
- 验证：assembleDebug／assembleDebugAndroidTest／37 项 JVM／lint exit 0，0 error／2 既有 warning；MuMu **28 项全部通过**（视频 UI 8、libmpv 6、视频后端 5、手势 4、音乐 5）。包含真实滑到 10、0.1 步进、1.25 快捷、重建保留、3× 恢复上下边界、真实抽屉滑动和返回。原生审计两 ABI 全部 24 so ELF／ZIP 16KB 通过。首次既有短屏点击因菜单增加需滚入目标，修正测试定位后全量通过；未删除断言。命令／exit／APK hash／日志／截图见 [U19 验证](validation/video-u19/README.md)。
- 文档：README、AGENTS、视频体验、技术设计、实施计划及 ADR0008 同步；纠正“mpv 未集成”与 U17 仅语义滚动证据边界，保留历史记录。
- 设备／限制／下一步：先枚举 MuMu `127.0.0.1:16384`／API35／x86_64，测试用自制素材和独立偏好，全部 install -r。实体小米／API26–32／高速帧率听感／完整无障碍与原生发布资格待验，未重复无关 DAC／主机测试。用户下一步复核新倍速体验。

## 2026-10-14：U18 修复抽屉无法拖动、补子菜单返回

用户在 MuMu 反馈设置列表不能滚动、子菜单没有返回上一级的画面按钮。已在 dev 修复并 install -r 更新模拟器，保留用户数据，未提交／推送。

- 根因／文件：`VideoSettingsDrawer.kt` 原 Final pass 无条件消费触摸，取消了子级拖动；现仅退出动画期间拦截，正常打开时保留滚动事件。`VideoPlayerPage.kt` 增加父级历史，子菜单标题左侧提供 Miuix 返回按钮，系统返回逐级回退；完成／取消／遮罩直接关闭。保留横屏半透明外观与竖屏弹窗。
- 检查：assembleDebug／assembleDebugAndroidTest／lintDebug exit 0，0 error、2 既有 warning。MuMu 直接 instrumentation **8 项通过**：真实 swipeUp／swipeDown 并断言滚动位移、短横屏／200% 字体滑动到底、画面内返回、字幕同步三级返回，兼顾原有动画／关闭／真实后端菜单。`VideoPlayerUiTest.kt`／`MpvVideoDeviceTest.kt` 更新；日志、APK hash、命令和截图见 [U18 验证](validation/video-u18/README.md)。
- U17 证据更正：当时 performScrollTo 为语义滚动，绕过触摸流程，不能证明手指拖动可用；本轮补上真实注入事件测试。
- 设备／边界：先枚举 MuMu `127.0.0.1:16384`、API35／x86_64，沿用自制 U15／U16 素材与独立偏好。实体小米、完整 TalkBack、API26–32、release 性能待验。下一步请用户在 MuMu 复核滚动与返回手感，未记视觉最终确认。

## 2026-10-14：U17 横屏视频设置右侧半透明抽屉

用户要求横屏视频设置从右侧抽屉弹出、背景略透明。已在 dev 完成并 install -r 更新 MuMu，未提交／推送；原 D01／U15／U16 改动保留。

- 文件／行为：新增 `VideoSettingsDrawer.kt`，修改 `VideoPlayerPage.kt`、`HyperDesign.kt`、`PlayerScreens.kt`；按实际视口宽高选择布局，横屏设置统一右侧 320–420dp 抽屉，260ms 滑入／滑出、90% 不透明 Miuix 背景、22% 黑色遮罩。标题和完成／取消固定，内容可滚动；竖屏和音乐保留原弹窗。设置打开时隐藏底层播放按钮，退出动画期间禁止误触；支持返回键／点击空白关闭。
- 验证：assembleDebug／assembleDebugAndroidTest／lintDebug exit 0（0 error、2 既有 warning），直接 instrumentation **7 项通过**（视频 UI 6＋libmpv 真实菜单 1）。新增 `VideoPlayerUiTest` 用例覆盖抽屉右侧几何、真实倍速选择、返回／遮罩关闭、布局宽高变化、360dp 高／200% 字体、滑入位置和退出屏蔽。首次返回测试缺少 Compose 同步已修正并复测通过；详见 [U17 命令、日志与截图](validation/video-u17/README.md)。
- 设备／素材：先枚举 MuMu `127.0.0.1:16384`／API35／x86_64、1920×1080 横屏，自制 U15／U16 素材和独立偏好。APK 和测试 APK 均 install -r，保留用户数据；主页面启动 Status ok。截图只含测试素材。
- 未验／下一步：实体小米、完整 TalkBack、API26–32、release 帧率待验；不因 UI 修改重复无关 native／音频硬件测试。下一步由用户复核横屏宽度、透明度和手感；未写视觉已满意。

## 2026-10-14：U16 可选 libmpv 与第四项视频差异补齐

用户要求继续未完成的第四项，并允许参考 libmpv-android、更换视频后端。已在 dev 加入可选 `dev.jdtech.mpv:libmpv:1.0.0` 原生路径，保留 Media3 默认及回退入口，没有提交／推送／发布。接手已有 D01／U15 改动保留；Rust 项目只读且状态干净。

- 功能／UI：`VideoPlayerPage.kt`、`PlaybackBackend.kt` 增加后端选择、真实章节、双向逐帧、音频／字幕偏移、libass 样式与三种字体预设／覆盖、deband。已有 A–B、轨道、3×、比例／双指变换与截图继续可用；libmpv 截图带字幕。入口为「播放工具 → 视频后端 → libmpv · 扩展功能」。
- 播放／生命周期：新 `MpvVideoBackend.kt`、`MpvNative.kt`、`native/jni/mpv_worker.cpp`；C++ worker 统一 mpv API，有界队列、合并 seek／poll、UTF-8、generation 与句柄注册表、白名单。Surface 和 FD 在 worker 销毁确认后释放，清空队列／退出也走同一保护。真实 SimpleBasePlayer 驱动 MediaSession，含 stop／prepare。`AndroidMedia3PlaybackBackend.kt`／`PlayerApplication.kt` 先停止释放视频再恢复音乐或启动音频自检，保留音量与播放意图。
- 构建：`app/build.gradle.kts`、`native/CMakeLists.txt` 固定 AAR／SHA256，提取两 ABI。发现 NDK28 libc++ 缺浮点 from_chars，改为显式共用上游固定 NDK29 libc++；编译器和公开接口保持 NDK28。没有复制未知 APK 库、运行上游自动安装脚本或修改全局环境。原生 header、脚本及库 hash 与架构决定见 ADR0007／U16 audit。
- 验证：assembleDebug／assembleDebugAndroidTest／37 项 JVM／lint exit 0（0 error、2 既有 warning）；MuMu 全量 **22 项**（libmpv 5＋U15 视频／手势／UI 12＋音乐 5）通过。之后补 stop／prepare 与清空队列销毁边界，再构建／单测／lint 并复测 5 项 libmpv。真实验证了单帧时间差、章节、偏移／样式实际属性、ASS 开关像素、Surface 重建、双后端往返、同一 MediaController 转 native PCM 音乐、A–B／3× 和菜单点击。`Verify-MpvArtifact.py` exit 0：全部 24 个 so、两 ABI ELF／ZIP 16 KB 对齐，单一 libc++。命令、exit、APK／素材 hash、日志／自制截图见 [U16 验证](validation/video-u16/README.md)。
- 设备／素材：先枚举 `127.0.0.1:16384` MuMu API35，实际 app x86_64；不是实体小米。自制三章节 4:3 H.264／双 AAC／ASS 动画 MKV 与低幅 PCM WAV，独立偏好；所有安装 install -r，未动用户库或全局系统设置。
- 限制／下一步：默认仍 Media3；物理 arm64 装载／硬解、API26–32、HDR、实体多指／触感、release 性能及完整 SAF 流程待验。libmpv 严格硬解不开放，字体仅三种预设；没有迁入全部桌面 shader／窗口功能。上游 FFmpeg 含 GPLv3+ 配置，完整依赖源码／许可证交付与可复现构建 gate 未关闭，所以本轮不发布、不称最终 native 迁移完成。下一步是用户从扩展入口复核体验与真实设备资格验证；未写视觉已满意。


## 2026-10-14：U15 视频页面、全窗口变换与桌面视频功能补齐

用户本轮要求优化小米风格的视频页、双指操作后提供还原、放大覆盖原黑边、3× 单次系统触感，并参考 Rust 桌面补视频功能。在 dev 上实现，未提交／推送；接手已有 D01 文档修改完整保留，Rust 工程只读（本轮参考 HEAD `e6a8456`）。

- 前端：`VideoPlayerPage.kt`、`PlayerScreens.kt`、`VideoSurface.kt`、新 `VideoViewport.kt`／`VideoGeometry.kt`、`PlayerHostActivity.kt`；黑色沉浸画布、统一白色操作、细进度条、工具／字幕／画面分组窗口、锁定／还原、±10 秒、系统栏和保持亮屏。`MusicPlayerScreen.kt` 仅把既有进度条开放为共用组件，音乐外观不变。旧比例限制 PlayerView XML 已移除。
- 变换：TextureView 布局铺满窗口，比例适应并入矩阵；0.5–5×、重心锚点、任意旋转与归一化平移保留。变换后独立显示还原。真实 4:3 样片的像素检查发现暂停画面未重绘，已通过同时 invalidate TextureView 与宿主修复；最终验证放大覆盖原黑边、还原恢复比例，不只检查矩阵。
- 后端：`PlaybackBackend.kt`／`AndroidMedia3PlaybackBackend.kt` 增加真实轨道投影／选择、重建保留参数、视频独立顺序／单项循环／随机、后端 PlayerMessage A–B、带 token 的临时 3×。进入成功才调用 KEYBOARD_TAP，一次短触感；失焦、第二指、媒体切换、重建、结束和取消恢复基础速度。
- 字幕与截图：新 `VideoSubtitle.kt` 后台限量导入 SAF SRT／VTT／ASS／SSA（4 MB／20000 条），Media3 预解析后才重建，拒绝旧 URI／generation；支持内外字幕、音轨开关和字幕字号。SubtitleView 跟随视频变换，控制层显示时提高默认字幕位置。截图单次读取，最长边 1920，系统选择保存位置，不含字幕／控制层。
- 验证：`assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` exit 0；37 项 JVM 无失败。MuMu 直接 instrumentation **17 项通过**（视频后端 5、手势 4、视频 UI 3、既有音乐回归 5），含真实字幕 cue、双音轨、解码重建、A–B 多次回绕、旧 boost 拒绝、黑边像素、还原／锁定、360dp／200% 字体约束布局。最终清理未使用资源后再打包／lint。具体命令、APK／样片 hash 和自制画面截图见 [U15 验证](validation/video-u15/README.md)。
- 设备：先枚举为空；从正在运行的 MuMuManager info 获取并连接 `127.0.0.1:16384`，Android15／API35／报告型号 2304FPN6DC。所有安装 install -r，无卸载／清数据。样片为自制 testsrc2＋低幅双 AAC＋mov_text，测试偏好独立，不操作用户媒体或全局设置。
- 限制：本轮不是 Rust 全功能对等。精确双向逐帧、通用容器章节、音频／字幕延迟、自选字幕字体／完整 libass 特效和 deband 未迁入；对照与原因见 [视频体验](UI_VIDEO_EXPERIENCE.md)。截图保存器的真实 SAF 写入完整流程、API26–32、实体多指／触感、TalkBack、release 性能／HDR仍待专项。MuMu 上的大字体约束测试不等于全系统无障碍验收，未写“视觉已满意”。

下一动作：用户复核小米风格与物理手感；若继续推进桌面对等，按缺口表先建立章节／时间偏移／精确逐帧的后端契约与素材 gate，不能伪装为已实现或用估算 seek 冒充逐帧。

## 2026-10-14：D01 文档与 AGENTS 接手基线同步

用户要求更新文档和 AGENTS.md。功能代码基线为 `dev` 提交 **`607ceb3`**；HiFi 基线 `c75b34d` 已合入，master 保持 `836a920`。本轮仅修改文档，尚未提交／推送。

- `AGENTS.md`：替换旧 U08／MuMu 接手入口，明确 dev 开发与 master 仅接受授权合入、最新 U11–U14 功能、真实 service／唯一引擎、最终按钮与动效规则，以及有线小米 13 Ultra／无 DAC 的验收条件。保留架构、线程、音频真实性、数据和兼容性约束；不把待验内容写成已通过。
- `../README.md.old`、`IMPLEMENTATION_PLAN.md`、`TECHNICAL_DESIGN.md`：更新当前实现与提交状态、设备命令和验收入口，区分已有 Media3／C++ PCM／JNI／AudioTrack 与后续 FFmpeg／libmpv／Oboe、Room／DataStore 规划；版本表核对实际 catalog，清除过时坐标与“无需 ExoPlayer”描述。
- `HANDOFF_HIFI.md`：修正 H02–H05 已提交状态和当前启动指令，标记旧设备／无线地址／测试命令为历史范围，修复相对链接。`UI_SETUP_AND_LIBRARY.md`、`UI_MUSIC_EXPERIENCE.md` 同步底栏单按钮、收藏入口、52dp／50dp 按钮、双语样式 v2 和 U14 保留回弹；移除未收录原图的失效链接。
- 验证：核对 `git log`／分支、catalog 和 service／歌词源码；文档入口本地链接检查及 `git diff --check` 通过（exit 0）。只改 Markdown，没有新增功能或重跑构建／设备测试，继续引用 U14／U13 已记录证据。

下一动作：按用户后续任务在 dev 继续；本轮文档改动待提交。Compose suite、低版本／无障碍／性能与音频硬件资格的既有未验事项不变。

## 2026-10-14：提交 U11–U14 到 dev

用户明确授权提交。本次提交收录 U11 音乐库／歌单／队列／输出回退／双语歌词，以及 U12–U14 按钮、列表与封面动效修复，包含对应测试和实机验收记录。下方各轮“未提交”描述为当时状态，本条覆盖当前提交状态；不推送远端，不合入 master，不修改 Rust 参考工程。

提交前检查：当前分支 `dev`，`git diff --check` exit 0；新增文件均为本批源码、测试素材或验收证据，没有加入 build、APK、设备参考原图或本地配置。沿用 U14 最终构建／34 项单测／lint 和真机录屏结果，本轮只提交及更新记录，不重复构建。后续按用户反馈继续开发，既有未验条件保持。

## 2026-10-14：U14 保留收回弹簧，消除终点换图

状态：**按用户纠正恢复收回回弹，已保留数据安装并录屏检查。** U13 的无回弹收回曲线被本轮覆盖，透明控件误触修复和 9° 无回弹按压保持。全部仍在 `dev` 未提交工作区，master／Rust 不变。

- U14.1 `ui/YYPlayerApp.kt`：收回 spring 刚度 230／阻尼比 0.8／阈值 0.0001，几何使用带符号进度，允许轻微越过落点后复位；页面透明度与控件退出单独处理。共享封面层完成弹簧后继续显示底栏小图，消除结束瞬间切换另一绘制节点；静止位置跟随底栏实际测量，减少动画／视频走原路径。
- U14.2 同文件：尾段快速重开时，以当前可见封面矩形重新起步并测量新页面目标，兼容歌词锚点转大封面。保留 U13 Initial pass 拦截、不可交互语义和透明详情移除，回弹不能让旧菜单恢复点击。
- 构建 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → **exit 0／BUILD SUCCESSFUL 1m10s**；JVM **34 项、0 失败**；lint **0 error／2 既有 warning**。主 APK install -r Success，`git diff --check` exit 0。
- 有线小米 13 Ultra `c9e352e3`／API36／`OS3.0.308.0.WMACNXM`，自制 QA Song 4 暂停。录屏确认末段宽度约 94→71→79px、顶部 y1466→1448（720px 宽录屏中的粉色边界），存在真实回弹，终点没有换图；大封面和歌词收起约 350ms 后点击底栏均正常重开，没有菜单闪现／意外播放。裁去系统栏的录屏和边界采样见 [U14 验收](validation/ui-u14/README.md)，此为视觉证据而非帧率基准。结束回到音乐库、4 首原顺序队列、QA Song 4 暂停，导入与偏好未清除。
- APK SHA256 `31C6A63D0E3C69C71FF4B4582F4583B8F81950F7721B735BAC48B10F85E74F25`。README／音乐体验文档同步说明 U14 覆盖 U13 收回参数。

未执行／下一步：本轮只有转场修改，未重跑 U13 已通过的播放 instrumentation 或 native CTest；Compose suite 已知挂起、release 帧率、横屏／大字体／TalkBack／API26–32 仍待验。由用户复核实际回弹幅度和收尾手感。

## 2026-10-14：U13 按钮比例、封面返回与透明页面误触

状态：**本轮四项修改已实现，最终 APK 已保留数据安装到有线小米 13 Ultra，旧版弹窗闪现已录到复现，新版相同操作对照通过。** 继续在 `dev`，保留 U11／U12 改动，未新提交／推送；master 为 `836a920`，Rust 参考仓库工作区干净。实机证据见 [U13 验收](validation/ui-u13/README.md)。

- U13.1 `ui/HyperDesign.kt`、`PlayerScreens.kt`：分析用户企业模式／米家截图，按 density 560 换算高度约 52.3dp／50dp。公共页面操作由 56dp／SemiBold 调整为 52dp、18sp／Medium；弹窗通过局部令牌统一 50dp、17sp／Medium，选择弹窗取消也使用同一组件。圆角 16dp，并排等宽间隔 12dp，灰色次要／主题色确认，图标触控仍为 48dp。截图不能唯一确定第一方原始字号／字重，不宣称复制精确字体参数。实机新版量得页面 182px、弹窗 175px；队列、取消确认和引导前进／返回已检查。
- U13.2 `ui/YYPlayerApp.kt`、`MusicMotion.kt`：收回从弹簧改为 360ms 单调贝塞尔曲线，避免在小目标处过冲和尾段迟迟不交接；展开保留低幅弹簧。共享封面层在详情静止时保留组合，更新稳定底栏测量，收回使用相同圆角／阴影插值；歌词页小封面连续回到左下角。角落最大倾角从 7° 增至 9°，临界阻尼 1／刚度 95 不变，保持无触底回弹。
- U13.3 同上：旧版收起后约 350ms 点击 `(800,2970)`，录屏确认误触透明详情的“歌词与播放选项”，独立弹窗短暂显示再随详情销毁。新增 Initial pass 输入拦截、不可交互语义隐藏和尾段透明详情树移除，阻止消失中的按钮发命令；新版同操作正常重开封面，歌词页较早点击被拦截，无弹窗或意外播放。
- U13.4 `ui/MusicLibraryBrowser.kt`：首页删除“全部音乐／收藏”卡片，直接显示歌曲组；原收藏数据保留，入口移至“歌单 → 我的收藏”，返回／分类／播放逻辑沿用。`LibraryTabsUiTest.kt` 改为断言“本地音乐”，适配卡片移除。

验证／运行结果：

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → **exit 0／BUILD SUCCESSFUL 1m15s**；JVM **34 项、0 失败**，lint **0 error／2 既有 warning**。两 APK install -r 均 Success；直接 instrumentation `MusicUpgradeDeviceTest` → **exit 0／OK (5 tests)，3.148s**。`git diff --check` exit 0。
- 设备 `c9e352e3`／小米 13 Ultra `2304FPN6DC`／API36／`OS3.0.308.0.WMACNXM`。沿用自制 4 首 30s 低幅 FLAC／纯色封面／双语 LRC；未卸载或清除数据，原导入、`QA-Playlist`、收藏和偏好保留；结束回到音乐库、4 首原顺序队列、QA Song 4 暂停。引导退出后完成标记保留。第一方参考原图只在忽略的 build 目录，公共证据只含自制素材。
- 主 APK SHA256 `CD9250290E7FAE111F71678A9C317C425D261B715C38E49B9D9711C6BCAC0FEE`。修复前／后误触对照、歌词返回、截图和完整构建／测试日志见验收目录。没有把录屏观感当成 release 性能证明。

未执行／下一动作：Compose Activity suite 在此 ROM 上既有挂起，本轮只完成编译；未改 native、未重跑 CTest。横屏／200% 字体／TalkBack／深色专项、API26–32、多指和 release 帧率仍待验；有线连接不具备 USB DAC 验证条件。下一步由用户复核此次按钮比例与收回手感，后续补齐设备专项。

## 2026-10-13：U12 音乐动效衔接与 HyperOS 页面整理

状态：**用户本轮四项修改已实现，最终 APK 已保留数据安装到有线小米 13 Ultra；实际页面、长按排序、多选移除和录屏已复核。** 继续在 `dev` 工作区，包含此前未提交 U11；没有新提交／推送，master／Rust 参考仓库不变。用户最新两张圈选截图仅在忽略的 build 目录作参考，没有进入公共证据。

- U12.1 `YYPlayerApp.kt`、`MusicMotion.kt`、`MusicPlayerScreen.kt`：过渡封面与静态封面使用一致的圆角和阴影；阴影随尺寸连续插值，直到 Animatable 的完整弹簧尾段结束才交回静态层，避免仅按位置阈值在回弹经过终点时切层。降低回弹幅度；固定移动期间的目标位置，结束后继续测量布局。歌词页将 44dp／12dp 圆角小封面作为收回起点，平移到首页圆形封面；隐藏底栏重复封面至收回完成。关闭相邻 pager 页的过渡绘制，避免未选中歌词／标题漏入展开画面；保留快速反向、减少动画设置和无回弹倾斜。
- U12.2 `MusicPlayerScreen.kt`：封面／歌词底部统一倍速、收藏、播放顺序、队列四项操作；播放顺序使用现有 Miuix ListView／Refresh／Replace 图标，不再单独占一行小胶囊。截图圈选的时间偏移小按钮改为整行“延后／提前／重置”操作。
- U12.3 新增 `MusicQueueActivity.kt`、`ui/MusicQueueScreen.kt`，在 Manifest 和 PlayerHostActivity 注册导航；替换 U11 队列弹窗，共用 service 所有的播放器，使用系统 Activity 转场。提供单选／多选、全选、移除、清空确认、长按拖动排序、让位动画、靠边自动滚动和无障碍上移／下移动作。实机发现 LazyColumn 会先消费长按后的移动，已修正为长按成立后在 Initial pass 接管移动，普通滑动和点击仍走正常路径；临时日志已移除。
- U12.4 `HyperDesign.kt` 新增统一 56dp 起的大操作按钮和分组列表行；`MusicLibraryBrowser.kt` 歌曲列表只有组首顶部／组尾底部圆角，中间行直连，继续按 lazy row 加载。`SetupScreen.kt`、`LyricStyleScreen.kt`、`LibraryScreens.kt`、`PlayerScreens.kt`、`OnlineSearchScreen.kt` 和歌单管理的相关小操作改为整行或大按钮；确认主题色，取消／次要操作灰色，既有弹窗全宽灰色取消保留。没有更换 UI 框架或引入第二播放器。

验证／证据：

- 最终 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → **exit 0／BUILD SUCCESSFUL 1m13s**；JVM **34 项、0 失败**，lint **0 error／2 warning**（原 ExportedService 与版本提示）。两 APK install -r 成功；实际完整输出见 [U12 验收](validation/ui-u12/README.md)。`git diff --check` exit 0。
- 最终 APK 直接 instrumentation `MusicUpgradeDeviceTest` → **OK (5 tests)，2.934s，adb exit 0**，队列／播放／持久化／回退回归通过。本轮未再尝试已知挂起的 Compose Activity suite，不能将其计作通过；未改 native C++，未重跑 CTest。
- 小米 13 Ultra `2304FPN6DC`，API36／`OS3.0.308.0.WMACNXM`，有线 `c9e352e3`。沿用自制 4 首低幅 FLAC／双语 LRC；最终版实机将第 4 首拖到首位，再拖回末位，当前歌曲仍为 QA Song 1、暂停位置未变；单／多选数量正确，移除第 3／4 首后仅剩 1／2，库和歌单不变。队列返回详情正常。向导前进／上一步／稍后设置正常，无导入或偏好清除。歌词偏移菜单已查看，未更改偏移。
- 最终录屏包含大封面展开／收回、歌词小封面收回和快速反向；已抽帧复核阴影持续存在、圆角转圆形连续、相邻歌词不漏入展开。此为实机视觉检查，不是帧率／性能达标证明。截图／裁去系统栏的录屏见验收目录。
- 最终主 APK SHA256 `BC128A9E1500488DB884015D451A334E2C3815AA6007410694985FADDD789C95`。结束在音乐库、4 首原顺序队列、QA Song 1 暂停；向导完成标记与原设置保持，用户素材未删除。

未验／下一动作：最终动画手感由用户复核；队列长列表靠边滚动、重复 URI、多指取消、横屏／200% 字体／TalkBack／深色专项、API26–32尚未单独设备验证。本轮无 USB DAC，不新增独占／位准确资格。后续根据用户视觉反馈微调，并在可用设备补齐 Compose UI 自动化。

## 2026-10-13：U11 音乐库／队列／输出回退／封面动画／双语歌词

状态：**五项升级已实现，构建与专项真机测试通过；普通应用 UI 已在小米 13 Ultra 操作验证，动画观感待用户复核。** 先按用户授权将原 dev-hifi 升级提交为 `c75b34d`，再 fast-forward 合入 `dev`；本轮 U11 修改全部留在 `dev` 工作区，尚未提交／推送。`master` 保持 `836a920`；桌面 Rust `e6a8456` 仅只读参考，未修改。

任务／主要文件：

- U11.1 `playback/MusicCollections.kt`、`MusicCollectionRepository.kt`、`ui/MusicLibraryBrowser.kt`、`LibraryScreens.kt`、`PlayerApplication.kt`：歌曲／歌手／专辑／歌单分类；同名专辑按歌手区分；歌单增删改名、单曲／多选添加、移出与排序。后台版本化持久化、数量／体积限制和损坏备份，content URI 为成员身份；库、歌单和播放队列独立，失去授权的成员保留。搜索／收藏／分类／歌单从当前曲目建立队列。
- U11.2 `PlaybackBackend.kt`、`AndroidMedia3PlaybackBackend.kt`、`MusicQueueDialog.kt`、`YYPlayerApp.kt`：顺序末尾停止、单曲自动循环、随机顺序和前后切歌；模式持久化，视频固定顺序。队列跳转、下一首播放、追加、移动、移除、清空；移动其他条目保留当前曲目和位置。`HyperDesign.kt` 移除底栏下一首，只留播放／暂停；详情页保留完整控制。
- U11.3 `audio/OutputFallbackPolicy.kt`、`AndroidMedia3PlaybackBackend.kt`、`PlayerSessionService.kt`、`MusicPlayerScreen.kt`：当前 AudioTrack 后端无法证明独占，PreferExclusive 使用 SYSTEM；native sink 配置／初始化／写入异常在确认同一输出仍连接、非 strict 的前提下单次回退 SYSTEM，保留队列／位置／播放意图／音量。回调匹配引擎版本和媒体代次，旧输出先释放；失败原因与实际路径可见，可主动重试。StrictExclusive／StrictUnprocessed 仍拒绝静默降级；设备丢失不触发回退扬声器。决定补入 ADR0006。
- U11.4 `ui/MusicMotion.kt`、`YYPlayerApp.kt`、`MusicPlayerScreen.kt`：实测底栏小封面与详情大封面位置，根层展开／收回至左下角，带轻微回弹；文字和控制区配套位移／渐变。角落倾斜改为临界阻尼、低刚度的连续跟随，按下不触底回弹；取消、滚动接管、第二指、换媒体复位。修正 clickable 消费初始按下导致倾斜提前复位的问题。
- U11.5 `playback/Lyrics.kt`、`ui/BilingualLyricText.kt`、`LyricStyle.kt`、`LyricStyleScreen.kt`：同步 Rust 的非空 ` / ` 原译文拆分，避免误拆 AC/DC／日期／URL；定时和纯文本共用样式，译文显示开关、当前／其他译文字号、字重、原译文间距。v1→v2 保留原文设置。更新 README、UI_MUSIC_EXPERIENCE 和 [U11 验收记录](validation/music-u11/README.md)。

命令／实际结果：

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → **exit 0 / BUILD SUCCESSFUL 1m22s**；JVM **34 项、0 失败**；lint **0 error／2 warning**（既有 ExportedService、Kotlin 新版本提示，未升级依赖）。最终源码构建见 [checks](validation/music-u11/checks.txt)。
- 两 APK `adb -s c9e352e3 install -r ...` 成功，保留导入／偏好；没有运行会卸载数据的 connectedDebugAndroidTest。直接 `am instrument -w -r -e class yy.daniel.yyplayerforandroid.MusicUpgradeDeviceTest .../androidx.test.runner.AndroidJUnitRunner` → **exit 0 / OK (5 tests)，3.072s**，见 [device-tests](validation/music-u11/device-tests.txt)。覆盖真实 Media3 EOF／随机切歌／队列编辑、真实持久化／迁移、带 encoder delay 的 AAC 触发 native→SYSTEM 且播放时钟前进、strict 拒绝与 PreferExclusive 选择 SYSTEM。测试使用独立偏好和自制素材，不将模拟状态当播放证据。
- 合并运行既有 Compose UI 测试时，新 5 项先通过，随后停在 LyricStyleUiTest；等待无进展后主动 force-stop，因此记录 `Process crashed`，**不计作 Compose UI 通过，也不能据此断言生产应用崩溃**。LibraryManagementUiTest／LibraryTabsUiTest 未完成。保存 [未完成输出](validation/music-u11/compose-ui-incomplete.txt)；普通应用 adb／uiautomator 操作正常。
- 主 APK SHA256 `825F747F145C8475C170817906987BCA1A7F45B029C6B0B9CF9749E2AE9C3856`。`git diff --check` 最终 exit 0；本轮未改 native C++，未重跑主机 CTest。

设备／UI 证据：小米 13 Ultra `2304FPN6DC`，API36／Android16／`OS3.0.308.0.WMACNXM`，有线 adb `c9e352e3`。只导入自制 4 首 30s 低幅 FLAC、内嵌纯色封面和双语 LRC，位于 `Music/YYPlayerQA-U11`，两个歌手各有同名专辑。实际验证分类、菜单创建 `QA-Playlist` 并添加歌曲、歌单播放／队列、底栏单按钮、封面展开／收回／快速反向和右上角按压。译文当前字号改至 24sp、原译间距 20dp 后返回立即生效；点句 seek 保持暂停。结束已恢复歌词默认值（原文 28sp、原行距 14dp、两种译文 20sp、原译间距 8dp），应用停留音乐库、播放暂停。截图仅自制素材，见验收目录。

未验／下一动作：本轮有线占用 USB，没有 USB DAC，**不宣称 DAC、独占或端到端位准确通过**；API26–32、200% 字体／TalkBack、暗色专项、物理多指未重验。Compose Activity instrumentation 在此 ROM 上仍需单独排查或换模拟器重跑；新增 UI 测试已可编译但未完成设备执行。下一步由用户复核实机动画手感；有独立 DAC／设备条件后补做相应输出资格验证。既有 H05 native gapless 限制仍在，本轮提供可说明原因的系统回退，并未宣称原生 gapless 已修复。

## 2026-10-13：保存 dev-hifi 升级基线

用户授权将既有 H02–H05／U09／U10 未提交升级提交到 `dev-hifi`，再合入 `dev` 继续新一轮音乐库、队列、输出回退、封面动画和双语歌词升级。接手时 `dev-hifi` 与新建 `dev` 均位于 `1daef67`，尚未开始新需求源码修改；保留全部既有实现与验收证据，`master` 不变，不推送。

- 提交前复查：`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4`，exit 0，BUILD SUCCESSFUL（19s，部分任务使用缓存）；`git diff --check` exit 0。
- 本次仅保存既有升级，真机与 PCM 资格沿用下面历史记录，没有重新宣称独占／位准确通过；本次未重新运行设备测试或 native CTest。
- 下一步：基线合入 `dev` 后，按 2026-10-13 用户五项需求实施；当前指定设备为小米 13 Ultra，adb `192.168.1.71:41155`，安装保留数据。

## 2026-10-08：U10 歌词显示快捷入口与行间距（当前记录）

状态：**两项用户要求已实现，在小米真实定时歌词页验证，并以install -r更新应用。** dev-hifi，保留全部此前未提交改动，无提交／推送。

- U10.1 `ui/MusicPlayerScreen.kt`／`ui/YYPlayerApp.kt`：歌词与播放菜单新增“歌词显示”，调用现有 `PlayerHostActivity.openLyricStyle()`，正常Activity导航；返回仍在歌词页，共享偏好即时应用。
- U10.2 `MusicPlayerScreen.kt`：原布局在lineSpacing之外叠加上下7dp留白和定时歌词48dp最小高度，小字号／0dp尤其明显。移除定时歌词这两项和纯文本歌词额外留白；保留clickable自动触控扩展、seek、字号／字重／颜色及行内行高／强调动画。更新UI_MUSIC_EXPERIENCE与 [验收README](validation/ui-lyrics-spacing/README.md)。
- 命令：`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → **exit0 / BUILD SUCCESSFUL 1m6s**，现有JVM **30项0失败／0error**，lint **0error／1warning**（既有ExportedService）。`git diff --check` exit0。未为简单布局修改增加镜像实现的单测。
- 设备／证据：小米17 Pro Max `2509FPN0BC`，SDK37，`192.168.1.246:33103`，两APK install -r成功；用户库内真实FLAC及已有定时歌词。0dp时相邻非选中句位置差80px，40dp时200px，增加120px＝40dp（density3）。菜单入口、打开／返回、立即应用、点句seek到00:23且保持暂停均通过。最后恢复原25sp／20sp、特粗／粗体、0dp，停留歌词页暂停；未改输出偏好／系统设置。
- 本轮未运行已知会挂起的Activity Compose instrumentation；使用普通应用UI／uiautomator真实操作与截图。纯文本分支已修复但未单独真机取证；API26–32、200%字体／TalkBack未新增验收，原H05 native gapless等限制不变。
- 主APK SHA256 `a3ff42d2613b2fb93e6e818c583b88839851abd7507edada86216718a304aab7`。下一动作：用户复核当前歌词视觉，后续根据用户反馈；既有H05专项仍按审计／STATUS历史记录待验。

## 2026-10-08：U09 在线音乐搜索 UI（当前记录）

状态：**用户要求的搜索历史、居中无限指示器、结果专辑封面、整条点击播放和单个“…”菜单已实现，并在小米实际页面验收。** dev-hifi，沿用 `192.168.1.246:33103`；不提交／推送，不覆盖之前H02–H05改动。

任务／文件：

- U09.1 `online/OnlineSearchHistory.kt`：独立版本化本地偏好，默认保留，最多20条最近关键词；trim、大小写去重、200字符上限，关闭不新增，支持单条／全清。提交时记录，翻页不记，清除不会被网络迟到结果重新添加；歌词搜索不混入音乐历史。`SearchHistoryStateTest.kt`与`OnlineSearchHistoryDeviceTest.kt`核验MRU／关闭及真实持久化。
- U09.2 `ui/OnlineSearchScreen.kt`：固定搜索输入下的内容区Box居中 `InfiniteProgressIndicator()`，替换纯文字加载，音质检查也用无限环形指示器；查询取消以revision隔离，旧finally不取消新spinner。历史入口位于页头菜单及空页“管理”，使用48dp触控；列表左56dp真实专辑图，原歌名／歌手／专辑文字样式保留，整条点按直接播放，右侧一个Miuix More菜单收纳选音质／下载，图与按钮仍遵守WindowDialog退场保留Composition。
- U09.3 `OnlineRepository.kt`：首个满足配置来源／歌曲声明条件的音质作为直接播放默认；缩略图2个后台worker／4MB内存缓存，复用既有安全有界专辑图缓存，失败占位。`OnlineSearchActivity.kt`／`PlayerApplication.kt`：取得音频URL即发播放，封面独立补齐，不等待图片；匹配媒体generation／identity，关联网和销毁时取消。没有第二播放器、自动改输出方式或静默换音质。
- U09.4 `OnlineApiTest.kt`新增默认档位／限制／无可用档位用例；更新ONLINE_API和 [验收记录](validation/ui-online-search/README.md)。

命令／实际结果：

- 普通四任务 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` **exit0 / BUILD SUCCESSFUL 59s**，JVM **30项0失败**，lint **0error/1warning**（已有ExportedService），[checks-final](validation/ui-online-search/checks-final.txt)。新增test最后需显式Unit返回给JUnit，修正后`:app:assembleDebugAndroidTest` exit0，见`test-build-final.txt`；早期3项/1初始化失败保留为`device-tests.txt`。
- 两APK均install -r保留数据；直接instrument 2项历史+默认音质+联网gate → **OK(4 tests)**（0.089s），[device-tests-final](validation/ui-online-search/device-tests-final.txt)，没有运行会卸载数据的connectedDebugAndroidTest。
- 小米SDK37／OS4.0.0.44，既有GD／网易云API实际搜索123，已取得中心无限环、7个封面／7个“…”、历史重启保留／清除／点击重新搜的真实截图。点整条SYSTEM下真实MediaSession **PLAYING、24604ms、error=null**；直接进入音乐详情，无音质确认。结束暂停，并恢复原Native／跟随系统与默认保留历史，最终停留新搜索结果页。
- 本机Native对这首源拒绝`gapless_trimming_requires_system_output`，属于H05已有encoder delay/padding限制，SYSTEM正面验证通过；本轮未修改PCM／sink，也不自动回退。
- 自动审批拒绝拟导出／替换设备API配置并设置QA服务器／reverse的组合操作（可能含凭据与额外副作用），整条命令未执行。已用安全替代完成：隔离Context测试与原GD普通UI；用户配置没有导出／替换，未装QA包，server未启动，adb reverse空。忽略目录中仅准备过自有QA资源；普通APK online_qa资源数0。
- 主APK SHA256 **`aafd67b65502864398a1da2a699d973a0db14f8f5fca14e38034703f290115cf`**；`git diff --check` exit0。源码及验证全部未提交。

未执行／限制：该小米既有Compose Activity instrumentation挂起，仍用普通adb实际界面验收；200%字体／TalkBack、暗色专项、API26–32、全部提供方音质供给、SAF/MediaStore下载专项未重跑，本轮没有更改下载或音频资格。下一具体动作：按用户反馈微调U09；带delay/padding的Native兼容能力需独立音频任务。用户本轮UI项已完成。

## 2026-10-08：H05 全量未提交代码审计／歌曲详情输出切换（前轮记录）

状态：**H05 源码审计、修复与歌曲详情输出控制完成；最终 APK 29 项真机回归通过，USB 整曲与真实界面操作已取证。** `dev-hifi` 上 H02–H05 全部未提交。用户更换网络后设备为小米 17 Pro Max / 2509FPN0BC，Wi-Fi adb **`192.168.1.246:33103`**（SDK37）；lifeme HiFi Audio Pro USB DAC 与 OPPO Enco Free4 A2DP 均在线。本轮复读 42 个 USB mixer profile 全为 DEFAULT，BIT_PERFECT 正面硬件与数字捕获仍无条件。

任务／文件／结果：

- H05.1 完整审查 tracked diff 与全部未跟踪源码，覆盖 H02/H03/H04 的核心、JNI、Android 输出、自检、播放器／service、UI、清单与构建；发现及处理表见 [HIFI_CODE_AUDIT](HIFI_CODE_AUDIT.md)。没有仅沿用上一 AI 的结论。
- H05.2 `yy_jni.cpp`、`NativeAudioCore.kt`、`pcm.cpp`、`ring.cpp`、`policy.cpp`：修正并发 release/Clear 的裸指针风险、ByteBuffer position/limit/只读目标、C++17 负数移位及 padding 溢出／重叠写、超 32 项 profile 截断、Android/core mask、异常边界及分配上限。DSP 参与处理时撤销 pristine 期待。
- H05.3 `AudioOutputController.kt`、`NativeOutputAudioSink.kt`、`AudioTrackPcmSink.kt`、`OutputSelfTest.kt`：规划不再操作活动 mixer 或冒充实际 sharing；依据实时契约／路由／API 格式投影，reset/pause 清除证据。设备失联保留 pin，停止／清空并拒绝自动扬声器回退。USB 匹配 DEFAULT mixer 在开 track 前申请；它只解决本机 pin 后首帧回旧蓝牙的问题，不证明原样或独占。
- H05.4 自检先释放歌曲输出，锁住播放和切换，finally 完成后解锁；保留软件音量／原位置，恢复为暂停。fixture 固定 S16 stereo，供帧前确认实际路由，短写重试、不补造 PCM，结束确认真实播放头；关闭不在调用线程 join。BIT 请求仅在原有系统／软件音量 unity 时允许，绝不调满音量；活动 BIT 的音量变化拒绝并停流。
- H05.5 PCM 格式切换等待旧尾部，flush 保留下一格式；API31+ 排空时降低 start threshold，修复暂停后短尾部不足门槛的停摆。自研拒绝 float／encoder delay-padding／重排／未证严格策略，实际 1×；系统 2× 意图在来回切换后保留。视频硬解偏好不再筛掉音频软件解码器；旧 player 回调以实例／revision 隔离。
- H05.6 `MusicPlayerScreen.kt`、`YYPlayerApp.kt`：歌曲详情右上“音频输出”图标及选项菜单进入同页 WindowDialog，内有“输出方式／输出设备”下拉，直接控制唯一 runtime，设置页同步；一次弹窗内设备 ID 索引稳定，断开 ID 保留为拒绝项。自研名称为“自研输出（原样优先）”，不把它宣传为已独占。
- H05.7 `OutputAuditDeviceTest.kt` 新增 7 项真机回归：JNI 窗口／并发／完整 profiles、位完美负证据、两方式 USB/BT 实际路由与进度／速度、自检互斥／静音／位置，以及短尾部重配和 flush；补齐 audit、ADR0006、HANDOFF 与 validation 记录。

实际命令／证据：

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → **exit0 / BUILD SUCCESSFUL**（1m3s），最终 [h05_oct08_gate_checks](validation/hifi-v1/h05_oct08_gate_checks.txt)。JVM **28 项 0 失败**；lint **0 error / 1 warning / 0 hint**，仅导出 MediaSessionService 的 ExportedService（供系统控制器绑定）。没有卸载、清数据或改变系统设置。
- `cmake --build native/build-host` + `yy_core_tests.exe` → exit0，**6390 checks / 0 failures**；NDK arm64 重编译，adb push 到 `/data/local/tmp` 后手机执行也为 **6390 / 0**。证据 `h05_oct08_core_*`、`h05_oct08_ndk_core_build.txt`、`h05_oct08_device_core_test.txt`。
- 两 APK 均 `adb install -r` Success。首次 29 项组合 **28 通过／1 失败**，暂停尾部重配 position=0，保留 [h05_oct08_device_test](validation/hifi-v1/h05_oct08_device_test.txt)。修正 API31 排空门槛后审计类 **OK (7 tests)**（18.282s），[h05_oct08_audit_retest](validation/hifi-v1/h05_oct08_audit_retest.txt)。先前 USB/BT 的真实路由失败与匹配 DEFAULT mixer 后通过日志亦保留，不把 shell exit0 当测试通过。
- 最终 5 类组合 `am instrument -w -e class ...` → **OK (29 tests)**（103.102s），[h05_oct08_final_regression_test](validation/hifi-v1/h05_oct08_final_regression_test.txt)／`h05_oct08_final_logcat_audio.txt`；包含自检期间高级 sharing/transparency 与 backend rebuild 也被 gate 拒绝的新增断言。前一完整组合为 29 项通过（102.031s），不混作最终结果。
- 固定实际 USB ID919，`-e fullPlayback true -e pinnedDeviceId 919 -e class '...NativeOutputPlaybackTest#nativeSinkPlaysALocalFileThroughExoPlayer'` → **OK (1 test)**（280.214s），[h05_oct08_usb_full_test](validation/hifi-v1/h05_oct08_usb_full_test.txt)／`h05_oct08_usb_full_logcat_audio.txt`。全程实际 USB，s24_3/44100Hz，结束 state=4，decoded=submitted=played=12109193、buffered=0、partial=0；metadata12109198，差5帧为毫秒舍入范围。该整曲 APK 为 `d30234def5969aea83adab453e295eeecc2dc9b8039244ed87d042f169c3fb0b`；其后只补 runtime/backend 自检 rebuild gate 和测试，PCM／sink 代码未变，最终 APK 又跑完整29项，不把两构建哈希混写。
- 2 ABI 各 3 个打包 so 的 ELF LOAD 为 **0x4000**；最终 zipalign `-c -P 16 -v 4` exit0，见 `h05_oct08_elf.txt`／`h05_oct08_gate_zipalign.txt`。最终安装 APK SHA256 **`1219906f9f6b00dcfe21e9c904322a89b85228eaacdd787e94dd4898bc7d8734`**。
- 最终 APK 的歌曲详情普通 adb/UI 验收：从真实库歌曲打开右上“音频输出”，操作方式 Native→SYSTEM、设备跟随→USB→A2DP，Native 分别回读 USB／OPPO；切换后本应用 MediaSession PLAYING、position=215218、error=null，暂停后详情03:37。恢复原 Native／跟随系统偏好。截图及操作记录见 [h05_ui_receipt](validation/hifi-v1/h05_ui_receipt.md)。持续动画时 uiautomator idle 偶发失败，暂停后正常；没有声称 Activity instrumentation 通过。
- 最终回归后再次无 extra 普通启动 MainActivity，topResumedActivity 为本应用，见 `h05_oct08_final_launch.txt`；`git diff --check` exit0，NativeOutputAudioSink 临时 HANDLE_BUFFER/EOS/IS_ENDED/DISCONTINUITY/TRACK_FORMAT/TRACE 日志检查无匹配。

未执行／限制：本机没有独占／BIT_PERFECT 正面资格，无数字 capture；API 格式不等于 HAL／hardware。API26–32 类加载、API26–30 短尾部、物理拔插／焦点、锁屏长测、高采样率、Oboe 与 FFmpeg PCM 解码均待专项。Compose Activity instrumentation 的旧挂起问题仍存在，不声称本轮 UI instrumentation 通过；歌曲详情采用普通 adb 启动／真实界面取证。

下一动作：用 API26–32 设备核验类加载和短尾部；在保持停止策略的前提下做物理 USB/BT 断开与焦点／锁屏专项；取得合格 BIT_PERFECT DAC／数字捕获设备后独立做正面资格。H05 用户两项请求已完成，不自动提交／推送。

## 2026-10-06：H04 输出通道与真实媒体会话（历史记录）

状态：**4.2 秒停摆已修复，真实 24-bit FLAC 整曲与 MediaController/service 验收通过；实际独占／位完美硬件资格仍待验。** 分支 `dev-hifi`，H02/H03/H04 仍全部未提交。设备仍为小米 2509FPN0BC（Wi-Fi adb `192.168.114.116:33077`、SDK37、HyperOS OS4.0.0.44）+ lifeme HiFi Audio Pro；该 DAC 无 BIT_PERFECT 组合，不能声称独占、系统报告位完美或数字捕获已验证。

任务／文件／实际行为：

- H04.1 `PcmSink.kt`、`AudioOutputScreen.kt`、`PlayerApplication.kt`：保留 `SYSTEM / NATIVE_EXACT` 输出通道选择；切换时保留队列、位置、播放意图和软件音量，先释放旧输出，再启动新输出。视频使用 SYSTEM；自研路径拒绝没有证据的严格独占／严格原样请求。
- H04.2 `NativeOutputAudioSink.kt`、`OutputPathRenderersFactory.kt`：修正两项真实根因。原 sink 只检查 `pcmEncoding`，FLAC 的源位深元数据被当作 PCM 支持，触发 Media3 压缩旁路，压缩块被写进 AudioTrack；现只接受 `audio/raw`，由 MediaCodec 真正解码。原时钟忽略 `presentationTimeUs` 中约 10^12 µs 的 renderer 偏移，造成 UI 位置约 −10^9 ms；现返回首输入时间戳 + 实际播放头，未初始化返回 `AudioSink.CURRENT_POSITION_NOT_SET`，与 `C.TIME_UNSET` 区分。**交接中“播放头落后缓冲导致门控停摆”的推断已被本轮证据纠正，写入位置方案未解决问题。**
- H04.3 `OutputPathRenderersFactory.kt`：整数源显式请求 s16/s24_3/s32 PCM，检查 decoder 实际输出，不允许高位深源静默缩为 s16 或绕到 float。24-bit FLAC 已实测解码后 `s24_3/44100Hz`。
- H04.4 `AudioTrackPcmSink.kt`：Media3 外部生产者使用非阻塞写入，短写只推进已接受的完整帧，余量交给 renderer 重试；原自检环泵保持独立。播放头扩展为 64 位并在 flush 后重置，pause/resume 真正作用于 track。实际播放链是 MediaCodec → AudioSink → AudioTrack，核心承担无损加宽；**本轮没有把普通播放改成由核心环泵取帧**，H02 自检仍经核心环。
- H04.5 `PlayerSessionService.kt`、`PlayerApplication.kt`、`AndroidMedia3PlaybackBackend.kt`、清单：session 使用唯一真实引擎；替换 player 后 `MediaSession.setPlayer` 改绑；service 持有生命周期引用，最后 Activity 退出不销毁音乐引擎。原“Activity stop 后 500ms 自动暂停”仅保留视频／无 session 情况。
- H04.6 混音器请求在创建 track 前进行，使用同一 attributes，并独立回读 preferred mixer；请求成功不等于路由或端到端验证。非默认软件音量保留，同时撤销原样资格；自研通道尚无变速／跳过静音处理，返回实际支持的默认值。
- H04.7 `NativeOutputPlaybackTest.kt`、`PlayerSessionServiceDeviceTest.kt`：旧测试只有日志、可无素材直接返回且不判失败；现有真实进度／错误／帧数／结束断言，另验暂停、续播、seek、输出切换后控制、队列切换与后台生命周期。清除 HANDLE_BUFFER／SINK_EOS／SINK_IS_ENDED／SINK_DISCONTINUITY／TRACK_FORMAT／TRACE_* 临时日志；保留必要 counters 与测试进度。
- H04.8 lint 发现并修正 H01/H02 遗留兼容边界：`AudioDeviceInfo.address` 是 API28，24/32 bit 平台编码是 API31，`getRoutedDevices` 是 API36（旧代码错守 API30）；Api34Mixer 标记 RequiresApi34，API26–35 使用单 routedDevice。这只有静态检查证据，低版本类加载仍待设备验证。

实际命令／设备／证据：

- 首先 `adb install -r` 两 APK + 原 `NativeOutputPlaybackTest`：仍在 `decodedFrames=186030` 停摆，`position=-999995781`；释放亦被阻塞，只能 force-stop **本应用**结束测试。instrumentation 的 shell exit0 不是通过，结果为 `Process crashed`。原始证据 `h04_initial_*`。
- 修复后短测：`NativeOutputPlaybackTest` → **OK (2 tests)**；整数请求后 native 单测 → **OK (1 test)**，`position=15560 state=3 isPlaying=true decodedFrames=693268 opened=s24_3`，见 `h04_integer_*`。float 过渡验证独立保留为 `h04_playback_test.txt`，不拿它作为整数路径资格。
- `cmake --build native/build-host` exit0（no work）；`yy_core_tests.exe` → **checks=6339 failures=0 RESULT OK**。JVM **28 项 0 失败**。
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 首次 lint 失败（API 守卫及 opt-in），修正后 BUILD SUCCESSFUL；最终检查 **exit0 / BUILD SUCCESSFUL**（1m4s），lint **0 errors / 1 warning / 1 hint**：ExportedService 是系统媒体控制器需绑定的导出服务，无权限墙；AutoboxingStateCreation 是既有 tick 提示。没有 suppress NewApi 或降低验收口径，输出见 `h04_final_checks.txt`。
- 整曲 `-e fullPlayback true`：**OK (1 test)**，运行 280.196 s，STATE_ENDED=4、position=274592、decodedFrames=submitted=played=12109193、buffered=0、partial=0；metadata 估计 12109198 帧，差 5 帧在 1ms 舍入范围内，见 `h04_full_*`。最终版在严格策略／callback revision／缓冲容量契约收尾后，4 类组合 instrumentation **OK (22 tests)**（78.68 s，runner exit0），见 `h04_regression_test.txt` 与 `h04_regression_logcat_audio.txt`。每次 install 均使用 `-r`，没有卸载、清库或修改系统设置。
- 真实 `PlayerSessionServiceDeviceTest`：**OK (1 test)**（9.496 s），MediaController 验暂停／续播／60s seek、SYSTEM ↔ NATIVE_EXACT 改绑、next、生产 UI `stop/release` 后 service 继续播放；日志 `background=true uiOwnersReleased=true position=4562 state=3`。首次测试线程创建 player 失败，修正后启动 Compose Activity 仍挂起，改为直接调用生产生命周期引用钩子、绑定真实 service/controller。失败记录 `h04_regression_failed_test.txt` 与 `h04_session_activity_hang_logcat.txt` 保留，**本轮没有取得实体机 UI Activity 自动化通过的证据**。
- 最终 APK 已 install -r 保留数据，并无 qa extra 正常启动：`am start -n yy.daniel.yyplayerforandroid/.MainActivity` exit0，dumpsys 的 topResumedActivity 为本应用 MainActivity，证据 `h04_normal_launch.txt`。APK SHA256：`13194d6b6d812ceb3ed892589af556a8ea03141e1a0ca2adafb6eb8dd9f40c8f`。这只确认正常前台启动，不代替像素级 UI 或 Activity instrumentation 验收。
- `AndroidMedia3PlaybackBackend` 的 sink 回调原在播放线程读取 Player，已移至 applicationLooper 并匹配 outputRevision；close/rebuild 使旧结果失效。`OnlineApiTest` 构造改为 `networkAllowed = { allowed }`，避免新增输出策略 callback 截获尾随 lambda；该网络用例本轮只重新编译，没有重跑联网。

未执行检查／已知限制：Oboe/AAudio 独占、BIT_PERFECT DAC 合格组合、数字捕获、API26–32 真机、96kHz 以上输出、物理拔插／焦点、锁屏长期运行、HyperOS 灵动岛各形态均待专项。`getFormat` 只证明 AudioTrack 配置，不能证明 HAL／硬件格式或无重采样。高位深解码器不支持整数请求时拒绝，FFmpeg PCM 解码仍为 S06 后续；自研路径当前固定 1×，不声称具备无缝切歌。系统路径保留 Media3 默认处理。

下一具体动作：本轮停摆修复与 service/controller 集成已取证，继续物理拔插／焦点与锁屏长期播放专项；实体机 Compose Activity instrumentation 启动挂起仍需独立排查。不自动提交／推送。

## 2026-10-06：H03 位深加宽取证（24/32 bit 容器、非 USB 对照）（前轮记录）

状态：**H03 已完成 24/32 bit 容器与非 USB 对照的真机取证，并修掉一个会造成 −48 dB 电平错误的真实核心缺陷。** 分支 `dev-hifi`。设备：小米 2509FPN0BC（Wi-Fi adb `192.168.114.116:33077`，Android 17／SDK 37，HyperOS OS4.0.0.44），USB DAC「lifeme HiFi Audio Pro」+ 蓝牙「OPPO Enco Free4」。完整证据见 [validation/hifi-v1/README.md](validation/hifi-v1/README.md)。

任务／文件／实际行为：

- H03.1 **核心加宽语义修正（最重要）**：`Convert()` 原实现只做符号扩展，把窄容器样本原样放进宽容器。整数 PCM 满刻度是 `2^(bits-1)`，样本是容器量程里的位置而非绝对幅度，因此 16-bit 满刻度 `32767` 放进 24-bit 容器只有满刻度的 1/256（**−48 dB**，几乎听不见）。现按位深差左移并补零（`native/core/src/pcm.cpp` + `pcm.h` 文档），`WidenS16ToS24/S16ToS32/S24ToS32` 三个死代码辅助函数同步改为保电平。性质：精确整数运算、新增低位全零、不丢有效位、不经浮点，仍在 pristine 路径；**同宽容器互转位移为 0（纯搬移，数值不变）**；**降位深仍按 `kLossy` 拒绝**。
- H03.2 核心测试（`native/core/tests/pcm_test.cpp`）：原有断言把“不缩放”当作正确行为，已改写为电平不变量，并新增满刻度比例检查、位移后字节检查、2000 样本随机 16→32、24-in-32→s32 不重复位移、以及 48-bit 边界。主机断言 **2345 → 6339**。
- H03.3 **格式比对修正**：`AudioTrack.getSampleRate()`/`getFormat()` 只是回显构造参数，拿它们与“请求”比对是同义反复，平台替换格式也发现不了。`PcmSink.SinkRouteReport` 新增 `platformFormat`（sink 打开时单独记录 `getFormat()` 结果），`OutputSelfTest.formatMatchesTrack` 与之比对并新增 `formatWasSubstituted`；`AudioOutputScreen` 在发生替换时显示“平台已替换”并单独列出平台实际接受的格式。
- H03.4 **`s24_in32` 明确拒绝**（`AudioTrackPcmSink.open`）：`AudioFormat` 只有 `ENCODING_PCM_24BIT_PACKED`（3 字节）与 `ENCODING_PCM_32BIT`（32 位），没有“24 位有效放 4 字节容器”的编码，故对 `s24_in32` 返回 `sink_bad_format` 并带原因，不静默换容器。核心仍可把 `s24_in32` 无损加宽到 `s32`（低位是填充），该映射尚未接入策略，记为遗留。
- H03.5 **参考信号改直接缓冲**：`AudioVerificationTone.renderS16()` 原用 `ByteBuffer.allocate`（堆缓冲），而 JNI 用 `GetDirectBufferAddress` 取样本内存，堆缓冲返回 null 使 `convertPcm` 返回 −1，表现成“核心拒绝无损转换”。已改 `allocateDirect`。
- H03.6 设备测试（`AudioOutputSinkDeviceTest` 6 → 11 项）：新增 `widenedFormatsOpenAndDeliverAtTheDecidedFormat`（s24_3 / s32）、`twentyFourInThirtyTwoHasNoPlatformContainerAndIsRefused`、`builtInSpeakerDeliversWithoutEverClaimingBitPerfect`、`bluetoothRouteDeliversWithoutEverClaimingBitPerfect`，并把加宽用例改为**固定决策格式**驱动（原先让核心自选 profile，会选中匹配的 s16 profile 从而什么都没测到）。`widenedBytesAre…` 增加全长（336000 帧 / 1.3 MB → 2.7 MB）转换断言，覆盖缓冲区大小与直接缓冲属性。

实际命令／设备／证据：

- 主机核心：`cmake --build native/build-host` exit0；`yy_core_tests.exe` → **checks=6339 failures=0 RESULT OK**。真机：重新交叉编译 arm64-v8a 后推送 `/data/local/tmp/yy_core_tests` → **同样是 checks=6339 failures=0**，两侧同一套断言。
- 宿主单测：`:app:testDebugUnitTest` BUILD SUCCESSFUL（28 项）。
- 真机：`AudioOutputDeviceTest` + `AudioOutputSinkDeviceTest` → **OK (18 tests)**，连续 2 次全绿；`AudioOutputSinkDeviceTest` 单独 **OK (11 tests)**。
- 三种路由同一套证据（均 336000 帧 = 7 s 48 kHz 双声道）：
  - USB DAC `s24_3`：`platform=s24_3/48000Hz matches=true substituted=false fed=336000 dropped=0 consumed=336000`，路由 `USB-Audio - lifeme HiFi Audio Pro`。
  - USB DAC `s32`：`platform=s32/48000Hz matches=true`，同样全部送达。
  - 蓝牙 A2DP：`platform=s16/48000Hz`，`usb=false`，336000 帧送达，`systemReportedBitPerfect=false`，路由 `OPPO Enco Free4`。
  - 内置扬声器：`usb=false`，336000 帧送达，`systemReportedBitPerfect=false`，路由 `2509FPN0BC`。
  - `s24_in32`：`started=false reason=sink_bad_format`。
- 加宽字节由设备侧断言锁定：`−12345 → s24_3 [00 C7 CF]`、`s24_in32 [00 C7 CF FF]`、`s32 [00 00 C7 CF]`，与 `−12345 << 8/8/16` 一致且符号扩展正确。

未执行检查／已知问题：

- **端到端位准确（`capture_verified`）仍未取得**：无数字捕获设备；`digestHex` 已作为比对基准记录。
- **`system_reported` 级位完美仍取不到**：本机 USB DAC 无任何 BIT_PERFECT 组合（H02 已证），与位深无关。
- **96 kHz 及以上高采样率实机输出未验**：本轮先在 96 kHz 上跑加宽用例，测试进程因反复渲染多兆字节缓冲陷入 GC 风暴而挂住（无 ANR，持续 sticky GC），因此把用例固定在 48 kHz。加宽是位深操作、与采样率无关，但高采样率输出本身仍未验。
- **`s24_in32` → `s32` 的显式映射未接入策略**：核心支持，调用方尚未使用。
- Oboe/AAudio 独占与实际 `SharingMode`、插拔与焦点策略、Android 17 后台音频收紧：均未验。
- `duringSignal` 是泵块内节拍余量（每轮最多交一个缓冲块 = 4800 帧 ≈ 100 ms），不是丢数据（写入与取走都是 336000，平台 xrun=0）；断言写成“不得被整整一个块饿到”。
- 实体机上 Compose UI instrumentation 仍会挂起（U08 既有问题）；UI 取证改用 adb + `uiautomator dump` + 截图。
- 本轮两次撞到 Gradle 增量缓存把新测试类判为 `UP-TO-DATE` 而没编译进测试 APK（交接文档第 5 节第 2 条），均以 `--rerun-tasks` 解决。

下一具体动作（H04 建议顺序）：

1. **高采样率取证**：在 96 kHz 上跑同一自检（先让测试按需生成、用完释放，避免 GC 风暴），确认 `AudioTrack` 与 HAL 的实际采样率路径与投递计数。
2. **`s24_in32` 源接入**：解码器若产出 24-in-32，由策略显式决定加宽到 `s32` 再打开（核心已支持无损加宽）。
3. **Oboe/AAudio 独占**（ADR0006 退出条件）：用 `AAudioStream_getSharingMode` 读回实际共享模式；AudioTrack 路径不得据此声称独占。
4. **换一台声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT` 的 USB DAC** 跑同一自检即可取 `system_reported`；随后数字捕获比对 `digestHex` 争取 `capture_verified`。
5. **把决策格式接到真实播放**：S06 的 FFmpeg 解码 → `PcmSink`，替换 Media3 共享输出（ADR0001 退出条件）。不自动提交／推送。

## 2026-10-06：H02 核心 PCM 环接到真实输出 sink（USB DAC 实机取证）（前轮记录）

状态：**H02 已实现并在真机上取得 USB DAC 输出证据；该 DAC 不提供 BIT_PERFECT 组合，`system_reported` 级位完美仍记为未验；端到端位准确无数字捕获，未达成。**（H03 的位深加宽与非 USB 对照见上一条。） 分支 `dev-hifi`。设备：小米 2509FPN0BC（Wi-Fi adb `192.168.114.116:33077`，Android 17／SDK 37，HyperOS OS4.0.0.44），**同时接入 USB DAC「Synaptics lifeme HiFi Audio Pro」与蓝牙「OPPO Enco Free4」**。完整证据见 [docs/validation/hifi-v1/README.md](validation/hifi-v1/README.md)（含截图、instrumentation 输出与 logcat 原文）。

任务／文件／实际行为：

- H02.1 输出 sink 边界（新增 `audio/PcmSink.kt`）：定义“核心拥有环、sink 只拉取”的方向——sink 拿到环句柄后在自己的线程取帧，绝不接收样本数据本身，这样慢的输出只会让生产者丢帧计数，而不会阻塞解码。同时定义 `SinkFailure`（稳定原因码）、`SinkRouteReport`（播放中回读结果）与 `YY_AUDIO_TAG`。
- H02.2 系统 AudioTrack sink（新增 `audio/AudioTrackPcmSink.kt`）：用**决策给出的确切格式**打开输出，`MODE_STREAM` + `PERFORMANCE_MODE_NONE`（低延迟会走 fast mixer 重采样/混音，与“决策格式必须原样到设备”冲突）；泵线程按平台可接纳量从核心环 `ringRead`，环空时补**全零静音**并同时在自己的计数器和核心的欠载计数里记账；写不满一次调用时只推进整帧，避免声道交织错位（`partialWriteFrames` 记录该情况从未发生）。自己的 `PcmSink.framesConsumed` 与核心 `ringStats` 的 `read_frames` 必须一致。
- H02.3 决策真正作用到播放（`audio/OutputSelfTest.kt` 新增）：把“核心决定格式 → 打开 sink → 请求混音器契约 → 播放中回读”串成一次可复核的运行。契约请求用与 AudioTrack **同一个** `AudioAttributes` 实例（平台按属性+格式挂载到 preferred mixer），`boolean` 返回值只当请求结果，只有 `getPreferredMixerAttributes` 回读值算证据。`digestHex` 记录进入环的字节的 SHA-256，作为将来数字捕获的比对基准。
- H02.4 `AudioOutputController`：新增 `runOutputSelfTest()`（唯一会用 native 通路出声的入口，且只由显式用户操作触发）。求值路由与实际打开路由必须是同一个设备，否则回读描述的就不是决策的那个输出；`publishRoutedDevice()` 只把平台回读写进状态。
- H02.5 界面（`ui/AudioOutputScreen.kt` 新增“输出自检”分组 + `PlayerHostActivity` 接线）：一行“用当前决策格式试听 → 开始”，下面是位完美契约、播放中实际路由、实际输出格式、样本投递（写入/丢弃/取走/信号期间欠载/平台 xrun）与参考信号摘要。全部为回读事实，未请求或未回读的项显示“不可用／未回读”。
- H02.6 参考信号与喂入（新增 `audio/AudioVerificationTone.kt`）：定点整数正弦（0.16 相位 + 四分之一波表），48 kHz 双声道 s16，计划为 2 s 静音 → 左 440 Hz → 右 880 Hz → 双声道 → 2 s 静音（共 7 s = 336000 帧）。开头静音是有意的：先开流并配置混音器，再出声。`PcmRingFeeder` 按**采样率节拍**投喂（否则 1 秒长的环会被瞬间灌满并全部丢弃），摘要只覆盖环真正接受的字节。样本不在此处转换：s16 用核心的 `convertPcm` 无损加宽到目标编码。
- H02.7 修掉 H01 的真实缺陷（`native/jni/yy_jni.cpp`）：7 个环/转换入口的导出名缺少 `_00024Bridge_`。Kotlin 把嵌套 `Bridge` 编译成独立顶层类且**全部 10 个 native 声明都在它上面**（`javap` 证据见验证文档），所以 `createRing`／`ringWrite`／`ringRead`／`ringClear`／`ringStats`／`releaseRing`／`convertPcm` **从未解析成功过**：`System.loadLibrary` 照常成功，仅调用抛 `UnsatisfiedLinkError`，被 Kotlin 包装吞成“原生不可用”。H01 的 7 项真机测试没有一项触及环，因此当时不可见。现改为显式 `RegisterNatives` 表：签名或宿主类不匹配会使 `JNI_OnLoad` 失败、库装载失败，而不是某个功能静默变成“此设备不支持”。桥版本升到 2（新增 `nativeIsPristineConversion`，核心函数原本就有，仅补 JNI 入口）。
- H02.8 新增宿主契约测试（`app/src/test/.../NativeAudioCoreContractTest.kt`，4 项）：用反射（不初始化类，因为 `Bridge` 初始化会装载 native 库）锁定 10 个声明都在 `Bridge`、外层 `NativeAudioCore` 无 native 方法、`EXPECTED_VERSION=2`、环入口只用 DirectByteBuffer 与标量。同类名字错误今后不需要真机就能发现。

实际命令／设备／证据：

- 主机核心测试：`cmake --build native/build-host` exit0；`yy_core_tests.exe` → `checks=2345 failures=0`、`RESULT OK`（H02 未改核心逻辑，断言数不变；H03 修正加宽语义后为 **6339**）。
- 宿主单测：`:app:testDebugUnitTest` → **28 项 0 失败**（新增 4 项 JNI 契约）。
- 构建：`:app:assembleDebug :app:assembleDebugAndroidTest` BUILD SUCCESSFUL；`llvm-nm -D` 确认 10 个导出全部带 `_00024Bridge_`，且 `JNI_OnLoad` 存在。
- 真机 instrumentation：`AudioOutputDeviceTest` + `AudioOutputSinkDeviceTest` → **OK (13 tests)**，H01 的 7 项无回归；`AudioOutputSinkDeviceTest` 连续 4 次全绿。
- 关键实测（`h02_logcat_audio.txt` 原文）：决策 `OutputFormat(48000, 2, s16, mask 3)`、`bitPerfectRequested=false`；`AudioTrack` 实际打开 `48000 Hz / s16 / mask 0x3 / frameCount 7688`；`framesFed=336000 dropped=0 consumed=336000`；`getRoutedDevices()` = **id 7927「USB-Audio - lifeme HiFi Audio Pro」**、`routedIsUsb=true`；平台 `getUnderrunCount()=0`；`digestSha256=cdf7b526…6e573eb0`。
- **该 USB DAC 无任何 BIT_PERFECT 组合**：`getSupportedMixerAttributes` 返回 42 个组合（16000/32000/44100/48000/96000/192000/384000 Hz × s16/s24_3/s32 × mask 12/0），全部 `MIXER_BEHAVIOR_DEFAULT`。因此核心不请求契约，界面显示“不可用 / 该设备组合没有系统位完美契约”。这是本轮最重要的硬件事实：位完美资格由厂商 HAL 是否声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT` 决定，不是应用能争取的。
- **`setPreferredDevice` 返回值不可作为路由证据**：小米上返回 `false` 而平台仍路由到该设备（`pinAccepted=false, routeMatchesRequest=true`）。已记录该字段但路由一律以回读为准。
- 界面取证（真机 adb 点击 + `uiautomator dump` + 截图，见 `docs/validation/hifi-v1/shots/`）：设置页音频输出行显示 `不可用`；输出页位准确状态 `不可用`；USB 设备行“USB 耳机 · … · 无位完美组合”；自检进行中显示“正在用决策格式打开输出并回读系统报告…”；完成后显示“已用决策格式打开输出并完成一次参考信号输出”、路由 `USB-Audio - lifeme HiFi Audio Pro`（USB）、`48.0 kHz · 16 bit · 缓冲 7688 帧`、`与决策一致`。界面与 instrumentation 是同一次决策的两条路径，两者一致。

未执行检查／已知问题：

- **端到端位准确（`capture_verified`）未达成**：需要外接数字捕获并与 `digestHex` 比对，本机无捕获设备。
- **`system_reported` 级位完美未验**：本机 USB DAC 没有 BIT_PERFECT 组合，**无法在此设备上取到该级别证据**。代码路径（请求 + 回读 + 判定）已就绪，换一台声明该 flag 的 DAC 即可取证。
- **24/32 bit 实际输出未验**：本轮源与决策格式都是 s16。无损加宽路径（s16→s24_3/s24_in32/s32）已由宿主契约测试与真机用例锁定，但没有用真实播放验证过 24/32 bit 到 DAC。
- **Oboe/AAudio 独占与实际 `SharingMode` 未实现**（H02 按交接建议只做系统 AudioTrack 共享路径）。
- 蓝牙输出、插拔与焦点策略、Android 17 后台音频收紧：均未验。蓝牙设备在列表中且被正确标为“无位完美组合”，但未做播放取证。
- `duringSignal=130` 帧（2.7 ms）：泵每轮最多交一个缓冲块，节拍落后时该块余量补静音。不是丢数据（写入/取走都是 336000，平台 xrun=0），测试断言写成“不得被整整一个块饿到”，而不是依赖调度器的 0。
- 小米会把**应用自身**的 `android.util.Log` 从 logcat 过滤掉（同一次运行里 `AudioTrack`／`AudioPolicyManager` 等系统日志正常），因此应用侧证据以界面回读与 instrumentation 输出为准。
- 实体机上 Compose UI instrumentation 仍会挂起（U08 既有问题）；本轮 UI 取证改用 adb 点击 + `uiautomator dump` + 截图。

下一具体动作（H03 建议顺序）：

1. 24/32 bit 取证：用 96 kHz / s24_3（或 s32）源跑同一自检，确认 `convertPcm` 无损加宽后的实际输出格式与投递计数，并把该组合加入设备测试。
2. 蓝牙与内置输出的对照取证：同一条自检在 A2DP 上必须保持 `unavailable` 并给出原因，补齐“非 USB 不得报告位完美”的真机证据。
3. Oboe/AAudio 独占路径（按 ADR0006 退出条件），用 `AAudioStream_getSharingMode` 读回实际共享模式；AudioTrack 路径不得据此声称独占。
4. 若可换一台 HAL 声明 `AUDIO_OUTPUT_FLAG_BIT_PERFECT` 的 USB DAC，跑同一自检即可取 `system_reported`；随后做数字捕获比对 `digestHex` 争取 `capture_verified`。
5. 把决策格式接到真实播放：S06 的 FFmpeg 解码 → `PcmSink`，替换 Media3 的共享输出（ADR0001 退出条件）。不自动提交／推送。

## 2026-10-05：H01 HiFi 输出基础——可移植 C++ 核心 + Android 输出策略与设备选择（前轮记录）

状态：**H01 已实现并在真机取得证据；位完美（BIT_PERFECT）与独占的硬件资格未验（H02 已在接 USB DAC 的实机上补测，见上）。** 分支 `dev-hifi`（由 master `836a920` 建立）。当时设备为小米 17 Pro Max（`ac07514c`、Android 17／SDK 37、HyperOS OS4.0），但**没有连接 USB DAC 或蓝牙音频设备**，因此任何 `system_reported` 级别的位完美都无法取证；本轮把这一点如实记为未验，没有伪造状态。设计决策见 [ADR0006](adr/0006-hifi-output-core-android-split.md)，官方 API 事实见 [USB_DAC_BITPERFECT_API_FACTS](research/USB_DAC_BITPERFECT_API_FACTS.md)。**H02 更正：当时记录的“导出符号与 Kotlin 声明一致”只核对了 `native*` 两个函数，7 个环/转换入口的 `_00024Bridge_` 缺失未被发现。**

任务／文件／实际行为：

- H01.1 Git 保存点：dev-api 的 U06/U07/U08 改动提交为 `4fff9f1`，以 `--no-ff` 合并入 master `836a920`，再由 dev-api 建立 `dev-hifi`。
- H01.2 可移植核心（`native/core`）：C++17 静态库，无 Android/JNI/mpv 类型。`format`（PCM 格式、字节/帧运算与 64 位溢出检查）、`pcm`（只做不丢有效位的原样转换：整数加宽、24-packed 与 24-in-32 互转；丢位或经 float 一律拒绝）、`ring`（无锁单生产/单消费 PCM 环，区分丢弃与欠载计数）、`policy`（sharing 与 transparency 的 AND 判定，拒绝必带原因码）、`json`（控制桥用的点路径 JSON 读写）。
- H01.3 JNI 桥（`native/jni/yy_jni.cpp`）：控制面 JSON、PCM 走 DirectByteBuffer 与预分配环；句柄经注册表校验，旧句柄返回失败而不是解引用；桥内不抛异常、不做 JNI 回调。
- H01.4 平台层（`app/.../audio`）：`AudioDeviceProbe` 枚举输出设备并读取 API 34+ 混音器属性（API 34 引用全部隔离在嵌套对象内，API 26–33 不会加载新类）；`AudioOutputController` 按“核心决定 → 平台请求 → 只发布平台回读事实”的顺序工作；`NativeAudioCore` 是唯一 JNI 入口，其请求/响应翻译是纯函数，可在主机单测里锁定线格式。
- H01.5 输出策略与设备选择 UI：新增 `AudioOutputActivity`／`AudioOutputScreen`，可设置“流独占／样本原样”策略、在软件内选择输出设备（或跟随系统）、查看实际流共享、位准确状态、实际输出格式与路由设备。设置页“播放”分组新增“音频输出”入口，媒体信息弹窗按实际观测显示音频输出路径。
- H01.6 权限与清单：新增 `MODIFY_AUDIO_SETTINGS`（`AudioManager.setPreferredMixerAttributes` 必需，属普通权限，安装即授予）；注册 `AudioOutputActivity`（未导出，只能从应用内进入）。
- H01.7 Gradle：接入 `externalNativeBuild`（NDK 28.2.13676358 + CMake 3.22.1），ABI 限 `arm64-v8a` 与 `x86_64`；`app/testOptions.unitTests.isReturnDefaultValues = true` 供主机单测运行含 `android.util.Log` 的代码。

实际命令／设备／证据：

- 主机核心测试：`cmake -S native -B native/build-host -G Ninja ... && cmake --build native/build-host` exit0；`yy_core_tests.exe` 输出 `checks=2345 failures=0`、`RESULT OK`；`ctest` 1/1 Passed。
- 同一二进制在真机运行：`adb push native/build-android-arm64-v8a/yy_core_tests /data/local/tmp/` → `checks=2345 failures=0`、`RESULT OK`（arm64-v8a 与 x86_64 交叉编译均成功）。
- App 构建：`:app:assembleDebug :app:assembleDebugAndroidTest` BUILD SUCCESSFUL；APK 内含 `lib/arm64-v8a/libyyplayer_jni.so` 与 `lib/x86_64/libyyplayer_jni.so`（`llvm-nm` 校验导出符号与 Kotlin 声明一致）。
- 主机单测：`AudioBridgeJsonTest`、`AudioPolicyWireTest` 锁定请求/响应字段名与枚举拼写，`:app:testDebugUnitTest` 24 项 0 失败。
- 真机 instrumentation：`AudioOutputDeviceTest` **OK (7 tests)**，覆盖原生核心装载与版本（`YYPLAYER_CORE_VERSION=1`）、无契约路由拒绝严格原样、软件音量拒绝严格原样、系统 AudioTrack 路径拒绝严格独占、设备能力记录（非 USB 输出不得报告位完美组合）。
- 设备能力实测（无 USB DAC）：系统只报告内置输出；`getSupportedMixerAttributes` 无 USB 设备可查，故本机不存在任何 `system_reported` 位完美证据。

未执行／已知边界：**没有 USB DAC 与蓝牙设备，BIT_PERFECT 契约、Oboe 独占、真实独占回读、数字捕获、耳机/USB 插拔策略全部未验**；本轮也**没有**把 Media3/ExoPlayer 的 PCM 换成核心输出通路（S06 的 FFmpeg 解码 + AudioTrack/Oboe sink 仍是后续工作），因此“位完美状态”目前只能显示 `unavailable`／`requested`，播放本身仍走系统共享输出。Android 17 后台音频收紧（后台播放与音量 API 静默失败）尚未专项验证。输出页 UI 只做了编译与控制器级验证，未在锁屏/无人值守条件下完成像素级目检（小米运行申请弹窗曾遮挡设备）。

下一具体动作：H02 接入 S06 的 PCM 输出通路（FFmpeg 解码或先把核心环接到 AudioTrack sink），使策略决定真正作用到播放；随后 H03 在实体 USB DAC 上取证 `getSupportedMixerAttributes`／`setPreferredMixerAttributes`／`getRoutedDevices` 的真实结果，并按 ADR0006 的退出条件推进 S08（Oboe 独占）。不自动提交／推送。

## 2026-10-05：U08 媒体库分页与页头视觉（前轮记录）

状态：**用户已复核通过并明确表示满意（第二轮结果）。** 首个实现把标题/页头/分页整片横移，并按固定时间轴切换文字，用户实测指出“滑动时标题不收起、图标和标题闪一下”；本轮改为：页头（标题＋右侧图标＋TabRow）固定在 pager 之外，只有 TabRow 下方的内容左右切换；标题与图标透明度直接跟随 pager 偏移量，换字发生在两页交界、透明度最低处；滚动重新把大标题收成栏内小标题。用户确认 TabRow／卡片收窄与淡入淡出效果符合预期。仍在 dev-api 工作区，未自动提交／推送；未修改桌面 Rust 工程。交互规格见 [UI_SETUP_AND_LIBRARY](UI_SETUP_AND_LIBRARY.md)。

任务／文件／实际行为：

- U08.1 页头提升：[YYPlayerApp](`../app/src/main/java/yy/daniel/yyplayerforandroid/ui/YYPlayerApp.kt`) 把 `HyperPage`（标题、图标、TabRow）提到 `HorizontalPager` 之外，pager 只承载内容；音乐/视频各自持有 `LibraryPageController`、`LazyListState`、`LazyGridState`，切换后回到原滚动位置。宽横屏仍走侧栏、不显示 TabRow。
- U08.2 跟手换字：[HyperDesign](`../app/src/main/java/yy/daniel/yyplayerforandroid/ui/HyperDesign.kt`) 的 `HyperPage` 新增 `headerAlpha: () -> Float`（延迟读取，只在顶栏子组合里读状态）与 `tabs` 槽（Miuix `TopAppBar.bottomContent`）。标题与图标透明度＝`1 - |currentPageOffsetFraction| * 2`，与手指/弹簧动画同步；减少动画时恒为 1，文字随页翻转即时切换。
- U08.3 收窄：TabRow 48→40dp（`LibraryTabs` 保留 8dp 垂直外边距），“全部音乐／收藏”卡片竖向内边距 22→13dp、横向 18→16dp。实机像素（density 3.0）TabRow 容器 144→120px、卡片 260→206px。
- U08.4 收起标题修复：首轮把 `MiuixScrollBehavior`/`TopAppBarState` 提到 `YYPlayerApp` 共享，实机滚动后大标题淡出但栏内小标题不再出现（设置页自持 scroll behavior 正常）。本轮去掉共享，`HyperPage` 自持 scroll behavior，滚动重新把大标题收成栏内小标题。
- U08.5 拆分：[LibraryScreens](`../app/src/main/java/yy/daniel/yyplayerforandroid/ui/LibraryScreens.kt`) 由单个 `LibraryScreen`（内部自带 `HyperPage`）拆成 `LibraryTabs`／`LibraryHeaderActions`／`LibraryPageController`（多选状态，带 Saver）／`LibraryContent`（仅滚动内容与对话框），使页头与内容可分别放在 pager 内外。
- U08.6 可见状态：首轮页头 4 个图标曾因 `Box` 堆叠而只显示 1 个，已改为 `Row` 后实机复核 4 个图标正常。

实际命令／设备／证据：

- 普通四任务 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` exit0、BUILD SUCCESSFUL、JVM **16 项 0 失败／0 错误**、lint **No issues found**，见 [checks-normal](validation/ui-v8/checks-normal.txt)；首轮两个失败日志（Receiver 作用域、测试尾随 lambda）保留在 [checks-normal-failed-2](validation/ui-v8/checks-normal-failed-2.txt)，未当通过。
- 设备：用户实体小米 2509FPN0BC（Android 16／targetSdk37，1200×2608、density 480、竖屏），`adb install -r` 保留 9 首音乐与收藏。改动前后同为该机截图：用户原图 [user-home-reference](validation/ui-v8/user-home-reference.jpg)、[改动后首页](validation/ui-v8/after-home.png)、[滚动收起](validation/ui-v8/after-scroll.png)、[切换中间帧](validation/ui-v8/after-tab-midfade.png)、[视频页](validation/ui-v8/after-tab-video.png)。
- 像素核对（同机、同密度）：TabRow 外框 463–606px→463–582px（48→40dp）；“全部音乐／收藏”卡片 667–926px→643–848px（86.7→68.7dp，−20.7%）；卡片与 TabRow 间距保持 20dp。
- 实机目检：滚动后大标题淡出、栏内居中显示“音乐/视频”小标题、TabRow 常驻；点“视频”后标题、图标、TabRow 选中态、内容同时切到视频页，歌单/视频列表各自保留滚动位置。约 0.1s 中间帧确认标题与图标处于半透明过渡态（跟随偏移量而非时间轴闪烁）。
- 用户复核：2026-10-05 用户在本机确认本轮结果满意，未再提出视觉或交互修改；本轮结论以此为验收，后续改动需重新取证。

未执行／已知问题：**本轮新增的 `LibraryTabsUiTest` 与改写后的 `LibraryManagementUiTest` 只完成编译（`assembleDebugAndroidTest` exit0），未在该实体机上跑通**：`am instrument` 先报 `Process crashed`，单独跑既有 `LibraryManagementUiTest` 亦长时间无输出（同一 APK 此前在 MuMu 上通过），未取得崩溃栈（MIUI `ProcessManager: error write exception log`），因此本轮的界面结论只以实机截图与像素为准，不声称自动化通过。API26–32 玻璃回退、200% 字体／TalkBack、横屏侧栏未专项复验。

下一具体动作：U08 已按用户确认收尾，当前无待改视觉项。可选的后续顺序：(a) 排查实体机 Compose instrumentation 崩溃或改回 MuMu 跑通 `LibraryTabsUiTest`／`LibraryManagementUiTest`，补回自动化证据；(b) 补 API26–32 磨砂回退、200% 字体／TalkBack、横屏侧栏专项；(c) 用户给出新指令后进入 S02 native 依赖与 JNI/Surface gate。不自动提交／发布，不擅自跳 S02。

## 2026-10-05：U07 首次引导、媒体库与音乐控件（前轮记录）

状态：**七项需求已实现并完成 MuMu 功能／UI 验收；普通最终包已 install -r 更新并正常无 extra 启动。** 在 dev-api 工作区继续，未自动提交／推送；master 80538db 基线保持，未修改桌面 Rust 工程。旧底部纯图标导航规则按本轮用户指令替换，其他 U04 弹窗缩进、U05 同页音乐和 U06 API／下载保留。交互规格见 [UI_SETUP_AND_LIBRARY](UI_SETUP_AND_LIBRARY.md)。

任务／文件／实际行为：

- U07.1 SetupRepository／SetupScreen／YYPlayerApp／PlayerHostActivity／SettingsScreen：独立 setup_v1 首次标记，欢迎→SAF歌曲／视频／目录→两个联网选项及JSON导入→主题／强调色／减少动画→完成。授权与联网均可选；新用户默认联网关闭。已有偏好或库迁移为完成，不重置数据；设置“使用引导”可重入。步骤在Android重建保存；完全结束后未完成引导从欢迎页开始，已作选择保留。后台扫描／标签读取与配置导入继续用真实仓库。ThemePreview整块含文字可点，手动发现标签原本不响应后修复并添加明暗切换设备验证。
- U07.2 LibraryScreens／YYPlayerApp／HyperDesign：竖屏页头 TabRowWithContour 切换音视频，选中与轮廓明确；本地搜索／在线云图标／设置／添加移到页头。宽横屏保留侧栏。移除原 FloatingLibraryNavigation；底部只放当前媒体播放条。
- U07.3 HyperDesign／既有 API33 blur facade：整条 CircleShape 磨砂／玻璃描边与 SinkFeedback，文字打开当前详情；播放／暂停和下一首直接控制唯一 Media3。进度为播放键外围环，依据真实position／duration并保留无障碍seek；没有底置线性条或假时钟。API26–32不透明回退，普通UI不暴露blur类型。
- U07.4–5 MusicPlayerScreen／MediaArtwork：单个稳定圆角背景／shadow壳保持，等待新封面解析后只交叉淡入图片；缓存命中首帧不闪占位卡。TiltFeedback真实按压倾斜，松手点按以500ms进入歌词；横屏将歌词展开。减少动画与系统动画关闭时禁用倾斜／下沉并即时切页。
- U07.6 LyricStyle／LyricStyleScreen／PlayerApplication／PlayerHostActivity／Manifest／MusicPlayerScreen：新增原生“歌词显示”子页；选中和非选中各自字号14–48sp、字重400–900六档、自动色／预设色／6或8位HEX，全部行额外间距0–40dp与实时预览／恢复默认。version1持久化、数值类型和范围／有限数校验，坏值备份回退；LRC和纯文本均应用样式，纯文本拆行在Default后台，不逐行重复解析。补齐Miuix滑杆SetProgress未触发finish时的保存路径。
- U07.7 LibraryScreens／YYPlayerApp／PlayerApplication／MediaLibraryRepository：长按进入可保存的多选集，勾选／全选当前列表／退出，批量播放所选／收藏／取消收藏／确认后移除；更多菜单提供多选入口。库移除一次写入、取消旧标签任务，不删除原文件，也不更改正在播放的队列。真实批量播放按库顺序只建立所选两首队列。
- 测试／交接：LyricStyleTest、SetupPreferencesTest、OnboardingUiTest、LyricStyleUiTest、LibraryManagementUiTest、MusicExperienceUiTest；README、技术设计、实施计划、AGENTS和本记录。新增/.kotlin/忽略编译缓存，无清理用户源码。

实际命令／设备／证据：

- QA完整 `.\scripts\Test-MumuUi.ps1 -OnlineQa -DownloadTree 'content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYYPlayerOnlineQA'` exit0，**OK (33 tests)**／13.443s；此前真实 HTTPS、GD五档供应／降档、签名FLAC、Music／SAF、封面歌词与原视频手势均回归，新U07交互一起执行，无网络skip。见 [checks-qa-final](validation/ui-v7/checks-qa-final.txt)。此后只补ThemePreview文字点击和隔离测试清理，应用最终版本继续如下普通检查。
- 普通最终四任务 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` exit0、BUILD SUCCESSFUL／41s，JVM **16项0失败／0错误**，lint **No issues found**。见 [checks-normal-final](validation/ui-v7/checks-normal-final.txt)。普通主／测试APK均install -r成功，直接instrumentation **OK (33 tests)**／14.981s，其中**27项实际通过、6项仅QA HTTPS网络用例按条件跳过**，未冒充最终普通包网络联调；见 [device-normal-final](validation/ui-v7/device-normal-final.txt)。没有默认connectedDebugAndroidTest卸载。
- 隔离偏好测试的apply异步写入会在delete之后重建文件，已改commit排空后delete；最后只有测试清理变化，assembleDebugAndroidTest／lint exit0，直接隔离4项 **OK (4 tests)**／0.036s，[test-cleanup-checks](validation/ui-v7/test-cleanup-checks.txt)／[device-cleanup](validation/ui-v7/device-cleanup.txt)，确认u07-test残留0。最终主／测试APK SHA256、截图hash、单测数量与普通包online_qa资源**0**见 [artifacts](validation/ui-v7/artifacts.json)。本轮localhost HTTPS进程已停止、18443 adb reverse已移除。
- 设备：已有MuMu实例0，Android15/API35、2304FPN6DC、ADB127.0.0.1:16384；竖屏1080×1920、横屏1920×1080。测试为应用私有30秒44.1kHz双声道16bitWAV与原创500×500几何PNG；线上回归是自有12秒HTTPS样例；手动使用已授权原创“落日之后／微光漂浮”FLAC与LRC。没有播放／下载用户“铁花飞”，没有个人视频帧。MuMu UIAutomator曾在退出线程SIGSEGV，应用进程仍正常；只在成功写出新层级后读取，并使用实际截图／Compose instrumentation继续，不把工具崩溃当应用成功证据。
- 真实按压像素验证 Tilt 与整个Sink；记录切歌150／300／450ms和完成帧，壳尺寸不变且图像中心未变灰占位，圆角／阴影目检正常：[150ms](validation/ui-v7/cover-next-150ms.png)、[300ms](validation/ui-v7/cover-next-300ms.png)、[450ms](validation/ui-v7/cover-next-450ms.png)、[完成](validation/ui-v7/cover-next-complete.png)、[倾斜](validation/ui-v7/cover-tilt-pressed.png)、[环形进度](validation/ui-v7/mini-player-ring.png)、[整体下沉](validation/ui-v7/mini-player-sink-pressed.png)。早期测试误用固定触控外壳bounds推断倾斜，已改渲染像素；颜色测试先前点到横屏弹窗不可见行，已滚动后点选；早期失败日志保留，未当通过。
- 手动UI：[欢迎](validation/ui-v7/setup-welcome.png)、[可选文件授权](validation/ui-v7/setup-files.png)、[两个联网选项](validation/ui-v7/setup-online.png)、[SAF真实JSON导入成功](validation/ui-v7/setup-api-imported.png)、[主题文字点击切换浅色](validation/ui-v7/setup-theme.png)、[完成](validation/ui-v7/setup-complete.png)；系统目录选择取消不影响继续，JSON使用独立自有u07文件。首次空状态条件用隔离偏好测试，设备UI用真实“使用引导”重入，没有卸载／清数据模拟首次。
- [浅色顶部TabRow](validation/ui-v7/library-tabs-light.png)、[两首多选](validation/ui-v7/music-batch-selection.png)、[真实仅两首队列](validation/ui-v7/batch-real-queue.png)、[实际播放胶囊](validation/ui-v7/library-mini-player.png)、[竖屏封面](validation/ui-v7/music-cover-portrait.png)、[歌词样式预览](validation/ui-v7/lyric-style-custom.png)、[应用于真实同步歌词](validation/ui-v7/lyrics-custom-real.png)。实际拖动选中37sp／非选中23sp、选择800／400、选中青绿，关闭子页重进仍保留；随后恢复原样式和主题。
- 收尾：仅恢复手动验收修改的主题／样式与API副本；首次SD卡中转被Android权限拒绝，改为run-as私有文件stdin写入完成。原ui_preferences／local_media／online_api／music_lyrics逐键语义一致，7首音乐／2个视频／1收藏、两个原联网选择及原SAF下载目录保留；原没有私有配置仍无副本。临时u07导入文件删除，完成标记true；见 [数据保留校验](validation/ui-v7/data-preservation.json)。最终无qa_portrait／open_music extra正常启动，没有修改全局方向或动画设置。

未执行／边界：API26–32的真实类加载／玻璃回退、物理小米HyperOS效果、200%字体与完整TalkBack矩阵尚未专项验收；模拟器不等同私有HyperOS玻璃或真机输出资格。首次完整关闭后从欢迎页重入，偏好／授权保留，未承诺跨进程精确恢复向导步。多选移除仅媒体库管理，不支持删除磁盘文件或改标签。原native／USB／后台MediaSession未实现状态不变。

下一具体动作：用户在当前普通包复核首次引导与封面／播放条动效，提供实体设备视觉反馈；随后按实际反馈调整并补API26–32／大字体／TalkBack证据。不自动提交／发布或跳S02。


## 2026-10-05：U06.6–U06.9 在线下载体验优化（前轮记录）

状态：**四项体验改动已实现，并通过自有 HTTPS／MuMu 验收；普通最终构建／单测／lint通过，已install -r更新MuMu。** 本轮依据用户提供的 GD API 文档（2026-09-16 更新）实现，用户反馈第三方搜索已可用；没有把前轮403或本轮自有素材结果当成当前 GD 实际供给事实。仍在 dev-api 工作区，未自动提交／推送，master 基线80538db保持。未修改桌面Rust工程或用户已有下载文件。

任务／文件／行为：

- U06.6 ApiConfig／OnlineRepository／OnlineSearchScreen／assets/online-providers.json：GD补齐128、192、320、740（16bit）、999（24bit）五档和netease／joox／bilibili；可配置分档参数、分档变量／来源、曲目额外字段变量、静态变量、嵌套有类型JSON正文、pageSize、额外API主机及模板访问头。保留旧version1配置兼容，不自动覆盖私有导入副本。菜单结合曲目音质映射与来源限制，实际解析每个URL并核对服务返回档位；空地址／错误／已知回退置不可用，无报告则明确未确认。成功地址缓存60秒／128项上限，仍检查联网开关。GD窗口保守40次／5分钟；出处与用途写入示例。
- U06.7 DownloadTarget／OnlineRepository／PlayerHostActivity／SettingsScreen／MusicDocumentContracts／MediaLibraryRepository／PlaybackBackend：默认API29+写Music/ MediaStore音频集合，IS_PENDING保护未完成文件；设置可恢复默认或选择SAF持久读写目录，并显示当前路径。API26–28首用先授权，选择器初始位置Music，无全盘权限。自有MediaStore下载持久入库，重开不被SAF权限过滤丢弃；取消清理本次音频／已写旁文件，不触及原文件。
- U06.8 ApiConfig／OnlineRepository／OnlineSearchActivity／MediaArtwork／LocalMedia：可配置artwork操作、picId／直链映射与artworkSize。串流／下载自动获取，输入8MiB／8192边长校验、解码缩采样至1024，保存本地JPEG并显示封面取色背景。下载入库保留封面URI，默认Music封面与歌词保存在应用内关联；SAF另尽力保存同名JPG／LRC，不覆盖原旁文件。图获取失败不阻断歌曲。GD tlyric可按时间合并原文／翻译。
- U06.9 AudioFileFormat／DownloadTarget／OnlineRepository：前256KiB文件签名识别FLAC、MP3、WAV、M4A、OGG／Opus、AAC、AIFF、APE，读取FLAC STREAMINFO位深；不依赖MIME／URL后缀／大小。未识别格式拒绝保存并清理。命名为“歌曲名 - 歌手.实际格式”，只清理文件名非法字符，无随机尾缀；重名才增加(2)序号。下载提示实际格式／可读取的位深，不把服务br等同源或设备输出资格。
- 验收／文档：AudioFileFormatTest、OnlineApiTest／OnlineUiTest、Serve-OnlineFixtures.py（真实FFmpeg编码自有FLAC、500×500原创几何封面）、ONLINE_API／ADR0005／README／TECHNICAL_DESIGN／IMPLEMENTATION_PLAN和此记录。

实际命令／证据：

- 首轮普通`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` exit0。新库／默认下载／封面实现编译成功。后续lint发现API29常量分支提示，改为显式SDK保护；最后KTX两处等价转换建议已修正，普通最终结果另列。失败或告警没有写成“无问题”。
- 最后`.\scripts\Test-MumuUi.ps1 -OnlineQa -DownloadTree 'content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYYPlayerOnlineQA'` exit0，**OK (21 tests)**／9.073s；JVM **14 tests，0 failures/errors**，包括新4项签名／命名／翻译测试。见[checks-qa-final](validation/online-v2/checks-qa-final.txt)，早期轮保留[checks-qa](validation/online-v2/checks-qa.txt)。APK与原创素材hash／测试总数见[artifacts](validation/online-v2/artifacts.json)。QA最后设备运行后只有两处Uri.parse等价KTX转换和cleanup格式整理，普通包继续完整编译与lint验证。
- 最终普通（无-PonlineQa）完整四任务 exit0，**BUILD SUCCESSFUL 1m11s**、JVM14项0失败、lint **No issues found**，见[checks-normal-final](validation/online-v2/checks-normal-final.txt)。主包／测试包均install -r成功，普通APK内online_qa资源数**0**，hash追加artifacts。正常无extra启动，停止本轮自有HTTPS进程并移除adb reverse。用户ui_preferences与online_api逐键语义校验一致，原本未导入私有配置仍无私有副本；没有残留QA配置或强制修改开关／目录。
- 真实自有HTTPS：保留特殊API／媒体头、跨主机重定向隔离、歌词独立、Media3 WAV播放／seek和原10项回归。新增999实际回退320菜单不可用；真实FLAC故意返回.mp3 URL与audio/mpeg，最终正确保存.flac／audio/flac／显示16bit；文件实际在Music/，IS_PENDING=0，同名第二次(2)且原字节保留。封面本地文件、歌词关联、下载入库及重新load均验证；SAF同名LRC／JPG与取消不残留、默认Music首块取消删除pending行也验证。测试所创建Music和SAF文件清理；没有操作用户《铁花飞》。
- 设备：已有MuMu索引0，Android15/API35、2304FPN6DC（本轮adb实际型号），ADB127.0.0.1:16384，截图1080×1920。全部install -r，无卸载或清库。自有12秒44.1kHz双声道16bit低电平WAV／FLAC、原创LRC与几何PNG，实际本机HTTPS传输。生成的CA／私钥只在忽略的app/build，普通包须确认不含QA信任资源。
- 手动UI：正常主页导航检查[目录选项](validation/online-v2/download-directory.png)、[完整五档及状态](validation/online-v2/five-qualities.png)、[真实串流封面与取色背景](validation/online-v2/online-cover.png)。检查临时使用自有配置，随后还原用户原API副本（原来未导入时继续内置）和ui_preferences／online_api原偏好，包括下载目录；不强制修改原开关／目录。已有7首音乐（包含用户铁花飞）和收藏仍在。只保留必要在线页面截图，无个人音视频帧。

未执行／已知边界：本轮未下载GD的授权商业曲目，也未逐个验证其动态来源、五档供应和地区限制；以用户所贴API文档实现，实际API降档／文件格式通过运行时核对。API26–28文件选择器／真机、大字体／TalkBack、后台下载和长时间下载仍待验。文件头识别不等于全文件解码校验或端到端位准确；罕见容器／超大前置ID3超过256KiB会拒绝，不能退回猜测.mp3。专辑图保存与展示，不写嵌入音频标签。默认Music的非媒体歌词在应用内，自选SAF才另写可移植旁文件。原有手工目录保持，旧导入配置需自行更新或重新导入新版示例。native／USB／后台MediaSession资格不变。

下一具体动作：用户在当前可用GD网络使用一首授权曲目复核五档返回、picId／lyricId；收集低版本和实体手机证据，按新的视觉反馈调整。无新指令不自动提交／发布或跳至S02。

## 2026-10-05：U06 dev-api 可配置在线音乐与歌词（前轮历史记录）

状态：**设计、实现及自有 HTTPS／MuMu UI 验收完成；GD 第三方服务联调待验，整体 Partial。** 用户本轮授权优先于旧 U05／S02 顺序。先将原有全部非忽略源码与资料提交到 master：`80538db chore: snapshot Android player U05 baseline`，再创建 `dev-api`；新增功能留在该分支工作区，未自动提交／推送。没有修改桌面 Rust 工程、清除应用数据或修改系统配置。

任务／文件：

- U06.1：`online/ApiConfig.kt`、`OnlineHttp.kt`、`OnlineRepository.kt`、`assets/online-providers.json`：version=1 JSON，多 provider／source、独立搜索／URL解析／下载／歌词操作，GET／POST、query／form／JSON、特殊访问头、JSON字段映射／text 返回、音质／超时／限流。设置用 SAF 导入，校验后原子替换，坏配置保留原配置；headers 配置在 noBackupFilesDir。规格见 [ONLINE_API](ONLINE_API.md)、[ADR0005](adr/0005-configurable-online-music.md)。参考 LX 官方自定义源的动作／音质分离，不执行或兼容其 JS 脚本。
- U06.2：`OnlineSearchActivity.kt`、`ui/OnlineSearchScreen.kt`、LibraryScreens／YYPlayerApp／HyperDesign：音乐库开启联网时提供独立在线搜索 Activity，初始留白、顶部搜索框／来源，真实结果与分页、Miuix播放／下载图标、WindowDialog 音质选项。`OnlineAudioDataSource.kt` 和现有 AndroidMedia3PlaybackBackend 使用同一引擎渐进式 HTTPS 串流／Range seek，访问头以实际媒体 URI 原子快照绑定，跨主机重定向不传原凭证。
- U06.3：PlayerApplication／SettingsScreen／PlayerHostActivity／MusicDocumentContracts／Manifest：联网歌曲、联网歌词独立默认关闭；关闭歌曲停止当前串流，两个入口分别隐藏，仓库直接调用也检查开关。下载经专用 SAF 持久读写目录、512MiB上限、临时文件／失败取消清理、完成重命名、不覆盖用户文件，并保存原 provider 元数据入库；目录可在设置重新选择。MediaLibraryRepository 合并后台标签更新，避免下载入库被旧结果覆盖。
- U06.4：MusicLyricsRepository／MusicPlayerScreen／MediaArtwork／MainActivity：当前歌名自动搜索并优先展示同名候选，也可手动改名查询／选择来源；选中保存并刷新 U05 歌词，换曲 generation 取消候选。在线串流／下载在歌词开关开启时自动取 lyricId；下载保存同名 LRC，应用内关联仍可离线使用。稳定 provider/source/id 身份避免临时 URL 更新丢歌词；自动获取保持手动／同目录优先级。修复 MainActivity 返回时替换启动 Intent 的生命周期问题、Android ICU 模板正则、系统 text/plain 追加 `.txt` 和无内嵌标签下载曲目的元数据回退。
- U06.5：OnlineApiTest／OnlineUiTest、scripts/Serve-OnlineFixtures.py／Test-MumuUi.ps1、README／TECHNICAL_DESIGN／IMPLEMENTATION_PLAN／AGENTS 与本条证据更新。

实际运行／验收：

- 普通首轮 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` exit0；设备首次运行发现 Android ICU 未转义右大括号导致进程崩溃，修复后验证。显式 QA 的网络XML曾因缺 includeSubdomains lint失败，已修复；UI返回曾因 MainActivity.setIntent 导致 ActivityScenario 清理失败，保留启动 Intent 后通过。失败轮均未当成功证据。
- 最后 `.\scripts\Test-MumuUi.ps1 -OnlineQa -DownloadTree 'content://com.android.externalstorage.documents/tree/primary%3ADownload%2FYYPlayerOnlineQA'` exit0；BUILD SUCCESSFUL 43s、JVM 10项0失败、直接 instrumentation **OK (18 tests)**／8.608s、lint **No issues found**，见 [checks-qa](validation/online-v1/checks-qa.txt)。包含实际 HTTPS 搜索／特殊API头／同名排序、音质请求参数与三个UI选项、歌词开关独立、跨主机凭证隔离、真实 WAV Media3播放／seek／关联网、真实持久 SAF音频与精确 `.lrc` 下载／字节校验、首块后取消不残留临时文件、坏配置保护、离线歌词、真实前端音质／导航／入口开关及原10项回归。测试只在专用上下文或还原设置后执行，测试下载文件自行清理。
- 普通无 `-PonlineQa` 完整检查 exit0、BUILD SUCCESSFUL 43s，见 [checks-normal](validation/online-v1/checks-normal.txt)；末尾补充元数据回退后普通 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` exit0、BUILD SUCCESSFUL 42s，见 [checks-normal-final](validation/online-v1/checks-normal-final.txt)，并在 MuMu实测离线页面。APK ZIP检查 `online_qa` 资源数0；SHA256与QA包／示例配置hash见 [artifacts](validation/online-v1/artifacts.json)。只为显式QA构建生成三天 localhost证书／私钥，均在 app/build忽略目录；正常包使用平台 TLS信任，未关闭证书验证或修改系统信任。
- 设备：已有 MuMu索引0，Android15/API35、V2364A、ADB127.0.0.1:16384，截图1080×1920。启动既有模拟器，`install -r` 更新；没有 UTP卸载或清库。素材为原创低电平12秒、双声道44.1kHz/16bit WAV及UTF-8 LRC；通过 localhost HTTPS和adb reverse 实际传输，不是 mock／假快照；测试服务已停止、转发已移除。
- 实际UI：系统文件选择器导入配置成功；[独立空搜索页](validation/online-v1/search-empty.png)、[真实结果](validation/online-v1/search-results.png)、[音质选择](validation/online-v1/download-quality.png)、[下载完成](validation/online-v1/download-complete.png)、[入库](validation/online-v1/download-library.png)、[当前歌名候选](validation/online-v1/lyrics-candidates.png)、[手动歌名候选](validation/online-v1/lyrics-manual-search.png)；点“获取”提示保存成功，返回显示真实歌词。最终正常包关闭两个开关后，UI层级音乐在线入口0／歌词联网入口0，[开关关闭](validation/online-v1/settings-disabled.png)，下载曲目实际本地播放、[离线同步歌词](validation/online-v1/lyrics-offline-normal.png)。已有4首音频／2个QA视频／1收藏保留，另增加2首本轮自有下载验收曲；早期旧 `.lrc.txt` 自有样例仍在专用QA目录，新版正确生成 `.lrc`。恢复两个开关为关闭，移除本轮临时QA API配置，安装普通Debug包，结束播放并正常无extra启动；未保存个人视频帧。

未执行／限制：GD官方API从当前网络实际返回403 Cloudflare挑战，无法读取其文档或获得成功搜索／URL／歌词响应；GD内置JSON仍为待核实示例，**不宣称已通过GD真实服务联调**。其他provider、POST／text服务的端到端联调、API26–32、200%字体／TalkBack、手机实机网络／OEM动效、长时间下载未专项验收。当前仅渐进式音频，不支持JS签名／OAuth刷新／HLS／DASH／DRM，下载依附当前页，后台MediaSession／service仍未新增。USB/native资格不变。

下一具体动作：在GD服务可达或提供另一份有效API配置时，用真实授权曲目逐项核对search字段、音质供应／返回码率、stream/download与lyricId，再补第三方服务证据；随后按用户视觉反馈完善在线搜索，并做低版本／大字体专项。未经新指令不跳回S02、自动提交或发布。

## 2026-10-05：U05 音乐标签、同页详情与歌词（历史记录）

状态：**本轮实现、构建和 MuMu 功能／UI 验收完成；物理设备动效与低版本专项待验。** 新版已安装在 MuMu；不宣称达到 HyperOS 私有动效或硬件输出资格。

任务：U05.1 歌曲名／歌手／专辑／独立时长；U05.2 MainActivity 内音乐覆盖层、封面色渐变与分层弹簧；U05.3 本地歌词、同步／seek／对齐／紧凑控件。用户本轮指令替代 U04 的后续 UI 优先级；视频与设置仍用原生 Activity，音乐例外见 ADR0004。桌面工程仅只读参考 lyrics.rs / music_assets.rs，没有修改其文件。

已实现：音乐标签持久化与旧库后台补齐、标签搜索；目录 SAF 授权和有界导入；同目录 LRC、手动关联、内嵌 ID3/Vorbis/Opus/MP4 标签；桌面 LRC 时间／offset／多时间戳／稳定重复语义与 UTF-8／UTF-16／GB18030；动态封面取色、静态多层光晕、可中断展开收起／页面跟随／歌词强调、系统 scale=0 和减少动画。真实播放进度只取 PlaybackBackend，不使用假计时或 mock 生产数据。

文件：PlaybackBackend / AndroidMedia3PlaybackBackend / MediaLibraryRepository / MusicDocuments / MusicLyricsRepository / Lyrics / EmbeddedLyrics；MediaArtwork / MusicDocumentContracts / MusicPlayerScreen / YYPlayerApp / HyperDesign / LibraryScreens / PlayerScreens；MainActivity / PlayerHostActivity / PlayerApplication；LyricsTest / MusicLyricsRepositoryTest / ActivityPlaybackTest；scripts/Generate-MusicUiFixtures.ps1 / Test-MumuUi.ps1、ADR0004。自动 EOF 后实际曲目索引与 mediaGeneration 已修正，旧歌词／松手事件不会套到新歌曲。

实际验证：

- 最终代码 `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` BUILD SUCCESSFUL（50s）；KTX 建议已修正，lint **No issues found**。最终 `./scripts/Test-MumuUi.ps1` exit 0，构建复核 BUILD SUCCESSFUL（10s），JVM 10 项、直接 Android instrumentation 10 项均 0 失败；设备输出 `OK (10 tests)` / Time 5.417s。记录见 [final-checks](validation/ui-v6/final-checks.txt)。设备回归新增真实 WAV 自动 EOF、主 Activity 音乐路由、手动关联失败保护、ID3/FLAC/MP4 UTF-8/UTF-16BE 标签，与原视频变换测试一起通过。
- MuMu 现有实例，ADB `127.0.0.1:16384`，Android15/API35、1080×1920。真实 SAF 导入，曲名／歌手／专辑／右侧时长、同目录 LRC、手动歌词、无歌词、左右对齐、点句 seek（暂停状态跳到 00:10）、细进度拖动（02:08）、末句居中阅读位置、跨页手势／切歌换色、收起返回／再次进入均实际操作。覆盖层空白顶部点击保持音乐页，没有点穿库设置。最终普通无 extra 启动为 1920×1080 横屏，已检查封面／歌词并排和控件，结束时暂停并收起音乐。最终库为 4 首音频／2 个 QA 视频／1 个收藏。图与原始动画录像见 [U05 验收说明](UI_MUSIC_EXPERIENCE.md)。
- 素材为脚本生成的自有低电平 FLAC/WAV、原创封面和 LRC；实际播放不使用 mock 或假进度。素材与 APK SHA256 见 [artifacts.json](validation/ui-v6/artifacts.json)。桌面仓库未改，手机未更新／清除，本轮截图没有个人视频帧。
- 初次 Gradle `connectedDebugAndroidTest` 的 9 项断言通过，但 UTP 收尾 exit 1，默认卸载 MuMu 应用并丢失应用数据；**不能把该轮写成成功或数据保留**。之后用 SAF 恢复观察到的 QA 音频／视频与收藏，未备份历史偏好不能逐项保证。安全脚本改用 `install -r`＋直接 `am instrument`，后续检查实际保留数据与 APK，不再走默认卸载路径。初次自有素材脚本遇到 Windows PowerShell 编码问题，补 BOM 后生成成功；失败结果不用于验收。

未执行／限制：物理120Hz/OEM 动效／多指，API26–32/R8、200%字体／TalkBack、横屏实机、长时间／20k 库性能未专项验收；USB/native 与后台服务未新增。目录读取需要真实 SAF 授权；内嵌歌词支持常见标签，ID3 v2.2／SYLT／加密等变体未支持，可手动 LRC/TXT。原视频片尾 Surface 问题没有因本轮音乐索引修复自动视为解决。下一具体动作：按用户真实音乐库与视觉反馈检查标签／歌词／物理跟手感；随后处理视频片尾问题，完整核心继续 S02 native gate。

## 2026-10-05：U04 弹窗反馈铺满、导航回退（历史记录）

状态：**修复/回退与本轮真机检查完成，等待用户反馈。** 用户新增09:30两张截图，指出媒体选项按压反馈左右留白，并否定U03图文导航效果、允许回退；读取指定的两张截图，没有读取更早的照片。规则和证据见 [UI_DIALOG_FEEDBACK](UI_DIALOG_FEEDBACK.md)。

修改：HyperDialog原生WindowDialog水平insideMargin改0，HyperSettingRow/HyperSpinnerPreference条目内部24dp，点击/原生indication覆盖整个行；取消按钮/媒体标题单独24dp。卡片内16dp、方向SemiBold和固定取消空间保持。FloatingLibraryNavigation恢复U02官方FloatingNavigationBarItem纯图标项、180dp包装/52dp搜索，库底部空间恢复192/108dp；不继续声称U03已达到相册的动画/模糊/间距。文件为HyperDesign.kt、LibraryScreens.kt、YYPlayerApp.kt。

验证：

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 最终BUILD SUCCESSFUL（42s）；策略5项0失败，lint No issues found。初次补丁整体未匹配、随后使用新增常量时构建失败，补齐完整补丁后重跑通过；未安装失败版本。
- 小米17 Pro Max实际型号2509FPN0BC，Android17/API37、1200×2608/density480。`adb install -r` Success，最新包已留在手机；媒体9首/收藏0等数据保留。按住收藏后截图，再移到弹窗外取消并释放，仍显示“收藏”，没有更改收藏。
- 真实UI层级：收藏/播放/移除三行clickable从x36至1164，与弹窗左右边界相同；文字x108，保持24dp。按压截图灰色实际覆盖两侧，用户提供的旧图同位置有72px白条。证据：[原图](validation/ui-v5/reference-player.jpg)、[新按压](validation/ui-v5/dialog-pressed.png)、[旧行](validation/ui-v5/pressed-before-row.png)、[新行](validation/ui-v5/pressed-after-row.png)、[回退导航](validation/ui-v5/navigation-restored.png)。
- 未改系统密度、尺寸、方向锁或偏好；没有删除原手机截图/用户媒体。只清理本轮临时截图/XML。没有新增镜像单测或重跑旧6项设备测试；窗口/后端/手势生命周期没有改动。未将相册等效动画、API26–32/R8/200%字体、USB和旧片尾问题标记已验。

下一动作：优先用户实际反馈；之后修复已记录片尾Surface问题，再按产品计划推进native。U03的字重和设置缩进保留，导航和弹窗边距规则以U04为准。

## 2026-10-05：U03 小米真机缩进、方向字重、图文悬浮栏（历史记录）

状态：**本轮三项实现、构建与指定真机布局检查完成，等待用户视觉反馈。** 用户授权使用其小米17 Pro Max取材调试；实际ADB型号2509FPN0BC、Android17/API37、1200×2608、density480。没有改密度、尺寸、字体、旋转锁或系统设置。取材及对应规则见 [UI_REAL_DEVICE_REFINEMENTS](UI_REAL_DEVICE_REFINEMENTS.md)。

实现：

- 设置和播放窗口缩进统一：系统“锁屏”条目文字左边界约83px，对应卡片外12dp/内16dp；原设置原生解码行x84px，自写行x96px，修改后主题/强调色/减少动画/视频解码/方向/手势及分组标题统一x84px。HyperDesign增加PreferenceInset/PageTextInset，HyperSettingRow与HyperSpinnerPreference统一局部令牌；SettingsScreen和PlayerScreens使用同一包装。
- WindowDialog保留自身24dp，内部行水平边距0，避免再加16/20dp；真机横屏播放设置条目由原生x794px/自写x806px统一x746px。检查其原生倍速锚定浮窗可以展开/系统返回关闭。横屏还发现旧滚动区把取消按钮挤出屏幕，改为整个内容区有界、滚动列weight(fill=false)，固定取消按钮预留空间，实际可见并能关闭。
- OrientationPage的自动/竖屏/横屏保留17sp，全部SemiBold600；勾选槽与选择逻辑保留，说明/行边距按共同规则对齐。
- 按真机相册“图标上、文字下”取材，FloatingNavigationBar容器中组合官方NavigationBarItem/IconAndText，两个标签始终可见，选中胶囊底色；搜索64dp与条目等高，YYPlayerApp给库底部空间增加12dp。没有伪造FloatingNavigationBarItem的showLabel参数；Miuix图标/原生press/分页弹簧/Activity架构保留。

最终检查：

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 最后一次 **BUILD SUCCESSFUL in31s**；5项纯策略测试0失败；lint No issues found。首次布局构建41s也通过，发现横屏取消后修复并重跑。APK大小18,885,524bytes。
- 真机两次 `adb install -r` 均Success；没有卸载、清库或变更用户偏好。原9首音频、2个视频仍可展示，已导入视频仍能实际打开；测试期间暂停视频。
- 实际查看新版设置/方向页、点击图文导航切到视频库、打开视频播放设置和倍速浮窗、滚动长内容、点击取消。证据在 [validation/ui-v4](validation/ui-v4/)：system-settings、gallery-navigation局部、settings-before/after、orientation-before/after、navigation-before/after、video-dialog-before/after。最终弹窗截图包含可见取消按钮。视频画面/相册整屏临时文件与设备临时截图/XML已删除，仅保留必要UI局部。
- 手机自然横放时已有debug qa_portrait用于手机布局截图；横屏播放按真实比例，尺寸/密度未经override。结束停止测试播放、无extra普通MainActivity启动；最终包已留在手机。手机截图是这台设备实际渲染，不是模拟器或UI mock。
- 本轮未重跑U02六项instrumentation，未更改引擎/手势/Activity生命周期；没有为可逆布局变更添加镜像单测。API26–32、release R8、200%字体/TalkBack、物理手势专项、OEM转场专项、USB DAC和片尾Surface问题仍未因本轮布局验证自动通过。

修改文件：HyperDesign.kt、SettingsScreen.kt、PlayerScreens.kt、OrientationPage.kt、YYPlayerApp.kt；README/AGENTS/本记录/界面说明同步当前规则。下一动作：优先接收真机视觉反馈，然后处理已记录片尾Surface恢复；不能跳过用户UI反馈直接进入native阶段。

## 2026-10-04：U02 Miuix窗口、图标、Activity及动画（历史记录）

状态：**本轮实现与MuMu检查完成，等待用户视觉反馈；HyperOS厂商动效待真机。** 严格对照用户新增5张窗口截图，以及只读 `references/miuix-0.9.4/` 中文文档、示例、组件源码；没有修改参考文件。对应与动效参数详见 [UI_COMPONENTS_AND_MOTION](UI_COMPONENTS_AND_MOTION.md)。

实现与文件职责：

- `HyperDesign.kt`：原生WindowDialog/居中标题/TextButton，所有弹窗保留节点直到原生退出；原生FloatingNavigationBar/Item默认图标布局、独立搜索、按压反馈和默认顶栏回弹。`PlayerIcons`改为MiuixIcons.Regular，移除自绘向量和Material扩展图标实现，新增同版本icons/preference依赖。
- `SettingsScreen.kt`、`PlayerScreens.kt`：锚定选择用WindowSpinnerPreference；速度/队列按上游WindowDropdownDialog源码组合WindowDialog与DropdownImpl；原生布局/勾选/触觉/取消按钮，显式surface底色修正上游标题灰块；列表有界可滚动。暗色视频菜单同样使用原生窗口。
- `OrientationPage.kt`：完整独立选择页，大标题、白色分立卡片、左侧选中勾选；选择后留在页内并持久保存。
- `MainActivity.kt`、`PlayerHostActivity.kt`、manifest：SettingsActivity、PlayerActivity、OrientationActivity实际入栈，移除二级页面AnimatedContent。普通start/finish不覆盖系统/ROM转场；reduce-motion才关闭Activity转场。截图extra仅debug可用。
- `PlayerApplication.kt`：前台唯一runtime、共享播放/偏好，内部切页不暂停/释放引擎；所有Activity停止后暂停，最后owner退出释放。生命周期与以后service迁移规则见 [ADR0003](adr/0003-activity-stack-runtime.md)。`VideoSurface.kt`保留外层变换状态，只在Activity停止时detach实际AndroidView。
- `YYPlayerApp.kt`：媒体库仍为根页内音乐/视频分页，使用上游springAnimateToPage、horizontalPagerSwipeOverride、PagerGestureNestedScrollConnection；重复点击取消旧动画job。视频控件采用Miuix DecelerateEasing1.5/300–250ms淡入淡出。

最终验证：

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4`：BUILD SUCCESSFUL（37s）。纯策略5项通过；lint报告No issues found。
- 两个APK使用 `adb -s emulator-5554 install -r` 更新，保留媒体库/偏好；`am instrument -w yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner`：**OK (6 tests)，3.431s**。包括1项真实30秒PCM WAV跨Activity播放/同实例/返回旧Main，4项TextureView手势/变换/临时倍速测试，原context测试1项。测试文件临时创建在私有cache后删除，没有改用户导入或偏好。
- MuMu Android15/API35：原生白色倍速选择1.5×确实更新快照、关闭窗口，再还原1×；浅色锚定解码浮窗/全页方向/媒体操作窗口、深色播放菜单/倍速窗口逐张检查。实际PlayerActivity→OrientationActivity→PlayerActivity，播放中和暂停在1.25秒的视频均重显真实解码帧；活动栈记录确认有独立Activity。
- 证据：[总览](validation/ui-v3/overview.png)、[选择窗口](validation/ui-v3/speed-dialog.png)、[深色窗口](validation/ui-v3/speed-dark.png)、[锚定浮窗](validation/ui-v3/decoder-popup.png)、[方向页](validation/ui-v3/orientation-page.png)、[暂停视频返回](validation/ui-v3/video-paused-return.png)、[Activity栈](validation/ui-v3/activity-stack.txt)、[6项测试](validation/ui-v3/instrumentation.txt)、[切换录屏](validation/ui-v3/motion.mp4)。录屏展示实际系统Activity进出、锚定浮窗和主页弹簧翻页；不是HyperOS真机动画证明。
- 手机截图临时density440/debug qa_portrait；结束恢复原density280、1080×1920、free rotation，并无extra普通启动。没有新增提交、清理用户数据、修改Raidian/Rust/截图/Miuix参考仓库。

已知问题与范围：视频已到片尾STATE_ENDED后，detach/新建TextureView时可能不再重绘最后一帧而显示黑色；点播放重头播放可恢复。本轮没有改播放状态伪造最后帧，下一播放修复应保留真实EOF/重播意图并添加末帧恢复测试。播放中/暂停往返已验不受此问题影响。小米第一方OEM转场、系统手势返回、API26–32/release R8/200%字体、物理多指、真机/DAC沿用待验；Miuix自带动效的使用不代表全部第一方应用完全相同。下一动作优先用户视觉反馈及片尾Surface恢复，之后再按计划推进native。

## 2026-10-04：U01 HyperOS 前端整体重写（历史视觉基线）

状态：**实现与本轮模拟器检查完成，等待用户审美反馈。** 用户明确否定 E00 前端；本轮阅读 `references/` 的 11 张 HyperOS 4 截图与 Raidian 的 Miuix 页面、主题和磨砂实现，以此整体重写界面。Raidian 和 Rust 参考仓库均未修改。具体设计/文件职责见 [UI_REWRITE](UI_REWRITE.md)。

实现：音乐/视频双分页、可折叠大标题、灰白圆角分组、悬浮磨砂导航与独立搜索、真实封面/视频首帧、收藏过滤、迷你播放器、音乐播放页、沉浸视频控件与弹层、浅深/系统主题、三种强调色与减少动画、横屏侧栏。SAF 导入留在媒体库，点击后才播放；后台读取元数据，限制并发和缓存；技术信息收进菜单。应用入口替换了 Android 模板图标。收藏、主题、强调色、减少动画和方向偏好持久化；解码模式仍为本次播放策略，没有伪称重启保持。

修复：长按等待曾阻止第二指进入缩放；改成单一 pointer 事件循环，第二指立即取消临时加速并变换真实 TextureView。新增 3 项 instrumentation：双指缩放/旋转实际矩阵、3×结束恢复原1.5×、移除 surface 时恢复速度；取消/媒体切换路径不向新媒体回写旧速度。还修正主题实时切换后的预览丢失、卡片点击反馈越过圆角，以及窗口尺寸变化后的系统栏明暗图标。

构建与测试：

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --no-daemon --max-workers=4` 通过；纯策略测试 5 项，设备测试 4 项（3 手势 + 原有 context 测试），0 失败。
- 最后视觉资源/主题预览/系统栏修正后，重复 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` 通过；lint `No issues found`。手势生产实现没有继续改动，因此未重复 connected 测试。
- MuMu `emulator-5554`，Android15/API35：实际 SAF 导入 WAV 与两个 H.264/AAC MP4；播放、暂停、进度、收藏过滤、搜索、浅深主题、重启后 URI/收藏保留已检查。新版菜单实际软解 `OMX.google.h264.decoder`、严格硬解 `OMX.qcom.video.decoder.avc`；同一竖屏素材手动横屏再自动恢复竖屏成功。
- 新证据在 [validation/ui-v2](validation/ui-v2/)；`music-light.png`、`music-dark.png`、`video-library.png`、`audio-player.png`、`settings-light.png`、`settings-dark.png`、`search.png`、`favorites.png`、`video-menu.png`、`video-portrait.png`、`video-landscape.png`、两个 decoder info 图和 `library-wide.png`。素材沿用 E00 的 hash；不是虚构曲库。

验收复现注意：手机截图临时设置 density440，并以 debug-only `qa_portrait=true` Intent 启动，因为 MuMu host sensor 覆盖 wm 方向锁；显式视频方向优先。宽屏布局以临时 `wm size 1920x1080` 验证。结束已恢复 `wm size reset`、density280、`wm user-rotation free`，以无 extra 的普通 Intent 启动。常规安装用 `adb install -r` 保留导入/偏好。Gradle connected UTP 在本机卸载目标包并清掉测试导入/偏好，后续只在可丢弃 QA 实例执行；本轮仅测试素材导入受影响，已从真实 SAF 重新导入，设备媒体文件保留。

Miuix blur0.9.4 的 manifest min33；项目仍 min26，通过 [ADR0002](adr/0002-api33-blur-facade.md) 的无类型 facade、SDK 守卫与 `@Keep` API33 类隔离。API26–32 的不透明回退、release R8 仍需对应系统实测。USB HiFi/native、真机物理多指、后台服务等没有因本轮 UI 工作变成已验。下一步先接收用户对新版视觉的反馈；无进一步 UI 调整时继续 S02。

## 2026-10-04：E00 MuMu 前后端与 UI 纵切

状态：**历史播放纵切。其视觉已被用户否定，并由 U01 替换。** 已安装/运行 MuMu；真实 SAF 本地音视频经过 Media3 播放；实际硬件与软件 H.264 decoder 都切换成功；自动/手动方向和主题切换已通过截图检查。E00 只代表当时模拟器验收范围，不代表长期音频后端或硬件 HiFi 验收完成。

主要实现：

- Compose + Miuix 四页 UI（音乐、视频、播放、设置），SYSTEM/LIGHT/DARK 主题和 reduce-motion 偏好持久化。
- SAF 多选音频/视频，保存 URI、展示名、MIME 和类型；不申请全盘访问权限，授予可持续 URI 权限；应用更新/重启后仍能读取导入条目。
- `PlaybackBackend` 与 `PlaybackSnapshot` 状态/命令契约；当前 `AndroidMedia3PlaybackBackend` 以 ExoPlayer 做本地音视频播放、进度、队列、变速和 decoder analytics。
- MediaCodec 策略 Auto / PreferHardware / StrictHardware / Software；切换时保留当前媒体、进度和播放意图。StrictHardware 只给硬件 decoder，关闭 decoder fallback。MuMu 对 `OMX.google.h264.decoder` 同时报 hardwareAccelerated=false、softwareOnly=false，因此软件策略将 Android 标准 `OMX.google.*` / `c2.android.*` 名称作为明确 fallback；UI 显示“平台软解（系统标记不完整）”，不将系统 false flag 说成系统标记软件。
- 视频 TextureView surface 显示实际 decoder；自动从有效视频比例选择方向，提供手动竖屏/横屏/自动；双指缩放/旋转/平移矩阵、0.5–5×范围、长按临时 3×已实现。
- 已提交纯策略单测；新增 `scripts/Generate-MumuFixtures.ps1` 以 FFmpeg 生成并可选推送测试素材。

环境和命令：

- Windows；MuMu adb serial `emulator-5554`；Android 15 / API 35；系统枚举 Xiaomi 2304FPN6DC；显示物理尺寸 1080×1920，activity 可切横/竖。
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4`：BUILD SUCCESSFUL；5 tests、0 failures；lint 通过。编译剩一项 Media3 弃用 warning：`VideoSize.unappliedRotationDegrees`。
- `.\scripts\Generate-MumuFixtures.ps1 -PushToEmulator` 完成。测试素材 SHA256：WAV `64F80E999CE6B3D2805FD5A2466F248BC59B900543BD83F6F082A9B6161918DB`；横屏 MP4 `15752209EE46CDA0271949C2F806CE19C7554181B89066A8F19B1588DA5838B7`；竖屏 MP4 `2830840578CD76AE4A8867928EFC33FB3B40B33C9D2C1FAB12397CAC70F562E7`。

MuMu 实测和证据：

- SAF 导入 44.1 kHz / 2 声道、120 秒 WAV；播放页采样率/声道/进度与实际 Media3 输入轨道相符。
- 横屏 H.264/AAC 使用 `OMX.qcom.video.decoder.avc` 严格硬解播放；切换 Software 后使用 `OMX.google.h264.decoder` 播放；两个模式的画面持续更新，快照显示当前实际 decoder。
- 竖屏 360×640 H.264 在 Auto 下保持竖屏；对同一媒体手动横屏，再切回 Auto 会回竖屏；横屏素材手动竖屏也生效。
- 用户可见主题浅/深切换生效。截图在 `docs/validation/`：`mumu-home.png`、`mumu-audio.png`、`mumu-video-hardware.png`、`mumu-video-software.png`、`mumu-video-portrait-auto.png`、`mumu-settings-light.png`、`mumu-settings-dark.png`。

仍未验：真实小米/红米和 USB DAC；USB BIT_PERFECT、AAudio Exclusive、AudioTrack 实际输出格式/位深；C++/JNI/libmpv/FFmpeg/Oboe；API 26；API37 大屏/折叠/多窗口；后台 MediaSession；多指/长按物理输入自动化；字幕/多音轨/codec 格式矩阵；release 签名/16 KB native 装载/HDR。MuMu 上的音频轨道格式不是设备输出格式，不能作为 HiFi 证明。手势在 UI 已实现，但未以真实双指设备做验收。

下一具体阶段：S02 native 依赖供应链与 JavaVM/TextureView gate；E00 Media3 后端保留作 UI/播放对照，native 构建/装载证据到位前不替换。

## 2026-10-04：需求调研与技术规划

状态：**规划基线（历史记录，已被上面的 E00 实现记录 supersede）。** 当时应用实现尚未开始；本条保留调研时环境/范围快照。

已确认的产品范围：

- 主要HiFi组合为小米/红米手机 + USB DAC；兼容内置输出、扬声器、蓝牙。
- 本地音视频优先，参考哔哩哔哩的播放功能与手势；首版不接其内容服务。
- Compose + Miuix的HyperOS风格、主题和充分动画；核心与UI分离，保留其他移动OS适配空间。
- 视频必须软硬解切换、自动/手动横竖屏、长按临时3×、双指连续缩放/旋转/平移；不能删需求来降低实现难度。

实际检查：

- Android工程为Hello Android模板，只有`:app`；当前master无提交，大部分模板文件未跟踪；本轮没有修改业务源码、Gradle配置或manifest。
- 当前catalog：AGP9.4.1、Gradle9.6.0、Compose compiler2.2.10、BOM2026.02.01，API26–37；包名`yy.daniel.yyplayerforandroid`。
- 本机Java25.0.3、Rust1.99.0；SDK配置为`E:\DeveloperTools\AndroidSDK`，已发现NDK28.2.13676358与CMake3.22.1等版本。Rust版本只是环境记录，Android首版不依赖它。
- Rust参考项目`E:\SourceFiles\rust\yyplayer`位于dev、`ec55789`，工作区检查无变更；读取README/AGENTS/STATUS/core模型/音频策略/worker清单/runtime manifest。未修改参考仓库。
- 查阅官方Android文档、Miuix/mpv/mpv-android/Oboe/libusb源码及发布资料；完整来源和候选版本见RESEARCH_SOURCES。

当时交付：README.md、AGENTS.md、docs/TECHNICAL_DESIGN.md、docs/IMPLEMENTATION_PLAN.md、docs/RESEARCH_SOURCES.md、本文件。除后续 E00 明确列出的实现外，规划接口/模块/脚本仍不是已创建的软件实现。

选择：Kotlin/Compose/Miuix + C++17 portable core；libmpv视频；FFmpeg音乐PCM；AudioTrack系统USB/Oboe独占；Media3系统会话；默认SDR TextureView连续变换。完整理由/数据契约/生命周期/质量策略/验收见TECHNICAL_DESIGN。

本轮检查与限制：

- 默认shell/Node沙箱启动返回`setup refresh had errors`；使用获准的沙箱外只读命令完成目录/环境检查，apply_patch完成工作区文档写入。没有因工具错误修改系统配置。
- PowerShell严格UTF-8读取、无替代字符/行尾空白、Markdown围栏配对、8个本地文档链接、S00–S14共15个阶段与STATUS对应检查通过；`git diff --check`通过。由于模板及文档尚未跟踪，另外直接检查了新增文档文本，未把空diff当文档检查。
- 没有运行Gradle编译、安装工具、下载native二进制、连接/操作DAC、创建提交或发布。版本为查证过的候选，组合兼容性仍需S00/S02。
- 未验证：具体手机/ROM/DAC能力、系统BIT_PERFECT、AAudio实际Exclusive、TextureView硬解任意角、16KB装载、数字捕获/HDR。没有把这些标为支持。

当时下一任务：S00 工具链/Miuix 最小接入；实际当前下一任务已更新为上方 S02。

## 阶段状态

| 阶段 | 状态 | 尚需证据 |
| --- | --- | --- |
| U04 弹窗反馈/纯图标导航回退 | Done（实现/指定真机按压检查） | 用户视觉反馈 |
| U03 真机缩进/字重/图文导航 | Done（本轮实现/指定真机布局检查） | 用户视觉反馈；字体缩放/无障碍专项另验 |
| U02 Miuix窗口/图标/Activity/动画 | Done（本轮实现/模拟器检查） | 用户视觉反馈、HyperOS真机转场；片尾Surface恢复另列修复 |
| U01 HyperOS 前端重写 | Done（实现/模拟器检查） | 用户审美反馈、低 API/真机/R8 |
| E00 MuMu 实际播放/UI 纵切 | Done（仅本轮范围） | 物理双指/长按及真机硬件另验 |
| S00 工具链/Miuix | Partial | API26/API34真机启动、无障碍/多设备 |
| S01 契约/视觉骨架 | Partial | 可移植 C ABI/schema、完整预览/字体验收 |
| S02 原生技术gate | Planned | runtime闭包/JNI/任意角/音频probe |
| S03 URI/权限 | Partial | native FD/取消/失权/非seekable |
| S04 真实视频 | Partial | native Surface/字幕/多音轨/codec矩阵 |
| S05 系统服务 | Planned | 息屏/焦点/session/重建 |
| S06 FFmpeg音乐 | Partial | Media3 WAV已跑；FFmpeg PCM/sink 尚未实现 |
| S07 系统USB | Planned | 能力/严格失败/路由/真DAC |
| S08 Oboe独占 | Planned | 实际sharing/失败/长时 |
| S09 视频交互 | Partial | 方向已测；手势取消路径/物理多指未验 |
| S10 首版收口 | Partial | 基础 UI/主题已跑；无障碍/性能/功能完整度 |
| S11 DSP/无缝 | Planned | 样本/滤波/profile/连续输出 |
| S12 USB Direct | Planned | UAC/等时/白名单/数字capture |
| S13 后续专项 | Planned | PiP/网络/弹幕/HDR各自资格 |
| S14 正式发行 | Planned | 许可/源码/签名/ABI/真机闭包 |

## 后续任务记录模板

```text
日期：
阶段/子步骤：
状态：Planned / InProgress / Partial / Done
范围与前置条件：
改动文件：
检查命令、exit code、结果：
设备/ROM/DAC/固件/runtime/配置/素材hash：
证据文件：
未执行或未通过：
已知限制/可恢复动作：
下一具体步骤：
```
## E00 状态补充（历史，当前以顶部 U01 为准）

本节补充当时 E00 事实；当前工作以顶部 U01 为准。E00 已完成首个可运行的 Android 纵向切片：Jetpack Compose + Miuix UI、SAF 本地媒体导入、持久媒体列表、Media3 音视频播放、软解/硬解策略、基础旋转与主题切换。

在 MuMu Android 15/API 35（小米 2304FPN6DC 模拟设备）上，`:app:assembleDebug`、`:app:testDebugUnitTest`（5 项通过）与 `:app:lintDebug` 均通过。脚本生成的 H.264/AAC 样片实际分别使用 `OMX.qcom.video.decoder.avc` 硬件解码和 `OMX.google.h264.decoder` 平台软解；后者在 MuMu 上的系统 hardware/software 标记均为 false，界面已明确标注系统标记不完整。WAV 播放链路显示 44.1 kHz、2 声道输入格式。

后续从 S02 原生核心供应链与 JNI/Surface 门禁开始。S00、S01、S03、S04、S06、S09、S10 仍有部分或真机验证工作；S02、S05、S07、S08、S11–S14 尚未完成。MuMu 结果不代表 USB DAC 独占、bit-perfect、真实设备音频路由或物理多点手势通过。完整命令、样片哈希、截图和细分边界见 README 与 IMPLEMENTATION_PLAN。
