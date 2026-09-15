package ru.spbchurch.biblelearning

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {
    private var lessons = emptyList<Lesson>()
    private var records = emptyList<AnswerRecord>()
    private lateinit var results: LinearLayout
    private var course: String? = null
    private var savedOnly = false
    private var query = ""
    private var rendering = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        course = savedInstanceState?.getString("course")
        savedOnly = savedInstanceState?.getBoolean("saved") ?: false
        query = savedInstanceState?.getString("query").orEmpty()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    course != null -> { course = null; renderResults() }
                    savedOnly -> { savedOnly = false; build() }
                    else -> finish()
                }
            }
        })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("course", course); outState.putBoolean("saved", savedOnly); outState.putString("query", query)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        refresh()
    }
    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid ?: AnswerStore.GUEST
    private fun refresh() {
        if (rendering) return
        rendering = true
        lifecycleScope.launch {
            try {
                val owner = uid()
                val loaded = withContext(Dispatchers.IO) { LessonRepository(this@MainActivity).all() to AnswerStore.get(this@MainActivity).records(owner) }
                if (owner != uid()) return@launch
                lessons = loaded.first; records = loaded.second
                build()
            } catch (e: Exception) {
                val content = screen("Изучение Библии", false)
                content.addView(text("Не удалось открыть каталог. Ответы не удалены.", 18))
                content.addView(action("Повторить") { refresh() })
                content.addView(action("Настройки", false) { launchSettings() })
            } finally { rendering = false }
        }
    }
    private fun build() {
        val content = screen("Изучение Библии", false)
        card(content) {
            addView(text(if (savedOnly) "МОЯ ТЕТРАДЬ" else "ЧИТАТЬ · РАЗМЫШЛЯТЬ · ПРИМЕНЯТЬ", 12, true, true))
            addView(text(if (savedOnly) "Ваши размышления" else "Писание.\nВ вашем ритме.", 30, true))
            addView(text(if (savedOnly) "Личные ответы и закладки этого аккаунта."
                else "Все уроки из комплекта доступны без интернета.", 15, muted = true))
            val last = prefs.lastLesson(uid())?.let { slug -> lessons.find { it.slug == slug } }
            if (last != null) addView(action("Продолжить: " + last.title) { open(last) })
        }
        val pending = records.count { it.dirty }
        val conflicts = records.count { it.remoteConflict != null }
        content.addView(text(if (uid() == AnswerStore.GUEST)
            "Гостевой режим · черновики только на телефоне"
            else "Не отправлено: $pending · Конфликты: $conflicts", 13, muted = true))
        val search = input(content, "Найти курс, урок или отрывок")
        search.setText(query)
        search.doAfterTextChanged { query = it.toString(); renderResults() }
        results = column()
        content.addView(results)
        val navigation = BottomNavigationView(this).apply {
            setBackgroundColor(palette().surface)
            menu.add(0, 1, 0, "Курсы").setIcon(android.R.drawable.ic_menu_agenda)
            menu.add(0, 2, 1, "Моё").setIcon(android.R.drawable.ic_menu_edit)
            menu.add(0, 3, 2, "Настройки").setIcon(android.R.drawable.ic_menu_preferences)
            selectedItemId = if (savedOnly) 2 else 1
            setOnItemSelectedListener { item ->
                if (item.itemId == 3) { launchSettings(); false }
                else { savedOnly = item.itemId == 2; course = null; build(); true }
            }
        }
        root.addView(navigation)
        renderResults()
    }
    private fun renderResults() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        if (course != null) {
            results.addView(action("Все курсы", false) { course = null; renderResults() })
            results.addView(text(Courses.name(course!!), 24, true))
        }
        val bookmarks = getSharedPreferences("bookmarks", MODE_PRIVATE)
        val selected = lessons.filter { lesson ->
            (course == null || lesson.course == course) &&
                (!savedOnly || records.any { it.slug == lesson.slug && (it.values.isNotEmpty() || it.remoteConflict != null || it.dirty) } ||
                    bookmarks.getBoolean(uid() + ":" + lesson.slug, false)) &&
                (query.isBlank() || (lesson.title + " " + lesson.reference + " " + Courses.name(lesson.course)).contains(query.trim(), true))
        }
        if (selected.isEmpty()) {
            results.addView(text(if (savedOnly) "Здесь появятся ваши ответы и закладки." else "Ничего не найдено. Попробуйте другое слово.", 18))
            return
        }
        if (course == null && !savedOnly && query.isBlank()) {
            results.addLabel("БИБЛИОТЕКА · КУРСОВ: " + selected.groupBy { it.course }.size)
            selected.groupBy { it.course }.entries.sortedBy { Courses.names.keys.indexOf(it.key) }.forEach { (id, items) ->
                card(results) {
                    addView(text(Courses.name(id), 21, true))
                    addView(text("Уроков: " + items.size + " · На телефоне", 14, muted = true))
                    val answered = records.count { r -> items.any { it.slug == r.slug } && r.values.isNotEmpty() }
                    if (answered > 0) addView(text("С ответами: $answered", 13, muted = true))
                    addView(action("Открыть курс", false) { course = id; renderResults() })
                }
            }
        } else selected.sortedWith(compareBy({ it.course }, { it.order })).forEach { lesson ->
            card(results) {
                addView(text(Courses.name(lesson.course), 12, true, true))
                addView(text(lesson.title, 20, true))
                addView(text(lesson.reference, 14, muted = true))
                val record = records.find { it.slug == lesson.slug }
                val status = when {
                    record?.remoteConflict != null -> "Две версии ответа · нужен ваш выбор"
                    record?.dirty == true -> if (uid() == AnswerStore.GUEST) "Черновик на телефоне" else "Есть неотправленные изменения"
                    record?.values?.isNotEmpty() == true -> "Сохранено · Ответов: " + record.values.size
                    else -> "Можно читать офлайн"
                }
                addView(text(status, 13, muted = true))
                addView(action("Читать и отвечать", false) { open(lesson) })
            }
        }
    }
    private fun open(lesson: Lesson) {
        startActivity(Intent(this, LessonActivity::class.java).putExtra("slug", lesson.slug))
    }
}
