package com.minenergo.monitor.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.minenergo.monitor.Config
import com.minenergo.monitor.data.PreferencesStore
import com.minenergo.monitor.log.AppLogger
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val TAG = "Scheduler"

    /**
     * Включает периодическую проверку с интервалом из настроек.
     * Использует [ExistingPeriodicWorkPolicy.UPDATE], чтобы обновить расписание
     * при изменении интервала пользователем.
     */
    fun applyFromPreferences(context: Context) {
        val store = PreferencesStore(context)
        if (!store.isAutoCheckEnabled()) {
            WorkManager.getInstance(context).cancelUniqueWork(Config.WORK_NAME)
            AppLogger.i(TAG, "Автопроверка выключена — задача отменена")
            return
        }
        val schedule = store.loadSchedule()
        val interval = schedule.intervalMinutes.coerceIn(Config.MIN_INTERVAL_MINUTES, Config.MAX_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<CheckWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Config.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        AppLogger.i(TAG, "Периодическая проверка запланирована: каждые $interval мин")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(Config.WORK_NAME)
        AppLogger.i(TAG, "Периодическая проверка отменена")
    }

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<CheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
        AppLogger.i(TAG, "Запущена ручная проверка")
    }
}
