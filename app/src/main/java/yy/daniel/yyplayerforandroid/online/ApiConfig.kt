package yy.daniel.yyplayerforandroid.online

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

data class ApiQuality(val id: String, val name: String, val value: String, val extension: String,
    val parameters: Map<String, String> = emptyMap(), val sources: Set<String> = emptySet(),
    val variables: Map<String, String> = emptyMap())
data class ApiEndpoint(val url: String, val method: String, val encoding: String,
    val parameters: Map<String, String>, val headers: Map<String, String>, val response: JSONObject,
    val body: JSONObject? = null)
data class ApiProvider(val id: String, val name: String, val baseUrl: String, val headers: Map<String, String>,
    val mediaHeaders: Map<String, Map<String, String>>, val sources: Map<String, String>, val qualities: List<ApiQuality>,
    val search: ApiEndpoint?, val stream: ApiEndpoint?, val download: ApiEndpoint?, val lyricSearch: ApiEndpoint?,
    val lyric: ApiEndpoint?, val timeoutMs: Int, val requestsPerWindow: Int, val windowSeconds: Int,
    val artwork: ApiEndpoint? = null, val variables: Map<String, String> = emptyMap(),
    val trackFields: Map<String, String> = emptyMap(), val pageSize: Int = 20,
    val apiHosts: Set<String> = emptySet())
data class ApiConfig(val providers: List<ApiProvider>) {
    companion object {
        const val MAX_BYTES = 256 * 1024
        fun parse(text: String): ApiConfig {
            require(text.toByteArray().size <= MAX_BYTES) { "配置超过 256KB" }
            checkJsonDepth(text)
            val root = JSONObject(text.trimStart('\uFEFF'))
            require(root.getInt("version") == 1) { "不支持的 API 配置版本" }
            val list = root.getJSONArray("providers")
            require(list.length() in 1..16) { "需要 1–16 个 API" }
            val providers = (0 until list.length()).map { index ->
                val p = list.getJSONObject(index)
                val id = p.getString("id"); require(id.matches(Regex("[a-zA-Z0-9_-]{1,48}"))) { "非法 API id" }
                val base = p.getString("baseUrl"); secureUri(base)
                val headers = headerMap(p.optJSONObject("headers"))
                val constants = stringMap(p.optJSONObject("variables"))
                val trackFields = stringMap(p.optJSONObject("trackFields"))
                require((constants.keys + trackFields.keys).all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,47}")) }) { "非法变量名" }
                val builtIns = setOf("query", "source", "page", "limit", "id", "lyricId", "picId", "title", "artist", "album", "quality")
                require(trackFields.keys.none { it in builtIns } && constants.keys.none { it in builtIns }) { "自定义变量与保留变量重复" }
                require(trackFields.keys.none { it in constants }) { "曲目变量与固定变量重复" }
                headers.values.forEach { template(it, (builtIns + constants.keys + trackFields.keys).associateWith { "test" }) }
                val apiHosts = p.optJSONArray("apiHosts")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty() + secureUri(base).host
                require(apiHosts.size <= 16 && apiHosts.all { it.matches(Regex("[A-Za-z0-9.-]+")) }) { "非法 API 主机" }
                fun endpoint(key: String): ApiEndpoint? = p.optJSONObject(key)?.let { e ->
                    val url = e.optString("url", base); require(url.startsWith("https://")) { "API 仅支持 HTTPS" }
                    val variables = (builtIns + constants.keys + trackFields.keys).associateWith { "test" }
                    val checked = secureUri(template(url, variables, url = true))
                    val origin = secureUri(base)
                    require(checked.host in apiHosts && (checked.port == -1 || checked.port == origin.port)) { "操作地址需位于 apiHosts 的 HTTPS 主机" }
                    val method = e.optString("method", "GET"); require(method in listOf("GET", "POST")) { "仅支持 GET/POST" }
                    val encoding = e.optString("encoding", "query")
                    require(encoding in listOf("query", "form", "json") && (method != "GET" || encoding == "query")) { "非法请求编码" }
                    val response = e.getJSONObject("response")
                    require(response.optString("mode", "json") in listOf("json", "text")) { "返回方式需为 json/text" }
                    if (key in listOf("search", "lyricSearch")) {
                        require(response.optString("mode", "json") == "json" && response.has("items")) { "搜索必须配置 JSON 条目路径" }
                        val fields = response.getJSONObject("fields")
                        require(fields.has("id") && fields.has("title")) { "搜索缺少 id/title 字段映射" }
                        stringMap(fields)
                    }
                    require(response.optString("durationUnit", "seconds") in listOf("seconds", "milliseconds")) { "非法时长单位" }
                    require(response.optString("sizeUnit", "bytes") in listOf("bytes", "KB")) { "非法文件大小单位" }
                    val parameters = stringMap(e.optJSONObject("parameters")); parameters.values.forEach { template(it, variables) }
                    val body = e.optJSONObject("body")
                    require(body == null || encoding == "json") { "body 仅用于 JSON 请求" }
                    body?.let { template(it.toString(), variables) }
                    val operationHeaders = headerMap(e.optJSONObject("headers"))
                    operationHeaders.values.forEach { template(it, variables) }
                    ApiEndpoint(url, method, encoding, parameters, operationHeaders, response, body)
                }
                val sources = stringMap(p.optJSONObject("sources")); require(sources.size in 1..16) { "需要音乐来源" }
                val q = p.optJSONArray("qualities") ?: JSONArray()
                require(q.length() <= 12) { "音质选项过多" }
                val qualities = (0 until q.length()).map { i -> q.getJSONObject(i).let {
                    val extension = it.optString("extension", "mp3")
                    require(extension in listOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "aiff", "ape")) { "非法文件扩展名" }
                    val applicableSources = it.optJSONArray("sources")?.let { a -> (0 until a.length()).map { n -> a.getString(n) }.toSet() }.orEmpty()
                    require(applicableSources.all { source -> source in sources }) { "音质来源未配置" }
                    val params = stringMap(it.optJSONObject("parameters"))
                    params.values.forEach { value -> template(value, (builtIns + constants.keys + trackFields.keys).associateWith { "test" }) }
                    val overrides = stringMap(it.optJSONObject("variables"))
                    require(overrides.keys.all { key -> key in constants }) { "音质变量需先在 provider.variables 定义" }
                    require(it.getString("id").matches(Regex("[A-Za-z0-9_-]{1,48}")) && it.getString("value").length in 1..256 && it.getString("name").length in 1..80) { "非法音质名称或参数" }
                    ApiQuality(it.getString("id"), it.getString("name"), it.getString("value"), extension, params, applicableSources, overrides)
                } }
                require(qualities.map { it.id }.distinct().size == qualities.size) { "音质 id 重复" }
                val media = p.optJSONObject("mediaHeaders")
                val scoped = media?.keys()?.asSequence()?.associateWith { host ->
                    require(host.matches(Regex("[a-zA-Z0-9.-]+"))) { "媒体头必须按精确主机配置" }
                    headerMap(media.getJSONObject(host))
                }.orEmpty()
                val search = endpoint("search"); val stream = endpoint("stream")
                val lyricSearch = endpoint("lyricSearch"); val lyric = endpoint("lyric")
                require((search != null && stream != null && qualities.isNotEmpty()) || (lyricSearch != null && lyric != null)) { "缺少音乐或歌词操作" }
                ApiProvider(id, p.getString("name").take(80), base, headers, scoped, sources, qualities,
                    search, stream, endpoint("download"), lyricSearch, lyric,
                    p.optInt("timeoutMs", 15000).also { require(it in 1000..60000) },
                    p.optInt("requestsPerWindow", 40).also { require(it in 1..1000) },
                    p.optInt("windowSeconds", 300).also { require(it in 1..3600) }, endpoint("artwork"), constants, trackFields,
                    p.optInt("pageSize", 20).also { require(it in 1..200) }, apiHosts)
            }
            require(providers.map { it.id }.distinct().size == providers.size) { "API id 重复" }
            return ApiConfig(providers)
        }
    }
}

