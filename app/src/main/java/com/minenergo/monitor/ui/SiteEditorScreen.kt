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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.minenergo.monitor.data.FilterConfig
import com.minenergo.monitor.data.SiteConfig
import com.minenergo.monitor.data.SourceType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteEditorScreen(
    initial: SiteConfig,
    onSave: (SiteConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var dateFromText by remember { mutableStateOf(formatDateForInput(initial.dateFromIso)) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var sourceType by remember { mutableStateOf(initial.sourceType) }
    // HTML-режим
    var advancedExpanded by remember {
        mutableStateOf(initial.linkSelector != null || initial.dateRegex != null)
    }
    var linkSelector by remember { mutableStateOf(initial.linkSelector ?: "") }
    var containerSelector by remember { mutableStateOf(initial.containerSelector ?: "") }
    var dateRegex by remember { mutableStateOf(initial.dateRegex ?: "") }
    var dateFormat by remember { mutableStateOf(initial.dateFormat) }
    // API-режим
    var apiCode by remember { mutableStateOf(initial.apiCode ?: "") }
    var apiSectionIds by remember { mutableStateOf(initial.apiSectionIds.joinToString(", ")) }
    // Локальный фильтр
    var localFilterEnabled by remember {
        mutableStateOf(initial.siteFilter != null && !initial.siteFilter.isEffectivelyEmpty())
    }
    val initialFilter = initial.siteFilter ?: FilterConfig.EMPTY
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

        // === Тип источника ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Тип источника", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sourceType == SourceType.MINENERGO_API,
                        onClick = { sourceType = SourceType.MINENERGO_API },
                        label = { Text("API Минэнерго") },
                    )
                    FilterChip(
                        selected = sourceType == SourceType.UNIVERSAL_HTML,
                        onClick = { sourceType = SourceType.UNIVERSAL_HTML },
                        label = { Text("HTML-страница") },
                    )
                }
                val description = when (sourceType) {
                    SourceType.MINENERGO_API ->
                        "Прямой запрос к API minenergo.gov.ru. Быстро и надёжно. " +
                            "Нужен только код организации (последняя часть URL страницы)."
                    SourceType.UNIVERSAL_HTML ->
                        "Парсер HTML-страницы. Подходит для других гос-сайтов с серверной " +
                            "вёрсткой, где список документов сразу есть в HTML."
                }
                Text(description, style = MaterialTheme.typography.labelSmall)
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim() },
            label = { Text(if (sourceType == SourceType.MINENERGO_API) "URL страницы (для информации)" else "URL страницы") },
            placeholder = { Text("https://minenergo.gov.ru/...") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dateFromText,
            onValueChange = { dateFromText = it },
            label = { Text("Минимальная дата (ДД.ММ.ГГГГ)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // === Поля для API-режима ===
        if (sourceType == SourceType.MINENERGO_API) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Параметры API Минэнерго", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Код организации — последний сегмент в URL страницы организации " +
                            "на minenergo.gov.ru. Например для «Россети Центр и Приволжье»: " +
                            "pao_rosseti_tsentr_i_privolzhe.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = apiCode,
                        onValueChange = { apiCode = it.trim() },
                        label = { Text("Код организации") },
                        placeholder = { Text("pao_rosseti_tsentr_i_privolzhe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "ID разделов через запятую — оставьте пустым, чтобы получать все разделы. " +
                            "Например, 647 — это «Информация о проектах ИПР».",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    OutlinedTextField(
                        value = apiSectionIds,
                        onValueChange = { apiSectionIds = it },
                        label = { Text("ID разделов") },
                        placeholder = { Text("647") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // === Продвинутые опции HTML-парсера ===
        if (sourceType == SourceType.UNIVERSAL_HTML) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Продвинутые настройки парсера",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Switch(checked = advancedExpanded, onCheckedChange = { advancedExpanded = it })
                    }
                    if (advancedExpanded) {
                        Text(
                            "Заполняйте только когда понимаете HTML-структуру сайта.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        OutlinedTextField(
                            value = linkSelector,
                            onValueChange = { linkSelector = it },
                            label = { Text("CSS-селектор ссылок") },
                            placeholder = { Text("a[href\$=.pdf]") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = containerSelector,
                            onValueChange = { containerSelector = it },
                            label = { Text("CSS-селектор контейнера с датой") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = dateRegex,
                            onValueChange = { dateRegex = it },
                            label = { Text("Regex даты (группа 1)") },
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
        }

        // === Локальный фильтр ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Локальный фильтр",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(checked = localFilterEnabled, onCheckedChange = { localFilterEnabled = it })
                }
                Text(
                    "Применяется поверх глобального фильтра.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (localFilterEnabled) {
                    OutlinedTextField(
                        value = filterTitleContains,
                        onValueChange = { filterTitleContains = it },
                        label = { Text("Подстроки в названии (по строке)") },
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
                            label = { Text("От, МБ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = filterSizeMax,
                            onValueChange = { filterSizeMax = it },
                            label = { Text("До, МБ") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (validationError != null) {
            Text(
                validationError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
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
                        validationError = "Введите дату в формате ДД.ММ.ГГГГ"
                        return@Button
                    }
                    val urlSafe = url.trim()
                    if (sourceType == SourceType.UNIVERSAL_HTML) {
                        if (urlSafe.isBlank() ||
                            (!urlSafe.startsWith("http://") && !urlSafe.startsWith("https://"))
                        ) {
                            validationError = "URL должен начинаться с http:// или https://"
                            return@Button
                        }
                    }
                    if (sourceType == SourceType.MINENERGO_API && apiCode.isBlank()) {
                        validationError = "Для API-источника укажите код организации"
                        return@Button
                    }
                    val nameSafe = name.trim().ifEmpty {
                        if (sourceType == SourceType.MINENERGO_API) apiCode
                        else urlSafe.ifEmpty { "Без названия" }
                    }
                    val effectiveUrl = urlSafe.ifEmpty {
                        if (sourceType == SourceType.MINENERGO_API)
                            "https://minenergo.gov.ru/industries/power-industry/investment-programs/$apiCode"
                        else ""
                    }
                    val sectionIdList = apiSectionIds.split(',', ' ', ';')
                        .mapNotNull { it.trim().toLongOrNull() }
                    val localFilter = if (localFilterEnabled) {
                        FilterConfig(
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
                            url = effectiveUrl,
                            dateFromIso = parsedDate.toString(),
                            sourceType = sourceType,
                            linkSelector = linkSelector.trim().ifEmpty { null },
                            containerSelector = containerSelector.trim().ifEmpty { null },
                            dateRegex = dateRegex.trim().ifEmpty { null },
                            dateFormat = dateFormat.trim().ifEmpty { "dd.MM.yyyy" },
                            apiCode = apiCode.trim().ifEmpty { null },
                            apiSectionIds = sectionIdList,
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
