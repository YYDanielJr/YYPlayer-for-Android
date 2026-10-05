# YYPlayer for Android 执行规则

本文件适用于整个Android工程，面向后续人类与代码代理。用户明确指令优先。用户已将任务从规划切换为实现，并指定本轮用 MuMu Android 模拟器验收“真实前后端跑通 + UI”；随 STATUS 更新实际进展，不能把未实现的目标写成现有能力。

## 1. 接手顺序和当前任务

2026-10-05 最新用户任务为 U08，按 docs/UI_SETUP_AND_LIBRARY.md 和 STATUS 接手：媒体库分页与页头视觉——页头（大标题、右侧图标、40dp 轮廓 TabRow）固定在 HorizontalPager 之外，只有 TabRow 下方内容左右切换；标题与图标透明度跟随 pager 偏移量渐隐渐显并在两页交界换字；滚动时大标题收成栏内小标题；库页自持 scroll behavior。**用户已复核满意，本轮无待改视觉项。** 下述 U07／U06／U05 为已实现基线。

2026-10-05 前轮用户任务为 U07，按 docs/UI_SETUP_AND_LIBRARY.md 和 STATUS 接手：首次引导／顶部轮廓TabRow／仅播放玻璃底栏及Sink与环形进度／封面阴影与Tilt／500ms歌词切换／歌词样式／长按多选。该轮要求覆盖旧底部纯图标导航规则，其他弹窗／生命周期规则保留。

2026-10-05 前轮用户任务：在 dev-api 继续 U06.6–U06.9 下载体验，丰富 JSON 参数与 GD 五档音质、默认 Music／自选 SAF 目录、自动专辑图、文件签名与准确命名。原 U06 两个独立默认关闭的联网开关、在线搜索和歌词候选保留。设计见 docs/ONLINE_API.md 与 ADR0005；U05/U04 规则继续保留，native gate不抢先。按 STATUS 实际证据接手。

1. 阅读 README、docs/STATUS.md、docs/TECHNICAL_DESIGN.md，再读 docs/IMPLEMENTATION_PLAN.md 的当前阶段。
2. 查看git status、实际目录与上次证据；用户已授权将 U05 基线提交到 master（80538db），并创建 dev-api。后续不清理用户文件，不自动提交／推送新改动。
3. U05音乐前端作为基线保留：标签歌曲名/歌手/专辑/独立时长、MainActivity内音乐详情、封面色背景/渐变/分层弹簧、同目录/手动/内嵌歌词与同步控制；规则/证据见docs/UI_MUSIC_EXPERIENCE.md和ADR0004。U04弹窗按压铺满、U02纯图标栏回退规则继续保留，见docs/UI_DIALOG_FEEDBACK.md；U03设置边距与方向字重保留。视频/设置/方向的U02原生Activity基线见docs/UI_COMPONENTS_AND_MOTION.md与ADR0003，音乐例外以ADR0004为准。U01截图仍为参考，不代表用户最终认可。E00真实Media3/SAF链路保留；USB DAC、API26、HiFi/native仍待验，不用假快照或mock代替播放。
4. 每轮写清任务ID/子步骤、文件、验收，完成具体可审查结果。被设备/工具阻塞时继续不依赖它的工作，记录未验条件。
5. 更新STATUS的实际结果和下一具体动作，最后中文说明实现/验证/限制。

常规实现、可逆修改和检查不需要本文件额外批准；不要以“请确认是否继续”停止已授权工作。用户只要求规划时，不安装工具、不新增功能、不提交/发布。没有明确请求不创建其他用户聊天；本项目没有要求多代理委派。

## 2. 架构已锁定

- Kotlin + Jetpack Compose + Miuix；Android平台与数据服务用Kotlin。
- 长期可移植媒体核心仍是C++17 + C ABI、JNI薄桥；首版不引入Rust工具链。桌面Rust项目只读参考，不直接依赖绝对path，不修改其源码。
- 长期后端仍是视频libmpv/FFmpeg/MediaCodec/libass、音乐FFmpeg PCM + AudioTrack/Oboe；两个后端互斥，不能并发占同一输出。
- **当前安卓纵切按 [ADR 0001](docs/adr/0001-android-media3-vertical-slice.md) 临时用 Media3 ExoPlayer 播放本地音视频。** 它实现真实播放和可选 MediaCodec 解码器，不是最终 HiFi/native 后端。`PlaybackBackend` 是当前 UI 命令/状态边界，后续保持该意图接口并逐步替换实现。
- 不创建空Player冒充通知；本轮尚未实现 MediaSession/后台 service。
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
- native受控LinuxCI构建。mpv-android不是AAR，其上游脚本不支持Windows/WSL；不能添加不存在的Maven坐标或直接执行自动安装脚本。
- 所有so均做16KB ELF/ZIP检查和真实装载，不仅JNI壳。runtime下载先校验再解压、路径校验、原子替换。
- 不改全局环境/系统配置，不把local.properties、keystore、token、下载native包提交为公共源码。

## 4. 线程与生命周期硬规则

