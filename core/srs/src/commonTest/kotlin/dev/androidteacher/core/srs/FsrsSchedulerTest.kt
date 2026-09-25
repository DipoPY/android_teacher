package dev.androidteacher.core.srs

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsSchedulerTest {

    private val scheduler = FsrsScheduler()
    private val day0 = LocalDate(2026, 9, 1)

    private fun intervalAfterFirst(grade: Grade): Int = day0.daysUntil(scheduler.review(null, grade, day0).due)

    @Test
    fun firstReviewUsesInitialStability() {
        assertEquals(1, intervalAfterFirst(Grade.Again))
        assertEquals(1, intervalAfterFirst(Grade.Hard))
        assertEquals(3, intervalAfterFirst(Grade.Good))
        assertEquals(16, intervalAfterFirst(Grade.Easy))
    }

    @Test
    fun firstAgainCountsAsLapse() {
        assertEquals(1, scheduler.review(null, Grade.Again, day0).lapses)
        assertEquals(0, scheduler.review(null, Grade.Good, day0).lapses)
    }

    @Test
    fun retrievabilityIsNinetyPercentAfterStabilityDays() {
        val card = CardState(stability = 10.0, difficulty = 5.0, due = day0, lastReview = day0, reps = 1, lapses = 0)
        val r = scheduler.retrievability(card, day0.plus(10, DateTimeUnit.DAY))
        assertTrue(abs(r - 0.9) < 1e-9, "R was $r")
        assertEquals(1.0, scheduler.retrievability(card, day0))
    }

    @Test
    fun intervalsGrowWhenAnsweredOnTime() {
        var card = scheduler.review(null, Grade.Good, day0)
        var previous = day0.daysUntil(card.due)
        repeat(5) {
            val reviewDay = card.due
            card = scheduler.review(card, Grade.Good, reviewDay)
            val next = reviewDay.daysUntil(card.due)
            assertTrue(next > previous, "interval should grow: $previous -> $next")
            previous = next
        }
    }

    @Test
    fun lapseResetsIntervalAndRaisesDifficulty() {
        val learned = scheduler.review(scheduler.review(null, Grade.Good, day0), Grade.Good, day0.plus(3, DateTimeUnit.DAY))
        val reviewDay = learned.due
        val lapsed = scheduler.review(learned, Grade.Again, reviewDay)
        assertTrue(lapsed.stability < learned.stability)
        assertTrue(lapsed.difficulty > learned.difficulty)
        assertEquals(1, reviewDay.daysUntil(lapsed.due))
        assertEquals(1, lapsed.lapses)
    }

    @Test
    fun easyGivesLongerIntervalThanHard() {
        val card = scheduler.review(null, Grade.Good, day0)
        val day = card.due
        val hard = scheduler.review(card, Grade.Hard, day)
        val good = scheduler.review(card, Grade.Good, day)
        val easy = scheduler.review(card, Grade.Easy, day)
        assertTrue(hard.stability < good.stability && good.stability < easy.stability)
    }

    @Test
    fun sameDaySuccessDoesNotInflateStability() {
        val card = scheduler.review(null, Grade.Good, day0)
        val again = scheduler.review(card, Grade.Good, day0)
        assertTrue(abs(again.stability - card.stability) < 1e-9)
    }

    @Test
    fun difficultyStaysInRange() {
        var card = scheduler.review(null, Grade.Again, day0)
        repeat(20) { card = scheduler.review(card, Grade.Again, card.due) }
        assertTrue(card.difficulty in 1.0..10.0)
        repeat(20) { card = scheduler.review(card, Grade.Easy, card.due) }
        assertTrue(card.difficulty in 1.0..10.0)
    }
}
