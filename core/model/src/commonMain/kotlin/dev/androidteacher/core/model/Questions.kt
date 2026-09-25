package dev.androidteacher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QuizKind {
    /** Один верный вариант. */
    @SerialName("single")
    Single,

    /** Несколько верных вариантов. */
    @SerialName("multiple")
    Multiple,

    /** Утверждение: «Верно» / «Неверно». */
    @SerialName("true-false")
    TrueFalse,
}

/**
 * Вопрос квиза.
 *
 * @property code фрагмент кода к вопросу, показывается отдельным блоком под текстом.
 * @property correct индексы верных вариантов в [options].
 */
@Serializable
data class QuizQuestion(
    val id: QuestionId,
    val kind: QuizKind,
    val prompt: String,
    val code: String? = null,
    val options: List<String>,
    val correct: Set<Int>,
    val explanation: String,
) {
    fun isCorrect(selected: Set<Int>): Boolean = selected == correct
}

/**
 * Открытый вопрос для режима «Таймер»: ответ вслух и разбор по чек-листу.
 *
 * @property hints ровно три подсказки: намёк → ключевые слова → начало ответа.
 * @property checklist аспекты, которые должны прозвучать в хорошем ответе.
 * @property answer эталонный ответ в Markdown.
 */
@Serializable
data class OpenQuestion(
    val id: QuestionId,
    val prompt: String,
    val code: String? = null,
    val hints: List<String>,
    val checklist: List<String>,
    val followUps: List<String> = emptyList(),
    val answer: String,
)
