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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
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
        if (::root.isInitialized) outState.putInt("screen_scroll", root.findViewById<ScrollView>(R.id.screen_scroll)?.scrollY ?: 0)
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
        val header = MaterialToolbar(this).apply {
            this.title = title
            setTitleTextColor(palette().ink)
            setBackgroundColor(palette().background)
            minimumHeight = dp(64)
            if (back) {
                setNavigationIcon(R.drawable.ic_arrow_back)
                setNavigationIconTint(palette().ink)
                navigationContentDescription = "Назад"
                setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            }
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        WindowCompat.getInsetsController(window, root).apply {
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        ViewCompat.requestApplyInsets(root)
        val scroll = ScrollView(this).apply { id = R.id.screen_scroll; isFillViewport = true }
        val horizontal = dp(maxOf(16, (resources.configuration.screenWidthDp - 640) / 2))
        val content = column().apply { setPadding(horizontal, dp(8), horizontal, dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        restoredScroll?.let { y -> scroll.post { scroll.scrollTo(0, y) }; restoredScroll = null }
        return content
    }
    fun message(text: String) {
        if (::root.isInitialized) Snackbar.make(root, text, Snackbar.LENGTH_LONG).setTextMaxLines(5).show()
    }
    protected fun navigation(selected: Int, select: (Int) -> Unit) {
        val bar = BottomNavigationView(this).apply {
            id = R.id.bottom_navigation
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_container))
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            itemIconSize = dp(24)
            minimumHeight = dp(80)
            val colors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette().accent, palette().muted))
            itemIconTintList = colors
            itemTextColor = colors
            itemActiveIndicatorColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_container))
            menu.add(0, 1, 0, "Курсы").setIcon(R.drawable.ic_courses)
            menu.add(0, 2, 1, "Класс").setIcon(R.drawable.ic_groups)
            menu.add(0, 3, 2, "Настройки").setIcon(R.drawable.ic_settings)
            selectedItemId = selected
            setOnItemSelectedListener { item ->
                if (item.itemId != selected) select(item.itemId)
                true
            }
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
    }
    fun confirm(title: String, detail: String, action: String, yes: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(detail)
            .setNegativeButton("Отмена", null).setPositiveButton(action) { _, _ -> yes() }.show()
    }
    fun launchSettings(rootDestination: Boolean = false) = startActivity(Intent(this, SettingsActivity::class.java).putExtra("rootDestination", rootDestination))
}

fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.text(value: CharSequence, size: Int = 16, bold: Boolean = false, muted: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(if (muted) palette().muted else palette().ink)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(0f, 1.2f)
        setPadding(0, dp(4), 0, dp(4))
        if (bold && size >= 20) ViewCompat.setAccessibilityHeading(this, true)
    }
fun Context.action(label: String, primary: Boolean = true, click: () -> Unit) =
    MaterialButton(this, null, if (primary) com.google.android.material.R.attr.materialButtonStyle
        else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        minHeight = dp(48)
        minWidth = dp(48)
        insetTop = 0
        insetBottom = 0
        setPadding(dp(20), dp(12), dp(20), dp(12))
        cornerRadius = dp(24)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(4) }
        if (primary) {
            backgroundTintList = ColorStateList.valueOf(palette().accent)
            setTextColor(palette().onAccent)
        } else {
            setTextColor(palette().accent)
            strokeWidth = 0
        }
        setOnClickListener { click() }
    }
fun Context.card(parent: LinearLayout, build: LinearLayout.() -> Unit): LinearLayout {
    val inner = column().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
    val card = MaterialCardView(this).apply {
        radius = dp(16).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = ColorUtils.blendARGB(palette().background, palette().muted, .25f)
        setCardBackgroundColor(palette().surface)
        addView(inner)
    }
    parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    inner.build()
    return inner
}
fun LinearLayout.addLabel(value: String) {
    addView(context.text(value.lowercase().replaceFirstChar { it.titlecase() }, 14, bold = true, muted = true).apply {
        setPadding(0, context.dp(20), 0, context.dp(8))
    })
}
fun Context.input(parent: LinearLayout, label: String, multiline: Boolean = false): TextInputEditText {
    val box = TextInputLayout(this).apply {
        hint = label
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxStrokeColor = palette().accent
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
    box.addView(edit, LinearLayout.LayoutParams(-1, -2))
    parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
    return edit
}

/** A single accessible target for a course, lesson or student. */
fun Context.listItem(parent: LinearLayout, title: String, detail: String, icon: Int = R.drawable.ic_courses,
    badge: String? = null, click: () -> Unit) {
    val item = MaterialCardView(this).apply {
        radius = dp(16).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(palette().surface)
        isClickable = true
        isFocusable = true
        contentDescription = listOfNotNull(title, detail, badge).joinToString(". ")
        setOnClickListener { click() }
    }
    val row = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(88)
        setPadding(dp(16), dp(12), dp(12), dp(12))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    val image = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette().accent)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    row.addView(image, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
    val labels = column()
    labels.addView(text(title, 17, true))
    labels.addView(text(detail, 14, muted = true))
    badge?.let { labels.addView(text(it, 12, true).apply { setTextColor(palette().accent) }) }
    row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
    row.addView(ImageView(this).apply {
        setImageResource(R.drawable.ic_chevron_right)
        imageTintList = ColorStateList.valueOf(palette().muted)
    }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8) })
    item.addView(row)
    parent.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
}

fun Context.notice(parent: LinearLayout, title: String, detail: String) {
    val inner = card(parent) {
        addView(text(title, 20, true))
        addView(text(detail, 14, muted = true))
    }
    (inner.parent as MaterialCardView).apply {
        setCardBackgroundColor(ColorUtils.blendARGB(palette().background, palette().accent, .08f))
        strokeWidth = 0
    }
}
