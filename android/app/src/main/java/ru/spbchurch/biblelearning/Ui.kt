package ru.spbchurch.biblelearning

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

fun Context.dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
data class Palette(val background: Int, val surface: Int, val ink: Int, val muted: Int, val accent: Int, val onAccent: Int)
fun Context.palette(): Palette {
    val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    fun c(hex: String) = Color.parseColor(hex)
    return if (dark) Palette(c("#151A18"), c("#202823"), c("#F0EEE6"), c("#B6C2B9"), c("#A7D4B5"), c("#153D29"))
    else Palette(c(if (Preferences(this).theme == "paper") "#F3EAD8" else "#F8F7F2"),
        c(if (Preferences(this).theme == "paper") "#FFF6E5" else "#FFFFFF"),
        c("#202D25"), c("#58685E"), c("#285B42"), c("#FFFFFF"))
}

open class BaseActivity : AppCompatActivity() {
    protected val prefs by lazy { Preferences(this) }
    private var appearance = ""
    private var restoredScroll: Int? = null
    protected lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = when (prefs.theme) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light", "paper" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        super.onCreate(savedInstanceState)
        restoredScroll = savedInstanceState?.getInt("screen_scroll")
        appearance = prefs.appearanceKey()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::root.isInitialized) outState.putInt("screen_scroll", root.findViewById<ScrollView>(0x5001)?.scrollY ?: 0)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (appearance != prefs.appearanceKey()) recreate()
    }
    protected fun screen(title: String, back: Boolean = true): LinearLayout {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette().background)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, root).apply {
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(12), dp(4))
        }
        if (back) header.addView(action("Назад", false) { onBackPressedDispatcher.onBackPressed() })
        header.addView(text(title, 20, true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        val scroll = ScrollView(this).apply { id = 0x5001; isFillViewport = true }
        val content = column().apply { setPadding(dp(20), dp(8), dp(20), dp(28)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        restoredScroll?.let { y -> scroll.post { scroll.scrollTo(0, y) }; restoredScroll = null }
        return content
    }
    fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    fun confirm(title: String, detail: String, action: String, yes: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(detail)
            .setNegativeButton("Отмена", null).setPositiveButton(action) { _, _ -> yes() }.show()
    }
    fun launchSettings() = startActivity(Intent(this, SettingsActivity::class.java))
}

fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.text(value: CharSequence, size: Int = 16, bold: Boolean = false, muted: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(if (muted) palette().muted else palette().ink)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.16f)
        setPadding(0, dp(5), 0, dp(5))
    }
fun Context.action(label: String, primary: Boolean = true, click: () -> Unit) =
    MaterialButton(this, null, if (primary) com.google.android.material.R.attr.materialButtonStyle
        else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        minHeight = dp(48)
        cornerRadius = dp(16)
        if (primary) {
            backgroundTintList = ColorStateList.valueOf(palette().accent)
            setTextColor(palette().onAccent)
        } else {
            setTextColor(palette().accent)
            strokeColor = ColorStateList.valueOf(palette().accent)
        }
        setOnClickListener { click() }
    }
fun Context.card(parent: LinearLayout, build: LinearLayout.() -> Unit): LinearLayout {
    val inner = column().apply { setPadding(dp(18), dp(14), dp(18), dp(16)) }
    val card = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = Color.argb(35, 110, 135, 119)
        setCardBackgroundColor(palette().surface)
        addView(inner)
    }
    parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    inner.build()
    return inner
}
fun LinearLayout.addLabel(value: String) {
    addView(context.text(value, 13, bold = true, muted = true).apply {
        setPadding(0, context.dp(18), 0, context.dp(10))
    })
}
fun Context.input(parent: LinearLayout, label: String, multiline: Boolean = false): TextInputEditText {
    val box = TextInputLayout(this).apply {
        hint = label
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
    }
    val edit = TextInputEditText(box.context).apply {
        setTextColor(palette().ink)
        textSize = 16f
        minHeight = dp(56)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            if (multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        if (multiline) { minLines = 3; gravity = Gravity.TOP }
    }
    box.addView(edit, ViewGroup.LayoutParams(-1, -2))
    parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
    return edit
}
