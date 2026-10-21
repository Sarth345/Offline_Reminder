package com.example.offlinereminder.presentation.eventeditor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.presentation.components.DateTimeFormatters
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.LoadingView
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Composable
fun EventEditorScreen(
    eventId: String?,
    initialDate: String?,
    initialEventTypeId: String?,
    viewModel: EventEditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId, initialDate, initialEventTypeId) {
        if (eventId != null) {
            viewModel.onAction(EventEditorAction.InitWithEvent(eventId))
        } else {
            val parsedDate = try {
                initialDate?.let { LocalDate.parse(it) }
            } catch (_: Exception) {
                null
            }
            viewModel.onAction(EventEditorAction.InitWithDefaults(parsedDate, initialEventTypeId))
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    EventEditorContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorContent(
    uiState: EventEditorUiState,
    onAction: (EventEditorAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newChecklistText by remember { mutableStateOf("") }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditing) "Edit Reminder" else "New Reminder")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Cancel and go back" }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (uiState.availableTemplates.isNotEmpty() && !uiState.isEditing) {
                        IconButton(
                            onClick = { showTemplatesDialog = true },
                            modifier = Modifier.semantics { contentDescription = "Choose template" }
                        ) {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = "Templates")
                        }
                    }
                    Button(
                        onClick = { onAction(EventEditorAction.SaveEvent) },
                        enabled = uiState.canSave,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .semantics { contentDescription = "Save event" }
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.errorMessage != null) {
                ErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(EventEditorAction.ClearError) }
                )
            }

            if (uiState.validationError != null) {
                ErrorBanner(
                    message = uiState.validationError,
                    onDismiss = { onAction(EventEditorAction.ClearError) }
                )
            }

            if (uiState.isLoading) {
                LoadingView(message = "Loading...")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sub-event parent banner
                    if (uiState.parentEventTitle != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Sub-event",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Parent: ${uiState.parentEventTitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Conflict Guidance Banner
                    if (uiState.detectedConflicts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Schedule Conflict Detected (${uiState.detectedConflicts.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                uiState.detectedConflicts.forEach { conflict ->
                                    Text(
                                        text = "• Overlaps with \"${conflict.existingEventTitle}\" (${DateTimeFormatters.formatDateTime(conflict.existingOccurrence.effectiveStartDateTime)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // 1. Title Input
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { onAction(EventEditorAction.UpdateTitle(it)) },
                        label = { Text("Title *") },
                        placeholder = { Text("e.g., Dentist Appointment") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Event title input" },
                        singleLine = true,
                        isError = uiState.validationError != null && uiState.title.isBlank()
                    )

                    // 2. Notes Input
                    OutlinedTextField(
                        value = uiState.notes ?: "",
                        onValueChange = { onAction(EventEditorAction.UpdateNotes(it.ifBlank { null })) },
                        label = { Text("Notes (optional)") },
                        placeholder = { Text("Add additional details...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Event notes input" },
                        maxLines = 4
                    )

                    // 3. Location Input
                    OutlinedTextField(
                        value = uiState.locationDisplayName,
                        onValueChange = { onAction(EventEditorAction.UpdateLocation(it)) },
                        label = { Text("Location (optional)") },
                        placeholder = { Text("e.g., Conference Room B, Downtown") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 4. Priority Selection
                    EditorSectionCard(title = "Priority") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Priority.entries.forEach { p ->
                                FilterChip(
                                    selected = uiState.priority == p,
                                    onClick = { onAction(EventEditorAction.UpdatePriority(p)) },
                                    label = { Text(p.name) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { contentDescription = "Select priority ${p.name}" }
                                )
                            }
                        }
                    }

                    // 5. Category Selection
                    EditorSectionCard(title = "Category *") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.availableCategories.forEach { cat ->
                                FilterChip(
                                    selected = uiState.categoryId == cat.id,
                                    onClick = { onAction(EventEditorAction.SelectCategory(cat.id)) },
                                    label = { Text(cat.name) },
                                    modifier = Modifier.semantics { contentDescription = "Select category ${cat.name}" }
                                )
                            }
                        }
                    }

                    // 6. Event Type Selection (with search)
                    EditorSectionCard(title = "Event Type *") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.eventTypeSearchQuery,
                                onValueChange = { onAction(EventEditorAction.SearchEventType(it)) },
                                placeholder = { Text("Search event types...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Search event types" },
                                singleLine = true
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val displayTypes = if (uiState.eventTypeSearchQuery.isBlank()) {
                                    uiState.availableEventTypes
                                } else {
                                    uiState.filteredEventTypes
                                }

                                displayTypes.forEach { type ->
                                    FilterChip(
                                        selected = uiState.eventTypeId == type.id,
                                        onClick = { onAction(EventEditorAction.SelectEventType(type.id)) },
                                        label = { Text(type.name) },
                                        modifier = Modifier.semantics { contentDescription = "Select event type ${type.name}" }
                                    )
                                }
                            }
                        }
                    }

                    // 7. Date & Time Selection (with Date Mode selector)
                    val currentTemporal = uiState.temporal ?: EventDateTime.DateTime(
                        dateTime = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0),
                        zoneId = ZoneId.systemDefault()
                    )

                    EditorSectionCard(title = "Date & Time Mode") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Date Mode Selector Chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.dateMode == EventDateMode.DATE_TIME,
                                    onClick = { onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_TIME)) },
                                    label = { Text("Date & Time") }
                                )
                                FilterChip(
                                    selected = uiState.dateMode == EventDateMode.DATE_ONLY,
                                    onClick = { onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_ONLY)) },
                                    label = { Text("All-Day") }
                                )
                                FilterChip(
                                    selected = uiState.dateMode == EventDateMode.DATE_RANGE,
                                    onClick = { onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_RANGE)) },
                                    label = { Text("Date Range") }
                                )
                                FilterChip(
                                    selected = uiState.dateMode == EventDateMode.DATE_RANGE_TIME,
                                    onClick = { onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_RANGE_TIME)) },
                                    label = { Text("Date & Time Range") }
                                )
                            }

                            // Current start date/time display
                            Text(
                                text = "Current Bounds: ${DateTimeFormatters.formatEventDateTime(currentTemporal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Quick Date Shifters
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val today = LocalDate.now().atTime(currentTemporal.startLocalDateTime().toLocalTime())
                                        onAction(EventEditorAction.UpdateTemporal(EventDateTime.DateTime(dateTime = today)))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Today")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val tomorrow = LocalDate.now().plusDays(1).atTime(currentTemporal.startLocalDateTime().toLocalTime())
                                        onAction(EventEditorAction.UpdateTemporal(EventDateTime.DateTime(dateTime = tomorrow)))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Tomorrow")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val nextWeek = currentTemporal.startLocalDateTime().plusWeeks(1)
                                        onAction(EventEditorAction.UpdateTemporal(EventDateTime.DateTime(dateTime = nextWeek)))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+1 Week")
                                }
                            }

                            // Timezone Display / Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Timezone: ${uiState.timeZoneId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 8. Recurrence
                    EditorSectionCard(title = "Repeat") {
                        val currentFrequency = uiState.recurrenceRule?.frequency
                        val rule = uiState.recurrenceRule

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = currentFrequency == null,
                                    onClick = { onAction(EventEditorAction.UpdateRecurrence(null)) },
                                    label = { Text("Does not repeat") }
                                )
                                RecurrenceFrequency.entries.forEach { freq ->
                                    FilterChip(
                                        selected = currentFrequency == freq,
                                        onClick = {
                                            val newDays = if (freq == RecurrenceFrequency.WEEKLY) {
                                                rule?.daysOfWeek?.ifEmpty { setOf(LocalDate.now().dayOfWeek) } ?: setOf(LocalDate.now().dayOfWeek)
                                            } else emptySet()
                                            onAction(
                                                EventEditorAction.UpdateRecurrence(
                                                    RecurrenceRule(
                                                        frequency = freq,
                                                        interval = rule?.interval ?: 1,
                                                        daysOfWeek = newDays,
                                                        endCondition = rule?.endCondition ?: RecurrenceEnd.Never
                                                    )
                                                )
                                            )
                                        },
                                        label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }

                            // Weekly days of week selector
                            if (currentFrequency == RecurrenceFrequency.WEEKLY) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Repeat on:", style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    DayOfWeek.entries.forEach { day ->
                                        val isSelected = rule.daysOfWeek.contains(day)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                val updatedDays = if (isSelected) {
                                                    if (rule.daysOfWeek.size > 1) rule.daysOfWeek - day else rule.daysOfWeek
                                                } else {
                                                    rule.daysOfWeek + day
                                                }
                                                onAction(EventEditorAction.UpdateRecurrence(rule.copy(daysOfWeek = updatedDays)))
                                            },
                                            label = { Text(day.name.take(1)) },
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 9. Notification Schedules
                    EditorSectionCard(title = "Reminders") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.notificationSchedules.forEach { schedule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (schedule.deliveryType == ReminderDeliveryType.ALARM) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.secondary
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (schedule.offsetDuration.isZero) "At time of event" else "${schedule.offsetDuration.toMinutes()}m before",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Type: ${schedule.deliveryType.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onAction(EventEditorAction.RemoveNotificationSchedule(schedule.id)) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .semantics { contentDescription = "Remove reminder" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Quick add reminder buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onAction(
                                            EventEditorAction.AddNotificationSchedule(
                                                NotificationSchedule.atStart()
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("At event", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onAction(
                                            EventEditorAction.AddNotificationSchedule(
                                                NotificationSchedule.beforeStart(Duration.ofMinutes(15))
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("15m before", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        onAction(
                                            EventEditorAction.AddNotificationSchedule(
                                                NotificationSchedule(
                                                    offsetDuration = Duration.ZERO,
                                                    triggerType = com.example.offlinereminder.domain.model.NotificationTriggerType.AT_START,
                                                    deliveryType = ReminderDeliveryType.ALARM
                                                )
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Alarm", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // 10. Groups & Tags
                    EditorSectionCard(title = "Groups & Tags") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.availableGroups.isNotEmpty()) {
                                Text("Groups", style = MaterialTheme.typography.labelMedium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.availableGroups.forEach { group ->
                                        FilterChip(
                                            selected = uiState.groupIds.contains(group.id),
                                            onClick = { onAction(EventEditorAction.ToggleGroup(group.id)) },
                                            label = { Text(group.name) }
                                        )
                                    }
                                }
                            }

                            if (uiState.availableTags.isNotEmpty()) {
                                Text("Tags", style = MaterialTheme.typography.labelMedium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.availableTags.forEach { tag ->
                                        FilterChip(
                                            selected = uiState.tagIds.contains(tag.id),
                                            onClick = { onAction(EventEditorAction.ToggleTag(tag.id)) },
                                            label = { Text("#${tag.name}") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 11. Checklist
                    EditorSectionCard(title = "Checklist") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val items = uiState.checklist?.items ?: emptyList()
                            items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.isCompleted,
                                            onCheckedChange = { onAction(EventEditorAction.ToggleChecklistItem(item.id)) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                                        )
                                    }
                                    IconButton(
                                        onClick = { onAction(EventEditorAction.RemoveChecklistItem(item.id)) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove checklist item",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newChecklistText,
                                    onValueChange = { newChecklistText = it },
                                    placeholder = { Text("Add task item...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (newChecklistText.isNotBlank()) {
                                            onAction(EventEditorAction.AddChecklistItem(newChecklistText.trim()))
                                            newChecklistText = ""
                                        }
                                    },
                                    modifier = Modifier.semantics { contentDescription = "Add task item" }
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        }
                    }

                    // 12. Attachments
                    if (uiState.attachments.isNotEmpty()) {
                        EditorSectionCard(title = "Attachments (${uiState.attachments.size})") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.attachments.forEach { att ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = att.fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = att.mimeType,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onAction(EventEditorAction.RemoveAttachment(att.id))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove attachment",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Template Picker Dialog
    if (showTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            title = { Text("Choose a Template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.availableTemplates.forEach { template ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAction(EventEditorAction.ApplyTemplate(template.id))
                                    showTemplatesDialog = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (template.description.isNotBlank()) {
                                    Text(
                                        text = template.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EditorSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
