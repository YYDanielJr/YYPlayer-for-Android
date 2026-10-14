# YYPlayer for Android 执行规则

本文件适用于整个 Android 工程，面向后续人类与代码代理。用户明确指令优先。当前为实现阶段；最新功能基线为 2026-10-14 的 `dev` U15–U20（在 `607ceb3` 的 U11–U14 上继续实现；提交身份以 git log 为准），不要将历史计划写成已实现能力。

## 1. 接手顺序和当前任务

1. 先读 README、docs/STATUS.md 顶部，再读 docs/UI_MUSIC_EXPERIENCE.md、docs/TECHNICAL_DESIGN.md 和 docs/IMPLEMENTATION_PLAN.md 的当前基线；音频工作另读 docs/HANDOFF_HIFI.md 与 ADR0006。带日期的早期记录为历史证据，不能覆盖最新用户要求。
2. 检查 git status、分支与证据。**所有修改在 `dev`；不存在时创建 `dev`。master 只接受经授权的合入，禁止直接开发。** dev-hifi 的 H02–H05／U09–U10 已保存为 `c75b34d` 并合入 dev；U11–U14 已提交为 `607ceb3`；U15–U19 已提交为 `d551f38`，用户明确授权本轮 U20 统一 8× 及文档提交到 dev，未授权推送／合入 master。master 当前基线 `836a920`。不清理用户文件；没有新授权不自动提交／推送／合并。
3. 当前音乐能力：歌曲／歌手／专辑／歌单分类，收藏入口在“歌单 → 我的收藏”；库、歌单和播放队列独立。独立队列 Activity 支持单／多选移除、长按排序；顺序／单曲／随机播放由真实后端实现。主页底栏只留播放／暂停，不恢复下一首和“全部音乐／收藏”快捷卡片。双语歌词及自定义译文字号／字重／原译间距按 U11 保留。
4. 音乐前端基线为 U14：封面展开和收回均保留轻微回弹，收回终点继续使用同一绘制层；不能以删除回弹规避闪现。透明详情退出后不可点击，快速重开不得误弹菜单。9° 角落按压是缓慢、无触底回弹的另一套效果。按钮／列表令牌见第 7 节。U08 固定页头与顶部 40dp TabRow、U04 弹窗反馈等已确认基线继续保留。
5. 当前验收设备为**有线小米 13 Ultra `c9e352e3`／`2304FPN6DC`／API36／OS3.0.308.0.WMACNXM**；每次先枚举设备，不把旧无线地址当作当前连接。用户已说明有线占用 USB，本轮没有 USB DAC；历史 H05 DAC 证据不能套用到此设备。MuMu 可补专项，但不替代物理设备资格。
6. 视频最新基线为 U20：可选 libmpv、字幕／章节／同步／逐帧，横屏半透明右侧抽屉、真实滚动与逐级返回；工具内倍速 0.1×–8×／步进 0.1×，外侧仍为 0.5／1／1.25／1.5／2／3。Media3 与 libmpv 统一最高 8×；不按倍速自动切换后端，不再为仅硬解提供单独倍速范围。见视频体验与 ADR0007／0008。
7. 每轮写清任务／文件／命令与 exit 结果／设备素材／证据／未验条件和下一动作，更新 STATUS，最后中文说明实现、验证与限制。设备阻塞时继续不依赖设备的工作；没有用户最终确认时不写“视觉已满意”。

常规实现、可逆修改和检查不需要本文件额外批准；不要以“请确认是否继续”停止已授权工作。用户只要求规划时，不安装工具、不新增功能、不提交／发布。没有明确请求不创建其他用户聊天；本项目没有要求多代理委派。

## 2. 架构已锁定

