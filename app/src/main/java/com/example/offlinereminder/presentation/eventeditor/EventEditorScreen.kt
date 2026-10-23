package com.example.offlinereminder.presentation.eventeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.presentation.attachment.AttachmentHelper
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
    var showCustomRepeatDialog by remember { mutableStateOf(false) }
    var expandedConflicts by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saveResult = AttachmentHelper.saveAttachmentFromUri(context, uri)
            if (saveResult is Result.Success) {
                onAction(EventEditorAction.AddAttachment(saveResult.data))
            }
        }
    }
    val defaultStartDate = remember(uiState.temporal) {
        (uiState.temporal?.startLocalDateTime() ?: LocalDateTime.now()).toLocalDate()
    }
    var showMoreOptions by remember {
        mutableStateOf(
            uiState.isEditing && (
                uiState.locationDisplayName.isNotBlank() ||
                uiState.priority != Priority.NORMAL ||
                uiState.groupIds.isNotEmpty() ||
                uiState.tagIds.isNotEmpty() ||
                (uiState.checklist?.items?.isNotEmpty() == true) ||
                uiState.attachments.isNotEmpty()
            )
        )
    }

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
                        .imePadding()
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

                    // Conflict Guidance Banner (compact & non-obtrusive)
                    if (uiState.detectedConflicts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedConflicts = !expandedConflicts }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Conflict: ${uiState.detectedConflicts.size} overlapping event${if (uiState.detectedConflicts.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Text(
                                        text = if (expandedConflicts) "Hide" else "Details",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (expandedConflicts) {
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
                    }

                    // Validation guidance banner when Save cannot proceed
                    if (!uiState.canSave && uiState.title.isNotBlank()) {
                        val explanation = uiState.missingFieldExplanation
                        if (explanation != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Required to save: $explanation",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // 1. Title Input (required)
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { onAction(EventEditorAction.UpdateTitle(it)) },
                        label = { Text("Title") },
                        placeholder = { Text("What do you need to remember?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Event title input" },
                        singleLine = true,
                        isError = uiState.validationError != null && uiState.title.isBlank()
                    )

                    // 2. Note Input (optional)
                    OutlinedTextField(
                        value = uiState.notes ?: "",
                        onValueChange = { onAction(EventEditorAction.UpdateNotes(it.ifBlank { null })) },
                        label = { Text("Note") },
                        placeholder = { Text("Add a note... (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Event notes input" },
                        maxLines = 3
                    )

                    // Date & Time computation
                    val currentTemporal = uiState.temporal ?: EventDateTime.DateTime(
                        dateTime = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0),
                        zoneId = ZoneId.systemDefault()
                    )
                    val context = LocalContext.current
                    val startLdt = currentTemporal.startLocalDateTime()
                    val isAllDay = uiState.dateMode == EventDateMode.DATE_ONLY || uiState.dateMode == EventDateMode.DATE_RANGE

                    // 3. Date Card (required)
                    EditorSectionCard(title = "Date") {
                        val today = LocalDate.now()
                        val tomorrow = today.plusDays(1)
                        val currentDate = startLdt.toLocalDate()
                        val dateLabel = when (currentDate) {
                            today -> "Today, ${DateTimeFormatters.formatDate(currentDate)}"
                            tomorrow -> "Tomorrow, ${DateTimeFormatters.formatDate(currentDate)}"
                            else -> DateTimeFormatters.formatDate(currentDate)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val initialYear = startLdt.year
                                    val initialMonth = startLdt.monthValue - 1
                                    val initialDay = startLdt.dayOfMonth
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val newDate = LocalDate.of(y, m + 1, d)
                                            val newLdt = newDate.atTime(startLdt.toLocalTime())
                                            onAction(EventEditorAction.UpdateTemporal(updateTemporalDate(currentTemporal, newDate, newLdt)))
                                        },
                                        initialYear, initialMonth, initialDay
                                    ).show()
                                }
                                .semantics { contentDescription = "Pick date" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = currentDate == today,
                                onClick = {
                                    val newLdt = today.atTime(startLdt.toLocalTime())
                                    onAction(EventEditorAction.UpdateTemporal(updateTemporalDate(currentTemporal, today, newLdt)))
                                },
                                label = { Text("Today") }
                            )
                            FilterChip(
                                selected = currentDate == tomorrow,
                                onClick = {
                                    val newLdt = tomorrow.atTime(startLdt.toLocalTime())
                                    onAction(EventEditorAction.UpdateTemporal(updateTemporalDate(currentTemporal, tomorrow, newLdt)))
                                },
                                label = { Text("Tomorrow") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val nextWeek = currentDate.plusWeeks(1)
                                    val newLdt = nextWeek.atTime(startLdt.toLocalTime())
                                    onAction(EventEditorAction.UpdateTemporal(updateTemporalDate(currentTemporal, nextWeek, newLdt)))
                                },
                                label = { Text("+1 Week") }
                            )
                        }
                    }

                    // 4. Time Card (optional depending on event mode)
                    EditorSectionCard(title = "Time") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAllDay) "All-day event" else "Specific time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "All-Day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = isAllDay,
                                    onCheckedChange = { allDay ->
                                        if (allDay) {
                                            onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_ONLY))
                                        } else {
                                            onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_TIME))
                                        }
                                    },
                                    modifier = Modifier.semantics { contentDescription = "All day toggle" }
                                )
                            }
                        }

                        if (!isAllDay) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val initialHour = startLdt.hour
                                        val initialMin = startLdt.minute
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, h, m ->
                                                val newTime = LocalTime.of(h, m)
                                                onAction(EventEditorAction.UpdateTemporal(updateTemporalTime(currentTemporal, newTime)))
                                            },
                                            initialHour, initialMin, false
                                        ).show()
                                    }
                                    .semantics { contentDescription = "Pick time" }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    val timeFormat = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                                    Text(
                                        text = startLdt.format(timeFormat),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 5. Repeat Card (optional)
                    EditorSectionCard(title = "Repeat") {
                        val currentFrequency = uiState.recurrenceRule?.frequency
                        val rule = uiState.recurrenceRule

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = currentFrequency == null,
                                onClick = { onAction(EventEditorAction.UpdateRecurrence(null)) },
                                label = { Text("Never") }
                            )
                            listOf(
                                RecurrenceFrequency.DAILY to "Daily",
                                RecurrenceFrequency.WEEKLY to "Weekly",
                                RecurrenceFrequency.MONTHLY to "Monthly",
                                RecurrenceFrequency.YEARLY to "Yearly"
                            ).forEach { (freq, label) ->
                                val isStandardSelected = currentFrequency == freq && (rule?.interval ?: 1) == 1 && (rule?.endCondition is RecurrenceEnd.Never)
                                FilterChip(
                                    selected = isStandardSelected,
                                    onClick = {
                                        val days = if (freq == RecurrenceFrequency.WEEKLY) {
                                            rule?.daysOfWeek?.ifEmpty { setOf(startLdt.dayOfWeek) } ?: setOf(startLdt.dayOfWeek)
                                        } else emptySet()
                                        onAction(
                                            EventEditorAction.UpdateRecurrence(
                                                RecurrenceRule(
                                                    frequency = freq,
                                                    interval = 1,
                                                    daysOfWeek = days,
                                                    endCondition = RecurrenceEnd.Never
                                                )
                                            )
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                            val isCustom = rule != null && (rule.interval > 1 || rule.endCondition !is RecurrenceEnd.Never)
                            FilterChip(
                                selected = isCustom,
                                onClick = { showCustomRepeatDialog = true },
                                label = {
                                    Text(
                                        if (isCustom) {
                                            val unit = when (rule?.frequency) {
                                                RecurrenceFrequency.DAILY -> if (rule.interval == 1) "day" else "days"
                                                RecurrenceFrequency.WEEKLY -> if (rule.interval == 1) "week" else "weeks"
                                                RecurrenceFrequency.MONTHLY -> if (rule.interval == 1) "month" else "months"
                                                RecurrenceFrequency.YEARLY -> if (rule.interval == 1) "year" else "years"
                                                null -> "Custom"
                                            }
                                            "Custom (Every ${rule?.interval} $unit)"
                                        } else "Custom..."
                                    )
                                }
                            )
                        }

                        if (currentFrequency == RecurrenceFrequency.WEEKLY) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Repeat on:", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DayOfWeek.entries.forEach { day ->
                                    val isSelected = rule?.daysOfWeek?.contains(day) == true
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val currentDays = rule?.daysOfWeek ?: emptySet()
                                            val updatedDays = if (isSelected) {
                                                if (currentDays.size > 1) currentDays - day else currentDays
                                            } else {
                                                currentDays + day
                                            }
                                            rule?.let {
                                                onAction(EventEditorAction.UpdateRecurrence(it.copy(daysOfWeek = updatedDays)))
                                            }
                                        },
                                        label = { Text(day.name.take(1)) },
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Reminders Card (optional)
                    EditorSectionCard(title = "Reminders") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.notificationSchedules.isNotEmpty()) {
                                uiState.notificationSchedules.forEach { schedule ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (schedule.deliveryType == ReminderDeliveryType.ALARM) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = formatReminderTiming(schedule.offsetDuration),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = schedule.deliveryType.name.lowercase().replaceFirstChar { it.uppercase() },
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
                            } else {
                                Text(
                                    text = "No reminders set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { showAddReminderDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Add reminder" }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add reminder")
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // Divider & "+ More options" Expandable Toggle
                    // ----------------------------------------------------
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedButton(
                        onClick = { showMoreOptions = !showMoreOptions },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "More options" },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (showMoreOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showMoreOptions) "Fewer options" else "+ More options",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // ----------------------------------------------------
                    // Advanced Options (Hidden by default, optional)
                    // ----------------------------------------------------
                    if (showMoreOptions) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Category (Optional)
                            EditorSectionCard(title = "Category") {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.availableCategories.forEach { cat ->
                                        FilterChip(
                                            selected = uiState.categoryId == cat.id,
                                            onClick = { onAction(EventEditorAction.SelectCategory(cat.id)) },
                                            label = { Text(cat.name) },
                                            leadingIcon = if (uiState.categoryId == cat.id) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            modifier = Modifier.semantics { contentDescription = "Select category ${cat.name}" }
                                        )
                                    }
                                }
                            }

                            // Event Type (Optional)
                            EditorSectionCard(title = "Event Type") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val selectedType = uiState.availableEventTypes.firstOrNull { it.id == uiState.eventTypeId }
                                    if (selectedType != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        ) {
                                            Text(
                                                text = "Selected: ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(selectedType.name, fontWeight = FontWeight.Bold) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }

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
                                            val inCat = uiState.availableEventTypes.filter { it.categoryId == uiState.categoryId }
                                            if (inCat.isNotEmpty()) inCat else uiState.availableEventTypes
                                        } else {
                                            uiState.filteredEventTypes
                                        }

                                        displayTypes.forEach { type ->
                                            FilterChip(
                                                selected = uiState.eventTypeId == type.id,
                                                onClick = { onAction(EventEditorAction.SelectEventType(type.id)) },
                                                label = { Text(type.name) },
                                                leadingIcon = if (uiState.eventTypeId == type.id) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                } else null,
                                                modifier = Modifier.semantics { contentDescription = "Select event type ${type.name}" }
                                            )
                                        }
                                    }
                                }
                            }

                            // Location (Optional)
                            EditorSectionCard(title = "Location") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = uiState.locationDisplayName,
                                        onValueChange = { onAction(EventEditorAction.UpdateLocation(it)) },
                                        label = { Text("Location") },
                                        placeholder = { Text("e.g., Conference Room B, Downtown") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics { contentDescription = "Location input" },
                                        singleLine = true
                                    )
                                    if (uiState.availableSavedLocations.isNotEmpty()) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            uiState.availableSavedLocations.take(4).forEach { loc ->
                                                FilterChip(
                                                    selected = uiState.locationDisplayName == loc.name,
                                                    onClick = { onAction(EventEditorAction.SelectSavedLocation(loc)) },
                                                    label = { Text(loc.name) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Priority (Optional)
                            EditorSectionCard(title = "Priority") {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Priority.entries.forEach { p ->
                                        FilterChip(
                                            selected = uiState.priority == p,
                                            onClick = { onAction(EventEditorAction.UpdatePriority(p)) },
                                            label = { Text(p.name, maxLines = 1) },
                                            modifier = Modifier.semantics { contentDescription = "Select priority ${p.name}" }
                                        )
                                    }
                                }
                            }

                            // Groups & Tags (Optional)
                            if (uiState.availableGroups.isNotEmpty() || uiState.availableTags.isNotEmpty()) {
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
                            }

                            // Checklist (Optional)
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

                            // Attachments (Optional)
                            EditorSectionCard(
                                title = "Attachments (${uiState.attachments.size})",
                                action = {
                                    TextButton(onClick = { attachmentPickerLauncher.launch("*/*") }) {
                                        Text("+ Add File")
                                    }
                                }
                            ) {
                                if (uiState.attachments.isEmpty()) {
                                    Text(
                                        text = "No files attached. Tap '+ Add File' to attach documents or images.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        uiState.attachments.forEach { att ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AttachFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = att.fileName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${AttachmentHelper.formatFileSize(att.fileSizeBytes)} • ${att.mimeType}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        AttachmentHelper.deleteAttachmentFile(context, att)
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

                            // Advanced Date & Time (Optional)
                            EditorSectionCard(title = "Advanced Date & Time") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Date Mode", style = MaterialTheme.typography.labelSmall)
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

                                    HorizontalDivider()

                                    Text(
                                        text = "Timezone: ${uiState.timeZoneId} (Device default)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Sub-event Parent (Optional)
                            if (uiState.parentEventId != null) {
                                EditorSectionCard(title = "Parent Event") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uiState.parentEventTitle ?: "Parent ID: ${uiState.parentEventId}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(onClick = { onAction(EventEditorAction.SetParentEventId(null)) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove parent relationship")
                                        }
                                    }
                                }
                            }

                            // Templates (Optional)
                            if (uiState.availableTemplates.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showTemplatesDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply Template")
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

    if (showAddReminderDialog) {
        AddReminderDialog(
            onDismiss = { showAddReminderDialog = false },
            onAdd = { schedule ->
                onAction(EventEditorAction.AddNotificationSchedule(schedule))
            }
        )
    }

    if (showCustomRepeatDialog) {
        CustomRepeatDialog(
            initialRule = uiState.recurrenceRule,
            defaultStartDate = defaultStartDate,
            onDismiss = { showCustomRepeatDialog = false },
            onConfirm = { rule ->
                onAction(EventEditorAction.UpdateRecurrence(rule))
            }
        )
    }

    if (uiState.showRecurringSeriesDialog) {
        AlertDialog(
            onDismissRequest = { onAction(EventEditorAction.DismissRecurringSeriesDialog) },
            title = { Text("Edit Recurring Event") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This is a recurring event. How would you like to apply your changes?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onAction(EventEditorAction.SaveRecurringSeries(com.example.offlinereminder.domain.usecase.RecurringSeriesEditMode.THIS_OCCURRENCE))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("This Occurrence Only")
                    }
                    Button(
                        onClick = {
                            onAction(EventEditorAction.SaveRecurringSeries(com.example.offlinereminder.domain.usecase.RecurringSeriesEditMode.THIS_AND_FOLLOWING))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("This and All Following")
                    }
                    Button(
                        onClick = {
                            onAction(EventEditorAction.SaveRecurringSeries(com.example.offlinereminder.domain.usecase.RecurringSeriesEditMode.ENTIRE_SERIES))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Entire Series")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onAction(EventEditorAction.DismissRecurringSeriesDialog) }) {
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
    action: @Composable (() -> Unit)? = null,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

private fun updateTemporalDate(current: EventDateTime, newDate: LocalDate, newLdt: LocalDateTime): EventDateTime {
    return when (current) {
        is EventDateTime.DateOnly -> EventDateTime.DateOnly(newDate)
        is EventDateTime.DateRange -> {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(current.startDate, current.endDate)
            EventDateTime.DateRange(newDate, newDate.plusDays(daysBetween.coerceAtLeast(0)))
        }
        is EventDateTime.DateTime -> EventDateTime.DateTime(newLdt, current.zoneId)
        is EventDateTime.DateTimeRange -> {
            val diff = java.time.Duration.between(current.startDateTime, current.endDateTime)
            EventDateTime.DateTimeRange(newLdt, newLdt.plus(diff), current.zoneId)
        }
    }
}

private fun updateTemporalTime(current: EventDateTime, newTime: LocalTime): EventDateTime {
    val newLdt = current.startLocalDateTime().toLocalDate().atTime(newTime)
    return when (current) {
        is EventDateTime.DateOnly -> EventDateTime.DateTime(newLdt)
        is EventDateTime.DateRange -> EventDateTime.DateTimeRange(
            newLdt,
            current.endDate.atTime(newTime).plusHours(1)
        )
        is EventDateTime.DateTime -> EventDateTime.DateTime(newLdt, current.zoneId)
        is EventDateTime.DateTimeRange -> {
            val diff = java.time.Duration.between(current.startDateTime, current.endDateTime)
            EventDateTime.DateTimeRange(newLdt, newLdt.plus(diff), current.zoneId)
        }
    }
}

fun formatReminderTiming(duration: Duration): String {
    if (duration.isZero) return "At event"
    val minutes = duration.toMinutes()
    val hours = duration.toHours()
    val days = duration.toDays()
    return when {
        days >= 7 && days % 7 == 0L -> {
            val weeks = days / 7
            "$weeks week${if (weeks > 1) "s" else ""} before"
        }
        days > 0 && hours % 24 == 0L -> {
            "$days day${if (days > 1) "s" else ""} before"
        }
        hours > 0 && minutes % 60 == 0L -> {
            "$hours hour${if (hours > 1) "s" else ""} before"
        }
        else -> "$minutes minute${if (minutes > 1) "s" else ""} before"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (NotificationSchedule) -> Unit
) {
    var deliveryType by remember { mutableStateOf(ReminderDeliveryType.NOTIFICATION) }
    var selectedPresetIndex by remember { mutableStateOf<Int?>(0) }
    var isCustom by remember { mutableStateOf(false) }
    var customValueText by remember { mutableStateOf("10") }
    var customUnit by remember { mutableStateOf("Minutes") }

    val presets = listOf(
        "At event" to Duration.ZERO,
        "5m before" to Duration.ofMinutes(5),
        "15m before" to Duration.ofMinutes(15),
        "30m before" to Duration.ofMinutes(30),
        "1h before" to Duration.ofHours(1),
        "2h before" to Duration.ofHours(2),
        "1d before" to Duration.ofDays(1),
        "1w before" to Duration.ofDays(7)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reminder") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Delivery Mode", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = deliveryType == ReminderDeliveryType.NOTIFICATION,
                        onClick = { deliveryType = ReminderDeliveryType.NOTIFICATION },
                        label = { Text("Notification", maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = deliveryType == ReminderDeliveryType.ALARM,
                        onClick = { deliveryType = ReminderDeliveryType.ALARM },
                        label = { Text("Alarm", maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                Text("Timing", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEachIndexed { index, (label, _) ->
                        FilterChip(
                            selected = !isCustom && selectedPresetIndex == index,
                            onClick = {
                                isCustom = false
                                selectedPresetIndex = index
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                    FilterChip(
                        selected = isCustom,
                        onClick = { isCustom = true },
                        label = { Text("Custom...", style = MaterialTheme.typography.bodySmall) }
                    )
                }

                if (isCustom) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Custom Timing", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customValueText,
                            onValueChange = { customValueText = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Value") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            listOf("Minutes", "Hours", "Days", "Weeks").forEach { unit ->
                                FilterChip(
                                    selected = customUnit == unit,
                                    onClick = { customUnit = unit },
                                    label = { Text(unit, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    Text(
                        text = "before event",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val duration: Duration = if (isCustom) {
                        val num = customValueText.toLongOrNull()?.coerceAtLeast(1) ?: 10L
                        when (customUnit) {
                            "Minutes" -> Duration.ofMinutes(num)
                            "Hours" -> Duration.ofHours(num)
                            "Days" -> Duration.ofDays(num)
                            "Weeks" -> Duration.ofDays(num * 7)
                            else -> Duration.ofMinutes(num)
                        }
                    } else {
                        presets[selectedPresetIndex ?: 0].second
                    }

                    val triggerType = if (duration.isZero) {
                        com.example.offlinereminder.domain.model.NotificationTriggerType.AT_START
                    } else {
                        com.example.offlinereminder.domain.model.NotificationTriggerType.BEFORE_START
                    }

                    onAdd(
                        NotificationSchedule(
                            offsetDuration = duration,
                            triggerType = triggerType,
                            deliveryType = deliveryType
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomRepeatDialog(
    initialRule: RecurrenceRule?,
    defaultStartDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule) -> Unit
) {
    val context = LocalContext.current
    var intervalText by remember { mutableStateOf((initialRule?.interval ?: 1).toString()) }
    var selectedFrequency by remember {
        mutableStateOf(initialRule?.frequency ?: RecurrenceFrequency.DAILY)
    }
    var selectedDays by remember {
        mutableStateOf(
            if (initialRule?.frequency == RecurrenceFrequency.WEEKLY && initialRule.daysOfWeek.isNotEmpty()) {
                initialRule.daysOfWeek
            } else {
                setOf(defaultStartDate.dayOfWeek)
            }
        )
    }

    var endType by remember {
        mutableStateOf(
            when (initialRule?.endCondition) {
                is RecurrenceEnd.UntilDate -> 1
                is RecurrenceEnd.Count -> 2
                else -> 0
            }
        )
    }
    var untilDate by remember {
        mutableStateOf(
            (initialRule?.endCondition as? RecurrenceEnd.UntilDate)?.date ?: defaultStartDate.plusMonths(3)
        )
    }
    var countText by remember {
        mutableStateOf(
            ((initialRule?.endCondition as? RecurrenceEnd.Count)?.maxOccurrences ?: 5).toString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Repeat") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interval & Unit
                Text("Repeat every", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(3) },
                        modifier = Modifier.width(70.dp),
                        singleLine = true
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        listOf(
                            RecurrenceFrequency.DAILY to "Days",
                            RecurrenceFrequency.WEEKLY to "Weeks",
                            RecurrenceFrequency.MONTHLY to "Months",
                            RecurrenceFrequency.YEARLY to "Years"
                        ).forEach { (freq, label) ->
                            FilterChip(
                                selected = selectedFrequency == freq,
                                onClick = { selectedFrequency = freq },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }

                // Weekday picker if Weekly
                if (selectedFrequency == RecurrenceFrequency.WEEKLY) {
                    Text("Repeat on", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            val isSelected = selectedDays.contains(day)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedDays = if (isSelected) {
                                        if (selectedDays.size > 1) selectedDays - day else selectedDays
                                    } else {
                                        selectedDays + day
                                    }
                                },
                                label = { Text(day.name.take(1)) },
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // End condition
                Text("Ends", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = endType == 0,
                            onClick = { endType = 0 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Never", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = endType == 1,
                            onClick = { endType = 1 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("On date: ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                endType = 1
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d -> untilDate = LocalDate.of(y, m + 1, d) },
                                    untilDate.year,
                                    untilDate.monthValue - 1,
                                    untilDate.dayOfMonth
                                ).show()
                            },
                            enabled = endType == 1
                        ) {
                            Text(DateTimeFormatters.formatDate(untilDate), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = endType == 2,
                            onClick = { endType = 2 }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("After ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = countText,
                            onValueChange = { countText = it.filter { ch -> ch.isDigit() }.take(3) },
                            modifier = Modifier.width(65.dp),
                            singleLine = true,
                            enabled = endType == 2
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("occurrences", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val endCondition = when (endType) {
                        0 -> RecurrenceEnd.Never
                        1 -> RecurrenceEnd.UntilDate(untilDate)
                        2 -> {
                            val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 5
                            RecurrenceEnd.Count(count)
                        }
                        else -> RecurrenceEnd.Never
                    }
                    val days = if (selectedFrequency == RecurrenceFrequency.WEEKLY) selectedDays else emptySet()
                    val rule = RecurrenceRule(
                        frequency = selectedFrequency,
                        interval = interval,
                        daysOfWeek = days,
                        endCondition = endCondition
                    )
                    onConfirm(rule)
                    onDismiss()
                }
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
