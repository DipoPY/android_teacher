package dev.androidteacher.core.platform

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * `localStorage` браузера. Доступ может бросать исключение (приватный режим, запрет cookie) —
 * тогда работаем без сохранения, а не падаем.
 */
class LocalStorageKeyValueStore : KeyValueStore {
    override fun get(key: String): String? = runCatching { localStorage[key] }.getOrNull()

    override fun set(key: String, value: String) {
        runCatching { localStorage[key] = value }
    }

    override fun remove(key: String) {
        runCatching { localStorage.removeItem(key) }
    }
}
