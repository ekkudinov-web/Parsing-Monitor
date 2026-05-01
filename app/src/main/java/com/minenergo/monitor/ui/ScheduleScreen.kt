package com.minenergo.monitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minenergo.monitor.Config
import com.minenergo.monitor.data.ScheduleConfig

private enum class IntervalUnit(val title: String, val minutes: Long) {
    MINUTES("минут", 1L),
    HOURS("часов", 60L),
    DAYS("суток", 60L * 24L),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    schedule: ScheduleConfig,
    autoCheckEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onScheduleChange: (ScheduleConfig) -> Unit,
) {
    val (initialUnit, initialValue) = pickUnit(schedule.intervalMinutes)
    var unit by remember(schedule) { mutableStateOf(initialUnit) }
    var valueText by remember(schedule) { mutableStateOf(initialValue.toString()) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var windowEnabled by remember(schedule) { mutableStateOf(schedule.windowEnabled) }
    var windowFromText by remember(schedule) { mutableStateOf(schedule.windowFromHour.toString()) }
    var windowToText by remember(schedule) { mutableStateOf(schedule.windowToHour.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Автоматическая проверка",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(checked = autoCheckEnabled, onCheckedChange = onAutoCheckChange)
                }
                Text(
                    "Когда выключена, фоновых проверок не будет. Можно проверять " +
                        "вручную кнопкой «Проверить» на главном экране.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Периодичность", fontWeight = FontWeight.SemiBold)
                Text(
                    "Минимум — 15 минут (ограничение Android для фоновых задач без " +
                        "постоянного уведомления).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it.filter(Char::isDigit).take(5) },
                        label = { Text("Каждые") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = unitMenuOpen,
                            onExpandedChange = { unitMenuOpen = !unitMenuOpen },
                        ) {
                            OutlinedTextField(
                                value = unit.title,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Единица") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuOpen) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            DropdownMenu(
                                expanded = unitMenuOpen,
                                onDismissRequest = { unitMenuOpen = false },
                            ) {
                                IntervalUnit.values().forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.title) },
                                        onClick = {
                                            unit = item
                                            unitMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Окно активных часов",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(checked = windowEnabled, onCheckedChange = { windowEnabled = it })
                }
                Text(
                    "Когда выключено — проверки идут круглосуточно. Время в зоне " +
                        "${schedule.timezoneId}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (windowEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = windowFromText,
                            onValueChange = { windowFromText = it.filter(Char::isDigit).take(2) },
                            label = { Text("С (час, 0–23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = windowToText,
                            onValueChange = { windowToText = it.filter(Char::isDigit).take(2) },
                            label = { Text("До (час, 1–24)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = {
                val rawValue = valueText.toLongOrNull()
                if (rawValue == null || rawValue <= 0) {
                    error = "Введите положительное число"
                    return@Button
                }
                val totalMinutes = rawValue * unit.minutes
                if (totalMinutes < Config.MIN_INTERVAL_MINUTES) {
                    error = "Минимум ${Config.MIN_INTERVAL_MINUTES} минут"
                    return@Button
                }
                if (totalMinutes > Config.MAX_INTERVAL_MINUTES) {
                    error = "Максимум ${Config.MAX_INTERVAL_MINUTES / 60} часов"
                    return@Button
                }
                val from = windowFromText.toIntOrNull() ?: 0
                val to = windowToText.toIntOrNull() ?: 24
                if (windowEnabled) {
                    if (from !in 0..23 || to !in 1..24 || from >= to) {
                        error = "Окно: «с» в 0..23, «до» в 1..24, «с» < «до»"
                        return@Button
                    }
                }
                error = null
                onScheduleChange(
                    schedule.copy(
                        intervalMinutes = totalMinutes,
                        windowEnabled = windowEnabled,
                        windowFromHour = from,
                        windowToHour = to,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить расписание")
        }
    }
}

private fun pickUnit(minutes: Long): Pair<IntervalUnit, Long> = when {
    minutes % (60L * 24L) == 0L -> IntervalUnit.DAYS to (minutes / (60L * 24L))
    minutes % 60L == 0L -> IntervalUnit.HOURS to (minutes / 60L)
    else -> IntervalUnit.MINUTES to minutes
}