- UI只发命令/投影StateFlow；不在Compose、Activity、applicationLooper做扫描、tag读取、decode、阻塞mpv或worker join。
- 只有native engine worker执行普通mpv API；回调只非阻塞唤醒；数据跨边界前复制。
- Kotlin Player适配器全部调用在规定applicationLooper；Accepted不是完成，匹配Ack后再完成future。
- Oboe realtime callback没有alloc/JNI/IO/log/mutex，不关闭重开stream；错误交worker。
- 普通命令队列有界64，Stop保留路径、Shutdown可唤醒；高频seek/transform合并；不能随意丢设置命令。
- service拥有唯一controller/session；Activity/Composable不创建第二套引擎；当前U02前台Media3适配器按ADR0003暂由PlayerApplication共享，S05迁入service后不能保留双引擎；队列自动next只有native controller执行。
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

## 7. 文件、数据与UI

- content URI是身份；DisplayName不是路径；SAF持久授权基于实际flags；禁止MANAGE_EXTERNAL_STORAGE默认全盘访问。
- native源用明确FD/streamlease；dup共享offset，不能并行lseek；非seekable处理取消/顺序播放/显式缓存导入，禁止静默复制整库。
- scanner/图片/歌词有上限、后台、取消revision；旧结果不覆盖；库和queue分开、移除库不删除磁盘文件。
- 配置version/migration/badbackup、类型和范围校验；未知能力/错误保持可恢复，历史保存合并且静默。
- 正式UI只用真实snapshot；Preview/mock只在Preview/tests/debugprobe，不能凭timer伪造进度、音质或硬解状态。
- Miuix依赖包装在designsystem，主题令牌集中；不要把所有页面又换Material3。小米风格不使用受限字体/商标/私有API。
- U01 视觉以用户 references 为准：滚动大标题、灰白/暗色分组、磨砂悬浮音乐/视频双分页、独立搜索、真实媒体封面，播放/设置是子页；不恢复 E00 的宣传渐变 Hero 与常驻四项底栏。Raidian 只读参考，不修改其文件。
- U02界面图标必须来自MiuixIcons；窗口用WindowDialog/WindowSpinnerPreference，show=false时保留Composition直到退出动画完成。视频/设置/方向二级页真实Activity入栈，正常路径不自定义覆盖ROM转场；音乐按用户U05授权在主Activity内自行绘制转场，见ADR0004。参考仓库只读。
- 当前U04卡片外12dp/行内16dp，文字总左边距28dp；HyperDialog WindowDialog水平insideMargin=0，交互行内部24dp，统一使用HyperSettingRow/HyperSpinnerPreference令牌。clickable/indication放在文字padding之前，让反馈铺满弹窗；取消/文件标题单独24dp。不要恢复整个内容区水平24dp导致两条白边。方向三选项17sp/SemiBold保留；用户否定U03相册效果并授权回退。U07已用顶部TabRow替换旧的FloatingNavigationBar纯图标底栏（U08把页头固定在分页之外），不要恢复图文底栏或常驻四项导航，也不要声称已达完美相册效果。库页当前：轮廓TabRow 40dp、“全部音乐／收藏”卡片横向内边距16dp、竖向13dp。
- miuix-blur0.9.4 的 minSdk33 冲突按 ADR 0002 处理：普通 UI 不暴露 blur 类型；只在 API33+进入 Api33MiuixBlur，API26–32不透明回退。不得把 manifest override 当兼容已验证，不提高 minSdk，也不能把高版本调用移回通用文件。
- 明暗/设计/强调色/reduceMotion独立；系统scale0和后台停止持续动画，封面色只按revision更新；日常播放页不堆implementation参数。
- 48dp触控、中文/200%字体/TalkBack/暗色对比度；手势有按钮替代。亮度仅window，不申请WRITE_SETTINGS。
- 高级mpv设置白名单，禁止任意脚本/filter/shell/无限缓存；网络TLS不关闭验证。

## 8. 检验与完成定义

按IMPLEMENTATION_PLAN当前阶段做必要编译/单测/集成/真机；新版本查锁定源码，不编造API。纯文档无需应用编译或新增测试。

可移植native逻辑主机CTest；JNI/API26类加载/服务/手势Compose测试；输出/DAC/硬解/双指物理操作/16KB/HDR真机。模拟器成功不替代设备资格。

性能用release、固定device/ROM/runtime/options/fixturehash与口径；不能拿不同默认质量的播放结果比较。测试只用自有/授权fixture、独立配置；不写用户media或全局系统设置。

每次STATUS至少写：任务/子步骤、文件、运行命令与exit结果、设备/素材/证据、未执行检查、已知问题、下一动作。只有代码+要求证据齐备才Done，硬件不足记Partial/待验；不把Luna不能完成当验收标准变宽。

当前优先验收U08媒体库分页/页头（已获用户确认）并保留U07引导／导航／控件／歌词样式／多选与U05音乐前端，保留U04反馈/导航规则；不能因为旧计划的“下一步S02”跳过新要求。若用户之后继续完整产品核心实现，再进入S02 native依赖和JNI/Surface gate。保留Media3适配器作为对照，先证明原生runtime可构建/装载再替换。U08遗留：实体机上`LibraryTabsUiTest`／`LibraryManagementUiTest`尚未跑通（`am instrument`崩溃或无输出，需换MuMu或先查崩溃）；API26–32磨砂隔离、物理多指、DAC仍需后续验收；debug qa_portrait仅是截图辅助，正常启动没有该行为。任何已有数据的设备更新用install -r，不清除导入/偏好；默认connectedDebugAndroidTest本轮曾卸载MuMu应用，之后用scripts/Test-MumuUi.ps1的直接instrumentation保留数据。UI取材仅保留必要局部，不保存个人视频帧。
