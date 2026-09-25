package dev.androidteacher.core.domain

import dev.androidteacher.core.model.QuestionId
import dev.androidteacher.core.srs.Grade
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Попытка ответа в режиме «Таймер».
 *
 * @property checked сколько аспектов чек-листа ученик отметил как прозвучавшие.
 */
@Serializable
data class TimerAttempt(
    val questionId: QuestionId,
    val date: LocalDate,
    val durationSeconds: Int,
    val hintsUsed: Int,
    val checked: Int,
    val total: Int,
) {
    init {
        require(total > 0 && checked in 0..total) { "checked must be in 0..total" }
    }

    val scorePercent: Int get() = checked * 100 / total
}

object TimerScoring {
    fun grade(attempt: TimerAttempt): Grade = when {
        attempt.scorePercent < 40 -> Grade.Again
        attempt.scorePercent < 70 -> Grade.Hard
        attempt.scorePercent < 90 -> Grade.Good
        else -> Grade.Easy
    }
}
