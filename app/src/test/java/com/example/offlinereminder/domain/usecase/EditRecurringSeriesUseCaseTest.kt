package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EditRecurringSeriesUseCaseTest {

    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var exceptionRepository: InMemoryOccurrenceExceptionRepository
    private lateinit var editUseCase: EditRecurringSeriesUseCase

    @Before
    fun setUp() {
        eventRepository = InMemoryEventRepository()
        exceptionRepository = InMemoryOccurrenceExceptionRepository()
        val timeProvider = TestTimeProvider(Instant.parse("2026-10-21T10:00:00Z"))

        val notifRepo = object : NotificationRepository {
            override fun getInstancesForEventFlow(eventId: String) = flowOf(emptyList<com.example.offlinereminder.domain.model.NotificationInstance>())
            override fun getAllScheduledFlow() = flowOf(emptyList<com.example.offlinereminder.domain.model.NotificationInstance>())
            override suspend fun getInstanceById(id: String) = null
            override suspend fun getInstancesForEvent(eventId: String) = emptyList<com.example.offlinereminder.domain.model.NotificationInstance>()
            override suspend fun getAllActiveScheduled() = emptyList<com.example.offlinereminder.domain.model.NotificationInstance>()
            override suspend fun saveInstance(instance: com.example.offlinereminder.domain.model.NotificationInstance) = Result.Success(Unit)
            override suspend fun saveInstances(instances: List<com.example.offlinereminder.domain.model.NotificationInstance>) = Result.Success(Unit)
            override suspend fun updateStatus(id: String, status: com.example.offlinereminder.domain.model.NotificationInstanceStatus) = Result.Success(Unit)
            override suspend fun snooze(id: String, snoozeUntil: Instant, newTriggerMillis: Long) = Result.Success(Unit)
            override suspend fun deleteById(id: String) = Result.Success(Unit)
            override suspend fun deleteByEventId(eventId: String) = Result.Success(Unit)
        }

        val scheduleUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notifRepo,
            alarmScheduler = TestAlarmScheduler(),
            timeProvider = timeProvider
        )
        val cancelUseCase = CancelNotificationsForEventUseCase(notifRepo, TestAlarmScheduler())

        editUseCase = EditRecurringSeriesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = exceptionRepository,
            scheduleNotificationsUseCase = scheduleUseCase,
            cancelNotificationsUseCase = cancelUseCase,
            timeProvider = timeProvider
        )
    }

    private fun createSeries(): Event {
        val now = Instant.parse("2026-10-21T10:00:00Z")
        return Event(
            id = "series-1",
            title = "Weekly Standup",
            description = "Recurring meeting",
            eventTypeId = "type_meeting",
            categoryId = "cat_work",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 1, 9, 0)),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun mode1_thisOccurrenceOnly_createsExceptionWithoutMutatingMaster() = runTest {
        val original = createSeries()
        eventRepository.saveEvent(original)

        val targetOccurrence = LocalDateTime.of(2026, 10, 8, 9, 0)
        val editedDraft = original.copy(title = "Weekly Standup - Special Guest")

        val result = editUseCase(
            originalEvent = original,
            occurrenceOriginalStart = targetOccurrence,
            occurrenceOriginalZone = ZoneId.of("UTC"),
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.THIS_OCCURRENCE
        )

        assertTrue(result is Result.Success)
        // Master event unmodified
        val storedMaster = eventRepository.getEvent(original.id)
        assertEquals("Weekly Standup", storedMaster?.title)

        // Exception created with title override
        val exceptions = exceptionRepository.getExceptionsForEvent(original.id).first()
        assertEquals(1, exceptions.size)
        assertEquals("Weekly Standup - Special Guest", exceptions.first().titleOverride)
    }

    @Test
    fun mode2_thisAndFollowing_splitsSeries() = runTest {
        val original = createSeries()
        eventRepository.saveEvent(original)

        val splitStart = LocalDateTime.of(2026, 10, 15, 10, 0)
        val editedDraft = original.copy(
            title = "New Bi-Weekly Standup",
            temporal = EventDateTime.DateTime(splitStart),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = 2)
        )

        val result = editUseCase(
            originalEvent = original,
            occurrenceOriginalStart = splitStart,
            occurrenceOriginalZone = ZoneId.of("UTC"),
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.THIS_AND_FOLLOWING
        )

        assertTrue(result is Result.Success)
        val newEvent = (result as Result.Success).data
        assertEquals("New Bi-Weekly Standup", newEvent.title)
        assertEquals("series-1", newEvent.parentEventId)

        // Verify original series was truncated before split date (10/14)
        val truncatedOriginal = eventRepository.getEvent(original.id)
        assertNotNull(truncatedOriginal)
        val endCondition = truncatedOriginal?.recurrenceRule?.endCondition
        assertTrue(endCondition is RecurrenceEnd.UntilDate)
        assertEquals(LocalDate.of(2026, 10, 14), (endCondition as RecurrenceEnd.UntilDate).date)
    }

    @Test
    fun mode3_entireSeries_updatesMasterDirectly() = runTest {
        val original = createSeries()
        eventRepository.saveEvent(original)

        val editedDraft = original.copy(title = "Engineering Standup")

        val result = editUseCase(
            originalEvent = original,
            occurrenceOriginalStart = LocalDateTime.of(2026, 10, 1, 9, 0),
            occurrenceOriginalZone = ZoneId.of("UTC"),
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.ENTIRE_SERIES
        )

        assertTrue(result is Result.Success)
        val storedMaster = eventRepository.getEvent(original.id)
        assertEquals("Engineering Standup", storedMaster?.title)
    }

    @Test
    fun mode2_thisAndFollowing_dailyAndMonthlyRecurrence() = runTest {
        val now = Instant.parse("2026-10-21T10:00:00Z")
        // Monthly recurring event on the 1st of every month
        val monthlyEvent = Event(
            id = "monthly-1",
            title = "Rent Payment",
            eventTypeId = "type_reminder",
            categoryId = "cat_general",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 10, 0)),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(monthlyEvent)

        val targetOccurrence = LocalDateTime.of(2026, 6, 1, 10, 0)
        val editedDraft = monthlyEvent.copy(
            title = "Increased Rent Payment",
            temporal = EventDateTime.DateTime(targetOccurrence),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY)
        )

        val result = editUseCase(
            originalEvent = monthlyEvent,
            occurrenceOriginalStart = targetOccurrence,
            occurrenceOriginalZone = ZoneId.of("UTC"),
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.THIS_AND_FOLLOWING
        )

        assertTrue(result is Result.Success)
        val newEvent = (result as Result.Success).data
        assertEquals("Increased Rent Payment", newEvent.title)
        assertEquals(LocalDate.of(2026, 6, 1), newEvent.temporal.startDate)

        val oldEvent = eventRepository.getEvent("monthly-1")
        assertNotNull(oldEvent)
        assertEquals(
            LocalDate.of(2026, 5, 31),
            (oldEvent?.recurrenceRule?.endCondition as RecurrenceEnd.UntilDate).date
        )
    }

    @Test
    fun mode2_thisAndFollowing_existingCompletedExceptionPreserved() = runTest {
        val original = createSeries()
        eventRepository.saveEvent(original)

        // Mark earlier occurrence on Oct 8 as completed
        val oct8Ldt = LocalDateTime.of(2026, 10, 8, 9, 0)
        val completedEx = OccurrenceException(
            id = OccurrenceException.createId(original.id, oct8Ldt, ZoneId.of("UTC")),
            eventId = original.id,
            originalStartDateTime = oct8Ldt,
            originalZoneId = ZoneId.of("UTC"),
            status = com.example.offlinereminder.domain.model.OccurrenceStatus.COMPLETED,
            completedAt = Instant.parse("2026-10-08T10:00:00Z")
        )
        exceptionRepository.saveException(completedEx)

        // Split series starting from Oct 22
        val splitStart = LocalDateTime.of(2026, 10, 22, 9, 0)
        val editedDraft = original.copy(title = "Split Standup", temporal = EventDateTime.DateTime(splitStart))

        val result = editUseCase(
            originalEvent = original,
            occurrenceOriginalStart = splitStart,
            occurrenceOriginalZone = ZoneId.of("UTC"),
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.THIS_AND_FOLLOWING
        )

        assertTrue(result is Result.Success)

        // The earlier completed exception on Oct 8 MUST still exist for original event
        val exceptions = exceptionRepository.getExceptionsForEvent(original.id).first()
        assertTrue(exceptions.any { it.status == com.example.offlinereminder.domain.model.OccurrenceStatus.COMPLETED && it.originalDate == LocalDate.of(2026, 10, 8) })
    }

    @Test
    fun mode2_thisAndFollowing_timezoneAndDstSafety() = runTest {
        val nyZone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-10-21T10:00:00Z")
        val dstEvent = Event(
            id = "ny-dst-event",
            title = "Morning Huddle",
            eventTypeId = "type_meeting",
            categoryId = "cat_work",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 15, 9, 0), zoneId = nyZone),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(dstEvent)

        // Split event right around US DST change (Nov 1, 2026)
        val splitStart = LocalDateTime.of(2026, 11, 2, 9, 0)
        val editedDraft = dstEvent.copy(
            title = "Morning Huddle (Post-DST)",
            temporal = EventDateTime.DateTime(splitStart, zoneId = nyZone)
        )

        val result = editUseCase(
            originalEvent = dstEvent,
            occurrenceOriginalStart = splitStart,
            occurrenceOriginalZone = nyZone,
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.THIS_AND_FOLLOWING
        )

        assertTrue(result is Result.Success)
        val newEvent = (result as Result.Success).data
        assertEquals(nyZone, (newEvent.temporal as EventDateTime.DateTime).zoneId)

        val oldEvent = eventRepository.getEvent("ny-dst-event")
        assertEquals(
            LocalDate.of(2026, 11, 1),
            (oldEvent?.recurrenceRule?.endCondition as RecurrenceEnd.UntilDate).date
        )
    }
}
