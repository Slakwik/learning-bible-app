package ru.spbchurch.biblelearning

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class SynodalPassageLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val loader get() = SynodalPassageLoader(context)
    @Test fun bundledBibleHasEveryBookChapterAndVerse() {
        var chapters = 0
        var verses = 0
        BibleReferences.books.forEach { book ->
            val data = context.assets.open("bible-synodal/${book.code}.json").bufferedReader().use { JSONObject(it.readText()) }
            chapters += data.length()
            data.keys().forEach { chapter ->
                val values = data.getJSONObject(chapter)
                verses += values.length()
                values.keys().forEach { assertTrue(values.getString(it).isNotBlank()) }
            }
        }
        assertEquals(1189, chapters)
        assertEquals(31169, verses)
    }
    @Test fun readsExactVerseRangeFromAssets() = runBlocking {
        val passage = loader.load(BibleReference(0, 0, "JHN", "3.16-18"))
        assertTrue(passage.text.startsWith("16 Ибо так возлюбил Бог мир"))
        assertTrue(passage.text.contains("\n\n18 "))
        assertFalse(passage.text.contains("\n\n19 "))
        assertTrue(passage.attribution.contains("eBible.org"))
    }
    @Test fun preservesSynodalPsalmNumbering() = runBlocking {
        assertTrue(loader.load(BibleReference(0, 0, "PSA", "118.105")).text.contains("светильник ноге моей"))
    }
    @Test fun invalidRangeDoesNotSilentlyTruncate() = runBlocking {
        for (location in listOf("3.16-999", "999", "3.18-16", "3.0")) {
            try { loader.load(BibleReference(0, 0, "JHN", location)); fail(location) }
            catch (_: BiblePassageException) {}
        }
    }
    @Test fun selectionDefaultsOfflineAndPersists() {
        context.getSharedPreferences("settings_v2", 0).edit().clear().commit()
        assertEquals("synodal", Preferences(context).bibleTranslation)
        Preferences(context).bibleTranslation = "nrt"
        assertEquals("nrt", Preferences(context).bibleTranslation)
        Preferences(context).bibleTranslation = "synodal"
        assertEquals("synodal", Preferences(context).bibleTranslation)
    }
}
