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

    val subtitleText = when (uiState.mode) {
        UpcomingMode.NEXT -> if (uiState.occurrences.isNotEmpty()) "${uiState.occurrences.size} next future reminder${if (uiState.occurrences.size > 1) "s" else ""}" else null
        UpcomingMode.OVERDUE -> if (uiState.occurrences.isNotEmpty()) "${uiState.occurrences.size} overdue reminder${if (uiState.occurrences.size > 1) "s" else ""}" else null
        UpcomingMode.DATE -> "${DateTimeFormatters.formatDate(uiState.selectedDate)} (${uiState.occurrences.size})"
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
                        if (subtitleText != null) {
                            Text(
                                text = subtitleText,
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
                mode = uiState.mode,
                selectedDate = uiState.selectedDate,
                onSelectMode = { onAction(UpcomingAction.SelectMode(it)) },
                onNavigateDays = { onAction(UpcomingAction.NavigateDays(it)) },
                onJumpToToday = { onAction(UpcomingAction.JumpToToday) }
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
                    val emptyTitle = when (uiState.mode) {
                        UpcomingMode.NEXT -> "No upcoming reminders found"
                        UpcomingMode.OVERDUE -> "No overdue reminders! 🎉"
                        UpcomingMode.DATE -> "No reminders for ${DateTimeFormatters.formatDate(uiState.selectedDate)}"
                    }
                    val emptyMessage = when (uiState.mode) {
                        UpcomingMode.NEXT -> "You have no upcoming future reminders scheduled. Tap '+' to create a reminder."
                        UpcomingMode.OVERDUE -> "Great job! You have no overdue reminders pending."
                        UpcomingMode.DATE -> "No reminders scheduled for this date. Use arrow buttons to navigate or tap '+' to add one."
                    }
                    EmptyStateView(
                        title = emptyTitle,
                        message = emptyMessage,
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
    mode: UpcomingMode,
    selectedDate: LocalDate,
    onSelectMode: (UpcomingMode) -> Unit,
    onNavigateDays: (Long) -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Mode selector tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = mode == UpcomingMode.NEXT,
                onClick = { onSelectMode(UpcomingMode.NEXT) },
                label = { Text("Next") }
            )
            FilterChip(
                selected = mode == UpcomingMode.OVERDUE,
                onClick = { onSelectMode(UpcomingMode.OVERDUE) },
                label = { Text("Overdue") }
            )
            FilterChip(
                selected = mode == UpcomingMode.DATE,
                onClick = { onSelectMode(UpcomingMode.DATE) },
                label = { Text("By Date") }
            )
        }

        // Date stepping row (displayed in Date mode)
        if (mode == UpcomingMode.DATE) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onNavigateDays(-1) },
                    modifier = Modifier.semantics { contentDescription = "Previous day" }
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
                    onClick = { onNavigateDays(1) },
                    modifier = Modifier.semantics { contentDescription = "Next day" }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
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
