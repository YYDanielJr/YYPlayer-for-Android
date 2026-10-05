package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.ui.LyricStyle
import java.util.UUID

/** Separate preference namespaces: never clear or replace the user's setup/library. */
class SetupPreferencesTest {
    private fun isolated(test: (Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "u07-test-${UUID.randomUUID()}-"
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                names += name
                return target.getSharedPreferences(prefix + name, mode)
            }
        }
        try { test(context) } finally {
            names.forEach {
                // Drain pending apply writes before deletion, otherwise Android can recreate the fixture.
                target.getSharedPreferences(prefix + it, 0).edit().clear().commit()
                target.deleteSharedPreferences(prefix + it)
            }
        }
    }
    @Test fun freshSetupRemainsIncompleteAfterChoicesAndRelaunch() = isolated { context ->
        val setup = SetupRepository(context)
        assertFalse(setup.completed.value)
        context.getSharedPreferences("ui_preferences_v1", 0).edit().putString("theme", "DARK").commit()
        assertFalse(SetupRepository(context).completed.value)
        setup.finish()
        assertTrue(SetupRepository(context).completed.value)
        setup.restart()
        assertFalse(SetupRepository(context).completed.value)
    }
    @Test fun upgradePreservesExistingLibraryAndPreferences() = isolated { context ->
        val prefs = context.getSharedPreferences("local_media_v1", 0)
        prefs.edit().putString("items", "original-library").commit()
        assertTrue(SetupRepository(context).completed.value)
        assertEquals("original-library", prefs.getString("items", null))
    }
    @Test fun existingOnlineChoicesSkipFirstInstallGuide() = isolated { context ->
        context.getSharedPreferences("online_api_v1", 0).edit().putString("download_tree", "original-tree").commit()
        assertTrue(SetupRepository(context).completed.value)
    }
    @Test fun lyricStyleRoundTripDefaultsAndCorruptValues() {
        val value = LyricStyle(36f, 22f, 800, 400, 0xff3482ffL, 0x80242c35L, 23f)
        assertEquals(value, LyricStyle.decode(value.encode()))
        assertEquals(LyricStyle(), LyricStyle.decode(null))
        assertTrue(runCatching { LyricStyle.decode(value.encode().replace("36", "99")) }.isFailure)
        assertTrue(runCatching { LyricStyle.decode("{\"version\":2}") }.isFailure)
        assertTrue(runCatching { LyricStyle.decode(value.encode().replace("800", "800.5")) }.isFailure)
        assertTrue(runCatching { LyricStyle.decode(value.encode().replace("36", "\"36\"")) }.isFailure)
    }
}
