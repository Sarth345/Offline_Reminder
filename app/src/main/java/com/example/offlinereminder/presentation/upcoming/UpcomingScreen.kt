package com.example.offlinereminder.presentation.upcoming

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.presentation.components.DateTimeFormatters
import com.example.offlinereminder.presentation.components.EmptyStateView
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.LoadingView
import com.example.offlinereminder.presentation.components.OccurrenceCard
import com.example.offlinereminder.presentation.components.RescheduleDialog
import java.time.LocalDate

@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToAddEvent: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    UpcomingContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToDetails = onNavigateToDetails,
        onNavigateToAddEvent = onNavigateToAddEvent,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingContent(
    uiState: UpcomingUiState,
    onAction: (UpcomingAction) -> Unit,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToAddEvent: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var occurrenceToReschedule by remember { mutableStateOf<EventOccurrence?>(null) }

    if (occurrenceToReschedule != null) {
        RescheduleDialog(
            occurrence = occurrenceToReschedule!!,
            onDismiss = { occurrenceToReschedule = null },
            onConfirm = { newDateTime ->
                onAction(
                    UpcomingAction.RescheduleOccurrence(
                        occurrence = occurrenceToReschedule!!,
                        newStartDateTime = newDateTime
                    )
                )
                occurrenceToReschedule = null
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            com.example.offlinereminder.presentation.components.PrimaryScreenTopBar(
                title = {
                    Column {
                        Text(
                            text = "Upcoming Agenda",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.occurrences.isNotEmpty()) {
                            Text(
                                text = "${uiState.occurrences.size} occurrences in next ${uiState.horizonDays} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Navigation & Filter Bar
            AgendaNavigationToolbar(
                selectedDate = uiState.selectedDate,
                horizonDays = uiState.horizonDays,
                includeCompleted = uiState.includeCompleted,
                includeOverdue = uiState.includeOverdue,
                onNavigateDays = { onAction(UpcomingAction.NavigateDays(it)) },
                onJumpToToday = { onAction(UpcomingAction.JumpToToday) },
                onChangeHorizon = { onAction(UpcomingAction.ChangeHorizon(it)) },
                onToggleIncludeCompleted = { onAction(UpcomingAction.ToggleIncludeCompleted) },
                onToggleIncludeOverdue = { onAction(UpcomingAction.ToggleIncludeOverdue) }
            )

            if (uiState.errorMessage != null) {
                ErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(UpcomingAction.ClearError) }
                )
            }

            when {
                uiState.isLoading && uiState.isEmpty -> {
                    LoadingView(message = "Loading timeline...")
                }
                uiState.isEmpty -> {
                    EmptyStateView(
                        title = "No timeline events found",
                        message = "No reminders scheduled for this period. Try navigating or tap '+' to create a reminder.",
                        actionLabel = "Add Reminder",
                        onAction = onNavigateToAddEvent
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Upcoming occurrences list" },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        uiState.occurrencesByDate.forEach { (date, occurrences) ->
                            item(key = "header_$date") {
                                UpcomingDateHeader(date = date)
                            }

                            items(
                                items = occurrences,
                                key = { "${date}_${it.occurrence.occurrenceId}" }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    isOverdue = item.occurrence.isOverdue,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(UpcomingAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(UpcomingAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaNavigationToolbar(
    selectedDate: LocalDate,
    horizonDays: Long,
    includeCompleted: Boolean,
    includeOverdue: Boolean,
    onNavigateDays: (Long) -> Unit,
    onJumpToToday: () -> Unit,
    onChangeHorizon: (Long) -> Unit,
    onToggleIncludeCompleted: () -> Unit,
    onToggleIncludeOverdue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Date stepping row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onNavigateDays(-7) },
                modifier = Modifier.semantics { contentDescription = "Previous week" }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }

            OutlinedButton(
                onClick = onJumpToToday,
                modifier = Modifier.semantics { contentDescription = "Jump to today" }
            ) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Today", style = MaterialTheme.typography.labelMedium)
            }

            IconButton(
                onClick = { onNavigateDays(7) },
                modifier = Modifier.semantics { contentDescription = "Next week" }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = horizonDays == 7L,
                onClick = { onChangeHorizon(7L) },
                label = { Text("7 Days") }
            )
            FilterChip(
                selected = horizonDays == 14L,
                onClick = { onChangeHorizon(14L) },
                label = { Text("14 Days") }
            )
            FilterChip(
                selected = horizonDays == 30L,
                onClick = { onChangeHorizon(30L) },
                label = { Text("30 Days") }
            )
            FilterChip(
                selected = includeCompleted,
                onClick = onToggleIncludeCompleted,
                label = { Text("Show Completed") }
            )
            FilterChip(
                selected = includeOverdue,
                onClick = onToggleIncludeOverdue,
                label = { Text("Show Overdue") }
            )
        }
    }
}

@Composable
fun UpcomingDateHeader(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today (${DateTimeFormatters.formatDate(date)})"
        today.plusDays(1) -> "Tomorrow (${DateTimeFormatters.formatDate(date)})"
        else -> DateTimeFormatters.formatDate(date)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
