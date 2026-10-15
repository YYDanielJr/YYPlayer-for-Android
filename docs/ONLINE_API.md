# U06：在线音乐与歌词 API

U26／0.1.5：在线歌曲播放和搜索页控制条均在当前搜索 Activity 内展开共享封面动画，收起恢复原查询／结果／滚动位置；不再压入新 MainActivity。结果 ViewModel 支持配置重建，旧的 U23 压栈描述为历史。联网歌词页内切歌刷新候选并取消旧请求。


2026-10-15 U23：搜索结果直接播放、选择播放音质均保留原搜索 Activity，在返回栈中打开音乐封面。收起／系统返回回到原搜索条件、结果与滚动位置，流播放继续；主页入口仍收回主页，嵌套队列页逐级返回。验证见 [U23](validation/navigation-u23/README.md)。

2026-10-05，`dev-api`。在 U05 音乐前端基础上增加可配置联网服务，决策见 [ADR0005](adr/0005-configurable-online-music.md)。实际构建／设备结果以 [STATUS](STATUS.md) 为准。

## 使用

设置 → 联网服务：分别开启“联网搜索歌曲”和“联网搜索歌词”。两个开关默认关闭，互不依赖。仅开启歌词时仍可为本地歌曲搜索歌词，不显示音乐在线搜索。关闭歌曲联网停止当前串流；已下载的音频和歌词仍可离线使用。

音乐库右上角“在线搜索”打开独立 Activity。顶部搜索框和来源信息下显示本地搜索历史；输入关键词后按键盘搜索或搜索图标发请求。来源按钮可切换 provider 与 source。搜索加载时在内容区居中显示 Miuix `InfiniteProgressIndicator()`。结果左侧为真实专辑封面，歌名／歌手／专辑文字保留原样式，点击整条歌曲直接播放；右侧仅一个“…”菜单，含“选择播放音质／下载歌曲”。直接播放采用配置顺序中首个满足来源与歌曲声明限制的音质；服务实际拒绝时显示失败，可在菜单另选音质，不冒充已经取得更高音质。歌曲只通过现有共享 Media3 引擎播放，不持久化临时播放 URL 或新增引擎。音质弹窗明确“请求音质”；详情菜单同时显示服务报告，不能据此宣称源格式、输出无损或位准确。

2026-10-08 U09：页面右上“…”或搜索历史“管理”可控制“保留搜索历史”、删除单条、清除全部。默认保留，关闭后不新增记录但不自动删除已有记录。历史最多20条、按最近提交排序、trim且忽略大小写去重，最长200字符；在提交新搜索时记录，翻页不新增，清除后不会被迟到的网络结果重新写回。独立版本化本地偏好 `online_search_history_v1` 仅保存关键词和开关，不保存 API 凭据或播放地址，歌词搜索不写音乐历史。点击历史立即重新搜索。结果封面按可见条目后台加载，最多2个缩略图任务、4MB内存缓存；封面失败使用图标占位。串流地址取得后先播放，专辑图在 runtime 中独立补齐并匹配 mediaGeneration，避免图片请求阻塞开始播放。验收见 [U09 记录](validation/ui-online-search/README.md)。

下载默认保存到系统 `Music/`（Android10/API29及以上），设置 → 歌曲下载目录可选择任意有持久读写授权的 SAF 文件夹，或恢复默认 Music。Android8–9/API26–28 首次下载会打开以 Music 为初始位置的授权选择器。有界下载先写未完成文件，MediaStore 用 IS_PENDING 隐藏，SAF 用 `.partial`；取消／失败清理本次文件。成功加入本地音乐库。开启联网歌词时，串流与下载自动用该歌曲的 lyricId 获取歌词；失败保留可播放的歌曲。默认 Music 的歌词与封面保存在应用内关联（不会冒用 MediaStore 写入非媒体 LRC）；自选 SAF 文件夹另尽力写同名 LRC／JPG。歌曲名称为 `<歌曲名> - <歌手>.<实际格式>`，仅将路径分隔符等非法字符替换为 `_`；仅重名时追加 `(2)` 等序号，不再附加随机字符串。扩展名以音频文件头为准，支持 FLAC（包括 STREAMINFO 位深）、MP3、WAV、M4A、OGG／Opus、AAC、AIFF、APE；不根据 MIME、地址后缀或文件大小猜测，未识别格式会报错并清理。用户从页面返回会取消正在处理的下载；当前没有后台下载服务／断点续传。

音乐详情 → 歌词与播放 → “联网获取歌词”。自动以当前歌曲标题搜索，同名候选优先展示，再由用户选择；可修改搜索框，选择其他 API／来源。歌词与歌曲 generation 绑定，换歌关闭候选页；失败不覆盖原关联。选中候选后保存于应用私有目录，返回后 U05 歌词页立即显示。手动候选选择可以覆盖原关联；自动下载保留原手动／同目录优先级。关闭歌词联网隐藏入口，保留所有本地读取。在线曲目使用 `provider/source/id` 稳定身份，URL 更新仍能找到歌词。

## 为什么选择 JSON

