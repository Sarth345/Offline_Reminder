package com.example.offlinereminder.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Tag

@Composable
fun SearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: EventLifecycleStatus,
    onStatusSelect: (EventLifecycleStatus) -> Unit,
    onlyFavorites: Boolean,
    onToggleFavorites: (Boolean) -> Unit,
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelect: (String?) -> Unit,
    eventTypes: List<EventType>,
    selectedEventTypeId: String?,
    onEventTypeSelect: (String?) -> Unit,
    groups: List<EventGroup> = emptyList(),
    selectedGroupId: String? = null,
    onGroupSelect: ((String?) -> Unit)? = null,
    tags: List<Tag> = emptyList(),
    selectedTagId: String? = null,
    onTagSelect: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search events" },
            placeholder = { Text("Search events...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.semantics { contentDescription = "Clear search" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search text"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Favorites filter
            FilterChip(
                selected = onlyFavorites,
                onClick = { onToggleFavorites(!onlyFavorites) },
                label = { Text("Favorites") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.semantics { contentDescription = "Filter only favorites" }
            )

            // Status filters
            EventLifecycleStatus.entries.forEach { status ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onStatusSelect(status) },
                    label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.semantics { contentDescription = "Filter status ${status.name}" }
                )
            }

            // Categories
            categories.forEach { category ->
                val isSelected = selectedCategoryId == category.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(if (isSelected) null else category.id) },
                    label = { Text(category.name) },
                    modifier = Modifier.semantics { contentDescription = "Filter category ${category.name}" }
                )
            }

            // Event Types
            eventTypes.forEach { type ->
                val isSelected = selectedEventTypeId == type.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onEventTypeSelect(if (isSelected) null else type.id) },
                    label = { Text(type.name) },
                    modifier = Modifier.semantics { contentDescription = "Filter event type ${type.name}" }
                )
            }

            // Groups
            if (onGroupSelect != null) {
                groups.forEach { group ->
                    val isSelected = selectedGroupId == group.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGroupSelect(if (isSelected) null else group.id) },
                        label = { Text(group.name) },
                        modifier = Modifier.semantics { contentDescription = "Filter group ${group.name}" }
                    )
                }
            }

            // Tags
            if (onTagSelect != null) {
                tags.forEach { tag ->
                    val isSelected = selectedTagId == tag.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTagSelect(if (isSelected) null else tag.id) },
                        label = { Text("#${tag.name}") },
                        modifier = Modifier.semantics { contentDescription = "Filter tag ${tag.name}" }
                    )
                }
            }
        }
    }
}
