package com.minenergo.monitor.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.minenergo.monitor.data.SiteConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun SiteEditorScreen(
    initial: SiteConfig,
    onSave: (SiteConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var dateFromText by remember { mutableStateOf(formatDateForInput(initial.dateFromIso)) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var advancedExpanded by remember { mutableStateOf(initial.linkSelector != null || initial.dateRegex != null) }
    var linkSelector by remember { mutableStateOf(initial.linkSelector ?: "") }
    var containerSelector by remember { mutableStateOf(initial.containerSelector ?: "") }
    var dateRegex by remember { mutableStateOf(initial.dateRegex ?: "") }
    var dateFormat by remember { mutableStateOf(initial.dateFormat) }
    var localFilterEnabled by remember { mutableStateOf(initial.siteFilter != null && !initial.siteFilter.isEffectivelyEmpty()) }
    val initialFilter = initial.siteFilter ?: com.minenergo.monitor.data.FilterConfig.EMPTY
    var filterTitleContains by remember { mutableStateOf(initialFilter.titleContains.joinToString("\n")) }
    var filterExtensions by remember { mutableStateOf(initialFilter.extensions.joinToString(", ")) }
    var filterSizeMin by remember { mutableStateOf(initialFilter.sizeMinMb?.toString() ?: "") }
    var filterSizeMax by remember { mutableStateOf(initialFilter.sizeMaxMb?.toString() ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial.name.isBlank() && initial.url.isBlank()) "Новый источник" else "Редактирование",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim() },
            label = { Text("URL страницы") },
            placeholder = { Text("https://example.gov.ru/page") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dateFromText,
            onValueChange = {
                dateFromText = it
                dateError = null
            },
            label = { Text("Минимальная дата (ДД.ММ.ГГГГ)") },
            singleLine = true,
            isError = dateError != null,
            supportingText = { dateError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        // === Продвинутые опции ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Продвинутые настройки парсера",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(checked = advancedExpanded, onCheckedChange = { advancedExpanded = it })
                }
                if (advancedExpanded) {
                    Text(
                        "Оставьте поля пустыми, если автоматический парсер справляется. " +
                            "Заполняйте только когда понимаете HTML-структуру сайта.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = linkSelector,
                        onValueChange = { linkSelector = it },
                        label = { Text("CSS-селектор ссылок") },
                        placeholder = { Text("a[href$=.pdf], .doc-list a") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = containerSelector,
                        onValueChange = { containerSelector = it },
                        label = { Text("CSS-селектор контейнера с датой") },
                        placeholder = { Text(".doc-row, li, tr") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dateRegex,
                        onValueChange = { dateRegex = it },
                        label = { Text("Regex даты (группа 1 — дата)") },
                        placeholder = { Text("опубликовано:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dateFormat,
                        onValueChange = { dateFormat = it },
                        label = { Text("Формат даты") },
                        placeholder = { Text("dd.MM.yyyy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // === Локальный фильтр ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Локальный фильтр",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(checked = localFilterEnabled, onCheckedChange = { localFilterEnabled = it })
                }
                Text(
                    "Применяется поверх глобального фильтра (документ должен пройти оба).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (localFilterEnabled) {
                    OutlinedTextField(
                        value = filterTitleContains,
                        onValueChange = { filterTitleContains = it },
                        label = { Text("Подстроки в названии (по строке на каждую)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = filterExtensions,
                        onValueChange = { filterExtensions = it },
                        label = { Text("Расширения через запятую") },
                        placeholder = { Text(".pdf, .docx, .zip") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = filterSizeMin,
                            onValueChange = { filterSizeMin = it },
                            label = { Text("Размер от, МБ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = filterSizeMax,
                            onValueChange = { filterSizeMax = it },
                            label = { Text("Размер до, МБ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
            Button(
                onClick = {
                    val parsedDate = parseInputDate(dateFromText)
                    if (parsedDate == null) {
                        dateError = "Введите дату в формате ДД.ММ.ГГГГ"
                        return@Button
                    }
                    val nameSafe = name.trim().ifEmpty { url.ifEmpty { "Без названия" } }
                    val urlSafe = url.trim()
                    if (urlSafe.isBlank() || (!urlSafe.startsWith("http://") && !urlSafe.startsWith("https://"))) {
                        dateError = "URL должен начинаться с http:// или https://"
                        return@Button
                    }
                    val localFilter = if (localFilterEnabled) {
                        com.minenergo.monitor.data.FilterConfig(
                            enabled = true,
                            titleContains = filterTitleContains.lines()
                                .map { it.trim() }.filter { it.isNotBlank() },
                            extensions = filterExtensions.split(',')
                                .map { it.trim().lowercase() }
                                .filter { it.startsWith(".") },
                            sizeMinMb = filterSizeMin.replace(',', '.').toDoubleOrNull(),
                            sizeMaxMb = filterSizeMax.replace(',', '.').toDoubleOrNull(),
                        )
                    } else null
                    onSave(
                        initial.copy(
                            name = nameSafe,
                            url = urlSafe,
                            dateFromIso = parsedDate.toString(),
                            linkSelector = linkSelector.trim().ifEmpty { null },
                            containerSelector = containerSelector.trim().ifEmpty { null },
                            dateRegex = dateRegex.trim().ifEmpty { null },
                            dateFormat = dateFormat.trim().ifEmpty { "dd.MM.yyyy" },
                            siteFilter = localFilter,
                        )
                    )
                },
                modifier = Modifier.weight(1f),
            ) { Text("Сохранить") }
        }
    }
}

private val INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun formatDateForInput(iso: String): String =
    runCatching { LocalDate.parse(iso).format(INPUT_FORMATTER) }.getOrDefault(iso)

private fun parseInputDate(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), INPUT_FORMATTER)
} catch (_: DateTimeParseException) {
    null
}
