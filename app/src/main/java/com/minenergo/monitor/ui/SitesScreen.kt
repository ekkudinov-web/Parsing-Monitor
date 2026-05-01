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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun SitesScreen(
    sites: List<SiteConfig>,
    onAdd: () -> Unit,
    onEdit: (SiteConfig) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Источники для мониторинга",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Можно добавлять любые гос-сайты с похожей структурой " +
                    "(списки документов с датами рядом).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            if (sites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Источников ещё нет", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Нажмите «+», чтобы добавить первый сайт.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(sites, key = { it.id }) { site ->
                        SiteCard(
                            site = site,
                            onEdit = { onEdit(site) },
                            onToggleEnabled = { onToggleEnabled(site.id, it) },
                            onAskDelete = { confirmDeleteId = site.id },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Добавить") },
        )
    }

    val toDelete = confirmDeleteId
    if (toDelete != null) {
        val target = sites.firstOrNull { it.id == toDelete }
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Удалить источник?") },
            text = {
                Text(
                    "Сайт «${target?.name ?: ""}» будет удалён. " +
                        "Скачанные ранее файлы не пострадают."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDelete(toDelete)
                    confirmDeleteId = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDeleteId = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SiteCard(
    site: SiteConfig,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onAskDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    site.name.ifBlank { "(без названия)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = site.enabled, onCheckedChange = onToggleEnabled)
            }
            Text(
                site.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val date = runCatching { LocalDate.parse(site.dateFromIso).format(humanDate) }
                .getOrDefault(site.dateFromIso)
            Text("С даты: $date", style = MaterialTheme.typography.labelSmall)
            val tweaks = listOfNotNull(
                site.linkSelector?.let { "selector" },
                site.dateRegex?.let { "regex даты" },
                site.siteFilter?.takeIf { !it.isEffectivelyEmpty() }?.let { "локальный фильтр" },
            )
            if (tweaks.isNotEmpty()) {
                Text(
                    "Настроено: ${tweaks.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Редактировать") }
                IconButton(onClick = onAskDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
