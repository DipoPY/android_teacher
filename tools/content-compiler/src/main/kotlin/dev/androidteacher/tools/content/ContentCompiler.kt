package dev.androidteacher.tools.content

import dev.androidteacher.core.model.Content
import dev.androidteacher.core.model.OpenQuestion
import dev.androidteacher.core.model.QuizKind
import dev.androidteacher.core.model.QuizQuestion
import dev.androidteacher.core.model.Section
import dev.androidteacher.core.model.Topic
import java.io.File

data class ContentError(val file: File, val message: String)

data class CompileResult(val content: Content?, val errors: List<ContentError>)

/**
 * Собирает `content/` в [Content] и проверяет правила из README («Формат контента»).
 * Ошибки не прерывают разбор: за один запуск видны все проблемы.
 */
class ContentCompiler(
    private val quizPerTopic: IntRange = 10..15,
) {
    private val errors = mutableListOf<ContentError>()

    fun compile(root: File): CompileResult {
        errors.clear()
        val sectionsFile = File(root, "sections.yaml")
        val sectionIds = readList(sectionsFile, "sections") ?: return CompileResult(null, errors.toList())
        checkListedDirs(root, sectionIds, sectionsFile)

        val sections = sectionIds.mapNotNull { id -> section(File(root, id)) }
        checkUniqueIds(sections)
        return CompileResult(if (errors.isEmpty()) Content(sections) else null, errors.toList())
    }

    private fun section(dir: File): Section? {
        val file = File(dir, "section.yaml")
        val yaml = readYaml(file) ?: return null
        val title = yaml.string(file, "title") ?: return null
        val topicDirs = readList(file, "topics", yaml) ?: return null
        checkListedDirs(dir, topicDirs, file)
        val topics = topicDirs.mapNotNull { topic(File(dir, it), sectionId = dir.name) }
        if (topics.isEmpty()) error(file, "в разделе нет ни одной темы")
        return Section(id = dir.name, title = title, topics = topics)
    }

    private fun topic(dir: File, sectionId: String): Topic? {
        val file = File(dir, "topic.yaml")
        val title = readYaml(file)?.string(file, "title") ?: return null
        val quiz = markdownFiles(File(dir, "quiz")).mapNotNull { quizQuestion(it) }
        val open = markdownFiles(File(dir, "open")).mapNotNull { openQuestion(it) }
        if (quiz.size !in quizPerTopic) {
            error(file, "в теме должно быть ${quizPerTopic.first}–${quizPerTopic.last} вопросов квиза, сейчас ${quiz.size}")
        }
        return Topic(id = "$sectionId.${dir.name}", title = title, quiz = quiz, open = open)
    }

    private fun quizQuestion(file: File): QuizQuestion? = parse(file) { doc ->
        val id = doc.requireId()
        val kind = when (val type = doc.frontMatter["type"]) {
            "single" -> QuizKind.Single
            "multiple" -> QuizKind.Multiple
            "true-false" -> QuizKind.TrueFalse
            else -> throw ContentException("`type` должен быть single, multiple или true-false, а не `$type`")
        }
        val prompt = splitCode(doc.prompt)
        if (prompt.text.isBlank()) throw ContentException("пустая формулировка вопроса")
        val explanation = doc.section("Explanation")?.takeIf { it.isNotBlank() }
            ?: throw ContentException("нет раздела `## Explanation`")

        val (options, correct) = if (kind == QuizKind.TrueFalse) {
            val answer = doc.section("Answer")?.trim()?.lowercase()
            val isTrue = when (answer) {
                "true", "верно" -> true
                "false", "неверно" -> false
                else -> throw ContentException("в `## Answer` ожидается true или false")
            }
            listOf("Верно", "Неверно") to setOf(if (isTrue) 0 else 1)
        } else {
            options(doc.section("Options") ?: throw ContentException("нет раздела `## Options`"))
        }

        when (kind) {
            QuizKind.Single -> if (correct.size != 1) throw ContentException("в single ровно один верный вариант `- [x]`")
            QuizKind.Multiple -> if (correct.isEmpty()) throw ContentException("в multiple нужен хотя бы один верный вариант `- [x]`")
            QuizKind.TrueFalse -> Unit
        }
        if (options.size < 2) throw ContentException("нужно минимум два варианта ответа")

        QuizQuestion(id, kind, prompt.text, prompt.code, options, correct, explanation)
    }

    private fun openQuestion(file: File): OpenQuestion? = parse(file) { doc ->
        val id = doc.requireId()
        val prompt = splitCode(doc.prompt)
        if (prompt.text.isBlank()) throw ContentException("пустая формулировка вопроса")
        val hints = numberedItems(doc.section("Hints") ?: throw ContentException("нет раздела `## Hints`"))
        if (hints.size != 3) throw ContentException("подсказок должно быть ровно 3, сейчас ${hints.size}")
        val checklist = bulletItems(doc.section("Checklist") ?: throw ContentException("нет раздела `## Checklist`"))
        if (checklist.size < 3) throw ContentException("в чек-листе должно быть минимум 3 пункта")
        val followUps = doc.section("Follow-ups")?.let { bulletItems(it) }.orEmpty()
        val answer = doc.section("Answer")?.takeIf { it.isNotBlank() } ?: throw ContentException("нет раздела `## Answer`")
        OpenQuestion(id, prompt.text, prompt.code, hints, checklist, followUps, answer)
    }

    private fun options(section: String): Pair<List<String>, Set<Int>> {
        val options = mutableListOf<String>()
        val correct = mutableSetOf<Int>()
        section.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val match = Regex("""^- \[([ xX])]\s+(.+)$""").matchEntire(line)
                ?: throw ContentException("вариант ответа должен выглядеть как `- [ ] текст` или `- [x] текст`, а не `$line`")
            if (match.groupValues[1].isNotBlank()) correct += options.size
            options += match.groupValues[2].trim()
        }
        return options to correct
    }

    private fun MarkdownDocument.requireId(): String {
        val id = frontMatter["id"] ?: throw ContentException("в шапке нет `id`")
        if (!ID_PATTERN.matches(id)) throw ContentException("`id` должен состоять из a-z, 0-9, `-` и точек: `$id`")
        return id
    }

    private fun checkUniqueIds(sections: List<Section>) {
        val ids = sections.flatMap { section -> section.topics.flatMap { topic -> topic.quiz.map { it.id } + topic.open.map { it.id } } }
        ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.forEach { duplicate ->
            errors += ContentError(File("content"), "id `$duplicate` используется несколько раз")
        }
    }

    /** Все подпапки должны быть перечислены в списке — иначе тема «потеряется». */
    private fun checkListedDirs(dir: File, listed: List<String>, listFile: File) {
        listed.filter { !File(dir, it).isDirectory }.forEach { error(listFile, "папка `$it` не найдена") }
        dir.listFiles { file -> file.isDirectory && file.name !in IGNORED_DIRS }
            ?.map { it.name }
            ?.filter { it !in listed }
            ?.forEach { error(listFile, "папка `$it` не указана в списке") }
    }

    private fun markdownFiles(dir: File): List<File> =
        dir.listFiles { file -> file.isFile && file.extension == "md" }?.sortedBy { it.name }.orEmpty()

    private fun <T> parse(file: File, block: (MarkdownDocument) -> T): T? = try {
        block(MarkdownDocument.parse(file.readText()))
    } catch (e: ContentException) {
        error(file, e.message.orEmpty())
        null
    }

    private fun readYaml(file: File): Map<String, Any>? {
        if (!file.isFile) {
            error(file, "файл не найден")
            return null
        }
        return try {
            SimpleYaml.parse(file.readText())
        } catch (e: ContentException) {
            error(file, e.message.orEmpty())
            null
        }
    }

    private fun readList(file: File, key: String, yaml: Map<String, Any>? = readYaml(file)): List<String>? {
        yaml ?: return null
        @Suppress("UNCHECKED_CAST")
        val list = yaml[key] as? List<String>
        if (list.isNullOrEmpty()) error(file, "нужен непустой список `$key:`")
        return list?.takeIf { it.isNotEmpty() }
    }

    private fun Map<String, Any>.string(file: File, key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() } ?: run {
            error(file, "нет поля `$key`")
            null
        }

    private fun error(file: File, message: String) {
        errors += ContentError(file, message)
    }

    private companion object {
        val ID_PATTERN = Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)+$""")
        val IGNORED_DIRS = setOf("quiz", "open")
    }
}
