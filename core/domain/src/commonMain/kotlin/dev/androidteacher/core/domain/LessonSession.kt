package dev.androidteacher.core.domain

import dev.androidteacher.core.model.QuestionId
import dev.androidteacher.core.model.TopicId
import dev.androidteacher.core.srs.Grade
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LessonKind {
    /** Урок темы: после прохождения открывается следующая тема. */
    @Serializable
    @SerialName("topic")
    data class Topic(val topicId: TopicId) : LessonKind

    /** Повторение на сегодня. */
    @Serializable
    @SerialName("review")
    data object Review : LessonKind

    /** Ежедневный микс после прохождения всех тем. */
    @Serializable
    @SerialName("daily-mix")
    data object DailyMix : LessonKind
}

/**
 * Очередь урока (mastery learning): ошибка отправляет вопрос в конец очереди,
 * урок закончен, когда на каждый вопрос дан верный ответ.
 *
 * Неизменяемый: каждый ответ возвращает новую сессию, её удобно сохранять и тестировать.
 */
@Serializable
data class LessonSession(
    val kind: LessonKind,
    val queue: List<QuestionId>,
    val completed: List<QuestionId> = emptyList(),
    val mistakes: Map<QuestionId, Int> = emptyMap(),
) {
    val current: QuestionId? get() = queue.firstOrNull()

    val isFinished: Boolean get() = queue.isEmpty()

    val total: Int get() = queue.size + completed.size

    /** Доля вопросов, на которые уже ответили верно, 0..1. */
    val progress: Float get() = if (total == 0) 1f else completed.size.toFloat() / total

    val isPerfect: Boolean get() = mistakes.isEmpty()

    /** Доля вопросов, на которые ответили верно с первой попытки, в процентах. */
    val accuracyPercent: Int
        get() = if (total == 0) 100 else (total - mistakes.size) * 100 / total

    /** Сколько раз вопрос [id] уже был отвечен неверно в этом уроке. */
    fun mistakesFor(id: QuestionId): Int = mistakes[id] ?: 0

    fun answer(correct: Boolean): LessonSession {
        val id = checkNotNull(current) { "Lesson is already finished" }
        val rest = queue.drop(1)
        return if (correct) {
            copy(queue = rest, completed = completed + id)
        } else {
            copy(queue = rest + id, mistakes = mistakes + (id to mistakesFor(id) + 1))
        }
    }

    /**
     * Оценки для планировщика: ошибка в уроке (даже исправленная) — [Grade.Again],
     * вопрос придёт в повторение завтра; верно с первой попытки — [Grade.Good].
     */
    fun grades(): Map<QuestionId, Grade> =
        completed.associateWith { if (mistakesFor(it) > 0) Grade.Again else Grade.Good }

    companion object {
        fun start(kind: LessonKind, questions: List<QuestionId>): LessonSession {
            require(questions.isNotEmpty()) { "Lesson needs at least one question" }
            require(questions.toSet().size == questions.size) { "Lesson questions must be unique" }
            return LessonSession(kind = kind, queue = questions)
        }
    }
}
