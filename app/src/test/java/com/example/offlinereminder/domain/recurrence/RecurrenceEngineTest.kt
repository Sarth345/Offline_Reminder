package com.example.offlinereminder.domain.recurrence

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.usecase.ProjectOccurrencesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceEngineTest {

    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T10:00:00Z"),
        currentZoneId = ZoneId.of("UTC")
    )
    private val engine = DefaultRecurrenceEngine(timeProvider)
    private val projectOccurrencesUseCase = ProjectOccurrencesUseCase(engine)

    private fun createEvent(
        id: String = "event-test-1",
        temporal: EventDateTime = EventDateTime.DateTime(
            dateTime = LocalDateTime.of(2026, 1, 1, 9, 0),
            zoneId = ZoneId.of("UTC")
        ),
        recurrenceRule: RecurrenceRule? = null
    ): Event = Event(
        id = id,
        title = "Test Reminder",
        eventTypeId = "type-default",
        categoryId = "cat-general",
        priority = Priority.NORMAL,
        temporal = temporal,
        recurrenceRule = recurrenceRule,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    // =========================================================================
    // SECTION A — Non-recurring Events
    // =========================================================================

    @Test
    fun `A1 non-recurring date-only projects within window`() {
        val event = createEvent(
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 5, 10))
        )
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 5, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(1, occurrences.size)
        assertEquals(event.id, occurrences[0].eventId)
        assertEquals(LocalDate.of(2026, 5, 10), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalTime.MIDNIGHT, occurrences[0].originalStartDateTime.toLocalTime())
    }

    @Test
    fun `A2 non-recurring date-time projects within window`() {
        val ldt = LocalDateTime.of(2026, 6, 15, 14, 30)
        val event = createEvent(
            temporal = EventDateTime.DateTime(ldt, ZoneId.of("UTC"))
        )
        val from = LocalDateTime.of(2026, 6, 15, 0, 0)
        val to = LocalDateTime.of(2026, 6, 15, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(1, occurrences.size)
        assertEquals(ldt, occurrences[0].originalStartDateTime)
        assertEquals(ZoneId.of("UTC"), occurrences[0].zoneId)
    }

    @Test
    fun `A3 non-recurring date range projects when intersecting window`() {
        val event = createEvent(
            temporal = EventDateTime.DateRange(
                startDate = LocalDate.of(2026, 7, 10),
                endDate = LocalDate.of(2026, 7, 15)
            )
        )
        // Query overlapping middle of range
        val from = LocalDateTime.of(2026, 7, 12, 0, 0)
        val to = LocalDateTime.of(2026, 7, 13, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2026, 7, 10).atStartOfDay(), occurrences[0].originalStartDateTime)
        assertEquals(LocalDate.of(2026, 7, 15).atTime(23, 59, 59), occurrences[0].defaultEndDateTime)
    }

    @Test
    fun `A4 non-recurring date range with time projects when intersecting window`() {
        val start = LocalDateTime.of(2026, 8, 20, 10, 0)
        val end = LocalDateTime.of(2026, 8, 20, 12, 0)
        val event = createEvent(
            temporal = EventDateTime.DateTimeRange(start, end, ZoneId.of("UTC"))
        )
        val from = LocalDateTime.of(2026, 8, 20, 11, 0)
        val to = LocalDateTime.of(2026, 8, 20, 13, 0)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(1, occurrences.size)
        assertEquals(start, occurrences[0].originalStartDateTime)
        assertEquals(end, occurrences[0].defaultEndDateTime)
    }

    @Test
    fun `A5 non-recurring outside projection range returns empty`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 10, 0))
        )
        val from = LocalDateTime.of(2026, 2, 1, 0, 0)
        val to = LocalDateTime.of(2026, 2, 28, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertTrue(occurrences.isEmpty())
    }

    // =========================================================================
    // SECTION B — Daily Recurrence
    // =========================================================================

    @Test
    fun `B1 daily every day generates all consecutive days within window`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 8, 0)),
            recurrenceRule = RecurrenceRule.daily(interval = 1)
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 7, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(7, occurrences.size)
        for (i in 0 until 7) {
            assertEquals(
                LocalDateTime.of(2026, 1, 1 + i, 8, 0),
                occurrences[i].originalStartDateTime
            )
        }
    }

    @Test
    fun `B2 daily every N days (interval 3)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 3, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily(interval = 3)
        )
        val from = LocalDateTime.of(2026, 3, 1, 0, 0)
        val to = LocalDateTime.of(2026, 3, 10, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // March 1, 4, 7, 10 -> 4 occurrences
        assertEquals(4, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), occurrences[0].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 3, 4, 9, 0), occurrences[1].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 3, 7, 9, 0), occurrences[2].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 3, 10, 9, 0), occurrences[3].originalStartDateTime)
    }

    @Test
    fun `B3 daily recurrence bounded range query in future`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 10, 0)),
            recurrenceRule = RecurrenceRule.daily(interval = 1)
        )
        // Event started in Jan, query only 3 days in March
        val from = LocalDateTime.of(2026, 3, 10, 0, 0)
        val to = LocalDateTime.of(2026, 3, 12, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 0), occurrences[0].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 3, 11, 10, 0), occurrences[1].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 3, 12, 10, 0), occurrences[2].originalStartDateTime)
    }

    // =========================================================================
    // SECTION C — Weekly Recurrence
    // =========================================================================

    @Test
    fun `C1 weekly recurrence on one weekday (Wednesday)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 11, 0)), // Wednesday
            recurrenceRule = RecurrenceRule.weekly(daysOfWeek = setOf(DayOfWeek.WEDNESDAY), interval = 1)
        )
        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // Wednesdays in Oct 2026 on/after Oct 21: Oct 21, Oct 28
        assertEquals(2, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 10, 21, 11, 0), occurrences[0].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 10, 28, 11, 0), occurrences[1].originalStartDateTime)
    }

    @Test
    fun `C2 weekly recurrence on multiple weekdays (Mon, Wed, Fri)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 19, 9, 0)), // Monday
            recurrenceRule = RecurrenceRule.weekly(
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                interval = 1
            )
        )
        val from = LocalDateTime.of(2026, 10, 19, 0, 0)
        val to = LocalDateTime.of(2026, 10, 25, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
        assertEquals(DayOfWeek.MONDAY, occurrences[0].originalStartDateTime.dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, occurrences[1].originalStartDateTime.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, occurrences[2].originalStartDateTime.dayOfWeek)
    }

    @Test
    fun `C3 weekly recurrence every N weeks (interval 2)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 20, 15, 0)), // Tuesday
            recurrenceRule = RecurrenceRule.weekly(
                daysOfWeek = setOf(DayOfWeek.TUESDAY),
                interval = 2
            )
        )
        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 11, 30, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // Week 0: Oct 20
        // Week 2: Nov 3
        // Week 4: Nov 17
        assertEquals(3, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 10, 20, 15, 0), occurrences[0].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 3, 15, 0), occurrences[1].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 17, 15, 0), occurrences[2].originalStartDateTime)
    }

    @Test
    fun `C4 weekly recurrence mid-week start ignores earlier days of first week`() {
        // Starts on Wednesday Oct 21, rule specifies Monday, Wednesday, Friday
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 21, 9, 0)),
            recurrenceRule = RecurrenceRule.weekly(
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                interval = 1
            )
        )
        val from = LocalDateTime.of(2026, 10, 19, 0, 0) // Monday
        val to = LocalDateTime.of(2026, 10, 25, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // Monday Oct 19 is before event start, so only Wed Oct 21 and Fri Oct 23 should be produced
        assertEquals(2, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 10, 21, 9, 0), occurrences[0].originalStartDateTime)
        assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), occurrences[1].originalStartDateTime)
    }

    // =========================================================================
    // SECTION D — Weekdays
    // =========================================================================

    @Test
    fun `D1 weekdays helper generates only Monday to Friday`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 19, 10, 0)), // Monday
            recurrenceRule = RecurrenceRule.weekdays()
        )
        val from = LocalDateTime.of(2026, 10, 19, 0, 0)
        val to = LocalDateTime.of(2026, 10, 25, 23, 59) // Sunday

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)
        val days = occurrences.map { it.originalStartDateTime.dayOfWeek }
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            days
        )
        assertFalse(days.contains(DayOfWeek.SATURDAY))
        assertFalse(days.contains(DayOfWeek.SUNDAY))
    }

    // =========================================================================
    // SECTION E — Weekends
    // =========================================================================

    @Test
    fun `E1 weekends helper generates only Saturday and Sunday`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 24, 10, 0)), // Saturday
            recurrenceRule = RecurrenceRule.weekends()
        )
        val from = LocalDateTime.of(2026, 10, 19, 0, 0)
        val to = LocalDateTime.of(2026, 10, 25, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(2, occurrences.size)
        assertEquals(DayOfWeek.SATURDAY, occurrences[0].originalStartDateTime.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, occurrences[1].originalStartDateTime.dayOfWeek)
    }

    // =========================================================================
    // SECTION F — Monthly Recurrence & Clamping
    // =========================================================================

    @Test
    fun `F1 monthly normal day`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 15, 12, 0)),
            recurrenceRule = RecurrenceRule.monthly(dayOfMonth = 15, interval = 1)
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 4, 30, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(4, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 15), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 15), occurrences[1].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 15), occurrences[2].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 4, 15), occurrences[3].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `F2 monthly 30th clamping in February`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 30, 10, 0)),
            recurrenceRule = RecurrenceRule.monthly(dayOfMonth = 30, interval = 1)
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 3, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 30), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 28), occurrences[1].originalStartDateTime.toLocalDate()) // Clamped
        assertEquals(LocalDate.of(2026, 3, 30), occurrences[2].originalStartDateTime.toLocalDate()) // Restores to 30th
    }

    @Test
    fun `F3 monthly 31st clamping on 30-day months and February`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 31, 10, 0)),
            recurrenceRule = RecurrenceRule.monthly(dayOfMonth = 31, interval = 1)
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 5, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 31), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 28), occurrences[1].originalStartDateTime.toLocalDate()) // Feb clamped
        assertEquals(LocalDate.of(2026, 3, 31), occurrences[2].originalStartDateTime.toLocalDate()) // March 31
        assertEquals(LocalDate.of(2026, 4, 30), occurrences[3].originalStartDateTime.toLocalDate()) // April clamped
        assertEquals(LocalDate.of(2026, 5, 31), occurrences[4].originalStartDateTime.toLocalDate()) // May 31
    }

    @Test
    fun `F4 monthly 31st clamping in leap February (2028)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2028, 1, 31, 10, 0)),
            recurrenceRule = RecurrenceRule.monthly(dayOfMonth = 31, interval = 1)
        )
        val from = LocalDateTime.of(2028, 2, 1, 0, 0)
        val to = LocalDateTime.of(2028, 2, 29, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2028, 2, 29), occurrences[0].originalStartDateTime.toLocalDate()) // Leap year 29
    }

    @Test
    fun `F5 interval-based monthly recurrence (every 2 months)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 15, 10, 0)),
            recurrenceRule = RecurrenceRule.monthly(dayOfMonth = 15, interval = 2)
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 7, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // Jan 15, Mar 15, May 15, Jul 15
        assertEquals(4, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 15), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 15), occurrences[1].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 5, 15), occurrences[2].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 7, 15), occurrences[3].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `F6 monthly setPosition (2nd Tuesday)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 13, 10, 0)),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                daysOfWeek = setOf(DayOfWeek.TUESDAY),
                setPosition = 2
            )
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 2, 28, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(2, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 13), occurrences[0].originalStartDateTime.toLocalDate()) // 2nd Tue in Jan
        assertEquals(LocalDate.of(2026, 2, 10), occurrences[1].originalStartDateTime.toLocalDate()) // 2nd Tue in Feb
    }

    // =========================================================================
    // SECTION G — Yearly Recurrence
    // =========================================================================

    @Test
    fun `G1 yearly normal date recurrence`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2025, 6, 15, 10, 0)),
            recurrenceRule = RecurrenceRule.yearly(monthOfYear = 6, dayOfMonth = 15, interval = 1)
        )
        val from = LocalDateTime.of(2025, 1, 1, 0, 0)
        val to = LocalDateTime.of(2028, 12, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(4, occurrences.size)
        assertEquals(LocalDate.of(2025, 6, 15), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 15), occurrences[1].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2027, 6, 15), occurrences[2].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2028, 6, 15), occurrences[3].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `G2 yearly Feb 29 clamps to Feb 28 in non-leap years and Feb 29 in leap years`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2024, 2, 29, 10, 0)),
            recurrenceRule = RecurrenceRule.yearly(monthOfYear = 2, dayOfMonth = 29, interval = 1)
        )
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = LocalDateTime.of(2028, 12, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)
        assertEquals(LocalDate.of(2024, 2, 29), occurrences[0].originalStartDateTime.toLocalDate()) // Leap
        assertEquals(LocalDate.of(2025, 2, 28), occurrences[1].originalStartDateTime.toLocalDate()) // Non-leap
        assertEquals(LocalDate.of(2026, 2, 28), occurrences[2].originalStartDateTime.toLocalDate()) // Non-leap
        assertEquals(LocalDate.of(2027, 2, 28), occurrences[3].originalStartDateTime.toLocalDate()) // Non-leap
        assertEquals(LocalDate.of(2028, 2, 29), occurrences[4].originalStartDateTime.toLocalDate()) // Leap
    }

    @Test
    fun `G3 yearly interval-based recurrence (every 2 years)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2024, 5, 1, 10, 0)),
            recurrenceRule = RecurrenceRule.yearly(monthOfYear = 5, dayOfMonth = 1, interval = 2)
        )
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = LocalDateTime.of(2028, 12, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // 2024, 2026, 2028
        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2024, 5, 1), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 5, 1), occurrences[1].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2028, 5, 1), occurrences[2].originalStartDateTime.toLocalDate())
    }

    // =========================================================================
    // SECTION H — Exclusions
    // =========================================================================

    @Test
    fun `H1 single excluded date in RecurrenceRule is skipped`() {
        val excludedDate = LocalDate.of(2026, 1, 3)
        val baseRule = RecurrenceRule.daily(interval = 1).withExcludedDate(excludedDate)
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = baseRule
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 5, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(4, occurrences.size)
        val dates = occurrences.map { it.originalStartDateTime.toLocalDate() }
        assertFalse(dates.contains(excludedDate))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5)),
            dates
        )
    }

    @Test
    fun `H2 multiple excluded dates in RecurrenceRule`() {
        val ex1 = LocalDate.of(2026, 1, 2)
        val ex2 = LocalDate.of(2026, 1, 4)
        val baseRule = RecurrenceRule.daily(interval = 1)
            .withExcludedDate(ex1)
            .withExcludedDate(ex2)
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = baseRule
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 5, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
        val dates = occurrences.map { it.originalStartDateTime.toLocalDate() }
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5)),
            dates
        )
    }

    @Test
    fun `H3 exclusion outside range does not affect occurrences within range`() {
        val exOutside = LocalDate.of(2026, 2, 10)
        val baseRule = RecurrenceRule.daily(interval = 1).withExcludedDate(exOutside)
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = baseRule
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 3, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
    }

    // =========================================================================
    // SECTION I — Exceptions
    // =========================================================================

    @Test
    fun `I1 reschedule exception modifies effective time while preserving original identity`() {
        val event = createEvent(
            recurrenceRule = RecurrenceRule.daily(interval = 1)
        )
        val originalDate = LocalDateTime.of(2026, 1, 2, 9, 0)
        val rescheduledDate = LocalDateTime.of(2026, 1, 2, 16, 0)

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = originalDate,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledDate
        )

        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 3, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to, listOf(exception))
        assertEquals(3, occurrences.size)

        // Find the modified occurrence
        val modOccurrence = occurrences.first { it.originalStartDateTime == originalDate }
        assertEquals(OccurrenceStatus.RESCHEDULED, modOccurrence.status)
        assertTrue(modOccurrence.isException)
        assertEquals(rescheduledDate, modOccurrence.effectiveStartDateTime)
        assertEquals(originalDate, modOccurrence.originalStartDateTime)
        assertEquals(exception.occurrenceId, modOccurrence.occurrenceId)
    }

    @Test
    fun `I2 reschedule timezone exception preserves original zoneId and updates effectiveZoneId`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0), ZoneId.of("UTC")),
            recurrenceRule = RecurrenceRule.daily(interval = 1)
        )
        val originalDate = LocalDateTime.of(2026, 1, 2, 9, 0)
        val newZone = ZoneId.of("America/New_York")
        val newDate = LocalDateTime.of(2026, 1, 2, 10, 0)

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = originalDate,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = newDate,
            rescheduledZoneId = newZone
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(exception)
        )

        val mod = occurrences.first { it.originalStartDateTime == originalDate }
        assertEquals(ZoneId.of("UTC"), mod.zoneId)
        assertEquals(newZone, mod.effectiveZoneId)
        assertEquals(newZone, mod.rescheduledZoneId)
        assertEquals(newDate, mod.effectiveStartDateTime)
    }

    @Test
    fun `I3 completion exception sets status and completedAt`() {
        val event = createEvent(
            recurrenceRule = RecurrenceRule.daily()
        )
        val completedTarget = LocalDateTime.of(2026, 1, 2, 9, 0)
        val completedInstant = Instant.parse("2026-01-02T09:15:00Z")

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = completedTarget,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.COMPLETED,
            completedAt = completedInstant
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(exception)
        )

        val completed = occurrences.first { it.originalStartDateTime == completedTarget }
        assertEquals(OccurrenceStatus.COMPLETED, completed.status)
        assertTrue(completed.isCompleted)
        assertEquals(completedInstant, completed.completedAt)
    }

    @Test
    fun `I4 skip exception sets status to SKIPPED`() {
        val event = createEvent(
            recurrenceRule = RecurrenceRule.daily()
        )
        val skippedTarget = LocalDateTime.of(2026, 1, 2, 9, 0)

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = skippedTarget,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.SKIPPED
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(exception)
        )

        val skipped = occurrences.first { it.originalStartDateTime == skippedTarget }
        assertEquals(OccurrenceStatus.SKIPPED, skipped.status)
        assertTrue(skipped.isSkipped)
    }

    @Test
    fun `I5 exclusion exception omits occurrence entirely from results`() {
        val event = createEvent(
            recurrenceRule = RecurrenceRule.daily()
        )
        val excludedTarget = LocalDateTime.of(2026, 1, 2, 9, 0)

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = excludedTarget,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.EXCLUDED
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(exception)
        )

        assertEquals(2, occurrences.size)
        val dates = occurrences.map { it.originalStartDateTime }
        assertFalse(dates.contains(excludedTarget))
    }

    @Test
    fun `I6 occurrence rescheduled into window from outside is included`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        // Original occurrence on Jan 5 (outside window Jan 10..Jan 12)
        val originalDate = LocalDateTime.of(2026, 1, 5, 9, 0)
        val rescheduledDate = LocalDateTime.of(2026, 1, 11, 14, 0) // inside window

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = originalDate,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledDate
        )

        val from = LocalDateTime.of(2026, 1, 10, 0, 0)
        val to = LocalDateTime.of(2026, 1, 12, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to, listOf(exception))
        // Nominal occurrences: Jan 10, Jan 11, Jan 12 (3 items)
        // Rescheduled occurrence: originally Jan 5, now Jan 11 (1 item)
        // Total should include the rescheduled one
        val rescheduled = occurrences.firstOrNull { it.originalStartDateTime == originalDate }
        assertNotNull(rescheduled)
        assertEquals(rescheduledDate, rescheduled?.effectiveStartDateTime)
        assertEquals(originalDate, rescheduled?.originalStartDateTime)
    }

    @Test
    fun `I7 occurrence rescheduled out of window to outside is excluded from window`() {
        val event = createEvent(
            recurrenceRule = RecurrenceRule.daily()
        )
        // Original date Jan 2 (inside window), rescheduled to Jan 20 (outside window)
        val originalDate = LocalDateTime.of(2026, 1, 2, 9, 0)
        val rescheduledDate = LocalDateTime.of(2026, 1, 20, 9, 0)

        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = originalDate,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = rescheduledDate
        )

        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 3, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to, listOf(exception))
        // Jan 1 and Jan 3 remain. Jan 2 was moved out.
        assertEquals(2, occurrences.size)
        val dates = occurrences.map { it.originalStartDateTime }
        assertFalse(dates.contains(originalDate))
    }

    // =========================================================================
    // SECTION J — Boundaries
    // =========================================================================

    @Test
    fun `J1 recurrence start boundary never projects occurrences before event inception`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 5, 10, 10, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 5, 12, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        // Only May 10, 11, 12
        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2026, 5, 10), occurrences[0].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `J2 recurrence end date (UntilDate) stops projection exactly at date boundary`() {
        val until = LocalDate.of(2026, 1, 5)
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.UntilDate(until))
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 10, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 5), occurrences.last().originalStartDateTime.toLocalDate())
    }

    @Test
    fun `J3 occurrence count (Count) stops projection after maxOccurrences reached`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.Count(3))
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 10, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 3), occurrences.last().originalStartDateTime.toLocalDate())
    }

    @Test
    fun `J4 occurrence count with window starting after first occurrences`() {
        // Event started Jan 1 with Count(5): Jan 1, 2, 3, 4, 5.
        // Query window is Jan 4 to Jan 10. Should only return Jan 4 and Jan 5.
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.Count(5))
        )
        val from = LocalDateTime.of(2026, 1, 4, 0, 0)
        val to = LocalDateTime.of(2026, 1, 10, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(2, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 4), occurrences[0].originalStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 1, 5), occurrences[1].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `J5 projection range inclusive boundaries (exact match at from and to)`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 12, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 1, 1, 12, 0)
        val to = LocalDateTime.of(2026, 1, 3, 12, 0)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(3, occurrences.size)
        assertEquals(from, occurrences.first().originalStartDateTime)
        assertEquals(to, occurrences.last().originalStartDateTime)
    }

    // =========================================================================
    // SECTION K — Timezone & DST Transitions
    // =========================================================================

    @Test
    fun `K1 DST spring forward transition preserves local time across boundary`() {
        // America/New_York transitions to DST on Sunday March 8, 2026 (clocks jump 2:00 -> 3:00)
        val nyZone = ZoneId.of("America/New_York")
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 3, 6, 9, 0), nyZone),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 3, 6, 0, 0)
        val to = LocalDateTime.of(2026, 3, 10, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)

        for (occ in occurrences) {
            // Local wall clock remains strictly 09:00 AM every single day
            assertEquals(LocalTime.of(9, 0), occ.originalStartDateTime.toLocalTime())
            assertEquals(nyZone, occ.zoneId)
        }

        // Before transition: March 7 offset is -05:00
        val zdtBefore = occurrences[1].originalStartDateTime.atZone(nyZone)
        assertEquals("-05:00", zdtBefore.offset.id)

        // After transition: March 8 and March 9 offset is -04:00
        val zdtTransitionDay = occurrences[2].originalStartDateTime.atZone(nyZone)
        assertEquals("-04:00", zdtTransitionDay.offset.id)

        val zdtAfter = occurrences[3].originalStartDateTime.atZone(nyZone)
        assertEquals("-04:00", zdtAfter.offset.id)
    }

    @Test
    fun `K2 DST fall back transition preserves local time across boundary`() {
        // America/New_York transitions from DST on Sunday November 1, 2026 (clocks fall back 2:00 -> 1:00)
        val nyZone = ZoneId.of("America/New_York")
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 30, 9, 0), nyZone),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 10, 30, 0, 0)
        val to = LocalDateTime.of(2026, 11, 3, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)

        for (occ in occurrences) {
            assertEquals(LocalTime.of(9, 0), occ.originalStartDateTime.toLocalTime())
            assertEquals(nyZone, occ.zoneId)
        }

        // Before fall back: Oct 31 offset is -04:00
        val zdtBefore = occurrences[1].originalStartDateTime.atZone(nyZone)
        assertEquals("-04:00", zdtBefore.offset.id)

        // After fall back: Nov 1 and Nov 2 offset is -05:00
        val zdtAfter = occurrences[2].originalStartDateTime.atZone(nyZone)
        assertEquals("-05:00", zdtAfter.offset.id)
    }

    @Test
    fun `K3 occurrence identity is stable and differentiates timezones`() {
        val ldt = LocalDateTime.of(2026, 5, 1, 9, 0)
        val idUtc = EventOccurrence.createOccurrenceId("evt-1", ldt, ZoneId.of("UTC"))
        val idTokyo = EventOccurrence.createOccurrenceId("evt-1", ldt, ZoneId.of("Asia/Tokyo"))

        assertNotEquals(idUtc, idTokyo)
        assertEquals("evt-1_2026-05-01T09:00_UTC", idUtc)
        assertEquals("evt-1_2026-05-01T09:00_Asia/Tokyo", idTokyo)
    }

    // =========================================================================
    // SECTION L — Safety & Edge Cases
    // =========================================================================

    @Test
    fun `L1 unbounded recurring event with finite projection terminates promptly`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2020, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.Never)
        )
        val from = LocalDateTime.of(2026, 10, 1, 0, 0)
        val to = LocalDateTime.of(2026, 10, 5, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)
    }

    @Test
    fun `L2 empty projection range (to before from) returns empty list`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 1, 10, 0, 0)
        val to = LocalDateTime.of(2026, 1, 5, 0, 0) // Before from

        val occurrences = engine.projectOccurrences(event, from, to)
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `L3 projection range entirely before recurrence start returns empty list`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 6, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 31, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `L4 recurrence with zero interval defaults safely to interval 1 and does not loop infinitely`() {
        val zeroIntervalRule = RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            interval = 0
        )
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = zeroIntervalRule
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 5, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(5, occurrences.size)
    }

    // =========================================================================
    // SECTION M — Invariants & Property Tests (Step 15)
    // =========================================================================

    @Test
    fun `M1 Invariant 1 - Projection always terminates even with large range`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 12, 31, 23, 59) // Full year

        val occurrences = engine.projectOccurrences(event, from, to)
        assertEquals(365, occurrences.size)
    }

    @Test
    fun `M2 Invariant 2 - Every generated occurrence belongs to requested projection range`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 4, 10, 0, 0)
        val to = LocalDateTime.of(2026, 4, 20, 23, 59)

        val occurrences = engine.projectOccurrences(event, from, to)
        assertTrue(occurrences.isNotEmpty())
        for (occ in occurrences) {
            assertFalse(occ.effectiveStartDateTime.isBefore(from))
            assertFalse(occ.effectiveStartDateTime.isAfter(to))
        }
    }

    @Test
    fun `M3 Invariant 3 - No excluded occurrence is returned`() {
        val exDate = LocalDate.of(2026, 4, 15)
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 4, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily().withExcludedDate(exDate)
        )
        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 4, 10, 0, 0),
            LocalDateTime.of(2026, 4, 20, 23, 59)
        )
        for (occ in occurrences) {
            assertNotEquals(exDate, occ.originalStartDateTime.toLocalDate())
            assertNotEquals(OccurrenceStatus.EXCLUDED, occ.status)
        }
    }

    @Test
    fun `M4 Invariant 4 - Occurrences are strictly chronologically sorted`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 31, 23, 59)
        )
        for (i in 0 until occurrences.size - 1) {
            assertTrue(occurrences[i].effectiveStartDateTime <= occurrences[i + 1].effectiveStartDateTime)
        }
    }

    @Test
    fun `M5 Invariant 5 - Occurrence identity is deterministic and predictable`() {
        val ldt = LocalDateTime.of(2026, 1, 1, 9, 0)
        val event = createEvent(
            id = "deterministic-event",
            temporal = EventDateTime.DateTime(ldt, ZoneId.of("UTC")),
            recurrenceRule = RecurrenceRule.daily()
        )
        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 1, 23, 59)
        )
        assertEquals(1, occurrences.size)
        assertEquals("deterministic-event_2026-01-01T09:00_UTC", occurrences[0].occurrenceId)
    }

    @Test
    fun `M6 Invariant 6 - Rescheduling does not change original occurrence identity`() {
        val ldt = LocalDateTime.of(2026, 1, 2, 9, 0)
        val event = createEvent(
            id = "reschedule-invariant",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0), ZoneId.of("UTC")),
            recurrenceRule = RecurrenceRule.daily()
        )
        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = ldt,
            originalZoneId = ZoneId.of("UTC"),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = LocalDateTime.of(2026, 1, 2, 17, 0)
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(exception)
        )

        val resched = occurrences.first { it.status == OccurrenceStatus.RESCHEDULED }
        assertEquals("reschedule-invariant_2026-01-02T09:00_UTC", resched.occurrenceId)
        assertEquals(ldt, resched.originalStartDateTime)
    }

    @Test
    fun `M7 Invariant 7 - Completing an occurrence does not mutate the Event`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 1, 2, 9, 0),
            status = OccurrenceStatus.COMPLETED,
            completedAt = Instant.now()
        )

        engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(exception)
        )

        assertEquals(EventLifecycleStatus.ACTIVE, event.lifecycleStatus)
        assertEquals(RecurrenceFrequency.DAILY, event.recurrenceRule?.frequency)
        assertNull(event.deletedAt)
    }

    @Test
    fun `M8 Invariant 8 - Skipping one occurrence does not disable the recurrence`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val skipException = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 1, 2, 9, 0),
            status = OccurrenceStatus.SKIPPED
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 3, 23, 59),
            listOf(skipException)
        )

        assertEquals(3, occurrences.size)
        assertEquals(OccurrenceStatus.PENDING, occurrences[0].status)
        assertEquals(OccurrenceStatus.SKIPPED, occurrences[1].status)
        assertEquals(OccurrenceStatus.PENDING, occurrences[2].status)
    }

    @Test
    fun `M9 Invariant 9 - February 29 yearly recurrence clamps to Feb 28 in non-leap`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2024, 2, 29, 10, 0)),
            recurrenceRule = RecurrenceRule.yearly(monthOfYear = 2, dayOfMonth = 29)
        )
        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 12, 31, 23, 59)
        )
        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2026, 2, 28), occurrences[0].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `M10 Invariant 10 - Monthly invalid days clamp according to project semantics`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 31, 10, 0)),
            recurrenceRule = RecurrenceRule.monthly(dayOfMonth = 31)
        )
        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 30, 23, 59)
        )
        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2026, 4, 30), occurrences[0].originalStartDateTime.toLocalDate())
    }

    @Test
    fun `M11 Invariant 11 - No duplicate occurrence identities are generated`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        // Add exception that reschedules within the window
        val exception = OccurrenceException(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 1, 2, 9, 0),
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = LocalDateTime.of(2026, 1, 2, 14, 0)
        )

        val occurrences = engine.projectOccurrences(
            event,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 5, 23, 59),
            listOf(exception)
        )

        val ids = occurrences.map { it.occurrenceId }
        val uniqueIds = ids.toSet()
        assertEquals(ids.size, uniqueIds.size)
    }

    @Test
    fun `M12 Invariant 12 - Infinite recurrence rules remain finite under bounded projection`() {
        val infiniteEvent = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2020, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily(endCondition = RecurrenceEnd.Never)
        )
        val occurrences = engine.projectOccurrences(
            infiniteEvent,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 10, 23, 59)
        )
        assertEquals(10, occurrences.size)
    }

    // =========================================================================
    // SECTION N — ProjectOccurrencesUseCase & Overdue Evaluation
    // =========================================================================

    @Test
    fun `N1 ProjectOccurrencesUseCase delegates correctly to engine`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 1, 1, 9, 0)),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 1, 3, 23, 59)

        val occurrences = projectOccurrencesUseCase(event, from, to)
        assertEquals(3, occurrences.size)
    }

    @Test
    fun `N2 evaluateOverdue marks past uncompleted occurrences as OVERDUE without marking as COMPLETED`() {
        // Current test time is 2026-10-20T10:00:00Z
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 18, 9, 0), ZoneId.of("UTC")),
            recurrenceRule = RecurrenceRule.daily()
        )
        val from = LocalDateTime.of(2026, 10, 18, 0, 0)
        val to = LocalDateTime.of(2026, 10, 22, 23, 59)

        val occurrences = engine.projectOccurrences(
            event = event,
            from = from,
            to = to,
            evaluateOverdue = true,
            timeProvider = timeProvider
        )

        assertEquals(5, occurrences.size)
        // Oct 18 (past) -> OVERDUE
        assertEquals(OccurrenceStatus.OVERDUE, occurrences[0].status)
        assertTrue(occurrences[0].isOverdue)
        assertFalse(occurrences[0].isCompleted)

        // Oct 19 (past) -> OVERDUE
        assertEquals(OccurrenceStatus.OVERDUE, occurrences[1].status)

        // Oct 20 09:00 (past 10:00) -> OVERDUE
        assertEquals(OccurrenceStatus.OVERDUE, occurrences[2].status)

        // Oct 21 (future) -> PENDING
        assertEquals(OccurrenceStatus.PENDING, occurrences[3].status)

        // Oct 22 (future) -> PENDING
        assertEquals(OccurrenceStatus.PENDING, occurrences[4].status)
    }

    @Test
    fun `N3 evaluateOverdue does not alter already completed or skipped occurrences`() {
        val event = createEvent(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 18, 9, 0), ZoneId.of("UTC")),
            recurrenceRule = RecurrenceRule.daily()
        )
        val completedTarget = LocalDateTime.of(2026, 10, 18, 9, 0)
        val skippedTarget = LocalDateTime.of(2026, 10, 19, 9, 0)

        val exceptions = listOf(
            OccurrenceException(
                eventId = event.id,
                originalStartDateTime = completedTarget,
                originalZoneId = ZoneId.of("UTC"),
                status = OccurrenceStatus.COMPLETED,
                completedAt = Instant.parse("2026-10-18T09:30:00Z")
            ),
            OccurrenceException(
                eventId = event.id,
                originalStartDateTime = skippedTarget,
                originalZoneId = ZoneId.of("UTC"),
                status = OccurrenceStatus.SKIPPED
            )
        )

        val occurrences = engine.projectOccurrences(
            event = event,
            from = LocalDateTime.of(2026, 10, 18, 0, 0),
            to = LocalDateTime.of(2026, 10, 20, 23, 59),
            exceptions = exceptions,
            evaluateOverdue = true,
            timeProvider = timeProvider
        )

        assertEquals(OccurrenceStatus.COMPLETED, occurrences[0].status)
        assertEquals(OccurrenceStatus.SKIPPED, occurrences[1].status)
        assertEquals(OccurrenceStatus.OVERDUE, occurrences[2].status) // Oct 20 09:00 was uncompleted
    }
}
