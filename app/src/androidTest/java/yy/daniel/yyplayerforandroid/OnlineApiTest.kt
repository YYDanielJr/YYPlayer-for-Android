package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import yy.daniel.yyplayerforandroid.online.*
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File
import java.util.UUID

class OnlineApiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun defaultConfig() = context.assets.open("online-providers.json").bufferedReader().use { it.readText() }
    @Test fun rejectsUnsupportedVersionHttpAndHeaderInjection() {
        fun reject(change: (JSONObject) -> Unit) {
            val root = JSONObject(defaultConfig()); change(root)
            assertThrows(IllegalArgumentException::class.java) { ApiConfig.parse(root.toString()) }
        }
        reject { it.put("version", 2) }
        reject { it.getJSONArray("providers").getJSONObject(0).put("baseUrl", "http://example.com") }
        reject { it.getJSONArray("providers").getJSONObject(0).put("headers", JSONObject().put("Authorization", "Bearer key\r\nX-Evil: 1")) }
        reject { it.getJSONArray("providers").getJSONObject(0).getJSONArray("qualities").getJSONObject(0).put("extension", "../bin") }
        assertEquals("GD音乐台", ApiConfig.parse(defaultConfig()).providers.single().name)
    }
    @Test fun independentGatesRejectBeforeAnyNetwork() = runBlocking {
        isolated { c ->
            val repository = OnlineRepository(c, { false }, { false }); repository.load()
            try { repository.search("gd-studio", "netease", "test"); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("关闭")) }
            try { repository.search("gd-studio", "netease", "test", lyrics = true); fail() } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("关闭")) }
        }
    }
    @Test fun richConfigurationPreservesTypedBodiesAndTrackVariables() {
        val root = JSONObject(defaultConfig()); val p = root.getJSONArray("providers").getJSONObject(0)
        p.put("trackFields", JSONObject().put("token", "access.token"))
        p.getJSONObject("stream").put("method", "POST").put("encoding", "json").put("body",
            JSONObject("""{"track":"{id}","options":{"token":"{token}","count":5,"enabled":true,"qualities":["{quality}",999]}}"""))
        val parsed = ApiConfig.parse(root.toString()).providers.single()
        val body = renderBody(parsed.stream!!.body!!, mapOf("id" to "a\"&中文", "token" to "safe", "quality" to "740")) as JSONObject
        assertEquals("a\"&中文", body.getString("track"))
        assertTrue(body.getJSONObject("options").getBoolean("enabled"))
        assertEquals(5, body.getJSONObject("options").getInt("count"))
        assertEquals(999, body.getJSONObject("options").getJSONArray("qualities").getInt(1))
        assertEquals(5, parsed.qualities.size)
    }
    @Test fun actualProviderFallbackCannotBeSelectedAsHighResolutionQuality() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        isolated { c ->
            val r = OnlineRepository(c, { true }, { false }); importFixture(r, c)
            val song = r.search("yyplayer-qa", "netease", "音质回退").last().copy(id = "fallback")
            val options = r.qualityOptions(song, true)
            assertEquals(listOf("128", "192", "320", "740", "999"), options.map { it.quality.id })
            assertFalse(options.last().available)
            assertTrue(options.last().detail.contains("320"))
            assertTrue(options.first { it.quality.id == "320" }.available)
        }
    }
    @Test fun defaultMusicDetectsMislabelledFlacSavesCoverAndReloadsOwnedLibrary() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        isolated { c ->
            val r = OnlineRepository(c, { true }, { true }); importFixture(r, c)
            assertNull(r.downloadTree)
            val unique = "YYPlayerOwnFLAC-${UUID.randomUUID()}"
            val song = r.search("yyplayer-qa", "netease", unique).last().copy(id = "mislabelled")
            val created = mutableListOf<Uri>()
            try {
                val q = r.provider("yyplayer-qa").qualities.last()
                val lyrics = MusicLyricsRepository(c)
                val result = r.download(song, q, null, lyrics)
                created += result.media.uri
                assertEquals("$unique - YYPlayer QA.flac", result.media.title)
                assertEquals("audio/flac", result.media.mimeType)
                assertTrue(result.warning!!.contains("16bit"))
                assertTrue(result.lyricSaved)
                assertNotNull(result.media.artworkUri)
                assertTrue(File(result.media.artworkUri!!.path!!).isFile)
                assertTrue(lyrics.read(result.media).lyrics.lines.isNotEmpty())
                context.contentResolver.openInputStream(result.media.uri)!!.use { assertEquals("fLaC", String(it.readBytes().take(4).toByteArray())) }
                context.contentResolver.query(result.media.uri, arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.provider.MediaStore.MediaColumns.IS_PENDING, android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!.use {
                    assertTrue(it.moveToFirst()); assertEquals("Music/", it.getString(0)); assertEquals(0, it.getInt(1)); assertEquals(result.media.title, it.getString(2))
                }
                val library = MediaLibraryRepository(c)
                library.addDownloaded(result.media)
                val reloaded = library.load().single()
                assertEquals(result.media.uri, reloaded.uri)
                assertEquals(result.media.artworkUri, reloaded.artworkUri)
                val second = r.download(song, q, null, lyrics)
                created += second.media.uri
                assertEquals("$unique - YYPlayer QA (2).flac", second.media.title)
                assertEquals(result.media.uri, library.load().single().uri)
                // Cancel a real transfer; this download's pending MediaStore row must disappear.
                fun rows(): Set<String> = context.contentResolver.query(android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    arrayOf(android.provider.MediaStore.MediaColumns._ID), null, null, null)!!.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                val before = rows()
                try { r.download(song, q, null, lyrics) { _, _ -> throw CancellationException("Cancel real Music transfer") }; fail() }
                catch (_: CancellationException) { assertEquals(before, rows()) }
            } finally { created.forEach { context.contentResolver.delete(it, null, null) } }
        }
    }
    @Test fun invalidImportKeepsPreviousConfigurationAndCachedLyricsAreOffline() = runBlocking {
        isolated { c ->
            val repository = OnlineRepository(c, { false }, { false }); repository.load()
            val invalid = File(c.cacheDir, "bad-${UUID.randomUUID()}.json").apply { writeText("{\"version\":900}") }
            try { repository.importConfig(Uri.fromFile(invalid)); fail() } catch (_: Exception) { assertEquals("gd-studio", repository.config.value!!.providers.single().id) }
            finally { invalid.delete() }
            val lyrics = MusicLyricsRepository(c)
            val media = LocalMedia(Uri.parse("https://example.com/song.wav"), "测试歌", MediaKind.AUDIO, "audio/wav")
            lyrics.saveOnline(media, "[00:01.00]离线仍可读", selected = true)
            assertEquals("离线仍可读", lyrics.read(media).lyrics.lines.single().text)
        }
    }
    @Test fun realHttpsSearchResolveLyricsHeadersAndRedirectIsolation() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        isolated { c ->
            var music = true; var lyricsEnabled = true
            val repository = OnlineRepository(c, { music }, { lyricsEnabled })
            importFixture(repository, c)
            val songs = repository.search("yyplayer-qa", "netease", "原创测试")
            assertEquals(2, songs.size); assertEquals("YYPlayer QA", songs.last().artist); assertEquals(12000L, songs.last().durationMs)
            val candidates = repository.search("yyplayer-qa", "netease", "原创测试", lyrics = true)
            assertEquals("原创测试", candidates.first().title)
            val quality = repository.provider("yyplayer-qa").qualities.last()
            val resolved = repository.resolve(songs.last(), quality)
            assertTrue(resolved.reportedQuality.contains("999"))
            assertTrue(repository.lyric(candidates.first()).contains("原创测试歌词"))
            music = false
            assertEquals(2, repository.search("yyplayer-qa", "netease", "歌词独立", lyrics = true).size)
            lyricsEnabled = false
            try { repository.lyric(candidates.first()); fail() } catch (_: IllegalStateException) { }
            val redirected = network(ApiRequest("https://localhost:18443/redirect", headers = mapOf("Authorization" to "test-only")), 5000, { true }) {
                JSONObject(readBounded(it, 1024, { true }).toString(Charsets.UTF_8))
            }
            assertFalse(redirected.getBoolean("leaked"))
        }
    }
    @Test fun realHttpsMedia3PlaybackSeekAndDisable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        val songs = runBlocking {
            // Isolated repository configuration; real app preferences/configuration are not touched.
            isolated { c -> val r = OnlineRepository(c, { true }, { true }); importFixture(r, c); r.search("yyplayer-qa", "netease", "流式测试") }
        }
        lateinit var backend: AndroidMedia3PlaybackBackend
        var allowed = true
        instrumentation.runOnMainSync {
            backend = AndroidMedia3PlaybackBackend(context) { allowed }
            backend.play(listOf(LocalMedia(Uri.parse("https://localhost:18443/audio.wav"), songs.last().title, MediaKind.AUDIO, "audio/wav",
                mediaHeaders = mapOf("localhost" to mapOf("X-QA-Media" to "fixture-media")))))
        }
        try {
            val deadline = android.os.SystemClock.elapsedRealtime() + 15000
            while (!backend.state.value.isPlaying && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertTrue(backend.state.value.error.orEmpty(), backend.state.value.isPlaying)
            instrumentation.runOnMainSync { backend.seekTo(7000) }
            Thread.sleep(250)
            instrumentation.runOnMainSync { backend.refreshPosition(); assertTrue(backend.state.value.positionMs >= 6900); allowed = false; backend.stopOnline(); assertFalse(backend.state.value.isPlaying) }
        } finally { instrumentation.runOnMainSync { backend.close() } }
    }
    @Test fun realSafDownloadKeepsAudioAndSiblingLyrics() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineQa") == "true")
        val treeText = InstrumentationRegistry.getArguments().getString("downloadTree")
        assumeTrue(treeText != null)
        val tree = Uri.parse(treeText)
        require(DocumentsContract.getTreeDocumentId(tree) == "primary:Download/YYPlayerOnlineQA")
        isolated { c ->
            val repository = OnlineRepository(c, { true }, { true }); importFixture(repository, c)
            val song = repository.search("yyplayer-qa", "netease", "专用下载测试").last()
            val result = repository.download(song, repository.provider("yyplayer-qa").qualities.first(), tree, MusicLyricsRepository(c))
            try {
                assertTrue(result.media.title.endsWith(".wav")); assertTrue(result.lyricSaved)
                val bytes = context.contentResolver.openInputStream(result.media.uri)!!.use { it.readBytes() }
                assertEquals(2116844, bytes.size); assertEquals("RIFF", String(bytes, 0, 4)); assertEquals("WAVE", String(bytes, 8, 4))
                assertTrue(MusicLyricsRepository(c).read(result.media).lyrics.lines.isNotEmpty())
            } finally {
                val parent = DocumentsContract.getTreeDocumentId(tree)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
                context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) if (cursor.getString(1) in listOf(result.media.title.substringBeforeLast('.') + ".lrc", result.media.title.substringBeforeLast('.') + ".jpg"))
                        DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)))
                }
                DocumentsContract.deleteDocument(context.contentResolver, result.media.uri)
            }
            fun children(): Set<String> {
                val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                return buildSet { context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                } }
            }
            val beforeCancel = children()
            try {
                repository.download(song, repository.provider("yyplayer-qa").qualities.first(), tree, MusicLyricsRepository(c)) { _, _ -> throw CancellationException("Cancel after first real chunk") }
                fail("Download should cancel")
            } catch (_: CancellationException) { assertEquals("Cancellation must remove its partial file", beforeCancel, children()) }
        }
    }
    private suspend fun importFixture(repository: OnlineRepository, c: Context) {
        val root = JSONObject(defaultConfig()); val p = root.getJSONArray("providers").getJSONObject(0)
        p.put("id", "yyplayer-qa").put("baseUrl", "https://localhost:18443/api").put("requestsPerWindow", 1000)
            .put("headers", JSONObject().put("X-QA-Api", "fixture-only"))
            .put("mediaHeaders", JSONObject().put("localhost", JSONObject().put("X-QA-Media", "fixture-media")))
        val file = File(c.cacheDir, "qa-import-${UUID.randomUUID()}.json").apply { writeText(root.toString()) }
        try { repository.importConfig(Uri.fromFile(file)) } finally { file.delete() }
    }
    private suspend fun <T> isolated(block: suspend (Context) -> T): T {
        val id = "online-test-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, id).apply { mkdirs() }
        val preferenceNames = mutableSetOf<String>()
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getNoBackupFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val scoped = "$id-$name"; preferenceNames += scoped
                return context.getSharedPreferences(scoped, mode)
            }
        }
        try { return block(isolated) } finally { directory.deleteRecursively(); preferenceNames.forEach(context::deleteSharedPreferences) }
    }
}
