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
    val timezone: String, val place: String, val leaderUid: String = "", val canManage: Boolean = false
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
                doc.getString("place").orEmpty(), doc.getString("leaderUid").orEmpty())
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
        return documents.distinctBy { it.id }.map { doc ->
            StudyClass.from(doc).let { it.copy(canManage = role == "admin" || (role == "leader" && it.leaderUid == uid)) }
        }.sortedWith(compareBy({ it.archived }, { it.name }))
    }
    suspend fun get(uid: String, id: String): StudyClass {
        checkOwner(uid)
        val result = StudyClass.from(db.collection("classes").document(id).get(Source.SERVER).await())
        val role = db.collection("users").document(uid).get(Source.SERVER).await().getString("role")
        checkOwner(uid)
        return result.copy(canManage = role == "admin" || (role == "leader" && result.leaderUid == uid))
    }
    suspend fun roster(uid: String, id: String): List<ClassStudent> {
        val group = get(uid, id)
        check(group.canManage) { "Manager access required" }
        val answers = db.collection("classAnswers").whereEqualTo("_class", id).get(Source.SERVER).await()
        val answered = answers.documents.filter { doc ->
            doc.getString("_lesson") in group.lessonSlugs &&
                doc.data.orEmpty().any { (key, value) -> key.matches(Regex("q[0-9]+[a-z]*")) && value is String && value.isNotBlank() }
        }.groupBy { it.getString("_uid") }.mapValues { (_, docs) -> docs.mapNotNull { it.getString("_lesson") }.toSet() }
        val students = group.members.mapIndexed { index, member ->
            val profile = try { db.collection("users").document(member).get(Source.SERVER).await() }
                catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                    if (e.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                    null
                }
            checkOwner(uid)
            ClassStudent(member, profile?.getString("name")?.takeIf { it.isNotBlank() }
                ?: profile?.getString("email") ?: "Участник ${index + 1}", answered[member].orEmpty())
        }
        checkOwner(uid)
        return students.sortedBy { it.name }
    }
    suspend fun studentAnswers(uid: String, classId: String, studentUid: String, slug: String): AnswerRecord {
        val group = get(uid, classId)
        check(group.canManage && studentUid in group.members && slug in group.lessonSlugs) { "Student access unavailable" }
        val doc = db.collection("classAnswers").document("${classId}_${studentUid}_$slug").get(Source.SERVER).await()
        checkOwner(uid)
        val values = doc.data.orEmpty().filter { (key, value) -> key.matches(Regex("q[0-9]+[a-z]*")) && value is String }
            .mapValues { it.value as String }
        return AnswerRecord(studentUid, slug, values, values, classId = classId)
    }
}

data class ClassStudent(val uid: String, val name: String, val answeredSlugs: Set<String>)
