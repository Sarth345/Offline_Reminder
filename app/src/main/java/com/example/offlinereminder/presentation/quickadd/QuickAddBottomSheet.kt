package com.example.offlinereminder.presentation.quickadd

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.presentation.components.ChipBadge
import com.example.offlinereminder.presentation.components.DateTimeFormatters
import com.example.offlinereminder.presentation.components.PriorityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    viewModel: QuickAddViewModel,
    onDismiss: () -> Unit,
    onEventSaved: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            val eventId = uiState.savedEventId
            viewModel.onAction(QuickAddAction.Reset)
            onDismiss()
            if (eventId != null) {
                onEventSaved(eventId)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onAction(QuickAddAction.Reset)
            onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier
    ) {
        QuickAddContent(
            uiState = uiState,
            onAction = viewModel::onAction,
            onDismiss = {
                viewModel.onAction(QuickAddAction.Reset)
                onDismiss()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddContent(
    uiState: QuickAddUiState,
    onAction: (QuickAddAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Quick Add Reminder",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.input,
            onValueChange = { onAction(QuickAddAction.UpdateInput(it)) },
            placeholder = { Text("e.g. Doctor tomorrow 3pm !high #Health rem:30m") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Quick add input field" },
            singleLine = false,
            maxLines = 3,
            isError = uiState.errorMessage != null || (uiState.draft != null && !uiState.draft.isValid)
        )

        if (uiState.errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Parsed Live Preview Section
        val draft = uiState.draft
        if (draft != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Preview: ${draft.title.ifBlank { "(Untitled reminder)" }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PriorityBadge(priority = draft.priority)

                        if (draft.date != null) {
                            val timeText = if (draft.time != null) {
                                "${DateTimeFormatters.formatDate(draft.date)} at ${draft.time}"
                            } else {
                                "${DateTimeFormatters.formatDate(draft.date)} (All-day)"
                            }
                            ChipBadge(
                                text = timeText,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (draft.recurrenceRule != null) {
                            ChipBadge(
                                text = "Repeats ${draft.recurrenceRule.frequency.name.lowercase()}",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (!draft.categoryName.isNullOrBlank()) {
                            ChipBadge(
                                text = "#${draft.categoryName}",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                textColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        draft.groupNames.forEach { group ->
                            ChipBadge(
                                text = "@$group",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        draft.tagNames.forEach { tag ->
                            ChipBadge(
                                text = "+$tag",
                                containerColor = MaterialTheme.colorScheme.surface,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (draft.reminderMinutesBefore != null) {
                            val remText = if (draft.reminderMinutesBefore == 0) "At event time" else "${draft.reminderMinutesBefore}m before"
                            ChipBadge(
                                text = "Remind: $remText",
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                textColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    if (draft.ambiguities.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = draft.ambiguities.first(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Supported shortcuts: !high, !critical, #Work, @Trip, +urgent, rem:15m, tomorrow, 3pm, every Monday.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onAction(QuickAddAction.Save) },
                enabled = uiState.canSave,
                modifier = Modifier.semantics { contentDescription = "Confirm save reminder" }
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save Reminder")
            }
        }
    }
}
