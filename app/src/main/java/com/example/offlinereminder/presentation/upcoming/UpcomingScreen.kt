package com.example.offlinereminder.presentation.upcoming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    UpcomingContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToDetails = onNavigateToDetails,
        onNavigateToAddEvent = onNavigateToAddEvent,
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Upcoming Agenda",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.occurrences.isNotEmpty()) {
                            Text(
                                text = "${uiState.occurrences.size} occurrences in next 14 days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onDismiss = { onAction(UpcomingAction.ClearError) }
                )
            }

            when {
                uiState.isLoading && uiState.isEmpty -> {
                    LoadingView(message = "Loading upcoming agenda...")
                }
                uiState.isEmpty -> {
                    EmptyStateView(
                        title = "No upcoming reminders",
                        message = "There are no reminders scheduled for the next 14 days.",
                        actionLabel = "Add Reminder",
                        onAction = onNavigateToAddEvent
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Upcoming occurrences list" },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        uiState.occurrencesByDate.forEach { (date, occurrences) ->
                            item(key = "header_$date") {
                                UpcomingDateHeader(date = date)
                            }

                            items(
                                items = occurrences,
                                key = { it.occurrence.occurrenceId }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
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