Android 内置 `org.json`，无需增加 TOML 解析依赖；嵌套请求／响应／音质映射可以直接表达。配置是声明式数据，不运行脚本。参考 [LX 自定义源契约](https://lxmusic.toside.cn/desktop/custom-source) 将源、动作、音质与结果分离；LX 的 JS 事件／脚本格式不能直接导入。

内置示例：[online-providers.json](../app/src/main/assets/online-providers.json)，来源 **GD音乐台(music.gdstudio.xyz)**。本轮依据用户提供的 GD 官方文档（更新日期2026-09-16）配置 search/url/pic/lyric，稳定来源 netease／joox／bilibili，五档128／192／320／740（请求16bit无损）／999（请求24bit无损）。客户端保守限流40次／300秒，低于文档50次／5分钟上限；打开一个曲目的音质菜单最多检查五次 URL，成功地址缓存60秒且有条目上限，避免选中后重复解析。用户反馈搜索已经可用；当前代码验收使用自有 HTTPS 素材，不把该证据当成 GD 每首歌所有档位的供给保证。GD 文档要求 CC BY-NC 4.0、注明出处、仅供学习，示例保留出处和用途字段。

若此前手动导入过旧配置，私有副本继续优先，不自动改写用户接口／访问头；请更新原 JSON 或重新导入新版示例以启用五档音质和专辑图操作。未导入过配置时直接使用更新后的内置示例。

## 配置格式 v1

根对象 `version: 1`，`providers` 数组（1–16 个），provider ID 唯一。设置中的“API 配置 → 导入 JSON”使用系统文件选择器，验证后原子替换私有副本；不依赖原文件后续授权，不自动监视原文件。坏配置保留原配置并提示。配置及 headers 在 `noBackupFilesDir`，不进日志或云备份；源码示例不含账号秘密。

| provider 字段 | 含义 |
| --- | --- |
| `id`, `name` | 稳定标识、展示名称 |
| `baseUrl` | HTTPS 基地址；默认操作同主机，额外 API 主机须显式列入 apiHosts |
| `headers` | API 访问头，例如 Accept、Authorization、X-API-Key；可含已定义的模板变量，展开后再次检查换行等非法内容 |
| `mediaHeaders` | 精确媒体主机到 header 对象的映射；不把 API 凭证自动发给 CDN |
| `sources` | source ID → 显示名称；可配置多个源 |
| `qualities` | 最多12个 `{id,name,value,extension,parameters?,variables?,sources?}`；value 替换 `{quality}`，parameters 覆盖该档位请求参数，variables 覆盖已声明常量，sources 限制适用来源；extension 保留兼容提示，不能决定下载最终扩展名 |
| `timeoutMs` | 1000–60000ms，默认15000 |
| `requestsPerWindow`, `windowSeconds` | provider 级滚动窗口限制；默认40次／300秒，超出直接提示，搜索不做自动重试 |
| `search`, `stream`, `download` | 搜索／播放 URL 解析／可选下载 URL 解析；缺少 download 复用 stream |
| `lyricSearch`, `lyric` | 独立候选搜索／歌词获取；可在纯歌词 provider 中配置 |
| `artwork` | 可选专辑图 URL 解析操作；支持独立 URL、参数与 headers |
| `variables` | 自定义常量，如 artworkSize、区域、客户端版本；最多64项字符串 |
| `trackFields` | 自定义曲目变量 → 搜索条目字段路径，如 token: access.token，后续操作可用 `{token}` |
| `pageSize` | 1–200，默认20；用于 `{limit}` 和分页判断 |
| `apiHosts` | 可选额外 HTTPS API 主机白名单；端口只允许默认 HTTPS 或 baseUrl 的端口 |

每个操作包含 `url`（省略则使用 baseUrl）、`method`（GET/POST，默认GET）、`encoding`（query/form/json，默认query）、`parameters`（字符串字典）、`headers`（覆盖 provider 头）、可选 `body` 和 `response`。GET 只允许 query；POST 可带 query、表单或 JSON body。URL 中的变量单独编码，query/form 参数键值 UTF-8 编码，`body` 可声明嵌套 JSON 对象／数组／number／boolean／null，只替换字符串叶子并由 JSON 编码器转义；有 body 时以它作为请求正文，parameters 不参与正文。未声明 body 的 JSON 操作保持原字符串字典，避免中文／`&`／引号破坏请求。

变量：`{query}`, `{source}`, `{page}`, `{limit}`（provider.pageSize）、`{id}`, `{lyricId}`, `{picId}`, `{title}`, `{artist}`, `{album}`, `{quality}` 及 variables／trackFields 声明的变量。常量与曲目变量不能重名或覆盖保留变量，未知变量拒绝导入。Host／Range／长度等头由客户端管理。首版不支持 OAuth 登录刷新、签名计算或 JS 代码，需要此类 API 时应设计独立有类型适配器。

| response 字段 | 含义 |
| --- | --- |
| `mode` | json（默认）或 text；搜索必须 json |
| `items` | 搜索列表路径；空字符串代表响应根数组 |
| `fields` | 结果映射：id/title 必需，artist/album/source/lyricId/picId/artworkUrl/duration/qualities 可选；数组歌手合并展示 |
| `durationUnit` | seconds（默认）或 milliseconds；未知时长保持 null |
| `url` | stream/download 的 URL 字段路径；text 返回纯 URL 时用空字符串 |
| `quality` | 服务报告音质路径，例如 br；未给出则显示未提供 |
| `lyric` | LRC/TXT 字段路径；text 纯歌词时用空字符串 |
| `translation` | 可选翻译 LRC 路径，如 GD 的 tlyric；同时间原文与翻译合并展示 |
| `size`, `sizeUnit` | 服务报告大小路径、bytes（默认）或 KB；下载仍核对实际传输长度 |
| `format` | 服务报告格式路径；最终文件类型仍通过文件头识别 |

搜索条目 `fields.qualities` 可映射音质 ID／value 数组或逗号分隔字符串；存在时与配置、quality.sources 取交集。菜单再检查该曲目每个候选 URL；返回空地址／错误／其他已配置音质时置为不可用，没有档位报告但有地址时显示“服务未确认此档位”。服务报告仅用于可用性匹配，不当24bit或输出无损的证明。

路径为点分隔的对象字段／数组索引，例如 `data.results`、`data.0.url`。不支持过滤表达式、带点字段名、base64 歌词、加密响应或逐词专用格式。纯歌词服务也使用统一候选模型，将 ID／标题映射到 fields，lyricId 缺省用 id；不要求它提供歌曲播放地址。

示例 POST：

```json
"lyricSearch": {
  "url": "https://api.example.com/lyrics/search",
  "method": "POST", "encoding": "json",
  "body": {"title": "{query}", "options": {"page": "{page}", "limit": 20, "exact": false}},
  "headers": {"X-API-Key": "填写你自己的密钥"},
  "response": {"items": "data.results", "fields": {"id": "id", "title": "title", "artist": "artist"}}
}
```

下载接口返回可取的 HTTPS 音频 URL，客户端负责 Range／文件传输；直链服务可配置 stream 的 URL 请求返回 text，`response.url: ""`。目前支持渐进式音频，不宣称 HLS/DASH/DRM 支持。

## 边界与验收

本轮四项体验修改的真实结果见 [STATUS](STATUS.md) 和 [online-v2 验收目录](validation/online-v2/)。保留前轮 online-v1 历史证据，当前测试增加真实 FLAC（故意错误 .mp3／audio/mpeg）、默认 Music、重名保护、取消清理、专辑图和服务音质回退。

API 配置上限256KiB／JSON深度32，API响应2MiB／单次200条，歌词1MB／10000行，专辑图输入8MiB／边长8192上限／解码缩采样1024，单首下载512MiB／文件头识别前256KiB。IO均在后台，页面／切源取消前次请求；两个开关在仓库和 UI 都校验。TLS 使用系统证书验证；重定向最多6次、拒绝降至 HTTP、不同主机不携带原鉴权头，POST重定向拒绝。

`scripts/Serve-OnlineFixtures.py --prepare` 仅在 app/build 创建短期 localhost CA／私钥、自有低电平12秒WAV／FFmpeg编码FLAC／原创LRC及500×500几何PNG的配置。仅显式 `-PonlineQa=true` 的 debug 构建信任生成的本机证书；普通 debug/release 没有这些资源，不更改系统信任或设置。启动该脚本，`adb reverse tcp:18443 tcp:18443` 后，直接 instrumentation 加 `-e onlineQa true` 运行自有 HTTPS 测试。真实响应、真实 Media3 position、SAF 下载和可视截图与第三方 GD 可达性分别记录。

### 分档参数示例

```json
"variables": {"codec": "mp3", "artworkSize": "500"},
"trackFields": {"token": "access.token"},
"qualities": [
  {"id": "high", "name": "高品质", "value": "320", "extension": "mp3", "sources": ["sourceA"], "parameters": {"codec": "mp3", "br": "{quality}"}},
  {"id": "lossless", "name": "无损", "value": "lossless", "extension": "flac", "variables": {"codec": "flac"}}
],
"stream": {
  "method": "POST", "encoding": "json",
  "body": {"track": "{id}", "token": "{token}", "quality": {"level": "{quality}", "codec": "{codec}"}, "options": {"download": false}},
  "response": {"url": "data.url", "quality": "data.level", "size": "data.bytes", "sizeUnit": "bytes"}
}
```

quality.parameters 针对 query／form／无 body 的 JSON 字典；嵌套 body 使用 quality.variables 覆盖常量。此片段需与完整 provider 的 sourceA 来源、search 字段映射和 baseUrl 一起使用。

默认 Music 使用方式依据 [Android 官方共享媒体存储文档](https://developer.android.com/training/data-storage/shared/media)，没有新增全盘存储权限。应用内封面不是写入音频文件的嵌入式标签；用户选择的 SAF 目录提供可移植同名 JPG。未实现 JS 签名／OAuth 刷新／加密歌词、HLS／DASH／DRM、后台续传；API26–28／实体设备另待专项验证。
