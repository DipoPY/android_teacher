package dev.androidteacher.core.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Источник текущей даты. Вся логика получает дату отсюда, чтобы её можно было подменить в тестах. */
fun interface Today {
    fun date(): LocalDate
}

class SystemToday(private val timeZone: TimeZone = TimeZone.currentSystemDefault()) : Today {
    override fun date(): LocalDate = Clock.System.todayIn(timeZone)
}
