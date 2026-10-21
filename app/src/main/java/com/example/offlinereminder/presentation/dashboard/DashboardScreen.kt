package com.example.offlinereminder.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.presentation.components.EmptyStateView
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.LoadingView
import com.example.offlinereminder.presentation.components.OccurrenceCard
import com.example.offlinereminder.presentation.components.RescheduleDialog

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToAddEvent: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    DashboardContent(
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
fun DashboardContent(
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToAddEvent: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var occurrenceToReschedule by remember { mutableStateOf<EventOccurrence?>(null) }
    var showCompletedSection by remember { mutableStateOf(false) }

    if (occurrenceToReschedule != null) {
        RescheduleDialog(
            occurrence = occurrenceToReschedule!!,
            onDismiss = { occurrenceToReschedule = null },
            onConfirm = { newDateTime ->
                onAction(
                    DashboardAction.RescheduleOccurrence(
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
                            text = "Today's Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.totalActionableCount > 0) {
                            Text(
                                text = "${uiState.totalActionableCount} actionable reminder${if (uiState.totalActionableCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Backup"
                        )
                    }
                }
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
                    onDismiss = { onAction(DashboardAction.ClearError) }
                )
            }

            when {
                uiState.isLoading && uiState.isEmpty -> {
                    LoadingView(message = "Loading dashboard...")
                }
                uiState.isEmpty -> {
                    EmptyStateView(
                        title = "All clear for today!",
                        message = "No reminders scheduled for today or overdue. Tap '+' to create a new reminder.",
                        actionLabel = "Add Reminder",
                        onAction = onNavigateToAddEvent
                    )
                }
                uiState.isAllTodayCompleted && uiState.overdueOccurrences.isEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Dashboard items list" },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            DashboardSummaryCard(uiState = uiState)
                        }
                        item {
                            EmptyStateView(
                                title = "All done for today! 🎉",
                                message = "You have completed all ${uiState.completedCountToday} reminders scheduled for today.",
                                actionLabel = "Add New Reminder",
                                onAction = onNavigateToAddEvent
                            )
                        }
                        if (uiState.completedTodayOccurrences.isNotEmpty()) {
                            item {
                                CompletedSectionHeader(
                                    count = uiState.completedTodayOccurrences.size,
                                    expanded = showCompletedSection,
                                    onToggle = { showCompletedSection = !showCompletedSection }
                                )
                            }
                            if (showCompletedSection) {
                                items(
                                    items = uiState.completedTodayOccurrences,
                                    key = { it.occurrence.occurrenceId }
                                ) { item ->
                                    OccurrenceCard(
                                        occurrenceWithEvent = item,
                                        onClick = { onNavigateToDetails(item.event.id) }
                                    )
                                }
                            }
                        }
                        if (uiState.tomorrowOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Tomorrow (${uiState.tomorrowOccurrences.size})")
                            }
                            items(
                                items = uiState.tomorrowOccurrences,
                                key = { it.occurrence.occurrenceId }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Dashboard items list" },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Summary metrics card (shown when tasks have been completed today)
                        if (uiState.completedCountToday > 0) {
                            item {
                                DashboardSummaryCard(uiState = uiState)
                            }
                        }

                        // 1. Overdue section
                        if (uiState.overdueOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Overdue (${uiState.overdueOccurrences.size})",
                                    icon = Icons.Default.Warning,
                                    isWarning = true
                                )
                            }
                            items(
                                items = uiState.overdueOccurrences,
                                key = { it.occurrence.occurrenceId }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    isOverdue = true,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }

                        // 2. High Priority / Important section
                        if (uiState.highPriorityOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "High Priority (${uiState.highPriorityOccurrences.size})",
                                    icon = Icons.Default.PriorityHigh
                                )
                            }
                            items(
                                items = uiState.highPriorityOccurrences,
                                key = { "high_${it.occurrence.occurrenceId}" }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }

                        // 3. Favorites / Pinned section
                        if (uiState.favoriteOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Favorites (${uiState.favoriteOccurrences.size})",
                                    icon = Icons.Default.Star
                                )
                            }
                            items(
                                items = uiState.favoriteOccurrences,
                                key = { "fav_${it.occurrence.occurrenceId}" }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }

                        // 4. Today section
                        if (uiState.todayOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Today (${uiState.todayOccurrences.size})",
                                    icon = Icons.Default.DateRange
                                )
                            }
                            items(
                                items = uiState.todayOccurrences,
                                key = { it.occurrence.occurrenceId }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }

                        // 5. Completed Today section
                        if (uiState.completedTodayOccurrences.isNotEmpty()) {
                            item {
                                CompletedSectionHeader(
                                    count = uiState.completedTodayOccurrences.size,
                                    expanded = showCompletedSection,
                                    onToggle = { showCompletedSection = !showCompletedSection }
                                )
                            }
                            if (showCompletedSection) {
                                items(
                                    items = uiState.completedTodayOccurrences,
                                    key = { "completed_${it.occurrence.occurrenceId}" }
                                ) { item ->
                                    OccurrenceCard(
                                        occurrenceWithEvent = item,
                                        onClick = { onNavigateToDetails(item.event.id) }
                                    )
                                }
                            }
                        }

                        // 6. Tomorrow section
                        if (uiState.tomorrowOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Tomorrow (${uiState.tomorrowOccurrences.size})",
                                    icon = Icons.Default.DateRange
                                )
                            }
                            items(
                                items = uiState.tomorrowOccurrences,
                                key = { it.occurrence.occurrenceId }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
                                    onReschedule = { occurrenceToReschedule = item.occurrence }
                                )
                            }
                        }

                        // 7. Later / Upcoming section
                        if (uiState.upcomingOccurrences.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Later This Week (${uiState.upcomingOccurrences.size})",
                                    icon = Icons.Default.DateRange
                                )
                            }
                            items(
                                items = uiState.upcomingOccurrences,
                                key = { it.occurrence.occurrenceId }
                            ) { item ->
                                OccurrenceCard(
                                    occurrenceWithEvent = item,
                                    onClick = { onNavigateToDetails(item.event.id) },
                                    onComplete = { onAction(DashboardAction.CompleteOccurrence(item.occurrence)) },
                                    onSkip = { onAction(DashboardAction.SkipOccurrence(item.occurrence)) },
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
fun DashboardSummaryCard(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
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
                    text = "Today's Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.completedCountToday}/${uiState.totalTodayCount} done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            if (uiState.overdueOccurrences.isNotEmpty() || uiState.highPriorityOccurrences.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.overdueOccurrences.isNotEmpty()) {
                        Text(
                            text = "${uiState.overdueOccurrences.size} overdue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (uiState.highPriorityOccurrences.isNotEmpty()) {
                        Text(
                            text = "${uiState.highPriorityOccurrences.size} high priority",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (uiState.favoriteOccurrences.isNotEmpty()) {
                        Text(
                            text = "${uiState.favoriteOccurrences.size} starred",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.DateRange,
    isWarning: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CompletedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Completed Today ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse completed" else "Expand completed",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
