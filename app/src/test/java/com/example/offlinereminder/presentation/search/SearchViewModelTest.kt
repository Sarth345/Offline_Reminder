package com.example.offlinereminder.presentation.search

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.SearchEventsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)
    private val categoryRepository = InMemoryCategoryRepository()
    private val eventTypeRepository = InMemoryEventTypeRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository(eventRepository)
    private val tagRepository = InMemoryTagRepository()

    private lateinit var searchEventsUseCase: SearchEventsUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        occurrenceExceptionRepository.clear()

        searchEventsUseCase = SearchEventsUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        getEventTypesUseCase = GetEventTypesUseCase(eventTypeRepository)
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        getTagsUseCase = GetTagsUseCase(tagRepository)
    }

    private fun createViewModel(): SearchViewModel = SearchViewModel(
        searchEventsUseCase = searchEventsUseCase,
        getCategoriesUseCase = getCategoriesUseCase,
        getEventTypesUseCase = getEventTypesUseCase,
        getEventGroupsUseCase = getEventGroupsUseCase,
        getTagsUseCase = getTagsUseCase,
        eventRepository = eventRepository,
        dispatcherProvider = dispatcherProvider
    )

    private fun createEvent(
        id: String,
        title: String,
        description: String = "",
        categoryId: String = "cat_1",
        priority: Priority = Priority.NORMAL,
        isFavorite: Boolean = false,
        dateTime: LocalDateTime = LocalDateTime.of(2026, 10, 25, 10, 0)
    ): Event = Event(
        id = id,
        title = title,
        description = description,
        eventTypeId = "type_1",
        categoryId = categoryId,
        priority = priority,
        isFavorite = isFavorite,
        lifecycleStatus = EventLifecycleStatus.ACTIVE,
        temporal = EventDateTime.DateTime(dateTime, utcZone),
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun init_loadsMetadataAndActiveEvents() = runTest(testDispatcher) {
        val cat = Category(id = "cat_work", name = "Work")
        categoryRepository.saveCategory(cat)

        val evt = createEvent("e1", "Dentist", categoryId = "cat_work")
        eventRepository.saveEvent(evt)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.availableCategories.size)
        assertEquals("Work", state.availableCategories[0].name)
        assertEquals(1, state.results.size)
        assertEquals("Dentist", state.results[0].title)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun queryChanged_filtersResults() = runTest(testDispatcher) {
        val e1 = createEvent("e1", "Doctor Appointment")
        val e2 = createEvent("e2", "Buy Groceries")
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(SearchAction.QueryChanged("doctor"))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("doctor", state.searchQuery)
        assertEquals(1, state.results.size)
        assertEquals("e1", state.results[0].id)
    }

    @Test
    fun toggleCategory_and_togglePriority_updatesFilters() = runTest(testDispatcher) {
        val e1 = createEvent("e1", "Event 1", categoryId = "cat_A", priority = Priority.HIGH)
        val e2 = createEvent("e2", "Event 2", categoryId = "cat_B", priority = Priority.NORMAL)
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Toggle Category A
        viewModel.onAction(SearchAction.ToggleCategory("cat_A"))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedCategoryIds.contains("cat_A"))
        assertEquals(1, viewModel.uiState.value.results.size)

        // Toggle Priority HIGH
        viewModel.onAction(SearchAction.TogglePriority(Priority.HIGH))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedPriorities.contains(Priority.HIGH))
        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun toggleFavorites_filtersByFavorites() = runTest(testDispatcher) {
        val e1 = createEvent("e1", "Favorite", isFavorite = true)
        val e2 = createEvent("e2", "Not Favorite", isFavorite = false)
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(SearchAction.ToggleFavorites)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.onlyFavorites)
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("e1", viewModel.uiState.value.results[0].id)
    }

    @Test
    fun setDateRange_filtersByDateRange() = runTest(testDispatcher) {
        val e1 = createEvent("e1", "October Event", dateTime = LocalDateTime.of(2026, 10, 22, 10, 0))
        val e2 = createEvent("e2", "November Event", dateTime = LocalDateTime.of(2026, 11, 5, 10, 0))
        eventRepository.saveEvent(e1)
        eventRepository.saveEvent(e2)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(
            SearchAction.SetDateRange(
                startDate = LocalDate.of(2026, 10, 20),
                endDate = LocalDate.of(2026, 10, 31)
            )
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 10, 20), state.startDate)
        assertEquals(LocalDate.of(2026, 10, 31), state.endDate)
        assertEquals(1, state.results.size)
        assertEquals("e1", state.results[0].id)
    }

    @Test
    fun clearFilters_resetsFiltersWhileRetainingSearchQuery() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(SearchAction.QueryChanged("test"))
        viewModel.onAction(SearchAction.ToggleCategory("cat_X"))
        viewModel.onAction(SearchAction.ToggleFavorites)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasActiveFilters)

        // Clear filters
        viewModel.onAction(SearchAction.ClearFilters)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("test", state.searchQuery)
        assertTrue(state.selectedCategoryIds.isEmpty())
        assertFalse(state.onlyFavorites)
    }

    @Test
    fun clearSearch_resetsQuery() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(SearchAction.QueryChanged("my query"))
        testScheduler.advanceUntilIdle()
        assertEquals("my query", viewModel.uiState.value.searchQuery)

        viewModel.onAction(SearchAction.ClearSearch)
        testScheduler.advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun filterSheet_openAndClose() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFilterSheetVisible)

        viewModel.onAction(SearchAction.OpenFilterSheet)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFilterSheetVisible)

        viewModel.onAction(SearchAction.CloseFilterSheet)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFilterSheetVisible)
    }

    @Test
    fun toggleFavorite_updatesRepository() = runTest(testDispatcher) {
        val event = createEvent("eFav", "Event", isFavorite = false)
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(SearchAction.ToggleFavorite("eFav", true))
        testScheduler.advanceUntilIdle()

        val updated = eventRepository.getEvent("eFav")
        assertTrue(updated?.isFavorite == true)
    }
}
