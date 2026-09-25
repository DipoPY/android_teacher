package dev.androidteacher.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningEngineTest {

    private val engine = LearningEngine(testContent)

    private fun passTopic(progress: Progress, topicId: String, wrongOnce: Set<String> = emptySet(), day: Int = 0): LessonOutcome =
        engine.completeLesson(progress, engine.startTopic(topicId, progress).play(wrongOnce), DAY0.plus(day, DateTimeUnit.DAY))

    @Test
    fun passingTopicUnlocksNextAndRewards() {
        val (progress, result) = passTopic(Progress(), "kotlin.a")
        assertEquals("kotlin.a", result.passedTopic)
        assertEquals("kotlin.b", result.unlockedTopic)
        assertEquals(0, result.sectionPercentBefore)
        assertEquals(33, result.sectionPercentAfter)
        assertEquals(Xp.TOPIC_PASSED, result.xpGained)
        assertEquals(1, result.streak)
        assertEquals(setOf(Achievement.FirstTopic), result.newAchievements)
        assertEquals(TopicStatus.Available, engine.progression.status("kotlin.b", progress))
        assertEquals(setOf("kotlin.a"), progress.perfectTopics)
        assertEquals(1, progress.activityOn(DAY0))
    }

    @Test
    fun lockedTopicCannotBeStarted() {
        assertFailsWith<IllegalArgumentException> { engine.startTopic("kotlin.b", Progress()) }
    }

    @Test
    fun retakingPassedTopicGivesNoXp() {
        val first = passTopic(Progress(), "kotlin.a").progress
        val (second, result) = passTopic(first, "kotlin.a", day = 1)
        assertEquals(0, result.xpGained)
        assertNull(result.passedTopic)
        assertEquals(first.xp, second.xp)
    }

    @Test
    fun unfinishedLessonIsSavedAndResumed() {
        val session = engine.startTopic("kotlin.a", Progress()).answer(true).answer(false)
        val saved = engine.saveLesson(Progress(), session)
        val resumed = engine.startTopic("kotlin.a", saved)
        assertEquals(session, resumed)
    }

    @Test
    fun mistakesComeBackInReviewTomorrow() {
        val progress = passTopic(Progress(), "kotlin.a", wrongOnce = setOf("kotlin.a.q2")).progress
        assertNull(engine.startReview(progress, DAY0))

        val tomorrow = DAY0.plus(1, DateTimeUnit.DAY)
        val items = engine.reviewQueue.build(progress, tomorrow)
        assertEquals(listOf("kotlin.a.q2"), items.map { it.questionId })
        assertEquals(ReviewReason.Mistakes, items.single().reason)
    }

    @Test
    fun correctAnswersComeBackLaterToRefresh() {
        val progress = passTopic(Progress(), "kotlin.a", wrongOnce = setOf("kotlin.a.q2")).progress
        val inThreeDays = DAY0.plus(3, DateTimeUnit.DAY)
        val items = engine.reviewQueue.build(progress, inThreeDays)
        assertEquals("kotlin.a.q2", items.first().questionId)
        assertEquals(3, items.size)
        assertEquals(ReviewReason.Refresh, items.last().reason)
        assertEquals(listOf(0, 1, 0, 2, 0, 0, 0), engine.reviewQueue.forecast(progress, DAY0))
    }

    @Test
    fun reviewCompletionGivesXpAndExtendsStreak() {
        val progress = passTopic(Progress(), "kotlin.a", wrongOnce = setOf("kotlin.a.q1")).progress
        val tomorrow = DAY0.plus(1, DateTimeUnit.DAY)
        val review = assertNotNull(engine.startReview(progress, tomorrow))
        val (after, result) = engine.completeLesson(progress, review.play(), tomorrow)
        assertEquals(Xp.REVIEW_DONE, result.xpGained)
        assertEquals(2, after.streak.current)
        assertTrue(after.cards.getValue("kotlin.a.q1").due > tomorrow)
    }

    @Test
    fun dailyMixOpensAfterAllTopicsAndOncePerDay() {
        var progress = Progress()
        assertNull(engine.startDailyMix(progress, DAY0))
        for (topicId in listOf("kotlin.a", "kotlin.b", "kotlin.c", "coroutines.a")) {
            progress = passTopic(progress, topicId).progress
        }
        val mix = assertNotNull(engine.startDailyMix(progress, DAY0))
        assertEquals(12, mix.total)
        assertEquals(4, mix.queue.take(4).map { testContent.topicOfQuestion(it)!!.id }.toSet().size, "topics are interleaved")

        progress = engine.completeLesson(progress, mix.play(), DAY0).progress
        assertNull(engine.startDailyMix(progress, DAY0))
        assertNotNull(engine.startDailyMix(progress, DAY0.plus(1, DateTimeUnit.DAY)))
        assertTrue(Achievement.SectionComplete in progress.achievements)
    }

    @Test
    fun timerAttemptIsRecordedAndRewarded() {
        val attempt = TimerAttempt("kotlin.a.o1", DAY0, durationSeconds = 180, hintsUsed = 1, checked = 2, total = 3)
        val outcome = engine.completeTimer(Progress(), attempt)
        assertEquals(listOf(attempt), outcome.progress.timerHistory)
        assertEquals(Xp.TIMER_REVIEWED, outcome.progress.xp)
        assertNotNull(outcome.progress.openCards["kotlin.a.o1"])
        assertEquals(1, outcome.progress.streak.current)
    }

    @Test
    fun nextOpenQuestionPrefersUnansweredAndRespectsSections() {
        val answered = engine.completeTimer(
            Progress(),
            TimerAttempt("kotlin.a.o1", DAY0, 180, 0, 3, 3),
        ).progress
        repeat(20) {
            val next = engine.nextOpenQuestion(answered, setOf("kotlin"), DAY0, Random(it))
            assertTrue(next!!.id in setOf("kotlin.b.o1", "kotlin.c.o1"))
        }
        assertEquals("coroutines.a.o1", engine.nextOpenQuestion(Progress(), setOf("coroutines"), DAY0)?.id)
    }
}
