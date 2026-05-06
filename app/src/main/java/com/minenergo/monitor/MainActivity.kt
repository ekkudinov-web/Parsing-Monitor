package com.minenergo.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.minenergo.monitor.ui.AppNav
import com.minenergo.monitor.ui.theme.MinenergoTheme
import com.minenergo.monitor.worker.WorkScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MinenergoTheme {
                AppNav(viewModel = viewModel)
            }
        }
    }

    /**
     * При каждом возврате к приложению пере-регистрируем периодический
     * воркер. На некоторых устройствах (особенно Samsung One UI 6+)
     * система может незаметно «забыть» зарегистрированную задачу после
     * длительного простоя, и без этого вызова воркер вообще никогда
     * не запустится автоматически. Используется UPDATE-policy, поэтому
     * пере-регистрация дешёвая и не сбрасывает таймер.
     */
    override fun onResume() {
        super.onResume()
        WorkScheduler.applyFromPreferences(applicationContext)
    }
}
