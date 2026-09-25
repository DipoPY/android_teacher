package dev.androidteacher.core.srs

/** Оценка ответа для планировщика (шкала FSRS / Anki). */
enum class Grade(val value: Int) {
    /** Не вспомнил или ошибся. */
    Again(1),

    /** Вспомнил с трудом. */
    Hard(2),

    /** Вспомнил. */
    Good(3),

    /** Вспомнил легко. */
    Easy(4),
}
