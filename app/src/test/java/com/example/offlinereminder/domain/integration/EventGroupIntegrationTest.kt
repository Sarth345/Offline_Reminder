package com.example.offlinereminder.domain.integration

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
import com.example.offlinereminder.domain.usecase.SaveGroupUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EventGroupIntegrationTest {

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository(eventRepository)

    private lateinit var saveGroupUseCase: SaveGroupUseCase
    private lateinit var deleteGroupUseCase: DeleteGroupUseCase
    private lateinit var getActiveEventsForGroupUseCase: GetActiveEventsForGroupUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        saveGroupUseCase = SaveGroupUseCase(eventGroupRepository)
        deleteGroupUseCase = DeleteGroupUseCase(eventGroupRepository)
        getActiveEventsForGroupUseCase = GetActiveEventsForGroupUseCase(eventGroupRepository)
    }

    @Test
    fun event_canBelongToMultipleGroups_andPreserveMembershipsAcrossUpdates() = runTest {
        val groupA = EventGroup(id = "grp-A", name = "Work", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        val groupB = EventGroup(id = "grp-B", name = "Urgent Tasks", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        saveGroupUseCase(groupA)
        saveGroupUseCase(groupB)

        val event = Event(
            id = "evt-multi",
            title = "Quarterly Tax Filing",
            description = "Prepare documents",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.CRITICAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 9, 0), utcZone),
            groupIds = setOf("grp-A", "grp-B"),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        eventGroupRepository.addEventToGroup(event.id, "grp-A")
        eventGroupRepository.addEventToGroup(event.id, "grp-B")

        // Verify event appears in both groups
        val eventsInA = getActiveEventsForGroupUseCase("grp-A").first()
        val eventsInB = getActiveEventsForGroupUseCase("grp-B").first()
        assertEquals(1, eventsInA.size)
        assertEquals(1, eventsInB.size)
        assertEquals("evt-multi", eventsInA[0].id)
        assertEquals("evt-multi", eventsInB[0].id)

        // Update event details (title, priority)
        val updatedEvent = event.copy(title = "Updated Quarterly Tax Filing", priority = Priority.HIGH)
        eventRepository.saveEvent(updatedEvent)

        // Verify group memberships are intact
        val eventsInAAfterUpdate = getActiveEventsForGroupUseCase("grp-A").first()
        val eventsInBAfterUpdate = getActiveEventsForGroupUseCase("grp-B").first()
        assertEquals("Updated Quarterly Tax Filing", eventsInAAfterUpdate[0].title)
        assertEquals("Updated Quarterly Tax Filing", eventsInBAfterUpdate[0].title)
    }

    @Test
    fun removingOneMembership_preservesOtherGroupMemberships() = runTest {
        val groupA = EventGroup(id = "grp-1", name = "Group 1", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        val groupB = EventGroup(id = "grp-2", name = "Group 2", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        saveGroupUseCase(groupA)
        saveGroupUseCase(groupB)

        val event = Event(
            id = "evt-remove-test",
            title = "Shared Reminder",
            description = "Desc",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 9, 0), utcZone),
            groupIds = setOf("grp-1", "grp-2"),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        eventGroupRepository.addEventToGroup("evt-remove-test", "grp-1")
        eventGroupRepository.addEventToGroup("evt-remove-test", "grp-2")

        // Remove from group 1
        eventGroupRepository.removeEventFromGroup("evt-remove-test", "grp-1")

        val eventsIn1 = getActiveEventsForGroupUseCase("grp-1").first()
        val eventsIn2 = getActiveEventsForGroupUseCase("grp-2").first()

        assertTrue(eventsIn1.isEmpty())
        assertEquals(1, eventsIn2.size)
        assertEquals("evt-remove-test", eventsIn2[0].id)
    }

    @Test
    fun deletingGroup_doesNotDeleteEvents() = runTest {
        val group = EventGroup(id = "grp-to-delete", name = "Group to Delete", createdAt = timeProvider.nowInstant(), updatedAt = timeProvider.nowInstant())
        saveGroupUseCase(group)

        val event = Event(
            id = "evt-persist",
            title = "Persistent Reminder",
            description = "Should not be deleted",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = Priority.NORMAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 25, 9, 0), utcZone),
            groupIds = setOf("grp-to-delete"),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)
        eventGroupRepository.addEventToGroup("evt-persist", "grp-to-delete")

        // Delete group
        deleteGroupUseCase("grp-to-delete")

        // Group is gone
        val eventsInGroup = getActiveEventsForGroupUseCase("grp-to-delete").first()
        assertTrue(eventsInGroup.isEmpty())

        // Event still exists in event repository
        val storedEvent = eventRepository.getEvent("evt-persist")
        assertNotNull(storedEvent)
        assertEquals("Persistent Reminder", storedEvent?.title)
    }
}
