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
     *
     * @param forceRestart Если true — используется
     * [ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE], которая
     * полностью отменяет уже запланированную задачу и стартует таймер
     * заново. Это нужно при явном сохранении расписания пользователем,
     * иначе застрявшая в очереди задача может не запуститься никогда.
     * При false — обычное обновление параметров без сброса таймера.
     */
    fun applyFromPreferences(context: Context, forceRestart: Boolean = false) {
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
        val policy = if (forceRestart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        else ExistingPeriodicWorkPolicy.UPDATE
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Config.WORK_NAME,
            policy,
            request,
        )
        AppLogger.i(
            TAG,
            "Периодическая проверка запланирована: каждые $interval мин (policy=$policy)",
        )
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

    /**
     * Тестовая отложенная проверка через 1 минуту. Использует уникальное
     * имя работы, чтобы тестовый запуск можно было перезапустить, и
     * чтобы он точно не конфликтовал с обычным периодическим воркером.
     */
    fun scheduleDelayedTest(context: Context) {
        val request = OneTimeWorkRequestBuilder<CheckWorker>()
            .setInitialDelay(60, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            Config.WORK_NAME_TEST,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
        AppLogger.i(TAG, "Тестовая проверка запланирована через 60 секунд")
    }
}
