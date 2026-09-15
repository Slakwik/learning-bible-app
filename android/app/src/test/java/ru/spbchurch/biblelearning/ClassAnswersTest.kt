package ru.spbchurch.biblelearning

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class ClassAnswersTest {
    private lateinit var app: Application
    private lateinit var store: AnswerStore
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("answers_v2.db")
        store = AnswerStore(app)
    }
    @After fun close() { store.close() }

    @Test fun sameLessonHasIndependentPersonalAndClassAnswersForEachAccount() {
        store.edit("alice", "mark-1", "q1", "personal")
        store.edit("alice", "mark-1", "q1", "class A", "a")
        store.edit("alice", "mark-1", "q1", "class B", "b")
        store.edit("bob", "mark-1", "q1", "bob A", "a")
        assertEquals("personal", store.record("alice", "mark-1").values["q1"])
        assertEquals("class A", store.record("alice", "mark-1", "a").values["q1"])
        assertEquals("class B", store.record("alice", "mark-1", "b").values["q1"])
        assertEquals("bob A", store.record("bob", "mark-1", "a").values["q1"])
        assertTrue(store.record(AnswerStore.GUEST, "mark-1", "a").values.isEmpty())
        assertEquals(3, store.allRecords("alice").size)
    }
    @Test fun acknowledgmentDoesNotClearOtherClassOrNewerEdits() {
        store.edit("alice", "mark-1", "q1", "first", "a")
        store.edit("alice", "mark-1", "q1", "other", "b")
        val sent = store.record("alice", "mark-1", "a")
        store.edit("alice", "mark-1", "q1", "newer", "a")
        store.acknowledge(sent, sent.values)
        assertEquals("newer", store.record("alice", "mark-1", "a").values["q1"])
        assertTrue(store.record("alice", "mark-1", "a").dirty)
        assertTrue(store.record("alice", "mark-1", "b").dirty)
        assertEquals("other", store.record("alice", "mark-1", "b").values["q1"])
    }
    @Test fun classConflictAndExportKeepBothVersionsAndPersonalAnswers() {
        store.edit("alice", "mark-1", "q1", "personal")
        store.edit("alice", "mark-1", "q1", "phone", "a")
        store.conflict(store.record("alice", "mark-1", "a"), mapOf("q1" to "website"))
        val export = JSONObject(store.export("alice"))
        assertEquals("personal", export.getJSONObject("lessons").getJSONObject("mark-1").getJSONObject("answers").getString("q1"))
        val group = export.getJSONObject("classes").getJSONObject("a").getJSONObject("mark-1")
        assertEquals("phone", group.getJSONObject("answers").getString("q1"))
        assertEquals("website", group.getJSONObject("remoteConflict").getString("q1"))
        store.resolve("alice", "mark-1", true, "a")
        assertEquals("website", store.record("alice", "mark-1", "a").values["q1"])
        assertEquals("personal", store.record("alice", "mark-1").values["q1"])
    }
    @Test fun lateDownloadCannotReplaceClassDraftAndClearingIsPending() {
        store.acceptRemote("alice", "mark-1", mapOf("q1" to "old"), "a")
        store.edit("alice", "mark-1", "q1", "", "a")
        store.acceptRemote("alice", "mark-1", mapOf("q1" to "late"), "a")
        assertTrue(store.record("alice", "mark-1", "a").dirty)
        assertTrue(store.record("alice", "mark-1", "a").values.isEmpty())
    }
    @Test fun upgradeRetainsPersonalPayloadBaselineRevisionAndConflict() {
        store.close()
        app.getDatabasePath("answers_v2.db").parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath("answers_v2.db"), null).use { db ->
            db.execSQL("CREATE TABLE answers (uid TEXT NOT NULL, slug TEXT NOT NULL, payload TEXT NOT NULL, baseline TEXT NOT NULL, revision INTEGER NOT NULL, dirty INTEGER NOT NULL, conflict TEXT, PRIMARY KEY(uid, slug))")
            db.execSQL("INSERT INTO answers VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf("alice", "mark-1", "{\"q1\":\"draft\"}", "{\"q1\":\"base\"}", 7, 1, "{\"q1\":\"remote\"}"))
            db.version = 1
        }
        store = AnswerStore(app)
        val personal = store.record("alice", "mark-1")
        assertEquals("draft", personal.values["q1"])
        assertEquals("base", personal.base["q1"])
        assertEquals("remote", personal.remoteConflict!!["q1"])
        assertEquals(7L, personal.revision)
        assertTrue(personal.dirty)
        assertNull(personal.classId)
        assertTrue(store.record("alice", "mark-1", "a").values.isEmpty())
        store.edit("alice", "mark-1", "q1", "class", "a")
        assertEquals(2, store.allRecords("alice").size)
    }
    @Test fun classWriteRequiresMembershipActiveClassAndProgramLesson() {
        val group = StudyClass("a", "Class A", "Leader", listOf("mark-2", "mark-1"), setOf("alice"), false, 1, "19:00", 120, "Europe/Moscow", "")
        assertTrue(group.canWrite("alice", "mark-1"))
        assertFalse(group.canWrite("leader", "mark-1"))
        assertFalse(group.canWrite("bob", "mark-1"))
        assertFalse(group.canWrite("alice", "james-1"))
        assertFalse(group.copy(archived = true).canWrite("alice", "mark-1"))
        assertEquals(listOf("mark-2", "mark-1"), group.lessonSlugs)
    }
}
