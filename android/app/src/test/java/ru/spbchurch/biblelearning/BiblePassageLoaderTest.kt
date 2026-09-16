package ru.spbchurch.biblelearning

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class BiblePassageLoaderTest {
    private val reference = BibleReference(0, 0, "JHN", "3.16")
    @Test fun fetchesPlainTextAndCurrentAttribution() = runBlocking {
        val requests = mutableListOf<String>()
        val loader = YouVersionPassageLoader("test-key", 143) { url, key ->
            assertEquals("test-key", key)
            requests += url
            if ("/passages/" in url) """{"reference":"Иоанна 3:16","content":"Тестовый текст"}"""
            else """{"copyright":"Тестовый перевод"}"""
        }
        assertEquals(BiblePassage("Иоанна 3:16", "Тестовый текст", "Тестовый перевод"), loader.load(reference))
        assertEquals(2, requests.size)
        assertTrue(requests.first().contains("JHN.3.16?format=html"))
        assertFalse(requests.any { "test-key" in it })
    }
    @Test fun noKeyDoesNotMakeNetworkRequest() = runBlocking {
        try {
            YouVersionPassageLoader("", 143) { _, _ -> error("Network must not be called") }.load(reference)
            fail("Expected missing configuration")
        } catch (_: BiblePassageException) {}
    }
    @Test fun missingAttributionDoesNotDisplayIncompleteContent() = runBlocking {
        try {
            YouVersionPassageLoader("test-key", 143) { _, _ -> """{"content":"Текст"}""" }.load(reference)
            fail("Expected missing attribution")
        } catch (_: BiblePassageException) {}
    }
    @Test fun notesRemainReadableAndSeparate() {
        val result = renderPassageHtml("<div><span class='yv-vlbl'>1</span>Текст<span class='yv-n'><span>1:1</span> Примечание</span>.</div>")
        assertTrue(result.contains("1 Текст [1]"))
        assertTrue(result.contains("Примечания\n[1] 1:1 Примечание"))
    }
    @Test fun longRangesBecomeSingleChapterPages() {
        assertEquals(listOf("7", "8", "9"), readingPages(reference.copy(location = "7.1-9.26")).map { it.location })
        assertEquals(listOf("3.16-18"), readingPages(reference.copy(location = "3.16-18")).map { it.location })
    }
    @Test fun nullAttributionIsNotTheWordNull() = runBlocking {
        try {
            YouVersionPassageLoader("test-key", 143) { _, _ -> """{"content":"Текст","copyright":null,"promotional_content":null}""" }.load(reference)
            fail("Expected missing attribution")
        } catch (_: BiblePassageException) {}
    }
    @Test fun oversizedResponsesAreRejected() {
        assertArrayEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).inputStream().readBytesBounded(2))
        try { byteArrayOf(1, 2, 3).inputStream().readBytesBounded(2); fail("Expected size limit") }
        catch (_: BiblePassageException) {}
    }
}
