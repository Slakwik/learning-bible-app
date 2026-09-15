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
        val loader = YouVersionPassageLoader("test-key", 167) { url, key ->
            assertEquals("test-key", key)
            requests += url
            if ("/passages/" in url) """{"reference":"Иоанна 3:16","content":"Тестовый текст"}"""
            else """{"copyright":"Тестовый перевод"}"""
        }
        assertEquals(BiblePassage("Иоанна 3:16", "Тестовый текст", "Тестовый перевод"), loader.load(reference))
        assertEquals(2, requests.size)
        assertTrue(requests.first().contains("JHN.3.16?format=text"))
        assertFalse(requests.any { "test-key" in it })
    }
    @Test fun noKeyDoesNotMakeNetworkRequest() = runBlocking {
        try {
            YouVersionPassageLoader("", 167) { _, _ -> error("Network must not be called") }.load(reference)
            fail("Expected missing configuration")
        } catch (_: BiblePassageException) {}
    }
    @Test fun missingAttributionDoesNotDisplayIncompleteContent() = runBlocking {
        try {
            YouVersionPassageLoader("test-key", 167) { _, _ -> """{"content":"Текст"}""" }.load(reference)
            fail("Expected missing attribution")
        } catch (_: BiblePassageException) {}
    }
    @Test fun oversizedResponsesAreRejected() {
        assertArrayEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).inputStream().readBytesBounded(2))
        try { byteArrayOf(1, 2, 3).inputStream().readBytesBounded(2); fail("Expected size limit") }
        catch (_: BiblePassageException) {}
    }
}
