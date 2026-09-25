package dev.androidteacher.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Xp {
    const val TOPIC_PASSED = 40
    const val REVIEW_DONE = 20
    const val DAILY_MIX_DONE = 50
    const val TIMER_REVIEWED = 15
}

@Serializable
enum class Achievement {
    @SerialName("first-topic")
    FirstTopic,

    @SerialName("streak-7")
    Streak7,

    @SerialName("streak-30")
    Streak30,

    @SerialName("perfect-5")
    PerfectTopics5,

    @SerialName("section-complete")
    SectionComplete,

    @SerialName("timer-50")
    Timer50,
}

class AchievementChecker(private val progression: TopicProgression) {

    fun earned(progress: Progress): Set<Achievement> = buildSet {
        if (progress.passedTopics.isNotEmpty()) add(Achievement.FirstTopic)
        if (progress.streak.best >= 7) add(Achievement.Streak7)
        if (progress.streak.best >= 30) add(Achievement.Streak30)
        if (progress.perfectTopics.size >= 5) add(Achievement.PerfectTopics5)
        if (progression.sections(progress).any { it.isComplete }) add(Achievement.SectionComplete)
        if (progress.timerHistory.size >= 50) add(Achievement.Timer50)
    }
}
