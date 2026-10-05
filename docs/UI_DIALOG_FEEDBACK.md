# U04：弹窗反馈铺满宽度、悬浮导航回退

2026-10-05。用户提供手机最新两张截图：09:30:03系统设置、09:30:07 YYPlayer媒体选项。前者灰色按压反馈覆盖弹窗左右边缘，后者左右各留24dp白条。U03图文悬浮栏也被用户否定，用户明确允许回退；本轮恢复U02官方纯图标样式，没有声称实现了相册等效的动画/高斯模糊/间距。

## 修复规则

之前WindowDialog整体内容水平padding24dp，内部行padding0。文字缩进正确，但clickable/indication布局只能覆盖中间内容，无法进入两侧padding。

现在HyperDialog设置原生WindowDialog insideMargin=DpSize(0dp,24dp)，让列表铺满宽度；HyperSettingRow、HyperSpinnerPreference通过LocalPreferenceInset在条目内部提供24dp。点击/原生Miuix反馈在padding之前，覆盖整行；只有文字/箭头内缩。卡片仍为内16dp，方向SemiBold600、Activity、原生窗口进出及固定取消按钮不变。取消按钮自身水平24dp，媒体文件标题单独24dp；不再对整个交互内容区加水平padding。选择窗口原有DropdownImpl也是先selectable再内部padding，保持原样。

真机点击区域x36..1164px，与弹窗外边界相同；文字x108px，即36+24×3。按住“收藏”截图后移出并释放取消，没有改收藏；灰色区域实际铺满两端。采用库的原生MiuixIndication，没有自绘灰色遮罩或扩大截图。

## 导航回退

删除U03 NavigationBarItem/IconAndText/选中胶囊组合；恢复官方FloatingNavigationBarItem纯图标项、180dp包装、52dp搜索、主页底部预留192/108dp。保留U02上游磨砂参数、原生按压色和弹簧分页；没有增加重复navigationBar inset。

## 验证与文件

- 修改HyperDesign.kt、LibraryScreens.kt、YYPlayerApp.kt；构建/单测/lint及手机安装结果见STATUS。本轮未改后端/手势/Activity，不将旧设备测试记为重跑。
- [用户系统截图](validation/ui-v5/reference-system.jpg)、[用户应用截图](validation/ui-v5/reference-player.jpg)、[修复后实际按压](validation/ui-v5/dialog-pressed.png)。
- 同一行局部对比：[之前](validation/ui-v5/pressed-before-row.png)、[之后](validation/ui-v5/pressed-after-row.png)；没有修改截图颜色，只有裁切。
- [回退导航](validation/ui-v5/navigation-restored.png)。覆盖安装保留媒体和偏好；原系统截图文件没改动，设备临时取材文件已清理。
- 依据：[原生WindowDialog](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/window/WindowDialog.kt)、[BasicComponent的clickable/padding顺序](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt)、[NavigationBar](../references/miuix-0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/NavigationBar.kt)。参考仓库只读。
