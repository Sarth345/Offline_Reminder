package com.example.offlinereminder.presentation.eventdetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.EventDisplayStatus
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RelationshipType
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.presentation.attachment.AttachmentHelper
import com.example.offlinereminder.presentation.components.ChipBadge
import com.example.offlinereminder.presentation.components.DateTimeFormatters
import com.example.offlinereminder.presentation.components.EmptyStateView
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.LoadingView
import com.example.offlinereminder.presentation.components.PriorityBadge
import com.example.offlinereminder.presentation.components.RescheduleDialog
import com.example.offlinereminder.presentation.components.StatusBadge
import java.time.Duration
import java.time.ZoneId

@Composable
fun EventDetailScreen(
    eventId: String,
    viewModel: EventDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToEvent: ((String) -> Unit)? = null,
    onNavigateToAddSubEvent: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(eventId) {
        viewModel.onAction(EventDetailAction.LoadEvent(eventId))
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.duplicatedEventId) {
        val dupId = uiState.duplicatedEventId
        if (dupId != null) {
            viewModel.onAction(EventDetailAction.ClearDuplicatedEventId)
            if (onNavigateToEvent != null) {
                onNavigateToEvent(dupId)
            } else {
                onNavigateToEdit(dupId)
            }
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.onAction(EventDetailAction.ClearMessage)
        }
    }

    EventDetailContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onNavigateToEdit = { onNavigateToEdit(eventId) },
        onNavigateToEvent = onNavigateToEvent ?: onNavigateToEdit,
        onNavigateToAddSubEvent = onNavigateToAddSubEvent ?: onNavigateToEdit,
        snackbarHostState = snackbarHostState,
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
    onNavigateToEvent: (String) -> Unit = {},
    onNavigateToAddSubEvent: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var occurrenceToReschedule by remember { mutableStateOf<EventOccurrence?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showTrashConfirmDialog by remember { mutableStateOf(false) }
    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var showAddChecklistDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showAddDependencyDialog by remember { mutableStateOf(false) }
    var showAddRelationshipDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Safe local attachment launcher
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saveResult = AttachmentHelper.saveAttachmentFromUri(context, uri)
            when (saveResult) {
                is com.example.offlinereminder.core.result.Result.Success -> {
                    onAction(EventDetailAction.AddAttachment(saveResult.data))
                }
                is com.example.offlinereminder.core.result.Result.Failure -> {
                    // Handled gracefully in UI state
                }
            }
        }
    }

    val event = uiState.event
    val isTrashed = event?.isTrashed == true
    val isArchived = event?.isArchived == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isTrashed) "Trashed Event" else if (isArchived) "Archived Event" else "Event Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (event != null) {
                        if (!isTrashed && !isArchived) {
                            IconButton(
                                onClick = { onAction(EventDetailAction.ToggleFavorite) },
                                modifier = Modifier.semantics {
                                    contentDescription = if (uiState.isFavorite) "Remove from favorites" else "Add to favorites"
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onNavigateToEdit,
                                modifier = Modifier.semantics { contentDescription = "Edit event" }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                if (!isTrashed) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onAction(EventDetailAction.DuplicateEvent)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save as Template") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showCreateTemplateDialog = true
                                        }
                                    )
                                    if (!isArchived) {
                                        DropdownMenuItem(
                                            text = { Text("Archive") },
                                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onAction(EventDetailAction.ArchiveEvent)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Move to Trash") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showTrashConfirmDialog = true
                                            }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Restore from Archive") },
                                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onAction(EventDetailAction.RestoreFromArchive)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Move to Trash") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showTrashConfirmDialog = true
                                            }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Restore from Trash") },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onAction(EventDetailAction.RestoreFromTrash)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Permanently") },
                                        leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDeleteConfirmDialog = true
                                        }
                                    )
                                }
                            }
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
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.errorMessage != null) {
                ErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(EventDetailAction.ClearError) }
                )
            }

            when {
                uiState.isLoading -> {
                    LoadingView(message = "Loading reminder details...")
                }
                event == null -> {
                    EmptyStateView(
                        title = "Reminder Not Found",
                        message = "This event does not exist or was permanently removed.",
                        actionLabel = "Go Back",
                        onAction = onNavigateBack
                    )
                }
                else -> {
                    // Lifecycle Banners
                    if (isTrashed) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "In Trash",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Notifications are paused. Restore to enable actions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Button(onClick = { onAction(EventDetailAction.RestoreFromTrash) }) {
                                    Text("Restore")
                                }
                            }
                        }
                    } else if (isArchived) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Archived",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "This event is completed and archived.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(onClick = { onAction(EventDetailAction.RestoreFromArchive) }) {
                                    Text("Restore")
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1: Title & Overview Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                if (event.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = event.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PriorityBadge(priority = event.priority)
                                    StatusBadge(status = uiState.displayStatus)

                                    uiState.category?.let { cat ->
                                        ChipBadge(
                                            text = cat.name,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    uiState.eventType?.let { type ->
                                        ChipBadge(
                                            text = type.name,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            textColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    if (event.isRecurring) {
                                        ChipBadge(
                                            text = "Recurring",
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            textColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                if (uiState.groups.isNotEmpty() || uiState.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        uiState.groups.forEach { grp ->
                                            ChipBadge(
                                                text = "@${grp.name}",
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        uiState.tags.forEach { tag ->
                                            ChipBadge(
                                                text = "#${tag.name}",
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 1.5: Reminder Status & Direct Actions
                        if (!isTrashed) {
                            val primaryOcc = uiState.primaryOccurrence
                            val displayStatus = uiState.displayStatus
                            val isOverdue = displayStatus == EventDisplayStatus.OVERDUE

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOverdue) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Reminder Action & Status",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        StatusBadge(status = displayStatus)
                                    }

                                    if (primaryOcc != null) {
                                        val timeStr = if (event.temporal.isAllDay) {
                                            "${DateTimeFormatters.formatDate(primaryOcc.effectiveStartDateTime.toLocalDate())} (All-day)"
                                        } else {
                                            DateTimeFormatters.formatDateTime(primaryOcc.effectiveStartDateTime)
                                        }
                                        Text(
                                            text = "Scheduled: $timeStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    when (displayStatus) {
                                        EventDisplayStatus.ACTIVE, EventDisplayStatus.OVERDUE, EventDisplayStatus.RESCHEDULED -> {
                                            if (primaryOcc != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    FilledTonalButton(
                                                        onClick = { onAction(EventDetailAction.CompleteOccurrence(primaryOcc)) },
                                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Complete", maxLines = 1)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { occurrenceToReschedule = primaryOcc },
                                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Reschedule", maxLines = 1)
                                                    }
                                                }
                                                val cancelLabel = if (isOverdue) "Cancel" else "Skip"
                                                OutlinedButton(
                                                    onClick = { onAction(EventDetailAction.SkipOccurrence(primaryOcc)) },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                                                ) {
                                                    Text(cancelLabel, maxLines = 1)
                                                }
                                            }
                                        }
                                        EventDisplayStatus.COMPLETED -> {
                                            if (primaryOcc != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                OutlinedButton(
                                                    onClick = { onAction(EventDetailAction.UndoOccurrenceAction(primaryOcc.occurrenceId)) },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Undo Completion", maxLines = 1)
                                                }
                                            }
                                        }
                                        EventDisplayStatus.SKIPPED -> {
                                            if (primaryOcc != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                OutlinedButton(
                                                    onClick = { onAction(EventDetailAction.UndoOccurrenceAction(primaryOcc.occurrenceId)) },
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Restore Occurrence", maxLines = 1)
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }

                        // Section 2: Conflict Visibility Banner
                        if (uiState.conflicts.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Schedule Conflict Detected (${uiState.conflicts.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    uiState.conflicts.forEach { conflict ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Overlaps with: ${conflict.existingEventTitle}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = "${conflict.severity.name}: ${conflict.type.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                            TextButton(onClick = { onNavigateToEvent(conflict.existingEventId) }) {
                                                Text("View")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 3: Sub-Event Parent Banner
                        if (uiState.parentEvent != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToEvent(uiState.parentEvent.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sub-event of",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = uiState.parentEvent.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    TextButton(onClick = { onNavigateToEvent(uiState.parentEvent.id) }) {
                                        Text("Open Parent")
                                    }
                                }
                            }
                        }

                        // Section 4: Date, Time & Recurrence Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Date & Timing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                DetailRow(label = "Date Mode", value = when (event.temporal) {
                                    is EventDateTime.DateOnly -> "All-day (Single Date)"
                                    is EventDateTime.DateRange -> "All-day (Date Range)"
                                    is EventDateTime.DateTime -> "Date & Time"
                                    is EventDateTime.DateTimeRange -> "Date & Time Range"
                                })

                                DetailRow(label = "Start", value = DateTimeFormatters.formatEventDateTime(event.temporal))

                                when (val t = event.temporal) {
                                    is EventDateTime.DateTime -> {
                                        DetailRow(label = "Timezone", value = t.zoneId.id)
                                    }
                                    is EventDateTime.DateTimeRange -> {
                                        DetailRow(label = "Timezone", value = t.zoneId.id)
                                    }
                                    else -> {}
                                }

                                if (event.isRecurring && event.recurrenceRule != null) {
                                    val rule = event.recurrenceRule
                                    DetailRow(label = "Recurrence", value = "${rule.frequency.name} (every ${rule.interval})")
                                    if (rule.daysOfWeek.isNotEmpty()) {
                                        DetailRow(label = "Days of Week", value = rule.daysOfWeek.joinToString { it.name.take(3) })
                                    }
                                    when (val end = rule.endCondition) {
                                        is RecurrenceEnd.UntilDate -> DetailRow(label = "Ends", value = "Until ${end.date}")
                                        is RecurrenceEnd.Count -> DetailRow(label = "Ends", value = "After ${end.maxOccurrences} occurrences")
                                        RecurrenceEnd.Never -> DetailRow(label = "Ends", value = "Never")
                                    }
                                }

                                if (event.location != null && event.location.placeName.isNotBlank()) {
                                    DetailRow(label = "Location", value = event.location.placeName)
                                }
                            }
                        }

                        // Section 5: Checklists Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (event.checklist?.title.isNullOrBlank()) "Checklist" else event.checklist!!.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = " (${uiState.completedChecklistCount}/${uiState.totalChecklistCount})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!isTrashed) {
                                        IconButton(onClick = { showAddChecklistDialog = true }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add checklist item")
                                        }
                                    }
                                }

                                if (uiState.totalChecklistCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { uiState.checklistProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    event.checklist?.items?.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = item.isCompleted,
                                                onCheckedChange = { if (!isTrashed) onAction(EventDetailAction.ToggleChecklistItem(item.id)) },
                                                enabled = !isTrashed
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!isTrashed) {
                                                IconButton(
                                                    onClick = { onAction(EventDetailAction.DeleteChecklistItem(item.id)) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Delete item", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No checklist items. Tap '+' to add subtasks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 6: Sub-Events Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sub-Events (${uiState.subEvents.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isTrashed) {
                                        TextButton(onClick = { onNavigateToAddSubEvent(event.id) }) {
                                            Text("+ Add Sub-Event")
                                        }
                                    }
                                }

                                if (uiState.subEvents.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    uiState.subEvents.forEach { child ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToEvent(child.id) }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = child.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = DateTimeFormatters.formatEventDateTime(child.temporal),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            StatusBadge(status = child.lifecycleStatus)
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                } else {
                                    Text(
                                        text = "No sub-events attached.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 7: Reminders & Notification Schedules Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Reminders (${event.notificationSchedules.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isTrashed) {
                                        IconButton(onClick = { showAddReminderDialog = true }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add reminder")
                                        }
                                    }
                                }

                                if (event.notificationSchedules.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    event.notificationSchedules.forEach { sched ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                val timing = if (sched.offsetDuration.isZero) {
                                                    "At start time"
                                                } else {
                                                    "${sched.offsetDuration.toMinutes()}m before start"
                                                }
                                                val typeLabel = if (sched.deliveryType == ReminderDeliveryType.ALARM) "Alarm" else "Notification"
                                                val profileLabel = sched.profileId?.let { uiState.notificationProfiles[it]?.name }
                                                Text(
                                                    text = "$timing ($typeLabel)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (profileLabel != null) {
                                                    Text(
                                                        text = "Profile: $profileLabel",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (!isTrashed) {
                                                IconButton(
                                                    onClick = { onAction(EventDetailAction.RemoveReminder(sched.id)) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No notification reminders configured.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 8: Attachments Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Attachments (${event.attachments.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isTrashed) {
                                        TextButton(onClick = { attachmentPickerLauncher.launch("*/*") }) {
                                            Text("+ Add File")
                                        }
                                    }
                                }

                                if (event.attachments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    event.attachments.forEach { att ->
                                        val isAvailable = AttachmentHelper.isAttachmentAvailable(context, att)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AttachFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = att.fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${AttachmentHelper.formatFileSize(att.fileSizeBytes)} • ${if (isAvailable) "Local file" else "Unavailable"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (isAvailable) {
                                                TextButton(onClick = { AttachmentHelper.openAttachment(context, att) }) {
                                                    Text("Open")
                                                }
                                            }
                                            if (!isTrashed) {
                                                IconButton(
                                                    onClick = {
                                                        AttachmentHelper.deleteAttachmentFile(context, att)
                                                        onAction(EventDetailAction.RemoveAttachment(att.id))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No files attached.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 9: Dependencies Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Dependencies (${uiState.dependencies.size + uiState.dependents.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isTrashed && uiState.availableEventsForLinking.isNotEmpty()) {
                                        TextButton(onClick = { showAddDependencyDialog = true }) {
                                            Text("+ Add")
                                        }
                                    }
                                }

                                if (uiState.dependencies.isNotEmpty() || uiState.dependents.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (uiState.dependencies.isNotEmpty()) {
                                        Text(
                                            text = "Blocked by (Prerequisites):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        uiState.dependencies.forEach { dep ->
                                            val target = uiState.resolvedLinkedEvents[dep.targetEventId]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onNavigateToEvent(dep.targetEventId) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = target?.title ?: "Event ${dep.targetEventId.take(8)}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Type: ${dep.type.name}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (!isTrashed) {
                                                    IconButton(onClick = { onAction(EventDetailAction.RemoveDependency(dep.targetEventId)) }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Remove dependency")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (uiState.dependents.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Blocks (Dependents):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        uiState.dependents.forEach { dep ->
                                            val source = uiState.resolvedLinkedEvents[dep.sourceEventId]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onNavigateToEvent(dep.sourceEventId) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = source?.title ?: "Event ${dep.sourceEventId.take(8)}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Waiting on this event",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No prerequisite or dependent tasks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 10: Relationships Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Related Events (${uiState.relationships.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isTrashed && uiState.availableEventsForLinking.isNotEmpty()) {
                                        TextButton(onClick = { showAddRelationshipDialog = true }) {
                                            Text("+ Add")
                                        }
                                    }
                                }

                                if (uiState.relationships.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    uiState.relationships.forEach { rel ->
                                        val otherId = if (rel.sourceEventId == event.id) rel.targetEventId else rel.sourceEventId
                                        val other = uiState.resolvedLinkedEvents[otherId]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToEvent(otherId) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = other?.title ?: "Event ${otherId.take(8)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = rel.type.name.replace("_", " "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (!isTrashed) {
                                                IconButton(onClick = { onAction(EventDetailAction.RemoveRelationship(otherId)) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove relationship")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No related events linked.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 11: Upcoming Occurrences (for recurring / actionable events)
                        if (!isTrashed && uiState.occurrences.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Upcoming Occurrences (${uiState.occurrences.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    uiState.occurrences.take(5).forEach { occ ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = DateTimeFormatters.formatEventDateTime(
                                                        EventDateTime.DateTime(occ.effectiveStartDateTime, occ.effectiveZoneId)
                                                    ),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Status: ${occ.status.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (occ.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                FilledTonalButton(
                                                    onClick = { onAction(EventDetailAction.CompleteOccurrence(occ)) },
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = "Complete", modifier = Modifier.size(16.dp))
                                                }
                                                OutlinedButton(
                                                    onClick = { onAction(EventDetailAction.SkipOccurrence(occ)) },
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text("Skip")
                                                }
                                                OutlinedButton(
                                                    onClick = { occurrenceToReschedule = occ },
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Reschedule", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }

                        // Section 12: Creation & Audit Metadata
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "System Information",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Created: ${event.createdAt} • Modified: ${event.updatedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "ID: ${event.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (occurrenceToReschedule != null) {
        RescheduleDialog(
            occurrence = occurrenceToReschedule!!,
            onDismiss = { occurrenceToReschedule = null },
            onConfirm = { newDateTime ->
                onAction(
                    EventDetailAction.RescheduleOccurrence(
                        occurrence = occurrenceToReschedule!!,
                        newStartDateTime = newDateTime
                    )
                )
                occurrenceToReschedule = null
            }
        )
    }

    if (showTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showTrashConfirmDialog = false },
            title = { Text("Move to Trash?") },
            text = { Text("This reminder will be moved to Trash. Scheduled notifications will be cancelled. Trashed items are automatically purged after 30 days.") },
            confirmButton = {
                Button(onClick = {
                    showTrashConfirmDialog = false
                    onAction(EventDetailAction.MoveToTrash)
                }) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Permanently Delete Reminder?") },
            text = { Text("This will permanently remove this reminder and all associated attachments, occurrences, and checklists. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onAction(EventDetailAction.DeletePermanently)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateTemplateDialog) {
        var templateName by remember { mutableStateOf("${event?.title ?: ""} Template") }
        AlertDialog(
            onDismissRequest = { showCreateTemplateDialog = false },
            title = { Text("Save as Template") },
            text = {
                Column {
                    Text("Enter a template name to easily create future reminders using these settings:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCreateTemplateDialog = false
                    onAction(EventDetailAction.CreateTemplateFromEvent(templateName))
                }) {
                    Text("Save Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTemplateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddChecklistDialog) {
        var itemText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddChecklistDialog = false },
            title = { Text("Add Checklist Item") },
            text = {
                OutlinedTextField(
                    value = itemText,
                    onValueChange = { itemText = it },
                    label = { Text("Subtask description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddChecklistDialog = false
                        if (itemText.isNotBlank()) onAction(EventDetailAction.AddChecklistItem(itemText))
                    },
                    enabled = itemText.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChecklistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddReminderDialog) {
        var selectedMinutes by remember { mutableStateOf(15) }
        var isAlarm by remember { mutableStateOf(false) }
        var selectedProfileId by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddReminderDialog = false },
            title = { Text("Add Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select notification timing:")
                    val options = listOf(0 to "At event start", 5 to "5m before", 15 to "15m before", 30 to "30m before", 60 to "1h before", 1440 to "1d before")
                    options.forEach { (mins, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMinutes = mins }
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedMinutes == mins,
                                onClick = { selectedMinutes = mins }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isAlarm, onCheckedChange = { isAlarm = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Full Alarm Mode")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAddReminderDialog = false
                    val schedule = if (selectedMinutes == 0) {
                        NotificationSchedule.atStart(profileId = selectedProfileId).copy(
                            deliveryType = if (isAlarm) ReminderDeliveryType.ALARM else ReminderDeliveryType.NOTIFICATION
                        )
                    } else {
                        NotificationSchedule.beforeStart(
                            offset = Duration.ofMinutes(selectedMinutes.toLong()),
                            profileId = selectedProfileId
                        ).copy(
                            deliveryType = if (isAlarm) ReminderDeliveryType.ALARM else ReminderDeliveryType.NOTIFICATION
                        )
                    }
                    onAction(EventDetailAction.AddReminder(schedule))
                }) {
                    Text("Add Reminder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDependencyDialog) {
        var selectedTargetId by remember { mutableStateOf(uiState.availableEventsForLinking.firstOrNull()?.id ?: "") }
        var selectedType by remember { mutableStateOf(DependencyType.PREREQUISITE) }

        AlertDialog(
            onDismissRequest = { showAddDependencyDialog = false },
            title = { Text("Add Dependency") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select prerequisite event (this event will be blocked until it finishes):")
                    uiState.availableEventsForLinking.take(5).forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetId = candidate.id }
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedTargetId == candidate.id,
                                onClick = { selectedTargetId = candidate.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddDependencyDialog = false
                        if (selectedTargetId.isNotBlank()) {
                            onAction(EventDetailAction.AddDependency(selectedTargetId, selectedType))
                        }
                    },
                    enabled = selectedTargetId.isNotBlank()
                ) {
                    Text("Add Dependency")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDependencyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddRelationshipDialog) {
        var selectedTargetId by remember { mutableStateOf(uiState.availableEventsForLinking.firstOrNull()?.id ?: "") }
        var selectedType by remember { mutableStateOf(RelationshipType.RELATED_TO) }

        AlertDialog(
            onDismissRequest = { showAddRelationshipDialog = false },
            title = { Text("Add Relationship") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select related event:")
                    uiState.availableEventsForLinking.take(5).forEach { candidate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetId = candidate.id }
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedTargetId == candidate.id,
                                onClick = { selectedTargetId = candidate.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddRelationshipDialog = false
                        if (selectedTargetId.isNotBlank()) {
                            onAction(EventDetailAction.AddRelationship(selectedTargetId, selectedType))
                        }
                    },
                    enabled = selectedTargetId.isNotBlank()
                ) {
                    Text("Add Relationship")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRelationshipDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
