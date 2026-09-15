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
        classTab = savedInstanceState?.getBoolean("class_tab") ?: (intent.getIntExtra("destination", 1) == 2)
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        classTab = intent.getIntExtra("destination", 1) == 2
        course = null; selectedClassId = null; studentsMode = false; selectedStudent = null
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
        val content = screen(if (classTab) "Класс" else "Курсы", false)
        if (!classTab) {
            notice(content, "Знакомство с курсами", "Вопросы и ответы доступны в разделе «Класс».")
            if (uid() == AnswerStore.GUEST) {
                content.addView(text("Без входа доступны названия и описания курсов.", 14, muted = true))
                content.addView(action("Войти") { startActivity(Intent(this, LoginActivity::class.java)) })
            } else content.addView(text("В каталоге можно прочитать вводный текст до вопросов.", 14, muted = true))
            val search = input(content, if (uid() == AnswerStore.GUEST) "Найти курс" else "Найти курс, урок или отрывок")
            search.setText(query)
            search.doAfterTextChanged { query = it.toString(); renderResults() }
        }
        results = column()
        content.addView(results)
        navigation(if (classTab) 2 else 1) { destination ->
            if (destination == 3) launchSettings(rootDestination = true)
            else {
                classTab = destination == 2; course = null; selectedClassId = null
                studentsMode = false; selectedStudent = null; students = null
                build()
            }
        }
        renderResults()
    }
    private fun renderResults() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        if (classTab) { renderClasses(); return }
        if (uid() == AnswerStore.GUEST) {
            course = null
            val selected = lessons.filter {
                (Courses.name(it.course) + " " + CourseDescriptions.get(it.course)).contains(query.trim(), true)
            }
            selected.groupBy { it.course }.forEach { (id, items) ->
                card(results) {
                    addView(text(Courses.name(id), 22, true))
                    addView(text(CourseDescriptions.get(id), 16))
                    addView(text("Уроков в программе: ${items.size}", 14, muted = true))
                }
            }
            if (selected.isEmpty()) results.addView(text("Ничего не найдено. Попробуйте другое слово.", 18))
            return
        }
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
            notice(results, "Учитесь вместе", "Войдите в аккаунт сайта, чтобы открыть свои классы, программу и ответы группы.")
            results.addView(action("Войти") { startActivity(Intent(this, LoginActivity::class.java)) })
            return
        }
        classError?.let {
            results.addView(text(it, 18)); results.addView(action("Повторить") { refresh() }); return
        }
        val available = classes
        if (available == null) { results.addView(text("Загрузка доступных классов…", 18)); return }
        if (available.isEmpty()) {
            notice(results, "Вы пока не в классе", "Попросите ведущего добавить вас в группу. После этого её программа появится здесь.")
            results.addView(action("Обновить", false) { refresh() }); return
        }
        val selected = available.find { it.id == selectedClassId }
        if (selected == null) {
            if (selectedClassId != null) { selectedClassId = null; course = null }
            results.addView(text("Ваши классы", 26, true))
            results.addView(text("Ответы из этого раздела сохраняются в выбранный класс на сайте.", 15, muted = true))
            available.forEach { item ->
                listItem(results, item.name, "${item.leader}\n${item.schedule()}", R.drawable.ic_groups,
                    if (item.archived) "Архив · ${item.lessonSlugs.size} уроков" else "${item.lessonSlugs.size} уроков · " + if (item.canManage) "Ведущий" else "Участник") {
                    selectedClassId = item.id; course = null; studentsMode = false
                    students = null; selectedStudent = null; renderResults()
                }
            }
            results.addView(action("Обновить список", false) { refresh() })
            return
        }
        results.addView(action("Все классы", false) { selectedClassId = null; course = null; studentsMode = false; selectedStudent = null; students = null; renderResults() })
        notice(results, selected.name, "Ведущий: ${selected.leader}\n${selected.schedule()}")
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
            roster.forEach { item ->
                listItem(results, item.name, "Уроков с ответами: ${item.answeredSlugs.size} из ${group.lessonSlugs.size}", R.drawable.ic_person, "Курсы и ответы") {
                    selectedStudent = item; course = null; renderResults()
                }
            }
            results.addView(action("Обновить ответы", false) { students = null; renderResults() })
            return
        }
        results.addView(action("Все ученики", false) { selectedStudent = null; course = null; renderResults() })
        results.addView(text(student.name, 24, true))
        val program = group.lessonSlugs.mapNotNull { slug -> lessons.find { it.slug == slug } }
        if (course == null) {
            program.groupBy { it.course }.forEach { (id, items) ->
                listItem(results, Courses.name(id), "С ответами: ${items.count { it.slug in student.answeredSlugs }} из ${items.size}") {
                    course = id; renderResults()
                }
            }
        } else {
            results.addView(action("Курсы ученика", false) { course = null; renderResults() })
            program.filter { it.course == course }.forEach { lesson ->
                listItem(results, lesson.title, lesson.reference, badge = if (lesson.slug in student.answeredSlugs) "Есть ответы" else "Ответов пока нет") {
                    startActivity(Intent(this@MainActivity, LessonActivity::class.java)
                        .putExtra("slug", lesson.slug).putExtra("classId", group.id)
                        .putExtra("studentUid", student.uid).putExtra("studentName", student.name))
                }
            }
        }
    }
    private fun courseCards(selected: List<Lesson>) {
        results.addLabel("Курсы")
        selected.groupBy { it.course }.forEach { (id, items) ->
            listItem(results, Courses.name(id), CourseDescriptions.get(id) + "\nУроков в программе: ${items.size}") {
                course = id; renderResults()
            }
        }
    }
    private fun lessonCard(lesson: Lesson, studyClass: StudyClass?) {
        val record = if (studyClass == null) null else records.find { it.slug == lesson.slug && it.classId == studyClass.id }
        val status = when {
            record?.remoteConflict != null -> "Нужно сравнить две версии"
            record?.dirty == true -> "Ожидает отправки"
            record?.values?.isNotEmpty() == true -> "С ответами · ${record.values.size}"
            studyClass != null && !studyClass.canWrite(uid(), lesson.slug) -> "Только чтение"
            studyClass != null -> "Ответы класса"
            else -> "Ознакомление · без вопросов"
        }
        listItem(results, lesson.title, lesson.reference, badge = status) { open(lesson, studyClass) }
    }
    private fun open(lesson: Lesson, studyClass: StudyClass? = null) {
        if (uid() == AnswerStore.GUEST) return
        startActivity(Intent(this, LessonActivity::class.java).putExtra("slug", lesson.slug).putExtra("classId", studyClass?.id))
    }
}
