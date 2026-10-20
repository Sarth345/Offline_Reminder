package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class GroupUseCasesTest {

    private val eventRepository = InMemoryEventRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository(eventRepository)

    private lateinit var saveGroupUseCase: SaveGroupUseCase
    private lateinit var deleteGroupUseCase: DeleteGroupUseCase
    private lateinit var getGroupByIdUseCase: GetGroupByIdUseCase
    private lateinit var getActiveEventsForGroupUseCase: GetActiveEventsForGroupUseCase

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")
    private val utcZone = ZoneId.of("UTC")

    @Before
    fun setUp() {
        eventRepository.clear()
        saveGroupUseCase = SaveGroupUseCase(eventGroupRepository)
        deleteGroupUseCase = DeleteGroupUseCase(eventGroupRepository)
        getGroupByIdUseCase = GetGroupByIdUseCase(eventGroupRepository)
        getActiveEventsForGroupUseCase = GetActiveEventsForGroupUseCase(eventGroupRepository)
    }

    @Test
    fun saveGroup_validGroup_persistsSuccessfully() = runTest {
        val group = EventGroup(
            id = "grp-1",
            name = "Work Projects",
            description = "All tasks related to work",
            color = 0xFF1E88E5.toInt(),
            icon = "work",
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val result = saveGroupUseCase(group)
        assertTrue(result is Result.Success)

        val retrieved = getGroupByIdUseCase("grp-1").first()
        assertNotNull(retrieved)
        assertEquals("Work Projects", retrieved?.name)
        assertEquals("All tasks related to work", retrieved?.description)
    }

    @Test
    fun saveGroup_blankName_failsValidation() = runTest {
        val group = EventGroup(
            id = "grp-invalid",
            name = "   ",
            description = "No name",
            createdAt = testInstant,
            updatedAt = testInstant
        )

        val result = saveGroupUseCase(group)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun deleteGroup_removesGroup() = runTest {
        val group = EventGroup(
            id = "grp-del",
            name = "To Delete",
            createdAt = testInstant,
            updatedAt = testInstant
        )
        saveGroupUseCase(group)

        val deleteResult = deleteGroupUseCase("grp-del")
        assertTrue(deleteResult is Result.Success)

        val retrieved = getGroupByIdUseCase("grp-del").first()
        assertNull(retrieved)
    }

    @Test
    fun getActiveEventsForGroup_returnsOnlyActiveEventsInGroup() = runTest {
        val activeEvent1 = Event(
            id = "evt-1",
            title = "Active Event 1",
            description = "Desc",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 10, 0), utcZone),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        val activeEvent2 = Event(
            id = "evt-2",
            title = "Active Event 2",
            description = "Desc",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 10, 0), utcZone),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        val trashedEvent = Event(
            id = "evt-3",
            title = "Trashed Event",
            description = "Desc",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0), utcZone),
            createdAt = testInstant,
            updatedAt = testInstant
        )

        eventRepository.saveEvent(activeEvent1)
        eventRepository.saveEvent(activeEvent2)
        eventRepository.saveEvent(trashedEvent)

        eventGroupRepository.addEventToGroup("evt-1", "grp-active")
        eventGroupRepository.addEventToGroup("evt-2", "grp-other") // in another group
        eventGroupRepository.addEventToGroup("evt-3", "grp-active") // in grp-active but trashed

        val eventsInGroup = getActiveEventsForGroupUseCase("grp-active").first()
        assertEquals(1, eventsInGroup.size)
        assertEquals("evt-1", eventsInGroup[0].id)
    }
}
