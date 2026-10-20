package com.example.offlinereminder.presentation.eventdetail

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
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

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
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    private lateinit var deleteEventUseCase: DeleteEventUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        categoryRepository.categories.value = emptyMap()
        eventTypeRepository.types.value = emptyMap()
        eventGroupRepository.groups.value = emptyMap()
        tagRepository.tags.value = emptyMap()
        occurrenceExceptionRepository.clear()

        getEventByIdUseCase = GetEventByIdUseCase(eventRepository)
        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        getEventTypesUseCase = GetEventTypesUseCase(eventTypeRepository)
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        getTagsUseCase = GetTagsUseCase(tagRepository)

        val completeUseCase = CompleteOccurrenceUseCase(timeProvider)
        completeOccurrenceActionUseCase = CompleteOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            completeOccurrenceUseCase = completeUseCase,
            timeProvider = timeProvider
        )

        skipOccurrenceActionUseCase = SkipOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository
        )

        deleteEventUseCase = DeleteEventUseCase(eventRepository)
    }

    private fun createViewModel(): EventDetailViewModel = EventDetailViewModel(
        getEventByIdUseCase = getEventByIdUseCase,
        getCategoriesUseCase = getCategoriesUseCase,
        getEventTypesUseCase = getEventTypesUseCase,
        getEventGroupsUseCase = getEventGroupsUseCase,
        getTagsUseCase = getTagsUseCase,
        eventRepository = eventRepository,
        occurrenceExceptionRepository = occurrenceExceptionRepository,
        recurrenceEngine = recurrenceEngine,
        timeProvider = timeProvider,
        completeOccurrenceActionUseCase = completeOccurrenceActionUseCase,
        skipOccurrenceActionUseCase = skipOccurrenceActionUseCase,
        deleteEventUseCase = deleteEventUseCase,
        dispatcherProvider = dispatcherProvider
    )

    @Test
    fun loadEvent_populatesDetailsAndProjectedOccurrences() = runTest(testDispatcher) {
        val cat = Category("c1", "Personal", 0, null, false, 0)
        val type = EventType("t1", "Meeting", "meeting", "c1", createdAt = timeProvider.nowInstant())
        val group = EventGroup("g1", "Project X", "", null, null, timeProvider.nowInstant(), timeProvider.nowInstant())
        val tag = Tag("tag1", "Urgent", "urgent", 0)

        categoryRepository.saveCategory(cat)
        eventTypeRepository.saveEventType(type)
        eventGroupRepository.saveGroup(group)
        tagRepository.saveTag(tag)

        val event = Event(
            id = "event_100",
            title = "Quarterly Sync",
            description = "Detailed notes",
            eventTypeId = "t1",
            categoryId = "c1",
            priority = Priority.HIGH,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone),
            groupIds = setOf("g1"),
            tagIds = setOf("tag1"),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        viewModel.onAction(EventDetailAction.LoadEvent("event_100"))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.event)
        assertEquals("Quarterly Sync", state.event?.title)
        assertEquals("Personal", state.category?.name)
        assertEquals("Meeting", state.eventType?.name)
        assertEquals(1, state.groups.size)
        assertEquals("Project X", state.groups.first().name)
        assertEquals(1, state.tags.size)
        assertEquals("Urgent", state.tags.first().name)
        assertEquals(1, state.occurrences.size)
    }

    @Test
    fun actions_toggleFavorite_and_deletePermanently() = runTest(testDispatcher) {
        val event = Event(
            id = "event_200",
            title = "Delete Me",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone),
            isFavorite = false,
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        viewModel.onAction(EventDetailAction.LoadEvent("event_200"))
        testScheduler.advanceUntilIdle()

        // Toggle favorite
        viewModel.onAction(EventDetailAction.ToggleFavorite)
        testScheduler.advanceUntilIdle()

        val updated = eventRepository.getEvent("event_200")
        assertTrue(updated?.isFavorite == true)

        // Delete permanently
        viewModel.onAction(EventDetailAction.DeletePermanently)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleted)
        assertTrue(eventRepository.getEvent("event_200") == null)
    }

    @Test
    fun actions_completeOccurrence() = runTest(testDispatcher) {
        val start = LocalDateTime.of(2026, 10, 22, 10, 0)
        val event = Event(
            id = "event_300",
            title = "Task",
            eventTypeId = "t1",
            categoryId = "c1",
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(start, utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val viewModel = createViewModel()
        viewModel.onAction(EventDetailAction.LoadEvent("event_300"))
        testScheduler.advanceUntilIdle()

        val occ = viewModel.uiState.value.occurrences.first()
        viewModel.onAction(EventDetailAction.CompleteOccurrence(occ))
        testScheduler.advanceUntilIdle()

        val exception = occurrenceExceptionRepository.getExceptionByOccurrenceId(occ.occurrenceId)
        assertEquals(OccurrenceStatus.COMPLETED, exception?.status)
    }
}
