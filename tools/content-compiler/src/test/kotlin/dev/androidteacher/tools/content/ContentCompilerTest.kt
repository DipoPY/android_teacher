package dev.androidteacher.tools.content

import dev.androidteacher.core.model.QuizKind
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentCompilerTest {

    private val root: File = createTempDirectory("content").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun write(path: String, text: String) {
        File(root, path).apply { parentFile.mkdirs() }.writeText(text.trimIndent())
    }

    private fun single(id: String) = """
        ---
        id: $id
        type: single
        ---
        Что напечатает код?

        ```kotlin
        println(1 + 1)
        ```

        ## Options
        - [ ] 11
        - [x] 2

        ## Explanation
        Сложение чисел.
    """

    private fun validContent(questions: Int = 2) {
        write("sections.yaml", "sections:\n  - kotlin")
        write("kotlin/section.yaml", "title: Kotlin\ntopics:\n  - basics")
        write("kotlin/basics/topic.yaml", "title: \"Основы\"")
        repeat(questions) { write("kotlin/basics/quiz/q%02d.md".format(it + 1), single("kotlin.basics.q${it + 1}")) }
        write(
            "kotlin/basics/open/o01.md",
            """
            ---
            id: kotlin.basics.o1
            ---
            Что такое val?

            ## Hints
            1. Подумай про изменяемость
            2. Ссылка, а не объект
            3. val — это…

            ## Checklist
            - Ссылку нельзя переназначить
            - Объект может быть изменяемым
            - Отличие от const

            ## Follow-ups
            - Чем val отличается от const val?

            ## Answer
            `val` — неизменяемая ссылка.
            """,
        )
    }

    private fun compile() = ContentCompiler(quizPerTopic = 1..3).compile(root)

    @Test
    fun compilesValidContent() {
        validContent()
        val result = compile()
        assertEquals(emptyList(), result.errors)
        val topic = assertNotNull(result.content).topics.single()
        assertEquals("kotlin.basics", topic.id)
        assertEquals("Основы", topic.title)

        val quiz = topic.quiz.first()
        assertEquals(QuizKind.Single, quiz.kind)
        assertEquals("Что напечатает код?", quiz.prompt)
        assertEquals("println(1 + 1)", quiz.code)
        assertEquals(listOf("11", "2"), quiz.options)
        assertEquals(setOf(1), quiz.correct)

        val open = topic.open.single()
        assertEquals(3, open.hints.size)
        assertEquals(3, open.checklist.size)
        assertEquals(listOf("Чем val отличается от const val?"), open.followUps)
    }

    @Test
    fun trueFalseBecomesTwoOptions() {
        validContent()
        write(
            "kotlin/basics/quiz/q03.md",
            """
            ---
            id: kotlin.basics.q3
            type: true-false
            ---
            data class может быть open.

            ## Answer
            false

            ## Explanation
            data class всегда final.
            """,
        )
        val quiz = assertNotNull(compile().content).topics.single().quiz.last()
        assertEquals(listOf("Верно", "Неверно"), quiz.options)
        assertEquals(setOf(1), quiz.correct)
    }

    @Test
    fun reportsAllErrorsWithFiles() {
        validContent()
        write("kotlin/basics/quiz/q01.md", single("kotlin.basics.q1").replace("- [x] 2", "- [ ] 2"))
        write("kotlin/basics/quiz/q02.md", single("kotlin.basics.q1"))
        File(root, "kotlin/forgotten").mkdirs()

        val result = compile()
        assertNull(result.content)
        val messages = result.errors.map { "${it.file.name}: ${it.message}" }
        assertTrue(messages.any { it.startsWith("q01.md: в single ровно один верный") }, messages.toString())
        assertTrue(messages.any { "папка `forgotten` не указана" in it }, messages.toString())
    }

    @Test
    fun detectsDuplicateIds() {
        validContent()
        write("kotlin/basics/quiz/q02.md", single("kotlin.basics.q1"))
        assertTrue(compile().errors.any { "kotlin.basics.q1" in it.message && "несколько раз" in it.message })
    }

    @Test
    fun enforcesQuestionsPerTopic() {
        validContent(questions = 0)
        assertTrue(compile().errors.any { "вопросов квиза" in it.message })
    }

    @Test
    fun headingsInsideCodeAreNotSections() {
        val doc = MarkdownDocument.parse(
            """
            ---
            id: a.b
            ---
            Вопрос
            ```
            ## not a section
            ```
            ## Answer
            Ответ
            """.trimIndent(),
        )
        assertEquals(setOf("answer"), doc.sections.keys)
        assertTrue("## not a section" in doc.prompt)
    }
}
