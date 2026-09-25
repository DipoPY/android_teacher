package dev.androidteacher.core.domain

import dev.androidteacher.core.model.QuestionId
import dev.androidteacher.core.model.SectionId
import dev.androidteacher.core.model.TopicId
import dev.androidteacher.core.srs.CardState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Весь прогресс ученика. Хранится одним JSON-снимком (см. `core/data`).
 * Любое изменение схемы — новая [schemaVersion] и миграция в `ProgressMigrations`.
 *
 * @property cards состояние памяти по вопросам квиза.
 * @property openCards состояние памяти по открытым вопросам (режим «Таймер»).
 * @property passedTopics пройденные темы и дата прохождения.
 * @property perfectTopics темы, пройденные без единой ошибки.
 * @property lessons незаконченные уроки тем, ключ — id темы.
 * @property activity число завершённых занятий по дням (ключ — дата ISO), для календаря активности.
 * @property lastDailyMix дата последнего пройденного ежедневного микса.
 */
@Serializable
data class Progress(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val cards: Map<QuestionId, CardState> = emptyMap(),
    val openCards: Map<QuestionId, CardState> = emptyMap(),
    val passedTopics: Map<TopicId, LocalDate> = emptyMap(),
    val perfectTopics: Set<TopicId> = emptySet(),
    val lessons: Map<TopicId, LessonSession> = emptyMap(),
    val streak: StreakState = StreakState(),
    val xp: Int = 0,
    val achievements: Set<Achievement> = emptySet(),
    val activity: Map<String, Int> = emptyMap(),
    val timerHistory: List<TimerAttempt> = emptyList(),
    val lastDailyMix: LocalDate? = null,
    val settings: Settings = Settings(),
) {
    fun isPassed(topicId: TopicId): Boolean = topicId in passedTopics

    fun activityOn(date: LocalDate): Int = activity[date.toString()] ?: 0

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * @property timerSeconds время на ответ в режиме «Таймер» по умолчанию.
 * @property timerSections разделы-источники для таймера, пусто — все.
 */
@Serializable
data class Settings(
    val timerSeconds: Int = 180,
    val recordAudio: Boolean = true,
    val timerSections: Set<SectionId> = emptySet(),
)
