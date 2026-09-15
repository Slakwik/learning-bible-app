package ru.spbchurch.biblelearning

import android.app.Application
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29, 35])
class LoginActivityTest {
    @Test fun loginScreenOpensWithEmailAndPasswordFields() {
        // Opening the screen must not require a Firebase session or network request.
        val controller = Robolectric.buildActivity(LoginActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val fields = descendants(activity.window.decorView).filterIsInstance<TextInputEditText>().toList()
            assertEquals(2, fields.size)
            assertEquals(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                fields[0].inputType and InputType.TYPE_MASK_VARIATION)
            assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                fields[1].inputType and InputType.TYPE_MASK_VARIATION)
            assertFalse(fields[1].isSaveEnabled)
            fields[0].setText("reader@example.test")
            fields[1].setText("local-test-input")
            assertEquals("reader@example.test", fields[0].text.toString())
            assertEquals("local-test-input", fields[1].text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
