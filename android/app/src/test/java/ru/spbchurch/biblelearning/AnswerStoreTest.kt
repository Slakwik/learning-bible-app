package ru.spbchurch.biblelearning

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AnswerStoreTest {
    private lateinit var store: AnswerStore
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("answers_v2.db")
        store = AnswerStore(context)
    }
    @After fun close() { store.close() }
    @Test fun accountsNeverShareAnswers() {
        store.edit("alice", "james-1", "q1", "private")
        assertTrue(store.record("bob", "james-1").values.isEmpty())
        assertTrue(store.record(AnswerStore.GUEST, "james-1").values.isEmpty())
    }
    @Test fun successfulUploadKeepsOfflineCopy() {
        store.edit("alice", "james-1", "q1", "answer")
        val snapshot = store.record("alice", "james-1")
        store.acknowledge(snapshot, snapshot.values)
        assertEquals("answer", store.record("alice", "james-1").values["q1"])
        assertFalse(store.record("alice", "james-1").dirty)
    }
    @Test fun lateFetchCannotReplaceTyping() {
        store.edit("alice", "james-1", "q1", "new")
        store.acceptRemote("alice", "james-1", mapOf("q1" to "old"))
        assertEquals("new", store.record("alice", "james-1").values["q1"])
    }
    @Test fun editsMadeDuringUploadRemainPending() {
        store.edit("alice", "james-1", "q1", "first")
        val sent = store.record("alice", "james-1")
        store.edit("alice", "james-1", "q1", "second")
        store.acknowledge(sent, sent.values)
        assertEquals("second", store.record("alice", "james-1").values["q1"])
        assertTrue(store.record("alice", "james-1").dirty)
    }
    @Test fun uploadRacePreservesNonOverlappingWebsiteField() {
        store.edit("alice", "james-1", "q1", "first")
        val sent = store.record("alice", "james-1")
        store.edit("alice", "james-1", "q1", "second")
        store.acknowledge(sent, mapOf("q1" to "first", "q2" to "website"))
        assertEquals(mapOf("q1" to "second", "q2" to "website"), store.record("alice", "james-1").values)
    }
    @Test fun clearingLastAnswerRemainsPending() {
        store.acceptRemote("alice", "james-1", mapOf("q1" to "old"))
        store.edit("alice", "james-1", "q1", "")
        assertTrue(store.record("alice", "james-1").dirty)
        assertTrue(store.record("alice", "james-1").values.isEmpty())
    }
    @Test fun conflictsKeepBothVersionsUntilChoice() {
        store.edit("alice", "james-1", "q1", "phone")
        val sent = store.record("alice", "james-1")
        store.conflict(sent, mapOf("q1" to "website"))
        val record = store.record("alice", "james-1")
        assertEquals("phone", record.values["q1"])
        assertEquals("website", record.remoteConflict!!["q1"])
        assertTrue(store.export("alice").contains("website"))
        store.resolve("alice", "james-1", true)
        assertFalse(store.record("alice", "james-1").dirty)
        assertEquals("website", store.record("alice", "james-1").values["q1"])
    }
    @Test fun choosingPhoneStillRequiresUpload() {
        store.edit("alice", "james-1", "q1", "phone")
        store.conflict(store.record("alice", "james-1"), mapOf("q1" to "website"))
        store.resolve("alice", "james-1", false)
        assertTrue(store.record("alice", "james-1").dirty)
        assertEquals("website", store.record("alice", "james-1").base["q1"])
    }
}
