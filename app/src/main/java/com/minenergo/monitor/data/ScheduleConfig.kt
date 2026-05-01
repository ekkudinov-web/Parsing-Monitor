package com.minenergo.monitor.data

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleConfig(
    /** Интервал между проверками в минутах. Минимум 15 (ограничение WorkManager). */
    val intervalMinutes: Long = 60,
    /** Включено ли ограничение по часам активности. */
    val windowEnabled: Boolean = true,
    /** Час начала окна активности (включительно), 0..23. */
    val windowFromHour: Int = 8,
    /** Час конца окна активности (исключительно), 1..24. */
    val windowToHour: Int = 22,
    /** Зона. Хранится как строка ZoneId, например "Europe/Moscow". */
    val timezoneId: String = "Europe/Moscow",
)
