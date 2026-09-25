package dev.androidteacher.tools.content

/**
 * Минимальный YAML для `sections.yaml`, `section.yaml`, `topic.yaml`:
 * `key: value` и списки `key:` + строки `  - item`. Комментарии `#` на отдельной строке.
 */
internal object SimpleYaml {

    fun parse(text: String): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        var listKey: String? = null
        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            if (trimmed.startsWith("- ")) {
                val key = listKey ?: throw ContentException("строка ${index + 1}: элемент списка без ключа")
                @Suppress("UNCHECKED_CAST")
                (result.getValue(key) as MutableList<String>) += unquote(trimmed.removePrefix("- ").trim())
                return@forEachIndexed
            }

            val colon = trimmed.indexOf(':')
            if (colon <= 0) throw ContentException("строка ${index + 1}: ожидается `ключ: значение`")
            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim()
            if (value.isEmpty()) {
                result[key] = mutableListOf<String>()
                listKey = key
            } else {
                result[key] = unquote(value)
                listKey = null
            }
        }
        return result
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.first() == value.last() && value.first() in "\"'") value.substring(1, value.length - 1) else value
}
