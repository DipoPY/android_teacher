package dev.androidteacher.core.model

import kotlinx.serialization.Serializable

/**
 * Весь учебный контент. Собирается из `content/` утилитой `tools/content-compiler`
 * в `content.json` и загружается приложением целиком.
 */
@Serializable
data class Content(
    val sections: List<Section>,
) {
    val topics: List<Topic> get() = sections.flatMap { it.topics }

    fun section(id: SectionId): Section? = sections.firstOrNull { it.id == id }

    fun topic(id: TopicId): Topic? = topics.firstOrNull { it.id == id }

    fun sectionOf(topicId: TopicId): Section? = sections.firstOrNull { section -> section.topics.any { it.id == topicId } }

    fun quizQuestion(id: QuestionId): QuizQuestion? = quizQuestionsById[id]

    fun topicOfQuestion(id: QuestionId): Topic? = topicByQuestionId[id]

    private val quizQuestionsById: Map<QuestionId, QuizQuestion> by lazy {
        topics.flatMap { it.quiz }.associateBy { it.id }
    }

    private val topicByQuestionId: Map<QuestionId, Topic> by lazy {
        topics.flatMap { topic -> topic.quiz.map { it.id to topic } + topic.open.map { it.id to topic } }.toMap()
    }
}

/** Раздел, например «Kotlin». Порядок тем внутри раздела задаёт порядок их открытия. */
@Serializable
data class Section(
    val id: SectionId,
    val title: String,
    val topics: List<Topic>,
)

/** Тема, например «data class»: вопросы квиза и открытые вопросы для режима «Таймер». */
@Serializable
data class Topic(
    val id: TopicId,
    val title: String,
    val quiz: List<QuizQuestion>,
    val open: List<OpenQuestion>,
)

typealias SectionId = String
typealias TopicId = String
typealias QuestionId = String
