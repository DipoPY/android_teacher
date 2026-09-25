package dev.androidteacher.core.domain

import dev.androidteacher.core.model.Content
import dev.androidteacher.core.model.OpenQuestion
import dev.androidteacher.core.model.SectionId
import dev.androidteacher.core.model.TopicId
import dev.androidteacher.core.srs.FsrsScheduler
import dev.androidteacher.core.srs.Scheduler
import kotlinx.datetime.LocalDate
import kotlin.random.Random

/** Итог завершённого урока для экрана результата. */
data class LessonResult(
    val kind: LessonKind,
    val questionsCount: Int,
    val accuracyPercent: Int,
    val xpGained: Int,
    val streak: Int,
    val passedTopic: TopicId?,
    val unlockedTopic: TopicId?,
    val sectionPercentBefore: Int?,
    val sectionPercentAfter: Int?,
    val newAchievements: Set<Achievement>,
)

data class LessonOutcome(val progress: Progress, val result: LessonResult)

data class TimerOutcome(val progress: Progress, val xpGained: Int, val newAchievements: Set<Achievement>)

/**
 * Точка входа в правила обучения. Чистые функции над [Progress]: получают прогресс
 * и возвращают новый, ничего не хранят и не читают текущую дату сами.
 */
class LearningEngine(
    val content: Content,
    private val scheduler: Scheduler = FsrsScheduler(),
) {
    val progression = TopicProgression(content)
    val reviewQueue = ReviewQueueBuilder(content, scheduler)
    val dailyMix = DailyMixBuilder(content, scheduler, progression)
    private val achievements = AchievementChecker(progression)

    /** Начинает урок темы или продолжает сохранённый. */
    fun startTopic(topicId: TopicId, progress: Progress): LessonSession {
        val topic = requireNotNull(content.topic(topicId)) { "Unknown topic $topicId" }
        require(progression.status(topicId, progress) != TopicStatus.Locked) { "Topic $topicId is locked" }
        val ids = topic.quiz.map { it.id }
        val saved = progress.lessons[topicId] ?: return LessonSession.start(LessonKind.Topic(topicId), ids)

        // Контент мог измениться: убираем удалённые вопросы и добавляем новые в конец.
        val completed = saved.completed.filter { it in ids }
        val queue = saved.queue.filter { it in ids } + ids.filter { it !in saved.queue && it !in completed }
        if (queue.isEmpty()) return LessonSession.start(LessonKind.Topic(topicId), ids)
        return saved.copy(queue = queue, completed = completed, mistakes = saved.mistakes.filterKeys { it in ids })
    }

    /** Повторение на сегодня или `null`, если повторять нечего. */
    fun startReview(progress: Progress, today: LocalDate): LessonSession? {
        val items = reviewQueue.build(progress, today)
        if (items.isEmpty()) return null
        return LessonSession.start(LessonKind.Review, items.map { it.questionId })
    }

    /** Ежедневный микс или `null`, если он закрыт или уже пройден сегодня. */
    fun startDailyMix(progress: Progress, today: LocalDate): LessonSession? {
        if (!dailyMix.isUnlocked(progress) || dailyMix.isDoneToday(progress, today)) return null
        val ids = dailyMix.build(progress, today)
        if (ids.isEmpty()) return null
        return LessonSession.start(LessonKind.DailyMix, ids)
    }

    /** Сохраняет незаконченный урок темы, чтобы его можно было продолжить. */
    fun saveLesson(progress: Progress, session: LessonSession): Progress {
        val kind = session.kind as? LessonKind.Topic ?: return progress
        return if (session.isFinished) {
            progress.copy(lessons = progress.lessons - kind.topicId)
        } else {
            progress.copy(lessons = progress.lessons + (kind.topicId to session))
        }
    }

    fun completeLesson(progress: Progress, session: LessonSession, today: LocalDate): LessonOutcome {
        require(session.isFinished) { "Lesson is not finished" }

        val cards = progress.cards.toMutableMap()
        session.grades().forEach { (id, grade) -> cards[id] = scheduler.review(cards[id], grade, today) }

        var updated = progress.copy(cards = cards)
        var xp = 0
        var passedTopic: TopicId? = null
        var unlockedTopic: TopicId? = null
        var sectionBefore: Int? = null
        var sectionAfter: Int? = null

        when (val kind = session.kind) {
            is LessonKind.Topic -> {
                val sectionId = content.sectionOf(kind.topicId)?.id
                sectionBefore = sectionId?.let { sectionPercent(it, progress) }
                if (!progress.isPassed(kind.topicId)) {
                    xp = Xp.TOPIC_PASSED
                    passedTopic = kind.topicId
                    unlockedTopic = progression.nextTopicAfter(kind.topicId)?.id
                    updated = updated.copy(passedTopics = updated.passedTopics + (kind.topicId to today))
                }
                if (session.isPerfect) updated = updated.copy(perfectTopics = updated.perfectTopics + kind.topicId)
                updated = updated.copy(lessons = updated.lessons - kind.topicId)
                sectionAfter = sectionId?.let { sectionPercent(it, updated) }
            }

            LessonKind.Review -> xp = Xp.REVIEW_DONE

            LessonKind.DailyMix -> {
                xp = Xp.DAILY_MIX_DONE
                updated = updated.copy(lastDailyMix = today)
            }
        }

        val (rewarded, newAchievements) = recordActivity(updated.copy(xp = updated.xp + xp), progress, today)
        return LessonOutcome(
            progress = rewarded,
            result = LessonResult(
                kind = session.kind,
                questionsCount = session.total,
                accuracyPercent = session.accuracyPercent,
                xpGained = xp,
                streak = rewarded.streak.current,
                passedTopic = passedTopic,
                unlockedTopic = unlockedTopic,
                sectionPercentBefore = sectionBefore,
                sectionPercentAfter = sectionAfter,
                newAchievements = newAchievements,
            ),
        )
    }

    /** Открытые вопросы для таймера из выбранных разделов (пусто — все). */
    fun openQuestions(sections: Set<SectionId>): List<OpenQuestion> =
        content.sections
            .filter { sections.isEmpty() || it.id in sections }
            .flatMap { section -> section.topics.flatMap { it.open } }

    /**
     * Следующий вопрос для таймера: сначала те, что ни разу не отвечались или пора повторить,
     * среди равных — случайный. [exclude] — вопрос, который только что был.
     */
    fun nextOpenQuestion(
        progress: Progress,
        sections: Set<SectionId>,
        today: LocalDate,
        random: Random = Random.Default,
        exclude: String? = null,
    ): OpenQuestion? {
        val candidates = openQuestions(sections).filter { it.id != exclude }.ifEmpty { openQuestions(sections) }
        if (candidates.isEmpty()) return null
        val priority = { question: OpenQuestion ->
            progress.openCards[question.id]?.let { scheduler.retrievability(it, today) } ?: 0.0
        }
        val best = candidates.minOf(priority)
        return candidates.filter { priority(it) <= best + 0.05 }.random(random)
    }

    fun completeTimer(progress: Progress, attempt: TimerAttempt): TimerOutcome {
        val openCards = progress.openCards + (
            attempt.questionId to scheduler.review(progress.openCards[attempt.questionId], TimerScoring.grade(attempt), attempt.date)
            )
        val updated = progress.copy(
            openCards = openCards,
            timerHistory = progress.timerHistory + attempt,
            xp = progress.xp + Xp.TIMER_REVIEWED,
        )
        val (rewarded, newAchievements) = recordActivity(updated, progress, attempt.date)
        return TimerOutcome(rewarded, Xp.TIMER_REVIEWED, newAchievements)
    }

    fun sectionPercent(sectionId: SectionId, progress: Progress): Int =
        progression.section(sectionId, progress)?.percent ?: 0

    /** Засчитывает день в серию и календарь, выдаёт новые достижения. */
    private fun recordActivity(updated: Progress, before: Progress, today: LocalDate): Pair<Progress, Set<Achievement>> {
        val key = today.toString()
        val withActivity = updated.copy(
            streak = Streaks.onActivity(updated.streak, today),
            activity = updated.activity + (key to (updated.activity[key] ?: 0) + 1),
        )
        val earned = achievements.earned(withActivity)
        return withActivity.copy(achievements = before.achievements + earned) to (earned - before.achievements)
    }
}

