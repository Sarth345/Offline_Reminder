package com.example.offlinereminder.presentation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.presentation.calendar.CalendarAction
import com.example.offlinereminder.presentation.calendar.CalendarContent
import com.example.offlinereminder.presentation.calendar.CalendarUiState
import com.example.offlinereminder.presentation.groups.GroupDetailAction
import com.example.offlinereminder.presentation.groups.GroupDetailContent
import com.example.offlinereminder.presentation.groups.GroupDetailUiState
import com.example.offlinereminder.presentation.groups.GroupsAction
import com.example.offlinereminder.presentation.groups.GroupsContent
import com.example.offlinereminder.presentation.groups.GroupsUiState
import com.example.offlinereminder.ui.theme.OfflineReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(packageName = "com.example.offlinereminder")
class CalendarAndGroupsComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")
    private val utcZone = ZoneId.of("UTC")

    private val sampleGroup = EventGroup(
        id = "group-1",
        name = "Family",
        description = "Family related reminders",
        color = 0xFF1E88E5.toInt(),
        createdAt = testInstant,
        updatedAt = testInstant
    )

    private val sampleEvent = Event(
        id = "evt-1",
        title = "Family Dinner",
        description = "Dinner with parents",
        eventTypeId = "type-1",
        categoryId = "cat-1",
        priority = Priority.NORMAL,
        lifecycleStatus = EventLifecycleStatus.ACTIVE,
        temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 19, 0), utcZone),
        groupIds = setOf("group-1"),
        createdAt = testInstant,
        updatedAt = testInstant
    )

    private val sampleOccurrence = EventOccurrence(
        eventId = sampleEvent.id,
        occurrenceId = "evt-1:2026-10-20T19:00",
        originalStartDateTime = LocalDateTime.of(2026, 10, 20, 19, 0),
        status = OccurrenceStatus.PENDING,
        zoneId = utcZone
    )

    private val sampleOccurrenceWithEvent = EventOccurrenceWithEvent(
        occurrence = sampleOccurrence,
        event = sampleEvent
    )

    @Test
    fun calendarContent_displaysHeaderAndOccurrences() {
        var actionReceived: CalendarAction? = null
        val state = CalendarUiState(
            displayedYearMonth = YearMonth.of(2026, 10),
            selectedDate = LocalDate.of(2026, 10, 20),
            monthOccurrences = listOf(sampleOccurrenceWithEvent),
            occurrencesByDate = mapOf(LocalDate.of(2026, 10, 20) to listOf(sampleOccurrenceWithEvent)),
            selectedDateOccurrences = listOf(sampleOccurrenceWithEvent),
            datesWithOccurrences = setOf(LocalDate.of(2026, 10, 20))
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                CalendarContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onNavigateToDetails = {},
                    onNavigateToAddEventWithDate = {}
                )
            }
        }

        // Check month title is displayed
        composeTestRule.onNodeWithText("October 2026").assertIsDisplayed()
        // Check occurrence title is present
        composeTestRule.onNodeWithText("Family Dinner").assertExists()

        // Click next month
        composeTestRule.onNodeWithContentDescription("Next month").performClick()
        assertEquals(CalendarAction.NextMonth, actionReceived)
    }

    @Test
    fun calendarContent_emptyStateWhenNoOccurrencesForDate() {
        val state = CalendarUiState(
            displayedYearMonth = YearMonth.of(2026, 10),
            selectedDate = LocalDate.of(2026, 10, 21),
            selectedDateOccurrences = emptyList()
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                CalendarContent(
                    uiState = state,
                    onAction = {},
                    onNavigateToDetails = {},
                    onNavigateToAddEventWithDate = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No reminders for this date").assertExists()
        composeTestRule.onNodeWithText("Add for this Date").assertExists()
    }

    @Test
    fun groupsContent_displaysGroupsAndCounts() {
        var actionReceived: GroupsAction? = null
        val state = GroupsUiState(
            groups = listOf(sampleGroup),
            filteredGroups = listOf(sampleGroup),
            eventCounts = mapOf("group-1" to 3)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                GroupsContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onNavigateToGroupDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Groups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Family").assertIsDisplayed()
        composeTestRule.onNodeWithText("Family related reminders").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 reminders").assertIsDisplayed()

        // Enter search text
        composeTestRule.onNodeWithText("Search groups...").performTextInput("Fam")
        assertTrue(actionReceived is GroupsAction.Search)
        assertEquals("Fam", (actionReceived as GroupsAction.Search).query)
    }

    @Test
    fun groupsContent_emptyStateWhenNoGroups() {
        val state = GroupsUiState(
            groups = emptyList(),
            filteredGroups = emptyList()
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                GroupsContent(
                    uiState = state,
                    onAction = {},
                    onNavigateToGroupDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No groups yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Group").assertIsDisplayed()
    }

    @Test
    fun groupDetailContent_displaysGroupHeaderAndEventList() {
        var actionReceived: GroupDetailAction? = null
        val state = GroupDetailUiState(
            group = sampleGroup,
            events = listOf(sampleEvent)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                GroupDetailContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onNavigateBack = {},
                    onNavigateToDetails = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("Family")[0].assertExists()
        composeTestRule.onNodeWithText("Family related reminders").assertIsDisplayed()
        composeTestRule.onNodeWithText("Family Dinner").assertExists()

        composeTestRule.onNodeWithContentDescription("Remove Family Dinner from Family")
            .performScrollTo()
            .performClick()

        // Shows confirmation dialog
        composeTestRule.onNodeWithText("Remove Reminder?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").performClick()

        assertEquals(GroupDetailAction.RemoveEventFromGroup("evt-1"), actionReceived)
    }
}
