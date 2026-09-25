package dev.androidteacher.core.domain

import dev.androidteacher.core.model.Content
import dev.androidteacher.core.model.QuestionId
import dev.androidteacher.core.model.TopicId
import dev.androidteacher.core.srs.CardState
import dev.androidteacher.core.srs.Scheduler
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class ReviewReason {
    /** По вопросу были ошибки. */
    Mistakes,

    /** Вопрос давно не повторялся, пора освежить. */
    Refresh,
}

data class ReviewItem(
    val questionId: QuestionId,
    val topicId: TopicId,
    val reason: ReviewReason,
)

/**
 * «Повторение на сегодня»: вопросы, срок которых наступил.
 * Сначала вопросы с ошибками, затем с наименьшей вероятностью вспомнить.
 */
class ReviewQueueBuilder(
    private val content: Content,
    private val scheduler: Scheduler,
) {

    fun build(progress: Progress, today: LocalDate, limit: Int = DEFAULT_LIMIT): List<ReviewItem> =
        dueCards(progress, today)
            .sortedWith(
                compareByDescending<Pair<QuestionId, CardState>> { (_, card) -> card.lapses }
                    .thenBy { (_, card) -> scheduler.retrievability(card, today) }
                    .thenBy { (_, card) -> card.due }
                    .thenBy { (id, _) -> id },
            )
            .take(limit)
            .map { (id, card) ->
                ReviewItem(
                    questionId = id,
                    topicId = checkNotNull(content.topicOfQuestion(id)).id,
                    reason = if (card.lapses > 0) ReviewReason.Mistakes else ReviewReason.Refresh,
                )
            }

    fun dueCount(progress: Progress, today: LocalDate, limit: Int = DEFAULT_LIMIT): Int =
        minOf(dueCards(progress, today).size, limit)

    /** Сколько вопросов станет к повторению в каждый из [days] дней, начиная с сегодня (с учётом просроченных). */
    fun forecast(progress: Progress, today: LocalDate, days: Int = 7): List<Int> {
        val cards = knownCards(progress)
        return List(days) { offset ->
            val day = today.plus(offset, DateTimeUnit.DAY)
            cards.count { (_, card) -> if (offset == 0) card.due <= day else card.due == day }
        }
    }

    private fun dueCards(progress: Progress, today: LocalDate): List<Pair<QuestionId, CardState>> =
        knownCards(progress).filter { (_, card) -> card.due <= today }

    /** Карточки вопросов, которые есть в текущем контенте (удалённые вопросы игнорируются). */
    private fun knownCards(progress: Progress): List<Pair<QuestionId, CardState>> =
        progress.cards.entries
            .filter { (id, _) -> content.quizQuestion(id) != null }
            .map { it.key to it.value }

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