internal fun stringMap(json: JSONObject?): Map<String, String> = json?.keys()?.asSequence()?.associateWith { key ->
    require(key.length <= 128 && json.get(key) is String) { "参数必须为字符串" }
    json.getString(key).also { require(it.length <= 8192) }
}.orEmpty().also { require(it.size <= 64) }

internal fun headerMap(json: JSONObject?): Map<String, String> = stringMap(json).also { map ->
    map.forEach { (key, value) ->
        require(key.matches(Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]+")) && '\r' !in value && '\n' !in value) { "非法访问头" }
        require(key.lowercase() !in listOf("host", "content-length", "connection", "transfer-encoding", "range")) { "访问头由客户端管理" }
    }
}

internal fun secureUri(url: String): URI = URI(url).also {
    require(it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null && it.fragment == null && url.length <= 8192) { "需要有效 HTTPS 地址" }
}

internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
private val variablePattern = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
internal fun template(text: String, variables: Map<String, String>, url: Boolean = false): String = variablePattern.replace(text) {
    val value = variables[it.groupValues[1]] ?: error("配置使用了未知变量 ${it.groupValues[1]}")
    if (url) encode(value) else value
}

/** Dot-separated object keys and array indices; no executable JSONPath expressions. Empty path = root. */
internal fun field(root: Any?, path: String): Any? {
    if (path.isEmpty()) return root
    require(path.length <= 256)
    return path.split('.').fold(root) { node, segment ->
        when (node) { is JSONObject -> node.opt(segment); is JSONArray -> segment.toIntOrNull()?.let(node::opt); else -> null }
    }?.takeUnless { it == JSONObject.NULL }
}
internal fun fieldText(root: Any?, path: String): String = when (val value = field(root, path)) {
    is JSONArray -> (0 until value.length()).joinToString(" / ") { value.optString(it) }
    null -> ""
    else -> value.toString()
}

/** Bound parser recursion for imported configs and untrusted service responses. */
internal fun checkJsonDepth(text: String) {
    var depth = 0; var quoted = false; var escaped = false
    text.forEach { c ->
        if (quoted) {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 32) { "JSON 嵌套过深" } }
            '}', ']' -> depth--
        }
    }
}
