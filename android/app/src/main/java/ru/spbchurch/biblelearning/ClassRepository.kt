package ru.spbchurch.biblelearning

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** Server-verified class metadata. Cached membership is never treated as authorization. */
data class StudyClass(
    val id: String, val name: String, val leader: String,
    val lessonSlugs: List<String>, val members: Set<String>, val archived: Boolean,
    val weekday: Int, val time: String, val duration: Long,
    val timezone: String, val place: String
) {
    fun canWrite(uid: String, slug: String) = !archived && uid in members && slug in lessonSlugs
    fun schedule(): String {
        val days = listOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        return listOfNotNull(days.getOrNull(weekday), time.ifBlank { null },
            if (duration > 0) "$duration мин" else null, timezone.ifBlank { null }).joinToString(" · ")
    }
    companion object {
        fun from(doc: DocumentSnapshot): StudyClass {
            check(doc.exists()) { "Class unavailable" }
            fun strings(key: String) = (doc.get(key) as? List<*>)?.filterIsInstance<String>().orEmpty()
            return StudyClass(doc.id, doc.getString("name").orEmpty(), doc.getString("leaderName").orEmpty(),
                strings("lessonSlugs"), strings("memberUids").toSet(), doc.getBoolean("archived") ?: false,
                (doc.getLong("weekday") ?: -1).toInt(), doc.getString("time").orEmpty(),
                doc.getLong("duration") ?: 0, doc.getString("timezone") ?: "Europe/Moscow",
                doc.getString("place").orEmpty())
        }
    }
}

class ClassRepository {
    private val db get() = FirebaseFirestore.getInstance()
    private fun checkOwner(uid: String) {
        check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
    }
    suspend fun available(uid: String): List<StudyClass> {
        checkOwner(uid)
        val profile = db.collection("users").document(uid).get(Source.SERVER).await()
        val role = profile.getString("role") ?: "user"
        val classes = db.collection("classes")
        val documents = if (role == "admin") classes.get(Source.SERVER).await().documents else {
            val member = classes.whereArrayContains("memberUids", uid).get(Source.SERVER).await().documents
            if (role == "leader") member + classes.whereEqualTo("leaderUid", uid).get(Source.SERVER).await().documents else member
        }
        checkOwner(uid)
        return documents.distinctBy { it.id }.map(StudyClass::from).sortedWith(compareBy({ it.archived }, { it.name }))
    }
    suspend fun get(uid: String, id: String): StudyClass {
        checkOwner(uid)
        val result = StudyClass.from(db.collection("classes").document(id).get(Source.SERVER).await())
        checkOwner(uid)
        return result
    }
}
