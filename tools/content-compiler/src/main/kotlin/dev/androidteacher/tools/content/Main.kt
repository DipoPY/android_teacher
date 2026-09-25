package dev.androidteacher.tools.content

import dev.androidteacher.core.model.Content
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

/** `content-compiler <папка content> <выходной content.json>` */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: content-compiler <content dir> <output json>" }
    val root = File(args[0])
    val output = File(args[1])

    val result = ContentCompiler().compile(root)
    result.errors.forEach { System.err.println("${it.file.relativeToOrSelf(root.parentFile)}: ${it.message}") }
    val content = result.content
    if (content == null) {
        System.err.println("Контент содержит ошибок: ${result.errors.size}")
        exitProcess(1)
    }

    output.parentFile.mkdirs()
    output.writeText(Json.encodeToString(Content.serializer(), content))
    val questions = content.topics.sumOf { it.quiz.size + it.open.size }
    println("content.json: разделов ${content.sections.size}, тем ${content.topics.size}, вопросов $questions")
}
