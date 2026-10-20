package com.example.offlinereminder.presentation.groups

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.usecase.DeleteGroupUseCase
import com.example.offlinereminder.domain.usecase.GetActiveEventsForGroupUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetGroupByIdUseCase
import com.example.offlinereminder.domain.usecase.SaveGroupUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository(eventRepository)

    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var saveGroupUseCase: SaveGroupUseCase
    private lateinit var deleteGroupUseCase: DeleteGroupUseCase
    private lateinit var getGroupByIdUseCase: GetGroupByIdUseCase
    private lateinit var getActiveEventsForGroupUseCase: GetActiveEventsForGroupUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        saveGroupUseCase = SaveGroupUseCase(eventGroupRepository)
        deleteGroupUseCase = DeleteGroupUseCase(eventGroupRepository)
        getGroupByIdUseCase = GetGroupByIdUseCase(eventGroupRepository)
        getActiveEventsForGroupUseCase = GetActiveEventsForGroupUseCase(eventGroupRepository)
    }

    private fun createGroupsViewModel(): GroupsViewModel = GroupsViewModel(
        getEventGroupsUseCase = getEventGroupsUseCase,
        saveGroupUseCase = saveGroupUseCase,
        deleteGroupUseCase = deleteGroupUseCase,
        eventGroupRepository = eventGroupRepository,
        timeProvider = timeProvider,
        dispatcherProvider = dispatcherProvider
    )

    private fun createGroupDetailViewModel(): GroupDetailViewModel = GroupDetailViewModel(
        getGroupByIdUseCase = getGroupByIdUseCase,
        getActiveEventsForGroupUseCase = getActiveEventsForGroupUseCase,
        saveGroupUseCase = saveGroupUseCase,
        deleteGroupUseCase = deleteGroupUseCase,
        eventGroupRepository = eventGroupRepository,
        timeProvider = timeProvider,
        dispatcherProvider = dispatcherProvider
    )

    @Test
    fun loadGroups_populatesGroupsAndEventCounts() = runTest(testDispatcher) {
        val group1 = EventGroup(
            id = "grp-1",
            name = "Work",
            description = "Work tasks",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val group2 = EventGroup(
            id = "grp-2",
            name = "Personal",
            description = "Personal errands",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventGroupRepository.saveGroup(group1)
        eventGroupRepository.saveGroup(group2)

        val event = Event(
            id = "evt-1",
            title = "Design Review",
            description = "Review UI",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.HIGH,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 14, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        eventGroupRepository.addEventToGroup("evt-1", "grp-1")

        val viewModel = createGroupsViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.groups.size)
        assertEquals(1, state.eventCounts["grp-1"])
        assertEquals(0, state.eventCounts["grp-2"] ?: 0)
    }

    @Test
    fun search_filtersGroupsByNameAndDescription() = runTest(testDispatcher) {
        val g1 = EventGroup(id = "g1", name = "Vacation", description = "Hawaii trip", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        val g2 = EventGroup(id = "g2", name = "Finance", description = "Budget and taxes", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        eventGroupRepository.saveGroup(g1)
        eventGroupRepository.saveGroup(g2)

        val viewModel = createGroupsViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(GroupsAction.Search("trip"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.filteredGroups.size)
        assertEquals("Vacation", viewModel.uiState.value.filteredGroups[0].name)

        viewModel.onAction(GroupsAction.Search(""))
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredGroups.size)
    }

    @Test
    fun createGroup_persistsNewGroup() = runTest(testDispatcher) {
        val viewModel = createGroupsViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(
            GroupsAction.CreateGroup(
                name = "Fitness",
                description = "Gym and running",
                color = 0xFF43A047.toInt()
            )
        )
        testScheduler.advanceUntilIdle()

        val groups = eventGroupRepository.groups.value.values
        val created = groups.find { it.name == "Fitness" }
        assertNotNull(created)
        assertEquals("Gym and running", created?.description)
        assertEquals(0xFF43A047.toInt(), created?.color)
    }

    @Test
    fun updateGroup_modifiesExistingGroup() = runTest(testDispatcher) {
        val group = EventGroup(
            id = "grp-upd",
            name = "Old Name",
            description = "Old Desc",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventGroupRepository.saveGroup(group)

        val viewModel = createGroupsViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(GroupsAction.UpdateGroup(group.copy(name = "New Name", description = "New Desc")))
        testScheduler.advanceUntilIdle()

        val updated = eventGroupRepository.getGroup("grp-upd")
        assertEquals("New Name", updated?.name)
        assertEquals("New Desc", updated?.description)
    }

    @Test
    fun deleteGroup_removesGroup() = runTest(testDispatcher) {
        val group = EventGroup(
            id = "grp-del",
            name = "Temporary",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventGroupRepository.saveGroup(group)

        val viewModel = createGroupsViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(GroupsAction.DeleteGroup("grp-del"))
        testScheduler.advanceUntilIdle()

        assertNull(eventGroupRepository.getGroup("grp-del"))
    }

    @Test
    fun groupDetailViewModel_loadGroupAndEvents() = runTest(testDispatcher) {
        val group = EventGroup(
            id = "grp-detail",
            name = "House Project",
            description = "Home repairs",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventGroupRepository.saveGroup(group)

        val event = Event(
            id = "evt-repair",
            title = "Fix roof",
            description = "Call contractor",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.HIGH,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        eventGroupRepository.addEventToGroup("evt-repair", "grp-detail")

        val detailViewModel = createGroupDetailViewModel()
        detailViewModel.onAction(GroupDetailAction.LoadGroup("grp-detail"))
        testScheduler.advanceUntilIdle()

        val state = detailViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("House Project", state.group?.name)
        assertEquals(1, state.events.size)
        assertEquals("evt-repair", state.events[0].id)
    }

    @Test
    fun groupDetailViewModel_removeEventFromGroup() = runTest(testDispatcher) {
        val group = EventGroup(
            id = "grp-detail2",
            name = "Project",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventGroupRepository.saveGroup(group)

        val event = Event(
            id = "evt-remove",
            title = "Remove Me",
            description = "Desc",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        eventGroupRepository.addEventToGroup("evt-remove", "grp-detail2")

        val detailViewModel = createGroupDetailViewModel()
        detailViewModel.onAction(GroupDetailAction.LoadGroup("grp-detail2"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, detailViewModel.uiState.value.events.size)

        // Remove from group
        detailViewModel.onAction(GroupDetailAction.RemoveEventFromGroup("evt-remove"))
        testScheduler.advanceUntilIdle()

        assertEquals(0, detailViewModel.uiState.value.events.size)
        // Event itself is NOT deleted
        assertNotNull(eventRepository.getEvent("evt-remove"))
    }

    @Test
    fun groupDetailViewModel_deleteGroup_setsIsDeleted() = runTest(testDispatcher) {
        val group = EventGroup(
            id = "grp-to-delete",
            name = "To Delete",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventGroupRepository.saveGroup(group)

        val detailViewModel = createGroupDetailViewModel()
        detailViewModel.onAction(GroupDetailAction.LoadGroup("grp-to-delete"))
        testScheduler.advanceUntilIdle()

        detailViewModel.onAction(GroupDetailAction.DeleteGroup)
        testScheduler.advanceUntilIdle()

        assertTrue(detailViewModel.uiState.value.isDeleted)
        assertNull(eventGroupRepository.getGroup("grp-to-delete"))
    }
}
