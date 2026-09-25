package dev.androidteacher.core.domain

import dev.androidteacher.core.srs.Grade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LessonSessionTest {

    private val start = LessonSession.start(LessonKind.Review, listOf("a", "b", "c"))

    @Test
    fun wrongAnswerMovesQuestionToTheEnd() {
        val session = start.answer(correct = false)
        assertEquals(listOf("b", "c", "a"), session.queue)
        assertEquals(1, session.mistakesFor("a"))
        assertEquals(0f, session.progress)
    }

    @Test
    fun finishesOnlyWhenEveryQuestionIsAnsweredCorrectly() {
        var session = start.answer(false).answer(true).answer(true)
        assertFalse(session.isFinished)
        session = session.answer(true)
        assertTrue(session.isFinished)
        assertEquals(listOf("b", "c", "a"), session.completed)
    }

    @Test
    fun anyMistakeGradesQuestionAsAgain() {
        val finished = start.play(wrongOnce = setOf("b"))
        assertEquals(mapOf("a" to Grade.Good, "b" to Grade.Again, "c" to Grade.Good), finished.grades())
        assertEquals(66, finished.accuracyPercent)
        assertFalse(finished.isPerfect)
    }

    @Test
    fun cannotAnswerFinishedLesson() {
        assertFailsWith<IllegalStateException> { start.play().answer(true) }
    }

    @Test
    fun rejectsEmptyOrDuplicateQuestions() {
        assertFailsWith<IllegalArgumentException> { LessonSession.start(LessonKind.Review, emptyList()) }
        assertFailsWith<IllegalArgumentException> { LessonSession.start(LessonKind.Review, listOf("a", "a")) }
    }
}
