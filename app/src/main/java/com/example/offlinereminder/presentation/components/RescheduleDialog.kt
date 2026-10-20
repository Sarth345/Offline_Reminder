package com.example.offlinereminder.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.model.EventOccurrence
import java.time.LocalDateTime

@Composable
fun RescheduleDialog(
    occurrence: EventOccurrence,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val currentStart = occurrence.effectiveStartDateTime

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reschedule Occurrence",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Current start: ${DateTimeFormatters.formatDateTime(currentStart)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Choose a new time:",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onConfirm(currentStart.plusHours(1)) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Reschedule plus 1 hour" }
                    ) {
                        Text("+1 Hour")
                    }
                    OutlinedButton(
                        onClick = { onConfirm(currentStart.plusDays(1)) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Reschedule plus 1 day" }
                    ) {
                        Text("+1 Day")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onConfirm(currentStart.plusDays(2)) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Reschedule plus 2 days" }
                    ) {
                        Text("+2 Days")
                    }
                    OutlinedButton(
                        onClick = { onConfirm(currentStart.plusWeeks(1)) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Reschedule plus 1 week" }
                    ) {
                        Text("+1 Week")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
