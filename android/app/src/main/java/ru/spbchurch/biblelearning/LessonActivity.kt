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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LessonActivity : BaseActivity() {
    private lateinit var lesson: Lesson
    private lateinit var owner: String
    private lateinit var status: TextView
    private var record: AnswerRecord? = null
    private val classId: String? get() = intent.getStringExtra("classId")?.takeIf { it.isNotBlank() }
    private var studyClass: StudyClass? = null
    private val canWrite: Boolean get() = classId == null || studyClass?.canWrite(owner, lesson.slug) == true
    private fun positionKey() = classId?.let { "class:$it:${lesson.slug}" } ?: lesson.slug
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
        try {
            classId?.let { id ->
                check(SyncEngine.connected(appContext, prefs.unmetered))
                withTimeout(20_000) {
                    studyClass = ClassRepository().get(owner, id)
                    val slug = intent.getStringExtra("slug").orEmpty()
                    check(slug in studyClass!!.lessonSlugs) { "Lesson no longer in class" }
                    withContext(Dispatchers.IO) { SyncEngine.readClassLesson(appContext, owner, id, slug) }
                }
            }
        } catch (e: CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            classLoadError(); return@launch
        } catch (e: Exception) {
            classLoadError(); return@launch
        }
        // A barrier behind queued edits ensures navigation/recreation reads the durable latest values.
        LocalIo.executor.execute {
            val result = runCatching {
                val item = LessonRepository(appContext).get(intent.getStringExtra("slug").orEmpty())
                    ?: error("Урок не найден")
                item to AnswerStore.get(appContext).record(owner, item.slug, classId)
            }
            runOnUiThread {
                loading = false
                if (isDestroyed || isFinishing || owner != (FirebaseAuth.getInstance().currentUser?.uid ?: AnswerStore.GUEST)) return@runOnUiThread
                result.onSuccess { (item, answers) ->
                    lesson = item; record = answers
                    if (classId == null) prefs.remember(owner, lesson.slug)
                    build()
                }.onFailure {
                    screen("Урок").addView(text("Не удалось открыть урок. Вернитесь в каталог и попробуйте ещё раз.", 18))
                }
            }
        }
        }
    }
    private fun classLoadError() {
        loading = false
        editorContent = null
        val content = screen("Урок класса")
        content.addView(text("Не удалось проверить доступ или загрузить ответы класса. Проверьте сеть и членство в классе. Черновики сохранены на телефоне и доступны для экспорта в настройках.", 18))
        content.addView(action("Повторить") { load() })
        content.addView(action("Настройки", false) { launchSettings() })
    }
    private fun build() {
        val content = screen(Courses.name(lesson.course))
        editorContent = content
        val saved = record ?: return
        content.addView(text(lesson.title, 28, true))
        content.addView(text(lesson.reference, 16, muted = true))
        content.addView(text(studyClass?.let { "Класс: ${it.name} · Ведущий: ${it.leader}" }
            ?: "Личное изучение", 15, true))
        if (!canWrite) content.addView(text("Только чтение: класс архивирован или вы не участник.", 16, true))
        val marks = getSharedPreferences("bookmarks", MODE_PRIVATE)
        val key = owner + ":" + positionKey()
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
                if (canWrite) {
                    addView(action("Оставить ответы телефона") { resolve(false) })
                    addView(action("Взять ответы сайта", false) { resolve(true) })
                }
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
                    edit.isEnabled = canWrite && saved.remoteConflict == null
                    edit.doAfterTextChanged { value ->
                        val snapshot = value.toString()
                        status.text = "Сохраняется на телефоне…"
                        val slug = lesson.slug
                        val uid = owner
                        LocalIo.executor.execute {
                            val result = runCatching { AnswerStore.get(appContext).edit(uid, slug, block.id, snapshot, classId) }
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
                        val report = withContext(Dispatchers.IO) { SyncEngine.sync(appContext, explicit = true, onlyClassId = classId) }
                        message((studyClass?.let { "Класс: ${it.name}\n" } ?: "Личное изучение и доступные классы\n") + report.description())
                        loading = false
                        load()
                    }
                }
            }
        })
        content.addView(text(if (classId == null) "Это личные ответы. Для работы с группой откройте раздел «Класс»."
            else "Ответы сохраняются в этот класс и доступны его ведущему на сайте.", 13, muted = true))
        studyClass?.let { group ->
            val index = group.lessonSlugs.indexOf(lesson.slug)
            listOf(-1, 1).forEach { step ->
                group.lessonSlugs.getOrNull(index + step)?.let { slug ->
                    content.addView(action(if (step < 0) "Предыдущий урок класса" else "Следующий урок класса", false) {
                        startActivity(Intent(this, LessonActivity::class.java).putExtra("slug", slug).putExtra("classId", group.id))
                        finish()
                    })
                }
            }
        }
        val scroll = root.findViewById<android.widget.ScrollView>(R.id.screen_scroll)
        val position = prefs.readingPosition(owner, positionKey())
        scroll.post {
            val range = (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, (range * position).toInt())
        }
    }
    private fun savePosition() {
        if (!::lesson.isInitialized || editorContent == null) return
        val scroll = root.findViewById<android.widget.ScrollView>(R.id.screen_scroll) ?: return
        val range = (scroll.getChildAt(0).height - scroll.height).coerceAtLeast(1)
        prefs.rememberPosition(owner, positionKey(), scroll.scrollY.toFloat() / range)
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
                AnswerStore.get(appContext).resolve(owner, lesson.slug, remote, classId)
                runOnUiThread { load() }
            }
        }
    }
}
