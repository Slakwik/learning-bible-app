package ru.spbchurch.biblelearning

import org.junit.Assert.*
import org.junit.Test

class SyncPolicyTest {
    private fun values(value: String) = mapOf("q1" to value)
    @Test fun unchangedLocalTakesWebsite() {
        assertEquals(values("website"), SyncPolicy.merge(values("old"), values("old"), values("website")).answers)
    }
    @Test fun unchangedWebsiteTakesPhone() {
        assertEquals(values("phone"), SyncPolicy.merge(values("old"), values("phone"), values("old")).answers)
    }
    @Test fun sameEditIsNotConflict() {
        assertTrue(SyncPolicy.merge(values("old"), values("same"), values("same")).conflicts.isEmpty())
    }
    @Test fun simultaneousDifferentEditsNeedChoice() {
        assertEquals(setOf("q1"), SyncPolicy.merge(values("old"), values("phone"), values("website")).conflicts)
    }
    @Test fun nonOverlappingChangesMerge() {
        val result = SyncPolicy.merge(mapOf("q1" to "a", "q2" to "b"),
            mapOf("q1" to "phone", "q2" to "b"), mapOf("q1" to "a", "q2" to "website"))
        assertEquals(mapOf("q1" to "phone", "q2" to "website"), result.answers)
        assertTrue(result.conflicts.isEmpty())
    }
    @Test fun clearingLastAnswerIsAChange() {
        assertEquals(emptyMap<String, String>(), SyncPolicy.merge(values("old"), emptyMap(), values("old")).answers)
    }
    @Test fun clearVersusEditIsConflict() {
        assertEquals(setOf("q1"), SyncPolicy.merge(values("old"), emptyMap(), values("website")).conflicts)
    }
    @Test fun unknownBaseCannotClobberExistingAnswer() {
        assertEquals(setOf("q1"), SyncPolicy.merge(emptyMap(), values("phone"), values("website")).conflicts)
    }
    @Test fun emptyAndMissingAreEquivalent() {
        assertTrue(SyncPolicy.merge(emptyMap(), values(""), emptyMap()).conflicts.isEmpty())
    }
    @Test fun partialQuestionIdsArePreserved() {
        val data = mapOf("q3a" to "Ответ а", "q3b" to "Ответ б")
        assertEquals(data, SyncPolicy.merge(emptyMap(), data, emptyMap()).answers)
    }
    @Test fun whitespaceAndUnicodeAreNotDestroyed() {
        val text = "  Мой ответ\nИ ещё строка 🌿  "
        assertEquals(values(text), SyncPolicy.merge(emptyMap(), values(text), emptyMap()).answers)
    }
}
