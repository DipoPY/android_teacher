package dev.androidteacher.core.data

import kotlinx.serialization.json.Json

/** Общие настройки JSON: новые поля в контенте и прогрессе не ломают старые версии. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}
