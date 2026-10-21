package com.example.offlinereminder.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceStatus
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventCard(
    event: Event,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    eventTypeName: String? = null,
    groupNames: List<String> = emptyList(),
    tagNames: List<String> = emptyList(),
    onClick: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Event: ${event.title}, Priority: ${event.priority.name}, Status: ${event.lifecycleStatus.name}"
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + Favorite Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (onFavoriteToggle != null) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (event.isFavorite) "Remove from favorites" else "Add to favorites"
                            }
                    ) {
                        Icon(
                            imageVector = if (event.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (event.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Date / Time row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                val timeStr = if (event.temporal.isAllDay) {
                    "${DateTimeFormatters.formatDate(event.temporal.startDate)} (All-day)"
                } else {
                    DateTimeFormatters.formatDateTime(event.temporal.startLocalDateTime())
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (event.notificationSchedules.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications scheduled",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Badges row: Priority, Status, Category, EventType
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PriorityBadge(priority = event.priority)
                StatusBadge(status = event.lifecycleStatus)

                if (!categoryName.isNullOrBlank()) {
                    ChipBadge(text = categoryName, containerColor = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (!eventTypeName.isNullOrBlank()) {
                    ChipBadge(text = eventTypeName, containerColor = MaterialTheme.colorScheme.secondaryContainer, textColor = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                groupNames.forEach { groupName ->
                    ChipBadge(text = groupName, containerColor = MaterialTheme.colorScheme.tertiaryContainer, textColor = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                tagNames.forEach { tagName ->
                    ChipBadge(text = "#$tagName", containerColor = MaterialTheme.colorScheme.surface, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Description preview if present
            if (event.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OccurrenceCard(
    occurrenceWithEvent: EventOccurrenceWithEvent,
    modifier: Modifier = Modifier,
    isOverdue: Boolean = false,
    onClick: () -> Unit,
    onComplete: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onReschedule: (() -> Unit)? = null
) {
    val event = occurrenceWithEvent.event
    val occurrence = occurrenceWithEvent.occurrence

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Occurrence: ${event.title}, Start: ${DateTimeFormatters.formatDateTime(occurrence.effectiveStartDateTime)}"
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title & Occurrence Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (occurrence.isCompleted || occurrence.isSkipped) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (occurrence.status != OccurrenceStatus.PENDING) {
                    ChipBadge(
                        text = occurrence.status.name,
                        containerColor = if (occurrence.isCompleted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        textColor = if (occurrence.isCompleted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }
            }

            // Date / Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                val timeStr = if (event.temporal.isAllDay) {
                    "${DateTimeFormatters.formatDate(occurrence.effectiveStartDateTime.toLocalDate())} (All-day)"
                } else {
                    DateTimeFormatters.formatDateTime(occurrence.effectiveStartDateTime)
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (event.notificationSchedules.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notification indicator",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Badges
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PriorityBadge(priority = event.priority)
                StatusBadge(status = event.lifecycleStatus)

                if (event.isRecurring) {
                    ChipBadge(
                        text = "Recurring",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (event.temporal.isAllDay) {
                    ChipBadge(
                        text = "All-day",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (occurrence.isMultiDay) {
                    ChipBadge(
                        text = "Multi-day",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        textColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                if (occurrence.effectiveZoneId != ZoneId.systemDefault()) {
                    ChipBadge(
                        text = occurrence.effectiveZoneId.id,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Actions: Complete, Reschedule, Skip (only if not already completed or skipped)
            val isActionable = !occurrence.isCompleted && !occurrence.isSkipped
            if (isActionable && (onComplete != null || onReschedule != null || onSkip != null)) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onComplete != null) {
                        FilledTonalButton(
                            onClick = onComplete,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Complete occurrence ${event.title}" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Complete", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (onReschedule != null) {
                        OutlinedButton(
                            onClick = onReschedule,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Reschedule occurrence ${event.title}" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Reschedule", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (onSkip != null) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.semantics { contentDescription = "Skip occurrence ${event.title}" }
                        ) {
                            Text(text = "Skip", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChipBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
