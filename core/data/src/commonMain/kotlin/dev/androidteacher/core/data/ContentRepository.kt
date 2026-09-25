package dev.androidteacher.core.data

import dev.androidteacher.core.model.Content
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Загружает `content.json` один раз и держит в памяти.
 *
 * @param source читает сырой JSON (в приложении — из Compose-ресурсов).
 */
class ContentRepository(private val source: suspend () -> String) {
    private val mutex = Mutex()
    private var cached: Content? = null

    suspend fun content(): Content = mutex.withLock {
        cached ?: AppJson.decodeFromString(Content.serializer(), source()).also { cached = it }
    }
}
