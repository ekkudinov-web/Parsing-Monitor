package com.minenergo.monitor

import java.time.LocalDate
import java.time.ZoneId

/**
 * Глобальные дефолты приложения. Большая часть значений теперь живёт
 * в пользовательских настройках (см. data/AppPreferences.kt) — здесь
 * остаются только начальные значения и неизменяемые ограничения.
 */
object Config {

    // === Дефолтные значения для первого запуска ===
    const val DEFAULT_SITE_NAME: String = "Россети Центр и Приволжье"
    const val DEFAULT_SITE_URL: String =
        "https://minenergo.gov.ru/industries/power-industry/investment-programs/" +
            "pao_rosseti_tsentr_i_privolzhe"
    val DEFAULT_DATE_FROM: LocalDate = LocalDate.of(2026, 4, 1)

    // === Расписание по умолчанию ===
    const val DEFAULT_INTERVAL_MINUTES: Long = 60L
    const val DEFAULT_WINDOW_ENABLED: Boolean = true
    const val DEFAULT_WINDOW_FROM_HOUR: Int = 8
    const val DEFAULT_WINDOW_TO_HOUR: Int = 22
    val DEFAULT_TIMEZONE: ZoneId = ZoneId.of("Europe/Moscow")

    /**
     * Минимально допустимая периодичность WorkManager — 15 минут (ограничение Android).
     * Меньшие интервалы потребовали бы foreground-service с постоянным уведомлением,
     * что в текущем дизайне не используется.
     */
    const val MIN_INTERVAL_MINUTES: Long = 15L
    const val MAX_INTERVAL_MINUTES: Long = 24L * 60L

    // === Сетевые ограничения ===
    const val REQUEST_TIMEOUT_SECONDS: Long = 30L
    const val RETRY_COUNT: Int = 3
    const val USER_AGENT: String = "Mozilla/5.0 (compatible; MinenergoMonitorBot/1.0)"

    // === Ограничения скачивания и распаковки ===
    const val MAX_DOWNLOAD_FILE_SIZE_MB: Long = 500L
    const val MAX_ARCHIVE_SIZE_MB: Long = 500L
    const val MAX_UNPACKED_TOTAL_SIZE_MB: Long = 2048L
    const val MAX_ARCHIVE_FILES_COUNT: Int = 1000
    const val MAX_ARCHIVE_DEPTH: Int = 10

    val DANGEROUS_EXTENSIONS: Set<String> = setOf(
        ".exe", ".bat", ".cmd", ".ps1", ".sh", ".vbs", ".js",
        ".jar", ".scr", ".msi", ".com", ".apk", ".dex"
    )

    // === Имена внутренних подсистем ===
    const val NOTIFICATION_CHANNEL_ID: String = "minenergo_documents"
    const val NOTIFICATION_CHANNEL_NAME: String = "Уведомления о документах"
    const val NOTIFICATION_ID_NEW_DOCS: Int = 1001
    const val WORK_NAME: String = "minenergo_periodic_check"

    const val DOWNLOADS_SUBDIR: String = "downloads"
    const val LOGS_SUBDIR: String = "logs"
    const val LOG_FILE: String = "minenergo.log"
    const val LOG_MAX_BYTES: Long = 5L * 1024L * 1024L
}
