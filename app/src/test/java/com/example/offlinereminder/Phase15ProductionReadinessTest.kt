package com.example.offlinereminder

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryCustomSoundRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTemplateRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryNotificationProfileRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.backup.AttachmentBackupDto
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.BackupValidator
import com.example.offlinereminder.domain.backup.CategoryBackupDto
import com.example.offlinereminder.domain.backup.ChecklistBackupDto
import com.example.offlinereminder.domain.backup.ChecklistItemBackupDto
import com.example.offlinereminder.domain.backup.EventBackupDto
import com.example.offlinereminder.domain.backup.EventTypeBackupDto
import com.example.offlinereminder.domain.backup.NotificationScheduleBackupDto
import com.example.offlinereminder.domain.backup.TemporalBackupDto
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventFilterParams
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceEnd
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.InMemoryNotificationRepository
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.quickadd.CreateEventFromQuickAddUseCase
import com.example.offlinereminder.domain.quickadd.QuickAddParser
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.RebootRecoveryUseCase
import com.example.offlinereminder.domain.usecase.SearchEventsUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

/**
 * Phase 15 Final Production Readiness, Architecture, and End-to-End Integration Test Suite.
 * Validates the complete release candidate across all 15 project phases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Phase15ProductionReadinessTest {

    private val testDispatcher = StandardTestDispatcher()
    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val categoryRepository = InMemoryCategoryRepository()
    private val eventTypeRepository = InMemoryEventTypeRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository()
    private val tagRepository = InMemoryTagRepository()
    private val templateRepository = InMemoryEventTemplateRepository()
    private val customSoundRepository = InMemoryCustomSoundRepository()
    private val notificationProfileRepository = InMemoryNotificationProfileRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
    private val notificationRepository = InMemoryNotificationRepository()

    private val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)
    private val testAlarmScheduler = TestAlarmScheduler()

    @Before
    fun setUp() = runTest(testDispatcher) {
        eventRepository.clear()
        categoryRepository.categories.value = emptyMap()
        eventTypeRepository.types.value = emptyMap()
        eventGroupRepository.groups.value = emptyMap()
        tagRepository.tags.value = emptyMap()
        templateRepository.templates.value = emptyMap()
        customSoundRepository.sounds.value = emptyMap()
        notificationProfileRepository.clear()
        notificationRepository.clear()
        testAlarmScheduler.clear()

        // Seed foundational category and event type
        categoryRepository.saveCategory(
            Category(id = "cat-work", name = "Work", color = 0xFF2196F3.toInt(), icon = "briefcase", isCustom = false)
        )
        eventTypeRepository.saveEventType(
            EventType(id = "type-meeting", name = "Meeting", key = "meeting", categoryId = "cat-work", createdAt = timeProvider.nowInstant())
        )
    }

    // ==========================================
    // 1. Architecture & Framework Isolation
    // ==========================================

    @Test
    fun architecture_domainLayerHasZeroFrameworkDependencies() {
        val domainDir = File("src/main/java/com/example/offlinereminder/domain")
        assertTrue("Domain directory must exist", domainDir.exists())

        val violations = mutableListOf<String>()
        domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val imported = trimmed.removePrefix("import ").trim()
                        if (imported.startsWith("android.") ||
                            imported.startsWith("androidx.compose.") ||
                            imported.startsWith("androidx.room.") ||
                            imported.startsWith("androidx.lifecycle.")
                        ) {
                            violations.add("${file.name}:${index + 1} -> $imported")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("Architecture violations in domain layer:\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun architecture_presentationLayerHasZeroDirectRoomOrAlarmManagerDependencies() {
        val presentationDir = File("src/main/java/com/example/offlinereminder/presentation")
        assertTrue("Presentation directory must exist", presentationDir.exists())

        val violations = mutableListOf<String>()
        presentationDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val imported = trimmed.removePrefix("import ").trim()
                        if (imported.contains(".data.local.dao.") ||
                            imported.contains(".data.local.AppDatabase") ||
                            imported == "android.app.AlarmManager"
                        ) {
                            violations.add("${file.name}:${index + 1} -> $imported")
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("Architecture violations in presentation layer:\n" + violations.joinToString("\n"))
        }
    }

    // ==========================================
    // 2. 100% Offline & Privacy Validation
    // ==========================================

    @Test
    fun offlinePrivacy_manifestHasZeroInternetPermissionsAndOnlyMainActivityExported() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText().replace("\r\n", "\n")

        assertFalse("Must NOT declare INTERNET permission", manifestContent.contains("android.permission.INTERNET"))
        assertFalse("Must NOT declare ACCESS_NETWORK_STATE", manifestContent.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse("Must NOT declare ACCESS_WIFI_STATE", manifestContent.contains("android.permission.ACCESS_WIFI_STATE"))

        // Verify only launcher activity is exported
        assertTrue("MainActivity must be exported for launch", manifestContent.contains("android:name=\".MainActivity\"\n            android:exported=\"true\""))
        assertTrue("AlarmAlertActivity must not be exported", manifestContent.contains("android:name=\".presentation.alarm.AlarmAlertActivity\"\n            android:exported=\"false\""))
        assertTrue("AlarmPlaybackService must not be exported", manifestContent.contains("android:name=\".data.alarm.AlarmPlaybackService\"\n            android:exported=\"false\""))
    }

    @Test
    fun brandingAndPermissions_appLabeledPlanerizeWithCorrectExactAlarmAndNoWakeLock() {
        val stringsFile = File("src/main/res/values/strings.xml")
        assertTrue("strings.xml must exist", stringsFile.exists())
        val stringsContent = stringsFile.readText()
        assertTrue("app_name must be Planerize", stringsContent.contains("<string name=\"app_name\">Planerize</string>"))
        assertTrue("app_tagline must be configured", stringsContent.contains("<string name=\"app_tagline\">Plan Your Time. Organize Your Life.</string>"))

        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestContent = manifestFile.readText()
        assertTrue("Manifest application must reference @string/app_name", manifestContent.contains("android:label=\"@string/app_name\""))
        assertTrue("Manifest must declare SCHEDULE_EXACT_ALARM", manifestContent.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertFalse("Manifest must NOT declare USE_EXACT_ALARM", manifestContent.contains("android.permission.USE_EXACT_ALARM"))
        assertFalse("Manifest must NOT declare WAKE_LOCK", manifestContent.contains("android.permission.WAKE_LOCK"))
    }

    @Test
    fun offlinePrivacy_zeroNetworkLibrariesOrHttpClientsInProductionCode() {
        val mainJavaDir = File("src/main/java")
        assertTrue("src/main/java must exist", mainJavaDir.exists())

        val forbiddenTokens = listOf(
            "java.net.HttpURLConnection",
            "java.net.URL",
            "okhttp3",
            "retrofit2",
            "io.ktor",
            "com.android.volley"
        )

        val violations = mutableListOf<String>()
        mainJavaDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        for (token in forbiddenTokens) {
                            if (trimmed.contains(token)) {
                                violations.add("${file.name}:${index + 1} contains network token: $token")
                            }
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail("Network library violations found in production code:\n" + violations.joinToString("\n"))
        }
    }

    // ==========================================
    // 3. Complete Event Model Verification
    // ==========================================

    @Test
    fun eventModel_supportsAllFourTemporalModesRoundtripWithFullMetadata() = runTest(testDispatcher) {
        val now = timeProvider.nowInstant()
        val nyZone = ZoneId.of("America/New_York")

        // 1. DateOnly
        val dateOnlyEvent = Event(
            id = "evt-date-only",
            title = "All Day Conference",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 15)),
            priority = Priority.HIGH,
            checklist = Checklist(
                id = "cl-1",
                title = "Tasks",
                items = listOf(ChecklistItem("ci-1", "Review slides", false, 0))
            ),
            createdAt = now,
            updatedAt = now
        )

        // 2. DateTime
        val dateTimeEvent = Event(
            id = "evt-date-time",
            title = "One-on-One Sync",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, 11, 16, 14, 30), nyZone),
            priority = Priority.NORMAL,
            createdAt = now,
            updatedAt = now
        )

        // 3. DateRange
        val dateRangeEvent = Event(
            id = "evt-date-range",
            title = "Team Offsite Retreat",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateRange(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)),
            priority = Priority.CRITICAL,
            createdAt = now,
            updatedAt = now
        )

        // 4. DateTimeRange
        val dateTimeRangeEvent = Event(
            id = "evt-datetime-range",
            title = "Sprint Planning Session",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(2026, 11, 18, 9, 0),
                endDateTime = LocalDateTime.of(2026, 11, 18, 11, 30),
                zoneId = nyZone
            ),
            priority = Priority.NORMAL,
            attachments = listOf(
                AttachmentMetadata(
                    id = "att-1",
                    localUri = "att-1_agenda.pdf",
                    fileName = "agenda.pdf",
                    mimeType = "application/pdf",
                    fileSizeBytes = 12000L,
                    createdAt = now
                )
            ),
            notificationSchedules = listOf(
                NotificationSchedule(
                    id = "ns-1",
                    offsetDuration = Duration.ofMinutes(15),
                    triggerType = NotificationTriggerType.BEFORE_START,
                    deliveryType = ReminderDeliveryType.ALARM,
                    sound = SoundReference(type = SoundSourceType.SYSTEM)
                )
            ),
            createdAt = now,
            updatedAt = now
        )

        eventRepository.saveEvent(dateOnlyEvent)
        eventRepository.saveEvent(dateTimeEvent)
        eventRepository.saveEvent(dateRangeEvent)
        eventRepository.saveEvent(dateTimeRangeEvent)

        val retrieved1 = eventRepository.getEvent("evt-date-only")
        val retrieved2 = eventRepository.getEvent("evt-date-time")
        val retrieved3 = eventRepository.getEvent("evt-date-range")
        val retrieved4 = eventRepository.getEvent("evt-datetime-range")

        assertNotNull(retrieved1)
        assertTrue(retrieved1?.temporal is EventDateTime.DateOnly)
        assertEquals(1, retrieved1?.checklist?.items?.size)

        assertNotNull(retrieved2)
        assertTrue(retrieved2?.temporal is EventDateTime.DateTime)
        assertEquals(nyZone, (retrieved2?.temporal as EventDateTime.DateTime).zoneId)

        assertNotNull(retrieved3)
        assertTrue(retrieved3?.temporal is EventDateTime.DateRange)
        assertEquals(LocalDate.of(2026, 11, 23), (retrieved3?.temporal as EventDateTime.DateRange).endDate)

        assertNotNull(retrieved4)
        assertTrue(retrieved4?.temporal is EventDateTime.DateTimeRange)
        assertEquals(1, retrieved4?.attachments?.size)
        assertEquals(1, retrieved4?.notificationSchedules?.size)
        assertEquals(ReminderDeliveryType.ALARM, retrieved4?.notificationSchedules?.first()?.deliveryType)
    }

    // ==========================================
    // 4. Recurrence Edge Cases
    // ==========================================

    @Test
    fun recurrence_edgeCases_leapYearMonthEndAndDstTransitions() {
        val nyZone = ZoneId.of("America/New_York")
        val now = timeProvider.nowInstant()

        // 1. Leap Year: Feb 29 yearly recurrence (2024 leap year)
        val leapYearEvent = Event(
            id = "evt-leap",
            title = "Leap Day Review",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2024, Month.FEBRUARY, 29, 10, 0), utcZone),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.YEARLY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            ),
            createdAt = now,
            updatedAt = now
        )
        val occurrencesYearly = recurrenceEngine.projectOccurrences(
            event = leapYearEvent,
            from = LocalDateTime.of(2024, 1, 1, 0, 0),
            to = LocalDateTime.of(2029, 1, 1, 0, 0),
            exceptions = emptyList(),
            evaluateOverdue = false,
            timeProvider = timeProvider
        )
        assertTrue("Yearly recurrence from Feb 29 must project instances", occurrencesYearly.isNotEmpty())
        assertEquals(LocalDate.of(2024, 2, 29), occurrencesYearly.first().effectiveStartDateTime.toLocalDate())

        // 2. Month-End: Day 31 monthly recurrence
        val monthEndEvent = Event(
            id = "evt-month-end",
            title = "End of Month Review",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, Month.JANUARY, 31, 12, 0), utcZone),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                endCondition = RecurrenceEnd.Never
            ),
            createdAt = now,
            updatedAt = now
        )
        val occurrencesMonthly = recurrenceEngine.projectOccurrences(
            event = monthEndEvent,
            from = LocalDateTime.of(2026, 1, 1, 0, 0),
            to = LocalDateTime.of(2026, 5, 1, 0, 0),
            exceptions = emptyList(),
            evaluateOverdue = false,
            timeProvider = timeProvider
        )
        assertTrue(occurrencesMonthly.isNotEmpty())
        assertEquals(LocalDate.of(2026, 1, 31), occurrencesMonthly[0].effectiveStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 28), occurrencesMonthly[1].effectiveStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 31), occurrencesMonthly[2].effectiveStartDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 4, 30), occurrencesMonthly[3].effectiveStartDateTime.toLocalDate())

        // 3. DST Transition: Fall Back in America/New_York (Nov 1, 2026)
        val dstEvent = Event(
            id = "evt-dst",
            title = "Daily Morning Standup",
            categoryId = "cat-work",
            eventTypeId = "type-meeting",
            temporal = EventDateTime.DateTime(LocalDateTime.of(2026, Month.OCTOBER, 31, 9, 0), nyZone),
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                interval = 1,
                endCondition = RecurrenceEnd.Count(5)
            ),
            createdAt = now,
            updatedAt = now
        )
        val occurrencesDst = recurrenceEngine.projectOccurrences(
            event = dstEvent,
            from = LocalDateTime.of(2026, 10, 31, 0, 0),
            to = LocalDateTime.of(2026, 11, 5, 23, 59),
            exceptions = emptyList(),
            evaluateOverdue = false,
            timeProvider = timeProvider
        )
        assertEquals(5, occurrencesDst.size)
        occurrencesDst.forEach { occ ->
            assertEquals("Local start time must consistently remain 09:00 across DST", LocalTime.of(9, 0), occ.effectiveStartDateTime.toLocalTime())
            assertEquals(nyZone, occ.zoneId)
        }
    }

    // ==========================================
    // 5. Notification & Alarm Stale Recovery
    // ==========================================

    @Test
    fun notificationAndAlarm_processDeathAndStaleRecoveryResilience() = runTest(testDispatcher) {
        val rebootRecoveryUseCase = RebootRecoveryUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = testAlarmScheduler,
            timeProvider = timeProvider
        )

        val pastTriggerMillis = timeProvider.nowInstant().minus(Duration.ofHours(2)).toEpochMilli()

        // Create an alarm instance stuck in RINGING due to process death
        val deadAlarmInstance = NotificationInstance(
            id = "inst-dead-alarm",
            eventId = "evt-dead",
            occurrenceOriginalStartDateTime = LocalDateTime.of(2026, 10, 21, 8, 0),
            occurrenceOriginalZoneId = utcZone,
            scheduleId = "sched-dead",
            triggerEpochMillis = pastTriggerMillis,
            status = NotificationInstanceStatus.RINGING
        )
        notificationRepository.saveInstance(deadAlarmInstance)

        // Run startup / reboot recovery
        val recoveryResult = rebootRecoveryUseCase()
        assertTrue("Recovery must succeed", recoveryResult is Result.Success)

        val recovered = notificationRepository.getInstanceById("inst-dead-alarm")
        assertNotNull(recovered)
        // Stuck ringing alarm beyond the 10-minute timeout threshold must be marked TIMED_OUT, not left in RINGING
        assertEquals(NotificationInstanceStatus.TIMED_OUT, recovered?.status)
    }

    // ==========================================
    // 6. Full Backup / Restore Validation
    // ==========================================

    @Test
    fun backupRestore_fullVersionedRoundtripPreservesAllEntities() {
        val validator = BackupValidator()
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = true
        }

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = "2026-10-21T10:00:00Z",
            categories = listOf(
                CategoryBackupDto(id = "cat-work", name = "Work", color = 0xFF2196F3.toInt())
            ),
            eventTypes = listOf(
                EventTypeBackupDto(id = "type-meeting", name = "Meeting", key = "meeting", categoryId = "cat-work", createdAt = "2026-10-21T10:00:00Z")
            ),
            events = listOf(
                EventBackupDto(
                    id = "evt-backup-test",
                    title = "Quarterly Business Review",
                    description = "Executive review",
                    eventTypeId = "type-meeting",
                    categoryId = "cat-work",
                    priority = "HIGH",
                    lifecycleStatus = "ACTIVE",
                    isFavorite = true,
                    temporal = TemporalBackupDto(
                        type = "DATE_TIME",
                        startDate = "2026-12-10",
                        startTime = "10:00:00",
                        zoneId = "UTC"
                    ),
                    checklist = ChecklistBackupDto(
                        id = "cl-b",
                        title = "Checklist",
                        items = listOf(ChecklistItemBackupDto("cbi-1", "Financial report", true, 0))
                    ),
                    attachments = listOf(
                        AttachmentBackupDto("att-b", "report.pdf", "application/pdf", 45000L, "attachments/att-b.pdf", "2026-10-21T10:00:00Z")
                    ),
                    notificationSchedules = listOf(
                        NotificationScheduleBackupDto("ns-b", 900L, "BEFORE_START")
                    ),
                    createdAt = "2026-10-21T10:00:00Z",
                    updatedAt = "2026-10-21T10:00:00Z"
                )
            )
        )

        // Validate payload before serialization
        val validationResult = validator.validate(payload)
        assertTrue("BackupPayload must validate successfully", validationResult is Result.Success)

        // Serialize to JSON and parse back
        val jsonString = json.encodeToString(BackupPayload.serializer(), payload)
        assertTrue(jsonString.isNotBlank())

        val parsedPayload = json.decodeFromString(BackupPayload.serializer(), jsonString)
        assertEquals(payload.backupVersion, parsedPayload.backupVersion)
        assertEquals(1, parsedPayload.events.size)

        val restoredEvent = parsedPayload.events.first()
        assertEquals("Quarterly Business Review", restoredEvent.title)
        assertEquals("HIGH", restoredEvent.priority)
        assertTrue(restoredEvent.isFavorite)
        assertEquals(1, restoredEvent.checklist?.items?.size)
        assertEquals(1, restoredEvent.attachments.size)
        assertEquals("report.pdf", restoredEvent.attachments.first().fileName)
    }

    // ==========================================
    // 7. Productivity: Quick Add & Advanced Search
    // ==========================================

    @Test
    fun productivity_quickAddAndSearch_handleComplexInputsDeterministically() = runTest(testDispatcher) {
        val quickAddParser = QuickAddParser(
            timeProvider = timeProvider
        )
        val validateEventUseCase = ValidateEventUseCase()
        val createEventUseCase = CreateEventUseCase(
            eventRepository = eventRepository,
            validateEventUseCase = validateEventUseCase,
            timeProvider = timeProvider
        )
        val createFromQuickAddUseCase = CreateEventFromQuickAddUseCase(
            createEventUseCase = createEventUseCase,
            categoryRepository = categoryRepository,
            eventTypeRepository = eventTypeRepository,
            eventGroupRepository = eventGroupRepository,
            tagRepository = tagRepository,
            timeProvider = timeProvider
        )

        // Parse: "Design Review tomorrow 10:00 !high"
        val draft = quickAddParser.parse("Design Review tomorrow 10:00 !high")
        assertEquals("Design Review", draft.title)
        assertEquals(Priority.HIGH, draft.priority)

        val quickAddResult = createFromQuickAddUseCase(draft)
        assertTrue(quickAddResult is Result.Success)
        val createdEvent = (quickAddResult as Result.Success<Event>).data
        assertEquals("Design Review", createdEvent.title)
        assertEquals(Priority.HIGH, createdEvent.priority)

        // Search verification
        val searchUseCase = SearchEventsUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
        val searchResults = searchUseCase(
            EventFilterParams(
                query = "Design",
                priorities = setOf(Priority.HIGH)
            )
        ).first()

        assertEquals(1, searchResults.size)
        assertEquals(createdEvent.id, searchResults.first().id)
    }
}
