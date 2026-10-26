package com.example.offlinereminder.presentation.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.offlinereminder.OfflineReminderApplication
import com.example.offlinereminder.data.alarm.AlarmPlaybackService
import com.example.offlinereminder.data.alarm.AlarmState
import com.example.offlinereminder.data.alarm.AlarmStateManager
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.ui.theme.OfflineReminderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Visual-only full-screen presentation for active alarms.
 *
 * Shows above the lockscreen and turns the screen on.
 * Renders complete reminder details: Title, Occurrence Date & Time,
 * Location (omitted if blank), Description/Notes (omitted if blank),
 * Category, Priority, and Recurrence indicator.
 * Provides prominent Snooze and Dismiss actions.
 */
class AlarmAlertActivity : ComponentActivity() {

    private var instanceId: String? = null
    private var eventId: String? = null

    // State properties for reactive compose UI
    private var eventTitleState by mutableStateOf("Alarm")
    private var eventDescState by mutableStateOf<String?>(null)
    private var locationState by mutableStateOf<String?>(null)
    private var formattedDateTimeState by mutableStateOf("")
    private var categoryNameState by mutableStateOf<String?>(null)
    private var categoryColorIntState by mutableStateOf<Int?>(null)
    private var priorityState by mutableStateOf(Priority.NORMAL)
    private var isRecurringState by mutableStateOf(false)
    private var recurrenceInfoState by mutableStateOf<String?>(null)
    private var snoozeDurationMinutesState by mutableIntStateOf(10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenVisibility()

        instanceId = intent.getStringExtra(NotificationConstants.EXTRA_INSTANCE_ID)
        eventId = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_ID)

