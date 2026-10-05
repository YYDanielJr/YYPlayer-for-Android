# U08 媒体库分页与页头视觉（验收素材）

用户实体小米 2509FPN0BC（Android 16／targetSdk37，1200×2608，density 480）截图与命令日志。**用户已复核本轮结果并确认满意；下表截图即该次验收依据。**

- `user-home-reference.jpg`：用户提供的改动前首页截图（音乐页，TabRow 48dp、卡片 86.7dp、页头随整页横移）。
- `after-home.png`：改动后音乐页。页头（标题＋图标＋TabRow）固定在 pager 之外，TabRow 40dp，卡片 68.7dp。
- `after-scroll.png`：同一页向下滚动后——大标题收成栏内“音乐”小标题，TabRow 常驻，内容在 TabRow 下方滚动。
- `after-tab-video.png`：切到视频页后的稳定状态（标题、图标、TabRow 选中态与内容一致切换）。
- `after-tab-midfade.png`：点击“视频”后约 0.1s 抓取，标题与图标处于渐隐中间态（跟随 pager 偏移量，而非固定时间轴闪烁）。
- `checks-normal.txt`：`:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` 的最终通过日志（JVM 16 项 0 失败、lint No issues found）。
- `checks-normal-failed-2.txt`：本轮首个失败构建日志（Kotlin DSL 作用域与测试尾随 lambda），保留未删，未当作通过证据。

未在该实体机跑通的自动化：`LibraryTabsUiTest`／`LibraryManagementUiTest` 已编译进测试 APK，但 `am instrument` 报 `Process crashed`／无输出，未取得崩溃栈；界面结论以实机截图为准。
