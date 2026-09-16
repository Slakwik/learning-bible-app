package ru.spbchurch.biblelearning

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A native reader over the lesson; closing it leaves the editor and its scroll position intact. */
class BiblePassageDialog : DialogFragment() {
    internal var loader: BiblePassageLoader = YouVersionPassageLoader()
    private var request: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = requireActivity()
        val reference = BibleReference(0, 0, requireArguments().getString("book")!!,
            requireArguments().getString("location")!!)
        val pages = readingPages(reference)
        var page = savedInstanceState?.getInt("page", 0)?.coerceIn(pages.indices) ?: 0
        val prefs = Preferences(host)
        val book = BibleReferences.books.first { it.code == reference.book }
        val title = "${book.name} ${reference.location.replace('.', ':')}"
        val body = host.column().apply { setPadding(host.dp(24), host.dp(8), host.dp(24), host.dp(16)) }
        val progress = ProgressBar(host).apply { contentDescription = "Загрузка отрывка" }
        body.addView(progress, LinearLayout.LayoutParams(-1, host.dp(48)))
        val status = host.text("Загрузка отрывка…", 16).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        body.addView(status)
        val passage = host.text("", prefs.fontSize).apply {
            id = R.id.bible_passage_text
            typeface = if (prefs.serif) Typeface.create("serif", Typeface.NORMAL) else Typeface.DEFAULT
            setLineSpacing(0f, if (prefs.roomy) 1.45f else 1.2f)
            setTextIsSelectable(true)
        }
        val attribution = host.text("", 13, muted = true)
        body.addView(passage); body.addView(attribution)
        if (pages.size > 1) body.addView(host.text("Отрывок из нескольких глав: показываем главы целиком, по одной.", 13, muted = true))
        val scroll = ScrollView(host).apply {
            addView(body)
            isFillViewport = false
        }
        val frame = host.column().apply {
            addView(scroll, LinearLayout.LayoutParams(-1, host.dp((resources.configuration.screenHeightDp - if (pages.size > 1) 320 else 220).coerceIn(140, 520))))
        }
        val dialog = MaterialAlertDialogBuilder(host).setTitle(title).setView(frame)
            .setNeutralButton("Biblica") { _, _ -> (host as BaseActivity).openBibleUrl("https://www.biblica.com") }
            .setPositiveButton("Закрыть", null).setNegativeButton("Повторить", null).create()
        val controls = host.column()
        frame.addView(controls)
        fun load() {
            request?.cancel()
            passage.text = ""; attribution.text = ""
            scroll.scrollTo(0, 0)
            progress.visibility = View.VISIBLE
            status.text = "Загрузка отрывка…"
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.visibility = View.GONE
            request = lifecycleScope.launch {
                try {
                    val result = loader.load(pages[page])
                    passage.text = result.text
                    attribution.text = result.attribution
                    status.text = "Новый русский перевод · ${result.reference}"
                } catch (e: CancellationException) { throw e }
                catch (e: BiblePassageException) {
                    status.text = e.message
                    dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.visibility = View.VISIBLE
                } catch (_: Exception) {
                    status.text = "Не удалось загрузить отрывок. Проверьте интернет и повторите."
                    dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.visibility = View.VISIBLE
                } finally { progress.visibility = View.GONE }
            }
        }
        if (pages.size > 1) {
            val previous = host.action("Предыдущая глава", false) {}
            val next = host.action("Следующая глава", false) {}
            fun updateButtons() { previous.isEnabled = page > 0; next.isEnabled = page < pages.lastIndex }
            previous.setOnClickListener { if (page > 0) { page--; updateButtons(); load() } }
            next.setOnClickListener { if (page < pages.lastIndex) { page++; updateButtons(); load() } }
            controls.addView(previous); controls.addView(next)
            updateButtons()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener { load() }
            load()
        }
        return dialog
    }
    override fun onDestroy() { request?.cancel(); super.onDestroy() }
    override fun onDismiss(dialog: android.content.DialogInterface) { request?.cancel(); super.onDismiss(dialog) }

    companion object {
        fun show(host: BaseActivity, reference: BibleReference) {
            if (host.supportFragmentManager.isStateSaved || host.supportFragmentManager.findFragmentByTag("bible_passage") != null) return
            BiblePassageDialog().apply {
                arguments = Bundle().apply { putString("book", reference.book); putString("location", reference.location) }
            }.show(host.supportFragmentManager, "bible_passage")
        }
    }
}