        // Seed initial UI values immediately from intent extras for instantaneous display
        val initialTitle = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_TITLE)
        if (!initialTitle.isNullOrBlank()) {
            eventTitleState = initialTitle
        }
        val initialDesc = intent.getStringExtra(NotificationConstants.EXTRA_EVENT_DESC)
        if (!initialDesc.isNullOrBlank()) {
            eventDescState = initialDesc
        }
        val initialLocation = intent.getStringExtra(NotificationConstants.EXTRA_LOCATION)
        if (!initialLocation.isNullOrBlank()) {
            locationState = initialLocation
        }
        snoozeDurationMinutesState = intent.getIntExtra(NotificationConstants.EXTRA_SNOOZE_DURATION_MINUTES, 10)

        val rawOccStart = intent.getStringExtra(NotificationConstants.EXTRA_OCCURRENCE_START)
        if (!rawOccStart.isNullOrBlank()) {
            try {
                val parsedDateTime = LocalDateTime.parse(rawOccStart)
                formattedDateTimeState = formatOccurrenceDateTime(parsedDateTime)
            } catch (_: Exception) {
                formattedDateTimeState = rawOccStart
            }
        }

        // Set rich Compose UI
        setContent {
            OfflineReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmAlertContent(
                        title = eventTitleState,
                        formattedDateTime = formattedDateTimeState,
                        location = locationState,
                        description = eventDescState,
                        categoryName = categoryNameState,
                        categoryColorInt = categoryColorIntState,
                        priority = priorityState,
                        isRecurring = isRecurringState,
                        recurrenceInfo = recurrenceInfoState,
                        snoozeMinutes = snoozeDurationMinutesState,
                        onSnooze = {
                            instanceId?.let { id ->
                                val durationMillis = snoozeDurationMinutesState * 60 * 1000L
                                AlarmPlaybackService.snooze(this@AlarmAlertActivity, id, eventId, durationMillis)
                            }
                            finish()
                        },
                        onDismiss = {
                            instanceId?.let { id ->
                                AlarmPlaybackService.dismiss(this@AlarmAlertActivity, id, eventId)
                            }
                            finish()
                        }
                    )
                }
            }
        }

        // Query authoritative database records asynchronously via domain repositories to enrich details
        loadAuthoritativeDetails()

        // Observe alarm state; if this instance is dismissed, snoozed, or timed out elsewhere, finish activity
        lifecycleScope.launch {
            AlarmStateManager.getInstance().stateFlow.collectLatest { snapshot ->
                if (snapshot.instanceId == instanceId && (snapshot.state == AlarmState.DISMISSED || snapshot.state == AlarmState.SNOOZED || snapshot.state == AlarmState.TIMED_OUT)) {
                    finish()
                }
            }
        }
    }

    private fun loadAuthoritativeDetails() {
        val currentEventId = eventId ?: return
        val currentInstanceId = instanceId
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val appContainer = (applicationContext as? OfflineReminderApplication)?.container
                val event = appContainer?.eventRepository?.getEvent(currentEventId)
                val instanceEntity = if (!currentInstanceId.isNullOrBlank()) {
                    appContainer?.notificationRepository?.getInstanceById(currentInstanceId)
                } else null

                val category = if (event != null && event.categoryId.isNotBlank()) {
                    appContainer?.categoryRepository?.getCategory(event.categoryId)
                } else null

                val profileId = instanceEntity?.scheduleId?.let { sId ->
                    event?.notificationSchedules?.firstOrNull { it.id == sId }?.profileId
                }
                val profile = if (!profileId.isNullOrBlank()) {
                    appContainer?.notificationProfileRepository?.getProfileById(profileId)
                } else null

                withContext(Dispatchers.Main) {
                    if (profile != null && profile.defaultSnoozeDurationMinutes > 0) {
                        snoozeDurationMinutesState = profile.defaultSnoozeDurationMinutes
                    }
                    if (event != null) {
                        eventTitleState = event.title
                        eventDescState = event.description.ifBlank { null }

                        val loc = event.location?.placeName?.ifBlank { null }
                            ?: event.location?.addressText?.ifBlank { null }
                        locationState = loc

                        categoryNameState = category?.name
                        categoryColorIntState = category?.color
                        priorityState = event.priority

                        isRecurringState = event.isRecurring
                        if (event.isRecurring) {
                            val freq = event.recurrenceRule?.frequency?.name
                                ?.lowercase()
                                ?.replaceFirstChar { it.uppercase() } ?: "Repeating"
                            recurrenceInfoState = "$freq Reminder"
                        }
                    }

                    if (instanceEntity != null) {
                        formattedDateTimeState = formatOccurrenceDateTime(instanceEntity.occurrenceOriginalStartDateTime)
                    } else if (event != null && formattedDateTimeState.isBlank()) {
                        formattedDateTimeState = formatOccurrenceDateTime(event.temporal.startLocalDateTime())
                    }
                }
            } catch (_: Exception) {
                // Keep intent extra values as fallback
            }
        }
    }

    private fun configureLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        fun formatOccurrenceDateTime(dateTime: LocalDateTime): String {
            val today = LocalDate.now()
            val date = dateTime.toLocalDate()
            val time = dateTime.toLocalTime()
            val timeStr = time.format(DateTimeFormatter.ofPattern("h:mm a"))

            val dateStr = when {
                date == today -> "Today"
                date == today.plusDays(1) -> "Tomorrow"
                date == today.minusDays(1) -> "Yesterday"
                date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
            }
            return "$dateStr • $timeStr"
        }
    }
}

@Composable
private fun AlarmAlertContent(
    title: String,
    formattedDateTime: String,
    location: String?,
    description: String?,
    categoryName: String?,
    categoryColorInt: Int?,
    priority: Priority,
    isRecurring: Boolean,
    recurrenceInfo: String?,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Alarm icon badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "Alarm",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top Status Label
            Text(
                text = "ALARM RINGING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Event Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Date & Time
            if (formattedDateTime.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Time",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDateTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Recurrence and badges row
            if (isRecurring || !categoryName.isNullOrBlank() || priority != Priority.NORMAL) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecurring) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Recurring",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = recurrenceInfo ?: "Recurring",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!categoryName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (categoryColorInt != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(categoryColorInt))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (priority == Priority.HIGH || priority == Priority.CRITICAL) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = priority.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Location Section (rendered only if location is non-blank)
            if (!location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Description / Notes Section (rendered only if description is non-blank)
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Notes,
                            contentDescription = "Notes",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Action Buttons at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Snooze (${snoozeMinutes}m)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dismiss",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
