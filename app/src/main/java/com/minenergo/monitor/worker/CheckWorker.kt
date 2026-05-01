package com.minenergo.monitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.DocumentFilter
import com.minenergo.monitor.data.PreferencesStore
import com.minenergo.monitor.log.AppLogger
import com.minenergo.monitor.network.SiteParser
import com.minenergo.monitor.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tag = "Worker"
        val store = PreferencesStore(applicationContext)
        if (!store.isAutoCheckEnabled()) {
            AppLogger.i(tag, "Автопроверка выключена — пропуск")
            return@withContext Result.success()
        }
        val schedule = store.loadSchedule()
        if (schedule.windowEnabled) {
            val zone = runCatching { ZoneId.of(schedule.timezoneId) }.getOrDefault(ZoneId.of("Europe/Moscow"))
            val hour = LocalDateTime.now(zone).hour
            if (hour < schedule.windowFromHour || hour >= schedule.windowToHour) {
                AppLogger.i(
                    tag,
                    "Час=$hour вне окна [${schedule.windowFromHour}..${schedule.windowToHour}) — пропуск"
                )
                return@withContext Result.success()
            }
        }

        val sites = store.loadSites().filter { it.enabled }
        if (sites.isEmpty()) {
            AppLogger.i(tag, "Активных сайтов нет — пропуск")
            return@withContext Result.success()
        }

        val previousUrls = store.loadDocuments().map { it.url }.toSet()
        val combined = mutableListOf<Document>()
        var anyError = false
        for (site in sites) {
            try {
                AppLogger.i(tag, "Проверка ${site.name} (${site.url})")
                combined += SiteParser.findDocuments(site)
            } catch (e: Throwable) {
                anyError = true
                AppLogger.e(tag, "Ошибка проверки ${site.name}: ${e.message}", e)
            }
        }

        // Сортируем все документы по дате убывая.
        combined.sortWith(compareByDescending<Document> { it.publicationDate }.thenBy { it.title })
        val now = System.currentTimeMillis()
        store.saveDocuments(combined, now)

        // Применяем фильтры для уведомлений.
        val filter = store.loadGlobalFilter()
        val visible = DocumentFilter.apply(combined, filter, sites)
        val newOnes = visible.filter { it.url !in previousUrls }
        if (newOnes.isNotEmpty()) {
            val summary = newOnes.take(5).joinToString("\n") { d ->
                val date = d.publicationDate.format(HUMAN_DATE)
                "• $date — ${d.title.take(70)} [${d.siteName.take(24)}]"
            } + (if (newOnes.size > 5) "\n…и ещё ${newOnes.size - 5}" else "")
            NotificationHelper.showDocumentsFound(applicationContext, newOnes.size, summary)
            AppLogger.i(tag, "Уведомление: новых документов ${newOnes.size}")
        } else {
            AppLogger.i(tag, "Новых документов нет (всего после фильтрации: ${visible.size})")
        }

        if (anyError && combined.isEmpty()) Result.retry() else Result.success()
    }

    companion object {
        private val HUMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
