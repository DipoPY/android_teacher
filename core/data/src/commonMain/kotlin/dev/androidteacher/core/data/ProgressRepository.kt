package dev.androidteacher.core.data

import dev.androidteacher.core.domain.Progress
import dev.androidteacher.core.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Прогресс ученика: живёт в [StateFlow], каждое изменение сразу сохраняется в [store].
 * Повреждённый или слишком новый снимок не теряется — откладывается под ключом [BACKUP_KEY].
 */
class ProgressRepository(private val store: KeyValueStore) {
    private val state = MutableStateFlow(load())

    val progress: StateFlow<Progress> = state.asStateFlow()

    fun update(transform: (Progress) -> Progress) {
        state.update(transform)
        save(state.value)
    }

    /** JSON для бэкапа или переноса в другой браузер. */
    fun export(): String = AppJson.encodeToString(Progress.serializer(), state.value)

    /** Заменяет прогресс импортированным. Невалидный JSON не меняет текущий прогресс. */
    fun import(json: String): Result<Unit> = runCatching { decode(json) }.map { imported ->
        state.value = imported
        save(imported)
    }

    fun reset() {
        state.value = Progress()
        store.remove(KEY)
    }

    private fun load(): Progress {
        val raw = store.get(KEY) ?: return Progress()
        return runCatching { decode(raw) }.getOrElse {
            store.set(BACKUP_KEY, raw)
            Progress()
        }
    }

    private fun decode(raw: String): Progress {
        val json = AppJson.parseToJsonElement(raw).jsonObject
        return AppJson.decodeFromJsonElement(Progress.serializer(), ProgressMigrations.migrate(json))
    }

    private fun save(progress: Progress) {
        store.set(KEY, AppJson.encodeToString(Progress.serializer(), progress))
    }

    companion object {
        const val KEY = "at.progress"
        const val BACKUP_KEY = "at.progress.backup"
    }
}

/**
 * Миграции снимка прогресса: `from` → функция, переводящая JSON версии `from` в версию `from + 1`.
 * Добавляя поле со значением по умолчанию, миграция не нужна; переименовывая или меняя смысл — нужна.
 */
internal object ProgressMigrations {
    private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun migrate(json: JsonObject): JsonObject {
        var version = json["schemaVersion"]?.jsonPrimitive?.int ?: 1
        require(version <= Progress.CURRENT_SCHEMA_VERSION) { "Progress schema $version is newer than the app" }
        var result = json
        while (version < Progress.CURRENT_SCHEMA_VERSION) {
            result = checkNotNull(steps[version]) { "No migration from schema $version" }(result)
            version++
        }
        return result
    }
}
