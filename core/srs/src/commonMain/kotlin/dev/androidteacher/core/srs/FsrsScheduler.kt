package dev.androidteacher.core.srs

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-5 (Free Spaced Repetition Scheduler) с параметрами по умолчанию.
 *
 * Модель памяти DSR: stability (S), difficulty (D), retrievability (R).
 * Ye, Su & Cao, «A Stochastic Shortest Path Algorithm for Optimizing Spaced Repetition Scheduling», KDD 2022.
 *
 * Отличие от эталона: после [Grade.Again] вопрос всегда возвращается завтра.
 *
 * Планирование — по дням: повторы в тот же день не меняют стабильность при успехе,
 * поэтому кратковременную (same-day) модель FSRS-5 не используем.
 *
 * @param desiredRetention целевая вероятность вспомнить в день повторения.
 * @param maxIntervalDays верхняя граница интервала.
 */
class FsrsScheduler(
    private val desiredRetention: Double = 0.9,
    private val maxIntervalDays: Int = 365,
    private val w: DoubleArray = DEFAULT_WEIGHTS,
) : Scheduler {

    init {
        require(desiredRetention in 0.7..0.97) { "desiredRetention must be in 0.7..0.97" }
        require(w.size == DEFAULT_WEIGHTS.size) { "FSRS-5 needs ${DEFAULT_WEIGHTS.size} weights" }
    }

    override fun review(card: CardState?, grade: Grade, today: LocalDate): CardState {
        if (card == null) {
            val stability = initialStability(grade)
            return CardState(
                stability = stability,
                difficulty = initialDifficulty(grade),
                due = nextDue(stability, grade, today),
                lastReview = today,
                reps = 1,
                lapses = if (grade == Grade.Again) 1 else 0,
            )
        }

        val r = retrievability(card, today)
        val stability = if (grade == Grade.Again) {
            forgetStability(card.difficulty, card.stability, r)
        } else {
            recallStability(card.difficulty, card.stability, r, grade)
        }
        return CardState(
            stability = stability,
            difficulty = nextDifficulty(card.difficulty, grade),
            due = nextDue(stability, grade, today),
            lastReview = today,
            reps = card.reps + 1,
            lapses = card.lapses + if (grade == Grade.Again) 1 else 0,
        )
    }

    override fun retrievability(card: CardState, today: LocalDate): Double {
        val elapsed = max(0, card.lastReview.daysUntil(today))
        return (1 + FACTOR * elapsed / card.stability).pow(DECAY)
    }

    /** Ошибка всегда возвращает вопрос на следующий день — так ученик доучивает его, пока свежо. */
    private fun nextDue(stability: Double, grade: Grade, today: LocalDate): LocalDate {
        val days = if (grade == Grade.Again) 1 else interval(stability)
        return today.plus(days, DateTimeUnit.DAY)
    }

    /** Интервал в днях, после которого R упадёт до [desiredRetention]. */
    internal fun interval(stability: Double): Int {
        val days = stability / FACTOR * (desiredRetention.pow(1 / DECAY) - 1)
        return days.roundToInt().coerceIn(1, maxIntervalDays)
    }

    private fun initialStability(grade: Grade): Double = max(w[grade.value - 1], MIN_STABILITY)

    private fun initialDifficulty(grade: Grade): Double =
        (w[4] - exp(w[5] * (grade.value - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, grade: Grade): Double {
        val delta = -w[6] * (grade.value - 3)
        val damped = d + delta * (10 - d) / 9
        val reverted = w[7] * initialDifficulty(Grade.Easy) + (1 - w[7]) * damped
        return reverted.coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, r: Double, grade: Grade): Double {
        val hardPenalty = if (grade == Grade.Hard) w[15] else 1.0
        val easyBonus = if (grade == Grade.Easy) w[16] else 1.0
        val growth = exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hardPenalty * easyBonus
        return max(s * (growth + 1), MIN_STABILITY)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val forgotten = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))
        return min(forgotten, s).coerceAtLeast(MIN_STABILITY)
    }

    companion object {
        /** Параметры FSRS-5 по умолчанию (оптимизированы на большом датасете Anki). */
        val DEFAULT_WEIGHTS = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
        )

        private const val DECAY = -0.5

        /** Подобран так, что R(t = S) = 0.9. */
        private const val FACTOR = 19.0 / 81.0

        private const val MIN_STABILITY = 0.01
    }
}
