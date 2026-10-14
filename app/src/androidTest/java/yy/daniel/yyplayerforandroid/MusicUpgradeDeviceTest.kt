package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.audio.*
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.LyricStyle
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real Media3 clock and decoded PCM, with isolated preferences and app-private generated fixtures. */
@UnstableApi
class MusicUpgradeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("u11_test_$name", mode)
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(label: String, test: () -> Boolean) {
        val end = System.nanoTime() + 12_000_000_000L
        while (System.nanoTime() < end) { var result = false; main { result = test() }; if (result) return; Thread.sleep(80) }
        fail("超时：$label")
    }
    private fun tracks(): List<LocalMedia> {
        val pcm = ByteBuffer.allocate(44 + 48_000 * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.put("RIFF".toByteArray()).putInt(pcm.capacity() - 8).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(48_000).putInt(96_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.capacity() - 44)
        repeat(96_000) { pcm.putShort((kotlin.math.sin(it * 2.0 * Math.PI * 440 / 48_000) * 180).toInt().toShort()) }
        return List(4) { index ->
            val file = File(context.cacheDir, "u11-test-$index.wav").apply { writeBytes(pcm.array()) }
            LocalMedia(Uri.fromFile(file), "测试$index.wav", MediaKind.AUDIO, "audio/wav", songTitle = "测试$index",
                artist = if (index < 2) "歌手甲" else "歌手乙", album = "同名专辑")
        }
    }
    @Test fun collectionsPersistAndAlbumsSeparateArtists() = runBlocking {
        context.getSharedPreferences("music_collections_v1", 0).edit().clear().commit()
        val media = tracks()
        assertEquals(2, groupMusic(media, true).size)
        assertEquals(2, groupMusic(media, false).size)
        val repository = MusicCollectionRepository(context)
        repository.update { it.create("通勤") }
        val id = repository.state.value.lists.single().id
        repository.update { it.add(id, media.map(LocalMedia::identity)) }
        repository.update { it.move(id, media.last().identity, -1).rename(id, "夜间通勤") }
        val fresh = MusicCollectionRepository(context).also { it.load() }
        assertEquals(repository.state.value, fresh.state.value)
        assertEquals(media.last().identity, fresh.state.value.lists.single().members[2])
        repository.update { it.delete(id) }
        assertTrue(media.all { File(it.uri.path!!).exists() })
        Unit
    }
    @Test fun bilingualStyleRoundTripAndOldPreferencesMigrate() {
        val original = LyricStyle(activeSize = 25f, inactiveSize = 20f, lineSpacing = 0f,
            translationActiveSize = 24f, translationSize = 16f, bilingualSpacing = 4f)
        assertEquals(original, LyricStyle.decode(original.encode()))
        val old = org.json.JSONObject(original.encode()).put("version", 1).apply {
            listOf("translationActiveSize", "translationSize", "translationWeight", "bilingualSpacing", "bilingual").forEach(::remove)
        }.toString()
        val migrated = LyricStyle.decode(old)
        assertEquals(25f, migrated.activeSize); assertEquals(0f, migrated.lineSpacing)
        assertEquals(8f, migrated.bilingualSpacing); assertEquals(20f, migrated.translationSize)
        assertTrue(runCatching { LyricStyle.decode(org.json.JSONObject(original.encode()).put("bilingualSpacing", 41).toString()) }.isFailure)
    }
    @Test fun realQueueModesEofAndEditingKeepTheCurrentPosition() {
        val media = tracks()
        lateinit var backend: AndroidMedia3PlaybackBackend
        main { backend = AndroidMedia3PlaybackBackend(context); backend.setPlayOrder(PlayOrder.SEQUENTIAL); backend.play(media) }
        try {
            await("播放时钟") { backend.state.value.isPlaying && backend.exoPlayer().currentPosition > 150 }
            main {
                backend.exoPlayer().pause()
                backend.seekTo(700)
                backend.moveQueueItem(0, 2)
                assertEquals(media[0], backend.state.value.current)
                assertEquals(700L, backend.state.value.positionMs)
                backend.removeQueueItem(0)
                assertEquals(media[0], backend.state.value.current)
                assertEquals(700L, backend.state.value.positionMs)
                backend.setPlayOrder(PlayOrder.SINGLE)
                backend.seekTo(1900); backend.exoPlayer().play()
            }
            await("单曲自动循环") { backend.exoPlayer().currentPosition < 1500 && backend.state.value.isPlaying }
            main {
                assertEquals(media[0], backend.state.value.current)
                backend.setPlayOrder(PlayOrder.SEQUENTIAL)
                backend.seekTo(1900)
            }
            await("顺序自动下一首") { backend.state.value.current == media[3] }
            main { backend.seekTo(1900) }
            await("顺序末尾停止") { backend.state.value.phase == PlaybackPhase.ENDED }
            main {
                backend.play(media)
                backend.setPlayOrder(PlayOrder.RANDOM)
                backend.exoPlayer().pause()
                val visited = mutableSetOf(backend.state.value.queueIndex)
                repeat(3) { backend.next(); assertTrue(visited.add(backend.state.value.queueIndex)) }
                val last = backend.state.value.current
                backend.previous(); backend.next(); assertEquals(last, backend.state.value.current)
                backend.jumpTo(0); backend.exoPlayer().pause()
                backend.enqueue(listOf(media[3]), next = true)
                assertEquals(media[0], backend.state.value.current)
                backend.next(); assertEquals(media[3], backend.state.value.current)
                assertEquals(4, backend.state.value.queue.size)
                backend.setPlayOrder(PlayOrder.SINGLE)
                backend.jumpTo(0); backend.next(); assertEquals(1, backend.state.value.queueIndex)
                backend.clearQueue(); assertTrue(backend.state.value.queue.isEmpty()); assertFalse(backend.state.value.isPlaying)
            }
        } finally { main { backend.close() } }
    }
    @Test fun nativeGaplessFailureRecoversOnSameSpeakerAndStrictDoesNot() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val speaker = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).first { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }.id
        val file = File(context.cacheDir, "u11-gapless.m4a")
        instrumentation.context.assets.open("u11-gapless.m4a").use { input -> file.outputStream().use(input::copyTo) }
        val media = LocalMedia(Uri.fromFile(file), "自有合成 AAC", MediaKind.AUDIO, "audio/mp4")
        lateinit var backend: AndroidMedia3PlaybackBackend
        main {
            backend = AndroidMedia3PlaybackBackend(context, outputPath = { OutputPath.NATIVE_EXACT }, selectedDeviceId = { speaker })
            backend.setPlayOrder(PlayOrder.SINGLE); backend.play(listOf(media))
        }
        try {
            await("native 编码延迟拒绝后系统真实播放") { backend.state.value.outputPath == OutputPath.SYSTEM && backend.state.value.isPlaying && backend.exoPlayer().currentPosition > 200 }
            main { assertNotNull(backend.state.value.outputFallback); assertEquals(PlayOrder.SINGLE, backend.state.value.playOrder); assertEquals(media, backend.state.value.current) }
        } finally { main { backend.close() } }
        val wav = tracks().first()
        main {
            backend = AndroidMedia3PlaybackBackend(context, outputPath = { OutputPath.NATIVE_EXACT }, selectedDeviceId = { speaker }, sharingRequest = { SharingRequest.STRICT_EXCLUSIVE })
            backend.play(listOf(wav))
        }
        try {
            await("严格独占拒绝") { backend.state.value.phase == PlaybackPhase.ERROR }
            main { assertEquals(OutputPath.NATIVE_EXACT, backend.state.value.outputPath); assertNull(backend.state.value.outputFallback); assertFalse(backend.state.value.isPlaying) }
        } finally { main { backend.close() } }
    }
    @Test fun preferExclusiveUsesSystemWhenExclusiveIsUnavailable() {
        val wav = tracks().first()
        lateinit var backend: AndroidMedia3PlaybackBackend
        main {
            backend = AndroidMedia3PlaybackBackend(context, outputPath = { OutputPath.NATIVE_EXACT }, sharingRequest = { SharingRequest.PREFER_EXCLUSIVE })
            backend.play(listOf(wav))
        }
        try {
            await("非独占设备系统播放") { backend.state.value.outputPath == OutputPath.SYSTEM && backend.state.value.isPlaying }
            main { assertNotNull(backend.state.value.outputFallback); assertNull(backend.state.value.negotiatedOutput) }
        } finally { main { backend.close() } }
    }
}
