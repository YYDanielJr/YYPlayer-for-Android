package yy.daniel.yyplayerforandroid

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import yy.daniel.yyplayerforandroid.online.OnlineSearchHistory
import java.util.UUID

class OnlineSearchHistoryDeviceTest {
    @Test fun historyPersistsItsOptOutAndClearsOnlySearches() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            val history = OnlineSearchHistory(context)
            history.record("  编织光流  "); history.record("夜曲"); history.record("编织光流")
            assertEquals(listOf("编织光流", "夜曲"), OnlineSearchHistory(context).state.value.queries)
            history.setKeep(false); history.record("不记录")
            assertFalse(OnlineSearchHistory(context).state.value.keep)
            assertEquals(listOf("编织光流", "夜曲"), history.state.value.queries)
            history.remove("夜曲")
            assertEquals(listOf("编织光流"), OnlineSearchHistory(context).state.value.queries)
            history.clear()
            assertTrue(OnlineSearchHistory(context).state.value.queries.isEmpty())
            assertFalse(OnlineSearchHistory(context).state.value.keep)
            history.setKeep(true); history.record("重新保留")
            assertEquals(listOf("重新保留"), OnlineSearchHistory(context).state.value.queries)
        } finally {
            base.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit()
            base.deleteSharedPreferences(name)
        }
    }
    @Test fun unsupportedHistoryVersionRecoversWithoutUsingUnvalidatedEntries() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        try {
            base.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putString("state", """{"version":9,"keep":true,"queries":["unknown"]}""").apply()
            assertTrue(OnlineSearchHistory(context).state.value.queries.isEmpty())
            assertTrue(OnlineSearchHistory(context).state.value.keep)
        } finally {
            base.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit()
            base.deleteSharedPreferences(name)
        }
    }
}
