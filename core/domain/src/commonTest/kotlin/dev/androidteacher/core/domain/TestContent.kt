package dev.androidteacher.core.domain

import dev.androidteacher.core.model.Content
import dev.androidteacher.core.model.OpenQuestion
import dev.androidteacher.core.model.QuizKind
import dev.androidteacher.core.model.QuizQuestion
import dev.androidteacher.core.model.Section
import dev.androidteacher.core.model.Topic
import kotlinx.datetime.LocalDate

internal val DAY0 = LocalDate(2026, 9, 1)

internal fun quiz(id: String) = QuizQuestion(
    id = id,
    kind = QuizKind.Single,
    prompt = "Вопрос $id",
    options = listOf("да", "нет"),
    correct = setOf(0),
    explanation = "",
)

internal fun open(id: String) = OpenQuestion(
    id = id,
    prompt = "Расскажи про $id",
    hints = listOf("1", "2", "3"),
    checklist = listOf("a", "b", "c"),
    answer = "",
)

internal fun topic(id: String, questions: Int = 3) =
    Topic(id = id, title = id, quiz = (1..questions).map { quiz("$id.q$it") }, open = listOf(open("$id.o1")))

/** Kotlin: 3 темы по 3 вопроса, Coroutines: 1 тема. */
internal val testContent = Content(
    sections = listOf(
        Section("kotlin", "Kotlin", listOf(topic("kotlin.a"), topic("kotlin.b"), topic("kotlin.c"))),
        Section("coroutines", "Coroutines", listOf(topic("coroutines.a"))),
    ),
)

/** Проходит урок: вопросы из [wrongOnce] сначала отвечаются неверно. */
internal fun LessonSession.play(wrongOnce: Set<String> = emptySet()): LessonSession {
    var session = this
    val failed = mutableSetOf<String>()
    while (!session.isFinished) {
        val id = checkNotNull(session.current)
        val correct = id !in wrongOnce || id in failed
        if (!correct) failed += id
        session = session.answer(correct)
    }
    return session
}
