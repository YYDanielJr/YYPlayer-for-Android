package yy.daniel.yyplayerforandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import yy.daniel.yyplayerforandroid.playback.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Actual Media3 WAV playback and two original covers, never personal music or a synthetic clock. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MusicExperienceUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(QaForegroundRule()).around(compose)
    private fun fixture(withLyrics: Boolean = false, test: (List<LocalMedia>) -> Unit) {
        val context = compose.activity
        if (context.packageName.endsWith(".qa")) compose.runOnIdle { context.runtime.setup.finish() }
        val files = mutableListOf<File>()
        val songs = listOf(0xff289cb8.toInt(), 0xffc8903c.toInt()).mapIndexed { index, color ->
            val audio = File.createTempFile("u07-own-$index-", ".wav", context.cacheDir).also { files += it }
            val pcm = ByteArray(44100 * 4 * 30)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(pcm.size + 36); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(2); putInt(44100); putInt(176400); putShort(4); putShort(16); put("data".toByteArray()); putInt(pcm.size)
            }.array()
            audio.outputStream().use { it.write(header); it.write(pcm) }
            val coverDirectory = File(context.filesDir, "online-artwork").apply { mkdirs() }
            val cover = File.createTempFile("u07-own-$index-", ".png", coverDirectory).also { files += it }
            val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(color)
            canvas.drawCircle(250f, 250f, 140f, Paint().apply { this.color = 0xffeaf4f8.toInt() })
            cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            LocalMedia(Uri.fromFile(audio), audio.name, MediaKind.AUDIO, "audio/wav", songTitle = "原创封面验收 ${index + 1}",
                artist = "YYPlayer QA", album = "自有几何封面", artworkUri = Uri.fromFile(cover), metadataRead = true)
        }
        try {
            if (withLyrics) songs.forEach { song ->
                val lrc = File.createTempFile("u22-original-", ".lrc", context.cacheDir).also { files += it }
                lrc.writeText("[00:00.00]把右半边留给歌词 / Room for every line\n[00:05.00]封面随音乐展开 / Let the artwork unfold\n[00:10.00]在同一段旋律里 / In the same melody\n[00:15.00]轻轻收回，再次相遇 / Close gently and return")
                kotlinx.coroutines.runBlocking { context.runtime.lyrics.import(song, Uri.fromFile(lrc)) }
            }
            compose.runOnIdle { compose.activity.runtime.playback.play(songs) }
            compose.waitUntil(10000) { compose.activity.runtime.playback.state.value.isPlaying }
            compose.runOnIdle { compose.activity.runtime.playback.togglePlayPause() }
            test(songs)
        } finally {
            if (withLyrics) kotlinx.coroutines.runBlocking { songs.forEach { context.runtime.lyrics.clear(it) } }
            files.forEach { it.delete() }
        }
    }
    private fun evidence(name: String, node: SemanticsNodeInteraction? = null) {
        if (InstrumentationRegistry.getArguments().getString("u07Evidence") != "true") return
        compose.waitForIdle()
        val directory = File(compose.activity.cacheDir, "u07-evidence").apply { mkdirs() }
        val bitmap = (node ?: compose.onRoot(useUnmergedTree = true)).captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun controlCenterSongOpensCoverAndBackReturnsHome() = fixture { songs ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val runtime = compose.activity.runtime
        fun shell(command: String) = automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
        fun songNode(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && node.text?.toString() == songs[0].musicTitle) return node
            for (index in 0 until node.childCount) songNode(node.getChild(index))?.let { return it }
            return null
        }
        try {
            compose.runOnIdle { runtime.playback.togglePlayPause() }
            compose.waitUntil(5000) { runtime.playback.state.value.isPlaying }
            shell("input keyevent KEYCODE_HOME")
            Thread.sleep(1000)
            val screen = automation.takeScreenshot()
            val width = screen.width; val height = screen.height; screen.recycle()
            shell("input swipe ${width * 9 / 10} 1 ${width * 9 / 10} ${height / 2} 400")
            var title: android.view.accessibility.AccessibilityNodeInfo? = null
            compose.waitUntil(5000) { title = songNode(automation.rootInActiveWindow); title != null }
            val bounds = android.graphics.Rect()
            title!!.getBoundsInScreen(bounds)
            android.util.Log.i("YYPlayerU28", "control-center title bounds=$bounds")
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            Thread.sleep(1000)
            if (automation.rootInActiveWindow?.packageName?.toString() == "com.android.systemui") {
                val expandedTitle = songNode(automation.rootInActiveWindow)
                assertNotNull("expanded system media card exposes the playing song", expandedTitle)
                expandedTitle!!.getBoundsInScreen(bounds)
                android.util.Log.i("YYPlayerU28", "expanded title bounds=$bounds")
                shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
                Thread.sleep(1000)
            }
            compose.waitUntil(5000) {
                runCatching { compose.onAllNodesWithContentDescription("收起音乐详情").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
            }
            compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertIsDisplayed()
            evidence("u28-control-center-cover")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            assertTrue(runtime.playback.state.value.isPlaying)
            evidence("u28-control-center-home")
        } finally {
            shell("cmd statusbar collapse")
            instrumentation.runOnMainSync { runtime.playback.exoPlayer().pause() }
        }
    }
    @Test fun mediaEntryHandlesWarmIntentWithoutReplayingOnRecreationOrLauncher() = fixture {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun resumed(): MainActivity? {
            var result: MainActivity? = null
            instrumentation.runOnMainSync {
                result = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
            }
            return result
        }
        val home = compose.activity
        val scenarioIntent = android.content.Intent(home.intent)
        var scenarioActivity = home
        try {
            compose.runOnIdle {
                home.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setClass(home, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(android.content.Intent.EXTRA_REFERRER, Uri.parse("android-app://com.android.systemui")))
            }
            compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertIsDisplayed()
            assertSame(home, resumed())
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            instrumentation.runOnMainSync { home.recreate() }
            compose.waitUntil(5000) { resumed()?.let { it !== home } == true }
            val restored = resumed()!!
            scenarioActivity = restored
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            compose.onNodeWithContentDescription("收起音乐详情").assertDoesNotExist()
            compose.runOnIdle {
                restored.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setClass(restored, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(android.content.Intent.EXTRA_REFERRER, Uri.parse("android-app://com.miui.home")))
            }
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            compose.onNodeWithContentDescription("收起音乐详情").assertDoesNotExist()
            // Reuse a legacy back-stack destination: a media entry must still collapse to home.
            compose.runOnIdle {
                restored.intent.putExtra(MainActivity.EXTRA_MUSIC_ON_BACK_STACK, true)
                restored.startActivity(android.content.Intent(restored, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_music", true))
            }
            compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertIsDisplayed()
            assertFalse(restored.musicOnBackStack)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            assertSame(restored, resumed())
        } finally {
            // ActivityScenario identifies lifecycle events by the original launch intent.
            // Restore only after assertions, so a real onNewIntent/setIntent can be tested.
            instrumentation.runOnMainSync { scenarioActivity.intent = scenarioIntent }
        }
    }
    @Test fun earlySwipeWaitsForSharedCoverThenPagesAndReturns() = fixture(withLyrics = true) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = compose.activity.runtime.preferences.value.reduceMotion
        try {
            compose.runOnIdle { compose.activity.runtime.setReduceMotion(false) }
            for (elapsed in listOf(160L, 320L, 480L)) {
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag("library-mini-player").performClick()
                repeat((elapsed / 32).toInt()) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
                // Use the root: gated detail semantics must not be needed to inject a real drag.
                compose.onRoot().performTouchInput {
                    swipe(androidx.compose.ui.geometry.Offset(width * .85f, height * .35f),
                        androidx.compose.ui.geometry.Offset(width * .15f, height * .35f), 80)
                }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertIsDisplayed()
                compose.onNodeWithTag("music-pager").performTouchInput { swipeLeft(durationMillis = 300) }
                compose.waitForIdle()
                compose.onNodeWithTag("music-compact-identity").assertIsDisplayed()
                evidence("u27-lyrics-after-early-$elapsed")
                compose.onNodeWithTag("music-pager").performTouchInput { swipeRight(durationMillis = 300) }
                compose.waitForIdle()
                compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertIsDisplayed()
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { compose.activity.runtime.setReduceMotion(original) }
        }
    }

    @Test fun platformNotificationPublishesArtworkAndControlsBackgroundPlayer() = fixture { songs ->
        val context = compose.activity
        val scenarioIntent = android.content.Intent(context.intent)
        val runtime = context.runtime
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        fun notification() = notifications.activeNotifications.firstOrNull {
            it.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)
        }?.notification
        try {
            compose.runOnIdle { runtime.playback.togglePlayPause() }
            compose.waitUntil(10000) { notification()?.getLargeIcon() != null }
            val n = notification()!!
            @Suppress("DEPRECATION")
            val token = n.extras.getParcelable<android.media.session.MediaSession.Token>(android.app.Notification.EXTRA_MEDIA_SESSION)!!
            val controller = android.media.session.MediaController(context, token)
            assertEquals("android.app.Notification\$MediaStyle", n.extras.getString(android.app.Notification.EXTRA_TEMPLATE))
            assertTrue(n.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0)
            compose.waitUntil(5000) { controller.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
                controller.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) != null }
            assertEquals(songs[0].musicTitle, controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE))
            val before = controller.playbackState!!.position
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
            }
            Thread.sleep(2200)
            assertEquals(android.media.session.PlaybackState.STATE_PLAYING, controller.playbackState!!.state)
            assertTrue(runtime.playback.state.value.positionMs > before + 1000)
            // Only the status/island strip is retained, never launcher contents or personal media.
            if (InstrumentationRegistry.getArguments().getString("u07Evidence") == "true") {
                val shot = instrumentation.uiAutomation.takeScreenshot()
                val strip = Bitmap.createBitmap(shot, 0, 0, shot.width, minOf(shot.height, 220))
                val directory = File(context.cacheDir, "u07-evidence").apply { mkdirs() }
                File(directory, "u27-system-island.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
                strip.recycle(); shot.recycle()
            }
            repeat(3) {
                controller.transportControls.pause()
                compose.waitUntil(5000) { !runtime.playback.state.value.isPlaying }
                controller.transportControls.play()
                compose.waitUntil(5000) { runtime.playback.state.value.isPlaying }
                Thread.sleep(500)
                assertNotNull(notification())
            }
            controller.transportControls.skipToNext()
            compose.waitUntil(5000) { runtime.playback.state.value.current?.identity == songs[1].identity &&
                controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) == songs[1].musicTitle }
            compose.waitUntil(5000) { notification()?.getLargeIcon() != null }
            // An ordinary background app cannot launch an Activity by sending another PI.
            // Bring the test caller foreground first; SystemUI has its own BAL exemption.
            instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/yy.daniel.yyplayerforandroid.MainActivity").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
            }
            notification()!!.contentIntent.send()
            compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription("收起音乐详情").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
        } finally {
            instrumentation.runOnMainSync { runtime.playback.exoPlayer().pause() }
            context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { context.intent = scenarioIntent }
        }
    }

    @Test fun lyricsFollowInOrderAndCoverStaysSquareDuringPaging() = fixture(withLyrics = true) {
        compose.onNodeWithTag("library-mini-player").performClick()
        val cover = compose.onNodeWithContentDescription("专辑封面，切换到歌词")
        val first = compose.onNodeWithText("把右半边留给歌词")
        val last = compose.onNodeWithText("轻轻收回，再次相遇")
        cover.performClick()
        val firstRest = first.getUnclippedBoundsInRoot().left.value
        val lastRest = last.getUnclippedBoundsInRoot().left.value
        compose.onNodeWithText("封面").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            cover.performClick()
            repeat(8) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
            val firstDelta = first.getUnclippedBoundsInRoot().left.value - firstRest
            val lastDelta = last.getUnclippedBoundsInRoot().left.value - lastRest
            assertTrue("click: top line leads lower lines ($firstDelta, $lastDelta)", lastDelta > firstDelta + 1f)
            evidence("u27-click-stagger")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("封面").performClick()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val pager = compose.onNodeWithTag("music-pager")
            pager.performTouchInput { down(androidx.compose.ui.geometry.Offset(width * .9f, height * .85f)) }
            repeat(6) {
                pager.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-width * .1f, 0f), 32) }
                compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
            }
            val firstDrag = first.getUnclippedBoundsInRoot().left.value - firstRest
            val lastDrag = last.getUnclippedBoundsInRoot().left.value - lastRest
            assertTrue("drag near bottom: nearest line leads ($firstDrag, $lastDrag)", firstDrag > lastDrag + 1f)
            val bounds = cover.getUnclippedBoundsInRoot()
            assertEquals("horizontal paging must not tilt the cover", (bounds.right - bounds.left).value, (bounds.bottom - bounds.top).value, 1f)
            evidence("u27-drag-stagger")
            pager.performTouchInput { up() }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun playbackDockPersistsAcrossSecondaryPagesAndReturnsToCaller() = fixture {
        val home = compose.activity
        val runtime = home.runtime
        val original = runtime.preferences.value
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun resumed(): PlayerHostActivity? {
            var page: PlayerHostActivity? = null
            instrumentation.runOnMainSync {
                page = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).singleOrNull() as? PlayerHostActivity
            }
            return page
        }
        fun back() = instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        try {
            compose.runOnIdle { runtime.setOnlineMusic(true); runtime.setOnlineLyrics(true) }
            val generation = runtime.playback.state.value.mediaGeneration
            val pages = listOf<Pair<Class<out PlayerHostActivity>, () -> Unit>>(
                SettingsActivity::class.java to home::openSettings,
                OrientationActivity::class.java to home::openOrientation,
                LyricStyleActivity::class.java to home::openLyricStyle,
                AudioOutputActivity::class.java to home::openAudioOutput,
                OnlineSearchActivity::class.java to home::openOnlineSearch,
                OnlineLyricsActivity::class.java to home::openOnlineLyrics,
            )
            var previousBounds: androidx.compose.ui.geometry.Rect? = null
            for ((type, open) in pages) {
                compose.runOnIdle(open)
                compose.waitUntil(5000) { type.isInstance(resumed()) }
                val page = resumed()!!
                compose.onAllNodesWithTag("library-mini-player").assertCountEquals(1)
                val dock = compose.onNodeWithTag("library-mini-player")
                dock.assertIsDisplayed()
                val bounds = dock.fetchSemanticsNode().boundsInRoot
                if (previousBounds != null) assertEquals("secondary pages keep identical dock geometry", previousBounds, bounds)
                previousBounds = bounds
                assertTrue("page extends beneath floating dock", compose.onNodeWithTag("playback-page-content").fetchSemanticsNode().boundsInRoot.bottom >= bounds.bottom)
                if (page is MusicQueueActivity) assertTrue("fixed queue actions remain reachable",
                    compose.onNodeWithText("清空队列").fetchSemanticsNode().boundsInRoot.bottom < bounds.top)
                compose.onNodeWithContentDescription("播放").performClick()
                compose.waitUntil(5000) { runtime.playback.state.value.isPlaying }
                compose.onNodeWithContentDescription("暂停").performClick()
                compose.waitUntil(5000) { !runtime.playback.state.value.isPlaying }
                if (page is SettingsActivity || page is MusicQueueActivity) evidence("u24-${type.simpleName}")
                val origin = compose.onNodeWithTag("music-shared-artwork").fetchSemanticsNode().boundsInRoot
                compose.mainClock.autoAdvance = false
                dock.performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                // Android layout/anchor callbacks need a real UI traversal between clock steps.
                repeat(5) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
                val opening = compose.onNodeWithTag("music-shared-artwork").fetchSemanticsNode().boundsInRoot
                assertTrue("${type.simpleName}: artwork grows from dock $origin to $opening", opening.width > origin.width * 1.2f)
                assertSame("expansion stays in its originating Activity", page, resumed())
                if (page is SettingsActivity) evidence("u26-settings-opening")
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                assertSame(runtime.playback, resumed()!!.runtime.playback)
                compose.onAllNodesWithTag("library-mini-player").assertCountEquals(0)
                val full = compose.onNodeWithTag("music-shared-artwork").fetchSemanticsNode().boundsInRoot
                assertTrue("opening has an intermediate geometry", opening.width < full.width)
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("收起音乐详情").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                repeat(5) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
                val closing = compose.onNodeWithTag("music-shared-artwork").fetchSemanticsNode().boundsInRoot
                assertTrue("artwork shrinks back toward the same dock", closing.width < full.width && closing.width > origin.width)
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.waitUntil(5000) { resumed() === page }
                compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
                assertEquals(origin, compose.onNodeWithTag("music-shared-artwork").fetchSemanticsNode().boundsInRoot)
                assertEquals(generation, runtime.playback.state.value.mediaGeneration)
                back()
                compose.waitUntil(5000) { resumed() === home }
            }
            // Removing the actual queue also removes the dock; it must not recreate the page.
            compose.runOnIdle { home.openMusicQueue() }
            compose.waitUntil(5000) { resumed() is MusicQueueActivity }
            val queue = resumed()
            compose.onNodeWithTag("library-mini-player").assertDoesNotExist()
            compose.onNodeWithText("清空队列").performClick()
            compose.onNodeWithText("停止并清空").performClick()
            compose.waitUntil(5000) { runtime.playback.state.value.current == null }
            compose.onNodeWithTag("library-mini-player").assertDoesNotExist()
            assertSame(queue, resumed())
            back()
        } finally {
            compose.mainClock.autoAdvance = true
            val lastPage = resumed()
            instrumentation.runOnMainSync {
                runtime.setOnlineMusic(original.onlineMusic); runtime.setOnlineLyrics(original.onlineLyrics)
                lastPage?.takeIf { it !== home }?.finish()
            }
        }
    }
    @Test fun playbackDockStaysOutsidePageAnimationsAndSetupReturns() = fixture {
        val home = compose.activity
        val runtime = home.runtime
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun dock() = compose.onNodeWithTag("library-mini-player")
        fun verifyTransition(action: () -> Unit) {
            val before = dock().fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            try {
                action()
                repeat(24) {
                    compose.mainClock.advanceTimeBy(32)
                    assertEquals("dock must not move with page animation", before, dock().fetchSemanticsNode().boundsInRoot)
                }
            } finally { compose.mainClock.autoAdvance = true }
            compose.waitForIdle()
        }
        try {
            // Use the sidebar/tab control, not the large page header with the same text.
            verifyTransition { compose.onAllNodesWithText("视频").onLast().performClick() }
            verifyTransition { compose.onAllNodesWithText("音乐").onLast().performClick() }
            compose.runOnIdle { runtime.setup.restart() }
            compose.onNodeWithText("开始设置").assertIsDisplayed()
            dock().assertDoesNotExist()
            compose.onNodeWithText("开始设置").performClick()
            compose.onNodeWithText("让音乐来到身边").assertIsDisplayed()
            dock().assertDoesNotExist()
        } finally {
            instrumentation.runOnMainSync { runtime.setup.finish() }
        }
    }
    @Test fun inlineCoverSurvivesInterruptedOpenRecreationAndLyricPageNext() = fixture { songs ->
        val home = compose.activity
        val runtime = home.runtime
        val original = runtime.preferences.value
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun resumed(): PlayerHostActivity? {
            var page: PlayerHostActivity? = null
            instrumentation.runOnMainSync { page = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).singleOrNull() as? PlayerHostActivity }
            return page
        }
        fun back() = instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        try {
            compose.runOnIdle { runtime.setReduceMotion(false); home.openSettings() }
            compose.waitUntil(5000) { resumed() is SettingsActivity }
            var settings = resumed()!!
            repeat(3) {
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag("library-mini-player").performClick()
                repeat(3) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
                back() // Cancel before detail controls become interactive.
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                assertSame(settings, resumed())
                compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
                compose.onNodeWithText("歌词与播放").assertDoesNotExist()
            }
            compose.onNodeWithTag("library-mini-player").performClick()
            compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
            val previous = settings
            instrumentation.runOnMainSync { settings.recreate() }
            compose.waitUntil(5000) { resumed() is SettingsActivity && resumed() !== previous }
            settings = resumed()!!
            compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
            back()
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            assertSame(settings, resumed())
            // The user's reduced-motion setting applies to the same inline entry.
            compose.runOnIdle { runtime.setReduceMotion(true) }
            compose.onNodeWithTag("library-mini-player").performClick()
            compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
            compose.onNodeWithTag("music-shared-artwork").assertDoesNotExist()
            back()
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            back()
            compose.waitUntil(5000) { resumed() === home }
            compose.runOnIdle { runtime.setReduceMotion(false); runtime.setOnlineLyrics(true); home.openOnlineLyrics() }
            compose.waitUntil(5000) { resumed() is OnlineLyricsActivity }
            val lyrics = resumed()
            compose.onNodeWithTag("library-mini-player").performClick()
            compose.onNodeWithContentDescription("下一首").performClick()
            compose.waitUntil(5000) { runtime.playback.state.value.current?.identity == songs[1].identity }
            compose.onNodeWithContentDescription("收起音乐详情").assertIsDisplayed()
            assertSame("next must not finish the host of the expanded cover", lyrics, resumed())
            back()
            compose.onNode(hasSetTextAction()).assertTextEquals(songs[1].musicTitle)
            compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
            assertSame(lyrics, resumed())
            back()
        } finally {
            compose.mainClock.autoAdvance = true
            val page = resumed()
            instrumentation.runOnMainSync {
                runtime.playback.exoPlayer().pause()
                runtime.setReduceMotion(original.reduceMotion); runtime.setOnlineLyrics(original.onlineLyrics)
                page?.takeIf { it !== home }?.finish()
            }
        }
    }
    @Test fun tabletCoverKeepsControlsLeftAndLyricsFullHeight() = fixture(withLyrics = true) {
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.smallestScreenWidthDp >= 600)
        compose.onNodeWithTag("library-mini-player").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("music-wide-cover-column").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("把右半边留给歌词").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("music-wide-cover-column").assertIsDisplayed()
        compose.onNodeWithTag("music-compact-identity").assertDoesNotExist()
        val left = compose.onNodeWithTag("music-wide-cover-column").fetchSemanticsNode().boundsInRoot
        val lyrics = compose.onNodeWithTag("music-wide-lyrics").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("music-controls").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithContentDescription("专辑封面，切换到歌词").fetchSemanticsNode().boundsInRoot
        assertTrue("Controls stay in the cover column", controls.right <= lyrics.left + 1 && controls.left >= left.left)
        assertTrue("Cover precedes controls", cover.bottom < controls.top)
        assertEquals("Lyrics occupy the whole column", left.bottom, lyrics.bottom, 1f)
        evidence("u22-landscape-cover")
        compose.onNodeWithText("歌词", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("music-compact-identity").assertIsDisplayed()
        compose.onNodeWithText("封面", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("music-compact-identity").assertDoesNotExist()
        repeat(3) {
            compose.onNodeWithContentDescription("收起音乐详情").performClick()
            compose.onNodeWithTag("library-mini-player").performClick()
            compose.mainClock.autoAdvance = false
            repeat(16) {
                compose.mainClock.advanceTimeBy(32)
                compose.onNodeWithTag("music-compact-identity").assertDoesNotExist()
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithTag("music-wide-cover-column").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("收起音乐详情").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        evidence("u22-sidebar")
    }
    @Test fun ringSeekPauseAndNextControlTheRealSharedPlayer() = fixture { songs ->
        val backend = compose.activity.runtime.playback
        compose.onNodeWithTag("library-mini-player").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.5f) }
        compose.waitUntil(5000) { backend.state.value.positionMs in 14000..16000 }
        assertFalse(backend.state.value.isPlaying)
        compose.onNodeWithContentDescription("播放").performClick()
        compose.waitUntil(5000) { backend.state.value.isPlaying }
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.waitUntil(5000) { !backend.state.value.isPlaying }
        compose.onNodeWithContentDescription("专辑封面，切换到歌词").assertDoesNotExist()
        val mini = compose.onNodeWithTag("library-mini-player")
        evidence("mini-player-ring", mini)
        val resting = mini.captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = false
        mini.performTouchInput { down(androidx.compose.ui.geometry.Offset(80f, center.y)) }
        compose.mainClock.advanceTimeBy(250)
        val pressed = mini.captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 0 until minOf(resting.height, pressed.height) step 4) for (x in 0 until minOf(resting.width, pressed.width) step 4) {
            if (resting.getPixel(x, y) != pressed.getPixel(x, y)) changed++
        }
        evidence("mini-player-sink-pressed", mini)
        assertTrue("Whole playback capsule must sink", changed > 100)
        mini.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        mini.performClick() // The dock intentionally only exposes play/pause; next lives in detail.
        compose.onNodeWithContentDescription("下一首").performClick()
        compose.waitUntil(5000) { backend.state.value.current?.uri == songs[1].uri }
    }
    @Test fun coverKeepsItsShapeDuringNextAndClickOpensLyrics() = fixture { songs ->
        val backend = compose.activity.runtime.playback
        compose.runOnIdle { compose.activity.showMusic() }
        val cover = compose.onNodeWithContentDescription("专辑封面，切换到歌词")
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("专辑封面，切换到歌词").fetchSemanticsNodes().isNotEmpty() }
        cover.assertIsDisplayed()
        val bounds = cover.fetchSemanticsNode().boundsInRoot
        evidence("cover-before")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("下一首").performClick()
        compose.waitUntil(5000) { backend.state.value.current?.uri == songs[1].uri }
        compose.mainClock.advanceTimeBy(150)
        assertEquals(bounds, cover.fetchSemanticsNode().boundsInRoot)
        evidence("cover-next-150ms")
        for (elapsed in listOf(300, 450)) {
            compose.mainClock.advanceTimeBy(150)
            assertEquals(bounds, cover.fetchSemanticsNode().boundsInRoot)
            evidence("cover-next-${elapsed}ms")
            val pixels = cover.captureToImage().asAndroidBitmap()
            val centerColor = pixels.getPixel(pixels.width / 2, pixels.height / 2)
            assertTrue("Artwork must not collapse to the grey placeholder mid-transition",
                android.graphics.Color.red(centerColor) > 150 && android.graphics.Color.green(centerColor) > 150 && android.graphics.Color.blue(centerColor) > 150)
        }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(bounds, cover.fetchSemanticsNode().boundsInRoot)
        evidence("cover-next-complete")
        val restingPixels = cover.captureToImage().asAndroidBitmap()
        cover.performTouchInput { down(center + androidx.compose.ui.geometry.Offset(30f, -30f)) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        evidence("cover-tilt-pressed")
        val heldPixels = cover.captureToImage().asAndroidBitmap()
        var changedPixels = 0
        for (y in 0 until minOf(restingPixels.height, heldPixels.height) step 4) {
            for (x in 0 until minOf(restingPixels.width, heldPixels.width) step 4) {
                if (restingPixels.getPixel(x, y) != heldPixels.getPixel(x, y)) changedPixels++
            }
        }
        assertTrue("Held cover must visibly tilt (changed samples=$changedPixels)", changedPixels > 100)
        cover.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Portrait moves to page 1; wide view expands the existing real lyrics pane.
        compose.onNodeWithText("无歌词").assertIsDisplayed()
        evidence("cover-click-lyrics")
    }
}
