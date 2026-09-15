package ru.spbchurch.biblelearning

import android.app.Application
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29, 35])
class BibleActivityTest {
    @Test fun bibleScreenHasFourDestinationsWithoutLoginOrNetwork() {
        val controller = Robolectric.buildActivity(BibleActivity::class.java).setup().visible()
        try {
            val bar = controller.get().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            assertEquals(4, bar.menu.size())
            assertEquals(4, bar.selectedItemId)
            assertEquals("Библия", bar.menu.findItem(4).title)
            val host = bar.parent as LinearLayout
            smoothContentChange(host)
            assertEquals(View.VISIBLE, bar.visibility)
        } finally { controller.pause().stop().destroy() }
    }
}