- Kotlin + Jetpack Compose + Miuix；Android平台与数据服务用Kotlin。
- 长期可移植媒体核心仍是C++17 + C ABI、JNI薄桥；首版不引入Rust工具链。桌面Rust项目只读参考，不直接依赖绝对path，不修改其源码。
- 长期后端仍是视频libmpv/FFmpeg/MediaCodec/libass、音乐FFmpeg PCM + AudioTrack/Oboe；两个后端互斥，不能并发占同一输出。
- **当前安卓纵切按 [ADR 0001](docs/adr/0001-android-media3-vertical-slice.md) 临时用 Media3 ExoPlayer 播放本地音视频。** 它实现真实播放和可选 MediaCodec 解码器，不是最终 HiFi/native 后端。`PlaybackBackend` 是当前 UI 命令/状态边界，后续保持该意图接口并逐步替换实现。
- 已实现 `PlayerSessionService : MediaSessionService` 和真实 MediaSession；service 持有 runtime 唯一引擎的生命周期引用，重建 Player 时同步 session。禁止创建空 Player 冒充通知或在 Activity／队列页面另建播放器。
- H01–H05 已接入 C++ PCM 核心／C ABI／JNI 与可选自研 AudioTrack AudioSink，音乐普通解码仍为 MediaCodec／Media3，FFmpeg PCM／Oboe 仍是后续目标；U16 视频已接可选 libmpv／FFmpeg／libass，默认仍 Media3，见 ADR0006／0007；不能把自研 AudioSink 说成自研解码器或已实现独占。
- 不自写视频时钟/解码器/字幕引擎，不从libmpv公开client API编造PCM回调。
- Android app继续使用 `yy.daniel.yyplayerforandroid`、minSdk26、compile/target37。native首发arm64-v8a，x86_64做模拟器。
- 默认SDR TextureView保证双指任意角旋转；SurfaceView是独立功耗/HDR资格路径。不能删掉连续旋转来换实现容易。

改变最终默认内核/输出路径/语言/呈现方式先写docs/adr：事实、候选、决定、影响、验证。API不方便不是换内核的证据。ADR 0001 已记录本轮可逆的 Android 纵切例外及 native 替换退出条件。

## 3. 版本与构建

- S00已按官方 Android 坐标接入 Miuix0.9.4；Kotlin2.4.20、Compose BOM2026.09.00、Media3 1.11.1 已锁 catalog，实际完整构建与 lint 仍以 STATUS 为准。不能混旧miuix坐标与新miuix-ui API。
- AGP9内置Kotlin，禁止照旧教程重复添加kotlin-android；实际KGP与Compose compiler一致，升级有dependencyInsight证据。
- 当前AGP9.4.1/Gradle9.6.0够用，不为了追新升级；BOM编号、KSP、Room等版本查官方/实际artifact后锁定，禁止编造“最新版本”。
- native候选NDK28.2.13676358/CMake3.22.1、mpv0.41.0、FFmpeg8.1.3、Oboe1.11.0；必须过S02兼容gate，冻结完整SHA/hash/patch/options/headers/SONAME/许可。
- 同进程只用一套FFmpeg和libc++；不从WindowsDLL、用户PATH、其他APK取得未知so。
- native受控LinuxCI构建。mpv-android 应用不是 AAR；本工程 U16 使用不同项目 libmpv-android 的真实坐标 `dev.jdtech.mpv:libmpv:1.0.0`，哈希、ABI、共用 libc++ 与许可限制按 ADR0007 冻结。不能添加不存在的坐标或执行上游自动安装脚本；完整源码／许可证和可复现发布 gate 仍未关闭。
- 所有so均做16KB ELF/ZIP检查和真实装载，不仅JNI壳。runtime下载先校验再解压、路径校验、原子替换。
- 不改全局环境/系统配置，不把local.properties、keystore、token、下载native包提交为公共源码。

## 4. 线程与生命周期硬规则

