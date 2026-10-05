package yy.daniel.yyplayerforandroid.online

import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ApiRequest(val url: String, val method: String = "GET", val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null)

/** HTTPS and explicit redirects: credentials stay on the exact original authority. */
internal fun connect(request: ApiRequest, timeout: Int, permitted: () -> Boolean,
    onConnection: (HttpURLConnection) -> Unit = {}): HttpURLConnection {
    var uri = secureUri(request.url)
    val origin = uri
    repeat(6) {
        check(permitted()) { "联网功能已关闭" }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        onConnection(connection)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeout; connection.readTimeout = timeout
            connection.requestMethod = request.method
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (uri.host == origin.host && uri.port == origin.port) request.headers.forEach(connection::setRequestProperty)
            request.body?.let { connection.doOutput = true; connection.outputStream.use { output -> output.write(it) } }
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                require(request.method == "GET") { "POST 重定向不受支持" }
                val location = connection.getHeaderField("Location") ?: error("重定向缺少地址")
                uri = secureUri(uri.resolve(location).toString()); connection.disconnect()
            } else {
                require(status in 200..299) { "服务返回 HTTP $status${if (status == 403) "，可能需要验证或访问授权" else ""}" }
                return connection
            }
        } catch (failure: Throwable) { connection.disconnect(); throw failure }
    }
    error("重定向次数过多")
}

internal suspend fun <T> network(request: ApiRequest, timeout: Int, permitted: () -> Boolean,
    read: suspend (HttpURLConnection) -> T): T = suspendCancellableCoroutine { continuation ->
    val active = java.util.concurrent.atomic.AtomicReference<HttpURLConnection?>()
    val worker = CoroutineScope(continuation.context + Dispatchers.IO).launch {
        try {
            val connection = connect(request, timeout, { continuation.isActive && permitted() }, { active.set(it); if (!continuation.isActive) it.disconnect() })
            val result = read(connection)
            ensureActive(); check(permitted()) { "联网功能已关闭" }
            continuation.resume(result)
        } catch (e: Throwable) { if (continuation.isActive) continuation.resumeWithException(e) }
        finally { active.get()?.disconnect() }
    }
    continuation.invokeOnCancellation { active.get()?.disconnect(); worker.cancel() }
}

internal suspend fun readBounded(connection: HttpURLConnection, limit: Int, permitted: () -> Boolean): ByteArray {
    require(connection.contentLengthLong <= limit) { "响应超过大小限制" }
    return connection.inputStream.use { input ->
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive(); check(permitted()) { "联网功能已关闭" }
            val count = input.read(buffer); if (count < 0) break
            require(output.size() + count <= limit) { "响应超过大小限制" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}
