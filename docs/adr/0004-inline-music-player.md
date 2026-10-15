# ADR 0004：音乐详情采用主 Activity 内转场

## 2026-10-15 U28：系统媒体入口返回首页

小米 13 Ultra 控制中心实际发送 MAIN／LAUNCHER，来源 android-app://com.android.systemui，没有 sessionActivity 的 open_music 参数。仅依赖显式参数会落到首页。继续使用同一 MainActivity 共享封面宿主，增加 SystemUI 来源且存在当前音频的导航兼容；不增加 Activity 或播放器。referrer 可被构造，仅用于无权限副作用的显示决策。

媒体入口清除旧 music_on_back_stack 标记，使收起／返回始终回到此 Activity 的首页。onNewIntent 先更新 Intent 再读取来源，消费后移除 open_music；配置重建沿用保存的展开状态，不重放初始启动命令。普通桌面启动无额外展开。真机系统卡片与通知、温启动和重建验证见 [U28](../validation/navigation-u28/README.md)。

## 2026-10-15 U27：宿主默认能力与页面排除

按 buglist.txt，队列和设置向导不再叠加播放栏。新二级 Activity 默认从 `AppTheme` 获得 `PlaybackPageHost` 的栏与 `MusicExpansionHost` 的完整展开／返回收回能力；已有完整控件的页面显式使用 `AppTheme(playbackDock = false)`。主页向导分支直接绘制 `SetupScreen`，不创建展开宿主。

共享封面仍冻结展开终点并保留原回弹，所以详情输入必须等弹簧完成后再放行；过去进度达到 0.96 即放行，导致快速横滑时页面离开而共享封面仍滞留。分页本身使用逐元素水平弹簧跟随，去掉分页倾角；独立角落按压不变，拖动时立即禁用倾角。见 [U27 验证](../validation/music-u27/README.md)。

## 2026-10-15 U26：所有播放栏宿主内展开

用户明确要求将主页共享封面展开／收回动画应用到所有控制条页面。现将主页动画提取为 MusicExpansionHost，主页、搜索、设置／方向／歌词样式／音频输出、队列、联网歌词、重新设置共用同一套状态与绘制层。每页从自己的实际小封面 bounds 展开，收回后仍由同一层绘制小封面，保持原弹簧参数和反向动作处理。控制条材质不变。

普通音频入口改为发送当前 Activity 的消费一次命令，在线搜索播放成功也使用同页入口；不再新建 MainActivity。底层内容保持 Composition 和状态，BackHandler 在其后注册以优先关闭详情。键盘在展开时隐藏，原页布局更新后的控制条作为收回终点。旧 PlayerActivity 音频转向保留独立 MainActivity 兼容路径，视频、设置／队列等子 Activity 的 ROM 转场不改。

展开状态 rememberSaveable；配置重建从完全展开态恢复。搜索结果使用 Activity ViewModel，不将完整结果塞进 Bundle；搜索词、列表位置仍由 Compose 保存。联网歌词页内切歌刷新当前曲目候选，取消旧请求并核对 generation，不结束承载详情的 Activity。没有新播放器或全局浮窗。验证和限制见 [U26](../validation/music-u26/README.md)。

以下为历史决定，普通二级页音频入口的 U23 压栈方案由本节替代。

日期：2026-10-05；状态：采纳（用户本轮明确授权）。

U02 为音乐和视频统一使用 PlayerActivity。用户现在要求视频保留原生 Activity，音乐由应用自行绘制进入、退出及封面／歌词滑动动画。

决定：音乐详情作为 MainActivity 的覆盖层，与库共用 PlayerRuntime 和 PlaybackBackend。视频、设置、方向页保留系统 Activity 栈。音乐覆盖层使用可中断弹簧进退、封面色渐变、Miuix Pager 拖动与分层弹簧跟随；关闭只改变界面，不停止真实播放。歌词仅从已授权 URI、目录或实际内嵌标签读取；未知授权提供手动添加／目录授权。

影响：系统返回先关闭音乐详情，再退出库；旋转重建保留展开状态；唯一播放器生命周期不改变。减少动画及系统 animator scale=0 时直接完成页面和颜色过渡，无持续背景动画。

验证：编译、解析边界测试、真实播放／返回／切歌／歌词 seek／授权与拒绝路径、MuMu 页面截图及手势；API26 和物理设备专项单列。

## 2026-10-15 U23：从搜索进入封面的返回栈

用户要求联网搜索流式播放后，退出封面回到上一搜索页。旧入口使用 CLEAR_TOP 找回主页，再 finish 搜索页，导致搜索结果与列表状态被销毁。

搜索入口现在在原生 Activity 栈中压入一个带 `music_on_back_stack` 标记的 MainActivity 音乐页面；不清栈、不关闭搜索页。这个入口首帧直接显示完整封面，使用 ROM 的 Activity 转场；收起按钮和系统返回均 finish 当前入口，露出仍存活的搜索页，避免先收回到重复主页再跳转。Intent 标记随 Activity 重建保留。队列／歌词等子 Activity 仍逐级返回。

主页入口仍使用原有覆盖层、共享封面弹簧与收回动画；返回行为由入口决定，不由歌曲是否在线决定。所有入口继续共享 Application runtime 唯一播放引擎，返回界面不停止或重载歌曲。没有引入全局 URL 路由栈或第二套播放器。搜索进程被系统完全回收后的结果恢复不在此次验证范围内。

一加平板真实 HTTPS 流式播放、列表位置、重复进出、系统返回、子页面与重建证据见 [U23](../validation/navigation-u23/README.md)。

## 2026-10-15 U24：页面宿主共享播放栏

用户要求所有页面可控制播放，尤其搜索页；尽量不受页面切换动画影响，并接受 Activity 动画限制。采用 AppTheme 中的 PlaybackPageHost 统一包裹二级 Activity，播放栏位于内容布局外，由实际测量分配底部空间和消费 IME／导航栏 inset。重新设置页同样接入；主页保留现有共享封面锚点，完整音乐／视频页使用其完整控制器。

所有页面仍订阅 Application runtime 唯一后端，不新增 Player，不引入跨窗口 overlay／悬浮窗权限。非主页 openPlayer 的音频分支统一使用 U23 压栈入口，移除残余 CLEAR_TOP 路径，关闭封面回到真实调用页。页内 Pager／AnimatedContent 不作用于播放栏；不同 Activity 的控制器仍随 ROM 窗口过渡运动。证据见 [U24](../validation/playback-u24/README.md)。

## 2026-10-15 U25：悬浮于真实内容上方

用户明确否定 U24 的底部占位效果。宿主改为全尺寸内容层＋后绘制的独立播放栏，磨砂捕获包含实际列表的完整内容层；不捕获播放栏自身。实际栏高通过 LocalPlaybackBottomSpace 仅供滚动尾部 contentPadding／固定操作按钮避让，禁止用作整页 padding 或 viewport 裁切。返回栈、唯一后端与跨 Activity 转场决定不变，版本 0.1.4，见 [U25](../validation/playback-u25/README.md)。