- UI只发命令/投影StateFlow；不在Compose、Activity、applicationLooper做扫描、tag读取、decode、阻塞mpv或worker join。
- 只有native engine worker执行普通mpv API；回调只非阻塞唤醒；数据跨边界前复制。
- Kotlin Player适配器全部调用在规定applicationLooper；Accepted不是完成，匹配Ack后再完成future。
- Oboe realtime callback没有alloc/JNI/IO/log/mutex，不关闭重开stream；错误交worker。
- 普通命令队列有界64，Stop保留路径、Shutdown可唤醒；高频seek/transform合并；不能随意丢设置命令。
- runtime 提供唯一引擎，service 持有生命周期引用和 session；Activity／Composable 不创建第二套引擎。当前自动切歌／单曲循环／随机序列由 Media3 timeline 管理，UI 不另写 EOF 自动 next；未来迁入 native controller 时也只能有一个自动推进者。
- mediaGeneration、surfaceGeneration、queueRevision、requestId分开；旧open/EOF/路由/封面/松手事件不能覆盖新媒体。
- Surface按冻结mpv Androidcontext使用Java GlobalRef，不盲转ANativeWindow或纹理ID；VO确认不再使用后才能releaseSurface/GlobalRef。
- surface detach不能在主线程等待；持lease直到Ack，超时走受控销毁，不提前释放让mpv用旧指针。
- 音视频互切先停止flush并确认旧输出释放，再启动新输出；无视频音轨双引擎时钟方案。
- C ABI不暴露STL/mpv/FFmpeg/JNI/Rust类型；handle registry防旧指针；输入/返回buffer/FD所有权明确；JNIEnv不跨线程保留。
- close幂等、取消IO/回调、join、native销毁顺序有证据；不能exit/abort掩盖清理缺陷。

## 5. 音频真实性

- 音频焦点 ≠ 流独占 ≠ PCM未处理 ≠ 端到端位准确。sharing和transparency独立，严格条件同时满足。
- 源format、decoderformat、APIformat、hardwareformat分开；不可得字段Unknown。setPreferredMixerAttributes/open成功不直接显示已验证。
- AudioTrack BIT_PERFECT只在API34+支持的USB组合使用，不当通用独占证明；Oboe读取实际SharingMode，不从请求推断。
- StrictUnprocessed须有匹配的系统BIT_PERFECT或合格Direct契约；只有OboeExclusive且HAL处理未知时拒绝。PreferUnprocessed不自动取消用户gain/EQ或改满音量。
- 默认DSP关闭、源格式优先、稳定缓冲；不强制192k/32bit、不以低延迟/最大位宽宣传最佳音质。
- 严格失败暂停并分类报错；宽松回退仅同route且有原因；设备失联立即pause/flush，不继续向扬声器写入。不得承诺公开API保证插拔瞬间零泄漏。
- 严格未处理与软件volume/EQ/ReplayGain/SRC/变速/crossfade互斥；不能为了bitperfect突然把低音量设满；设备音量不可用时显示真实限制。
- integer无处理路径不统一转float32；24packed、24in32、planar、interleaved明确；降低位深/dither/downmix撤销原样资格。
- PCM packing有极值/随机/声道/endian/对齐测试；帧/字节/时间64位溢出检查。AudioTrack短写处理、timestamp回绕/归零正确。
- Oboe欠载计数；非预期补零/重采样后不能保持本次capture资格。strict异常先暂停。
- EQ固定named chain、平直旁路、参数/Nyquist/余量检查、profile回滚；不累加滤镜。
- 数字capture才给特定设备组合CaptureVerified；DAC采样率屏、dumpsys、模拟录音、听感和sink前hash不等同端到端证明。
- USB Direct在S12做UAC/libusb等时传输，不用bulkTransfer占位；DoP/DSD未经专门验证不输出普通PCM设备。

## 6. 视频和手势

