package com.minenergo.monitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minenergo.monitor.MainViewModel

private enum class Tab(val title: String, val icon: ImageVector) {
    Documents("Документы", Icons.Filled.Description),
    Sites("Источники", Icons.Filled.Public),
    Filter("Фильтр", Icons.Filled.FilterAlt),
    Schedule("Расписание", Icons.Filled.Schedule),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(Tab.Documents) }
    val snackbarHostState = remember { SnackbarHostState() }
    var editingSiteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.toast) {
        state.toast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Мониторинг документов", fontWeight = FontWeight.Medium)
                        Text(
                            currentTab.title,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            // Если открыт редактор сайта — bottom-bar скрываем.
            if (editingSiteId == null) {
                NavigationBar {
                    Tab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val editing = editingSiteId
            if (editing != null) {
                val draft = state.sites.firstOrNull { it.id == editing }
                    ?: viewModel.makeNewSiteDraft().copy(id = editing)
                SiteEditorScreen(
                    initial = draft,
                    onSave = { saved ->
                        viewModel.upsertSite(saved)
                        editingSiteId = null
                    },
                    onCancel = { editingSiteId = null },
                )
            } else when (currentTab) {
                Tab.Documents -> DocumentsScreen(
                    state = state,
                    onCheckNow = viewModel::checkNow,
                    onToggle = viewModel::toggleSelected,
                    onSelectAll = viewModel::selectAllVisible,
                    onClearSelection = viewModel::clearSelection,
                    onDownload = viewModel::downloadSelected,
                    onDismissResults = viewModel::clearDownloadResults,
                    onAutoCheckChange = viewModel::setAutoCheckEnabled,
                )
                Tab.Sites -> SitesScreen(
                    sites = state.sites,
                    onAdd = {
                        val draft = viewModel.makeNewSiteDraft()
                        editingSiteId = draft.id
                    },
                    onEdit = { editingSiteId = it.id },
                    onToggleEnabled = viewModel::toggleSiteEnabled,
                    onDelete = viewModel::deleteSite,
                )
                Tab.Filter -> FilterScreen(
                    filter = state.globalFilter,
                    onChange = viewModel::updateGlobalFilter,
                )
                Tab.Schedule -> ScheduleScreen(
                    schedule = state.schedule,
                    autoCheckEnabled = state.autoCheckEnabled,
                    onAutoCheckChange = viewModel::setAutoCheckEnabled,
                    onScheduleChange = viewModel::updateSchedule,
                )
            }
        }
    }
}
