package dev.androidteacher.core.domain

import dev.androidteacher.core.model.Content
import dev.androidteacher.core.model.Section
import dev.androidteacher.core.model.SectionId
import dev.androidteacher.core.model.Topic
import dev.androidteacher.core.model.TopicId

enum class TopicStatus { Passed, Available, Locked }

data class TopicProgress(val topic: Topic, val status: TopicStatus)

data class SectionProgress(
    val section: Section,
    val topics: List<TopicProgress>,
) {
    val passedCount: Int get() = topics.count { it.status == TopicStatus.Passed }

    val totalCount: Int get() = topics.size

    val percent: Int get() = percentOf(passedCount, totalCount)

    val isComplete: Boolean get() = totalCount > 0 && passedCount == totalCount

    /** Первая доступная, но не пройденная тема — «продолжить» на главной. */
    val nextTopic: Topic? get() = topics.firstOrNull { it.status == TopicStatus.Available }?.topic
}

/**
 * Правила открытия тем: разделы открыты все сразу, темы внутри раздела — по порядку,
 * следующая открывается после прохождения предыдущей.
 */
class TopicProgression(private val content: Content) {

    fun sections(progress: Progress): List<SectionProgress> = content.sections.map { section(it, progress) }

    fun section(id: SectionId, progress: Progress): SectionProgress? = content.section(id)?.let { section(it, progress) }

    fun status(topicId: TopicId, progress: Progress): TopicStatus? {
        val section = content.sectionOf(topicId) ?: return null
        return section(section, progress).topics.first { it.topic.id == topicId }.status
    }

    /** Следующая тема раздела после [topicId] или `null`, если тема последняя. */
    fun nextTopicAfter(topicId: TopicId): Topic? {
        val topics = content.sectionOf(topicId)?.topics ?: return null
        val index = topics.indexOfFirst { it.id == topicId }
        return topics.getOrNull(index + 1)
    }

    fun passedCount(progress: Progress): Int = content.topics.count { progress.isPassed(it.id) }

    fun totalCount(): Int = content.topics.size

    fun overallPercent(progress: Progress): Int = percentOf(passedCount(progress), totalCount())

    fun allPassed(progress: Progress): Boolean = totalCount() > 0 && passedCount(progress) == totalCount()

    private fun section(section: Section, progress: Progress): SectionProgress {
        var previousPassed = true
        val topics = section.topics.map { topic ->
            val status = when {
                progress.isPassed(topic.id) -> TopicStatus.Passed
                previousPassed -> TopicStatus.Available
                else -> TopicStatus.Locked
            }
            previousPassed = status == TopicStatus.Passed
            TopicProgress(topic, status)
        }
        return SectionProgress(section, topics)
    }
}

internal fun percentOf(part: Int, total: Int): Int = if (total == 0) 0 else part * 100 / total
