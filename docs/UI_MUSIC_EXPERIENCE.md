# U05：音乐库、封面与歌词详情

2026-10-05。用户本轮授权重构音乐前端；视频、设置和方向页面保留 U02 的原生 Activity。音乐使用 MainActivity 内覆盖层，决定见 [ADR0004](adr/0004-inline-music-player.md)。本轮没有修改只读桌面工程，没有提交或发布。

## 已实现

- 音乐库大标题取歌曲标签，小标题为歌手与专辑，时长单独放在文字与菜单之间。缺标签时回退去扩展名的文件名、未知歌手／专辑；未知时长显示 `—:—`。旧库后台补齐标签，搜索匹配歌曲名、文件名、歌手与专辑。小播放器、队列与详情共用这些信息。
- 音乐详情在主 Activity 内展开／收起，返回不停止播放。音乐页默认封面在左、歌词在右；横向手势与按钮都能切页。宽横屏并排展示封面与歌词。视频继续使用 PlayerActivity。
- 从真实封面按色相聚合、色度和亮度加权取色，限制饱和度／亮度后绘制多层径向柔光与底部渐变。没有持续循环背景动画；封面色只在读取完成后改变，避免换歌时闪到默认蓝。封面支持内嵌图片及已授权同目录的同名 JPG/PNG、cover.jpg/cover.png/folder.jpg。
- 歌词从真实播放位置同步：当前句突出、渐隐边缘、手动浏览短暂暂停跟随、回到当前歌词按钮、点句 seek、居中／左对齐、±0.5 秒偏移和重置。末句也能滚到一致阅读位置。没有歌词显示“无歌词”，提供添加与目录授权。下方独立紧凑控件支持播放／暂停、前后曲、进度、收藏、队列与倍速。
- 修复原 Media3 适配器的自动切歌投影：`onMediaItemTransition` 更新实际队列索引和 mediaGeneration，歌词请求、滚动及进度拖动用 URI／代次取消旧事件。自动播放下一曲由真实 Media3 引擎执行，UI 不模拟 EOF 或进度。

## 动画和交互依据

只读核对锁定 Miuix0.9.4 的 PagerGestureUtils、Slider、WindowDialog／SpinnerPreference 和图标源码。按钮切页使用 Miuix `springAnimateToPage`；拖动使用 Compose 原生 HorizontalPager，确保子控件与竖向歌词滚动能优先消费手势，落页弹簧 stiffness322.2、damping0.9。

元素甩尾由小幅、可中断 Compose 弹簧实现：封面340、标题210、歌词220，阻尼0.82；页面位移本身跟手，弹簧只增加有限元素滞后／轻微角度。展开收起280／0.86，按压480／0.68，主题色950ms，封面换图450ms、歌词强调350ms。该实现借鉴物理感，不宣称复刻 HyperOS 私有状态栏效果，也没有 FPS／功耗资格。

进度条为专用细轨道与可弹性变化的拇指，实际触控区48dp；一个手势循环处理点击／拖动／取消，竖向或第二指取消 seek，旧媒体松手不能 seek 新媒体。控件不在 Pager 内，不会被横翻页抢走。覆盖层建立完整窗口的被动触摸边界，空白区域不会点穿库的设置／导航；不消费子控件和 Pager 的事件。系统 animator scale=0 或应用减少动画关闭这些动效，没有新增无限计时或后台动画。

## 歌词与桌面版的对应

只读参考 `crates/player-core/src/lyrics.rs` 与 `crates/yyplayer-app/src/music_assets.rs`：手动关联优先于同名 LRC，同名 LRC 优先于内嵌标签；外部歌词损坏时保留内嵌结果并显示错误。时间戳毫秒精度、多时间戳、全局 offset ±600000ms、稳定重复时间、active 取最后匹配行、用户偏移和 seek 的反向补偿与桌面一致。重复时间的译文可同时强调；无时间戳文本按纯歌词显示。

