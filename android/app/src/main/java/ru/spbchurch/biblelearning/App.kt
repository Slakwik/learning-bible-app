package ru.spbchurch.biblelearning

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.util.concurrent.Executors

class BibleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The existing project's public configuration; no service-account credentials.
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
                .setApiKey("AIzaSyBu_g78Wmrls7_Q6A1mTgQwb013LlTiWls")
                .setApplicationId("1:1093935422667:web:a1d86785a7ac3aea320f4e")
                .setProjectId("bible-learning-b4b4d").build())
        }
        // Our SQLite store owns offline state. Never silently queue Firestore writes.
        FirebaseFirestore.getInstance().firestoreSettings =
            FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        FirebaseAuth.getInstance().addAuthStateListener { SyncScheduler.configure(this) }
    }
}

object LocalIo {
    val executor = Executors.newSingleThreadExecutor()
}

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings_v2", Context.MODE_PRIVATE)
    var bibleTranslation: String
        get() = prefs.getString("bible_translation", "synodal")?.takeIf { it == "nrt" } ?: "synodal"
        set(value) { prefs.edit().putString("bible_translation", if (value == "nrt") "nrt" else "synodal").apply() }
    var loginEmail: String
        get() = prefs.getString("login_email", "").orEmpty()
        set(value) { prefs.edit().putString("login_email", value).apply() }
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) { prefs.edit().putString("theme", value).apply() }
    var fontSize: Int
        get() = prefs.getInt("font_size", 18).coerceIn(16, 26)
        set(value) { prefs.edit().putInt("font_size", value.coerceIn(16, 26)).apply() }
    var serif: Boolean
        get() = prefs.getBoolean("serif", true)
        set(value) { prefs.edit().putBoolean("serif", value).apply() }
    var roomy: Boolean
        get() = prefs.getBoolean("roomy", true)
        set(value) { prefs.edit().putBoolean("roomy", value).apply() }
    var keepAwake: Boolean
        get() = prefs.getBoolean("awake", false)
        set(value) { prefs.edit().putBoolean("awake", value).apply() }
    var autoSync: Boolean
        get() = prefs.getBoolean("auto_sync", false)
        set(value) { prefs.edit().putBoolean("auto_sync", value).apply() }
    var unmetered: Boolean
        get() = prefs.getBoolean("unmetered", true)
        set(value) { prefs.edit().putBoolean("unmetered", value).apply() }
    fun lastSync(uid: String) = prefs.getLong("last_sync:$uid", 0)
    fun setLastSync(uid: String) { prefs.edit().putLong("last_sync:$uid", System.currentTimeMillis()).apply() }
    fun lastLesson(uid: String) = prefs.getString("last_lesson:$uid", null)
    fun remember(uid: String, slug: String) { prefs.edit().putString("last_lesson:$uid", slug).apply() }
    fun readingPosition(uid: String, slug: String) = prefs.getFloat("position:$uid:$slug", 0f).coerceIn(0f, 1f)
    fun rememberPosition(uid: String, slug: String, fraction: Float) {
        prefs.edit().putFloat("position:$uid:$slug", fraction.coerceIn(0f, 1f)).apply()
    }
    fun resetReading() {
        prefs.edit().remove("theme").remove("font_size").remove("serif")
            .remove("roomy").remove("awake").apply()
    }
    fun appearanceKey() = "$theme/$fontSize/$serif/$roomy/$keepAwake"
}
