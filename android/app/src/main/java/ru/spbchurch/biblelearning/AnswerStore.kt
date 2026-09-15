package ru.spbchurch.biblelearning

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class AnswerRecord(
    val uid: String, val slug: String, val values: Map<String, String>,
    val base: Map<String, String>, val revision: Long = 0, val dirty: Boolean = false,
    val remoteConflict: Map<String, String>? = null
)

/** Account-isolated durable store; the network never holds its lock. */
class AnswerStore internal constructor(context: Context) : SQLiteOpenHelper(context, "answers_v2.db", null, 1) {
    companion object {
        const val GUEST = "local-guest"
        @Volatile private var instance: AnswerStore? = null
        fun get(context: Context): AnswerStore = instance ?: synchronized(this) {
            instance ?: AnswerStore(context.applicationContext).also { instance = it }
        }
        fun json(values: Map<String, String>) = JSONObject(values).toString()
        fun map(json: String): Map<String, String> {
            val data = JSONObject(json)
            return data.keys().asSequence().associateWith { data.getString(it) }
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE answers (
            uid TEXT NOT NULL, slug TEXT NOT NULL, payload TEXT NOT NULL,
            baseline TEXT NOT NULL, revision INTEGER NOT NULL, dirty INTEGER NOT NULL,
            conflict TEXT, PRIMARY KEY(uid, slug))""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Explicit non-destructive migration required")
    }
    @Synchronized fun record(uid: String, slug: String): AnswerRecord =
        records(uid).firstOrNull { it.slug == slug } ?: AnswerRecord(uid, slug, emptyMap(), emptyMap())

    @Synchronized fun records(uid: String): List<AnswerRecord> {
        val result = mutableListOf<AnswerRecord>()
        readableDatabase.query("answers", null, "uid = ?", arrayOf(uid), null, null, "slug").use { c ->
            while (c.moveToNext()) {
                fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name))
                result += AnswerRecord(uid, s("slug"), map(s("payload")), map(s("baseline")),
                    c.getLong(c.getColumnIndexOrThrow("revision")), c.getInt(c.getColumnIndexOrThrow("dirty")) != 0,
                    c.getColumnIndexOrThrow("conflict").let { if (c.isNull(it)) null else map(c.getString(it)) })
            }
        }
        return result
    }
    private fun put(r: AnswerRecord) {
        val values = ContentValues().apply {
            put("uid", r.uid); put("slug", r.slug); put("payload", json(r.values)); put("baseline", json(r.base))
            put("revision", r.revision); put("dirty", if (r.dirty) 1 else 0)
            if (r.remoteConflict == null) putNull("conflict") else put("conflict", json(r.remoteConflict))
        }
        check(writableDatabase.insertWithOnConflict("answers", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    @Synchronized fun edit(uid: String, slug: String, question: String, value: String) {
        val before = record(uid, slug)
        val after = SyncPolicy.normalize(before.values + (question to value))
        if (after != before.values) put(before.copy(values = after, revision = before.revision + 1, dirty = after != before.base))
    }
    @Synchronized fun acceptRemote(uid: String, slug: String, values: Map<String, String>) {
        val before = record(uid, slug)
        // A slow fetch may finish after typing starts. Never overwrite local edits.
        if (!before.dirty && before.remoteConflict == null)
            put(before.copy(values = values, base = values, revision = before.revision + 1))
    }
    @Synchronized fun acknowledge(sent: AnswerRecord, accepted: Map<String, String>) {
        val current = record(sent.uid, sent.slug)
        // Preserve edits made during upload; only advance their baseline.
        val now = if (current.revision == sent.revision) accepted
            else SyncPolicy.merge(sent.values, current.values, accepted).answers
        put(current.copy(values = now, base = accepted, dirty = now != accepted,
            remoteConflict = null, revision = current.revision + 1))
    }
    @Synchronized fun conflict(sent: AnswerRecord, remote: Map<String, String>) {
        val current = record(sent.uid, sent.slug)
        put(current.copy(remoteConflict = remote))
    }
    @Synchronized fun resolve(uid: String, slug: String, remoteVersion: Boolean) {
        val current = record(uid, slug)
        val remote = current.remoteConflict ?: return
        val selected = if (remoteVersion) remote else current.values
        put(current.copy(values = selected, base = remote, dirty = selected != remote,
            remoteConflict = null, revision = current.revision + 1))
    }
    @Synchronized fun export(uid: String): String {
        val lessons = JSONObject()
        records(uid).forEach { r ->
            lessons.put(r.slug, JSONObject().put("answers", JSONObject(r.values))
                .put("base", JSONObject(r.base)).put("pending", r.dirty)
                .put("remoteConflict", r.remoteConflict?.let { JSONObject(it) } ?: JSONObject.NULL))
        }
        return JSONObject().put("format", "bible-learning-personal-answers-v2")
            .put("uid", uid).put("lessons", lessons).toString(2)
    }
}
