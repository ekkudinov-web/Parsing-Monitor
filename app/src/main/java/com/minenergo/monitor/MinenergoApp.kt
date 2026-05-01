package com.minenergo.monitor

import android.app.Application
import com.minenergo.monitor.log.AppLogger
import com.minenergo.monitor.notification.NotificationHelper
import com.minenergo.monitor.worker.WorkScheduler

class MinenergoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i(TAG, "Запуск приложения")
        NotificationHelper.ensureChannel(this)
        WorkScheduler.applyFromPreferences(this)
    }

    companion object {
        private const val TAG = "MinenergoApp"
    }
}
