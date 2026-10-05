# 进度与交接记录

## 2026-10-05：U08 媒体库分页与页头视觉（当前记录）

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
