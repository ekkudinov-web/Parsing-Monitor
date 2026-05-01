package com.minenergo.monitor.data

import android.content.Context
import androidx.core.content.edit
import com.minenergo.monitor.Config
import com.minenergo.monitor.log.AppLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Единое хранилище для всех пользовательских настроек:
 * - список сайтов,
 * - глобальный фильтр,
 * - расписание,
 * - снапшот последних найденных документов и время последней проверки.
 *
 * Хранит всё в одном [Context.getSharedPreferences] под именем
 * `minenergo_state`, JSON-строками. Это просто, не требует миграций
 * Room, и достаточно быстро для 5–50 сайтов.
 */
class PreferencesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val sitesSerializer = ListSerializer(SiteConfig.serializer())
    private val docsSerializer = ListSerializer(Document.serializer())

    init {
        if (!prefs.contains(KEY_SITES)) {
            // Первый запуск — создаём дефолтный сайт.
            saveSites(listOf(defaultSite()))
        }
    }

    // ===== Сайты =====

    fun loadSites(): List<SiteConfig> {
        val raw = prefs.getString(KEY_SITES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(sitesSerializer, raw) }
            .onFailure { AppLogger.w(TAG, "Не удалось прочитать список сайтов", it) }
            .getOrDefault(emptyList())
    }

    fun saveSites(sites: List<SiteConfig>) {
        prefs.edit { putString(KEY_SITES, json.encodeToString(sitesSerializer, sites)) }
    }

    fun upsertSite(site: SiteConfig) {
        val sites = loadSites().toMutableList()
        val index = sites.indexOfFirst { it.id == site.id }
        if (index >= 0) sites[index] = site else sites.add(site)
        saveSites(sites)
    }

    fun deleteSite(siteId: String) {
        saveSites(loadSites().filterNot { it.id == siteId })
    }

    // ===== Глобальный фильтр =====

    fun loadGlobalFilter(): FilterConfig {
        val raw = prefs.getString(KEY_GLOBAL_FILTER, null) ?: return FilterConfig.EMPTY
        return runCatching { json.decodeFromString(FilterConfig.serializer(), raw) }
            .onFailure { AppLogger.w(TAG, "Не удалось прочитать глобальный фильтр", it) }
            .getOrDefault(FilterConfig.EMPTY)
    }

    fun saveGlobalFilter(filter: FilterConfig) {
        prefs.edit {
            putString(KEY_GLOBAL_FILTER, json.encodeToString(FilterConfig.serializer(), filter))
        }
    }

    // ===== Расписание =====

    fun loadSchedule(): ScheduleConfig {
        val raw = prefs.getString(KEY_SCHEDULE, null) ?: return ScheduleConfig()
        return runCatching { json.decodeFromString(ScheduleConfig.serializer(), raw) }
            .onFailure { AppLogger.w(TAG, "Не удалось прочитать настройки расписания", it) }
            .getOrDefault(ScheduleConfig())
    }

    fun saveSchedule(schedule: ScheduleConfig) {
        prefs.edit {
            putString(KEY_SCHEDULE, json.encodeToString(ScheduleConfig.serializer(), schedule))
        }
    }

    // ===== Автопроверка =====

    fun isAutoCheckEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CHECK, true)
    fun setAutoCheckEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CHECK, value) }
    }

    // ===== Снапшот документов =====

    fun loadDocuments(): List<Document> {
        val raw = prefs.getString(KEY_DOCUMENTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(docsSerializer, raw) }
            .onFailure { AppLogger.w(TAG, "Не удалось прочитать снапшот документов", it) }
            .getOrDefault(emptyList())
    }

    fun saveDocuments(documents: List<Document>, checkedAtMillis: Long) {
        prefs.edit {
            putString(KEY_DOCUMENTS, json.encodeToString(docsSerializer, documents))
            putLong(KEY_CHECKED_AT, checkedAtMillis)
        }
    }

    fun lastCheckedAtMillis(): Long = prefs.getLong(KEY_CHECKED_AT, 0L)

    // ===== helpers =====

    private fun defaultSite(): SiteConfig = SiteConfig(
        id = UUID.randomUUID().toString(),
        name = Config.DEFAULT_SITE_NAME,
        url = Config.DEFAULT_SITE_URL,
        dateFromIso = Config.DEFAULT_DATE_FROM.toString(),
        enabled = true,
        sourceType = SourceType.MINENERGO_API,
        apiCode = "pao_rosseti_tsentr_i_privolzhe",
        // 647 — раздел "Информация о проектах ИПР..."
        apiSectionIds = listOf(647L),
    )

    companion object {
        private const val TAG = "PreferencesStore"
        private const val PREFS = "minenergo_state"
        private const val KEY_SITES = "sites_json"
        private const val KEY_GLOBAL_FILTER = "global_filter_json"
        private const val KEY_SCHEDULE = "schedule_json"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_DOCUMENTS = "documents_json"
        private const val KEY_CHECKED_AT = "checked_at_ms"
    }
}
