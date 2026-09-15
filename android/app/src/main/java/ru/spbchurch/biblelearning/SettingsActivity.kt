package ru.spbchurch.biblelearning

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class SettingsActivity : BaseActivity() {
    private var exportingOwner: String? = null
    private var exportLegacy = false
    private var busy = false
    private var lastMessage = ""
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val owner = exportingOwner
        if (uri != null && owner != null) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val data = if (exportLegacy) JSONObject(
                            getSharedPreferences("offline_answers", MODE_PRIVATE).all).toString(2)
                        else AnswerStore.get(this@SettingsActivity).export(owner)
                        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(data) }
                            ?: error("No writable file")
                    }
                }
                message(if (result.isSuccess) "Ответы экспортированы в выбранный файл."
                    else "Не удалось записать файл. Ответы на телефоне не изменены.")
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportingOwner = savedInstanceState?.getString("export_owner")
        exportLegacy = savedInstanceState?.getBoolean("export_legacy") ?: false
        lastMessage = savedInstanceState?.getString("message").orEmpty()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("export_owner", exportingOwner)
        out.putBoolean("export_legacy", exportLegacy)
        out.putString("message", lastMessage)
        super.onSaveInstanceState(out)
    }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val content = screen("Настройки", back = !intent.getBooleanExtra("rootDestination", false))
        val user = FirebaseAuth.getInstance().currentUser
        val owner = user?.uid ?: AnswerStore.GUEST
        if (lastMessage.isNotBlank()) content.addView(text(lastMessage, 15, true))
        content.addLabel("АККАУНТ")
        card(content) {
            addView(text(user?.email ?: "Ваш аккаунт", 20, true))
            addView(text(if (user == null) "Читайте и делайте заметки без входа. Гостевые черновики не отправляются на сайт."
                else "Личные ответы синхронизируются с аккаунтом learning.spbchurch.ru.", 14, muted = true))
            if (user == null) addView(action("Войти") { startActivity(Intent(this@SettingsActivity, LoginActivity::class.java)) })
            else {
                addView(action("Сбросить пароль", false) {
                    val email = user.email ?: return@action
                    confirm("Отправить письмо?", "Ссылка для смены пароля будет отправлена на $email.", "Отправить") {
                        FirebaseAuth.getInstance().setLanguageCode("ru")
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                            .addOnSuccessListener { message("Если адрес зарегистрирован, письмо для сброса пароля будет отправлено.") }
                            .addOnFailureListener { message("Письмо не отправлено. Проверьте соединение и повторите позже.") }
                    }
                })
                addView(action("Выйти из аккаунта", false) {
                    lifecycleScope.launch {
                        val pending = withContext(Dispatchers.IO) { AnswerStore.get(this@SettingsActivity).allRecords(owner).count { it.dirty } }
                        confirm("Выйти?", "Неотправленных уроков: $pending. Ответы останутся на телефоне отдельно для этого аккаунта. Для продолжения потребуется повторный вход с интернетом.", "Выйти") {
                            FirebaseAuth.getInstance().signOut()
                            SyncScheduler.configure(this@SettingsActivity)
                            render()
                        }
                    }
                })
            }
        }
        content.addLabel("ЧТЕНИЕ И ОФОРМЛЕНИЕ")
        card(content) {
            val themes = listOf("Как в системе", "Светлая", "Тёмная", "Бумага")
            val themeKeys = listOf("system", "light", "dark", "paper")
            addView(action("Оформление: " + themes[themeKeys.indexOf(prefs.theme).coerceAtLeast(0)], false) {
                choose("Тема приложения", themes, themeKeys.indexOf(prefs.theme)) { prefs.theme = themeKeys[it]; recreate() }
            })
            addView(action("Размер текста · " + prefs.fontSize, false) {
                val sizes = listOf(16, 18, 20, 22, 24, 26)
                choose("Размер текста урока", sizes.map { "$it" }, sizes.indexOf(prefs.fontSize)) { prefs.fontSize = sizes[it]; recreate() }
            })
            toggle(this, "Книжный шрифт", "Засечки в тексте уроков; поля ответов остаются простыми.", prefs.serif) {
                prefs.serif = it; recreate()
            }
            toggle(this, "Просторные строки", "Больше расстояния между строками в тексте урока.", prefs.roomy) {
                prefs.roomy = it; recreate()
            }
            toggle(this, "Не гасить экран при чтении", "Работает только в открытом уроке. Расходует больше батареи.", prefs.keepAwake) {
                prefs.keepAwake = it
            }
            addView(text("Пример текста", 12, true, true))
            addView(text("«Слово Твоё — светильник ноге моей и свет стезе моей».\nПсалом 118:105", prefs.fontSize).apply {
                typeface = if (prefs.serif) Typeface.create("serif", Typeface.NORMAL) else Typeface.DEFAULT
                setLineSpacing(0f, if (prefs.roomy) 1.45f else 1.15f)
            })
            addView(action("Вернуть настройки чтения", false) {
                confirm("Сбросить оформление?", "Будут сброшены только тема, размер и вид шрифта, межстрочный интервал и режим экрана. Ответы и настройки сети сохранятся.", "Сбросить") {
                    prefs.resetReading(); recreate()
                }
            })
        }
        content.addLabel("СИНХРОНИЗАЦИЯ")
        card(content) {
            val whenSynced = prefs.lastSync(owner)
            addView(text(if (whenSynced == 0L) "Полной синхронизации ещё не было"
                else "Последняя синхронизация:\n" + DateFormat.getDateTimeInstance().format(Date(whenSynced)), 14, muted = true))
            val counter = text("Проверяем локальные ответы…", 14)
            addView(counter)
            lifecycleScope.launch {
                val records = withContext(Dispatchers.IO) { AnswerStore.get(this@SettingsActivity).allRecords(owner) }
                counter.text = "Уроков с изменениями: " + records.count { it.dirty } +
                    "\nКонфликтов: " + records.count { it.remoteConflict != null }
            }
            toggle(this, "Автоматическая синхронизация", "В фоне после редактирования и периодически. Android может откладывать запуск для экономии батареи.", prefs.autoSync) {
                prefs.autoSync = it
                SyncScheduler.configure(this@SettingsActivity)
                SyncScheduler.afterEdit(this@SettingsActivity)
            }
            toggle(this, "Только безлимитная сеть", "Обычно Wi-Fi. Ограничение действует и на ручную синхронизацию, и на обновление курсов.", prefs.unmetered) {
                prefs.unmetered = it
                SyncScheduler.configure(this@SettingsActivity)
                SyncScheduler.afterEdit(this@SettingsActivity)
            }
            addView(action("Синхронизировать сейчас") {
                if (busy) return@action
                busy = true
                lastMessage = "Синхронизация…"
                lifecycleScope.launch {
                    val report = withContext(Dispatchers.IO) { SyncEngine.sync(applicationContext) }
                    busy = false
                    lastMessage = report.description()
                    render()
                }
            }.apply { isEnabled = user != null && !busy })
            addView(text("Личные ответы и ответы классов синхронизируются отдельно. При конфликте откройте соответствующий урок и выберите нужную версию.", 14, muted = true))
        }
        content.addLabel("КУРСЫ БЕЗ ИНТЕРНЕТА")
        card(content) {
            val repo = LessonRepository(this@SettingsActivity)
            addView(text("Базовый комплект всегда на телефоне", 18, true))
            addView(text("Уроки включены в приложение. Обновления загружаются целиком, проверяются и сохраняются для офлайн-чтения.", 14, muted = true))
            addView(text("Загруженные обновления: " + android.text.format.Formatter.formatFileSize(this@SettingsActivity, repo.downloadedBytes()), 14))
            addView(action("Проверить обновления курсов", false) {
                if (busy) return@action
                if (!SyncEngine.connected(this@SettingsActivity, prefs.unmetered)) {
                    message("Нет подходящей сети. Проверьте интернет и настройку безлимитной сети."); return@action
                }
                busy = true
                message("Проверяем каталог сайта…")
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repo.update() } }
                    busy = false
                    lastMessage = result.fold({ "Каталог обновлён. Уроков: $it" },
                        { "Не удалось обновить каталог. Если мобильный каталог ещё не опубликован на сайте, обновления недоступны. Прежние материалы и ответы сохранены." })
                    render()
                }
            }.apply { isEnabled = !busy })
            addView(action("Удалить загруженные обновления", false) {
                confirm("Вернуться к комплекту из приложения?", "Удалится только скачанный каталог. Базовые уроки, закладки и все ответы останутся.", "Удалить обновления") {
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { repo.clearDownloaded() } }
                        lastMessage = if (result.isSuccess) "Загруженные обновления удалены; их можно скачать снова."
                            else "Не удалось удалить обновления."
                        render()
                    }
                }
            }.apply { isEnabled = !busy && repo.downloadedBytes() > 0 })
        }
        content.addLabel("ДАННЫЕ И ПРИВАТНОСТЬ")
        card(content) {
            addView(text("Ответы хранятся в закрытой папке приложения. Удаление приложения или очистка его данных в Android удалит локальные черновики. Сделайте экспорт перед этим.", 14))
            addView(action("Экспортировать мои ответы", false) { export(owner) })
            if (user != null) addView(action("Экспортировать гостевые черновики", false) { export(AnswerStore.GUEST) })
            if (getSharedPreferences("offline_answers", MODE_PRIVATE).all.isNotEmpty()) {
                addView(text("Найдены черновики старого прототипа без привязки к аккаунту. Они не отправляются автоматически.", 14))
                addView(action("Экспортировать старые черновики", false) { export(owner, legacy = true) })
            }
            addView(text("Экспорт — читаемый JSON, включая обе версии конфликтов. Он не зашифрован. Приложение не запрашивает доступ ко всем файлам телефона.", 13, muted = true))
        }
        content.addLabel("О ПРИЛОЖЕНИИ")
        card(content) {
            addView(text("Изучение Библии", 20, true))
            addView(text("Версия " + BuildConfig.VERSION_NAME + " · Предварительная сборка", 14, muted = true))
            addView(action("Открыть сайт", false) { openUrl("https://learning.spbchurch.ru/") })
            addView(action("Исходный проект сайта", false) { openUrl("https://github.com/Slakwik/bible-learning") })
            addView(action("Код Android-приложения", false) { openUrl("https://github.com/Slakwik/learning-bible-app") })
            addView(text("Материалы: Санкт-Петербургский Центр «Духовное Возрождение». Приложение связано с вашим аккаунтом на сайте.", 13, muted = true))
        }
        if (intent.getBooleanExtra("rootDestination", false)) navigation(3) { destination ->
            startActivity(Intent(this, MainActivity::class.java).putExtra("destination", destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }
    private fun choose(title: String, labels: List<String>, selected: Int, update: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected.coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss(); update(which)
            }.setNegativeButton("Отмена", null).show()
    }
    private fun toggle(parent: LinearLayout, label: String, detail: String, value: Boolean, changed: (Boolean) -> Unit) {
        parent.addView(MaterialSwitch(this).apply {
            text = label; textSize = 16f; minHeight = dp(56)
            setTextColor(palette().ink)
            isChecked = value
            setOnCheckedChangeListener { _, checked -> changed(checked) }
        }, LinearLayout.LayoutParams(-1, -2))
        parent.addView(text(detail, 13, muted = true))
    }
    private fun export(owner: String, legacy: Boolean = false) {
        confirm("Экспортировать ответы?", "В выбранный вами файл будут записаны тексты ответов и сохранённые версии конфликтов. Храните файл в надёжном месте.", "Выбрать файл") {
            exportingOwner = owner; exportLegacy = legacy
            exporter.launch(if (legacy) "bible-legacy-drafts.json" else "bible-answers.json")
        }
    }
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            .onFailure { message("На телефоне не найден браузер.") }
    }
}
