package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.domain.model.*
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DashboardUpcomingAndReschedulingReliabilityTest {

    private val utcZone = ZoneId.of("UTC")
    private val nyZone = ZoneId.of("America/New_York")

    // Wednesday, Oct 28, 2026 at 12:41:00 UTC
    private val baseInstant = Instant.parse("2026-10-28T12:41:00Z")
    private lateinit var timeProvider: TestTimeProvider

    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var exceptionRepository: InMemoryOccurrenceExceptionRepository
    private lateinit var notificationRepository: InMemoryNotificationRepository
    private lateinit var alarmScheduler: TestAlarmScheduler
    private lateinit var recurrenceEngine: DefaultRecurrenceEngine

    private lateinit var getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase
    private lateinit var getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase
    private lateinit var scheduleNotificationsUseCase: ScheduleNotificationsForEventUseCase
    private lateinit var cancelNotificationsUseCase: CancelNotificationsForEventUseCase
    private lateinit var editRecurringSeriesUseCase: EditRecurringSeriesUseCase
    private lateinit var rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase

    private val deliveredImmediateList = mutableListOf<Triple<Event, NotificationInstance, Int>>()
    private var stoppedActiveAlarmCount = 0

    @Before
    fun setUp() {
        timeProvider = TestTimeProvider(currentInstant = baseInstant, currentZoneId = utcZone)
        eventRepository = InMemoryEventRepository()
        exceptionRepository = InMemoryOccurrenceExceptionRepository()
        notificationRepository = InMemoryNotificationRepository()
        alarmScheduler = TestAlarmScheduler()
        recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

        getDashboardOccurrencesUseCase = GetDashboardOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = exceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )

        getUpcomingOccurrencesUseCase = GetUpcomingOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = exceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )

        scheduleNotificationsUseCase = ScheduleNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )

        cancelNotificationsUseCase = CancelNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler
        )

        editRecurringSeriesUseCase = EditRecurringSeriesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = exceptionRepository,
            scheduleNotificationsUseCase = scheduleNotificationsUseCase,
            cancelNotificationsUseCase = cancelNotificationsUseCase,
            timeProvider = timeProvider
        )

        deliveredImmediateList.clear()
        stoppedActiveAlarmCount = 0

        rescheduleOccurrenceActionUseCase = RescheduleOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = exceptionRepository,
            rescheduleOccurrenceUseCase = RescheduleOccurrenceUseCase(),
            scheduleNotificationsForEventUseCase = scheduleNotificationsUseCase,
            timeProvider = timeProvider,
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            stopActiveAlarm = { stoppedActiveAlarmCount++ },
            immediateNotificationDeliverer = { event, instance, missedCount ->
                deliveredImmediateList.add(Triple(event, instance, missedCount))
            }
        )
    }

    private fun buildEvent(
        id: String,
        title: String,
        startDateTime: LocalDateTime,
        zone: ZoneId = utcZone,
        rule: RecurrenceRule? = null,
        schedules: List<NotificationSchedule> = emptyList()
    ): Event = Event(
        id = id,
        title = title,
        description = "Test description",
        eventTypeId = "type_1",
        categoryId = "cat_1",
        priority = Priority.NORMAL,
        lifecycleStatus = EventLifecycleStatus.ACTIVE,
        temporal = EventDateTime.DateTime(startDateTime, zone),
        recurrenceRule = rule,
        notificationSchedules = schedules,
        createdAt = timeProvider.nowInstant(),
        updatedAt = timeProvider.nowInstant()
    )

    // ==========================================
    // 1. TODAY DASHBOARD TESTS
    // ==========================================

    @Test
    fun todayDashboard_showsOnlyTodayOccurrences_andSeparatesOverdueFromRemaining() = runTest {
        // Today is 2026-10-28 12:41
        // Event 1: earlier today at 10:00 (Overdue on Today)
        val todayOverdue = buildEvent("e_tod_overdue", "Morning Sync", LocalDateTime.of(2026, 10, 28, 10, 0))
        // Event 2: later today at 14:00 (Today / Remaining)
        val todayRemaining = buildEvent("e_tod_rem", "Afternoon Planning", LocalDateTime.of(2026, 10, 28, 14, 0))
        // Event 3: Daily recurrence at 10:00 (Tomorrow must NOT appear on Today Dashboard)
        val dailyEvent = buildEvent(
            id = "e_daily",
            title = "Daily Standup",
            startDateTime = LocalDateTime.of(2026, 10, 28, 15, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        )
        // Event 4: Tomorrow one-time event
        val tomorrowEvent = buildEvent("e_tom", "Tomorrow Event", LocalDateTime.of(2026, 10, 29, 9, 0))

        eventRepository.saveEvent(todayOverdue)
        eventRepository.saveEvent(todayRemaining)
        eventRepository.saveEvent(dailyEvent)
        eventRepository.saveEvent(tomorrowEvent)

        val dashboard = getDashboardOccurrencesUseCase().first()

        // 1. Overdue section: strictly today's past incomplete occurrence
        assertEquals(1, dashboard.overdue.size)
        assertEquals("e_tod_overdue", dashboard.overdue.first().event.id)

        // 2. Today / Remaining section: today's upcoming occurrences
        assertEquals(2, dashboard.today.size)
        val todayIds = dashboard.today.map { it.event.id }.toSet()
        assertTrue(todayIds.contains("e_tod_rem"))
        assertTrue(todayIds.contains("e_daily"))

        // 3. Daily event must produce ONLY today's occurrence on Today Dashboard (not tomorrow, not later)
        val dailyOccurrencesOnDashboard = (dashboard.overdue + dashboard.today).filter { it.event.id == "e_daily" }
        assertEquals(1, dailyOccurrencesOnDashboard.size)

        // 4. Strict rule: tomorrow and upcoming lists are empty
        assertTrue("Tomorrow list must be empty on Today Dashboard", dashboard.tomorrow.isEmpty())
        assertTrue("Upcoming list must be empty on Today Dashboard", dashboard.upcoming.isEmpty())
        assertTrue(dashboard.today.none { it.event.id == "e_tom" })
    }

    // ==========================================
    // 2. UPCOMING NEXT TESTS
    // ==========================================

    @Test
    fun upcomingNext_returnsExactlyOneFutureOccurrencePerEvent_strictlyAfterToday() = runTest {
        // Today is 2026-10-28
        // Event 1: Daily recurrence
        val daily = buildEvent(
            "e_daily", "Daily Exercise",
            LocalDateTime.of(2026, 10, 28, 7, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        )
        // Event 2: Weekly recurrence on Friday (Oct 30)
        val weekly = buildEvent(
            "e_weekly", "Friday Retro",
            LocalDateTime.of(2026, 10, 23, 16, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY)
        )
        // Event 3: Monthly recurrence on the 1st
        val monthly = buildEvent(
            "e_monthly", "Monthly Rent",
            LocalDateTime.of(2026, 10, 1, 10, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY)
        )
        // Event 4: Yearly recurrence on Dec 25
        val yearly = buildEvent(
            "e_yearly", "Christmas",
            LocalDateTime.of(2025, 12, 25, 9, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.YEARLY)
        )
        // Event 5: One-time event next month (Nov 15)
        val oneTimeFuture = buildEvent(
            "e_onetime", "Conference",
            LocalDateTime.of(2026, 11, 15, 10, 0)
        )
        // Event 6: One-time event today (MUST BE EXCLUDED from Next)
        val oneTimeToday = buildEvent(
            "e_today_only", "Today Lunch",
            LocalDateTime.of(2026, 10, 28, 13, 0)
        )

        eventRepository.saveEvent(daily)
        eventRepository.saveEvent(weekly)
        eventRepository.saveEvent(monthly)
        eventRepository.saveEvent(yearly)
        eventRepository.saveEvent(oneTimeFuture)
        eventRepository.saveEvent(oneTimeToday)

        val nextItems = getUpcomingOccurrencesUseCase.nextOccurrencePerEvent(afterDate = LocalDate.of(2026, 10, 28)).first()

        // Exactly one occurrence for daily, weekly, monthly, yearly, and oneTimeFuture = 5
        assertEquals(5, nextItems.size)

        // Today's event must NOT appear in Next
        assertTrue(nextItems.none { it.event.id == "e_today_only" })

        // Daily recurring series must produce exactly ONE occurrence (tomorrow Oct 29), not 14 or 30
        val dailyItems = nextItems.filter { it.event.id == "e_daily" }
        assertEquals(1, dailyItems.size)
        assertEquals(LocalDate.of(2026, 10, 29), dailyItems.first().occurrence.effectiveStartDateTime.toLocalDate())

        // Weekly recurring series must produce exactly ONE occurrence (Oct 30)
        val weeklyItems = nextItems.filter { it.event.id == "e_weekly" }
        assertEquals(1, weeklyItems.size)
        assertEquals(LocalDate.of(2026, 10, 30), weeklyItems.first().occurrence.effectiveStartDateTime.toLocalDate())

        // Monthly recurring series must produce exactly ONE occurrence (Nov 1)
        val monthlyItems = nextItems.filter { it.event.id == "e_monthly" }
        assertEquals(1, monthlyItems.size)
        assertEquals(LocalDate.of(2026, 11, 1), monthlyItems.first().occurrence.effectiveStartDateTime.toLocalDate())

        // Yearly recurring series must produce exactly ONE occurrence (Dec 25, 2026)
        val yearlyItems = nextItems.filter { it.event.id == "e_yearly" }
        assertEquals(1, yearlyItems.size)
        assertEquals(LocalDate.of(2026, 12, 25), yearlyItems.first().occurrence.effectiveStartDateTime.toLocalDate())
    }

    // ==========================================
    // 3. UPCOMING OVERDUE TESTS
    // ==========================================

    @Test
    fun upcomingOverdue_limitsToMaxFourMostRecentPerEvent_excludesCompletedAndSkipped() = runTest {
        // Today is Oct 28. Event A has daily recurrence starting Oct 20.
        // Overdue dates: Oct 20, 21, 22, 23, 24, 25, 26, 27 (8 past dates)
        val dailySeries = buildEvent(
            id = "e_daily_overdue",
            title = "Daily Journal",
            startDateTime = LocalDateTime.of(2026, 10, 20, 9, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        )
        eventRepository.saveEvent(dailySeries)

        // Mark Oct 27 as COMPLETED
        val exc27 = OccurrenceException(
            eventId = "e_daily_overdue",
            originalStartDateTime = LocalDateTime.of(2026, 10, 27, 9, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = timeProvider.nowInstant()
        )
        // Mark Oct 26 as SKIPPED
        val exc26 = OccurrenceException(
            eventId = "e_daily_overdue",
            originalStartDateTime = LocalDateTime.of(2026, 10, 26, 9, 0),
            originalZoneId = utcZone,
            status = OccurrenceStatus.SKIPPED
        )
        exceptionRepository.saveException(exc27)
        exceptionRepository.saveException(exc26)

        // Event B has 2 overdue occurrences (Oct 24, Oct 25)
        val eventB = buildEvent(
            id = "e_b",
            title = "Bi-Weekly Task",
            startDateTime = LocalDateTime.of(2026, 10, 24, 11, 0)
        )
        eventRepository.saveEvent(eventB)

        val overdueList = getUpcomingOccurrencesUseCase.overdueOccurrences(
            asOf = LocalDateTime.of(2026, 10, 28, 12, 41),
            maxPerEvent = 4
        ).first()

        // For Event A: Oct 27 (completed) and Oct 26 (skipped) are excluded.
        // Remaining past uncompleted: Oct 25, 24, 23, 22, 21, 20.
        // Max 4 most recent must be: Oct 25, 24, 23, 22.
        val aOverdue = overdueList.filter { it.event.id == "e_daily_overdue" }
        assertEquals(4, aOverdue.size)
        val aDates = aOverdue.map { it.occurrence.effectiveStartDateTime.toLocalDate() }.toSet()
        assertTrue(aDates.contains(LocalDate.of(2026, 10, 25)))
        assertTrue(aDates.contains(LocalDate.of(2026, 10, 24)))
        assertTrue(aDates.contains(LocalDate.of(2026, 10, 23)))
        assertTrue(aDates.contains(LocalDate.of(2026, 10, 22)))
        assertFalse(aDates.contains(LocalDate.of(2026, 10, 21))) // 5th older excluded!
        assertFalse(aDates.contains(LocalDate.of(2026, 10, 20))) // 6th older excluded!

        // Event B has 1 overdue occurrence
        val bOverdue = overdueList.filter { it.event.id == "e_b" }
        assertEquals(1, bOverdue.size)
    }

    // ==========================================
    // 4. SELECTED DATE QUERY & MULTIPLE OCCURRENCES
    // ==========================================

    @Test
    fun upcomingSelectedDate_returnsAllOccurrencesOnDate_retainingMultiplePerEvent() = runTest {
        val selectedDate = LocalDate.of(2026, 11, 5)

        // Event A occurs twice on Nov 5: via daily recurrence at 10:00, and an exception or multiple events
        val eventA = buildEvent(
            "e_a", "Team Sync",
            LocalDateTime.of(2026, 11, 5, 10, 0)
        )
        val eventA2 = buildEvent(
            "e_a2", "Team Sync Part 2",
            LocalDateTime.of(2026, 11, 5, 15, 0)
        )
        val eventB = buildEvent(
            "e_b", "Design Review",
            LocalDateTime.of(2026, 11, 5, 14, 0)
        )
        val eventOtherDay = buildEvent(
            "e_other", "Other Day Task",
            LocalDateTime.of(2026, 11, 6, 10, 0)
        )

        eventRepository.saveEvent(eventA)
        eventRepository.saveEvent(eventA2)
        eventRepository.saveEvent(eventB)
        eventRepository.saveEvent(eventOtherDay)

        val dateOccurrences = getUpcomingOccurrencesUseCase.occurrencesForDate(selectedDate).first()

        assertEquals(3, dateOccurrences.size)
        val titles = dateOccurrences.map { it.event.title }
        assertEquals("Team Sync", titles[0])
        assertEquals("Design Review", titles[1])
        assertEquals("Team Sync Part 2", titles[2])
        assertTrue(dateOccurrences.none { it.event.id == "e_other" })
    }

    // ==========================================
    // 5. RECURRING EVENT EDITING & EXCEPTION PRECEDENCE
    // ==========================================

    @Test
    fun recurringEdit_mode1_thisOccurrence_createsExceptionAndRecalculatesSchedules() = runTest {
        val schedule = NotificationSchedule(
            id = "s_1",
            offsetDuration = Duration.ofMinutes(10),
            deliveryType = ReminderDeliveryType.NOTIFICATION
        )
        val series = buildEvent(
            id = "series_edit",
            title = "Morning Coffee",
            startDateTime = LocalDateTime.of(2026, 10, 28, 8, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY),
            schedules = listOf(schedule)
        )
        eventRepository.saveEvent(series)
        scheduleNotificationsUseCase(series)

        // Move Oct 29 occurrence from 08:00 to 09:30
        val targetStart = LocalDateTime.of(2026, 10, 29, 8, 0)
        val editedDraft = series.copy(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 29, 9, 30), utcZone)
        )

        val result = editRecurringSeriesUseCase(
            originalEvent = series,
            occurrenceOriginalStart = targetStart,
            occurrenceOriginalZone = utcZone,
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.THIS_OCCURRENCE
        )

        assertTrue(result is Result.Success)
        val exc = exceptionRepository.getExceptionByOccurrenceId(OccurrenceException.createId(series.id, targetStart, utcZone))
        assertNotNull(exc)
        assertEquals(LocalDateTime.of(2026, 10, 29, 9, 30), exc?.rescheduledStartDateTime)

        // Verify master event is unmodified
        val storedMaster = eventRepository.getEvent(series.id)
        assertEquals(LocalDateTime.of(2026, 10, 28, 8, 0), storedMaster?.temporal?.startLocalDateTime())
    }

    @Test
    fun recurringEdit_mode3_entireSeries_preservesExistingOccurrenceExceptionPrecedence() = runTest {
        // Daily event at 13:00 starting Oct 28
        val series = buildEvent(
            id = "series_precedence",
            title = "Daily Review",
            startDateTime = LocalDateTime.of(2026, 10, 28, 13, 0),
            rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        )
        eventRepository.saveEvent(series)

        // Pre-existing exception: Oct 29 was manually moved to 14:00
        val oct29Start = LocalDateTime.of(2026, 10, 29, 13, 0)
        val manualException = OccurrenceException(
            id = OccurrenceException.createId(series.id, oct29Start, utcZone),
            eventId = series.id,
            originalStartDateTime = oct29Start,
            originalZoneId = utcZone,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = LocalDateTime.of(2026, 10, 29, 14, 0),
            rescheduledZoneId = utcZone
        )
        exceptionRepository.saveException(manualException)

        // Now user edits ENTIRE_SERIES to change default time to 15:00
        val editedDraft = series.copy(
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 10, 28, 15, 0), utcZone)
        )

        val result = editRecurringSeriesUseCase(
            originalEvent = series,
            occurrenceOriginalStart = LocalDateTime.of(2026, 10, 28, 13, 0),
            occurrenceOriginalZone = utcZone,
            editedDraft = editedDraft,
            mode = RecurringSeriesEditMode.ENTIRE_SERIES
        )

        assertTrue(result is Result.Success)

        // Critical Section 12 rule: The manual Oct 29 exception must NOT be erased or ignored!
        val preservedException = exceptionRepository.getExceptionByOccurrenceId(manualException.id)
        assertNotNull(preservedException)
        assertEquals(LocalDateTime.of(2026, 10, 29, 14, 0), preservedException?.rescheduledStartDateTime)
    }

    // ==========================================
    // 6. RESCHEDULING HARD INVARIANT & DELIVERY
    // ==========================================

    @Test
    fun reschedule_futureReminder_cancelsOldAlarmBeforeSchedulingNew() = runTest {
        // Current time: 12:41
        // Event: 13:00, 5 min before reminder = 12:55
        val schedule = NotificationSchedule(id = "s_5m", offsetDuration = Duration.ofMinutes(5), deliveryType = ReminderDeliveryType.NOTIFICATION)
        val event = buildEvent("e_resched_fut", "Dentist", LocalDateTime.of(2026, 10, 28, 13, 0), schedules = listOf(schedule))
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 28, 13, 0),
            zoneId = utcZone,
            status = OccurrenceStatus.PENDING
        )
        // Simulate existing scheduled instance for 12:55
        val oldTriggerMillis = LocalDateTime.of(2026, 10, 28, 12, 55).atZone(utcZone).toInstant().toEpochMilli()
        val oldInstance = NotificationInstance(
            id = "inst_old_1255",
            eventId = event.id,
            scheduleId = "s_5m",
            occurrenceOriginalStartDateTime = occurrence.originalStartDateTime,
            occurrenceOriginalZoneId = occurrence.zoneId,
            triggerEpochMillis = oldTriggerMillis,
            status = NotificationInstanceStatus.SCHEDULED,
            requestCode = 1255
        )
        notificationRepository.saveInstance(oldInstance)
        alarmScheduler.scheduleAlarm(oldInstance.requestCode, oldInstance.triggerEpochMillis, NotificationConstants.ACTION_ALARM)

        // Reschedule event to 13:30 (new reminder: 13:25)
        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 10, 28, 13, 30)
        )

        assertTrue(result is Result.Success)

        // 1. Old instance cancelled and deleted
        assertNull(notificationRepository.getInstanceById("inst_old_1255"))
        assertTrue("Old alarm must be cancelled in AlarmScheduler", alarmScheduler.cancelledAlarms.any { it.requestCode == 1255 })

        // 2. New instance scheduled for 13:25
        val newTriggerMillis = LocalDateTime.of(2026, 10, 28, 13, 25).atZone(utcZone).toInstant().toEpochMilli()
        val activeInstances = notificationRepository.getAllActiveScheduled()
        assertEquals(1, activeInstances.size)
        assertEquals(newTriggerMillis, activeInstances.first().triggerEpochMillis)
        assertTrue(alarmScheduler.scheduledAlarms.any { it.triggerEpochMillis == newTriggerMillis })

        // 3. No immediate notification was delivered
        assertTrue(deliveredImmediateList.isEmpty())
    }

    @Test
    fun reschedule_pastReminderTrigger_deliversImmediatelyExactlyOnce() = runTest {
        // Current time: 12:41
        // Event moved to 12:45, reminder 5 min before -> calculated trigger = 12:40 (ALREADY PAST!)
        val schedule = NotificationSchedule(id = "s_5m", offsetDuration = Duration.ofMinutes(5), deliveryType = ReminderDeliveryType.NOTIFICATION)
        val event = buildEvent("e_past_trigger", "Quick Call", LocalDateTime.of(2026, 10, 28, 14, 0), schedules = listOf(schedule))
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 28, 14, 0),
            zoneId = utcZone,
            status = OccurrenceStatus.PENDING
        )

        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 10, 28, 12, 45)
        )

        assertTrue(result is Result.Success)

        // Required: Deliver immediately exactly once!
        assertEquals(1, deliveredImmediateList.size)
        val delivered = deliveredImmediateList.first()
        assertEquals("e_past_trigger", delivered.first.id)
        assertEquals(NotificationInstanceStatus.DELIVERED, delivered.second.status)

        // Recorded as DELIVERED in repository so it won't be resurrected
        val instInDb = notificationRepository.getInstanceById(delivered.second.id)
        assertNotNull(instInDb)
        assertEquals(NotificationInstanceStatus.DELIVERED, instInDb?.status)

        // No future alarm scheduled for the past trigger
        assertTrue(alarmScheduler.scheduledAlarms.isEmpty())
    }

    @Test
    fun reschedule_multipleMissedReminders_consolidatedIntoOneImmediateNotification() = runTest {
        // Current time: 12:41
        // Event moved to 12:45
        // Schedules: 30m before (12:15), 10m before (12:35), 5m before (12:40) -> All 3 are past!
        val s30 = NotificationSchedule(id = "s_30m", offsetDuration = Duration.ofMinutes(30), deliveryType = ReminderDeliveryType.NOTIFICATION)
        val s10 = NotificationSchedule(id = "s_10m", offsetDuration = Duration.ofMinutes(10), deliveryType = ReminderDeliveryType.NOTIFICATION)
        val s5 = NotificationSchedule(id = "s_5m", offsetDuration = Duration.ofMinutes(5), deliveryType = ReminderDeliveryType.NOTIFICATION)
        val event = buildEvent("e_multi_missed", "Urgent Meeting", LocalDateTime.of(2026, 10, 28, 16, 0), schedules = listOf(s30, s10, s5))
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 28, 16, 0),
            zoneId = utcZone,
            status = OccurrenceStatus.PENDING
        )

        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 10, 28, 12, 45)
        )

        assertTrue(result is Result.Success)

        // Critical Section 17 rule: Consolidate missed reminders into ONE immediate notification (not 3!)
        assertEquals(1, deliveredImmediateList.size)
        assertEquals(3, deliveredImmediateList.first().third) // Missed count was 3, delivered once!
    }

    @Test
    fun reschedule_afterSnooze_cancelsAndDeletesSnoozedAlarmInstance() = runTest {
        // Event at 13:00. User snoozed until 13:15.
        val event = buildEvent("e_snoozed", "Snoozed Task", LocalDateTime.of(2026, 10, 28, 13, 0))
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 28, 13, 0),
            zoneId = utcZone,
            status = OccurrenceStatus.PENDING
        )

        val snoozeMillis = LocalDateTime.of(2026, 10, 28, 13, 15).atZone(utcZone).toInstant().toEpochMilli()
        val snoozedInstance = NotificationInstance(
            id = "inst_snooze_1315",
            eventId = event.id,
            scheduleId = "s_default",
            occurrenceOriginalStartDateTime = occurrence.originalStartDateTime,
            occurrenceOriginalZoneId = occurrence.zoneId,
            triggerEpochMillis = snoozeMillis,
            status = NotificationInstanceStatus.SNOOZED,
            requestCode = 1315
        )
        notificationRepository.saveInstance(snoozedInstance)
        alarmScheduler.scheduleAlarm(snoozedInstance.requestCode, snoozeMillis, NotificationConstants.ACTION_ALARM)

        // User now reschedules event to 14:00
        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 10, 28, 14, 0)
        )

        assertTrue(result is Result.Success)

        // Critical Section 20 rule: Snoozed alarm must be cancelled and deleted
        assertNull(notificationRepository.getInstanceById("inst_snooze_1315"))
        assertTrue(alarmScheduler.cancelledAlarms.any { it.requestCode == 1315 })
    }

    @Test
    fun reschedule_whileRinging_invalidatesAndStopsActiveAlarm() = runTest {
        val event = buildEvent("e_ringing", "Ringing Alarm", LocalDateTime.of(2026, 10, 28, 12, 40))
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 28, 12, 40),
            zoneId = utcZone,
            status = OccurrenceStatus.PENDING
        )

        val ringingInstance = NotificationInstance(
            id = "inst_ringing_now",
            eventId = event.id,
            scheduleId = "s_default",
            occurrenceOriginalStartDateTime = occurrence.originalStartDateTime,
            occurrenceOriginalZoneId = occurrence.zoneId,
            triggerEpochMillis = baseInstant.toEpochMilli(),
            status = NotificationInstanceStatus.RINGING,
            requestCode = 9999
        )
        notificationRepository.saveInstance(ringingInstance)

        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 10, 28, 14, 0)
        )

        assertTrue(result is Result.Success)

        // Critical Section 21 rule: Ringing alarm must be stopped and invalidated
        assertEquals(1, stoppedActiveAlarmCount)
        assertNull(notificationRepository.getInstanceById("inst_ringing_now"))
    }

    @Test
    fun reschedule_intoThePast_derivesAsOverdueAndDoesNotAppearInNext() = runTest {
        // Current time: 12:41
        // Event rescheduled to 11:30 (in the past!)
        val event = buildEvent("e_into_past", "Missed Task", LocalDateTime.of(2026, 10, 28, 15, 0))
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 10, 28, 15, 0),
            zoneId = utcZone,
            status = OccurrenceStatus.PENDING
        )

        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 10, 28, 11, 30)
        )

        assertTrue(result is Result.Success)

        // 1. Appears as OVERDUE on Today Dashboard
        val dashboard = getDashboardOccurrencesUseCase().first()
        val overdueOnToday = dashboard.overdue.filter { it.event.id == "e_into_past" }
        assertEquals(1, overdueOnToday.size)
        assertEquals(OccurrenceStatus.OVERDUE, overdueOnToday.first().occurrence.status)

        // 2. Must NOT appear in Upcoming Next
        val nextList = getUpcomingOccurrencesUseCase.nextOccurrencePerEvent(afterDate = LocalDate.of(2026, 10, 28)).first()
        assertTrue(nextList.none { it.event.id == "e_into_past" })
    }

    @Test
    fun reschedule_timezoneAndDstSafety() = runTest {
        // Event in New York timezone (US Eastern)
        // Nov 1, 2026 is US Fall Back DST transition (EDT UTC-4 -> EST UTC-5 at 02:00)
        val event = buildEvent(
            id = "e_dst_ny",
            title = "NY Post-DST Meeting",
            startDateTime = LocalDateTime.of(2026, 11, 2, 9, 0), // 09:00 EST
            zone = nyZone,
            schedules = listOf(NotificationSchedule(id = "s_15m", offsetDuration = Duration.ofMinutes(15), deliveryType = ReminderDeliveryType.ALARM))
        )
        eventRepository.saveEvent(event)

        val occurrence = EventOccurrence(
            eventId = event.id,
            originalStartDateTime = LocalDateTime.of(2026, 11, 2, 9, 0),
            zoneId = nyZone,
            status = OccurrenceStatus.PENDING
        )

        // Reschedule to 10:00 EST on Nov 2
        val result = rescheduleOccurrenceActionUseCase(
            occurrence = occurrence,
            newStartDateTime = LocalDateTime.of(2026, 11, 2, 10, 0),
            newZoneId = nyZone
        )

        assertTrue(result is Result.Success)

        // Trigger should be 09:45 EST (UTC-5 -> 14:45 UTC)
        val expectedTriggerMillis = LocalDateTime.of(2026, 11, 2, 9, 45).atZone(nyZone).toInstant().toEpochMilli()
        val scheduled = alarmScheduler.scheduledAlarms.firstOrNull { it.triggerEpochMillis == expectedTriggerMillis }
        assertNotNull("Alarm trigger must account for NY timezone offset after DST change", scheduled)
    }
}
