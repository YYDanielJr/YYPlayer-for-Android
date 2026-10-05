# U02：Miuix 窗口、图标与 Activity 动画

U04（2026-10-05）已修复按压边缘，并按用户要求回退U03图文导航；当前规则见 [UI_DIALOG_FEEDBACK](UI_DIALOG_FEEDBACK.md)。U03历史测量见 [UI_REAL_DEVICE_REFINEMENTS](UI_REAL_DEVICE_REFINEMENTS.md)。下面保留U02组件/动效对应，导航说明同步实际实现。

依据：用户新增5张HyperOS窗口截图，以及用户提供的只读 `references/miuix-0.9.4/` 文档/示例/源码。没有运行参考仓库安装脚本，没有改参考文件。新增同版本的 `miuix-icons-android`、`miuix-preference-android`，删除Material扩展图标依赖。

## 窗口对应

| 参考 | 实现 |
| --- | --- |
| 15-44-11-640，小型锚定选择浮窗 | 设置的视频解码、视频菜单的解码/速度用 `WindowSpinnerPreference`，原生锚点、遮罩、蓝色文字/勾选 |
| 15-44-00-672 / 15-44-19-628，底部选择对话框 | 倍速、队列按照 `WindowDropdownDialog` 源码组合 `WindowDialog` + `DropdownImpl` + `TextButton`，居中标题、选中行、全宽灰色取消按钮 |
| 15-44-23-312，底部输入/操作窗口 | 媒体操作、媒体信息、播放设置用 `WindowDialog` + `TextButton`；当前没有需输入的设置，不新增无用输入框 |
| 15-43-55-912，完整选择窗口 | `OrientationActivity`，独立Activity、大标题、分立白色卡片、左侧蓝色勾选 |
| 上游悬浮导航组件 | `FloatingNavigationBar` + `FloatingNavigationBarItem`，纯图标和独立搜索；U04恢复U02样式 |

原生对话框节点始终保留在Composition，用show控制显隐；不能在show=false时直接删除，否则截断退出动画。`HyperDialog`只是原生WindowDialog包装，没有自绘平台Dialog容器和关闭X。0.9.4的WindowDropdownDialog没有backgroundColor参数，默认标题/底部使用background，会出现灰色区域；因此SelectionDialog照上游布局组合原生WindowDialog和DropdownImpl，明确使用surface，主题surfaceContainer与surface同色，让标题/列表/按钮外部底色一致。保留原生触觉反馈、选中勾选、LocalDismissState退出和有界LazyColumn，全宽取消按钮保持灰色。小屏底部呈现、大屏居中由库判定。

悬浮栏使用示例的25f磨砂半径、0.6表面混色、浅深GlassStrokeMiddle高光；实际shader仍只在API33 facade里，低版本不透明回退。原生栏自己处理底部间距，调用层不重复加手机navigationBar inset。U03曾组合NavigationBarItem图文项；用户指出动画/模糊/间距仍与相册有差距，U04按其授权回退FloatingNavigationBarItem纯图标样式。没有给纯图标项编造showLabel参数，也不把上游参数使用称为相册等效实现。

界面全部使用 `MiuixIcons.Regular`；PlayerIcons只是别名，已删除全部自绘Path和Material图标。库没有专用视频/前后曲glyph，分别使用Play、ChevronBackward/ChevronForward，功能语义标签不变；页面返回用ChevronBackward。没有误用Forward这个外部跳转图标。启动器图标是应用自己的标识向量，保留。

## Activity 与所有权

MainActivity只放媒体库；SettingsActivity、PlayerActivity、OrientationActivity实际入栈。移除二级页面AnimatedContent，不覆盖普通windowAnimationStyle/start/finish动画，由系统/ROM选择。减少动态效果才将应用Activity transition覆盖为0，按钮翻页直接跳转、视频fade为0。

Application的单个PlayerRuntime共享当前Media3引擎和StateFlow偏好。Activity不能各自创建/释放引擎；owner最后退出才释放，配置重建保留，栈全部停止500ms后暂停（当前没有后台service）。STARTED以上收集UI状态；播放Activity停止时只卸下AndroidView，VideoSurface的变换状态保留，取消临时3×；恢复时重建viewport并挂接同一真实player。具体规则与S05退出条件见 [ADR0003](adr/0003-activity-stack-runtime.md)。

## 动画对照

| 位置 | U02实际处理 |
| --- | --- |
| 二级窗口 | 删除旧220/320ms页面滑入/淡出，交给真实Activity/ROM |
| 对话框 | WindowDialog原生实现：手机入场spring damping0.88/stiffness450，退出260ms Decelerate1.5，遮罩300/250ms；大屏原生centered scale/fade |
| 锚定浮窗 | WindowSpinnerPreference使用ListPopupDefaults的fraction/alpha/dim轨道和返回处理 |
| 主页翻页/手滑 | 示例springAnimateToPage，stiffness322.2、阻尼来自上游；horizontalPagerSwipeOverride和PagerGestureNestedScrollConnection，关闭竞争默认gesture，重复点击取消旧job |
| 悬浮栏/按压 | 官方组件选中/pressed色与布局；MiuixTheme统一提供MiuixIndication/folmeSpring反馈 |
| 顶栏回弹 | 移除自定stiffness2500，使用MiuixScrollBehavior默认snap |
| 视频控件 | 使用库DecelerateEasing1.5和300/250ms，仅透明度，不移动实际surface。这是本项目视频交互选择，不假称上游存在视频播放器示例 |

结论：采用Miuix自己的组件动效和示例参数，二级窗口留给ROM。**MuMu不是HyperOS真机；小米第一方跨Activity动效、系统手势返回、厂商窗口圆角仍需真机实测。** API26–32、release R8、200%字体、真机物理多点沿用未验范围。

## 来源与证据

- [图标文档](../references/miuix-0.9.4/docs/zh_CN/guide/icons.md)、[WindowDialog](../references/miuix-0.9.4/docs/zh_CN/components/windowdialog.md)、[WindowSpinnerPreference](../references/miuix-0.9.4/docs/zh_CN/components/windowspinnerpreference.md)。
- [AppContent示例](../references/miuix-0.9.4/example/shared/src/commonMain/kotlin/AppContent.kt)、[Dialog示例](../references/miuix-0.9.4/example/shared/src/commonMain/kotlin/component/DialogSection.kt)。
- [DialogContentLayout动画](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/layout/DialogContentLayout.kt)、[PagerGestureUtils](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/PagerGestureUtils.kt)。
- 本轮截图/动画录屏在 `validation/ui-v3/`；设备测试记录 [instrumentation.txt](validation/ui-v3/instrumentation.txt)，实际命令和结果见STATUS。测试采用保留数据的adb install -r与am instrument，不使用会卸载目标包的Gradle UTP。

