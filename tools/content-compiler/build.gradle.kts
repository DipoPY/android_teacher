plugins {
    id("at.jvm.app")
}

dependencies {
    implementation(projects.core.model)
}

application {
    mainClass = "dev.androidteacher.tools.content.MainKt"
}

val contentDir = rootProject.layout.projectDirectory.dir("content")
val contentJson = layout.buildDirectory.file("content/content.json")

/** Проверяет `content/` и собирает `content.json`. Результат забирает приложение. */
val compileContent by tasks.registering(JavaExec::class) {
    group = "content"
    description = "Validates content/ and writes content.json"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    inputs.dir(contentDir)
    outputs.file(contentJson)
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(contentDir.asFile.absolutePath, contentJson.get().asFile.absolutePath)
}

tasks.check {
    dependsOn(compileContent)
}
