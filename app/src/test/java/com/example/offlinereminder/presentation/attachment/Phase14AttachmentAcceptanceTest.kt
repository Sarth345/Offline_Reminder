package com.example.offlinereminder.presentation.attachment

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTemplateRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.usecase.ArchiveEventUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.CreateEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.DuplicateEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventDependenciesUseCase
import com.example.offlinereminder.domain.usecase.GetEventDependentsUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventRelationshipsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreArchivedEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreTrashedEventUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import com.example.offlinereminder.presentation.eventdetail.EventDetailAction
import com.example.offlinereminder.presentation.eventdetail.EventDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Phase14AttachmentAcceptanceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

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
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()

    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var deleteEventUseCase: DeleteEventUseCase
    private lateinit var trashEventUseCase: TrashEventUseCase
    private lateinit var restoreTrashedEventUseCase: RestoreTrashedEventUseCase
    private lateinit var archiveEventUseCase: ArchiveEventUseCase
    private lateinit var restoreArchivedEventUseCase: RestoreArchivedEventUseCase
    private lateinit var permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase
    private lateinit var duplicateEventUseCase: DuplicateEventUseCase
    private lateinit var createEventTemplateUseCase: CreateEventTemplateUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase
    private lateinit var completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    private lateinit var skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    private lateinit var getEventDependenciesUseCase: GetEventDependenciesUseCase
    private lateinit var getEventDependentsUseCase: GetEventDependentsUseCase
    private lateinit var getEventRelationshipsUseCase: GetEventRelationshipsUseCase

    @Before
    fun setUp() = runTest(testDispatcher) {
        eventRepository.clear()
        getEventByIdUseCase = GetEventByIdUseCase(eventRepository)
        deleteEventUseCase = DeleteEventUseCase(eventRepository)
        trashEventUseCase = TrashEventUseCase(timeProvider, eventRepository)
        restoreTrashedEventUseCase = RestoreTrashedEventUseCase(eventRepository)
        archiveEventUseCase = ArchiveEventUseCase(timeProvider, eventRepository)
        restoreArchivedEventUseCase = RestoreArchivedEventUseCase(eventRepository)
        permanentlyDeleteEventUseCase = PermanentlyDeleteEventUseCase(eventRepository)
        duplicateEventUseCase = DuplicateEventUseCase(eventRepository, timeProvider)
        createEventTemplateUseCase = CreateEventTemplateUseCase(
            templateRepository, categoryRepository, eventTypeRepository, timeProvider
        )
        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        getEventTypesUseCase = GetEventTypesUseCase(eventTypeRepository)
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        getTagsUseCase = GetTagsUseCase(tagRepository)

        val completeUseCase = CompleteOccurrenceUseCase(timeProvider)
        completeOccurrenceActionUseCase = CompleteOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            completeOccurrenceUseCase = completeUseCase,
            timeProvider = timeProvider
        )
        skipOccurrenceActionUseCase = SkipOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository
        )
        getEventDependenciesUseCase = GetEventDependenciesUseCase(eventRepository)
        getEventDependentsUseCase = GetEventDependentsUseCase(eventRepository)
        getEventRelationshipsUseCase = GetEventRelationshipsUseCase(eventRepository)
    }

    private fun createViewModel(): EventDetailViewModel {
        return EventDetailViewModel(
            getEventByIdUseCase = getEventByIdUseCase,
            deleteEventUseCase = deleteEventUseCase,
            trashEventUseCase = trashEventUseCase,
            restoreTrashedEventUseCase = restoreTrashedEventUseCase,
            archiveEventUseCase = archiveEventUseCase,
            restoreArchivedEventUseCase = restoreArchivedEventUseCase,
            permanentlyDeleteEventUseCase = permanentlyDeleteEventUseCase,
            duplicateEventUseCase = duplicateEventUseCase,
            createEventTemplateUseCase = createEventTemplateUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            getEventGroupsUseCase = getEventGroupsUseCase,
            getTagsUseCase = getTagsUseCase,
            completeOccurrenceActionUseCase = completeOccurrenceActionUseCase,
            skipOccurrenceActionUseCase = skipOccurrenceActionUseCase,
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider,
            getEventDependenciesUseCase = getEventDependenciesUseCase,
            getEventDependentsUseCase = getEventDependentsUseCase,
            getEventRelationshipsUseCase = getEventRelationshipsUseCase
        )
    }

    @Test
    fun attachmentMetadata_creationRetrievalAndSizeFormatting() = runTest(testDispatcher) {
        val attachment = AttachmentMetadata(
            id = "att-1",
            localUri = "file:///data/user/0/com.example.offlinereminder/files/attachments/report.pdf",
            fileName = "blood_test_report.pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 256 * 1024L, // 256 KB
            createdAt = timeProvider.nowInstant()
        )

        val event = Event(
            id = "event-att-1",
            title = "Doctor Appointment",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            priority = Priority.HIGH,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            attachments = listOf(attachment),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val retrieved = eventRepository.getEvent("event-att-1")
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.attachments?.size)
        val loadedAtt = retrieved?.attachments?.first()
        assertEquals("att-1", loadedAtt?.id)
        assertEquals("blood_test_report.pdf", loadedAtt?.fileName)
        assertEquals("application/pdf", loadedAtt?.mimeType)
        assertEquals(256 * 1024L, loadedAtt?.fileSizeBytes)

        // Verify size formatting helper
        assertEquals("256 KB", AttachmentHelper.formatFileSize(256 * 1024L))
        assertEquals("1.5 MB", AttachmentHelper.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("500 B", AttachmentHelper.formatFileSize(500L))
    }

    @Test
    fun attachmentRemoval_viaViewModel_updatesEventWithoutImpactingOtherState() = runTest(testDispatcher) {
        val att1 = AttachmentMetadata(
            id = "att-keep",
            localUri = "file:///files/attachments/photo.jpg",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            fileSizeBytes = 1024L,
            createdAt = timeProvider.nowInstant()
        )
        val att2 = AttachmentMetadata(
            id = "att-delete",
            localUri = "file:///files/attachments/temp.txt",
            fileName = "temp.txt",
            mimeType = "text/plain",
            fileSizeBytes = 120L,
            createdAt = timeProvider.nowInstant()
        )

        val event = Event(
            id = "event-multi-att",
            title = "Consultation",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            attachments = listOf(att1, att2),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(event)

        val vm = createViewModel()
        vm.onAction(EventDetailAction.LoadEvent("event-multi-att"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.event?.attachments?.size)

        // Remove second attachment
        vm.onAction(EventDetailAction.RemoveAttachment("att-delete"))
        testScheduler.advanceUntilIdle()

        val updated = eventRepository.getEvent("event-multi-att")
        assertNotNull(updated)
        assertEquals(1, updated?.attachments?.size)
        assertEquals("att-keep", updated?.attachments?.first()?.id)
        assertEquals("photo.jpg", updated?.attachments?.first()?.fileName)
    }

    @Test
    fun missingOrUnavailableAttachment_isHandledGracefullyWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nonExistentAttachment = AttachmentMetadata(
            id = "missing-1",
            localUri = "file:///data/user/0/com.example.offlinereminder/files/attachments/does_not_exist.pdf",
            fileName = "does_not_exist.pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 100L,
            createdAt = timeProvider.nowInstant()
        )

        // Verify isAttachmentAvailable returns false for missing file
        assertFalse(AttachmentHelper.isAttachmentAvailable(context, nonExistentAttachment))

        // Opening missing file returns Failure with meaningful message
        val openResult = AttachmentHelper.openAttachment(context, nonExistentAttachment)
        assertTrue(openResult is Result.Failure)
        val errorMsg = (openResult as Result.Failure).error.message
        assertTrue(errorMsg?.contains("Attachment file not found on device") == true)
    }

    @Test
    fun localFileImport_copiesFileToInternalStorageAndComputesMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempSource = File(context.cacheDir, "test_source.txt")
        tempSource.writeText("Offline Reminder Local Attachment Content")

        val importResult = AttachmentHelper.saveAttachmentFromUri(
            context = context,
            sourceUri = android.net.Uri.fromFile(tempSource)
        )

        assertTrue(importResult is Result.Success)
        val metadata = (importResult as Result.Success<AttachmentMetadata>).data
        assertEquals("test_source.txt", metadata.fileName)
        assertTrue(metadata.fileSizeBytes > 0)
        assertTrue(AttachmentHelper.isAttachmentAvailable(context, metadata))

        // Clean up
        AttachmentHelper.deleteAttachmentFile(context, metadata)
        assertFalse(AttachmentHelper.isAttachmentAvailable(context, metadata))
        tempSource.delete()
    }
}
