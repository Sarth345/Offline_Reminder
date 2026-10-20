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
import com.example.offlinereminder.domain.model.EventFilterParams
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.presentation.search.FilterBottomSheet
import com.example.offlinereminder.presentation.search.FilterSheetContent
import com.example.offlinereminder.presentation.search.SearchAction
import com.example.offlinereminder.presentation.search.SearchContent
import com.example.offlinereminder.presentation.search.SearchUiState
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
class SearchComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")
    private val utcZone = ZoneId.of("UTC")

    private val sampleEvent = Event(
        id = "evt-1",
        title = "Dentist Appointment",
        description = "Routine cleaning",
        eventTypeId = "type-1",
        categoryId = "cat-1",
        priority = Priority.HIGH,
        lifecycleStatus = EventLifecycleStatus.ACTIVE,
        temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 14, 0), utcZone),
        createdAt = testInstant,
        updatedAt = testInstant
    )

    private val sampleCategory = Category(id = "cat-1", name = "Health")

    @Test
    fun searchContent_displaysHeaderAndResults() {
        var actionReceived: SearchAction? = null
        val state = SearchUiState(
            results = listOf(sampleEvent),
            availableCategories = listOf(sampleCategory)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                SearchContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onNavigateBack = {},
                    onNavigateToDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search & Discovery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dentist Appointment").assertExists()
        composeTestRule.onNodeWithText("1 result").assertIsDisplayed()

        // Type query in search field
        composeTestRule.onNodeWithText("Search by title or description...").performTextInput("clean")
        assertTrue(actionReceived is SearchAction.QueryChanged)
        assertEquals("clean", (actionReceived as SearchAction.QueryChanged).query)
    }

    @Test
    fun searchContent_displaysActiveFilterButton() {
        var actionReceived: SearchAction? = null
        val state = SearchUiState(
            filterParams = EventFilterParams(categoryIds = setOf("cat-1")),
            results = listOf(sampleEvent)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                SearchContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onNavigateBack = {},
                    onNavigateToDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Open filters").performClick()
        assertEquals(SearchAction.OpenFilterSheet, actionReceived)
    }

    @Test
    fun searchContent_emptyResults_displaysNoResultsStateWithClearAction() {
        var actionReceived: SearchAction? = null
        val state = SearchUiState(
            filterParams = EventFilterParams(query = "nonexistent"),
            results = emptyList()
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                SearchContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onNavigateBack = {},
                    onNavigateToDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No results found").assertExists()
        composeTestRule.onAllNodesWithText("Clear Filters")[0].assertExists()
    }

    @Test
    fun filterBottomSheet_displaysControlsAndTriggersActions() {
        var actionReceived: SearchAction? = null
        val state = SearchUiState(
            isFilterSheetVisible = true,
            availableCategories = listOf(sampleCategory),
            results = listOf(sampleEvent)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                FilterSheetContent(
                    uiState = state,
                    onAction = { actionReceived = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Filter Reminders").assertExists()
        composeTestRule.onNodeWithText("Clear All").performClick()
        assertEquals(SearchAction.ClearFilters, actionReceived)

        composeTestRule.onNodeWithText("Health").assertExists()
        composeTestRule.onNodeWithText("Health").performScrollTo().performClick()
        assertEquals(SearchAction.ToggleCategory("cat-1"), actionReceived)
    }
}
