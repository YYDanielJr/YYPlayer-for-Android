package yy.daniel.yyplayerforandroid

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.VideoGestureSettings
import yy.daniel.yyplayerforandroid.ui.VideoSeekPreview
import java.io.FileInputStream

/** Real Activity + platform MotionEvents: no Compose/Espresso idle synchronization on HyperOS. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class VideoPhoneDeviceTest {
    private val i = InstrumentationRegistry.getInstrumentation()
    private val context = i.targetContext
    private lateinit var activity: PlayerActivity
    private lateinit var backend: AndroidMedia3PlaybackBackend
    private val uri get() = when (InstrumentationRegistry.getArguments().getString("fixture")) {
        "1080p" -> Uri.fromFile(java.io.File(context.getExternalFilesDir(null), "u21-1080p.mp4"))
        "u22" -> Uri.fromFile(java.io.File(context.getExternalFilesDir(null), "u22-circle.mp4"))
        else -> Uri.parse("content://${i.context.packageName}.u21fixture/video")
    }
    private fun main(block: () -> Unit) = i.runOnMainSync(block)
    private fun await(label: String, timeout: Long = 15_000, check: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < until) {
            var ready = false
            main { backend.refreshPosition(); assertNull(backend.state.value.error); ready = check() }
            if (ready) return
            Thread.sleep(25)
        }
        fail("$label: ${backend.state.value}")
    }
    @Before fun start() {
        check(context.packageName.endsWith(".qa")) { "Use -PdeviceQa=true; never alter the user's release preferences" }
        VideoGestureSettings().save(context)
        if (uri.scheme == "content") i.uiAutomation.executeShellCommand("content gettype --uri $uri")
            .use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        // HyperOS denies an instrumented background UID launching an Activity. Bring the exported
        // launcher to the foreground through adb's normal shell permission, without changing ROM policy.
        i.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/yy.daniel.yyplayerforandroid.MainActivity")
            .use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        Thread.sleep(350)
        activity = i.startActivitySync(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PlayerActivity
        main {
            backend = (context.applicationContext as PlayerApplication).runtime.playback
            backend.setVideoEngine(VideoEngine.MEDIA3)
            backend.play(listOf(LocalMedia(uri, "U21 自制视频", MediaKind.VIDEO, "video/mp4")))
        }
        await("real activity ready") { backend.state.value.videoEngine == VideoEngine.MEDIA3 &&
            backend.state.value.phase == PlaybackPhase.READY && backend.state.value.videoWidth > 0 && activity.hasWindowFocus() }
        Thread.sleep(600)
        main { backend.exoPlayer().volume = 0f; backend.exoPlayer().pause(); backend.seekTo(2000) }
        await("test starts paused at 2s") { !backend.state.value.isPlaying && backend.state.value.positionMs in 1900..2100 }
        Thread.sleep(400)
    }
    @After fun stop() {
        if (::activity.isInitialized) main { if (::backend.isInitialized) backend.exoPlayer().pause(); activity.finish() }
    }
    private fun point(x: Float, y: Float): Pair<Float, Float> {
        var p = 0f to 0f
        main { p = activity.window.decorView.width * x to activity.window.decorView.height * y }
        return p
    }
    private fun event(action: Int, p: Pair<Float, Float>, down: Long) {
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, p.first, p.second, 0)
        try { i.sendPointerSync(e) } finally { e.recycle() }
    }
    private fun tap() { val p = point(.5f, .5f); val now = SystemClock.uptimeMillis(); event(0, p, now); Thread.sleep(45); event(1, p, now) }
    private fun freshRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) i.uiAutomation.clearCache()
        return i.uiAutomation.rootInActiveWindow
    }
    private fun nodes(node: android.view.accessibility.AccessibilityNodeInfo? = freshRoot()): List<android.view.accessibility.AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun click(label: String) {
        val node = nodes().first { it.text?.toString() == label || it.contentDescription?.toString() == label }
        val r = android.graphics.Rect(); node.getBoundsInScreen(r)
        val p = r.exactCenterX() to r.exactCenterY(); val down = SystemClock.uptimeMillis()
        event(0, p, down); Thread.sleep(40); event(1, p, down); Thread.sleep(400)
    }
    private fun swipe(from: Pair<Float, Float>, to: Pair<Float, Float>) {
        val down = SystemClock.uptimeMillis(); event(0, from, down)
        for (step in 1..12) {
            Thread.sleep(25)
            event(2, from.first + (to.first - from.first) * step / 12 to from.second + (to.second - from.second) * step / 12, down)
        }
        event(1, to, down); Thread.sleep(150)
    }
    @Test fun controlsHideWhilePausedAndGestureTogglesPersist() {
        assertTrue(nodes().any { it.contentDescription == "播放工具" })
        tap(); Thread.sleep(700)
        if (freshRoot()?.packageName == context.packageName) i.uiAutomation.takeScreenshot()?.let { image ->
            java.io.File(context.getExternalFilesDir("u21-evidence"), "hidden-controls.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
        assertFalse("paused controls fade out on video tap", nodes().any { it.contentDescription == "播放工具" })
        tap(); Thread.sleep(700)
        click("播放工具"); click("快捷手势")
        val label = nodes().first { it.text?.toString() == "双击暂停 / 播放" }
        var parent = label.parent
        var toggle: android.view.accessibility.AccessibilityNodeInfo? = null
        repeat(4) { if (toggle == null && parent != null) { toggle = nodes(parent).firstOrNull { it.isCheckable }; parent = parent?.parent } }
        assertNotNull("real gesture switch", toggle)
        assertTrue(toggle!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(300)
        assertFalse(VideoGestureSettings.load(context).doubleTap)
        i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); Thread.sleep(300)
        i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); Thread.sleep(300)
        tap(); Thread.sleep(80); tap(); Thread.sleep(600)
        assertFalse("disabled double tap must not play", backend.state.value.isPlaying)
    }
    @Test fun verticalGesturesAdjustWindowAndPlayerOnly() {
        val systemBrightness = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val systemVolume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        main { activity.window.attributes = activity.window.attributes.apply { screenBrightness = .4f }; backend.exoPlayer().volume = .2f }
        swipe(point(.2f, .55f), point(.2f, .3f))
        main { assertTrue(activity.window.attributes.screenBrightness > .65f) }
        swipe(point(.8f, .55f), point(.8f, .3f))
        main { assertTrue(backend.exoPlayer().volume > .45f); backend.exoPlayer().volume = 0f }
        assertEquals(systemBrightness, android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128))
        assertEquals(systemVolume, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }
    @Test fun borrowedDescriptorPlaysOnMpvWithoutPathPermission() {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
            assertTrue("Old /proc reopen must reproduce Permission denied",
                runCatching { FileInputStream("/proc/self/fd/${fd.fd}").use { it.read() } }.isFailure)
        }
        main { backend.setVideoEngine(VideoEngine.MPV) }
        await("mpv displays foreign authorized descriptor") { backend.state.value.videoEngine == VideoEngine.MPV && backend.state.value.phase == PlaybackPhase.READY && backend.state.value.videoWidth == 320 }
        Thread.sleep(400) // Let the engine-switch restore (position/base speed/pause) finish before a new command.
        main { backend.exoPlayer().play() }
        await("mpv clock advances") { backend.state.value.positionMs > 2600 }
        main { backend.seekTo(5000) }
        await("mpv descriptor seeks") { backend.state.value.positionMs in 4900..6500 }
        android.util.Log.i("YYPhoneQA", "PASS foreign FD: proc reopening denied, mpv playback/seek succeeded")
    }

    /** scripts/Test-TabletAspect.py supplies original circle fixtures; ordinary suites skip it. */
    @Test fun tabletCircleAspectAcrossBackends() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("fixture") == "u22")
        val records = mutableListOf<String>()
        for ((engine, mode) in listOf(VideoEngine.MEDIA3 to DecoderMode.AUTO,
            VideoEngine.MPV to DecoderMode.AUTO, VideoEngine.MPV to DecoderMode.SOFTWARE)) {
            main { backend.setVideoEngine(engine); backend.setDecoderMode(mode) }
            await("$engine ready") { backend.state.value.videoEngine == engine && backend.state.value.phase == PlaybackPhase.READY }
            Thread.sleep(1500)
            if (nodes().any { it.contentDescription == "播放工具" }) tap()
            Thread.sleep(700)
            val bitmap = i.uiAutomation.takeScreenshot()!!
            val directory = java.io.File(context.getExternalFilesDir("u22-evidence"), "aspect").apply { mkdirs() }
            java.io.File(directory, "$engine-$mode.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val cx = bitmap.width / 2; val cy = bitmap.height / 2
            fun white(x: Int, y: Int): Boolean {
                val p = bitmap.getPixel(x, y)
                return android.graphics.Color.red(p) > 210 && android.graphics.Color.green(p) > 210 && android.graphics.Color.blue(p) > 210
            }
            assertTrue("fixture centre", white(cx, cy))
            var left = cx; var right = cx; var top = cy; var bottom = cy
            while (left > 0 && white(left - 1, cy)) left--
            while (right < bitmap.width - 1 && white(right + 1, cy)) right++
            while (top > 0 && white(cx, top - 1)) top--
            while (bottom < bitmap.height - 1 && white(cx, bottom + 1)) bottom++
            val ratio = (right - left + 1).toFloat() / (bottom - top + 1)
            records += "$engine/$mode screenshot=${bitmap.width}x${bitmap.height} circle=${right-left+1}x${bottom-top+1} ratio=$ratio source=${backend.state.value.videoWidth}x${backend.state.value.videoHeight} decoder=${backend.state.value.videoDecoderName}"
            java.io.File(directory, "measurements.txt").writeText(records.joinToString("\n"))
            bitmap.recycle()
            assertEquals("$engine must preserve a circle", 1f, ratio, .025f)
        }
        main { backend.setDecoderMode(DecoderMode.AUTO) }
    }
    @Test fun doubleTapAndSeekPreviewUseRealTouch() {
        tap(); Thread.sleep(80); tap()
        await("double tap resumes") { backend.state.value.isPlaying }
        Thread.sleep(350); tap(); Thread.sleep(80); tap()
        await("double tap pauses") { !backend.state.value.isPlaying }
        val from = point(.3f, .5f); val to = point(.65f, .5f); val down = SystemClock.uptimeMillis()
        val before = backend.state.value.positionMs
        event(0, from, down)
        for (step in 1..12) { Thread.sleep(25); event(2, from.first + (to.first - from.first) * step / 12 to from.second, down) }
        Thread.sleep(650)
        val root = i.uiAutomation.rootInActiveWindow
        if (root?.packageName == context.packageName) {
            val file = java.io.File(context.getExternalFilesDir("u21-evidence"), "seek-preview.png")
            i.uiAutomation.takeScreenshot()?.let { image -> file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle() }
        }
        fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): String {
            if (node == null) return ""
            return "${node.text} ${node.contentDescription} " + (0 until node.childCount).joinToString(" ") { texts(node.getChild(it)) }
        }
        assertTrue("target / duration shown: ${texts(root)}", texts(root).contains("/ 00:12"))
        event(1, to, down)
        await("swipe commits seek") { backend.state.value.positionMs > before + 3000 }
        val preview = VideoSeekPreview(context, uri)
        try {
            preview.request(5000)
            val end = SystemClock.uptimeMillis() + 5000
            while (preview.frame.value == null && SystemClock.uptimeMillis() < end) Thread.sleep(30)
            assertNotNull("real bounded thumbnail", preview.frame.value?.bitmap)
            assertTrue(preview.frame.value!!.bitmap!!.width <= 320)
        } finally { preview.close() }
    }
    @Test fun pressAndReleaseRestoreBothBackendClocks() {
        for (engine in VideoEngine.entries) {
            main { backend.setVideoEngine(engine) }
            await("engine ready") { backend.state.value.videoEngine == engine && backend.state.value.phase == PlaybackPhase.READY }
            Thread.sleep(400)
            main { backend.setSpeed(1f); backend.seekTo(1000); backend.exoPlayer().play() }
            await("playing before boost") { backend.state.value.isPlaying }
            Thread.sleep(500)
            val p = point(.5f, .5f); val down = SystemClock.uptimeMillis()
            event(0, p, down)
            await("long press 3x") { backend.state.value.speed == 3f }
            val boosted = SystemClock.uptimeMillis()
            val start = backend.exoPlayer().let { var value = 0L; main { value = it.currentPosition }; value }
            Thread.sleep(400)
            var advanced = 0L; main { advanced = backend.exoPlayer().currentPosition - start }
            android.util.Log.i("YYPhoneQA", "before release $engine advanced=$advanced state=${backend.state.value.positionMs} playing=${backend.state.value.isPlaying}")
            val released = SystemClock.uptimeMillis(); event(1, p, down)
            await("release normal speed", 1000) { backend.state.value.speed == 1f }
            val ack = SystemClock.uptimeMillis() - released
            assertTrue("release acknowledgement $engine $ack ms", ack < 300)
            assertTrue("actual 3x clock $engine advanced=$advanced", advanced > 700)
            var afterRelease = 0L; main { afterRelease = backend.exoPlayer().currentPosition }
            Thread.sleep(400)
            var normalAdvance = 0L; main { normalAdvance = backend.exoPlayer().currentPosition - afterRelease }
            assertTrue("actual restored clock $engine advanced=$normalAdvance", normalAdvance in 200..750)
            android.util.Log.i("YYPhoneQA", "speed $engine press=${boosted-down}ms releaseAck=${ack}ms clock400ms=$advanced")
            android.util.Log.i("YYPhoneQA", "restored $engine clock400ms=$normalAdvance")
        }
    }
    @Test fun mpvSpeedTrace() {
        main { backend.setVideoEngine(VideoEngine.MPV) }
        await("mpv ready") { backend.state.value.videoEngine == VideoEngine.MPV && backend.state.value.phase == PlaybackPhase.READY }
        Thread.sleep(500)
        main { backend.seekTo(1000); backend.exoPlayer().play() }
        await("mpv playing") { backend.state.value.isPlaying }
        Thread.sleep(700)
        val field = backend.javaClass.getDeclaredField("mpv").apply { isAccessible = true }
        val mpv = field.get(backend)!!
        val handle = mpv.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(mpv)
        val epoch = SystemClock.uptimeMillis()
        val positions = mutableListOf<Double>()
        for (n in 0..25) {
            if (n == 1) main { backend.setSpeed(3f) }
            if (n == 13) main { backend.setSpeed(1f) }
            val data = org.json.JSONObject(MpvNative.call(handle, arrayOf("snapshot")))
            positions += data.getDouble("time-pos")
            assertEquals("actual video AO on this device", "aaudio", data.optString("current-ao"))
            android.util.Log.i("YYPhoneQA", "trace t=${SystemClock.uptimeMillis()-epoch} speed=${data.optDouble("speed")} pts=${data.optDouble("time-pos")} ao=${data.optString("current-ao")}")
            Thread.sleep(80)
        }
        assertTrue("upshift must advance within the first 160ms", positions[3] - positions[1] > .2)
        assertTrue("downshift settles within 160ms", positions[17] - positions[15] < .3)
        assertTrue("normal clock continues after release", positions[25] - positions[17] in .5..1.0)
    }
}

/** Runs before Compose's Activity rule, which starts before @Before methods on HyperOS. */
internal class QaForegroundRule : org.junit.rules.ExternalResource() {
    override fun before() {
        val i = InstrumentationRegistry.getInstrumentation()
        if (i.targetContext.packageName.endsWith(".qa"))
            i.uiAutomation.executeShellCommand("am start -W -n ${i.targetContext.packageName}/yy.daniel.yyplayerforandroid.MainActivity")
                .use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
    }
}
