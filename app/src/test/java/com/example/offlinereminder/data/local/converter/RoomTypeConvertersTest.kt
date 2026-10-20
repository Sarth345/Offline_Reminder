package com.example.offlinereminder.data.local.converter

import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RoomTypeConvertersTest {

    private lateinit var converters: RoomTypeConverters

    @Before
    fun setUp() {
        converters = RoomTypeConverters()
    }

    @Test
    fun instantConverter_roundTripsCorrectly() {
        val now = Instant.parse("2026-10-20T10:15:30.500Z")
        val epochMillis = converters.fromInstant(now)
        val restored = converters.toInstant(epochMillis)
        assertEquals(now.toEpochMilli(), restored?.toEpochMilli())
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }

    @Test
    fun localDateConverter_roundTripsCorrectly() {
        val date = LocalDate.of(2026, 10, 20)
        val str = converters.fromLocalDate(date)
        assertEquals("2026-10-20", str)
        assertEquals(date, converters.toLocalDate(str))
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun localTimeConverter_roundTripsCorrectly() {
        val time = LocalTime.of(14, 30, 45)
        val str = converters.fromLocalTime(time)
        assertEquals("14:30:45", str)
        assertEquals(time, converters.toLocalTime(str))
        assertNull(converters.fromLocalTime(null))
        assertNull(converters.toLocalTime(null))
    }

    @Test
    fun localDateTimeConverter_roundTripsCorrectly() {
        val ldt = LocalDateTime.of(2026, 10, 20, 14, 30, 45)
        val str = converters.fromLocalDateTime(ldt)
        assertEquals(ldt, converters.toLocalDateTime(str))
        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTime(null))
    }

    @Test
    fun enumsConverter_roundTripsCorrectly() {
        assertEquals(Priority.CRITICAL, converters.toPriority(converters.fromPriority(Priority.CRITICAL)))
        assertEquals(EventLifecycleStatus.TRASHED, converters.toLifecycleStatus(converters.fromLifecycleStatus(EventLifecycleStatus.TRASHED)))
        assertEquals(OccurrenceStatus.RESCHEDULED, converters.toOccurrenceStatus(converters.fromOccurrenceStatus(OccurrenceStatus.RESCHEDULED)))
        assertEquals(RecurrenceFrequency.MONTHLY, converters.toFrequency(converters.fromFrequency(RecurrenceFrequency.MONTHLY)))
        assertEquals(NotificationTriggerType.BEFORE_START, converters.toTriggerType(converters.fromTriggerType(NotificationTriggerType.BEFORE_START)))
        assertEquals(DependencyType.BLOCKS, converters.toDependencyType(converters.fromDependencyType(DependencyType.BLOCKS)))
        assertEquals(RelationshipType.FOLLOW_UP_TO, converters.toRelationshipType(converters.fromRelationshipType(RelationshipType.FOLLOW_UP_TO)))
    }

    @Test
    fun daysOfWeekSet_roundTripsCorrectly() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val str = converters.fromDaysOfWeek(days)
        val restored = converters.toDaysOfWeek(str)
        assertEquals(days, restored)

        assertNull(converters.fromDaysOfWeek(null))
        assertNull(converters.fromDaysOfWeek(emptySet()))
        assertTrue(converters.toDaysOfWeek(null)?.isEmpty() == true)
        assertTrue(converters.toDaysOfWeek("")?.isEmpty() == true)
    }

    @Test
    fun localDateSet_roundTripsCorrectly() {
        val dates = setOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        val str = converters.fromLocalDateSet(dates)
        val restored = converters.toLocalDateSet(str)
        assertEquals(dates, restored)

        assertNull(converters.fromLocalDateSet(null))
        assertNull(converters.fromLocalDateSet(emptySet()))
        assertTrue(converters.toLocalDateSet(null)?.isEmpty() == true)
    }

    @Test
    fun stringList_roundTripsCorrectly() {
        val aliases = listOf("bday", "anniversary", "celebration")
        val json = converters.fromStringList(aliases)
        val restored = converters.toStringList(json)
        assertEquals(aliases, restored)

        assertNull(converters.fromStringList(null))
        assertNull(converters.toStringList(null))
    }

    @Test
    fun stringMap_roundTripsCorrectly() {
        val metadata = mapOf("source" to "google_import", "meeting_url" to "https://meet.jit.si/test")
        val json = converters.fromStringMap(metadata)
        val restored = converters.toStringMap(json)
        assertEquals(metadata, restored)

        assertNull(converters.fromStringMap(null))
        assertNull(converters.toStringMap(null))
    }
}
