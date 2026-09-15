package ru.spbchurch.biblelearning

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BibleActivity : BaseActivity() {
    private var bookIndex = 42
    private var passage = "3:16"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookIndex = savedInstanceState?.getInt("book", 42) ?: 42
        passage = savedInstanceState?.getString("passage") ?: "3:16"
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("book", bookIndex); outState.putString("passage", passage)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        val content = screen("Библия", false)
        notice(content, "Библия онлайн", "Синодальный перевод на Bible.com · нужен интернет")
        content.addView(text("Выберите книгу и отрывок. Текст откроется поверх текущего экрана; ссылки в уроках используют то же окно.", 16))
        val book = BibleReferences.books[bookIndex.coerceIn(BibleReferences.books.indices)]
        val location = input(content, "Глава или глава:стихи")
        location.setText(passage)
        location.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { passage = s.toString() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        content.addView(action("Книга: ${book.name}", false) {
            MaterialAlertDialogBuilder(this).setTitle("Книга Библии")
                .setSingleChoiceItems(BibleReferences.books.map { it.name }.toTypedArray(), bookIndex) { dialog, index ->
                    bookIndex = index; dialog.dismiss(); render()
                }.setNegativeButton("Отмена", null).show()
        })
        content.addView(text("Например: 3, 3:16 или 3:16–18. Текст можно прокручивать и выделять в окне чтения.", 14, muted = true))
        content.addView(action("Открыть отрывок") {
            val query = book.name + " " + passage.trim()
            val reference = BibleReferences.find(query).singleOrNull()?.takeIf { it.start == 0 && it.end == query.length }
            if (reference == null) location.error = "Укажите главу или отрывок, например 3:16–18"
            else openBible(reference)
        })
        content.addView(action("Все переводы на Bible.com", false) { openBibleUrl("https://www.bible.com/ru/versions") })
        navigation(4) { destination ->
            if (destination == 3) launchSettings(true)
            else startActivity(Intent(this, MainActivity::class.java).putExtra("destination", destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }
}

fun BaseActivity.openBibleUrl(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) { message("Не найден браузер для открытия Bible.com.") }
}
fun BaseActivity.openBible(reference: BibleReference) {
    BiblePassageDialog.show(this, reference)
}

fun BaseActivity.linkBibleReferences(view: TextView, defaultBook: String? = null) {
    val linked = SpannableString(view.text)
    BibleReferences.find(linked.toString(), defaultBook).forEach { ref ->
        if (linked.getSpans(ref.start, ref.end, URLSpan::class.java).isEmpty()) {
            linked.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { openBible(ref) }
            }, ref.start, ref.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    view.text = linked
    view.setLinkTextColor(palette().accent)
    view.movementMethod = LinkMovementMethod.getInstance()
}
