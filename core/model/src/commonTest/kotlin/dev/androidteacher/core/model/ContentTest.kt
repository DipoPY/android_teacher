package dev.androidteacher.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentTest {

    private val question = QuizQuestion(
        id = "kotlin.data-class.q01",
        kind = QuizKind.Multiple,
        prompt = "Что генерируется?",
        options = listOf("equals", "copy", "clone"),
        correct = setOf(0, 1),
        explanation = "clone не генерируется",
    )

    private val content = Content(
        sections = listOf(
            Section(
                id = "kotlin",
                title = "Kotlin",
                topics = listOf(Topic("kotlin.data-class", "data class", quiz = listOf(question), open = emptyList())),
            ),
        ),
    )

    @Test
    fun answerMustMatchAllCorrectOptionsExactly() {
        assertTrue(question.isCorrect(setOf(0, 1)))
        assertFalse(question.isCorrect(setOf(0)))
        assertFalse(question.isCorrect(setOf(0, 1, 2)))
    }

    @Test
    fun findsTopicAndSectionOfQuestion() {
        assertEquals("kotlin.data-class", content.topicOfQuestion("kotlin.data-class.q01")?.id)
        assertEquals("kotlin", content.sectionOf("kotlin.data-class")?.id)
    }

    @Test
    fun survivesJsonRoundTrip() {
        val json = Json.encodeToString(Content.serializer(), content)
        assertEquals(content, Json.decodeFromString(Content.serializer(), json))
        assertTrue("\"kind\":\"multiple\"" in json)
    }
}
