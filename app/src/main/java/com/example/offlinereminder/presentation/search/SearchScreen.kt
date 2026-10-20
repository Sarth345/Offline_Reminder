package com.example.offlinereminder.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.presentation.components.EmptyStateView
import com.example.offlinereminder.presentation.components.ErrorBanner
import com.example.offlinereminder.presentation.components.EventCard
import com.example.offlinereminder.presentation.components.LoadingView
import java.time.LocalDate

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    SearchContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onNavigateToDetails = onNavigateToDetails,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    uiState: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit,
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
                        text = "Search & Discovery",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onAction(SearchAction.OpenFilterSheet) },
                        modifier = Modifier.semantics { contentDescription = "Open filters" }
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.activeFilterCount > 0) {
                                    Badge {
                                        Text("${uiState.activeFilterCount}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null
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
            // Search Input Field
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onAction(SearchAction.QueryChanged(it)) },
                placeholder = { Text("Search by title or description...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onAction(SearchAction.ClearSearch) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Quick Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.onlyFavorites,
                        onClick = { onAction(SearchAction.ToggleFavorites) },
                        label = { Text("Favorites") }
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.onlyOverdue,
                        onClick = { onAction(SearchAction.ToggleOverdue) },
                        label = { Text("Overdue") }
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.activeFilterCount > 0,
                        onClick = { onAction(SearchAction.OpenFilterSheet) },
                        label = {
                            Text(
                                if (uiState.activeFilterCount > 0) "Filters (${uiState.activeFilterCount})"
                                else "All Filters"
                            )
                        }
                    )
                }

                if (uiState.hasActiveFilters || uiState.searchQuery.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = {
                                onAction(SearchAction.ClearFilters)
                                onAction(SearchAction.ClearSearch)
                            }
                        ) {
                            Text("Clear All")
                        }
                    }
                }
            }

            // Results Counter & Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isLoading) "Searching..."
                    else "${uiState.results.size} result${if (uiState.results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error Banner
            if (uiState.errorMessage != null) {
                ErrorBanner(
                    message = uiState.errorMessage,
                    onDismiss = { onAction(SearchAction.ClearError) }
                )
            }

            // Main Results / State View
            when {
                uiState.isLoading -> {
                    LoadingView(message = "Searching reminders...")
                }
                uiState.isEmptyResults -> {
                    EmptyStateView(
                        title = if (uiState.hasActiveFilters || uiState.searchQuery.isNotEmpty()) {
                            "No results found"
                        } else {
                            "No reminders yet"
                        },
                        message = if (uiState.hasActiveFilters || uiState.searchQuery.isNotEmpty()) {
                            "No reminders match your current search and filter criteria."
                        } else {
                            "Create reminders to search and discover them here."
                        },
                        actionLabel = if (uiState.hasActiveFilters || uiState.searchQuery.isNotEmpty()) "Clear Filters" else null,
                        onAction = if (uiState.hasActiveFilters || uiState.searchQuery.isNotEmpty()) {
                            {
                                onAction(SearchAction.ClearFilters)
                                onAction(SearchAction.ClearSearch)
                            }
                        } else null
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.results, key = { it.id }) { event ->
                            val catName = categoryMap[event.categoryId]?.name
                            val typeName = eventTypeMap[event.eventTypeId]?.name
                            val grpNames = event.groupIds.mapNotNull { groupMap[it]?.name }
                            val tNames = event.tagIds.mapNotNull { tagMap[it]?.name }

                            EventCard(
                                event = event,
                                categoryName = catName,
                                eventTypeName = typeName,
                                groupNames = grpNames,
                                tagNames = tNames,
                                onClick = { onNavigateToDetails(event.id) },
                                onFavoriteToggle = {
                                    onAction(SearchAction.ToggleFavorite(event.id, !event.isFavorite))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Advanced Filter Sheet
    if (uiState.isFilterSheetVisible) {
        FilterBottomSheet(
            uiState = uiState,
            onAction = onAction,
            onDismiss = { onAction(SearchAction.CloseFilterSheet) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    uiState: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FilterSheetContent(
            uiState = uiState,
            onAction = onAction,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    uiState: SearchUiState,
    onAction: (SearchAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onAction(SearchAction.ClearFilters) }) {
                        Text("Clear All")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close filters")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Toggles
            Text(
                text = "Attributes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Favorites only", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = uiState.onlyFavorites,
                    onCheckedChange = { onAction(SearchAction.ToggleFavorites) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Overdue only", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = uiState.onlyOverdue,
                    onCheckedChange = { onAction(SearchAction.ToggleOverdue) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Filter
            Text(
                text = "Priority",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Priority.entries.forEach { priority ->
                    val isSelected = uiState.selectedPriorities.contains(priority)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onAction(SearchAction.TogglePriority(priority)) },
                        label = { Text(priority.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lifecycle Status Filter
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventLifecycleStatus.entries.forEach { status ->
                    val isSelected = uiState.selectedLifecycleStatuses.contains(status)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onAction(SearchAction.ToggleLifecycleStatus(status)) },
                        label = { Text(status.name) }
                    )
                }
            }

            // Categories Filter
            if (uiState.availableCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableCategories.forEach { category ->
                        val isSelected = uiState.selectedCategoryIds.contains(category.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAction(SearchAction.ToggleCategory(category.id)) },
                            label = { Text(category.name) }
                        )
                    }
                }
            }

            // Event Types Filter
            if (uiState.availableEventTypes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Event Types",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableEventTypes.forEach { type ->
                        val isSelected = uiState.selectedEventTypeIds.contains(type.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAction(SearchAction.ToggleEventType(type.id)) },
                            label = { Text(type.name) }
                        )
                    }
                }
            }

            // Groups Filter
            if (uiState.availableGroups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Groups",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableGroups.forEach { group ->
                        val isSelected = uiState.selectedGroupIds.contains(group.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAction(SearchAction.ToggleGroup(group.id)) },
                            label = { Text(group.name) }
                        )
                    }
                }
            }

            // Tags Filter
            if (uiState.availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableTags.forEach { tag ->
                        val isSelected = uiState.selectedTagIds.contains(tag.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAction(SearchAction.ToggleTag(tag.id)) },
                            label = { Text("#${tag.name}") }
                        )
                    }
                }
            }

            // Date Range Presets
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Date Range",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val today = LocalDate.now()
                val isTodaySelected = uiState.startDate == today && uiState.endDate == today
                val isNext7Selected = uiState.startDate == today && uiState.endDate == today.plusDays(7)
                val isThisMonthSelected = uiState.startDate == today.withDayOfMonth(1) &&
                    uiState.endDate == today.withDayOfMonth(today.lengthOfMonth())

                FilterChip(
                    selected = isTodaySelected,
                    onClick = {
                        if (isTodaySelected) onAction(SearchAction.SetDateRange(null, null))
                        else onAction(SearchAction.SetDateRange(today, today))
                    },
                    label = { Text("Today") }
                )

                FilterChip(
                    selected = isNext7Selected,
                    onClick = {
                        if (isNext7Selected) onAction(SearchAction.SetDateRange(null, null))
                        else onAction(SearchAction.SetDateRange(today, today.plusDays(7)))
                    },
                    label = { Text("Next 7 Days") }
                )

                FilterChip(
                    selected = isThisMonthSelected,
                    onClick = {
                        if (isThisMonthSelected) onAction(SearchAction.SetDateRange(null, null))
                        else onAction(
                            SearchAction.SetDateRange(
                                today.withDayOfMonth(1),
                                today.withDayOfMonth(today.lengthOfMonth())
                            )
                        )
                    },
                    label = { Text("This Month") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text("Apply Filters (${uiState.results.size} results)")
            }
        }
    }
