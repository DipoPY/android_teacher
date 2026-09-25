package dev.androidteacher.core.domain

import dev.androidteacher.core.model.Content
import dev.androidteacher.core.model.QuestionId
import dev.androidteacher.core.srs.Scheduler
import kotlinx.datetime.LocalDate
import kotlin.random.Random

/**
 * Ежедневный микс: открывается, когда пройдены все темы.
 * Берёт вопросы с наименьшей вероятностью вспомнить и перемешивает темы (интерливинг).
 */
class DailyMixBuilder(
    private val content: Content,
    private val scheduler: Scheduler,
    private val progression: TopicProgression,
) {

    fun isUnlocked(progress: Progress): Boolean = progression.allPassed(progress)

    fun isDoneToday(progress: Progress, today: LocalDate): Boolean = progress.lastDailyMix == today

    fun build(progress: Progress, today: LocalDate, size: Int = DEFAULT_SIZE): List<QuestionId> {
        val random = Random(today.toString().hashCode())
        val weakest = content.topics
            .flatMap { topic -> topic.quiz.map { topic.id to it.id } }
            .shuffled(random)
            .sortedBy { (_, id) -> progress.cards[id]?.let { scheduler.retrievability(it, today) } ?: 0.0 }
            .take(size)
        return interleaveByTopic(weakest.groupBy({ it.first }, { it.second }))
    }

    /** Чередует темы: по одному вопросу из каждой темы по кругу. */
    private fun <K> interleaveByTopic(byTopic: Map<K, List<QuestionId>>): List<QuestionId> {
        val iterators = byTopic.values.map { it.iterator() }
        val result = mutableListOf<QuestionId>()
        while (iterators.any { it.hasNext() }) {
            iterators.filter { it.hasNext() }.forEach { result += it.next() }
        }
        return result
    }

    companion object {
        const val DEFAULT_SIZE = 30
    }
}
