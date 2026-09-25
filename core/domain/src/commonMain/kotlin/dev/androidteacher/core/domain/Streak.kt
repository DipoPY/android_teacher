package dev.androidteacher.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/**
 * Серия дней подряд.
 *
 * @property freezes заморозки: пропущенный день тратит одну вместо сброса серии.
 */
@Serializable
data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val lastActive: LocalDate? = null,
    val freezes: Int = 0,
)

object Streaks {
    const val MAX_FREEZES = 2

    /** Заморозка выдаётся за каждые столько дней подряд. */
    const val DAYS_PER_FREEZE = 7

    /** Учитывает занятие в день [today]. */
    fun onActivity(state: StreakState, today: LocalDate): StreakState {
        val last = state.lastActive ?: return earnFreeze(StreakState(current = 1, best = maxOf(state.best, 1), lastActive = today))
        val gap = last.daysUntil(today)
        if (gap <= 0) return state

        val missed = gap - 1
        val continues = missed <= state.freezes
        val current = if (continues) state.current + 1 else 1
        return earnFreeze(
            StreakState(
                current = current,
                best = maxOf(state.best, current),
                lastActive = today,
                freezes = if (continues) state.freezes - missed else 0,
            ),
        )
    }

    /** Серия, которую видит ученик в день [today]: 0, если она уже сгорела. */
    fun current(state: StreakState, today: LocalDate): Int {
        val last = state.lastActive ?: return 0
        val missed = last.daysUntil(today) - 1
        return if (missed <= state.freezes) state.current else 0
    }

    fun isDoneToday(state: StreakState, today: LocalDate): Boolean = state.lastActive == today

    private fun earnFreeze(state: StreakState): StreakState =
        if (state.current % DAYS_PER_FREEZE == 0) {
            state.copy(freezes = minOf(MAX_FREEZES, state.freezes + 1))
        } else {
            state
        }
}