- Auto/PreferHardware/StrictHardware/Software与copy许可分开；实际hwdec-current为事实；严格硬解不能偷退soft。
- 解码切换保存position/playIntent/轨道identity/baseSpeed/延迟/变换；重载Ack匹配generation，失败可恢复。
- 方向由有效display ratio、SAR/crop/rotation决定，防旋转两次；auto每媒体一次、manual优先；sw600/API37忽略requestedOrientation时布局仍可用。
- 长按是绝对3×临时override，原2×松手回2×；结束、取消、第二指、失焦、导航、PiP、换媒体、surface丢失统一恢复；旧token不可改新媒体。
- 一个GestureArbiter决定消耗pointer，不堆互抢detector。控件优先、系统返回边缘保留；锁定仅留解锁/返回。
- 双指连续缩放/旋转/平移，锚点和viewport矩阵一致；scale0.5–5、归一化pan/resize/bounds；TextureView一次变换，不能同时叠mpvzoom/rotate。
- 禁止CPU逐帧RGBA回读/上传、视频逐帧PixelCopy模糊、普通Bitmap循环充当生产视频。
- 字幕轨能力与ASS/SRT行为说清，首版libass随画面变换。素材HDR/SDR映射/原生HDR输出三种资格分开。
- 控件覆盖视频并自动隐藏，seek/长按/菜单时保持可操作；隐藏不改变surface尺寸。
- 横屏抽屉正常打开时不得在 Final pass 消费子级触摸；只在退出时 Initial pass 拦截。滚动必须有真实 swipeUp／swipeDown 证据，performScrollTo 不能代替手指测试。子菜单提供屏幕内返回箭头与真实父级历史，完成／遮罩关闭整个抽屉。
- 视频自定义倍速滑杆松手生效、加减 0.1×；快捷 1.25× 不被全局量化。真实后端限速必须查证，不能只改标签；绝对 3× 松手恢复基础速度，含 0.1×／8×。音乐原有快捷范围与精确输出拒绝变速规则保留。

## 7. 文件、数据与UI

- content URI是身份；DisplayName不是路径；SAF持久授权基于实际flags；禁止MANAGE_EXTERNAL_STORAGE默认全盘访问。
- native源用明确FD/streamlease；dup共享offset，不能并行lseek；非seekable处理取消/顺序播放/显式缓存导入，禁止静默复制整库。
- scanner/图片/歌词有上限、后台、取消revision；旧结果不覆盖；库和queue分开、移除库不删除磁盘文件。
- 配置version/migration/badbackup、类型和范围校验；未知能力/错误保持可恢复，历史保存合并且静默。
- 正式UI只用真实snapshot；Preview/mock只在Preview/tests/debugprobe，不能凭timer伪造进度、音质或硬解状态。
- Miuix依赖包装在designsystem，主题令牌集中；不要把所有页面又换Material3。小米风格不使用受限字体/商标/私有API。
- U01 视觉以用户 references 为准：滚动大标题、灰白/暗色分组、磨砂悬浮音乐/视频双分页、独立搜索、真实媒体封面，播放/设置是子页；不恢复 E00 的宣传渐变 Hero 与常驻四项底栏。Raidian 只读参考，不修改其文件。
- U02界面图标必须来自MiuixIcons；窗口用WindowDialog/WindowSpinnerPreference，show=false时保留Composition直到退出动画完成。视频/设置/方向二级页真实Activity入栈，正常路径不自定义覆盖ROM转场；音乐按用户U05授权在主Activity内自行绘制转场，见ADR0004。参考仓库只读。
- 当前U04卡片外12dp/行内16dp，文字总左边距28dp；HyperDialog WindowDialog水平insideMargin=0，交互行内部24dp，统一使用HyperSettingRow/HyperSpinnerPreference令牌。clickable/indication放在文字padding之前，让反馈铺满弹窗；取消/文件标题单独24dp。不要恢复整个内容区水平24dp导致两条白边。方向三选项17sp/SemiBold保留；用户否定U03相册效果并授权回退。U07已用顶部TabRow替换旧的FloatingNavigationBar纯图标底栏（U08把页头固定在分页之外），不要恢复图文底栏或常驻四项导航，也不要声称已达完美相册效果。库页当前轮廓 TabRow 40dp；U13 已移除“全部音乐／收藏”卡片，不恢复旧卡片。
- U12–U14 音乐 UI：整组歌曲只有首行顶部／末行底部圆角；封面／歌词页底部统一倍速、收藏、播放顺序、队列四项。队列使用独立 MusicQueueActivity 与 ROM 转场，音乐详情仍在 MainActivity 内；操作不要退回分散小胶囊。
- 操作按钮统一 HyperActionButton：页面最小 52dp、18sp／24sp 行高，弹窗最小 50dp、17sp／22sp 行高，Medium 500、16dp 圆角；并排等宽、间隔 12dp，取消／次要灰底、确认主题色，放大字体允许增高。图标保持 48dp 触控；截图不能证明第一方精确字重，不复制受限字体。
- 封面转场：展开 spring 180／0.86，收回 230／0.8，阈值 0.0001；几何保留带符号位移，透明度单独限幅。收回后共享层持续画底栏封面，避免终点换图；保留 Initial pass 输入拦截和快速反向锚点处理。按压单独使用临界阻尼 1／刚度 95、最大 9°，不可混入转场回弹。
- 音频回退：PreferExclusive 在现有不能证明独占的 AudioTrack 后端选择 SYSTEM；非严格 native sink 配置／初始化／写入失败，仅在同一输出仍连接时单次回退并保留播放意图。StrictExclusive／StrictUnprocessed 拒绝静默降级；拔线不能触发扬声器回退。
- miuix-blur0.9.4 的 minSdk33 冲突按 ADR 0002 处理：普通 UI 不暴露 blur 类型；只在 API33+进入 Api33MiuixBlur，API26–32不透明回退。不得把 manifest override 当兼容已验证，不提高 minSdk，也不能把高版本调用移回通用文件。
- 明暗/设计/强调色/reduceMotion独立；系统scale0和后台停止持续动画，封面色只按revision更新；日常播放页不堆implementation参数。
- 48dp触控、中文/200%字体/TalkBack/暗色对比度；手势有按钮替代。亮度仅window，不申请WRITE_SETTINGS。
- 高级mpv设置白名单，禁止任意脚本/filter/shell/无限缓存；网络TLS不关闭验证。

