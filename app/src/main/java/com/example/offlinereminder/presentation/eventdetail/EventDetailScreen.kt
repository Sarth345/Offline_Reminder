package com.example.offlinereminder.presentation.eventdetail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.presentation.components.ChipBadge
import com.example.offlinereminder.presentation.components.DateTimeFormatters
import com.example.offlinereminder.presentation.components.EmptyStateView
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.LoadingView
import com.example.offlinereminder.presentation.components.PriorityBadge
import com.example.offlinereminder.presentation.components.RescheduleDialog
import com.example.offlinereminder.presentation.components.StatusBadge
import java.time.ZoneId

@Composable
fun EventDetailScreen(
    eventId: String,
    viewModel: EventDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.onAction(EventDetailAction.LoadEvent(eventId))
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    EventDetailContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onNavigateToEdit = { onNavigateToEdit(eventId) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDetailContent(
    uiState: EventDetailUiState,
    onAction: (EventDetailAction) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var occurrenceToReschedule by remember { mutableStateOf<EventOccurrence?>(null) }

    if (occurrenceToReschedule != null) {
        RescheduleDialog(
            occurrence = occurrenceToReschedule!!,
            onDismiss = { occurrenceToReschedule = null },
            onConfirm = { newStart ->
                onAction(
                    EventDetailAction.RescheduleOccurrence(
                        occurrence = occurrenceToReschedule!!,
                        newStartDateTime = newStart
                    )
                )
                occurrenceToReschedule = null
            }
        )
    }

    if (showDeleteConfirmDialog) {
        val isAlreadyTrashed = uiState.event?.lifecycleStatus == EventLifecycleStatus.TRASHED
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(if (isAlreadyTrashed) "Delete Permanently?" else "Move to Trash?")
            },
            text = {
                Text(
                    if (isAlreadyTrashed) {
                        "This will permanently delete this event and cancel all future reminders. This action cannot be undone."
                    } else {
                        "Move this event to trash? You can restore it later from Trash."
                    }
                )
            },
            confirmButton = {
                ButtonWithText(
                    text = if (isAlreadyTrashed) "Delete Permanently" else "Move to Trash",
                    isDestructive = true,
                    onClick = {
                        showDeleteConfirmDialog = false
                        if (isAlreadyTrashed) {
                            onAction(EventDetailAction.DeletePermanently)
                        } else {
                            onAction(EventDetailAction.MoveToTrash)
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.event != null) {
                        IconButton(
                            onClick = { onAction(EventDetailAction.ToggleFavorite) },
                            modifier = Modifier.semantics {
                                contentDescription = if (uiState.isFavorite) "Remove from favorites" else "Add to favorites"
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onNavigateToEdit,
                            modifier = Modifier.semantics { contentDescription = "Edit event" }
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                        }

                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.semantics { contentDescription = "Delete event" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
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
                    onDismiss = { onAction(EventDetailAction.ClearError) }
                )
            }

            when {
                uiState.isLoading -> {
                    LoadingView(message = "Loading event...")
                }
                uiState.event == null -> {
                    EmptyStateView(
                        title = "Event not found",
                        message = "The requested event could not be found or has been deleted.",
                        actionLabel = "Go Back",
                        onAction = onNavigateBack
                    )
                }
                else -> {
                    val event = uiState.event
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Badges: Priority, Status, Category, EventType
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PriorityBadge(priority = event.priority)
                            StatusBadge(status = event.lifecycleStatus)
                            uiState.category?.let { cat ->
                                ChipBadge(text = cat.name, containerColor = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            uiState.eventType?.let { type ->
                                ChipBadge(text = type.name, containerColor = MaterialTheme.colorScheme.secondaryContainer, textColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // Description
                        if (event.description.isNotBlank()) {
                            DetailSectionCard(title = "Description") {
                                Text(
                                    text = event.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Date & Time details
                        DetailSectionCard(title = "Schedule") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (event.temporal.isAllDay) {
                                            "${DateTimeFormatters.formatDate(event.temporal.startDate)} (All-day)"
                                        } else {
                                            DateTimeFormatters.formatDateTime(event.temporal.startLocalDateTime())
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                val endDateTime = when (val t = event.temporal) {
                                    is EventDateTime.DateRange -> t.endDate.atTime(23, 59, 59)
                                    is EventDateTime.DateTimeRange -> t.endDateTime
                                    else -> null
                                }
                                endDateTime?.let { end ->
                                    Text(
                                        text = "Ends: ${DateTimeFormatters.formatDateTime(end)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 26.dp)
                                    )
                                }

                                val tzId = when (val t = event.temporal) {
                                    is EventDateTime.DateTime -> t.zoneId.id
                                    is EventDateTime.DateTimeRange -> t.zoneId.id
                                    else -> ZoneId.systemDefault().id
                                }
                                Text(
                                    text = "Timezone: $tzId",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 26.dp)
                                )
                            }
                        }

                        // Recurrence details
                        if (event.recurrenceRule != null) {
                            DetailSectionCard(title = "Recurrence") {
                                val rule = event.recurrenceRule
                                Text(
                                    text = "Repeats ${rule.frequency.name.lowercase()} (every ${rule.interval} ${rule.frequency.name.lowercase().removeSuffix("ly")}(s))",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                when (val end = rule.endCondition) {
                                    is RecurrenceEnd.UntilDate -> {
                                        Text(
                                            text = "Until: ${DateTimeFormatters.formatDate(end.date)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    is RecurrenceEnd.Count -> {
                                        Text(
                                            text = "Limit: ${end.maxOccurrences} occurrences",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RecurrenceEnd.Never -> {}
                                }
                            }
                        }

                        // Notification schedules
                        if (event.notificationSchedules.isNotEmpty()) {
                            DetailSectionCard(title = "Reminders") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    event.notificationSchedules.forEach { schedule ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (schedule.offsetDuration.isZero) "At time of event" else "${schedule.offsetDuration.toMinutes()} minutes before",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Groups & Tags
                        if (uiState.groups.isNotEmpty() || uiState.tags.isNotEmpty()) {
                            DetailSectionCard(title = "Groups & Tags") {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.groups.forEach { group ->
                                        ChipBadge(
                                            text = group.name,
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            textColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    uiState.tags.forEach { tag ->
                                        ChipBadge(
                                            text = "#${tag.name}",
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Checklists
                        event.checklist?.let { checklist ->
                            DetailSectionCard(title = checklist.title ?: "Checklist") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    checklist.items.forEach { item ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (item.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Projected Occurrences (Next 14 days)
                        if (uiState.occurrences.isNotEmpty()) {
                            DetailSectionCard(title = "Projected Occurrences (${uiState.occurrences.size})") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uiState.occurrences.forEach { occ ->
                                        OccurrenceRowItem(
                                            occurrence = occ,
                                            onComplete = { onAction(EventDetailAction.CompleteOccurrence(occ)) },
                                            onSkip = { onAction(EventDetailAction.SkipOccurrence(occ)) },
                                            onReschedule = { occurrenceToReschedule = occ }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun OccurrenceRowItem(
    occurrence: EventOccurrence,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onReschedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateTimeFormatters.formatDateTime(occurrence.effectiveStartDateTime),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = occurrence.status.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onComplete,
                modifier = Modifier.weight(1f)
            ) {
                Text("Complete", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onReschedule,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reschedule", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ButtonWithText(
    text: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = if (isDestructive) {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
        }
    ) {
        Text(text)
    }
}
