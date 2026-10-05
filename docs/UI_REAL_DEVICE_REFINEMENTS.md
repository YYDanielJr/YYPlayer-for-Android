# U03：小米真机缩进、字重和图文导航

历史记录：U04已按最新用户反馈回退图文导航，并将弹窗的24dp边距移到交互行内部，解决按压两侧留白；当前规则见 [UI_DIALOG_FEEDBACK](UI_DIALOG_FEEDBACK.md)。U03方向字重和设置卡片边距保留。

2026-10-05。用户在小米17 Pro Max安装debug包后提出三项反馈；本轮连接该真机取材、保留数据更新和验收。当前界面规则优先于U02的纯图标悬浮栏基线。

## 缩进：每个容器只拥有一层内边距

设备实际枚举型号2509FPN0BC，Android17/API37，物理1200×2608、density480，即3px/dp。系统“锁屏”设置页面的卡片左边界约36px，标题/说明左边界83px，折算为外12dp、内约16dp；1px差异来自系统测量取整。Miuix0.9.4的BasicComponentDefaults.InsideMargin也是16dp。

| 位置 | 修改前 | U03规则 / 真机结果 |
| --- | --- | --- |
| 设置卡片外边距 | 12dp | 保持12dp |
| 原生视频解码行 | 内16dp，文字x84px | 统一内16dp，x84px |
| 自写屏幕方向/播放手势等行 | 内20dp，文字x96px | 统一内16dp，x84px |
| 主题标题、分组标题 | 主题x96px、分组x78px | 统一文字x84px（28dp） |
| WindowDialog内条目 | 窗口24dp再叠行16/20dp | 窗口保留24dp，行水平内边距0 |
| 横屏播放窗口可见条目 | 原生行x794px，自写行x806px | 解码、方向、速度、重置、信息、队列统一x746px |

实现集中在HyperDesign：PreferenceInset=16dp，PageTextInset=12+16dp；HyperSettingRow和HyperSpinnerPreference读取同一个局部令牌。HyperDialog内容提供0dp行水平内边距，其原生WindowDialog已有24dp，避免二次缩进。选择浮窗自身的原生选项边距不受这个令牌影响；不修改参考仓库或上游库源码。

横屏检查还发现旧弹窗内容高度限制只限制列表，可能把取消按钮挤出屏幕；改为限制整个内容区，滚动列使用weight(fill=false)，先给固定底部按钮留空间，长内容仍可滚动。

## 方向选项字重

“自动”“竖屏”“横屏”保留17sp，全部使用FontWeight.SemiBold（600），保持蓝色选择/勾选和未选中颜色；没有只加重选中项。行内左边距统一16dp，28dp勾选槽保留；说明文字与设置标题统一28dp。对照此前“默认USB配置”截图，真机检查三个选项可见、同字重。

## 图标上方、文字下方的悬浮导航

真机相册底栏同时显示“照片”“影集”的图标和下方文字，选中项有圆角底色、右侧独立搜索。

Miuix0.9.4的FloatingNavigationBarItem只有图标，label仅是无障碍描述，没有showLabel参数；不编造API。U03保留FloatingNavigationBar窗口/底部安全间距/磨砂容器，在内部Row组合官方NavigationBarItem，明确LocalNavigationBarDisplayMode=IconAndText。两项始终显示“音乐”“视频”，默认图标26dp、文字12sp、条目64dp来自NavigationBarDefaults；使用选中胶囊底色和原生选中/按压文字色。搜索圆形触控区统一64dp；库列表底部预留增加12dp，防止图文栏遮挡最后一项。侧栏宽屏布局保留。

## 文件与验证

- 修改文件：HyperDesign.kt、SettingsScreen.kt、PlayerScreens.kt、OrientationPage.kt、YYPlayerApp.kt；未改变引擎、解码策略、偏好键和Activity架构。
- 执行 `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4`；最终结果见STATUS。本轮属于布局修正，没有新增映射实现的单元测试，也未把此前6项设备测试记成本轮重跑。
- 使用adb install -r覆盖真机原debug包，保留原有导入、URI授权、收藏和偏好。检查设置、方向页、图文导航切换、视频播放设置内锚定选择及滚动条目；测试时暂停真实导入视频，不保存个人视频帧。
- 取材/对比：[系统设置](validation/ui-v4/system-settings.png)、[相册底栏局部](validation/ui-v4/gallery-navigation.png)、[设置修改前](validation/ui-v4/settings-before.png)、[设置修改后](validation/ui-v4/settings-after.png)、[方向修改前](validation/ui-v4/orientation-before.png)、[方向修改后](validation/ui-v4/orientation-after.png)、[导航修改后](validation/ui-v4/navigation-after.png)、[播放设置局部](validation/ui-v4/video-dialog-after.png)。相册和视频只保留UI局部，临时整屏文件删除。
- 设备物理横放时，对应用手机布局检查临时使用已有debug qa_portrait；没有改系统密度、尺寸、字体或旋转锁。结束普通无extra启动；不把截图辅助路径说成所有系统返回动画专项已验。
- 本轮验证是指定真机布局验收，API26–32/R8/200%字体、USB DAC、片尾Surface恢复等旧待验项保持原状态。

## 锁定版本源码

- [BasicComponent边距](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt)。
- [WindowDialog](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/window/WindowDialog.kt)、[Dialog默认24dp](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/layout/DialogContentLayout.kt)。
- [NavigationBarItem / FloatingNavigationBar](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/NavigationBar.kt)、[导航文档](../references/miuix-0.9.4/docs/zh_CN/components/navigationbar.md)。
