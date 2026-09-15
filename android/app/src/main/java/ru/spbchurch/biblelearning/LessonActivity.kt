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
    private val studentUid: String? get() = intent.getStringExtra("studentUid")
    private val reviewing: Boolean get() = studentUid != null
    private var reviewRecord: AnswerRecord? = null
    private var studyClass: StudyClass? = null
    private val canWrite: Boolean get() = LessonAccess.canWrite(owner, lesson.slug, studyClass, reviewing)
    private fun positionKey() = classId?.let { "class:$it:${studentUid ?: owner}:${lesson.slug}" } ?: lesson.slug
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
        if (now == AnswerStore.GUEST || now != owner) { finish(); return }
        if (classId != null && !editingSession) { EditorSessions.count.incrementAndGet(); editingSession = true }
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
                    if (reviewing) reviewRecord = ClassRepository().studentAnswers(owner, id, studentUid!!, slug)
                    else withContext(Dispatchers.IO) { SyncEngine.readClassLesson(appContext, owner, id, slug) }
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
                item to (if (classId == null) null else if (reviewing) checkNotNull(reviewRecord) else AnswerStore.get(appContext).record(owner, item.slug, classId))
            }
            runOnUiThread {
                loading = false
                if (isDestroyed || isFinishing || owner != (FirebaseAuth.getInstance().currentUser?.uid ?: AnswerStore.GUEST)) return@runOnUiThread
                result.onSuccess { (item, answers) ->
                    lesson = item; record = answers
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
        if (classId == null) {
            content.addView(text(lesson.title, 24, true))
            content.addView(text(lesson.reference, 16, muted = true))
            notice(content, "Ознакомление", "Здесь только вводный текст. Вопросы и ответы доступны в вашем классе.")
            val preview = LessonAccess.blocks(owner != AnswerStore.GUEST, false, lesson.blocks)
            preview.filterIsInstance<LessonBlock.Reading>().forEach { block ->
                val view = text("", prefs.fontSize).apply {
                    typeface = if (prefs.serif) Typeface.create("serif", Typeface.NORMAL) else Typeface.DEFAULT
                    setLineSpacing(0f, if (prefs.roomy) 1.45f else 1.15f)
                    setTextIsSelectable(true)
                }
                markdown.setMarkdown(view, block.markdown)
                content.addView(view)
            }
            if (preview.isEmpty()) content.addView(text("В этом уроке нет вводного текста перед вопросами.", 16))
            content.addView(action("Перейти в класс") {
                startActivity(Intent(this, MainActivity::class.java).putExtra("destination", 2)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            })
            content.addView(action("Оформление", false) { launchSettings() })
            return
        }
        val saved = record ?: return
        content.addView(text(lesson.title, 24, true))
        content.addView(text(lesson.reference, 16, muted = true))
        content.addView(text(studyClass?.let { "Класс: ${it.name} · Ведущий: ${it.leader}" }
            ?: "Урок класса", 15, true))
        if (reviewing) content.addView(text("Ответы ученика: ${intent.getStringExtra("studentName").orEmpty()} · только просмотр", 16, true))
        else if (!canWrite) content.addView(text("Только чтение: класс архивирован или вы не участник.", 16, true))
        val marks = getSharedPreferences("bookmarks", MODE_PRIVATE)
        val key = owner + ":" + positionKey()
        val readingControls = LinearLayout(this)
        if (!reviewing) readingControls.addView(action(if (marks.getBoolean(key, false)) "В закладках" else "Закладка", false) {
            marks.edit().putBoolean(key, !marks.getBoolean(key, false)).apply()
            load()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        readingControls.addView(action("Оформление", false) { launchSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(readingControls)
        status = text(if (reviewing) "Ответы класса загружены с сайта · изменения недоступны"
            else if (saved.remoteConflict != null) "Есть две версии — сравните их ниже."
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
        LessonAccess.blocks(owner != AnswerStore.GUEST, studyClass != null, lesson.blocks).forEach { block ->
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
                    val prompt = text(label, prefs.fontSize, true)
                    addView(prompt)
                    if (!canWrite) {
                        addView(text(saved.values[block.id]?.takeIf { it.isNotBlank() } ?: "Ответ пока не добавлен", prefs.fontSize))
                        return@card
                    }
                    val edit = input(this, "Ответ на вопрос " + block.id.removePrefix("q"), true)
                    edit.id = View.generateViewId()
                    prompt.labelFor = edit.id
                    edit.isSaveEnabled = false // SQLite is the source of truth, not stale view hierarchy state.
                    edit.textSize = prefs.fontSize.toFloat()
                    edit.setText(saved.values[block.id].orEmpty())
                    edit.isEnabled = canWrite && saved.remoteConflict == null
                    edit.doAfterTextChanged { value ->
                        if (!canWrite) return@doAfterTextChanged
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
        if (!reviewing) root.addView(action("Синхронизировать класс") {
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
                        message((studyClass?.let { "Класс: ${it.name}\n" } ?: "Урок класса\n") + report.description())
                        loading = false
                        load()
                    }
                }
            }
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).apply { marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(8) }
        })
        content.addView(text("Ответы сохраняются в этот класс и доступны его ведущему на сайте.", 13, muted = true))
        studyClass?.let { group ->
            val index = group.lessonSlugs.indexOf(lesson.slug)
            listOf(-1, 1).forEach { step ->
                group.lessonSlugs.getOrNull(index + step)?.let { slug ->
                    content.addView(action(if (step < 0) "Предыдущий урок класса" else "Следующий урок класса", false) {
                        startActivity(Intent(this, LessonActivity::class.java).putExtra("slug", slug).putExtra("classId", group.id).putExtra("studentUid", studentUid).putExtra("studentName", intent.getStringExtra("studentName")))
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
        if (!canWrite) return
        confirm("Выбрать эту версию?", if (remote) "Локальные ответы этого урока будут заменены показанной версией сайта. Перед заменой можно экспортировать обе версии в настройках."
            else "Ответы телефона будут подготовлены к отправке. Если сайт изменился снова, приложение ещё раз проверит конфликт.", "Выбрать") {
            LocalIo.executor.execute {
                AnswerStore.get(appContext).resolve(owner, lesson.slug, remote, classId)
                runOnUiThread { load() }
            }
        }
    }
}
