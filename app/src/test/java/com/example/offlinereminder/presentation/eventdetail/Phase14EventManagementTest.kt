package com.example.offlinereminder.presentation.eventdetail

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTemplateRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RelationshipType
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.AddEventDependencyUseCase
import com.example.offlinereminder.domain.usecase.AddEventRelationshipUseCase
import com.example.offlinereminder.domain.usecase.ArchiveEventUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.CreateEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.DuplicateEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventDependenciesUseCase
import com.example.offlinereminder.domain.usecase.GetEventDependentsUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventRelationshipsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
import com.example.offlinereminder.domain.usecase.RemoveEventDependencyUseCase
import com.example.offlinereminder.domain.usecase.RemoveEventRelationshipUseCase
import com.example.offlinereminder.domain.usecase.RestoreArchivedEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreTrashedEventUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class Phase14EventManagementTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val categoryRepository = InMemoryCategoryRepository()
    private val eventTypeRepository = InMemoryEventTypeRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository()
    private val tagRepository = InMemoryTagRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val templateRepository = InMemoryEventTemplateRepository()
    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    private lateinit var deleteEventUseCase: DeleteEventUseCase
    private lateinit var trashEventUseCase: TrashEventUseCase
    private lateinit var restoreTrashedEventUseCase: RestoreTrashedEventUseCase
    private lateinit var archiveEventUseCase: ArchiveEventUseCase
    private lateinit var restoreArchivedEventUseCase: RestoreArchivedEventUseCase
    private lateinit var permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase
    private lateinit var duplicateEventUseCase: DuplicateEventUseCase
    private lateinit var createEventTemplateUseCase: CreateEventTemplateUseCase
    private lateinit var getEventDependenciesUseCase: GetEventDependenciesUseCase
    private lateinit var getEventDependentsUseCase: GetEventDependentsUseCase
    private lateinit var addEventDependencyUseCase: AddEventDependencyUseCase
    private lateinit var removeEventDependencyUseCase: RemoveEventDependencyUseCase
    private lateinit var getEventRelationshipsUseCase: GetEventRelationshipsUseCase
    private lateinit var addEventRelationshipUseCase: AddEventRelationshipUseCase
    private lateinit var removeEventRelationshipUseCase: RemoveEventRelationshipUseCase

    @Before
    fun setUp() = runTest(testDispatcher) {
        eventRepository.clear()
        categoryRepository.categories.value = emptyMap()
        eventTypeRepository.types.value = emptyMap()
        eventGroupRepository.groups.value = emptyMap()
        tagRepository.tags.value = emptyMap()
        occurrenceExceptionRepository.clear()
        templateRepository.templates.value = emptyMap()

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
        trashEventUseCase = TrashEventUseCase(timeProvider, eventRepository)
        restoreTrashedEventUseCase = RestoreTrashedEventUseCase(eventRepository)
        archiveEventUseCase = ArchiveEventUseCase(timeProvider, eventRepository)
        restoreArchivedEventUseCase = RestoreArchivedEventUseCase(eventRepository)
        permanentlyDeleteEventUseCase = PermanentlyDeleteEventUseCase(eventRepository)

        duplicateEventUseCase = DuplicateEventUseCase(eventRepository, timeProvider)
        createEventTemplateUseCase = CreateEventTemplateUseCase(
            templateRepository, categoryRepository, eventTypeRepository, timeProvider
        )

        getEventDependenciesUseCase = GetEventDependenciesUseCase(eventRepository)
        getEventDependentsUseCase = GetEventDependentsUseCase(eventRepository)
        addEventDependencyUseCase = AddEventDependencyUseCase(eventRepository)
        removeEventDependencyUseCase = RemoveEventDependencyUseCase(eventRepository)

        getEventRelationshipsUseCase = GetEventRelationshipsUseCase(eventRepository)
        addEventRelationshipUseCase = AddEventRelationshipUseCase(eventRepository)
        removeEventRelationshipUseCase = RemoveEventRelationshipUseCase(eventRepository)

        // Seed Category and EventType
        categoryRepository.saveCategory(Category("cat-1", "Personal", 0, null, false, 0))
        eventTypeRepository.saveEventType(
            EventType("type-1", "Meeting", "meeting", "cat-1", createdAt = timeProvider.nowInstant())
        )
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
        trashEventUseCase = trashEventUseCase,
        restoreTrashedEventUseCase = restoreTrashedEventUseCase,
        archiveEventUseCase = archiveEventUseCase,
        restoreArchivedEventUseCase = restoreArchivedEventUseCase,
        permanentlyDeleteEventUseCase = permanentlyDeleteEventUseCase,
        duplicateEventUseCase = duplicateEventUseCase,
        createEventTemplateUseCase = createEventTemplateUseCase,
        getEventDependenciesUseCase = getEventDependenciesUseCase,
        getEventDependentsUseCase = getEventDependentsUseCase,
        addEventDependencyUseCase = addEventDependencyUseCase,
        removeEventDependencyUseCase = removeEventDependencyUseCase,
        getEventRelationshipsUseCase = getEventRelationshipsUseCase,
        addEventRelationshipUseCase = addEventRelationshipUseCase,
        removeEventRelationshipUseCase = removeEventRelationshipUseCase,
        dispatcherProvider = dispatcherProvider
    )

    private fun createSampleEvent(
        id: String = "evt-1",
        title: String = "Design Review",
        status: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
        parentEventId: String? = null
    ): Event = Event(
        id = id,
        title = title,
        description = "Design Review Notes",
        categoryId = "cat-1",
        eventTypeId = "type-1",
        priority = Priority.HIGH,
        lifecycleStatus = status,
        parentEventId = parentEventId,
        temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 14, 0), utcZone),
        checklist = Checklist(
            title = "Review Tasks",
            items = listOf(
                ChecklistItem(id = "item-1", text = "Read Specs", isCompleted = false),
                ChecklistItem(id = "item-2", text = "Verify Mocks", isCompleted = true)
            )
        ),
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    @Test
    fun trashedEvent_restrictions_cannotCompleteOrFavorite_canRestoreOrDelete() = runTest(testDispatcher) {
        val event = createSampleEvent(id = "trashed-1", status = EventLifecycleStatus.TRASHED)
        eventRepository.saveEvent(event)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("trashed-1"))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(EventLifecycleStatus.TRASHED, state.event?.lifecycleStatus)

        // Attempting to favorite a trashed event does not change favorite status
        vm.onAction(EventDetailAction.ToggleFavorite)
        testScheduler.advanceUntilIdle()
        assertFalse(eventRepository.getEvent("trashed-1")?.isFavorite ?: true)

        // Restore from Trash works
        vm.onAction(EventDetailAction.RestoreFromTrash)
        testScheduler.advanceUntilIdle()

        val restored = eventRepository.getEvent("trashed-1")
        assertEquals(EventLifecycleStatus.ACTIVE, restored?.lifecycleStatus)
        assertNull(restored?.deletedAt)
    }

    @Test
    fun activeEvent_archiveAndRestoreFromArchive() = runTest(testDispatcher) {
        val event = createSampleEvent(id = "active-1")
        eventRepository.saveEvent(event)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("active-1"))
        testScheduler.advanceUntilIdle()

        // Archive
        vm.onAction(EventDetailAction.ArchiveEvent)
        testScheduler.advanceUntilIdle()

        val archived = eventRepository.getEvent("active-1")
        assertEquals(EventLifecycleStatus.ARCHIVED, archived?.lifecycleStatus)

        // Restore from archive
        vm.onAction(EventDetailAction.RestoreFromArchive)
        testScheduler.advanceUntilIdle()

        val restored = eventRepository.getEvent("active-1")
        assertEquals(EventLifecycleStatus.ACTIVE, restored?.lifecycleStatus)
    }

    @Test
    fun duplicateEvent_createsIndependentEventWithNewId() = runTest(testDispatcher) {
        val original = createSampleEvent(id = "orig-1", title = "Original Meeting")
        eventRepository.saveEvent(original)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("orig-1"))
        testScheduler.advanceUntilIdle()

        vm.onAction(EventDetailAction.DuplicateEvent)
        testScheduler.advanceUntilIdle()

        val duplicatedId = vm.uiState.value.duplicatedEventId
        assertNotNull(duplicatedId)
        assertNotEquals("orig-1", duplicatedId)

        val duplicated = eventRepository.getEvent(duplicatedId!!)
        assertNotNull(duplicated)
        assertEquals("Original Meeting (Copy)", duplicated?.title)
        assertEquals(original.categoryId, duplicated?.categoryId)
        assertEquals(original.eventTypeId, duplicated?.eventTypeId)
        assertEquals(original.priority, duplicated?.priority)

        // Original remains completely intact
        val origCheck = eventRepository.getEvent("orig-1")
        assertNotNull(origCheck)
        assertEquals("Original Meeting", origCheck?.title)
    }

    @Test
    fun createTemplateFromEvent_preservesCanonicalFields() = runTest(testDispatcher) {
        val original = createSampleEvent(id = "tmpl-source", title = "Sprint Retrospective")
        eventRepository.saveEvent(original)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("tmpl-source"))
        testScheduler.advanceUntilIdle()

        vm.onAction(EventDetailAction.CreateTemplateFromEvent("Sprint Retro Template"))
        testScheduler.advanceUntilIdle()

        val templates = templateRepository.templates.value.values
        val template = templates.find { it.name == "Sprint Retro Template" }
        assertNotNull(template)
        assertEquals("Sprint Retro Template", template?.name)
        assertEquals(original.categoryId, template?.categoryId)
        assertEquals(original.eventTypeId, template?.eventTypeId)
        assertEquals(original.priority, template?.priority)
        assertEquals(2, template?.checklist?.items?.size)
    }

    @Test
    fun checklistOperations_addToggleDelete_computesProgress() = runTest(testDispatcher) {
        val event = createSampleEvent(id = "chk-event")
        eventRepository.saveEvent(event)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("chk-event"))
        testScheduler.advanceUntilIdle()

        // Initial progress: 1 completed out of 2 total = 50%
        assertEquals(1, vm.uiState.value.completedChecklistCount)
        assertEquals(2, vm.uiState.value.totalChecklistCount)
        assertEquals(0.5f, vm.uiState.value.checklistProgress, 0.01f)

        // Toggle item-1 to completed
        vm.onAction(EventDetailAction.ToggleChecklistItem("item-1"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.completedChecklistCount)
        assertEquals(2, vm.uiState.value.totalChecklistCount)
        assertEquals(1.0f, vm.uiState.value.checklistProgress, 0.01f)

        // Add a third item
        vm.onAction(EventDetailAction.AddChecklistItem("New Item 3"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.completedChecklistCount)
        assertEquals(3, vm.uiState.value.totalChecklistCount)
        assertEquals(0.666f, vm.uiState.value.checklistProgress, 0.01f)

        // Delete item-2
        vm.onAction(EventDetailAction.DeleteChecklistItem("item-2"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.completedChecklistCount)
        assertEquals(2, vm.uiState.value.totalChecklistCount)
        assertEquals(0.5f, vm.uiState.value.checklistProgress, 0.01f)
    }

    @Test
    fun dependencyAndRelationshipOperations_addAndRemove() = runTest(testDispatcher) {
        val eventA = createSampleEvent(id = "event-A", title = "Task A")
        val eventB = createSampleEvent(id = "event-B", title = "Task B")
        eventRepository.saveEvent(eventA)
        eventRepository.saveEvent(eventB)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("event-A"))
        testScheduler.advanceUntilIdle()

        // Add Dependency: event-A is blocked by event-B
        vm.onAction(EventDetailAction.AddDependency(targetEventId = "event-B", type = DependencyType.BLOCKED_BY))
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.dependencies.size)
        assertEquals("event-B", vm.uiState.value.dependencies.first().targetEventId)

        // Remove Dependency
        vm.onAction(EventDetailAction.RemoveDependency(targetEventId = "event-B"))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.dependencies.isEmpty())

        // Add Relationship: event-A relates to event-B
        vm.onAction(EventDetailAction.AddRelationship(targetEventId = "event-B", type = RelationshipType.RELATED_TO))
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.relationships.size)

        // Remove Relationship
        vm.onAction(EventDetailAction.RemoveRelationship(targetEventId = "event-B"))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.relationships.isEmpty())
    }

    @Test
    fun subEvents_loadedAndDisplayedUnderParent() = runTest(testDispatcher) {
        val parent = createSampleEvent(id = "parent-1", title = "Parent Project")
        val child1 = createSampleEvent(id = "child-1", title = "Phase 1", parentEventId = "parent-1")
        val child2 = createSampleEvent(id = "child-2", title = "Phase 2", parentEventId = "parent-1")

        eventRepository.saveEvent(parent)
        eventRepository.saveEvent(child1)
        eventRepository.saveEvent(child2)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("parent-1"))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.subEvents.size)
        assertTrue(state.subEvents.any { it.title == "Phase 1" })
        assertTrue(state.subEvents.any { it.title == "Phase 2" })
    }
}
