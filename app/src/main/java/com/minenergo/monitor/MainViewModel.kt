package com.minenergo.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.DocumentFilter
import com.minenergo.monitor.data.DownloadResult
import com.minenergo.monitor.data.FilterConfig
import com.minenergo.monitor.data.PreferencesStore
import com.minenergo.monitor.data.ScheduleConfig
import com.minenergo.monitor.data.SiteConfig
import com.minenergo.monitor.log.AppLogger
import com.minenergo.monitor.network.DocumentDownloader
import com.minenergo.monitor.network.GenericSiteParser
import com.minenergo.monitor.notification.NotificationHelper
import com.minenergo.monitor.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val sites: List<SiteConfig> = emptyList(),
        val globalFilter: FilterConfig = FilterConfig.EMPTY,
        val schedule: ScheduleConfig = ScheduleConfig(),
        val autoCheckEnabled: Boolean = true,
        val allDocuments: List<Document> = emptyList(),
        val selectedUrls: Set<String> = emptySet(),
        val lastCheckedAtMillis: Long = 0L,
        val isChecking: Boolean = false,
        val isDownloading: Boolean = false,
        val errorMessage: String? = null,
        val downloadResults: List<DownloadResult> = emptyList(),
        val toast: String? = null,
    ) {
        /** Документы после применения глобального и per-site фильтров. */
        val visibleDocuments: List<Document>
            get() = DocumentFilter.apply(allDocuments, globalFilter, sites)
    }

    private val store = PreferencesStore(application)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun loadInitialState(): UiState = UiState(
        sites = store.loadSites(),
        globalFilter = store.loadGlobalFilter(),
        schedule = store.loadSchedule(),
        autoCheckEnabled = store.isAutoCheckEnabled(),
        allDocuments = store.loadDocuments(),
        lastCheckedAtMillis = store.lastCheckedAtMillis(),
    )

    // ===== Документы =====

    fun toggleSelected(url: String) {
        _state.update { current ->
            val updated = current.selectedUrls.toMutableSet().apply {
                if (!add(url)) remove(url)
            }
            current.copy(selectedUrls = updated)
        }
    }

    fun selectAllVisible() {
        _state.update { current ->
            current.copy(selectedUrls = current.visibleDocuments.map { it.url }.toSet())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedUrls = emptySet()) }
    }

    fun checkNow() {
        if (_state.value.isChecking) return
        val activeSites = _state.value.sites.filter { it.enabled }
        if (activeSites.isEmpty()) {
            _state.update { it.copy(toast = "Нет активных сайтов для проверки") }
            return
        }
        _state.update { it.copy(isChecking = true, errorMessage = null) }

        viewModelScope.launch {
            val previousUrls = _state.value.allDocuments.map { it.url }.toSet()
            val combined = mutableListOf<Document>()
            val errors = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (site in activeSites) {
                    try {
                        combined += GenericSiteParser.findDocuments(site)
                    } catch (e: Throwable) {
                        AppLogger.e("UI", "Ошибка проверки ${site.name}: ${e.message}", e)
                        errors += "${site.name}: ${e.message ?: "ошибка"}"
                    }
                }
            }
            combined.sortWith(compareByDescending<Document> { it.publicationDate }.thenBy { it.title })
            val now = System.currentTimeMillis()
            store.saveDocuments(combined, now)

            // Уведомление с учётом фильтра.
            val filter = _state.value.globalFilter
            val visible = DocumentFilter.apply(combined, filter, _state.value.sites)
            val newOnes = visible.filter { it.url !in previousUrls }
            if (newOnes.isNotEmpty()) {
                val summary = newOnes.take(5).joinToString("\n") { d ->
                    "• ${d.publicationDate} — ${d.title.take(70)} [${d.siteName.take(24)}]"
                }
                NotificationHelper.showDocumentsFound(getApplication(), newOnes.size, summary)
            }

            _state.update { state ->
                val errorText = if (errors.isEmpty()) null
                else errors.joinToString("\n")
                state.copy(
                    allDocuments = combined,
                    lastCheckedAtMillis = now,
                    isChecking = false,
                    errorMessage = errorText,
                    toast = buildString {
                        append("Найдено: ${combined.size}")
                        if (errors.isNotEmpty()) append(", ошибок: ${errors.size}")
                    },
                )
            }
        }
    }

    fun downloadSelected() {
        val current = _state.value
        if (current.selectedUrls.isEmpty() || current.isDownloading) return
        val toDownload = current.allDocuments.filter { it.url in current.selectedUrls }
        val allowedHosts = current.sites.mapNotNull { DocumentDownloader.hostOf(it.url) }.toSet()
        _state.update { it.copy(isDownloading = true, downloadResults = emptyList()) }

        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                val baseDir = File(getApplication<Application>().filesDir, Config.DOWNLOADS_SUBDIR)
                if (!baseDir.exists()) baseDir.mkdirs()
                val downloader = DocumentDownloader(baseDir, allowedHosts)
                toDownload.map { downloader.download(it) }
            }
            val ok = results.count { it.downloaded }
            val failed = results.size - ok
            _state.update {
                it.copy(
                    isDownloading = false,
                    downloadResults = results,
                    selectedUrls = emptySet(),
                    toast = if (failed == 0) "Скачано: $ok" else "Скачано: $ok, ошибок: $failed",
                )
            }
        }
    }

    // ===== Сайты =====

    fun upsertSite(site: SiteConfig) {
        store.upsertSite(site)
        _state.update { it.copy(sites = store.loadSites()) }
    }

    fun deleteSite(siteId: String) {
        store.deleteSite(siteId)
        _state.update { state ->
            // Удаляем документы этого сайта из снапшота.
            val filtered = state.allDocuments.filterNot { it.siteId == siteId }
            store.saveDocuments(filtered, state.lastCheckedAtMillis)
            state.copy(sites = store.loadSites(), allDocuments = filtered)
        }
    }

    fun toggleSiteEnabled(siteId: String, enabled: Boolean) {
        val site = _state.value.sites.firstOrNull { it.id == siteId } ?: return
        upsertSite(site.copy(enabled = enabled))
    }

    /** Создаёт новый "пустой" сайт-черновик для экрана редактирования. */
    fun makeNewSiteDraft(): SiteConfig = SiteConfig(
        id = UUID.randomUUID().toString(),
        name = "",
        url = "",
        dateFromIso = Config.DEFAULT_DATE_FROM.toString(),
    )

    // ===== Глобальный фильтр =====

    fun updateGlobalFilter(filter: FilterConfig) {
        store.saveGlobalFilter(filter)
        _state.update { it.copy(globalFilter = filter) }
    }

    // ===== Расписание =====

    fun updateSchedule(schedule: ScheduleConfig) {
        store.saveSchedule(schedule)
        _state.update { it.copy(schedule = schedule) }
        WorkScheduler.applyFromPreferences(getApplication())
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        store.setAutoCheckEnabled(enabled)
        _state.update { it.copy(autoCheckEnabled = enabled) }
        WorkScheduler.applyFromPreferences(getApplication())
    }

    // ===== UI helpers =====

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun clearDownloadResults() {
        _state.update { it.copy(downloadResults = emptyList()) }
    }
}
