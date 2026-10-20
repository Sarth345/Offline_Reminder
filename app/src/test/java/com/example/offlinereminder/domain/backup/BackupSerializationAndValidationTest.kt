package com.example.offlinereminder.domain.backup

import com.example.offlinereminder.core.result.Result
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupSerializationAndValidationTest {

    private val validator = BackupValidator()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    private lateinit var samplePayload: BackupPayload

    @Before
    fun setUp() {
        val cat = CategoryBackupDto(id = "cat-1", name = "Work", sortOrder = 1)
        val type = EventTypeBackupDto(id = "type-1", name = "Meeting", key = "meeting", categoryId = "cat-1", createdAt = "2026-10-20T10:00:00Z")
        val group = GroupBackupDto(id = "grp-1", name = "Project X", createdAt = "2026-10-20T10:00:00Z", updatedAt = "2026-10-20T10:00:00Z")
        val tag = TagBackupDto(id = "tag-1", name = "urgent", normalizedName = "urgent")
        val event = EventBackupDto(
            id = "evt-1",
            title = "Sprint Planning",
            description = "Bi-weekly sprint planning",
            eventTypeId = "type-1",
            categoryId = "cat-1",
            priority = "HIGH",
            lifecycleStatus = "ACTIVE",
            temporal = TemporalBackupDto(
                type = "DATE_TIME",
                startDate = "2026-10-25",
                startTime = "09:00:00",
                zoneId = "UTC"
            ),
            groupIds = listOf("grp-1"),
            tagIds = listOf("tag-1"),
            recurrenceRule = RecurrenceRuleBackupDto(
                frequency = "WEEKLY",
                interval = 2,
                daysOfWeek = listOf("MONDAY")
            ),
            notificationSchedules = listOf(
                NotificationScheduleBackupDto(id = "notif-1", offsetSeconds = 900, triggerType = "BEFORE_START")
            ),
            checklist = ChecklistBackupDto(
                id = "chk-1",
                title = "Agenda",
                items = listOf(ChecklistItemBackupDto(id = "item-1", text = "Review backlog", isCompleted = false))
            ),
            createdAt = "2026-10-20T10:00:00Z",
            updatedAt = "2026-10-20T10:00:00Z"
        )

        samplePayload = BackupPayload(
            backupVersion = 1,
            appVersion = "1.0",
            exportedAt = "2026-10-20T10:00:00Z",
            categories = listOf(cat),
            eventTypes = listOf(type),
            groups = listOf(group),
            tags = listOf(tag),
            events = listOf(event)
        )
    }

    @Test
    fun serializeAndDeserialize_preservesAllPayloadFields() {
        val jsonStr = json.encodeToString(BackupPayload.serializer(), samplePayload)
        assertTrue(jsonStr.contains("Sprint Planning"))
        assertTrue(jsonStr.contains("Project X"))

        val deserialized = json.decodeFromString(BackupPayload.serializer(), jsonStr)
        assertEquals(samplePayload.backupVersion, deserialized.backupVersion)
        assertEquals(samplePayload.events.size, deserialized.events.size)
        assertEquals(samplePayload.events[0].title, deserialized.events[0].title)
        assertEquals(samplePayload.events[0].temporal.startDate, deserialized.events[0].temporal.startDate)
        assertEquals(samplePayload.events[0].groupIds, deserialized.events[0].groupIds)
        assertEquals(samplePayload.events[0].checklist?.items?.size, deserialized.events[0].checklist?.items?.size)
    }

    @Test
    fun validate_validPayload_returnsSuccess() {
        val result = validator.validate(samplePayload)
        assertTrue(result is Result.Success)
    }

    @Test
    fun validate_unsupportedFutureVersion_returnsFailure() {
        val futurePayload = samplePayload.copy(backupVersion = 99)
        val result = validator.validate(futurePayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is UnsupportedBackupVersionException)
    }

    @Test
    fun validate_blankEventId_returnsFailure() {
        val badEvent = samplePayload.events[0].copy(id = "   ")
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("Event ID cannot be blank"))
    }

    @Test
    fun validate_duplicateEventId_returnsFailure() {
        val duplicateEvents = listOf(samplePayload.events[0], samplePayload.events[0].copy(title = "Another"))
        val badPayload = samplePayload.copy(events = duplicateEvents)
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("Duplicate event ID"))
    }

    @Test
    fun validate_missingCategoryReference_returnsFailure() {
        val badEvent = samplePayload.events[0].copy(categoryId = "non-existent-cat")
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("non-existent category ID"))
    }

    @Test
    fun validate_missingGroupReference_returnsFailure() {
        val badEvent = samplePayload.events[0].copy(groupIds = listOf("ghost-group"))
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("non-existent group ID"))
    }

    @Test
    fun validate_selfReferencingDependency_returnsFailure() {
        val badDep = EventDependencyBackupDto(sourceEventId = "evt-1", targetEventId = "evt-1")
        val badPayload = samplePayload.copy(dependencies = listOf(badDep))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("Dependency cannot have identical source and target"))
    }

    @Test
    fun validate_dependencyReferencingMissingEvent_returnsFailure() {
        val badDep = EventDependencyBackupDto(sourceEventId = "evt-1", targetEventId = "missing-evt")
        val badPayload = samplePayload.copy(dependencies = listOf(badDep))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("missing target event"))
    }

    @Test
    fun validate_malformedDate_returnsFailure() {
        val badTemporal = samplePayload.events[0].temporal.copy(startDate = "not-a-date")
        val badEvent = samplePayload.events[0].copy(temporal = badTemporal)
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("malformed date/time"))
    }

    @Test
    fun validate_endDateBeforeStartDate_returnsFailure() {
        val badTemporal = samplePayload.events[0].temporal.copy(
            type = "DATE_RANGE",
            startDate = "2026-10-25",
            endDate = "2026-10-20"
        )
        val badEvent = samplePayload.events[0].copy(temporal = badTemporal)
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("has end date before start date"))
    }

    @Test
    fun validate_negativeNotificationOffset_returnsFailure() {
        val badNotif = NotificationScheduleBackupDto(id = "notif-x", offsetSeconds = -60)
        val badEvent = samplePayload.events[0].copy(notificationSchedules = listOf(badNotif))
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("negative notification offset"))
    }

    @Test
    fun validate_invalidRecurrenceInterval_returnsFailure() {
        val badRule = RecurrenceRuleBackupDto(frequency = "DAILY", interval = 0)
        val badEvent = samplePayload.events[0].copy(recurrenceRule = badRule)
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("invalid recurrence interval"))
    }

    @Test
    fun validate_invalidLifecycleStatus_returnsFailure() {
        val badEvent = samplePayload.events[0].copy(lifecycleStatus = "COMPLETED")
        val badPayload = samplePayload.copy(events = listOf(badEvent))
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("invalid lifecycleStatus"))
    }

    @Test
    fun serializeAndDeserialize_dateTimeRange_preservesRangeAndTimeZone() {
        val rangeTemporal = TemporalBackupDto(
            type = "DATE_TIME_RANGE",
            startDate = "2026-10-25",
            startTime = "09:00:00",
            endDate = "2026-10-27",
            endTime = "17:30:00",
            zoneId = "America/New_York"
        )
        val eventWithRange = samplePayload.events[0].copy(
            id = "evt-range",
            temporal = rangeTemporal
        )
        val payloadWithRange = samplePayload.copy(events = listOf(eventWithRange))

        val jsonStr = json.encodeToString(BackupPayload.serializer(), payloadWithRange)
        val deserialized = json.decodeFromString(BackupPayload.serializer(), jsonStr)

        val restoredTemporal = deserialized.events[0].temporal
        assertEquals("DATE_TIME_RANGE", restoredTemporal.type)
        assertEquals("2026-10-25", restoredTemporal.startDate)
        assertEquals("09:00:00", restoredTemporal.startTime)
        assertEquals("2026-10-27", restoredTemporal.endDate)
        assertEquals("17:30:00", restoredTemporal.endTime)
        assertEquals("America/New_York", restoredTemporal.zoneId)
    }

    @Test
    fun serializeAndDeserialize_withSettingsDto_preservesAllSettingsFields() {
        val customSettings = SettingsBackupDto(
            themeMode = "DARK",
            use24HourFormat = true,
            dateFormat = "DMY",
            firstDayOfWeek = "MONDAY",
            defaultEventPriority = "CRITICAL",
            defaultEventDurationMinutes = 45,
            defaultSnoozeDurationMinutes = 20,
            soundEnabled = false,
            vibrationEnabled = false,
            quietHours = QuietHoursBackupDto(
                enabled = true,
                startLocalTime = "23:00",
                endLocalTime = "07:30",
                policy = "SILENT",
                allowCritical = false
            )
        )
        val payloadWithSettings = samplePayload.copy(settings = customSettings)

        val jsonStr = json.encodeToString(BackupPayload.serializer(), payloadWithSettings)
        val deserialized = json.decodeFromString(BackupPayload.serializer(), jsonStr)

        assertEquals("DARK", deserialized.settings.themeMode)
        assertEquals(true, deserialized.settings.use24HourFormat)
        assertEquals("DMY", deserialized.settings.dateFormat)
        assertEquals("CRITICAL", deserialized.settings.defaultEventPriority)
        assertEquals(45, deserialized.settings.defaultEventDurationMinutes)
        assertEquals(20, deserialized.settings.defaultSnoozeDurationMinutes)
        assertEquals(false, deserialized.settings.soundEnabled)
        assertEquals(true, deserialized.settings.quietHours.enabled)
        assertEquals("23:00", deserialized.settings.quietHours.startLocalTime)
        assertEquals("07:30", deserialized.settings.quietHours.endLocalTime)
        assertEquals("SILENT", deserialized.settings.quietHours.policy)
    }

    @Test
    fun validate_invalidThemeMode_returnsFailure() {
        val badSettings = SettingsBackupDto(themeMode = "RAINBOW")
        val badPayload = samplePayload.copy(settings = badSettings)
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("themeMode", ignoreCase = true))
    }

    @Test
    fun validate_invalidDateFormat_returnsFailure() {
        val badSettings = SettingsBackupDto(dateFormat = "INVALID_FORMAT")
        val badPayload = samplePayload.copy(settings = badSettings)
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("dateFormat", ignoreCase = true))
    }

    @Test
    fun validate_invalidQuietHoursFormat_returnsFailure() {
        val badSettings = SettingsBackupDto(
            quietHours = QuietHoursBackupDto(startLocalTime = "25:99")
        )
        val badPayload = samplePayload.copy(settings = badSettings)
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("QuietHours has malformed time", ignoreCase = true))
    }

    @Test
    fun validate_negativeDefaultEventDuration_returnsFailure() {
        val badSettings = SettingsBackupDto(defaultEventDurationMinutes = -15)
        val badPayload = samplePayload.copy(settings = badSettings)
        val result = validator.validate(badPayload)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message!!.contains("defaultEventDurationMinutes must be positive", ignoreCase = true))
    }
}
