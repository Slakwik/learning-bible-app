package ru.spbchurch.biblelearning

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LessonActivity : BaseActivity() {
    private lateinit var lesson: Lesson
    private lateinit var owner: String
    private lateinit var status: TextView
    private var record: AnswerRecord? = null
    private var loading = false
    private var editingSession = false
    private var writeFailed = false
    private var editorContent: LinearLayout? = null
    private val appContext by lazy { applicationContext }
    private val markdown by lazy { Markwon.builder(this).usePlugin(HtmlPlugin.create()).build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = savedInstanceState?.getString("owner")
            ?: (FirebaseAuth.getInstance().currentUser?.uid ?: AnswerStore.GUEST)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("owner", owner)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        val now = FirebaseAuth.getInstance().currentUser?.uid ?: AnswerStore.GUEST
        if (now != owner) { finish(); return }
        if (!editingSession) { EditorSessions.count.incrementAndGet(); editingSession = true }
        if (prefs.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        load()
    }
    override fun onPause() {
        savePosition()
        super.onPause()
        if (editingSession) { EditorSessions.count.decrementAndGet(); editingSession = false }
        SyncScheduler.afterEdit(appContext)
    }
    private fun load() {
        if (loading) return
        savePosition()
        loading = true
        editorContent?.let { disableChildren(it) }
        lifecycleScope.launch {
        // Let a sync already in flight finish before displaying its baseline.
        SyncEngine.awaitIdle()
        // A barrier behind queued edits ensures navigation/recreation reads the durable latest values.
        LocalIo.executor.execute {
            val result = runCatching {
                val item = LessonRepository(appContext).get(intent.getStringExtra("slug").orEmpty())
                    ?: error("Урок не найден")
                item to AnswerStore.get(appContext).record(owner, item.slug)
            }
            runOnUiThread {
                loading = false
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.onSuccess { (item, answers) ->
                    lesson = item; record = answers
                    prefs.remember(owner, lesson.slug)
                    build()
                }.onFailure {
                    screen("Урок").addView(text("Не удалось открыть урок. Вернитесь в каталог и попробуйте ещё раз.", 18))
                }
            }
        }
        }
    }
    private fun build() {
        val content = screen(Courses.name(lesson.course))
        editorContent = content
        val saved = record ?: return
        content.addView(text(lesson.title, 28, true))
        content.addView(text(lesson.reference, 16, muted = true))
        val marks = getSharedPreferences("bookmarks", MODE_PRIVATE)
        val key = owner + ":" + lesson.slug
        content.addView(action(if (marks.getBoolean(key, false)) "Убрать закладку" else "В закладки", false) {
            marks.edit().putBoolean(key, !marks.getBoolean(key, false)).apply()
            load()
        })
        content.addView(action("Оформление чтения", false) { launchSettings() })
        status = text(if (saved.remoteConflict != null) "Есть две версии — сравните их ниже."
            else if (owner == AnswerStore.GUEST) "Черновики сохраняются только на этом телефоне."
            else if (saved.dirty) "Сохранено на телефоне · ожидает синхронизации"
            else "Локальная копия · изменения сохраняются при вводе", 13, muted = true)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(status)
        saved.remoteConflict?.let { remote ->
            card(content) {
                addView(text("Ответ изменён на двух устройствах", 20, true))
                addView(text("Автоматическая перезапись остановлена. Ниже можно сравнить обе версии.", 15))
                (saved.values.keys + remote.keys).sorted().forEach { id ->
                    if (saved.values[id].orEmpty() != remote[id].orEmpty()) {
                        addView(text("Вопрос $id", 16, true))
                        addView(text("На телефоне:\n" + saved.values[id].orEmpty(), 16))
                        addView(text("На сайте:\n" + remote[id].orEmpty(), 16, muted = true))
                    }
                }
                addView(action("Оставить ответы телефона") { resolve(false) })
                addView(action("Взять ответы сайта", false) { resolve(true) })
            }
        }
        lesson.blocks.forEach { block ->
            when (block) {
                is LessonBlock.Reading -> {
                    val view = text("", prefs.fontSize).apply {
                        typeface = if (prefs.serif) Typeface.create("serif", Typeface.NORMAL) else Typeface.DEFAULT
                        setLineSpacing(0f, if (prefs.roomy) 1.45f else 1.15f)
                        setTextIsSelectable(true)
                        setPadding(0, dp(12), 0, dp(12))
                    }
                    markdown.setMarkdown(view, block.markdown)
                    content.addView(view)
                }
                is LessonBlock.Question -> card(content) {
                    val label = HtmlCompat.fromHtml(block.label, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    addView(text(label, prefs.fontSize, true))
                    val edit = input(this, "Ваш ответ · " + block.id, true)
                    edit.id = View.generateViewId()
                    edit.isSaveEnabled = false // SQLite is the source of truth, not stale view hierarchy state.
                    edit.textSize = prefs.fontSize.toFloat()
                    edit.setText(saved.values[block.id].orEmpty())
                    edit.isEnabled = saved.remoteConflict == null
                    edit.doAfterTextChanged { value ->
                        val snapshot = value.toString()
                        status.text = "Сохраняется на телефоне…"
                        val slug = lesson.slug
                        val uid = owner
                        LocalIo.executor.execute {
                            val result = runCatching { AnswerStore.get(appContext).edit(uid, slug, block.id, snapshot) }
                            runOnUiThread {
                                if (!isDestroyed) {
                                    if (result.isFailure) {
                                        writeFailed = true
                                        status.text = "Не удалось сохранить! Не закрывайте урок; скопируйте текст и освободите память."
                                    } else if (!writeFailed) status.text = if (uid == AnswerStore.GUEST)
                                        "Черновик сохранён на телефоне" else "Сохранено на телефоне · ещё не отправлено"
                                }
                            }
                        }
                    }
                }
            }
        }
        if (owner == AnswerStore.GUEST) content.addView(action("Войти в аккаунт сайта") {
            confirm("Войти?", "Гостевые черновики останутся отдельно на телефоне и не будут автоматически отправлены в аккаунт.", "Войти") {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }) else content.addView(action("Синхронизировать сейчас") {
            // Do not allow typing during an explicit refresh of the lesson.
            if (writeFailed) { message("Сначала сохраните текст: возникла ошибка памяти."); return@action }
            loading = true
            content.isEnabled = false
            disableChildren(content)
            status.text = "Синхронизация…"
            LocalIo.executor.execute {
                runOnUiThread {
                    lifecycleScope.launch {
                        val report = withContext(Dispatchers.IO) { SyncEngine.sync(appContext, explicit = true) }
                        message(report.description())
                        loading = false
                        load()
                    }
                }
            }
        })
        content.addView(text("Это личные ответы. Ответы класса доступны отдельно на сайте.", 13, muted = true))
        val scroll = root.findViewById<android.widget.ScrollView>(R.id.screen_scroll)
        val position = prefs.readingPosition(owner, lesson.slug)
        scroll.post {
            val range = (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, (range * position).toInt())
        }
    }
    private fun savePosition() {
        if (!::lesson.isInitialized || editorContent == null) return
        val scroll = root.findViewById<android.widget.ScrollView>(R.id.screen_scroll) ?: return
        val range = (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(1)
        prefs.rememberPosition(owner, lesson.slug, scroll.scrollY.toFloat() / range)
    }
    private fun disableChildren(parent: android.view.ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            child.isEnabled = false
            if (child is android.view.ViewGroup) disableChildren(child)
        }
    }
    private fun resolve(remote: Boolean) {
        confirm("Выбрать эту версию?", if (remote) "Локальные ответы этого урока будут заменены показанной версией сайта. Перед заменой можно экспортировать обе версии в настройках."
            else "Ответы телефона будут подготовлены к отправке. Если сайт изменился снова, приложение ещё раз проверит конфликт.", "Выбрать") {
            LocalIo.executor.execute {
                AnswerStore.get(appContext).resolve(owner, lesson.slug, remote)
                runOnUiThread { load() }
            }
        }
    }
}
