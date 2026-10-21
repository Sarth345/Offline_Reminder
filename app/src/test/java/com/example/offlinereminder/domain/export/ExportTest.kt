package com.example.offlinereminder.domain.export

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.usecase.ExportEventsUseCase
import com.example.offlinereminder.domain.usecase.ExportFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExportTest {

    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var exceptionRepository: InMemoryOccurrenceExceptionRepository
    private lateinit var exportEventsUseCase: ExportEventsUseCase

    @Before
    fun setUp() {
        eventRepository = InMemoryEventRepository()
        exceptionRepository = InMemoryOccurrenceExceptionRepository()
        exportEventsUseCase = ExportEventsUseCase(
            eventRepository = eventRepository,
            timeProvider = TestTimeProvider(Instant.parse("2026-10-21T10:00:00Z")),
            occurrenceExceptionRepository = exceptionRepository
        )
    }

    private fun sampleEvent(): Event {
        val now = Instant.parse("2026-10-21T10:00:00Z")
        return Event(
            id = "evt-export-1",
            title = "Engineering Sync",
            description = "Sprint planning notes, with commas and \"quotes\"",
            eventTypeId = "type_meeting",
            categoryId = "cat_work",
            priority = Priority.HIGH,
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 14, 30)),
            location = LocationData(
                id = "loc-1",
                placeName = "Room 402",
                addressText = "100 Main St"
            ),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun jsonExport_validPayloadAndFields() = runTest {
        val event = sampleEvent()
        eventRepository.saveEvent(event)

        val result = exportEventsUseCase(ExportFormat.JSON)
        assertTrue(result is Result.Success)
        val jsonStr = (result as Result.Success).data

        assertTrue(jsonStr.contains("\"title\": \"Engineering Sync\""))
        assertTrue(jsonStr.contains("\"appVersion\": \"1.1.0\""))
        assertTrue(jsonStr.contains("\"Room 402, 100 Main St\""))
        assertTrue(jsonStr.contains("WEEKLY"))
    }

    @Test
    fun csvExport_rfc4180Escaping() = runTest {
        val event = sampleEvent()
        eventRepository.saveEvent(event)

        val result = exportEventsUseCase(ExportFormat.CSV)
        assertTrue(result is Result.Success)
        val csvStr = (result as Result.Success).data

        assertTrue(csvStr.startsWith("ID,Title,Description"))
        // Check escaping of double quotes
        assertTrue(csvStr.contains("\"\"quotes\"\""))
        assertTrue(csvStr.contains("Engineering Sync"))
    }

    @Test
    fun icsExport_rfc5545ValidStructureAndLineFolding() = runTest {
        val event = sampleEvent()
        eventRepository.saveEvent(event)

        val result = exportEventsUseCase(ExportFormat.ICS)
        assertTrue(result is Result.Success)
        val icsStr = (result as Result.Success).data

        assertTrue(icsStr.contains("BEGIN:VCALENDAR"))
        assertTrue(icsStr.contains("VERSION:2.0"))
        assertTrue(icsStr.contains("PRODID:-//Planerize//Planerize 1.1//EN"))
        assertTrue(icsStr.contains("BEGIN:VEVENT"))
        assertTrue(icsStr.contains("SUMMARY:Engineering Sync"))
        assertTrue(icsStr.contains("RRULE:FREQ=WEEKLY"))
        assertTrue(icsStr.contains("LOCATION:Room 402\\, 100 Main St"))
        assertTrue(icsStr.contains("END:VEVENT"))
        assertTrue(icsStr.contains("END:VCALENDAR"))

        // Verify line length <= 75 octets
        val lines = icsStr.split("\r\n")
        for (line in lines) {
            assertTrue("Line exceeds 75 chars: $line", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }

    @Test
    fun icsExport_unicodeHindiEmojiAccentedLineFolding() = runTest {
        val now = Instant.parse("2026-10-21T10:00:00Z")
        val unicodeEvent = Event(
            id = "evt-unicode",
            title = "दीपावली पूजन एवं महा उत्सव 🪔 - सभी परिवारजन सादर आमंत्रित हैं",
            description = "Conférence internationale à Montréal avec café crème & croissants délicieux. 🚀 Kickoff 2026 📅 Celebration 🎉",
            eventTypeId = "type_meeting",
            categoryId = "cat_work",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 11, 1, 18, 0)),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(unicodeEvent)

        val result = exportEventsUseCase(ExportFormat.ICS)
        assertTrue(result is Result.Success)
        val icsStr = (result as Result.Success).data

        assertTrue(icsStr.contains("दीपावली पूजन"))
        assertTrue(icsStr.contains("Montréal"))
        assertTrue(icsStr.contains("🚀"))

        // Strict RFC 5545 octet length validation: no folded line > 75 UTF-8 bytes
        val lines = icsStr.split("\r\n")
        for (line in lines) {
            assertTrue("Unicode line exceeds 75 octets: $line", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }

    @Test
    fun icsExport_recurringException_recurrenceIdPreserved() = runTest {
        val now = Instant.parse("2026-10-21T10:00:00Z")
        val masterEvent = Event(
            id = "evt-master-rec",
            title = "Weekly Standup",
            eventTypeId = "type_meeting",
            categoryId = "cat_work",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 22, 10, 0)),
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
            createdAt = now,
            updatedAt = now
        )
        eventRepository.saveEvent(masterEvent)

        // Reschedule occurrence on 2026-10-29 to 2026-10-30 at 16:00
        val exception = OccurrenceException(
            id = "exc-1",
            eventId = masterEvent.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 29, 10, 0),
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = LocalDateTime.of(2026, 10, 30, 16, 0)
        )
        exceptionRepository.saveException(exception)

        val result = exportEventsUseCase(ExportFormat.ICS)
        assertTrue(result is Result.Success)
        val icsStr = (result as Result.Success).data

        // Master event
        assertTrue(icsStr.contains("UID:evt-master-rec@planerize.local"))
        assertTrue(icsStr.contains("RRULE:FREQ=WEEKLY"))

        // Exception VEVENT: RECURRENCE-ID MUST identify the ORIGINAL occurrence date (20261029)
        assertTrue(icsStr.contains("RECURRENCE-ID;VALUE=DATE:20261029"))
        // and DTSTART has the moved datetime (20261030T160000)
        assertTrue(icsStr.contains("DTSTART:20261030T160000Z") || icsStr.contains("20261030T160000"))
    }
}
