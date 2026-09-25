package dev.androidteacher.tools.content

/**
 * Markdown-файл вопроса: YAML-шапка между `---`, текст до первого `## ` — формулировка,
 * дальше разделы `## Название`. Заголовки внутри блоков кода не считаются разделами.
 */
internal class MarkdownDocument(
    val frontMatter: Map<String, String>,
    val prompt: String,
    val sections: Map<String, String>,
) {
    fun section(name: String): String? = sections[name.lowercase()]

    companion object {
        fun parse(text: String): MarkdownDocument {
            val lines = text.replace("\r\n", "\n").lines()
            if (lines.firstOrNull()?.trim() != "---") throw ContentException("файл должен начинаться с шапки `---`")
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end < 0) throw ContentException("шапка `---` не закрыта")

            val frontMatter = SimpleYaml.parse(lines.subList(1, end + 1).joinToString("\n"))
                .mapValues { (key, value) -> value as? String ?: throw ContentException("в шапке `$key` должно быть строкой") }

            val body = lines.drop(end + 2)
            val sections = linkedMapOf<String, String>()
            var current: String? = null
            val buffer = StringBuilder()
            var prompt = ""
            var inFence = false

            fun flush() {
                val value = buffer.toString().trim()
                if (current == null) prompt = value else sections[current!!] = value
                buffer.clear()
            }

            for (line in body) {
                if (line.trimStart().startsWith("```")) inFence = !inFence
                if (!inFence && line.startsWith("## ")) {
                    flush()
                    val name = line.removePrefix("## ").trim().lowercase()
                    if (name in sections) throw ContentException("раздел `## $name` встречается дважды")
                    current = name
                } else {
                    buffer.appendLine(line)
                }
            }
            if (inFence) throw ContentException("блок кода ``` не закрыт")
            flush()
            return MarkdownDocument(frontMatter, prompt, sections)
        }
    }
}

/** Формулировка без блока кода и сам код (первый блок ```), если он есть. */
internal data class PromptParts(val text: String, val code: String?)

internal fun splitCode(markdown: String): PromptParts {
    val lines = markdown.lines()
    val start = lines.indexOfFirst { it.trimStart().startsWith("```") }
    if (start < 0) return PromptParts(markdown.trim(), null)
    val end = (start + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith("```") }
        ?: throw ContentException("блок кода ``` не закрыт")
    val rest = lines.subList(end + 1, lines.size)
    if (rest.any { it.trimStart().startsWith("```") }) throw ContentException("в формулировке допускается только один блок кода")
    val text = (lines.subList(0, start) + rest).joinToString("\n").trim()
    val code = lines.subList(start + 1, end).joinToString("\n").trimEnd()
    return PromptParts(text, code)
}

/** Пункты маркированного списка `- текст`. */
internal fun bulletItems(section: String): List<String> = section.lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
        if (!line.startsWith("- ")) throw ContentException("ожидается пункт списка `- …`, а не `$line`")
        line.removePrefix("- ").trim()
    }

/** Пункты нумерованного списка `1. текст`. */
internal fun numberedItems(section: String): List<String> = section.lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
        val match = Regex("""^\d+\.\s+(.+)$""").matchEntire(line)
            ?: throw ContentException("ожидается пункт нумерованного списка `1. …`, а не `$line`")
        match.groupValues[1].trim()
    }

internal class ContentException(message: String) : Exception(message)
