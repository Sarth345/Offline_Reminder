package com.example.offlinereminder.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.DateFormatPreference
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.ThemeMode
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNotificationAlarmSettings: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToNotificationProfiles: () -> Unit = {},
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToSavedLocations: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToIcsImport: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                viewModel.onAction(SettingsAction.ExportBackup(outputStream))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.onAction(SettingsAction.ImportBackup(inputStream, uiState.selectedStrategy))
            }
        }
    }

    SettingsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onNavigateToNotificationAlarmSettings = onNavigateToNotificationAlarmSettings,
        onExportClick = { exportLauncher.launch("Planerize_Backup.zip") },
        onImportClick = { importLauncher.launch(arrayOf("*/*")) },
        onNavigateToTemplates = onNavigateToTemplates,
        onNavigateToTrash = onNavigateToTrash,
        onNavigateToArchive = onNavigateToArchive,
        onNavigateToNotificationProfiles = onNavigateToNotificationProfiles,
        onNavigateToAppLock = onNavigateToAppLock,
        onNavigateToSavedLocations = onNavigateToSavedLocations,
        onNavigateToExport = onNavigateToExport,
        onNavigateToIcsImport = onNavigateToIcsImport,
        onNavigateToStatistics = onNavigateToStatistics,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToNotificationAlarmSettings: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToArchive: () -> Unit = {},
    onNavigateToNotificationProfiles: () -> Unit = {},
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToSavedLocations: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToIcsImport: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showAddEventTypeDialog by remember { mutableStateOf(false) }
    var editingEventType by remember { mutableStateOf<EventType?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Data Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error Alert Banner
            if (uiState.errorMessage != null) {
                StatusAlertBanner(
                    message = uiState.errorMessage,
                    isError = true,
                    onDismiss = { onAction(SettingsAction.DismissMessages) }
                )
            }

            // Success Alert Banner
            if (uiState.successMessage != null) {
                StatusAlertBanner(
                    message = uiState.successMessage,
                    isError = false,
                    onDismiss = { onAction(SettingsAction.DismissMessages) }
                )
            }

            // 1. Appearance Section
            AppearanceSection(
                themeMode = uiState.settings.themeMode,
                onThemeSelected = { onAction(SettingsAction.UpdateThemeMode(it)) }
            )

            // 2. Date & Time Section
            DateTimeSection(
                use24HourFormat = uiState.settings.use24HourFormat,
                firstDayOfWeek = uiState.settings.firstDayOfWeek,
                dateFormat = uiState.settings.dateFormat,
                onUpdateTimeFormat = { onAction(SettingsAction.UpdateTimeFormat(it)) },
                onUpdateFirstDayOfWeek = { onAction(SettingsAction.UpdateFirstDayOfWeek(it)) },
                onUpdateDateFormat = { onAction(SettingsAction.UpdateDateFormat(it)) }
            )

            // 3. Event Defaults Section
            EventDefaultsSection(
                defaultPriority = uiState.settings.defaultEventPriority,
                defaultDurationMinutes = uiState.settings.defaultEventDurationMinutes,
                defaultSnoozeMinutes = uiState.settings.defaultSnoozeDurationMinutes,
                onUpdatePriority = { onAction(SettingsAction.UpdateDefaultPriority(it)) },
                onUpdateDuration = { onAction(SettingsAction.UpdateDefaultDuration(it)) },
                onUpdateSnooze = { onAction(SettingsAction.UpdateDefaultSnooze(it)) }
            )

            // 4. Notifications & Alarms Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notifications & Alarms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Manage sound tones, alarm tunes, snooze intervals & alert permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = onNavigateToNotificationAlarmSettings,
                        modifier = Modifier.semantics { contentDescription = "Open notification and alarm configuration" }
                    ) {
                        Text("Configure")
                    }
                }
            }

            // 5. Notifications & Quiet Hours Section
            NotificationsSection(
                soundEnabled = uiState.settings.soundEnabled,
                vibrationEnabled = uiState.settings.vibrationEnabled,
                quietHours = uiState.settings.quietHours,
                onToggleSound = { onAction(SettingsAction.ToggleSound(it)) },
                onToggleVibration = { onAction(SettingsAction.ToggleVibration(it)) },
                onUpdateQuietHours = { onAction(SettingsAction.UpdateQuietHours(it)) }
            )

            // 5. Category Management Section
            CategoryManagementSection(
                categories = uiState.categories,
                onAddCategoryClick = { showAddCategoryDialog = true },
                onEditCategory = { editingCategory = it },
                onDeleteCategory = { onAction(SettingsAction.DeleteCategory(it)) }
            )

            // 6. Event Type Management Section
            EventTypeManagementSection(
                eventTypes = uiState.eventTypes,
                onAddEventTypeClick = { showAddEventTypeDialog = true },
                onEditEventType = { editingEventType = it },
                onDeleteEventType = { onAction(SettingsAction.DeleteEventType(it)) }
            )

            // 7. Organization & Management Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Organization & Maintenance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToTemplates,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Open templates" }
                        ) {
                            Text("Templates")
                        }
                        OutlinedButton(
                            onClick = onNavigateToNotificationProfiles,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Open notification profiles" }
                        ) {
                            Text("Profiles")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToArchive,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Open archive" }
                        ) {
                            Text("Archive")
                        }
                        OutlinedButton(
                            onClick = onNavigateToTrash,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Open trash" }
                        ) {
                            Text("Trash")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToAppLock,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Open App Lock settings" }
                        ) {
                            Text("App Lock")
                        }
                        OutlinedButton(
                            onClick = onNavigateToSavedLocations,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Open saved locations" }
                        ) {
                            Text("Locations")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToExport,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Export calendar and events" }
                        ) {
                            Text("Export Data")
                        }
                        OutlinedButton(
                            onClick = onNavigateToIcsImport,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Import iCalendar file" }
                        ) {
                            Text("Import ICS")
                        }
                    }
                    OutlinedButton(
                        onClick = onNavigateToStatistics,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open statistics and productivity insights" }
                    ) {
                        Text("Statistics & Insights")
                    }
                }
            }

            // 8. Backup & Restore Section
            BackupRestoreSection(
                selectedStrategy = uiState.selectedStrategy,
                isLoading = uiState.isLoading,
                isExporting = uiState.isExporting,
                isImporting = uiState.isImporting,
                onSelectStrategy = { onAction(SettingsAction.SelectRestoreStrategy(it)) },
                onExportClick = onExportClick,
                onImportClick = onImportClick
            )

            // Restore Summary Card (if available)
            if (uiState.lastRestoreSummary != null) {
                RestoreSummaryCard(summary = uiState.lastRestoreSummary)
            }

            // 8. Reset to Defaults Button
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Settings to Defaults")
            }

            // 9. Offline Privacy Notice
            PrivacyNoticeCard()

            // 10. About Planerize
            AboutPlanerizeCard()
        }
    }

    // Category Dialogs
    if (showAddCategoryDialog) {
        CategoryAddDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name ->
                onAction(SettingsAction.CreateCategory(name = name))
                showAddCategoryDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        CategoryEditDialog(
            category = category,
            onDismiss = { editingCategory = null },
            onUpdate = { updated ->
                onAction(SettingsAction.UpdateCategory(updated))
                editingCategory = null
            }
        )
    }

    // EventType Dialogs
    if (showAddEventTypeDialog) {
        EventTypeAddDialog(
            categories = uiState.categories,
            onDismiss = { showAddEventTypeDialog = false },
            onSave = { name, categoryId ->
                onAction(SettingsAction.CreateEventType(name = name, categoryId = categoryId))
                showAddEventTypeDialog = false
            }
        )
    }

    editingEventType?.let { eventType ->
        EventTypeEditDialog(
            eventType = eventType,
            onDismiss = { editingEventType = null },
            onUpdate = { updated ->
                onAction(SettingsAction.UpdateEventType(updated))
                editingEventType = null
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        ResetSettingsConfirmDialog(
            onDismiss = { showResetConfirmDialog = false },
            onConfirm = {
                onAction(SettingsAction.ResetSettings)
                showResetConfirmDialog = false
            }
        )
    }
}

// ==========================================
// SECTIONS
// ==========================================

@Composable
private fun StatusAlertBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = if (isError) "Dismiss error" else "Dismiss message"
                )
            }
        }
    }
}

