import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Библиотека ядра: общий код для браузера (wasmJs) и JVM.
// JVM-таргет нужен для быстрых unit-тестов commonTest и для tools/content-compiler.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    // Без browser()/nodejs(): библиотеке не нужно окружение запуска, его задаёт app/web.
    // Так не создаются wasm-тесты и их npm-инструменты; логика тестируется на JVM.
    wasmJs()

    compilerOptions {
        allWarningsAsErrors = true
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
