# Android Teacher

Веб-тренажёр для подготовки к собеседованию на Android-разработчика (уровень до senior, без System Design).
Написан на **Kotlin Multiplatform + Compose Multiplatform (Kotlin/Wasm)**, работает в браузере без установки.

> Статус: проектирование. Этот документ — ТЗ, дизайн и архитектура. Код появится по плану из раздела [«Как делать»](#как-делать).

**Макеты всех экранов:** https://claude.ai/artifact/6nLNDZzXnQSU3AfiscffEa (приватная ссылка, доступ выдаёт владелец через Share).

---

## Содержание

1. [Продукт](#продукт)
2. [Механика обучения](#механика-обучения)
3. [Научная база](#научная-база)
4. [Дизайн](#дизайн)
5. [Архитектура](#архитектура)
6. [Формат контента](#формат-контента)
7. [Как делать](#как-делать)
8. [Разработка](#разработка)

---

## Продукт

**Для кого:** сначала для автора, потом для учеников. Регистрации нет, прогресс хранится в браузере.
**Язык интерфейса:** русский.
**Платформа:** сайт (Compose Multiplatform for Web). Архитектура позволяет позже добавить Desktop и Android без переписывания.

### MVP: два режима

| Режим | Суть |
|---|---|
| **Квизы** (геймифицированы) | Разделы → темы → 10–15 вопросов. Тема открывает следующую после идеального прохождения. Интервальные повторения, серия дней, XP, ежедневный микс |
| **Таймер** | Вопрос сверху, таймер по центру, 3 закрытые подсказки снизу, запись ответа с микрофона. По окончании времени — разбор: чек-лист аспектов «было / не было» и прослушивание записи |

### Экраны

| # | Экран | Макет | Что на нём |
|---|---|---|---|
| 1 | Первый вход | `Welcome` | Оффер, 3 фичи, «Начать» / «Попробовать таймер» |
| 2 | Главная | `Main` | Баннер «Повторение на сегодня», серия дней, сетка разделов с кольцами прогресса, закрытый «Ежедневный микс» |
| 3 | Раздел | `Section` | Баннер раздела, путь по темам (пройдена / текущая / закрыта), статистика, слабые места |
| 4 | Старт темы | `Topic` | Модалка: число вопросов, время, XP, прогресс, правила |
| 5 | Квиз — верно | `Quiz` | Прогресс-бар, вопрос (в т.ч. с кодом), варианты, зелёная панель с объяснением |
| 6 | Квиз — ошибка | `QuizWrong` | Красная панель, объяснение, «вопрос вернётся в конец урока» |
| 7 | Тема пройдена | `QuizResult` | Точность, серия, XP, открытая тема, рост процента раздела |
| 8 | Повторение | `Review` | Вопросы на сегодня по темам, прогноз на 7 дней, как работают интервалы, прогресс до микса |
| 9 | Профиль | `Profile` | Серия, XP, темы, ответы вслух, календарь активности, прогресс разделов, достижения, настройки |
| 10 | Таймер — выбор | `TimerHome` | Разделы-источники, время на ответ, микрофон, история ответов |
| 11 | Таймер — ответ | `Timer` | Вопрос, кольцо таймера, 3 подсказки, индикатор записи |
| 12 | Таймер — разбор | `TimerReview` | Таймер 00:00 + «Разбор», плеер записи, чек-лист аспектов, эталонный ответ |

Навигация (верхняя панель на всех экранах, кроме квиза и таймера): **Учиться · Повторение (счётчик) · Таймер · Профиль**.

```
Первый вход → Главная → Раздел → Старт темы → Квиз → Результат
                 ├→ Повторение → Квиз → Результат
                 ├→ Таймер: выбор → Ответ → Разбор
                 └→ Профиль
```

---

## Механика обучения

### Структура знаний

```
Раздел (Kotlin)
 └─ Тема (data class)                 ← 10–15 вопросов квиза + вопросы для таймера
     └─ Вопрос (id стабилен навсегда)
```

### Прохождение темы

1. Вопросы темы идут в фиксированном порядке (от простого к сложному).
2. Ошибка → показываем объяснение, вопрос **уходит в конец очереди урока**.
3. Тема **пройдена**, когда на каждый вопрос дан верный ответ. Начинать заново не нужно — это mastery learning.
4. После прохождения открывается следующая тема раздела. Прерванный урок можно продолжить (сохраняем прогресс урока).
5. Пройденную тему можно перепройти — это даёт повторение, но не XP за прохождение.

### Прогресс раздела

`прогресс = пройденные темы / все темы раздела`. 100% — раздел пройден и отмечается на главной.
Процент не откатывается (решение по умолчанию: мотивация важнее «честности», а забывание и так видно по повторениям).

### Интервальные повторения

У каждого отвеченного вопроса есть состояние карточки (см. [SRS](#srs-планировщик)) и дата следующего показа `due`.

| Ответ в квизе | Оценка для планировщика |
|---|---|
| Неверно | `Again` — вопрос вернётся завтра |
| Верно, но в этом уроке уже была ошибка | `Hard` |
| Верно с первой попытки | `Good` |

**Повторение на сегодня** = карточки с `due ≤ сегодня`, у которых были ошибки (`lapses > 0`) или которые давно не повторялись.
Сортировка: больше ошибок → ниже вероятность вспомнить → раньше срок. Лимит **20** за сессию, минимум не навязываем (если к повторению 6 — показываем 6).
Ошибка в повторении — вопрос снова в конец очереди сессии, как в уроке.

### Ежедневный микс

Открывается, когда **все темы всех разделов пройдены**. Каждый день — 1 урок из **30 вопросов**:
сначала просроченные карточки, затем карточки с наименьшей вероятностью вспомнить, темы перемешаны (интерливинг).
Контент добавился → появились новые темы → микс снова закрыт, пока их не пройти.

### Серия дней (streak)

- День засчитан, если за календарный день (локальное время) выполнено хотя бы одно: пройден урок темы, завершено повторение, пройден микс, сделан разбор в таймере.
- **Заморозка:** 1 штука за каждые 7 дней подряд, максимум 2. Пропущенный день автоматически тратит заморозку.
- Храним: текущая серия, рекорд, дата последней активности, число заморозок.

### XP и достижения

| Действие | XP |
|---|---|
| Тема пройдена | +40 |
| Повторение завершено | +20 |
| Ежедневный микс | +50 |
| Разбор в таймере | +15 |

Достижения (MVP): «Первая тема», «7 дней подряд», «30 дней подряд», «5 тем без ошибок», «Раздел на 100%», «50 ответов вслух».

### Таймер

1. Выбор источника (все разделы / конкретные), времени (1 / 2 / 3 / 5 мин / своё), записи микрофона.
2. Экран ответа: подсказки открываются по нажатию (1 → 2 → 3: намёк → ключевые слова → начало ответа). Подсказки **не снижают** оценку, но сохраняются в истории.
3. Время вышло → кнопка **«Разбор»**: чек-лист аспектов, плеер записи, эталонный ответ, follow-up вопрос.
4. Результат = доля отмеченных аспектов. Он идёт в историю и в планировщик (`<40%` Again, `<70%` Hard, `<90%` Good, иначе Easy) для открытых вопросов.

---

## Научная база

| Принцип | Исследования | Как используем |
|---|---|---|
| **Эффект тестирования** — вспоминать эффективнее, чем перечитывать | Roediger & Karpicke, 2006, *Psychological Science*; Karpicke & Roediger, 2008, *Science* | Всё обучение — через вопросы, а не конспекты. Таймер — свободное припоминание |
| **Интервальное повторение** — распределённая практика лучше зубрёжки | Cepeda et al., 2006, *Psychological Bulletin* (мета-анализ); кривая забывания Эббингауза | Повторение на сегодня, растущие интервалы |
| **Оптимальные интервалы** | Ye, Su & Cao, 2022, *KDD* — алгоритм FSRS | Планировщик FSRS с параметрами по умолчанию |
| **Интерливинг** — перемешивание тем | Rohrer & Taylor, 2007, *Instructional Science* | Ежедневный микс, повторение из разных тем |
| **Обратная связь сразу** | Dunlosky et al., 2013, *Psychological Science in the Public Interest* (обзор техник обучения) | Объяснение после каждого ответа, включая неверные варианты |
| **Самообъяснение** | там же | Ответ вслух в таймере + сверка с чек-листом |

---

## Дизайн

Минималистичный Duolingo: крупные объёмные кнопки, путь по темам, серия дней — без маскотов и лишних анимаций.
Цвета — электрический синий с обложки «Фабрика офферов» + салатовый акцент.

### Цвета (токены)

| Токен | HEX | Где |
|---|---|---|
| `background` | `#F3F6FC` | Фон страницы |
| `surface` | `#FFFFFF` | Карточки |
| `outline` | `#E1E7F3` | Рамки карточек, «тень» светлых кнопок |
| `ink` | `#0A1A3F` | Основной текст, код-блок |
| `inkMuted` | `#4A5878` | Вторичный текст |
| `primary` | `#1F5EFF` | Кнопки, прогресс, активное |
| `primaryDeep` | `#0B3BD6` | Нижняя «3D-тень» синих кнопок |
| `primaryPale` | `#DCE7FF` | Активный пункт меню, подсветка, фон колец |
| `primarySoft` | `#9DB8FF` | Рамки активного, второстепенные столбцы |
| `lime` | `#B8F23A` | Акцент на синем (прогресс в баннере, иконки наград) |
| `success` / `successBg` / `successText` | `#7DB00E` / `#EEFBD2` / `#3F6300` | Верный ответ |
| `error` / `errorBg` / `errorText` | `#E5484D` / `#FFE9E6` / `#9A1C16` | Неверный ответ |
| `streak` | `#FF8A3D` | Серия дней, бейдж счётчика |
| `locked` / `lockedDeep` / `lockedIcon` | `#DDE4F2` / `#C3CDE2` / `#7A88A6` | Закрытые темы |

Текст на цветном фоне держит контраст ≥ 4.5:1. Верно/неверно различаются не только цветом, но и иконкой (галочка / крестик).
Тёмная тема — после MVP (токены сразу заводим как `ColorScheme`, чтобы добавить её одной реализацией).

### Типографика

| Стиль | Шрифт | Размер / вес | Где |
|---|---|---|---|
| `display` | Unbounded | 34–54 / 800, КАПС | Баннеры, «ТЕМА ПРОЙДЕНА» |
| `title` | Unbounded | 18–28 / 700 | Заголовки экранов и карточек |
| `body` | Manrope | 15–16 / 600 | Основной текст |
| `label` | Manrope | 13–15 / 800 | Кнопки (КАПС, трекинг 0.04em), чипы |
| `caption` | Manrope | 12–13 / 700 | Подписи, мета |
| `code` | JetBrains Mono | 15 / 400 | Код в вопросах |

Все три шрифта под OFL, поддерживают кириллицу. В Compose for Web шрифты **не берутся из CSS** — кладём `.ttf` в `composeResources/font`.

### Форма и отступы

- Сетка отступов кратна 4: `4, 8, 12, 16, 20, 24, 32, 40, 48`.
- Скругления: чипы 10–12, кнопки 14–16, карточки 18–24, круги тем — полный круг.
- **3D-кнопка:** заливка + сплошная тень вниз на 4–6 px цветом `*Deep`, без размытия. При нажатии тень → 0, кнопка сдвигается на эту же высоту вниз.
- Карточка: `surface`, рамка 2 px `outline`, без теней (у кликабельных — тень 4 px `outline`).
- Контент центрируется, ширина рабочей области 1184 px (1280 − 2×48). Экран квиза — колонка 720 px.

### Компоненты дизайн-системы

`AtButton` (primary / secondary / success / danger, 3D), `AtCard`, `AtChip`, `AtProgressBar`, `AtProgressRing`, `TopicNode` (done / current / locked), `StreakWeek`, `AnswerOption` (default / selected / correct / wrong / missed), `FeedbackPanel` (success / error), `CodeBlock` (подсветка Kotlin), `HintCard` (closed / opened), `TimerRing`, `ChecklistItem`, `AudioPlayer`, `TopBar`, `ActivityHeatmap`, `Badge`.

Иконки — векторные линейные (stroke 2–2.4), в `composeResources/drawable`. Эмодзи не используем.

---

## Архитектура

### Технологии

| Что | Выбор |
|---|---|
| Язык / платформа | Kotlin Multiplatform, таргет `wasmJs` (+ `jvm` в core-модулях — для быстрых тестов) |
| UI | Compose Multiplatform (Material 3 как база, свой дизайн поверх) |
| Навигация | `org.jetbrains.androidx.navigation:navigation-compose`, type-safe маршруты, привязка к истории браузера |
| Состояние | `androidx.lifecycle` ViewModel (multiplatform) + `StateFlow`, паттерн MVI |
| DI | Koin |
| Сериализация | kotlinx.serialization (JSON) |
| Время | kotlinx-datetime |
| Хранение прогресса | `localStorage` за интерфейсом `KeyValueStore` |
| Аудио | `MediaRecorder` через JS-interop за интерфейсом `AudioRecorder` |
| Тесты | kotlin.test, Turbine, `kotlinx-coroutines-test` |
| Сборка | Gradle + version catalog + convention-плагины в `build-logic` |
| CI/CD | GitHub Actions: `check` на PR, деплой `wasmJsBrowserDistribution` на GitHub Pages из `main` |

> Compose for Web рисует в `<canvas>`: нет SEO, текст по умолчанию не выделяется (используем `SelectionContainer` для ответов и кода), нужен браузер с WasmGC (актуальные Chrome, Firefox, Safari 18.2+). Для учебного приложения по ссылке это приемлемо.

### Модули

```
android_teacher/
├── gradle/libs.versions.toml
├── build-logic/convention/          # плагины: at.kmp.library, at.compose.feature, at.wasm.app
├── content/                         # вопросы в Markdown (источник правды)
├── tools/content-compiler/          # JVM: парсит content/, валидирует, пишет content.json
├── core/
│   ├── model/                       # чистые данные: Section, Topic, Question, CardState…
│   ├── srs/                         # FSRS-планировщик, без зависимостей
│   ├── domain/                      # правила: прохождение темы, очередь урока, повторение, микс, серия, XP
│   ├── data/                        # репозитории: ContentRepository, ProgressRepository
│   ├── platform/                    # KeyValueStore, AudioRecorder, Clock — interface + wasm-реализации
│   ├── designsystem/                # тема, токены, шрифты, иконки, At*-компоненты
│   └── ui/                          # общие куски экранов: TopBar, CodeBlock, пустые состояния
├── feature/
│   ├── onboarding/                  # Welcome
│   ├── home/                        # Main
│   ├── section/                     # Section + Topic (модалка)
│   ├── quiz/                        # Quiz, QuizResult — используется темой, повторением и миксом
│   ├── review/                      # Review
│   ├── timer/                       # TimerHome, Timer, TimerReview
│   └── profile/                     # Profile, настройки
└── app/web/                         # точка входа wasmJs, NavHost, Koin-граф, index.html
```

**Правила зависимостей:**

```
app ─→ feature/* ─→ core/ui, core/designsystem, core/domain
                    core/domain ─→ core/data ─→ core/platform
                    core/domain ─→ core/srs ─→ core/model
```

- `feature` не зависят друг от друга. Переходы между экранами — только в `app` через колбэки навигации.
- `core/model`, `core/srs`, `core/domain` — без Compose и без платформенного кода. Здесь вся логика и основные тесты.
- Платформенное (браузер) — только в `core/platform` (`wasmJsMain`).

### Слои внутри фичи (MVI)

```kotlin
// feature/quiz
data class QuizState(
    val progress: Float,
    val question: QuestionUi,
    val selected: Set<Int>,
    val feedback: Feedback?,          // null — ещё не ответили
)

sealed interface QuizIntent {
    data class Select(val index: Int) : QuizIntent
    data object Check : QuizIntent
    data object Next : QuizIntent
}

sealed interface QuizEffect {
    data class Finished(val result: LessonResult) : QuizEffect
}

class QuizViewModel(
    private val session: LessonSession,  // из core/domain
) : ViewModel() {
    val state: StateFlow<QuizState>
    val effects: Flow<QuizEffect>
    fun onIntent(intent: QuizIntent)
}
```

Экран = `@Composable fun QuizScreen(state, onIntent)` без ViewModel внутри, поэтому его легко смотреть в превью и тестировать.

### Доменная модель

```kotlin
// core/model
data class Section(val id: SectionId, val title: String, val order: Int, val topics: List<Topic>)
data class Topic(val id: TopicId, val title: String, val order: Int,
                 val quiz: List<QuizQuestion>, val open: List<OpenQuestion>)

sealed interface QuizQuestion {
    val id: QuestionId; val prompt: String; val code: String?; val explanation: String
    data class Single(...,   val options: List<String>, val correct: Int) : QuizQuestion
    data class Multiple(..., val options: List<String>, val correct: Set<Int>) : QuizQuestion
    data class TrueFalse(..., val correct: Boolean) : QuizQuestion
}

data class OpenQuestion(
    val id: QuestionId, val prompt: String,
    val hints: List<String>,            // ровно 3
    val checklist: List<String>,        // аспекты для разбора
    val answer: String,                 // эталон, Markdown
    val followUps: List<String>,
)

data class CardState(
    val stability: Double, val difficulty: Double,
    val due: LocalDate, val lastReview: LocalDate?,
    val reps: Int, val lapses: Int,
)
```

### Ключевые доменные сервисы (`core/domain`)

| Класс | Отвечает за |
|---|---|
| `LessonSession` | Очередь урока: ошибка → в конец; «пройдено» = все верны. Общая для темы, повторения и микса |
| `TopicProgression` | Какие темы пройдены/открыты, процент раздела и общий |
| `ReviewQueueBuilder` | Повторение на сегодня: фильтр, сортировка, лимит 20 |
| `DailyMixBuilder` | 30 вопросов, доступность микса |
| `StreakTracker` | Серия, рекорд, заморозки по датам |
| `XpCalculator`, `AchievementChecker` | Награды |
| `TimerScoring` | Процент чек-листа → оценка планировщика |

Все сервисы получают `Clock` / `TimeZone` через конструктор — тесты не зависят от реальной даты.

### SRS-планировщик

`core/srs` — интерфейс `Scheduler` и реализация `FsrsScheduler` (FSRS с параметрами по умолчанию).

```kotlin
enum class Grade { Again, Hard, Good, Easy }

interface Scheduler {
    fun review(card: CardState?, grade: Grade, today: LocalDate): CardState
    fun retrievability(card: CardState, today: LocalDate): Double
}
```

Интерфейс оставляет возможность заменить алгоритм (например, простой Leitner для отладки) без изменений в домене. Тесты — по эталонным значениям открытой реализации FSRS.

### Хранение прогресса

Один JSON-снимок в `localStorage` под ключом `at.progress`:

```json
{
  "schemaVersion": 1,
  "cards":   { "kotlin.data-class.q01": { "stability": 3.2, "difficulty": 5.1, "due": "2026-09-28", "reps": 2, "lapses": 1 } },
  "topics":  { "kotlin.data-class": { "passed": true, "passedAt": "2026-09-20" } },
  "lessons": { "kotlin.extensions": { "queue": ["…"], "done": ["…"] } },
  "streak":  { "current": 12, "best": 21, "lastActive": "2026-09-25", "freezes": 1 },
  "xp": 1240,
  "achievements": ["first-topic", "streak-7"],
  "timerHistory": [ { "questionId": "…", "at": "…", "score": 0.67, "hintsUsed": 1 } ],
  "settings": { "timerSeconds": 180, "recordAudio": true }
}
```

- `schemaVersion` + цепочка миграций в `ProgressRepository`.
- Экспорт/импорт снимка файлом — в настройках профиля (бэкап и перенос между браузерами).
- Записи с микрофона в MVP живут только до перезагрузки страницы (в памяти). Сохранение в IndexedDB — после MVP.
- Прогресс ссылается на вопросы по `id`, поэтому **id нельзя менять**. Удалённые из контента id игнорируются.

### Задел на будущее

| Что | Где подключается |
|---|---|
| Аккаунты и синхронизация | Новая реализация `ProgressRepository` (сервер) вместо `localStorage` |
| LLM-оценка ответа в таймере | Интерфейс `AnswerEvaluator`: сейчас — ручной чек-лист, потом — речь в текст + LLM |
| LLM follow-up вопросы | Там же, `FollowUpProvider` |
| Desktop / Android | Новые таргеты в `app/`, `core/platform` получает `jvmMain` / `androidMain` |
| Мини-игры | Новые `feature/games/*`, те же карточки и планировщик |

---

## Формат контента

Вопросы живут в репозитории в `content/` — их удобно писать руками и генерировать с помощью Claude, а диффы читаются в PR.

```
content/
├── sections.yaml                       # порядок и названия разделов
└── kotlin/
    ├── section.yaml                    # порядок тем раздела
    └── data-class/
        ├── topic.yaml                  # название, порядок вопросов
        ├── quiz/
        │   ├── q01-equals.md
        │   └── q02-copy.md
        └── open/
            └── o01-what-is-data-class.md
```

**Вопрос квиза:**

````markdown
---
id: kotlin.data-class.q01
type: single            # single | multiple | true-false
---
Что напечатает этот код?

```kotlin
data class User(val name: String) { var age: Int = 0 }
val a = User("Ann").apply { age = 20 }
val b = User("Ann").apply { age = 30 }
println(a == b)
```

## Options
- [x] true
- [ ] false
- [ ] Ошибка компиляции
- [ ] Зависит от hashCode()

## Explanation
equals() и hashCode() генерируются только по свойствам из primary-конструктора. Поле age в сравнении не участвует.
````

**Открытый вопрос (таймер):**

```markdown
---
id: coroutines.cancellation.o01
---
Как работает отмена корутин и почему её называют кооперативной?

## Hints
1. Подумай, кто и где проверяет, что корутину отменили.
2. isActive, ensureActive(), yield(), CancellationException.
3. Отмена — это флаг в Job, который код должен сам проверять…

## Checklist
- Отмена кооперативная: код сам проверяет отмену
- Suspend-функции kotlinx проверяют отмену и бросают CancellationException
- isActive / ensureActive() / yield() для долгих вычислений
- Отмена распространяется от родителя к детям
- CancellationException нельзя проглатывать
- withContext(NonCancellable) для очистки в finally

## Follow-ups
- Что будет, если поймать CancellationException в catch (e: Exception)?

## Answer
Развёрнутый эталонный ответ с примерами кода…
```

**`tools/content-compiler`** (запускается Gradle-задачей перед сборкой приложения и в CI):
- проверяет обязательные поля, уникальность `id`, `id` совпадает с путём, в `single` ровно один `[x]`, в `multiple` ≥ 1, у открытого вопроса ровно 3 подсказки и ≥ 3 пункта чек-листа, в теме 10–15 вопросов квиза;
- собирает всё в `content.json` и кладёт в ресурсы `core/data`;
- падает с понятной ошибкой `content/kotlin/data-class/quiz/q02-copy.md: нет правильного варианта`.

---

## Как делать

План по этапам. Каждый этап заканчивается рабочим сайтом на GitHub Pages и зелёным CI.

| Этап | Что делаем | Готово, когда |
|---|---|---|
| **0. Каркас** | Gradle, version catalog, `build-logic`, пустые модули, `app/web` с «Hello», CI (`check`) и деплой на Pages, `CLAUDE.md` с правилами | По ссылке открывается страница, PR проверяются |
| **1. Дизайн-система** | Токены, шрифты, иконки, `At*`-компоненты, страница-витрина компонентов (только в dev-сборке) | Все компоненты из списка выше выглядят как на макетах |
| **2. Контент** | Формат, `content-compiler` с валидацией и тестами, раздел Kotlin с 2–3 темами | `./gradlew compileContent` собирает JSON, ошибки ловятся |
| **3. Квиз и прохождение** | `LessonSession`, `TopicProgression`, экраны Main / Section / Topic / Quiz / QuizResult, сохранение в `localStorage` | Можно пройти тему, открывается следующая, прогресс переживает перезагрузку |
| **4. Повторения** | `core/srs` (FSRS), `ReviewQueueBuilder`, экран Review, ежедневный микс | Ошибки возвращаются в повторение по расписанию (проверяется тестами со сдвигом даты) |
| **5. Геймификация** | Серия дней, заморозки, XP, достижения, Profile, экспорт/импорт | Цифры в шапке и профиле живые |
| **6. Таймер** | `AudioRecorder` (MediaRecorder), TimerHome / Timer / TimerReview, история, `TimerScoring` | Можно ответить вслух, послушать себя и отметить аспекты |
| **7. Полировка** | Welcome, пустые состояния, клавиатура (1–4 для вариантов, Enter — дальше), наполнение контента | Готово для учеников |

### Правила работы с кодом

- Логика — в `core/domain` с тестами, экраны — «глупые» функции `(state, onIntent)`.
- Новая фича = новый модуль `feature/*` с convention-плагином `at.compose.feature`.
- Строки интерфейса — в `composeResources/values/strings.xml` (сразу готовы к локализации).
- Никакой даты «из воздуха»: только через `Clock`.
- PR маленькие, один этап/подэтап — один PR. CI должен быть зелёным.
- Новый вопрос — только файл в `content/`, код не трогаем.

---

## Разработка

> Команды появятся после этапа 0.

```bash
./gradlew :app:web:wasmJsBrowserDevelopmentRun   # dev-сервер с hot reload
./gradlew compileContent                         # проверить и собрать контент
./gradlew check                                  # все тесты и проверки
./gradlew :app:web:wasmJsBrowserDistribution     # продакшен-сборка в app/web/build/dist
```

Требования: JDK 17+, актуальный Chrome/Firefox. Для записи микрофона сайт должен открываться по `https` или `localhost`.
