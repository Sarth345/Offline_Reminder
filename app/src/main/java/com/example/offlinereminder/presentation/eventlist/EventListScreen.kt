package com.example.offlinereminder.presentation.eventlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.presentation.components.EmptyStateView
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.EventCard
import com.example.offlinereminder.presentation.components.LoadingView
import com.example.offlinereminder.presentation.components.SearchAndFilterBar

@Composable
fun EventListScreen(
    viewModel: EventListViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    EventListContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToDetails = onNavigateToDetails,
        onNavigateToAddEvent = onNavigateToAddEvent,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListContent(
    uiState: EventListUiState,
    onAction: (EventListAction) -> Unit,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryMap = uiState.availableCategories.associateBy { it.id }
    val eventTypeMap = uiState.availableEventTypes.associateBy { it.id }
    val groupMap = uiState.availableGroups.associateBy { it.id }
    val tagMap = uiState.availableTags.associateBy { it.id }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.selectedStatus) {
                            EventLifecycleStatus.ACTIVE -> "All Reminders"
                            EventLifecycleStatus.ARCHIVED -> "Archived Reminders"
                            EventLifecycleStatus.TRASHED -> "Trash"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
            // Search and filter component
            SearchAndFilterBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { onAction(EventListAction.Search(it)) },
                selectedStatus = uiState.selectedStatus,
                onStatusSelect = { onAction(EventListAction.FilterStatus(it)) },
                onlyFavorites = uiState.onlyFavorites,
                onToggleFavorites = { onAction(EventListAction.ToggleOnlyFavorites(it)) },
                categories = uiState.availableCategories,
                selectedCategoryId = uiState.selectedCategoryId,
                onCategorySelect = { onAction(EventListAction.FilterCategory(it)) },
                eventTypes = uiState.availableEventTypes,
                selectedEventTypeId = uiState.selectedEventTypeId,
                onEventTypeSelect = { onAction(EventListAction.FilterEventType(it)) },
                groups = uiState.availableGroups,
                selectedGroupId = uiState.selectedGroupId,
                onGroupSelect = { onAction(EventListAction.FilterGroup(it)) },
                tags = uiState.availableTags,
                selectedTagId = uiState.selectedTagId,
                onTagSelect = { onAction(EventListAction.FilterTag(it)) }
            )

            if (uiState.errorMessage != null) {
                ErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(EventListAction.ClearError) }
                )
            }

            when {
                uiState.isLoading && uiState.isEmpty -> {
                    LoadingView(message = "Loading reminders...")
                }
                uiState.isEmpty -> {
                    val emptyMsg = if (uiState.searchQuery.isNotBlank()) {
                        "No reminders match '${uiState.searchQuery}'"
                    } else {
                        "No reminders found for the selected filter."
                    }
                    EmptyStateView(
                        title = "No reminders found",
                        message = emptyMsg,
                        actionLabel = if (uiState.selectedStatus == EventLifecycleStatus.ACTIVE) "Create Reminder" else null,
                        onAction = if (uiState.selectedStatus == EventLifecycleStatus.ACTIVE) onNavigateToAddEvent else null
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Event list" },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.filteredEvents,
                            key = { it.id }
                        ) { event ->
                            val categoryName = categoryMap[event.categoryId]?.name
                            val eventTypeName = eventTypeMap[event.eventTypeId]?.name
                            val groupNames = event.groupIds.mapNotNull { groupMap[it]?.name }
                            val tagNames = event.tagIds.mapNotNull { tagMap[it]?.name }

                            EventCard(
                                event = event,
                                displayStatus = uiState.eventStatusMap[event.id] ?: com.example.offlinereminder.domain.model.EventDisplayStatus.ACTIVE,
                                categoryName = categoryName,
                                eventTypeName = eventTypeName,
                                groupNames = groupNames,
                                tagNames = tagNames,
                                onClick = { onNavigateToDetails(event.id) },
                                onFavoriteToggle = {
                                    onAction(EventListAction.ToggleFavorite(event.id, !event.isFavorite))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
