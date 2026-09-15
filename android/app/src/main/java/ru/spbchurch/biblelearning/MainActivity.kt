package ru.spbchurch.biblelearning

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : BaseActivity() {
    private var lessons = emptyList<Lesson>()
    private var records = emptyList<AnswerRecord>()
    private lateinit var results: LinearLayout
    private var course: String? = null
    private var classTab = false
    private var selectedClassId: String? = null
    private var classes: List<StudyClass>? = null
    private var studentsMode = false
    private var students: List<ClassStudent>? = null
    private var selectedStudent: ClassStudent? = null
    private var rosterLoading = false
    private var rosterError: String? = null
    private var classError: String? = null
    private var query = ""
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        course = savedInstanceState?.getString("course")
        classTab = savedInstanceState?.getBoolean("class_tab") ?: false
        selectedClassId = savedInstanceState?.getString("class_id")
        query = savedInstanceState?.getString("query").orEmpty()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    course != null -> { course = null; renderResults() }
                    classTab && selectedStudent != null -> { selectedStudent = null; renderResults() }
                    classTab && studentsMode -> { studentsMode = false; renderResults() }
                    classTab && selectedClassId != null -> { selectedClassId = null; renderResults() }
                    classTab -> { classTab = false; build() }
                    else -> finish()
                }
            }
        })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("course", course)
        outState.putBoolean("class_tab", classTab)
        outState.putString("class_id", selectedClassId)
        outState.putString("query", query)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); refresh() }
    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid ?: AnswerStore.GUEST

    private fun refresh() {
        val request = ++generation
        val owner = uid()
        classes = null
        students = null
        selectedStudent = null
        rosterLoading = false
        rosterError = null
        classError = null
        lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    LessonRepository(this@MainActivity).all() to AnswerStore.get(this@MainActivity).allRecords(owner)
                }
                if (owner != uid() || request != generation) return@launch
                lessons = loaded.first; records = loaded.second
                build()
                if (owner != AnswerStore.GUEST) {
                    try {
                        check(SyncEngine.connected(this@MainActivity, prefs.unmetered))
                        val loadedClasses = withTimeout(20_000) { ClassRepository().available(owner) }
                        if (owner != uid() || request != generation) return@launch
                        classes = loadedClasses
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        if (owner != uid() || request != generation) return@launch
                        classError = "Сервер не ответил. Повторите загрузку классов."
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (owner != uid() || request != generation) return@launch
                        classError = "Не удалось проверить доступ к классам. Подключитесь к разрешённой в настройках сети и повторите."
                    }
                    if (owner != uid() || request != generation) return@launch
                    if (classTab) renderResults()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request != generation) return@launch
                val content = screen("Изучение Библии", false)
                content.addView(text("Не удалось открыть каталог. Ответы не удалены.", 18))
                content.addView(action("Повторить") { refresh() })
                content.addView(action("Настройки", false) { launchSettings() })
            }
        }
    }
    private fun build() {
        val content = screen(if (classTab) "Класс" else "Изучение Библии", false)
        if (!classTab) {
            card(content) {
                addView(text("ЛИЧНОЕ ИЗУЧЕНИЕ", 12, true, true))
                addView(text("Писание.\nВ вашем ритме.", 30, true))
                addView(text("Все уроки доступны без интернета. Для работы с группой откройте «Класс».", 15, muted = true))
                val last = prefs.lastLesson(uid())?.let { slug -> lessons.find { it.slug == slug } }
                if (last != null) addView(action("Продолжить: " + last.title) { open(last) })
            }
            val search = input(content, "Найти курс, урок или отрывок")
            search.setText(query)
            search.doAfterTextChanged { query = it.toString(); renderResults() }
        }
        results = column()
        content.addView(results)
        val navigation = BottomNavigationView(this).apply {
            setBackgroundColor(palette().surface)
            menu.add(0, 1, 0, "Курсы").setIcon(android.R.drawable.ic_menu_agenda)
            menu.add(0, 2, 1, "Класс").setIcon(android.R.drawable.ic_menu_myplaces)
            menu.add(0, 3, 2, "Настройки").setIcon(android.R.drawable.ic_menu_preferences)
            selectedItemId = if (classTab) 2 else 1
            setOnItemSelectedListener { item ->
                if (item.itemId == 3) { launchSettings(); false }
                else { classTab = item.itemId == 2; course = null; selectedClassId = null; studentsMode = false; selectedStudent = null; students = null; build(); true }
            }
        }
        root.addView(navigation)
        renderResults()
    }
    private fun renderResults() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        if (classTab) { renderClasses(); return }
        if (course != null) {
            results.addView(action("Все курсы", false) { course = null; renderResults() })
            results.addView(text(Courses.name(course!!), 24, true))
        }
        val selected = lessons.filter { lesson ->
            (course == null || lesson.course == course) &&
                (query.isBlank() || (lesson.title + " " + lesson.reference + " " + Courses.name(lesson.course)).contains(query.trim(), true))
        }
        if (selected.isEmpty()) { results.addView(text("Ничего не найдено. Попробуйте другое слово.", 18)); return }
        if (course == null && query.isBlank()) courseCards(selected)
        else selected.sortedWith(compareBy({ it.course }, { it.order })).forEach { lessonCard(it, null) }
    }
    private fun renderClasses() {
        if (uid() == AnswerStore.GUEST) {
            results.addView(text("Войдите, чтобы увидеть свои классы и их программы.", 20, true))
            results.addView(action("Войти") { startActivity(Intent(this, LoginActivity::class.java)) })
            return
        }
        classError?.let {
            results.addView(text(it, 18)); results.addView(action("Повторить") { refresh() }); return
        }
        val available = classes
        if (available == null) { results.addView(text("Загрузка доступных классов…", 18)); return }
        if (available.isEmpty()) {
            results.addView(text("У вас пока нет доступных классов. Обратитесь к ведущему.", 18))
            results.addView(action("Обновить", false) { refresh() }); return
        }
        val selected = available.find { it.id == selectedClassId }
        if (selected == null) {
            if (selectedClassId != null) { selectedClassId = null; course = null }
            results.addView(text("Ваши классы", 26, true))
            results.addView(text("Ответы из этого раздела сохраняются в выбранный класс на сайте.", 15, muted = true))
            available.forEach { item -> card(results) {
                addView(text(item.name, 22, true))
                addView(text("Ведущий: ${item.leader}", 15))
                addView(text(item.schedule(), 14, muted = true))
                addView(text("Уроков в программе: ${item.lessonSlugs.size}" + if (item.archived) " · Архив" else "", 14))
                addView(action("Открыть класс") { selectedClassId = item.id; course = null; studentsMode = false; students = null; selectedStudent = null; renderResults() })
            } }
            results.addView(action("Обновить список", false) { refresh() })
            return
        }
        results.addView(action("Все классы", false) { selectedClassId = null; course = null; studentsMode = false; selectedStudent = null; students = null; renderResults() })
        results.addView(text(selected.name, 26, true))
        results.addView(text("Ведущий: ${selected.leader}\n${selected.schedule()}", 15, muted = true))
        if (selected.place.isNotBlank()) results.addView(text(selected.place, 15))
        if (selected.archived) results.addView(text("Архивный класс · только чтение", 16, true))
        else if (uid() !in selected.members) results.addView(text("Вы управляете классом. Для собственных ответов нужно членство в нём.", 15))
        if (selected.canManage) {
            results.addView(action(if (studentsMode) "Программа класса" else "Ученики и их ответы", false) {
                studentsMode = !studentsMode; selectedStudent = null; course = null; renderResults()
            })
            if (studentsMode) { renderStudents(selected); return }
        }
        val program = selected.lessonSlugs.mapNotNull { slug -> lessons.find { it.slug == slug } }
        if (program.size != selected.lessonSlugs.size) results.addView(text("Часть уроков отсутствует в материалах телефона. Обновите материалы в настройках.", 15))
        if (selected.lessonSlugs.isEmpty()) { results.addView(text("Ведущий ещё не добавил уроки в программу.", 18)); return }
        if (course == null) courseCards(program)
        else {
            results.addView(action("Курсы класса", false) { course = null; renderResults() })
            results.addView(text(Courses.name(course!!), 22, true))
            program.filter { it.course == course }.forEach { lessonCard(it, selected) }
        }
    }
    private fun renderStudents(group: StudyClass) {
        results.addView(text("Ученики · ${group.members.size}", 22, true))
        rosterError?.let {
            results.addView(text(it, 16))
            results.addView(action("Повторить") { rosterError = null; students = null; renderResults() })
            return
        }
        val roster = students
        if (roster == null) {
            results.addView(text("Загрузка учеников и ответов класса…", 16))
            if (!rosterLoading) {
                rosterLoading = true
                val request = generation
                val owner = uid()
                lifecycleScope.launch {
                    try {
                        check(SyncEngine.connected(this@MainActivity, prefs.unmetered))
                        val loaded = withTimeout(30_000) { ClassRepository().roster(owner, group.id) }
                        if (owner != uid() || generation != request || selectedClassId != group.id) return@launch
                        students = loaded
                    } catch (e: CancellationException) {
                        if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                        if (owner == uid() && generation == request && selectedClassId == group.id)
                            rosterError = "Сервер не ответил. Повторите загрузку."
                    } catch (e: Exception) {
                        if (owner == uid() && generation == request && selectedClassId == group.id)
                            rosterError = "Не удалось загрузить учеников. Проверьте сеть и права ведущего."
                    } finally {
                        if (generation == request && selectedClassId == group.id) {
                            rosterLoading = false
                            if (classTab && studentsMode) renderResults()
                        }
                    }
                }
            }
            return
        }
        if (roster.isEmpty()) { results.addView(text("В классе пока нет учеников.", 18)); return }
        val student = selectedStudent
        if (student == null) {
            roster.forEach { item -> card(results) {
                addView(text(item.name, 20, true))
                addView(text("Уроков с ответами: ${item.answeredSlugs.size} из ${group.lessonSlugs.size}", 14))
                addView(action("Курсы и ответы", false) { selectedStudent = item; course = null; renderResults() })
            } }
            results.addView(action("Обновить ответы", false) { students = null; renderResults() })
            return
        }
        results.addView(action("Все ученики", false) { selectedStudent = null; course = null; renderResults() })
        results.addView(text(student.name, 24, true))
        val program = group.lessonSlugs.mapNotNull { slug -> lessons.find { it.slug == slug } }
        if (course == null) {
            program.groupBy { it.course }.forEach { (id, items) -> card(results) {
                addView(text(Courses.name(id), 21, true))
                addView(text("С ответами: ${items.count { it.slug in student.answeredSlugs }} из ${items.size}", 14))
                addView(action("Открыть курс", false) { course = id; renderResults() })
            } }
        } else {
            results.addView(action("Курсы ученика", false) { course = null; renderResults() })
            program.filter { it.course == course }.forEach { lesson -> card(results) {
                addView(text(lesson.title, 20, true))
                addView(text(if (lesson.slug in student.answeredSlugs) "Есть ответы" else "Ответов пока нет", 14))
                addView(action("Посмотреть ответы", false) {
                    startActivity(Intent(this@MainActivity, LessonActivity::class.java)
                        .putExtra("slug", lesson.slug).putExtra("classId", group.id)
                        .putExtra("studentUid", student.uid).putExtra("studentName", student.name))
                })
            } }
        }
    }
    private fun courseCards(selected: List<Lesson>) {
        selected.groupBy { it.course }.forEach { (id, items) -> card(results) {
            addView(text(Courses.name(id), 21, true))
            addView(text("Уроков: ${items.size}", 14, muted = true))
            addView(action("Открыть курс", false) { course = id; renderResults() })
        } }
    }
    private fun lessonCard(lesson: Lesson, studyClass: StudyClass?) {
        card(results) {
            addView(text(lesson.title, 20, true))
            addView(text(lesson.reference, 14, muted = true))
            val record = records.find { it.slug == lesson.slug && it.classId == studyClass?.id }
            val status = when {
                record?.remoteConflict != null -> "Две версии ответа · нужен ваш выбор"
                record?.dirty == true -> "Есть неотправленные изменения"
                record?.values?.isNotEmpty() == true -> "Сохранено на телефоне · Ответов: ${record.values.size}"
                studyClass != null -> "Ответы этого класса · проверка при открытии"
                else -> "Личное изучение · можно читать офлайн"
            }
            addView(text(status, 13, muted = true))
            addView(action(if (studyClass != null && !studyClass.canWrite(uid(), lesson.slug)) "Читать" else "Читать и отвечать", false) { open(lesson, studyClass) })
        }
    }
    private fun open(lesson: Lesson, studyClass: StudyClass? = null) {
        startActivity(Intent(this, LessonActivity::class.java).putExtra("slug", lesson.slug).putExtra("classId", studyClass?.id))
    }
}
