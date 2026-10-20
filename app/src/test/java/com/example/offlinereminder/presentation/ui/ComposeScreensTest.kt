package com.example.offlinereminder.presentation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.presentation.dashboard.DashboardAction
import com.example.offlinereminder.presentation.dashboard.DashboardContent
import com.example.offlinereminder.presentation.dashboard.DashboardUiState
import com.example.offlinereminder.presentation.eventdetail.EventDetailAction
import com.example.offlinereminder.presentation.eventdetail.EventDetailContent
import com.example.offlinereminder.presentation.eventdetail.EventDetailUiState
import com.example.offlinereminder.presentation.eventeditor.EventEditorAction
import com.example.offlinereminder.presentation.eventeditor.EventEditorContent
import com.example.offlinereminder.presentation.eventeditor.EventEditorUiState
import com.example.offlinereminder.presentation.eventlist.EventListAction
import com.example.offlinereminder.presentation.eventlist.EventListContent
import com.example.offlinereminder.presentation.eventlist.EventListUiState
import com.example.offlinereminder.presentation.upcoming.UpcomingAction
import com.example.offlinereminder.presentation.upcoming.UpcomingContent
import com.example.offlinereminder.presentation.upcoming.UpcomingUiState
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
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(packageName = "com.example.offlinereminder")
class ComposeScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val category = Category(id = "cat-1", name = "Health")
    private val eventType = EventType(
        id = "type-1",
        name = "Doctor",
        key = "doctor",
        categoryId = "cat-1",
        createdAt = Instant.parse("2026-10-20T10:00:00Z")
    )
    private val group = EventGroup(
        id = "group-1",
        name = "Family",
        createdAt = Instant.parse("2026-10-20T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-20T10:00:00Z")
    )
    private val tag = Tag(id = "tag-1", name = "urgent")

    private val sampleEvent = Event(
        id = "evt-123",
        title = "Dentist Checkup",
        description = "Routine dental examination",
        categoryId = "cat-1",
        eventTypeId = "type-1",
        priority = Priority.HIGH,
        temporal = EventDateTime.DateTime(
            dateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
            zoneId = ZoneId.of("UTC")
        ),
        groupIds = setOf("group-1"),
        tagIds = setOf("tag-1"),
        checklist = Checklist(
            title = "Prep",
            items = listOf(ChecklistItem(id = "chk-1", text = "Bring insurance card", isCompleted = false))
        ),
        createdAt = Instant.parse("2026-10-20T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-20T10:00:00Z")
    )

    private val sampleOccurrence = EventOccurrence(
        eventId = sampleEvent.id,
        originalStartDateTime = LocalDateTime.of(2026, 10, 25, 14, 0),
        status = OccurrenceStatus.PENDING
    )

    private val sampleOccurrenceWithEvent = EventOccurrenceWithEvent(
        occurrence = sampleOccurrence,
        event = sampleEvent
    )

    @Test
    fun dashboardRenders_emptyState() {
        var addNavigated = false
        composeTestRule.setContent {
            OfflineReminderTheme {
                DashboardContent(
                    uiState = DashboardUiState(isLoading = false),
                    onAction = {},
                    onNavigateToDetails = {},
                    onNavigateToAddEvent = { addNavigated = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Today's Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("All clear for today!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Reminder").performClick()
        assertTrue("Clicking Add Reminder should invoke navigate callback", addNavigated)
    }

    @Test
    fun dashboardRenders_withOverdueAndTodayOccurrences() {
        var completed = false

        val overdueOccurrence = EventOccurrence(
            eventId = sampleEvent.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 24, 14, 0),
            status = OccurrenceStatus.OVERDUE
        )
        val overdueOcc = EventOccurrenceWithEvent(
            occurrence = overdueOccurrence,
            event = sampleEvent
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                DashboardContent(
                    uiState = DashboardUiState(
                        todayOccurrences = listOf(sampleOccurrenceWithEvent),
                        overdueOccurrences = listOf(overdueOcc)
                    ),
                    onAction = { action ->
                        if (action is DashboardAction.CompleteOccurrence) {
                            completed = true
                        }
                    },
                    onNavigateToDetails = {},
                    onNavigateToAddEvent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Overdue (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 actionable reminders").assertIsDisplayed()

        // Complete action
        composeTestRule.onAllNodesWithText("Complete")[0].performClick()
        assertTrue("Complete action should be dispatched", completed)
    }

    @Test
    fun eventListRenders_andSupportsSearchAndFilterInteraction() {
        var recordedSearch = ""
        var recordedStatus: EventLifecycleStatus? = null

        composeTestRule.setContent {
            OfflineReminderTheme {
                EventListContent(
                    uiState = EventListUiState(
                        filteredEvents = listOf(sampleEvent),
                        availableCategories = listOf(category),
                        availableEventTypes = listOf(eventType),
                        availableGroups = listOf(group),
                        availableTags = listOf(tag)
                    ),
                    onAction = { action ->
                        when (action) {
                            is EventListAction.Search -> recordedSearch = action.query
                            is EventListAction.FilterStatus -> recordedStatus = action.status
                            else -> {}
                        }
                    },
                    onNavigateToDetails = {},
                    onNavigateToAddEvent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("All Reminders").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dentist Checkup").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Health")[0].assertExists()
        composeTestRule.onAllNodesWithText("Doctor")[0].assertExists()
        composeTestRule.onAllNodesWithText("Family")[0].assertExists()
        composeTestRule.onAllNodesWithText("#urgent")[0].assertExists()

        // Test search input
        composeTestRule.onNodeWithContentDescription("Search events").performTextInput("Dental")
        assertEquals("Dental", recordedSearch)

        // Test filter chip click
        composeTestRule.onNodeWithContentDescription("Filter status ARCHIVED").performClick()
        assertEquals(EventLifecycleStatus.ARCHIVED, recordedStatus)
    }

    @Test
    fun eventDetailRenders_andSupportsNavigationAndActions() {
        var backPressed = false
        var editNavigated = false
        var favoriteToggled = false

        composeTestRule.setContent {
            OfflineReminderTheme {
                EventDetailContent(
                    uiState = EventDetailUiState(
                        event = sampleEvent,
                        category = category,
                        eventType = eventType,
                        groups = listOf(group),
                        tags = listOf(tag),
                        occurrences = listOf(sampleOccurrence),
                        isFavorite = false
                    ),
                    onAction = { action ->
                        if (action is EventDetailAction.ToggleFavorite) {
                            favoriteToggled = true
                        }
                    },
                    onNavigateBack = { backPressed = true },
                    onNavigateToEdit = { editNavigated = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Event Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dentist Checkup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Routine dental examination").assertIsDisplayed()
        composeTestRule.onNodeWithText("Prep").assertExists()

        // Navigation actions
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        assertTrue("Back button pressed", backPressed)

        composeTestRule.onNodeWithContentDescription("Edit event").performClick()
        assertTrue("Edit button pressed", editNavigated)

        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()
        assertTrue("Favorite toggled", favoriteToggled)
    }

    @Test
    fun addEditEventFlow_rendersAndDispatchesUpdates() {
        var updatedTitle = ""
        var selectedPriority: Priority? = null
        var saved = false

        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(
                    EventEditorUiState(
                        title = "New Appointment",
                        categoryId = "cat-1",
                        eventTypeId = "type-1",
                        temporal = sampleEvent.temporal,
                        availableCategories = listOf(category),
                        availableEventTypes = listOf(eventType),
                        isEditing = false
                    )
                )
            }

            OfflineReminderTheme {
                EventEditorContent(
                    uiState = state,
                    onAction = { action ->
                        when (action) {
                            is EventEditorAction.UpdateTitle -> {
                                updatedTitle = action.title
                                state = state.copy(title = action.title)
                            }
                            is EventEditorAction.UpdatePriority -> {
                                selectedPriority = action.priority
                                state = state.copy(priority = action.priority)
                            }
                            is EventEditorAction.SaveEvent -> saved = true
                            else -> {}
                        }
                    },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("New Reminder").assertIsDisplayed()

        // Input title
        composeTestRule.onNodeWithContentDescription("Event title input").performTextInput(" Updated")
        assertTrue("Title was updated", updatedTitle.isNotBlank())

        // Select priority
        composeTestRule.onNodeWithContentDescription("Select priority CRITICAL").performClick()
        assertEquals(Priority.CRITICAL, selectedPriority)

        // Save event
        composeTestRule.onNodeWithContentDescription("Save event").performClick()
        assertTrue("Save event action dispatched", saved)
    }

    @Test
    fun eventEditor_displaysValidationError() {
        composeTestRule.setContent {
            OfflineReminderTheme {
                EventEditorContent(
                    uiState = EventEditorUiState(
                        title = "",
                        validationError = "Title cannot be blank",
                        isEditing = false
                    ),
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Title cannot be blank").assertIsDisplayed()
    }

    @Test
    fun upcomingRenders_groupedByDate() {
        val targetDate = LocalDate.of(2026, 10, 25)
        val groupedMap = mapOf(targetDate to listOf(sampleOccurrenceWithEvent))

        composeTestRule.setContent {
            OfflineReminderTheme {
                UpcomingContent(
                    uiState = UpcomingUiState(
                        occurrences = listOf(sampleOccurrenceWithEvent),
                        occurrencesByDate = groupedMap
                    ),
                    onAction = {},
                    onNavigateToDetails = {},
                    onNavigateToAddEvent = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Upcoming Agenda").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 occurrences in next 14 days").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dentist Checkup").assertIsDisplayed()
    }
}