@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Theme Preference",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeSelected(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DateTimeSection(
    use24HourFormat: Boolean,
    firstDayOfWeek: DayOfWeek,
    dateFormat: DateFormatPreference,
    onUpdateTimeFormat: (Boolean) -> Unit,
    onUpdateFirstDayOfWeek: (DayOfWeek) -> Unit,
    onUpdateDateFormat: (DateFormatPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Date & Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 24-Hour Format Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = use24HourFormat,
                        role = Role.Switch,
                        onValueChange = onUpdateTimeFormat
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use 24-Hour Format", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("e.g. 14:30 instead of 2:30 PM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = use24HourFormat,
                    onCheckedChange = null
                )
            }

            HorizontalDivider()

            // First Day of Week
            Text("First Day of Week", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                    FilterChip(
                        selected = firstDayOfWeek == day,
                        onClick = { onUpdateFirstDayOfWeek(day) },
                        label = { Text(day.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            HorizontalDivider()

            // Date Format Preference
            Text("Date Format", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFormatPreference.entries.forEach { fmt ->
                    FilterChip(
                        selected = dateFormat == fmt,
                        onClick = { onUpdateDateFormat(fmt) },
                        label = {
                            Text(
                                when (fmt) {
                                    DateFormatPreference.SYSTEM_DEFAULT -> "Default"
                                    DateFormatPreference.ISO -> "YYYY-MM-DD"
                                    DateFormatPreference.DMY -> "DD/MM/YYYY"
                                    DateFormatPreference.MDY -> "MM/DD/YYYY"
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EventDefaultsSection(
    defaultPriority: Priority,
    defaultDurationMinutes: Int,
    defaultSnoozeMinutes: Int,
    onUpdatePriority: (Priority) -> Unit,
    onUpdateDuration: (Int) -> Unit,
    onUpdateSnooze: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Event Defaults",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Default Priority: 2x2 grid prevents awkward vertical text wrapping on compact screens
            Text("Default Priority", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Priority.LOW, Priority.NORMAL).forEach { p ->
                        FilterChip(
                            selected = defaultPriority == p,
                            onClick = { onUpdatePriority(p) },
                            label = { Text(p.name, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Priority.HIGH, Priority.CRITICAL).forEach { p ->
                        FilterChip(
                            selected = defaultPriority == p,
                            onClick = { onUpdatePriority(p) },
                            label = { Text(p.name, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Default Duration
            Text("Default Event Duration", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30, 60, 120).forEach { mins ->
                    FilterChip(
                        selected = defaultDurationMinutes == mins,
                        onClick = { onUpdateDuration(mins) },
                        label = { Text("${mins}m", maxLines = 1) }
                    )
                }
            }

            HorizontalDivider()

            // Default Snooze
            Text("Default Snooze Duration", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 30).forEach { mins ->
                    FilterChip(
                        selected = defaultSnoozeMinutes == mins,
                        onClick = { onUpdateSnooze(mins) },
                        label = { Text("${mins}m", maxLines = 1) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationsSection(
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    quietHours: QuietHours,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onUpdateQuietHours: (QuietHours) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Notifications & Quiet Hours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Sound Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notification Sound", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onToggleSound
                )
            }

            // Vibration Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = onToggleVibration
                )
            }

            HorizontalDivider()

            // Quiet Hours Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quiet Hours", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Silence or delay reminders during rest periods (${quietHours.startLocalTime} - ${quietHours.endLocalTime})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = quietHours.enabled,
                    onCheckedChange = { onUpdateQuietHours(quietHours.copy(enabled = it)) }
                )
            }

            if (quietHours.enabled) {
                Text("Quiet Hours Policy", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuietHoursPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = quietHours.policy == policy,
                            onClick = { onUpdateQuietHours(quietHours.copy(policy = policy)) },
                            label = { Text(policy.name) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Allow Critical Reminders During Quiet Hours", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = quietHours.allowCritical,
                        onCheckedChange = { onUpdateQuietHours(quietHours.copy(allowCritical = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryManagementSection(
    categories: List<Category>,
    onAddCategoryClick: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddCategoryClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add category")
                }
            }

            if (categories.isEmpty()) {
                Text("No categories found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                categories.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row {
                            IconButton(onClick = { onEditCategory(cat) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit category", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete category", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventTypeManagementSection(
    eventTypes: List<EventType>,
    onAddEventTypeClick: () -> Unit,
    onEditEventType: (EventType) -> Unit,
    onDeleteEventType: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Event Types",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddEventTypeClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add event type")
                }
            }

            if (eventTypes.isEmpty()) {
                Text("No event types found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                eventTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(type.name, style = MaterialTheme.typography.bodyMedium)
                                Text(type.key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row {
                            IconButton(onClick = { onEditEventType(type) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit event type", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteEventType(type.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete event type", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupRestoreSection(
    selectedStrategy: RestoreStrategy,
    isLoading: Boolean,
    isExporting: Boolean,
    isImporting: Boolean,
    onSelectStrategy: (RestoreStrategy) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Local Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Export an encrypted/secure offline archive of your reminders, taxonomy, and settings, or restore from an existing backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Export Button
            Button(
                onClick = onExportClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting...")
                } else {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Backup")
                }
            }

            HorizontalDivider()

            // Restore Strategy Options
            Text(
                text = "Restore Conflict Strategy",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column {
                StrategyOption(
                    title = "Replace All",
                    description = "Completely clears current data and restores clean snapshot from backup.",
                    selected = selectedStrategy == RestoreStrategy.REPLACE_ALL,
                    onClick = { onSelectStrategy(RestoreStrategy.REPLACE_ALL) }
                )

                StrategyOption(
                    title = "Merge & Overwrite",
                    description = "Overwrites matching reminders with backup versions, keeping others.",
                    selected = selectedStrategy == RestoreStrategy.MERGE_OVERWRITE,
                    onClick = { onSelectStrategy(RestoreStrategy.MERGE_OVERWRITE) }
                )

                StrategyOption(
                    title = "Merge & Keep Existing",
                    description = "Only imports new reminders from backup; existing records remain untouched.",
                    selected = selectedStrategy == RestoreStrategy.MERGE_KEEP_EXISTING,
                    onClick = { onSelectStrategy(RestoreStrategy.MERGE_KEEP_EXISTING) }
                )
            }

            // Import Button
            OutlinedButton(
                onClick = onImportClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restoring...")
                } else {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Backup File")
                }
            }
        }
    }
}

@Composable
private fun StrategyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RestoreSummaryCard(
    summary: RestoreSummary,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Last Restore Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("• Reminders restored: ${summary.eventsRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Categories restored: ${summary.categoriesRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Groups restored: ${summary.groupsRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Event types restored: ${summary.eventTypesRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Tags restored: ${summary.tagsRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Exceptions restored: ${summary.exceptionsRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Dependencies restored: ${summary.dependenciesRestored}", style = MaterialTheme.typography.bodySmall)
            Text("• Attachments metadata restored: ${summary.attachmentsRestored}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrivacyNoticeCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Offline Security: Backups and preferences are stored strictly on your local device. No data is ever transmitted across the internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutPlanerizeCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Planerize",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Plan Your Time. Organize Your Life.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Version 1.1.0 • 100% Offline & Private",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ==========================================
// DIALOGS
// ==========================================

@Composable
fun CategoryAddDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var catName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            OutlinedTextField(
                value = catName,
                onValueChange = { catName = it },
                label = { Text("Category Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (catName.isNotBlank()) {
                        onSave(catName)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryEditDialog(
    category: Category,
    onDismiss: () -> Unit,
    onUpdate: (Category) -> Unit
) {
    var catName by remember(category) { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = {
            OutlinedTextField(
                value = catName,
                onValueChange = { catName = it },
                label = { Text("Category Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (catName.isNotBlank()) {
                        onUpdate(category.copy(name = catName))
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EventTypeAddDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, categoryId: String) -> Unit
) {
    var typeName by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Event Type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typeName,
                    onValueChange = { typeName = it },
                    label = { Text("Event Type Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select Category:", style = MaterialTheme.typography.bodySmall)
                categories.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedCatId == cat.id,
                                onClick = { selectedCatId = cat.id }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedCatId == cat.id, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (typeName.isNotBlank() && selectedCatId.isNotBlank()) {
                        onSave(typeName, selectedCatId)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EventTypeEditDialog(
    eventType: EventType,
    onDismiss: () -> Unit,
    onUpdate: (EventType) -> Unit
) {
    var typeName by remember(eventType) { mutableStateOf(eventType.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Event Type") },
        text = {
            OutlinedTextField(
                value = typeName,
                onValueChange = { typeName = it },
                label = { Text("Event Type Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (typeName.isNotBlank()) {
                        onUpdate(eventType.copy(name = typeName))
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ResetSettingsConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset All Settings?") },
        text = {
            Text("This will restore default theme, time format, event defaults, and quiet hours. Reminders and categories will remain untouched.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