## 8. 检验与完成定义

按IMPLEMENTATION_PLAN当前阶段做必要编译/单测/集成/真机；新版本查锁定源码，不编造API。纯文档无需应用编译或新增测试。

可移植native逻辑主机CTest；JNI/API26类加载/服务/手势Compose测试；输出/DAC/硬解/双指物理操作/16KB/HDR真机。模拟器成功不替代设备资格。

性能用release、固定device/ROM/runtime/options/fixturehash与口径；不能拿不同默认质量的播放结果比较。测试只用自有/授权fixture、独立配置；不写用户media或全局系统设置。

每次STATUS至少写：任务/子步骤、文件、运行命令与exit结果、设备/素材/证据、未执行检查、已知问题、下一动作。只有代码+要求证据齐备才Done，硬件不足记Partial/待验；不把Luna不能完成当验收标准变宽。

当前视频基线为 U15–U20，音乐保留 U11–U14；具体下一任务由最新用户指令决定，不因旧计划“下一步 S02”自行扩展到完整 native 替换。已通过：U14 最终 assembleDebug／assembleDebugAndroidTest／34 项 JVM／lint（0 error、2 既有 warning）；U13 MusicUpgradeDeviceTest 5 项真实播放回归；小米普通 UI 与录屏复核。细节与 APK hash 见 docs/validation/ui-u14 和 ui-u13。U19 构建／37 项 JVM／lint 和 MuMu 视频、libmpv、手势、音乐回归结果以 docs/validation/video-u19 为准；U20 上限及不切换后端的专项见 docs/validation/video-u20。实体视频高速解码／听感、API26–32 与原生发布资格仍待验。

仍待验：该 ROM 上 Compose Activity suite 挂起／崩溃或无输出（LibraryTabsUiTest、LibraryManagementUiTest 等不能计作通过）；API26–32、横屏／大字体／TalkBack／深色专项、物理多指、release 帧率，以及独占／位准确硬件资格。U14 录屏能证明回弹和无可见换图，不证明帧率达标；用户手感反馈仍是后续依据。

已有数据的设备一律 install -r，不清除导入／偏好；避免会卸载数据的 connectedDebugAndroidTest，采用直接 instrumentation（MuMu 可用 scripts/Test-MumuUi.ps1）。测试使用独立偏好与自制素材，默认不动用户媒体／全局设置。debug qa_portrait 仅为截图辅助，正常启动没有该行为。UI 证据只保留必要局部，不保存个人视频帧；原参考截图和临时抽帧留在忽略的 build 目录。
