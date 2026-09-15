package ru.spbchurch.biblelearning

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object EditorSessions { val count = AtomicInteger(0) }

data class SyncReport(val sent: Int, val received: Int, val conflicts: Int, val error: String? = null) {
    fun description() = error ?: "Отправлено: $sent · Получено: $received · Конфликты: $conflicts"
}
object SyncEngine {
    private val mutex = Mutex()
    suspend fun awaitIdle() { mutex.withLock { } }
    fun connected(context: Context, unmetered: Boolean): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (!unmetered || !manager.isActiveNetworkMetered)
    }
    private fun answers(data: Map<String, Any>?): Map<String, String> =
        data.orEmpty().filter { (k, v) -> k.matches(Regex("q[0-9]+[a-z]*")) && v is String }
            .mapValues { it.value as String }.let(SyncPolicy::normalize)

    suspend fun sync(context: Context, explicit: Boolean = false): SyncReport = mutex.withLock {
        if (!explicit && EditorSessions.count.get() > 0)
            return@withLock SyncReport(0, 0, 0, "Синхронизация отложена до закрытия урока.")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withLock SyncReport(0, 0, 0, "Войдите в аккаунт сайта для синхронизации.")
        val prefs = Preferences(context)
        if (!connected(context, prefs.unmetered))
            return@withLock SyncReport(0, 0, 0, if (prefs.unmetered)
                "Ожидание Wi-Fi или другой безлимитной сети. Ответы остаются на телефоне."
                else "Нет интернета. Ответы остаются на телефоне.")
        val local = AnswerStore.get(context)
        var sent = 0
        var received = 0
        var conflicts = 0
        try {
            withTimeout(60_000) {
                val db = FirebaseFirestore.getInstance()
                val remote = db.collection("answers").whereEqualTo("_uid", uid).get(Source.SERVER).await()
                check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
                val bySlug = remote.documents.mapNotNull { doc ->
                    val slug = doc.getString("_lesson") ?: return@mapNotNull null
                    if (doc.id != uid + "_" + slug) return@mapNotNull null
                    slug to answers(doc.data)
                }.toMap()
                (bySlug.keys + local.records(uid).map { it.slug }).forEach { slug ->
                    local.acceptRemote(uid, slug, bySlug[slug].orEmpty())
                    received++
                }
                local.records(uid).filter { it.dirty || it.remoteConflict != null }.forEach { snapshot ->
                    check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
                    val ref = db.collection("answers").document(uid + "_" + snapshot.slug)
                    val outcome = db.runTransaction { tx ->
                        check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
                        val current = answers(tx.get(ref).data)
                        val merge = SyncPolicy.merge(snapshot.base, snapshot.values, current)
                        if (merge.conflicts.isEmpty()) {
                            val data = mutableMapOf<String, Any>()
                            data.putAll(merge.answers)
                            data["_uid"] = uid
                            data["_lesson"] = snapshot.slug
                            data["_savedAt"] = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                            tx.set(ref, data)
                        }
                        Pair(merge, current)
                    }.await()
                    if (outcome.first.conflicts.isEmpty()) {
                        local.acknowledge(snapshot, outcome.first.answers); sent++
                    } else {
                        local.conflict(snapshot, outcome.second); conflicts++
                    }
                }
                prefs.setLastSync(uid)
            }
            SyncReport(sent, received, conflicts)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            SyncReport(sent, received, conflicts, "Сервер не ответил вовремя. Локальные ответы сохранены; повторите синхронизацию.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncReport(sent, received, conflicts, "Синхронизация не завершена. Проверьте интернет и доступ к аккаунту. Ответы остаются на телефоне.")
        }
    }
}
object SyncScheduler {
    private const val PERIODIC = "personal-answers-periodic"
    private const val ONCE = "personal-answers-once"
    private fun constraints(context: Context) = Constraints.Builder()
        .setRequiredNetworkType(if (Preferences(context).unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()
    fun configure(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!Preferences(context).autoSync || FirebaseAuth.getInstance().currentUser == null) {
            manager.cancelUniqueWork(PERIODIC)
            manager.cancelUniqueWork(ONCE)
            return
        }
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AnswerSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints(context)).build())
    }
    fun afterEdit(context: Context) {
        if (!Preferences(context).autoSync || FirebaseAuth.getInstance().currentUser == null) return
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AnswerSyncWorker>().setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(constraints(context)).build())
    }
}
class AnswerSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Preferences(applicationContext).autoSync || FirebaseAuth.getInstance().currentUser == null) return Result.success()
        val report = SyncEngine.sync(applicationContext)
        return if (report.error == null) Result.success() else Result.retry()
    }
}
