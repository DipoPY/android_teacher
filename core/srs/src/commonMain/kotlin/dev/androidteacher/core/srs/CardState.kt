package dev.androidteacher.core.srs

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Состояние памяти по одному вопросу.
 *
 * @property stability через сколько дней вероятность вспомнить упадёт до 90%.
 * @property difficulty сложность вопроса для ученика, 1..10.
 * @property due дата, когда вопрос пора повторить.
 * @property reps сколько раз вопрос оценивался.
 * @property lapses сколько раз был ответ [Grade.Again].
 */
@Serializable
data class CardState(
    val stability: Double,
    val difficulty: Double,
    val due: LocalDate,
    val lastReview: LocalDate,
    val reps: Int,
    val lapses: Int,
)
