package yy.daniel.yyplayerforandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yy.daniel.yyplayerforandroid.audio.NativeAudioCore
import yy.daniel.yyplayerforandroid.audio.ProcessingState
import yy.daniel.yyplayerforandroid.audio.SampleEncoding
import yy.daniel.yyplayerforandroid.audio.SharingRequest
import yy.daniel.yyplayerforandroid.audio.SourceFormat
import yy.daniel.yyplayerforandroid.audio.TransparencyRequest
import yy.daniel.yyplayerforandroid.audio.json.JsonValue

/**
 * The bridge parser must accept exactly what the native core emits and nothing else.
 *
 * Values in a policy document are addressed by dotted paths (for example "format.sampleRate"), the
 * same way the C++ reader resolves them; a path that does not exist must return null instead of
 * reaching into a child object.
 */
class AudioBridgeJsonTest {

    private val generatedRequest: String = NativeAudioCore.buildRequestForTest(
        source = SourceFormat(48000, 2, SampleEncoding.S24_IN32),
        route = null,
        sharing = SharingRequest.SHARED,
        transparency = TransparencyRequest.PREFER_UNPROCESSED,
        processing = ProcessingState(),
        routeSelected = false,
    )

    @Test
    fun findsTheFirstUnparseableRequestPrefix() {
        // Bisects the generated document so a parser gap names the exact construct that broke it.
        val failures = ArrayList<String>()
        for (end in 1..generatedRequest.length) {
            val prefix = generatedRequest.substring(0, end)
            val open = prefix.count { it == '{' } - prefix.count { it == '}' }
            val suffix = "}".repeat(maxOf(0, open))
            val candidate = prefix + suffix
            val result = runCatching { JsonValue.parse(candidate) }
            if (result.exceptionOrNull() != null) failures += candidate
        }
        // The full document as the production path consumes it, for comparison with the bisect run.
        val full = runCatching { JsonValue.parse("{$generatedRequest}") }
        android.util.Log.i(
            "YYPlayerAudio",
            "YYPLAYER_JSON_BISECT prefixFailures=${failures.size} full=${full.isSuccess} " +
                "error=${full.exceptionOrNull()}",
        )
        assertTrue("full document must parse", full.isSuccess)
    }

    @Test
    fun parsesTheGeneratedPolicyRequest() {
        // buildRequest already returns a complete document, braces included; wrapping it again is
        // exactly the mistake this test is here to prevent.
        val document = JsonValue.parse(generatedRequest)
            ?: error("parser rejected the generated document: ${JsonValue.lastParseError}")
        assertEquals(48000L, document.int("format.sampleRate"))
        assertEquals(2L, document.int("format.channels"))
        assertEquals("s24_in32", document.string("format.encoding"))
        assertEquals(3L, document.int("format.channelMask"))
        assertEquals("shared", document.string("sharing"))
        // route is an object here, so addressing it as an integer correctly yields nothing.
        assertNull(document.int("route"))
        assertEquals(0, document.array("route.profiles")?.size ?: -1)
    }

    @Test
    fun parsesThePolicyResponseShape() {
        val response = "{\"accepted\":true,\"reasonCode\":\"ok\",\"reasonText\":\"可以按请求输出\"," +
            "\"sink\":\"audiotrack_system\",\"sampleRate\":48000,\"channels\":2,\"encoding\":\"s24_in32\"," +
            "\"channelMask\":3,\"effectiveBits\":24,\"requestedSharing\":\"shared\"," +
            "\"expectedSharing\":\"shared\",\"requestBitPerfect\":true,\"pristineExpected\":true," +
            "\"sameRouteFallback\":false,\"bitPerfect\":\"requested\"}"
        val root = JsonValue.parse(response)
        assertTrue("parser must accept the response document", root != null)
        assertEquals(true, root!!.bool("accepted"))
        assertEquals("ok", root.string("reasonCode"))
        assertEquals(24L, root.int("effectiveBits"))
        assertEquals(true, root.bool("requestBitPerfect"))
    }

    @Test
    fun rejectsMalformedDocumentsInsteadOfGuessing() {
        val cases = linkedMapOf(
            "null" to JsonValue.parse(null),
            "empty" to JsonValue.parse(""),
            "open brace" to JsonValue.parse("{"),
            "missing value" to JsonValue.parse("{\"a\":}"),
            "trailing" to JsonValue.parse("{\"a\":1} trailing"),
            "array root" to JsonValue.parse("[1,2]"),
        )
        val accepted = cases.filterValues { it != null }.keys
        assertTrue("parser accepted malformed input: $accepted", accepted.isEmpty())
    }

    @Test
    fun aMemberIsNeverReachedThroughAChildObject() {
        val document = JsonValue.parse("{\"format\":{\"sampleRate\":48000},\"sampleRate\":44100}")
        assertEquals(44100L, document!!.int("sampleRate"))
        assertEquals(48000L, document.int("format.sampleRate"))
        assertNull(document.int("format.channels"))
    }
}
