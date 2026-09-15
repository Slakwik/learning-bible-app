package ru.spbchurch.biblelearning

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = screen("Вход")
        content.addView(text("Рады видеть вас", 26, true))
        content.addView(text("Войдите в аккаунт сайта, чтобы открыть свой класс и продолжить занятия.", 16, muted = true))
        card(content) {
            val email = input(this, "Электронная почта")
            email.id = R.id.login_email
            email.setText(prefs.loginEmail)
            email.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            if (android.os.Build.VERSION.SDK_INT >= 26) email.setAutofillHints(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS)
            val password = input(this, "Пароль")
            password.id = R.id.login_password
            password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (android.os.Build.VERSION.SDK_INT >= 26) password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            password.isSaveEnabled = false
            (password.parent.parent as? TextInputLayout)?.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            val error = text("", 14)
            error.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            addView(error)
            lateinit var button: MaterialButton
            button = action("Войти") {
                val address = email.text.toString().trim()
                if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.error = "Проверьте адрес почты"; return@action }
                if (password.text.isNullOrEmpty()) { password.error = "Введите пароль"; return@action }
                button.isEnabled = false
                error.text = "Выполняется вход…"
                FirebaseAuth.getInstance().signInWithEmailAndPassword(address, password.text.toString())
                    .addOnSuccessListener {
                        prefs.loginEmail = address
                        // Commit while credentials are still in the fields; the OS controls saving.
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            getSystemService(android.view.autofill.AutofillManager::class.java)?.commit()
                        }
                        password.text?.clear()
                        SyncScheduler.configure(applicationContext)
                        SyncScheduler.afterEdit(applicationContext)
                        if (!isDestroyed) { message("Вход выполнен. Синхронизация доступна в настройках."); finish() }
                    }
                    .addOnFailureListener {
                        if (!isDestroyed) {
                            button.isEnabled = true
                            error.text = "Не удалось войти. Проверьте интернет, почту и пароль."
                        }
                    }
            }
            addView(button)
            addView(action("Забыли пароль?", false) {
                val address = email.text.toString().trim()
                if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.error = "Сначала введите адрес почты"; return@action }
                confirm("Отправить письмо?", "Отправить ссылку для сброса пароля на $address?", "Отправить") {
                    FirebaseAuth.getInstance().setLanguageCode("ru")
                    FirebaseAuth.getInstance().sendPasswordResetEmail(address)
                        .addOnSuccessListener { message("Если адрес зарегистрирован, письмо для сброса пароля будет отправлено.") }
                        .addOnFailureListener { message("Не удалось отправить письмо. Попробуйте позже.") }
                }
            })
        }
        content.addView(text("Вход сохраняется между запусками приложения. Приглашение в класс принимается на сайте.", 14, muted = true))
    }
}
