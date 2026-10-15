package com.saysharp.shared.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgrammeTest {
    @Test
    fun programmeHasContiguousUniqueDaysAndScheduledCheckpoints() {
        assertEquals((1..14).toList(), Programme.outline.map { it.day })
        assertEquals(listOf(7, 14), Programme.outline.filter { it.kind == LessonKind.Checkpoint }.map { it.day })
    }

    @Test
    fun firstLessonSupportsPracticeRetryAndTransferWithoutScoring() {
        val lesson = Programme.firstLesson
        assertEquals(Programme.outline.first().day, lesson.day)
        listOf(lesson.instruction, lesson.brief, lesson.prompt, lesson.firstExample,
            lesson.clearerExample, lesson.selfCheck, lesson.scaffold,
            lesson.applicationPrompt, lesson.realLifeTask).forEach { assertTrue(it.isNotBlank()) }
        assertTrue(lesson.prompt != lesson.applicationPrompt)
    }

    @Test fun eachDayHasEitherAPracticeLessonOrADistinctCheckpoint() {
        Programme.outline.forEach { outline ->
            if (outline.kind == LessonKind.Checkpoint) {
                val checkpoint = kotlin.test.assertNotNull(Programme.checkpoint(outline.day))
                assertTrue(checkpoint.freshPrompt != Programme.baselinePrompt)
                assertTrue(checkpoint.realLifeTask.isNotBlank())
            } else {
                val lesson = Programme.lesson(outline.day)
                assertEquals(outline.day, lesson.day)
                listOf(lesson.instruction, lesson.brief, lesson.prompt, lesson.firstExample,
                    lesson.clearerExample, lesson.selfCheck, lesson.scaffold,
                    lesson.applicationPrompt, lesson.realLifeTask).forEach { assertTrue(it.isNotBlank()) }
                assertTrue(lesson.prompt != lesson.applicationPrompt)
            }
        }
    }

    @Test fun eachExpressionIsRecalledTwiceWithoutGoingBeyondTheProgramme() {
        Curriculum.vocabularyByDay.forEach { (day, word) ->
            assertEquals(listOf(day + 2, day + 7), (1..14).filter { word in Programme.recalls(it) })
            assertEquals(word, Programme.lesson(day).vocabulary)
            assertTrue(word.meaning.isNotBlank() && word.example.isNotBlank() && word.prompt.isNotBlank())
            assertTrue(!word.recallPrompt.contains(word.phrase))
        }
    }
}
