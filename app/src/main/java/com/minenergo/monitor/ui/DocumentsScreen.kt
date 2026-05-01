package com.minenergo.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minenergo.monitor.MainViewModel
import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.DownloadResult
import com.minenergo.monitor.ui.theme.Success
import com.minenergo.monitor.ui.theme.Warning
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun DocumentsScreen(
    state: MainViewModel.UiState,
    onCheckNow: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownload: () -> Unit,
    onDismissResults: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StatusCard(state, onAutoCheckChange)
        ActionsRow(state, onCheckNow, onDownload)
        if (state.downloadResults.isNotEmpty()) {
            DownloadResultsCard(state.downloadResults, onDismissResults)
        }
        DocumentsList(
            state = state,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StatusCard(
    state: MainViewModel.UiState,
    onAutoCheckChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Автоматическая проверка",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Switch(checked = state.autoCheckEnabled, onCheckedChange = onAutoCheckChange)
            }
            val activeSites = state.sites.count { it.enabled }
            Text(
                "Активных источников: $activeSites из ${state.sites.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            val intervalText = formatInterval(state.schedule.intervalMinutes)
            val windowText = if (state.schedule.windowEnabled) {
                ", окно ${state.schedule.windowFromHour}:00–${state.schedule.windowToHour}:00"
            } else ", круглосуточно"
            Text(
                "Период: $intervalText$windowText",
                style = MaterialTheme.typography.bodySmall,
            )
            val checkedAt = if (state.lastCheckedAtMillis > 0)
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(state.lastCheckedAtMillis))
            else "ещё не было"
            Text("Последняя проверка: $checkedAt", style = MaterialTheme.typography.bodySmall)

            if (state.errorMessage != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun formatInterval(minutes: Long): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0L && minutes < 24 * 60 -> "${minutes / 60} ч"
    minutes == 24L * 60 -> "1 сут"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

@Composable
private fun ActionsRow(
    state: MainViewModel.UiState,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCheckNow,
            modifier = Modifier.weight(1f),
            enabled = !state.isChecking,
        ) {
            if (state.isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Проверка…")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Проверить")
            }
        }
        Button(
            onClick = onDownload,
            modifier = Modifier.weight(1f),
            enabled = state.selectedUrls.isNotEmpty() && !state.isDownloading,
        ) {
            if (state.isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Скачивание…")
            } else {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Скачать (${state.selectedUrls.size})")
            }
        }
    }
}

@Composable
private fun DocumentsList(
    state: MainViewModel.UiState,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state.visibleDocuments
    val totalAll = state.allDocuments.size
    if (visible.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (totalAll == 0) "Документы пока не найдены."
                    else "Нет документов, соответствующих фильтру.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (totalAll == 0) "Нажмите «Проверить», чтобы запустить поиск."
                    else "Скрыто фильтром: $totalAll",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hidden = totalAll - visible.size
            val countLine = if (hidden > 0) "Документов: ${visible.size} (скрыто фильтром: $hidden)"
            else "Документов: ${visible.size}"
            Text(countLine, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (state.selectedUrls.size < visible.size) {
                OutlinedButton(onClick = onSelectAll) { Text("Все") }
            } else {
                OutlinedButton(onClick = onClearSelection) { Text("Снять") }
            }
        }
        HorizontalDivider()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.url }) { doc ->
                DocumentRow(doc, doc.url in state.selectedUrls) { onToggle(doc.url) }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: Document, selected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                val tail = listOfNotNull(
                    document.publicationDate.format(humanDate),
                    document.extension,
                    document.sizeText,
                    "[${document.siteName}]",
                ).joinToString("  •  ")
                Text(
                    tail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun DownloadResultsCard(results: List<DownloadResult>, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Результат скачивания", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Скрыть") }
            }
            results.forEach { ResultRow(it) }
        }
    }
}

@Composable
private fun ResultRow(result: DownloadResult) {
    val color: Color = when {
        !result.downloaded -> MaterialTheme.colorScheme.error
        result.archiveError != null -> Warning
        else -> Success
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (result.downloaded) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.document.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            val detail = when {
                !result.downloaded -> "Ошибка: ${result.error ?: "не удалось скачать"}"
                result.archiveUnpacked ->
                    "Распакован: файлов ${result.unpackedFilesCount}" +
                        (if (result.skippedFilesCount > 0) ", пропущено ${result.skippedFilesCount}" else "")
                result.archiveError != null ->
                    "Скачан, но архив не распакован: ${result.archiveError}"
                else -> "Скачан"
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

internal val humanDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
