package ru.spbchurch.biblelearning

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29, 35])
class BiblePassageDialogTest {
    @Test fun passageOpensOverScreenWithoutStartingBrowser() {
        val controller = Robolectric.buildActivity(BibleActivity::class.java).setup().visible()
        val activity = controller.get()
        val panel = activity.findViewById<android.view.View>(R.id.bottom_navigation)
        val reader = BiblePassageDialog().apply {
            arguments = Bundle().apply { putString("book", "JHN"); putString("location", "3.16") }
            loader = BiblePassageLoader { BiblePassage("Иоанна 3:16", "Тестовый текст для окна", "Тестовый источник") }
        }
        try {
            reader.showNow(activity.supportFragmentManager, "reader")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Тестовый текст для окна", reader.requireDialog().findViewById<TextView>(R.id.bible_passage_text).text.toString())
            assertNull(shadowOf(activity).nextStartedActivity)
            assertSame(panel, activity.findViewById(R.id.bottom_navigation))
            reader.dismissNow()
            assertFalse(activity.isFinishing)
        } finally { controller.pause().stop().destroy() }
    }
}
