package dev.androidteacher.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class StreaksTest {

    private fun day(n: Int) = DAY0.plus(n, DateTimeUnit.DAY)

    private fun activeDays(vararg days: Int): StreakState =
        days.fold(StreakState()) { state, n -> Streaks.onActivity(state, day(n)) }

    @Test
    fun consecutiveDaysGrowStreak() {
        val state = activeDays(0, 1, 2)
        assertEquals(3, state.current)
        assertEquals(3, state.best)
    }

    @Test
    fun severalActivitiesInOneDayCountOnce() {
        assertEquals(1, activeDays(0, 0, 0).current)
    }

    @Test
    fun missedDayWithoutFreezeResetsStreak() {
        val state = activeDays(0, 1, 3)
        assertEquals(1, state.current)
        assertEquals(2, state.best)
    }

    @Test
    fun sevenDaysEarnAFreezeThatSavesOneMissedDay() {
        val week = activeDays(*IntArray(7) { it })
        assertEquals(1, week.freezes)

        val afterGap = Streaks.onActivity(week, day(8))
        assertEquals(8, afterGap.current)
        assertEquals(0, afterGap.freezes)
    }

    @Test
    fun freezesAreCapped() {
        val state = activeDays(*IntArray(28) { it })
        assertEquals(Streaks.MAX_FREEZES, state.freezes)
    }

    @Test
    fun displayedStreakBurnsAfterMissedDays() {
        val state = activeDays(0, 1, 2)
        assertEquals(3, Streaks.current(state, day(3)))
        assertEquals(0, Streaks.current(state, day(4)))
    }
}
