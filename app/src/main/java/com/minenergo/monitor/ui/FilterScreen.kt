package com.minenergo.monitor.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.minenergo.monitor.data.FilterConfig

@Composable
fun FilterScreen(
    filter: FilterConfig,
    onChange: (FilterConfig) -> Unit,
) {
    var enabled by remember(filter) { mutableStateOf(filter.enabled) }
    var titleContains by remember(filter) { mutableStateOf(filter.titleContains.joinToString("\n")) }
    var titleCaseSensitive by remember(filter) { mutableStateOf(filter.titleCaseSensitive) }
    var extensions by remember(filter) { mutableStateOf(filter.extensions.joinToString(", ")) }
    var sizeMin by remember(filter) { mutableStateOf(filter.sizeMinMb?.toString() ?: "") }
    var sizeMax by remember(filter) { mutableStateOf(filter.sizeMaxMb?.toString() ?: "") }
    var passUnknown by remember(filter) { mutableStateOf(filter.passUnknownSize) }

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
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Глобальный фильтр",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(
                    "Применяется ко всем источникам. Локальные фильтры в карточке " +
                        "сайта могут добавлять дополнительные ограничения.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (enabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Название", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = titleContains,
                        onValueChange = { titleContains = it },
                        label = { Text("Подстроки (по одной на строку)") },
                        placeholder = { Text("инвестпрограмма\nтариф") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = titleCaseSensitive,
                            onCheckedChange = { titleCaseSensitive = it },
                        )
                        Text("  Учитывать регистр", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Расширения файлов", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = extensions,
                        onValueChange = { extensions = it },
                        label = { Text("Через запятую (с точкой)") },
                        placeholder = { Text(".pdf, .docx, .zip") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Размер файла", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sizeMin,
                            onValueChange = { sizeMin = it },
                            label = { Text("От, МБ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = sizeMax,
                            onValueChange = { sizeMax = it },
                            label = { Text("До, МБ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = passUnknown,
                            onCheckedChange = { passUnknown = it },
                        )
                        Text(
                            "  Показывать файлы без указанного размера",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                onChange(
                    FilterConfig(
                        enabled = enabled,
                        titleContains = titleContains.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() },
                        titleCaseSensitive = titleCaseSensitive,
                        extensions = extensions.split(',')
                            .map { it.trim().lowercase() }
                            .filter { it.startsWith(".") },
                        sizeMinMb = sizeMin.replace(',', '.').toDoubleOrNull(),
                        sizeMaxMb = sizeMax.replace(',', '.').toDoubleOrNull(),
                        passUnknownSize = passUnknown,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить фильтр")
        }
    }
}
