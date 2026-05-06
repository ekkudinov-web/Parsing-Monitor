package com.minenergo.monitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minenergo.monitor.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Диагностический экран для просмотра логов приложения. Показывает
 * последние строки из файла лога, разрешает копировать всё в буфер
 * обмена или очистить.
 *
 * Полезно когда нужно понять, действительно ли срабатывает фоновая
 * задача — после события в логе появится строка "Worker | Проверка
 * <сайт>". Если её нет — значит система не запустила воркер.
 */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("Загрузка…") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        content = withContext(Dispatchers.IO) { readLog() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "Диагностика и логи",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }

        Text(
            "Здесь видна история запусков воркера, ошибок парсинга и сетевых " +
                "проблем. Если автоматические проверки не работают, последняя " +
                "строка с тегом «Worker» покажет, когда воркер реально запускался.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refreshKey++ }, modifier = Modifier.weight(1f)) {
                Text("Обновить")
            }
            OutlinedButton(
                onClick = { copyToClipboard(context, content) },
                modifier = Modifier.weight(1f),
            ) { Text("Скопировать") }
        }
        OutlinedButton(
            onClick = {
                clearLog()
                refreshKey++
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Очистить логи") }

        Spacer(Modifier.height(4.dp))

        // Сами логи: моноширинный текст в скроллируемом контейнере.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                content.ifBlank { "(пусто)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun readLog(): String {
    val file = AppLogger.logFile() ?: return "(лог-файл не инициализирован)"
    if (!file.exists()) return "(лог-файла пока нет)"
    val text = runCatching { file.readText() }.getOrElse { "(ошибка чтения: ${it.message})" }
    if (text.isBlank()) return "(лог пустой)"
    // Обрезаем до последних ~30 КБ, чтобы экран не тормозил.
    val maxChars = 30_000
    return if (text.length > maxChars) {
        "...(обрезано)...\n" + text.substring(text.length - maxChars)
    } else text
}

private fun clearLog() {
    val file = AppLogger.logFile() ?: return
    runCatching { file.writeText("") }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Minenergo Monitor logs", text))
}
