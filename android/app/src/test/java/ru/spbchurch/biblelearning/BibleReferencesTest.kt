package ru.spbchurch.biblelearning

import org.junit.Assert.*
import org.junit.Test

class BibleReferencesTest {
    @Test fun mapsRussianBooksAndSynodalNumbering() {
        val refs = BibleReferences.find("Ин. 3:16; 1 Ин. 2:1; 3 Цар. 7:1; Пс. 118:105")
        assertEquals(listOf("JHN", "1JN", "1KI", "PSA"), refs.map { it.book })
        assertEquals("https://www.bible.com/bible/167/PSA.118.105", refs.last().url)
    }
    @Test fun supportsUnicodeDashesSpacesAndContext() {
        val text = "Марк 7:1–8:26; прочитайте (1:3–5)"
        val refs = BibleReferences.find(text, "MRK")
        assertEquals(listOf("7.1-8.26", "1.3-5"), refs.map { it.location })
        assertEquals("Марк 7:1–8:26", text.substring(refs[0].start, refs[0].end))
        assertEquals(listOf("7.1", "8.26"), refs[0].endpoints)
        assertEquals("https://www.bible.com/bible/167/MRK.7.1", refs[0].url)
        assertEquals(listOf("1.3-5"), refs[1].endpoints)
    }
    @Test fun chaptersAndRangesAreDistinctFromVerseRanges() {
        assertEquals(listOf("1", "16"), BibleReferences.find("Марк 1–16").single().endpoints)
        assertEquals("JHN.3.16-18", BibleReferences.find("Иоанна 3:16-18").single().url.substringAfter("167/"))
        assertTrue(BibleReferences.find("(3:16)").isEmpty())
        assertEquals("JHN", BibleReferences.defaultBook("Иоанна 3:16"))
        assertNull(BibleReferences.defaultBook("Иоанна 3:16 и Марка 1:1"))
    }
    @Test fun rejectsInvalidNumbersAndWordSuffixMatches() {
        assertTrue(BibleReferences.find("Ин. 0:0").isEmpty())
        assertTrue(BibleReferences.find("Ин. 999:999").isEmpty())
        assertTrue(BibleReferences.find("магазИн 3:16").isEmpty())
        assertEquals(66, BibleReferences.books.size)
        assertEquals(66, BibleReferences.books.map { it.code }.distinct().size)
    }
}
