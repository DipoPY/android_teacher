# Android Teacher — правила для Claude

Сайт-тренажёр для подготовки к Android-собеседованию. KMP + Compose Multiplatform (Kotlin/Wasm).
Продукт, механика, дизайн и архитектура — в `README.md`. Сначала прочитай нужный раздел README.

## Команды

- `./gradlew build` — всё: тесты, проверка контента, сборка. Должно быть зелёным перед коммитом.
- `./gradlew :tools:content-compiler:compileContent` — проверить контент после правок в `content/`.
- `./gradlew :<module>:jvmTest` — тесты модуля ядра (например, `:core:domain:jvmTest`).

## Модули и зависимости

`app → feature/* → core/ui, core/designsystem, core/domain → core/data → core/platform`, `core/domain → core/srs → core/model`.

- `feature/*` не зависят друг от друга; навигация — только в `app`.
- `core/model`, `core/srs`, `core/domain` — чистый Kotlin, без Compose и браузера.
- Браузерный код — только в `core/platform` (`wasmJsMain`).
- Новые модули подключают convention-плагины из `build-logic` (`at.kmp.library`, `at.serialization`, `at.jvm.app`), а не настраивают Kotlin сами.

## Правила кода

- Логика — в `core/domain`, чистыми функциями над `Progress`, с тестами в `commonTest`.
- Текущая дата — только через параметр `today` / интерфейс `Today`, никогда `Clock.System` в логике.
- `Progress` сериализуется в localStorage: новое поле — только со значением по умолчанию; переименование или смена смысла — новая `schemaVersion` + миграция в `ProgressMigrations`.
- Экраны — `@Composable fun XScreen(state, onIntent)`, состояние — в ViewModel (MVI).
- Цвета, шрифты, отступы — только из дизайн-системы (токены в README → «Дизайн»).
- `allWarningsAsErrors` включён: предупреждения не оставляем.
- Комментарии и KDoc — на русском, коротко; строки интерфейса — на русском.

## Контент

- Вопросы — Markdown в `content/<раздел>/<тема>/quiz|open/`, формат — README → «Формат контента».
- `id` вопроса никогда не меняется (к нему привязан прогресс учеников).
- В теме 10–15 вопросов квиза; у открытого вопроса 3 подсказки и ≥ 3 пункта чек-листа.
- Перед добавлением вопросов проверяй факты; объяснение должно говорить, почему верный вариант верен.
