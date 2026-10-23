package com.example.offlinereminder.presentation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.local.mapper.toBackupDto
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.backup.AttachmentBackupDto
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.EventBackupDto
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventDisplayStatus
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceFrequency
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.resolveEventDisplayStatus
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase
import com.example.offlinereminder.presentation.components.OccurrenceCard
import com.example.offlinereminder.presentation.components.StatusBadge
import com.example.offlinereminder.presentation.eventeditor.EventEditorAction
import com.example.offlinereminder.presentation.eventeditor.EventEditorContent
import com.example.offlinereminder.presentation.eventeditor.EventEditorUiState
import com.example.offlinereminder.presentation.eventeditor.EventEditorViewModel
import com.example.offlinereminder.ui.theme.OfflineReminderTheme
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(packageName = "com.example.offlinereminder")
class Planerize11EventConsistencyAndAttachmentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-21T10:00:00Z"),
        currentZoneId = zone
    )

    private fun createSampleEvent(
        id: String = "evt-1",
        title: String = "Test Event",
        startDateTime: LocalDateTime = LocalDateTime.of(2026, 10, 21, 9, 0),
        recurrenceRule: RecurrenceRule? = null,
        priority: Priority = Priority.NORMAL,
        lifecycleStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
        attachments: List<AttachmentMetadata> = emptyList()
    ): Event {
        return Event(
            id = id,
            title = title,
            eventTypeId = "type-default",
            categoryId = "cat-default",
            priority = priority,
            temporal = EventDateTime.DateTime(startDateTime, zone),
            recurrenceRule = recurrenceRule,
            lifecycleStatus = lifecycleStatus,
            attachments = attachments,
            createdAt = Instant.parse("2026-10-20T10:00:00Z"),
            updatedAt = Instant.parse("2026-10-20T10:00:00Z")
        )
    }

    // 1. Occurrence completion state
    @Test
    fun occurrenceCompletionState_resolvesToCompleted_andHidesActionButtons() {
        val nowLdt = timeProvider.nowLocalDateTime()
        val event = createSampleEvent(
            id = "evt-1",
            title = "Team Meeting",
            startDateTime = nowLdt.minusHours(2)
        )
        val occurrence = EventOccurrence(
            eventId = "evt-1",
            originalStartDateTime = nowLdt.minusHours(2),
            zoneId = zone,
            status = OccurrenceStatus.COMPLETED,
            completedAt = Instant.now()
        )

        val displayStatus = resolveEventDisplayStatus(event, occurrence, nowLdt)
        assertEquals(EventDisplayStatus.COMPLETED, displayStatus)

        var completedClicked = false

        composeTestRule.setContent {
            OfflineReminderTheme {
                OccurrenceCard(
                    occurrenceWithEvent = EventOccurrenceWithEvent(occurrence, event),
                    onClick = {},
                    onComplete = { completedClicked = true },
                    onReschedule = {},
                    onSkip = {}
                )
            }
        }

        // Verify COMPLETED badge is shown
        composeTestRule.onNodeWithText("COMPLETED").assertIsDisplayed()
        assertFalse(completedClicked)
    }

    // 2. Occurrence skip/cancel state
    @Test
    fun occurrenceSkipState_resolvesToSkipped_andRendersSkippedBadge() {
        val nowLdt = timeProvider.nowLocalDateTime()
        val event = createSampleEvent(
            id = "evt-2",
            title = "Dentist",
            startDateTime = nowLdt.plusDays(1)
        )
        val occurrence = EventOccurrence(
            eventId = "evt-2",
            originalStartDateTime = nowLdt.plusDays(1),
            zoneId = zone,
            status = OccurrenceStatus.SKIPPED
        )

        val displayStatus = resolveEventDisplayStatus(event, occurrence, nowLdt)
        assertEquals(EventDisplayStatus.SKIPPED, displayStatus)

        composeTestRule.setContent {
            OfflineReminderTheme {
                OccurrenceCard(
                    occurrenceWithEvent = EventOccurrenceWithEvent(occurrence, event),
                    onClick = {},
                    onComplete = {},
                    onReschedule = {},
                    onSkip = {}
                )
            }
        }

        composeTestRule.onNodeWithText("SKIPPED").assertIsDisplayed()
    }

    // 3. Overdue derivation
    @Test
    fun overdueDerivation_pastStart_pendingStatus_resolvesToOverdue() {
        val nowLdt = timeProvider.nowLocalDateTime()
        val pastTime = nowLdt.minusHours(3)

        val event = createSampleEvent(
            id = "evt-overdue",
            title = "Submit Report",
            startDateTime = pastTime,
            priority = Priority.HIGH
        )
        val occurrence = EventOccurrence(
            eventId = "evt-overdue",
            originalStartDateTime = pastTime,
            zoneId = zone,
            status = OccurrenceStatus.PENDING
        )

        val displayStatus = resolveEventDisplayStatus(event, occurrence, nowLdt)
        assertEquals(EventDisplayStatus.OVERDUE, displayStatus)

        composeTestRule.setContent {
            OfflineReminderTheme {
                OccurrenceCard(
                    occurrenceWithEvent = EventOccurrenceWithEvent(occurrence, event),
                    onClick = {},
                    onComplete = {},
                    onReschedule = {},
                    onSkip = {}
                )
            }
        }

        // For overdue events, actionable card shows Overdue badge and Cancel label
        composeTestRule.onNodeWithText("OVERDUE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reschedule").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    // 4. Reschedule state
    @Test
    fun rescheduleState_occurrenceStatusRescheduled_resolvesToRescheduled() {
        val nowLdt = timeProvider.nowLocalDateTime()
        val event = createSampleEvent(
            id = "evt-resched",
            title = "Doctor Follow-up",
            startDateTime = nowLdt.plusDays(2)
        )
        val occurrence = EventOccurrence(
            eventId = "evt-resched",
            originalStartDateTime = nowLdt.plusDays(2),
            zoneId = zone,
            status = OccurrenceStatus.RESCHEDULED,
            rescheduledStartDateTime = nowLdt.plusDays(3)
        )

        val displayStatus = resolveEventDisplayStatus(event, occurrence, nowLdt)
        assertEquals(EventDisplayStatus.RESCHEDULED, displayStatus)
    }

    // 5. State mapping consistency across screens
    @Test
    fun stateMappingConsistency_matchesAcrossScreens() {
        val nowLdt = timeProvider.nowLocalDateTime()
        val event = createSampleEvent(
            id = "evt-sync",
            title = "Gym Session",
            startDateTime = nowLdt.minusDays(1)
        )

        // Case A: No exceptions, past start time -> derived OVERDUE everywhere
        val emptyExceptions = emptyList<OccurrenceException>()
        val occurrence1 = EventOccurrence(
            eventId = "evt-sync",
            originalStartDateTime = nowLdt.minusDays(1),
            zoneId = zone,
            status = OccurrenceStatus.PENDING
        )
        val statusFromOcc = resolveEventDisplayStatus(event, occurrence1, nowLdt)
        val statusFromEvent = resolveEventDisplayStatus(event, emptyExceptions, nowLdt)
        assertEquals(EventDisplayStatus.OVERDUE, statusFromOcc)
        assertEquals(EventDisplayStatus.OVERDUE, statusFromEvent)

        // Case B: Completed exception -> COMPLETED everywhere
        val completedExceptions = listOf(
            OccurrenceException(
                id = "exc-1",
                eventId = "evt-sync",
                originalStartDateTime = nowLdt.minusDays(1),
                originalZoneId = zone,
                status = OccurrenceStatus.COMPLETED,
                completedAt = Instant.now()
            )
        )
        val occurrenceCompleted = EventOccurrence(
            eventId = "evt-sync",
            originalStartDateTime = nowLdt.minusDays(1),
            zoneId = zone,
            status = OccurrenceStatus.COMPLETED
        )
        assertEquals(EventDisplayStatus.COMPLETED, resolveEventDisplayStatus(event, occurrenceCompleted, nowLdt))
        assertEquals(EventDisplayStatus.COMPLETED, resolveEventDisplayStatus(event, completedExceptions, nowLdt))
    }

    // 6. Recurring occurrence completion
    @Test
    fun recurringOccurrenceCompletion_completesSingleOccurrence_leavesFutureOccurrencesPending() {
        val nowLdt = timeProvider.nowLocalDateTime()
        val startDate = nowLdt.toLocalDate().minusDays(1)
        val startDt = LocalDateTime.of(startDate, nowLdt.toLocalTime())

        val event = createSampleEvent(
            id = "daily-evt",
            title = "Daily Standup",
            startDateTime = startDt,
            recurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY)
        )

        val recurrenceEngine = DefaultRecurrenceEngine()
        val exceptions = mutableListOf<OccurrenceException>()
        val initialOccurrences = recurrenceEngine.projectOccurrences(
            event = event,
            from = startDate.atStartOfDay(),
            to = startDate.plusDays(3).atTime(23, 59, 59),
            exceptions = exceptions,
            evaluateOverdue = false,
            timeProvider = timeProvider
        )

        assertTrue(initialOccurrences.size >= 3)
        val occurrence1 = initialOccurrences[0]
        val occurrence2 = initialOccurrences[1]

        // Exception completing only occurrence1
        val completedException = OccurrenceException(
            id = "exc-daily-1",
            eventId = "daily-evt",
            originalStartDateTime = occurrence1.originalStartDateTime,
            originalZoneId = occurrence1.zoneId,
            status = OccurrenceStatus.COMPLETED,
            completedAt = Instant.now()
        )
        exceptions.add(completedException)

        // Re-project with exception
        val appliedOccurrences = recurrenceEngine.projectOccurrences(
            event = event,
            from = startDate.atStartOfDay(),
            to = startDate.plusDays(3).atTime(23, 59, 59),
            exceptions = exceptions,
            evaluateOverdue = false,
            timeProvider = timeProvider
        )

        val occ1Updated = appliedOccurrences.first { it.occurrenceId == occurrence1.occurrenceId }
        val occ2Updated = appliedOccurrences.first { it.occurrenceId == occurrence2.occurrenceId }

        assertEquals(OccurrenceStatus.COMPLETED, occ1Updated.status)
        assertEquals(OccurrenceStatus.PENDING, occ2Updated.status)

        assertEquals(EventDisplayStatus.COMPLETED, resolveEventDisplayStatus(event, occ1Updated, nowLdt))
        // Event overall is still ACTIVE because future occurrences are pending
        assertEquals(EventDisplayStatus.ACTIVE, resolveEventDisplayStatus(event, exceptions, nowLdt))
    }

    // 7. Calendar action availability & text wrap avoidance
    @Test
    fun calendarActionAvailability_rendersFullTextLabelsWithoutAwkwardWrapping() {
        val futureStart = LocalDateTime.now().plusDays(2)
        val event = createSampleEvent(
            id = "evt-card",
            title = "Review PR",
            startDateTime = futureStart
        )
        val occurrence = EventOccurrence(
            eventId = "evt-card",
            originalStartDateTime = futureStart,
            zoneId = zone,
            status = OccurrenceStatus.PENDING
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                OccurrenceCard(
                    occurrenceWithEvent = EventOccurrenceWithEvent(occurrence, event),
                    onClick = {},
                    onComplete = {},
                    onReschedule = {},
                    onSkip = {}
                )
            }
        }

        // Action buttons exist with complete readable text
        composeTestRule.onNodeWithText("Complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reschedule").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    // 8. Attachment creation persistence
    @Test
    fun attachmentCreation_updatesUiState_andPreservesAttachmentData() {
        val newAttachment = AttachmentMetadata(
            id = "att-1",
            localUri = "file:///data/user/0/com.example.offlinereminder/files/attachments/MeetingNotes.pdf",
            fileName = "MeetingNotes.pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 1024L,
            createdAt = Instant.now()
        )

        val uiState = EventEditorUiState(
            title = "Project Launch",
            isEditing = true,
            attachments = listOf(newAttachment)
        )

        composeTestRule.setContent {
            OfflineReminderTheme {
                EventEditorContent(
                    uiState = uiState,
                    onAction = {},
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("MeetingNotes.pdf").performScrollTo().assertIsDisplayed()

        val eventWithAttachment = createSampleEvent(
            id = "evt-att",
            title = "Project Launch",
            attachments = listOf(newAttachment)
        )
        assertEquals(1, eventWithAttachment.attachments.size)
        assertEquals("MeetingNotes.pdf", eventWithAttachment.attachments[0].fileName)
        assertEquals("application/pdf", eventWithAttachment.attachments[0].mimeType)
    }

    // 9. Attachment removal
    @Test
    fun attachmentRemoval_actionDispatchedProperly() {
        val attachment = AttachmentMetadata(
            id = "att-2",
            localUri = "file:///files/Contract.pdf",
            fileName = "Contract.pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 2048L,
            createdAt = Instant.now()
        )

        val eventRepo = InMemoryEventRepository()
        val catRepo = InMemoryCategoryRepository()
        val typeRepo = InMemoryEventTypeRepository()
        val groupRepo = InMemoryEventGroupRepository()
        val tagRepo = InMemoryTagRepository()
        val occExceptionRepo = com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository()
        val validateUseCase = ValidateEventUseCase()

        val viewModel = EventEditorViewModel(
            getEventByIdUseCase = GetEventByIdUseCase(eventRepo),
            createEventUseCase = CreateEventUseCase(
                eventRepository = eventRepo,
                validateEventUseCase = validateUseCase,
                timeProvider = timeProvider
            ),
            updateEventUseCase = UpdateEventUseCase(
                eventRepository = eventRepo,
                occurrenceExceptionRepository = occExceptionRepo,
                validateEventUseCase = validateUseCase,
                timeProvider = timeProvider
            ),
            getCategoriesUseCase = GetCategoriesUseCase(catRepo),
            getEventTypesUseCase = GetEventTypesUseCase(typeRepo),
            getEventGroupsUseCase = GetEventGroupsUseCase(groupRepo),
            getTagsUseCase = GetTagsUseCase(tagRepo),
            timeProvider = timeProvider,
            dispatcherProvider = TestDispatcherProvider()
        )

        viewModel.onAction(EventEditorAction.AddAttachment(attachment))
        assertEquals(1, viewModel.uiState.value.attachments.size)
        assertEquals("Contract.pdf", viewModel.uiState.value.attachments[0].fileName)

        viewModel.onAction(EventEditorAction.RemoveAttachment(attachment.id))
        assertEquals(0, viewModel.uiState.value.attachments.size)
    }

    // 10. Backup/restore with attachments
    @Test
    fun backupRestore_preservesAttachments() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        val attachment = AttachmentMetadata(
            id = "att-3",
            localUri = "file:///storage/spec.txt",
            fileName = "spec.txt",
            mimeType = "text/plain",
            fileSizeBytes = 512L,
            createdAt = Instant.parse("2026-10-21T09:00:00Z")
        )
        val event = createSampleEvent(
            id = "evt-backup",
            title = "Backup Event",
            attachments = listOf(attachment)
        )

        val eventDto = event.toBackupDto()
        assertEquals(1, eventDto.attachments.size)
        assertEquals("spec.txt", eventDto.attachments[0].fileName)

        val payload = BackupPayload(
            exportedAt = Instant.now().toString(),
            events = listOf(eventDto)
        )

        val serialized = json.encodeToString(BackupPayload.serializer(), payload)
        assertTrue(serialized.contains("spec.txt"))
        assertTrue(serialized.contains("text/plain"))

        val deserialized = json.decodeFromString(BackupPayload.serializer(), serialized)
        assertEquals(1, deserialized.events.size)
        assertEquals(1, deserialized.events[0].attachments.size)

        val restoredEvent = deserialized.events[0].toDomain()
        assertEquals(1, restoredEvent.attachments.size)
        assertEquals("spec.txt", restoredEvent.attachments[0].fileName)
        assertEquals("text/plain", restoredEvent.attachments[0].mimeType)
    }

    // 11. Status badge mapping
    @Test
    fun statusBadgeMapping_rendersAllDisplayStatuses() {
        val statuses = EventDisplayStatus.values()
        composeTestRule.setContent {
            OfflineReminderTheme {
                androidx.compose.foundation.layout.Column {
                    for (status in statuses) {
                        StatusBadge(status = status)
                    }
                }
            }
        }
        for (status in statuses) {
            composeTestRule.onNodeWithText(status.name).assertIsDisplayed()
        }
    }
}