文件上限1000000字节、10000时间行；UTF-8／BOM、UTF-16LE/BE、GB18030回退。标签读取上限16MiB，只读标签，不解码／缓存整曲；支持常见 ID3v2.3/2.4 USLT/TXXX、FLAC/Vorbis/Opus、可 seek MP4的 ©lyr（UTF-8/UTF-16BE）。压缩／加密／unsynchronised ID3、v2.2／逐词 SYLT 等未支持标签不造结果，可用 LRC/TXT。这里不是对桌面所有容器／标签变体逐一兼容的承诺。

Android 的单文件授权不自动授权旁边歌词。用户可从“添加音乐→选择音乐目录”导入，或在详情“授权歌曲目录”；使用 DocumentsContract 身份与实际返回的读／持久 flags，目录查找有上限、后台、可取消。手动关联按歌曲 URI 持久保存，取消选择不覆盖，坏文件／超大文件不替换旧关联。未申请全盘权限，未从文件名构造存储路径。

## 验证和证据

MuMu 现有 Android15/API35 实例，ADB `127.0.0.1:16384`，1080×1920。样片由 [生成脚本](../scripts/Generate-MusicUiFixtures.ps1) 创建，只有自有低电平合成 FLAC/WAV、原创 QA 封面与 LRC；不保存个人视频帧。素材 hash 和 APK hash 保存于 [artifacts.json](validation/ui-v6/artifacts.json)。

构建／纯测试／lint 与设备测试输出见 [final-checks](validation/ui-v6/final-checks.txt)。10 项纯策略／歌词测试；10 项直接设备测试涵盖真实 WAV 活动共享与主页内音乐、真实 EOF 后曲目／代次、真实文件手动关联／坏文件保护、内嵌标签／编码和原四项 TextureView 手势回归。

实际 SAF 目录导入并显示歌曲名／歌手／专辑／时长；真实 FLAC 播放、横滑翻页、同名 LRC、左／居中、点句与拖进度、无歌词和手动导入、切歌换色、库返回与再次进入均已操作。截图：[音乐库](validation/ui-v6/music-library.png)、[暖色封面](validation/ui-v6/cover-amber.png)、[自动歌词左对齐](validation/ui-v6/lyrics-auto.png)、[歌词居中](validation/ui-v6/lyrics-centered.png)、[手动歌词末句](validation/ui-v6/lyrics-manual.png)、[无歌词](validation/ui-v6/no-lyrics.png)。[实际动画录像](validation/ui-v6/music-motion.mp4) 展示原生渲染的横滑、换色与展开收起，没有生成中间帧；仅为模拟器观察，不是帧率资格。详细最后验收与未验项以 STATUS 为准。

结束前以普通无 extra 入口启动，模拟器实际横屏 1920×1080，已操作并检查 [横屏并排页面](validation/ui-v6/landscape.png)、视频库 2 项和音乐库 4 首／收藏 1 首。暂停并收起音乐后结束；没有更改系统方向／密度／字体／主题设置。

首次 Gradle `connectedDebugAndroidTest` 有9项断言通过，任务收尾 exit1，且默认卸载了 MuMu 的应用，导致其应用数据丢失；未把该任务写成成功。之后通过实际 SAF 恢复已观察的 QA 音频／视频与收藏，手机数据未修改；历史未备份偏好不能逐项保证。新增 [Test-MumuUi.ps1](../scripts/Test-MumuUi.ps1) 使用 `install -r`＋直接 `am instrument`，保留 APK／应用数据；未来不要在有用户数据的设备上用默认 UTP 卸载路径。

未新增 API26–32/R8、200%字体／TalkBack、物理120Hz多指／OEM动效、长时间／20k库性能、USB/native／后台MediaSession资格。模拟器结果不外推硬件。下一步按用户真实曲库与视觉反馈细调；后续核心仍按 S02，不能把本轮前端工作写成 HiFi 输出完成。
