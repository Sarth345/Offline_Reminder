package com.example.offlinereminder.presentation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.model.ConflictSeverity
import com.example.offlinereminder.domain.model.ConflictType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.ScheduleConflict
import com.example.offlinereminder.domain.notification.NotificationScheduleCalculator
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.presentation.eventeditor.EventDateMode
import com.example.offlinereminder.presentation.eventeditor.EventEditorContent
import com.example.offlinereminder.presentation.eventeditor.EventEditorUiState
import com.example.offlinereminder.presentation.eventeditor.formatReminderTiming
import com.example.offlinereminder.presentation.settings.SettingsAction
import com.example.offlinereminder.presentation.settings.SettingsContent
import com.example.offlinereminder.presentation.settings.SettingsUiState
import com.example.offlinereminder.ui.theme.OfflineReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(packageName = "com.example.offlinereminder")
class Planerize11ResponsiveAndEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_rendersPriority2x2Grid_withoutWrappingIssues() {
        var updatedPriority: Priority? = null

        composeTestRule.setContent {
            OfflineReminderTheme {
                SettingsContent(
                    uiState = SettingsUiState(),
                    onAction = { action ->
                        if (action is SettingsAction.UpdateDefaultPriority) {
                            updatedPriority = action.priority
                        }
                    },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Default Priority").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("LOW").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NORMAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("HIGH").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("CRITICAL").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("CRITICAL").performScrollTo().performClick()
        assertEquals(Priority.CRITICAL, updatedPriority)
    }

    @Test
    fun eventEditorScreen_primaryView_showsMinimalFieldsAndNoTimezone() {
        val state = EventEditorUiState(
            title = "Doctor Appointment",
            temporal = EventDateTime.DateTime(
                dateTime = LocalDateTime.of(2026, 10, 25, 15, 0),
                zoneId = ZoneId.of("Asia/Kolkata")
            ),
            timeZoneId = "Asia/Kolkata"
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                EventEditorContent(
                    uiState = state,
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        // Primary fields MUST be visible
        composeTestRule.onNodeWithText("Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Note").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Time").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Repeat").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Reminders").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+ More options").performScrollTo().assertIsDisplayed()

        // Timezone should NOT be visible on primary basic screen
        assertEquals(0, composeTestRule.onAllNodesWithText("Timezone: Asia/Kolkata").fetchSemanticsNodes().size)
    }

    @Test
    fun eventEditorScreen_remindersCard_displaysMultipleRemindersAndAddReminder() {
        val schedules = listOf(
            NotificationSchedule(
                id = "rem-1",
                offsetDuration = Duration.ofDays(1),
                triggerType = NotificationTriggerType.BEFORE_START,
                deliveryType = ReminderDeliveryType.NOTIFICATION
            ),
            NotificationSchedule(
                id = "rem-2",
                offsetDuration = Duration.ofHours(1),
                triggerType = NotificationTriggerType.BEFORE_START,
                deliveryType = ReminderDeliveryType.NOTIFICATION
            ),
            NotificationSchedule(
                id = "rem-3",
                offsetDuration = Duration.ZERO,
                triggerType = NotificationTriggerType.AT_START,
                deliveryType = ReminderDeliveryType.ALARM
            )
        )

        val state = EventEditorUiState(
            title = "Team Meeting",
            temporal = EventDateTime.DateTime(
                dateTime = LocalDateTime.of(2026, 10, 25, 10, 0),
                zoneId = ZoneId.of("UTC")
            ),
            notificationSchedules = schedules
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                EventEditorContent(
                    uiState = state,
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Reminders").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1 day before").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("1 hour before").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("At event").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Alarm").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Add reminder").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun eventEditorScreen_conflictBanner_rendersCompactSummary() {
        val start = LocalDateTime.of(2026, 10, 25, 14, 0)
        val conflictOccurrence = EventOccurrence(
            eventId = "evt-old",
            originalStartDateTime = start,
            zoneId = ZoneId.of("UTC")
        )
        val conflict = ScheduleConflict(
            conflictingEventId = "evt-new",
            conflictingEventTitle = "New Sync",
            conflictingOccurrence = conflictOccurrence,
            existingEventId = "evt-old",
            existingEventTitle = "Existing Sync",
            existingOccurrence = conflictOccurrence,
            type = ConflictType.DIRECT_OVERLAP,
            severity = ConflictSeverity.ADVISORY
        )

        val state = EventEditorUiState(
            title = "New Sync",
            temporal = EventDateTime.DateTime(dateTime = start, zoneId = ZoneId.of("UTC")),
            detectedConflicts = listOf(conflict)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                EventEditorContent(
                    uiState = state,
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Conflict: 1 overlapping event").assertIsDisplayed()
        composeTestRule.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test
    fun customRepeat_recurrenceRule_andEngine_projectsFiveOccurrencesEveryTwoWeeks() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            endCondition = RecurrenceEnd.Count(5)
        )
        assertTrue(rule.validate().isSuccess)

        val start = LocalDateTime.of(2026, 11, 2, 9, 0) // Monday
        val event = Event(
            id = "evt-rec-1",
            title = "Biweekly Sync",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateTime(dateTime = start, zoneId = ZoneId.of("UTC")),
            recurrenceRule = rule,
            createdAt = java.time.Instant.parse("2026-10-20T10:00:00Z"),
            updatedAt = java.time.Instant.parse("2026-10-20T10:00:00Z")
        )
        val timeProvider = TestTimeProvider(
            currentInstant = java.time.Instant.parse("2026-10-20T10:00:00Z"),
            currentZoneId = ZoneId.of("UTC")
        )
        val engine = DefaultRecurrenceEngine(timeProvider)
        val occurrences = engine.projectOccurrences(
            event = event,
            from = start,
            to = start.plusMonths(6)
        )

        assertEquals(5, occurrences.size)
        assertEquals(start, occurrences[0].originalStartDateTime)
        assertEquals(start.plusWeeks(2), occurrences[1].originalStartDateTime)
        assertEquals(start.plusWeeks(4), occurrences[2].originalStartDateTime)
        assertEquals(start.plusWeeks(6), occurrences[3].originalStartDateTime)
        assertEquals(start.plusWeeks(8), occurrences[4].originalStartDateTime)
    }

    @Test
    fun customReminders_calculator_computesAccurateOffsetsForMinutesHoursDaysWeeks() {
        val start = LocalDateTime.of(2026, 10, 25, 15, 0)
        val occ = EventOccurrence(
            eventId = "evt-1",
            originalStartDateTime = start,
            zoneId = ZoneId.of("UTC")
        )

        // 15m before
        val sched15m = NotificationSchedule.beforeStart(Duration.ofMinutes(15))
        val calc15m = NotificationScheduleCalculator.calculate(occ, sched15m)
        assertEquals(LocalDateTime.of(2026, 10, 25, 14, 45), calc15m.nominalDateTime)

        // 2 hours before
        val sched2h = NotificationSchedule.beforeStart(Duration.ofHours(2))
        val calc2h = NotificationScheduleCalculator.calculate(occ, sched2h)
        assertEquals(LocalDateTime.of(2026, 10, 25, 13, 0), calc2h.nominalDateTime)

        // 1 day before
        val sched1d = NotificationSchedule.beforeStart(Duration.ofDays(1))
        val calc1d = NotificationScheduleCalculator.calculate(occ, sched1d)
        assertEquals(LocalDateTime.of(2026, 10, 24, 15, 0), calc1d.nominalDateTime)

        // 1 week before
        val sched1w = NotificationSchedule.beforeStart(Duration.ofDays(7))
        val calc1w = NotificationScheduleCalculator.calculate(occ, sched1w)
        assertEquals(LocalDateTime.of(2026, 10, 18, 15, 0), calc1w.nominalDateTime)

        // At event
        val sched0 = NotificationSchedule.atStart()
        val calc0 = NotificationScheduleCalculator.calculate(occ, sched0)
        assertEquals(start, calc0.nominalDateTime)
    }

    @Test
    fun formatReminderTiming_formatsAllUnitsCorrectly() {
        assertEquals("At event", formatReminderTiming(Duration.ZERO))
        assertEquals("5 minutes before", formatReminderTiming(Duration.ofMinutes(5)))
        assertEquals("15 minutes before", formatReminderTiming(Duration.ofMinutes(15)))
        assertEquals("30 minutes before", formatReminderTiming(Duration.ofMinutes(30)))
        assertEquals("1 hour before", formatReminderTiming(Duration.ofHours(1)))
        assertEquals("2 hours before", formatReminderTiming(Duration.ofHours(2)))
        assertEquals("1 day before", formatReminderTiming(Duration.ofDays(1)))
        assertEquals("2 days before", formatReminderTiming(Duration.ofDays(2)))
        assertEquals("1 week before", formatReminderTiming(Duration.ofDays(7)))
        assertEquals("2 weeks before", formatReminderTiming(Duration.ofDays(14)))
    }
}
