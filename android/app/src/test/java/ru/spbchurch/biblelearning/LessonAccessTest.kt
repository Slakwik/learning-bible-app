package ru.spbchurch.biblelearning

import org.junit.Assert.*
import org.junit.Test

class LessonAccessTest {
    private val blocks = listOf(LessonBlock.Reading("Introduction"),
        LessonBlock.Question("q1", "Private question"), LessonBlock.Reading("After question"))
    private val group = StudyClass("class", "Class", "Leader", listOf("lesson"), setOf("member"),
        false, 1, "10:00", 60, "Europe/Moscow", "")

    @Test fun guestCannotReadLessonsEvenWithClassFlag() {
        assertTrue(LessonAccess.blocks(false, false, blocks).isEmpty())
        assertTrue(LessonAccess.blocks(false, true, blocks).isEmpty())
    }
    @Test fun previewStopsBeforeFirstQuestionAndHidesLaterReading() {
        assertEquals(listOf(blocks.first()), LessonAccess.blocks(true, false, blocks))
        assertTrue(LessonAccess.blocks(true, false, blocks.drop(1)).isEmpty())
    }
    @Test fun verifiedClassIncludesWholeLesson() {
        assertEquals(blocks, LessonAccess.blocks(true, true, blocks))
    }
    @Test fun writingRequiresActiveMembershipAndProgram() {
        assertFalse(LessonAccess.canWrite("member", "lesson", null, false))
        assertTrue(LessonAccess.canWrite("member", "lesson", group, false))
        assertFalse(LessonAccess.canWrite("member", "lesson", group, true))
        assertFalse(LessonAccess.canWrite("outsider", "lesson", group, false))
        assertFalse(LessonAccess.canWrite("member", "other", group, false))
        assertFalse(LessonAccess.canWrite("member", "lesson", group.copy(archived = true), false))
        assertFalse(LessonAccess.canWrite(AnswerStore.GUEST, "lesson", group.copy(members = setOf(AnswerStore.GUEST)), false))
    }
}
