package com.example.offlinereminder.domain.ics

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.usecase.ConfirmIcsImportUseCase
import com.example.offlinereminder.domain.usecase.PreviewIcsImportUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class IcsImportTest {

    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var categoryRepository: InMemoryCategoryRepository
    private lateinit var eventTypeRepository: InMemoryEventTypeRepository
    private lateinit var previewUseCase: PreviewIcsImportUseCase
    private lateinit var confirmUseCase: ConfirmIcsImportUseCase

    @Before
    fun setUp() = runTest {
        eventRepository = InMemoryEventRepository()
        categoryRepository = InMemoryCategoryRepository()
        eventTypeRepository = InMemoryEventTypeRepository()

        categoryRepository.saveCategory(Category(id = "cat_general", name = "General", color = 0xFF4285F4.toInt()))
        eventTypeRepository.saveEventType(
            EventType(
                id = "type_reminder",
                name = "Reminder",
                key = "reminder",
                categoryId = "cat_general",
                createdAt = Instant.now()
            )
        )

        val timeProvider = TestTimeProvider(Instant.parse("2026-10-21T10:00:00Z"))
        previewUseCase = PreviewIcsImportUseCase(categoryRepository, eventTypeRepository, timeProvider)

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

        val scheduleNotifUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notifRepo,
            alarmScheduler = TestAlarmScheduler(),
            timeProvider = timeProvider
        )

        confirmUseCase = ConfirmIcsImportUseCase(eventRepository, scheduleNotifUseCase)
    }

    @Test
    fun parseIcs_singleEvent_success() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Calendar//EN
            BEGIN:VEVENT
            UID:test-1234
            SUMMARY:Doctor Appointment
            DESCRIPTION:Checkup at medical center\, bring ID
            LOCATION:Clinic Room 3
            DTSTART:20261025T090000Z
            DTEND:20261025T100000Z
            PRIORITY:1
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val preview = IcsParser.parse(ics, "cat_general", "type_reminder")
        assertEquals(1, preview.totalCount)
        assertEquals(0, preview.recurringCount)
        val event = preview.candidateEvents.first()
        assertEquals("Doctor Appointment", event.title)
        assertEquals("Checkup at medical center, bring ID", event.description)
        assertEquals("Clinic Room 3", event.location?.placeName)
        assertEquals(Priority.CRITICAL, event.priority)
    }

    @Test
    fun parseIcs_recurringEventWithExDate() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:rec-5678
            SUMMARY:Weekly Architecture Review
            DTSTART;TZID=America/New_York:20261102T140000
            DTEND;TZID=America/New_York:20261102T150000
            RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=10
            EXDATE;TZID=America/New_York:20261116T140000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val preview = IcsParser.parse(ics, "cat_general", "type_reminder")
        assertEquals(1, preview.totalCount)
        assertEquals(1, preview.recurringCount)
        val event = preview.candidateEvents.first()
        assertNotNull(event.recurrenceRule)
        assertEquals(RecurrenceFrequency.WEEKLY, event.recurrenceRule?.frequency)
        assertEquals(2, event.recurrenceRule?.interval)
        assertEquals(1, event.recurrenceRule?.excludedDates?.size)
        assertEquals(LocalDate.of(2026, 11, 16), event.recurrenceRule?.excludedDates?.first())
    }

    @Test
    fun previewAndConfirmImportFlow() = runTest {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Sprint Demo
            DTSTART:20261028T160000
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Quarterly Review
            DTSTART:20261115
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val previewResult = previewUseCase(ics)
        assertTrue(previewResult is Result.Success)
        val preview = (previewResult as Result.Success).data
        assertEquals(2, preview.totalCount)

        val confirmResult = confirmUseCase(preview.candidateEvents)
        assertTrue(confirmResult is Result.Success)
        assertEquals(2, (confirmResult as Result.Success).data)
    }

    @Test
    fun parseIcs_unicodeAndEmoji_preservesText() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:unicode-event-1
            SUMMARY:दीपावली पूजन 🪔 - Diwali Celebration
            DESCRIPTION:Conférence à Montréal avec café crème & 🚀 rocket launch
            DTSTART:20261101T180000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val preview = IcsParser.parse(ics, "cat_general", "type_reminder")
        assertEquals(1, preview.totalCount)
        val event = preview.candidateEvents.first()
        assertEquals("दीपावली पूजन 🪔 - Diwali Celebration", event.title)
        assertEquals("Conférence à Montréal avec café crème & 🚀 rocket launch", event.description)
    }

    @Test
    fun parseIcs_recurrenceId_preservesOriginalOccurrenceIdentity() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:series-123
            SUMMARY:Bi-weekly All Hands
            DTSTART:20261020T100000Z
            RRULE:FREQ=WEEKLY;INTERVAL=2
            END:VEVENT
            BEGIN:VEVENT
            UID:series-123
            RECURRENCE-ID;VALUE=DATE:20261103
            DTSTART:20261104T150000Z
            DTEND:20261104T160000Z
            SUMMARY:Rescheduled All Hands
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val preview = IcsParser.parse(ics, "cat_general", "type_reminder")
        assertEquals(2, preview.totalCount)
        assertEquals(1, preview.candidateEvents.size)
        assertEquals(1, preview.candidateExceptions.size)

        val exception = preview.candidateExceptions.first()
        // RECURRENCE-ID represents the ORIGINAL occurrence identity (2026-11-03)
        // NOT the new moved DTSTART date (2026-11-04)!
        assertEquals(LocalDate.of(2026, 11, 3), exception.recurrenceOriginalDate)
        assertEquals("Rescheduled All Hands", exception.title)
    }
}
