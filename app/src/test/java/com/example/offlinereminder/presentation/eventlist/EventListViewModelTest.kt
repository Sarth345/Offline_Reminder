package com.example.offlinereminder.presentation.eventlist

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class EventListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val categoryRepository = InMemoryCategoryRepository()
    private val eventTypeRepository = InMemoryEventTypeRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository()
    private val tagRepository = InMemoryTagRepository()

    private lateinit var getEventsUseCase: GetEventsUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase
    private lateinit var deleteEventUseCase: DeleteEventUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        getEventsUseCase = GetEventsUseCase(eventRepository)
        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        getEventTypesUseCase = GetEventTypesUseCase(eventTypeRepository)
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        getTagsUseCase = GetTagsUseCase(tagRepository)
        deleteEventUseCase = DeleteEventUseCase(eventRepository)
    }

    private fun createViewModel(): EventListViewModel = EventListViewModel(
        getEventsUseCase = getEventsUseCase,
        getCategoriesUseCase = getCategoriesUseCase,
        getEventTypesUseCase = getEventTypesUseCase,
        getEventGroupsUseCase = getEventGroupsUseCase,
        getTagsUseCase = getTagsUseCase,
        eventRepository = eventRepository,
        deleteEventUseCase = deleteEventUseCase,
        dispatcherProvider = dispatcherProvider
    )

    private fun sampleEvent(
        id: String,
        title: String,
        categoryId: String = "cat_work",
        eventTypeId: String = "type_meeting",
        isFavorite: Boolean = false,
        status: EventLifecycleStatus = EventLifecycleStatus.ACTIVE
    ): Event = Event(
        id = id,
        title = title,
        description = "Notes about $title",
        eventTypeId = eventTypeId,
        categoryId = categoryId,
        priority = Priority.NORMAL,
        lifecycleStatus = status,
        isFavorite = isFavorite,
        temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 22)),
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun init_loadsEventsAndCatalogs() = runTest(testDispatcher) {
        categoryRepository.saveCategory(Category("cat_work", "Work", 1, null, false, 0))
        eventTypeRepository.saveEventType(EventType("type_meeting", "Meeting", "meeting", "cat_work", createdAt = timeProvider.nowInstant()))
        eventRepository.saveEvent(sampleEvent("e1", "Standup Meeting"))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.events.size)
        assertEquals(1, state.availableCategories.size)
        assertEquals(1, state.availableEventTypes.size)
    }

    @Test
    fun search_filtersEventsByTitleAndDescription() = runTest(testDispatcher) {
        eventRepository.saveEvent(sampleEvent("e1", "Doctor Appointment"))
        eventRepository.saveEvent(sampleEvent("e2", "Dentist Checkup"))
        eventRepository.saveEvent(sampleEvent("e3", "Grocery Shopping"))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.filteredEvents.size)

        viewModel.onAction(EventListAction.Search("Dentist"))
        assertEquals(1, viewModel.uiState.value.filteredEvents.size)
        assertEquals("Dentist Checkup", viewModel.uiState.value.filteredEvents.first().title)

        viewModel.onAction(EventListAction.Search("Notes"))
        assertEquals(3, viewModel.uiState.value.filteredEvents.size)
    }

    @Test
    fun filterCategory_and_filterFavorites() = runTest(testDispatcher) {
        eventRepository.saveEvent(sampleEvent("e1", "Work Task", categoryId = "cat_work", isFavorite = false))
        eventRepository.saveEvent(sampleEvent("e2", "Personal Task", categoryId = "cat_personal", isFavorite = true))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventListAction.FilterCategory("cat_personal"))
        assertEquals(1, viewModel.uiState.value.filteredEvents.size)
        assertEquals("Personal Task", viewModel.uiState.value.filteredEvents.first().title)

        viewModel.onAction(EventListAction.FilterCategory(null))
        viewModel.onAction(EventListAction.ToggleOnlyFavorites(true))
        assertEquals(1, viewModel.uiState.value.filteredEvents.size)
        assertEquals("Personal Task", viewModel.uiState.value.filteredEvents.first().title)
    }

    @Test
    fun actions_moveToTrash_and_restoreFromTrash() = runTest(testDispatcher) {
        val event = sampleEvent("e1", "Task to Trash")
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.events.size)

        // Move to trash
        viewModel.onAction(EventListAction.MoveToTrash("e1"))
        testScheduler.advanceUntilIdle()

        // Active list should now be empty
        assertEquals(0, viewModel.uiState.value.events.size)

        // Switch filter to TRASHED
        viewModel.onAction(EventListAction.FilterStatus(EventLifecycleStatus.TRASHED))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.events.size)
        assertEquals("Task to Trash", viewModel.uiState.value.events.first().title)

        // Restore from trash
        viewModel.onAction(EventListAction.RestoreFromTrash("e1"))
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.events.size)
    }
}
