package com.example.offlinereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class OccurrenceExceptionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: OccurrenceExceptionRepositoryImpl
    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = OccurrenceExceptionRepositoryImpl(database.occurrenceExceptionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedRecurringEvent(eventId: String = "evt-recur-sync") {
        database.categoryDao().insert(
            CategoryEntity(id = "cat-work", name = "Work", sortOrder = 0)
        )
        database.eventTypeDao().insert(
            EventTypeEntity(id = "type-meeting", name = "Meeting", key = "work_meeting", categoryId = "cat-work", createdAt = testInstant)
        )
        database.eventDao().insert(
            EventEntity(
                id = eventId,
                title = "Weekly Architecture Review",
                eventTypeId = "type-meeting",
                categoryId = "cat-work",
                temporalType = "DATE_TIME",
                startDate = LocalDate.of(2026, 10, 1),
                startTime = LocalTime.of(15, 0),
                zoneId = "Asia/Kolkata",
                hasRecurrence = true,
                recurrenceFrequency = RecurrenceFrequency.WEEKLY,
                createdAt = testInstant,
                updatedAt = testInstant
            )
        )
    }

    @Test
    fun occurrenceException_saveAndRetrieveByOccurrenceId_preservesTimezonesAndRescheduling() = runTest {
        seedRecurringEvent("evt-weekly-arch")

        val originalLdt = LocalDateTime.of(2026, 10, 15, 15, 0)
        val originalZone = ZoneId.of("Asia/Kolkata")
        val rescheduledLdt = LocalDateTime.of(2026, 10, 16, 11, 0)
        val rescheduledEndLdt = LocalDateTime.of(2026, 10, 16, 12, 0)
        val rescheduledZone = ZoneId.of("Asia/Kolkata")
        val actualExecution = LocalDateTime.of(2026, 10, 16, 11, 2)
        val completedInstant = Instant.parse("2026-10-16T05:32:00Z")

        val exception = OccurrenceException(
            id = "exc-arch-1",
            eventId = "evt-weekly-arch",
            originalStartDateTime = originalLdt,
            originalZoneId = originalZone,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledLdt,
            rescheduledEndDateTime = rescheduledEndLdt,
            rescheduledZoneId = rescheduledZone,
            actualExecutionDateTime = actualExecution,
            completedAt = completedInstant,
            titleOverride = "Special Deep Dive Review",
            notesOverride = "Shifted by 1 day for team availability"
        )

        val saveResult = repository.saveException(exception)
        assertTrue(saveResult is Result.Success)

        // Retrieve using canonical occurrence identity
        val retrieved = repository.getExceptionByOccurrenceId(exception.occurrenceId)
        assertNotNull(retrieved)
        assertEquals("exc-arch-1", retrieved?.id)
        assertEquals(exception.occurrenceId, retrieved?.occurrenceId)
        assertEquals(originalLdt, retrieved?.originalStartDateTime)
        assertEquals(originalZone, retrieved?.originalZoneId)
        assertEquals(OccurrenceStatus.RESCHEDULED, retrieved?.status)
        assertEquals(rescheduledLdt, retrieved?.rescheduledStartDateTime)
        assertEquals(rescheduledEndLdt, retrieved?.rescheduledEndDateTime)
        assertEquals(rescheduledZone, retrieved?.rescheduledZoneId)
        assertEquals(actualExecution, retrieved?.actualExecutionDateTime)
        assertEquals(completedInstant, retrieved?.completedAt)
        assertEquals("Special Deep Dive Review", retrieved?.titleOverride)
        assertEquals("Shifted by 1 day for team availability", retrieved?.notesOverride)

        // Flow observation
        val observed = repository.getExceptionByOccurrenceIdFlow(exception.occurrenceId).first()
        assertEquals("exc-arch-1", observed?.id)

        // Query by event Flow
        val eventExceptions = repository.getExceptionsForEvent("evt-weekly-arch").first()
        assertEquals(1, eventExceptions.size)
        assertEquals("exc-arch-1", eventExceptions[0].id)
    }

    @Test
    fun getExceptionsInRange_findsRescheduledAndOriginalOccurrences() = runTest {
        seedRecurringEvent("evt-weekly-arch")

        val originalLdt = LocalDateTime.of(2026, 10, 22, 15, 0)
        val rescheduledLdt = LocalDateTime.of(2026, 10, 25, 10, 0)

        val exception = OccurrenceException(
            id = "exc-range-test",
            eventId = "evt-weekly-arch",
            originalStartDateTime = originalLdt,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledLdt
        )
        repository.saveException(exception)

        // Query range covering rescheduled date
        val results = repository.getExceptionsInRange(
            startLdt = LocalDateTime.of(2026, 10, 24, 0, 0),
            endLdt = LocalDateTime.of(2026, 10, 26, 0, 0)
        )
        assertEquals(1, results.size)
        assertEquals("exc-range-test", results[0].id)

        // Query range covering original date
        val originalResults = repository.getExceptionsInRange(
            startLdt = LocalDateTime.of(2026, 10, 21, 0, 0),
            endLdt = LocalDateTime.of(2026, 10, 23, 0, 0)
        )
        assertEquals(1, originalResults.size)
        assertEquals("exc-range-test", originalResults[0].id)
    }

    @Test
    fun deleteException_removesSuccessfully() = runTest {
        seedRecurringEvent("evt-weekly-arch")

        val exception = OccurrenceException(
            id = "exc-to-delete",
            eventId = "evt-weekly-arch",
            originalStartDateTime = LocalDateTime.of(2026, 10, 29, 15, 0),
            status = OccurrenceStatus.SKIPPED
        )
        repository.saveException(exception)

        val deleteResult = repository.deleteByOccurrenceId(exception.occurrenceId)
        assertTrue(deleteResult is Result.Success)
        assertNull(repository.getExceptionByOccurrenceId(exception.occurrenceId))
    }
}
