package dev.androidteacher.core.platform

/** Простое строковое хранилище. В браузере — `localStorage`. */
interface KeyValueStore {
    fun get(key: String): String?

    fun set(key: String, value: String)

    fun remove(key: String)
}

class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val values = initial.toMutableMap()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values -= key
    }
}
