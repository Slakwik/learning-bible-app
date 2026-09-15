package ru.spbchurch.biblelearning

import org.junit.Test
import org.junit.Assert.*

class LessonParserTest {
    @Test fun preservesReadingQuestionOrderAndSuffixIds() {
        val body = """Before
<div class="question-block">
<label for="q3a">Question a?</label>
<textarea id="q3a" data-question="q3a"></textarea>
</div>
After"""
        val blocks = LessonParser.blocks(body)
        assertEquals(3, blocks.size)
        assertEquals(LessonBlock.Reading("Before"), blocks[0])
        assertEquals(LessonBlock.Question("q3a", "Question a?"), blocks[1])
        assertEquals(LessonBlock.Reading("After"), blocks[2])
    }
    @Test fun parsesRealFrontMatterFormat() {
        val lesson = LessonParser.parse("""---
title: "Урок 1: Введение"
slug: "james-1"
course: "james"
reference: "Иакова 1:1"
order: 1
---
## Прочитайте текст
Введение.
""")
        assertEquals("Урок 1: Введение", lesson.title)
        assertEquals("james-1", lesson.slug)
        assertTrue(lesson.body.contains("##"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateQuestions() {
        val question = """<div class="question-block"><label for="q1">One?</label><textarea></textarea></div>"""
        LessonParser.validate(Lesson("james-1", "title", "ref", "james", 1, question + question))
    }
}
