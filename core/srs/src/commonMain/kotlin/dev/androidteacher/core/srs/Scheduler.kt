package dev.androidteacher.core.srs

import kotlinx.datetime.LocalDate

/** Планировщик интервальных повторений. */
interface Scheduler {

    /** Новое состояние вопроса после ответа с оценкой [grade] в день [today]. `card == null` — первый ответ. */
    fun review(card: CardState?, grade: Grade, today: LocalDate): CardState

    /** Вероятность (0..1), что ученик вспомнит ответ в день [today]. */
    fun retrievability(card: CardState, today: LocalDate): Double
}
