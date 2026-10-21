package com.example.offlinereminder.presentation.ui

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
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
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.usecase.AddCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.CreateEventFromTemplateUseCase
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.CreateNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.DeleteCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.DeleteNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.DetectEventConflictsUseCase
import com.example.offlinereminder.domain.usecase.EmptyTrashUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.GetEventTemplatesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetNotificationProfilesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreArchivedEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreTrashedEventUseCase
import com.example.offlinereminder.domain.usecase.SetDefaultNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import com.example.offlinereminder.domain.usecase.UpdateNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase
import com.example.offlinereminder.presentation.archive.ArchiveAction
import com.example.offlinereminder.presentation.archive.ArchiveViewModel
import com.example.offlinereminder.presentation.eventeditor.EventDateMode
import com.example.offlinereminder.presentation.eventeditor.EventEditorAction
import com.example.offlinereminder.presentation.eventeditor.EventEditorViewModel
import com.example.offlinereminder.presentation.notificationprofiles.NotificationProfilesAction
import com.example.offlinereminder.presentation.notificationprofiles.NotificationProfilesViewModel
import com.example.offlinereminder.presentation.templates.TemplatesAction
import com.example.offlinereminder.presentation.templates.TemplatesViewModel
import com.example.offlinereminder.presentation.trash.TrashAction
import com.example.offlinereminder.presentation.trash.TrashViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class Phase14EditorAndOrganizationTest {

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
    private val notificationProfileRepository = InMemoryNotificationProfileRepository()
    private val customSoundRepository = InMemoryCustomSoundRepository()

    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var updateEventUseCase: UpdateEventUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase
    private lateinit var getEventTemplatesUseCase: GetEventTemplatesUseCase
    private lateinit var getEventTemplateUseCase: GetEventTemplateUseCase
    private lateinit var createEventFromTemplateUseCase: CreateEventFromTemplateUseCase
    private lateinit var deleteEventTemplateUseCase: DeleteEventTemplateUseCase
    private lateinit var getNotificationProfilesUseCase: GetNotificationProfilesUseCase
    private lateinit var createNotificationProfileUseCase: CreateNotificationProfileUseCase
    private lateinit var updateNotificationProfileUseCase: UpdateNotificationProfileUseCase
    private lateinit var deleteNotificationProfileUseCase: DeleteNotificationProfileUseCase
    private lateinit var setDefaultNotificationProfileUseCase: SetDefaultNotificationProfileUseCase
    private lateinit var getCustomSoundsUseCase: GetCustomSoundsUseCase
    private lateinit var addCustomSoundUseCase: AddCustomSoundUseCase
    private lateinit var deleteCustomSoundUseCase: DeleteCustomSoundUseCase
    private lateinit var restoreTrashedEventUseCase: RestoreTrashedEventUseCase
    private lateinit var trashEventUseCase: TrashEventUseCase
    private lateinit var permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase
    private lateinit var emptyTrashUseCase: EmptyTrashUseCase
    private lateinit var restoreArchivedEventUseCase: RestoreArchivedEventUseCase
    private lateinit var detectEventConflictsUseCase: DetectEventConflictsUseCase

    @Before
    fun setUp() = runTest(testDispatcher) {
        eventRepository.clear()
        categoryRepository.categories.value = emptyMap()
        eventTypeRepository.types.value = emptyMap()
        eventGroupRepository.groups.value = emptyMap()
        tagRepository.tags.value = emptyMap()
        templateRepository.templates.value = emptyMap()
        notificationProfileRepository.clear()
        customSoundRepository.sounds.value = emptyMap()

        getEventByIdUseCase = GetEventByIdUseCase(eventRepository)
        val validateEventUseCase = ValidateEventUseCase()
        val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()
        val recurrenceEngine = DefaultRecurrenceEngine(timeProvider)

        createEventUseCase = CreateEventUseCase(
            eventRepository = eventRepository,
            validateEventUseCase = validateEventUseCase,
            timeProvider = timeProvider
        )
        updateEventUseCase = UpdateEventUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            validateEventUseCase = validateEventUseCase,
            timeProvider = timeProvider
        )
        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        getEventTypesUseCase = GetEventTypesUseCase(eventTypeRepository)
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        getTagsUseCase = GetTagsUseCase(tagRepository)

        getEventTemplatesUseCase = GetEventTemplatesUseCase(templateRepository)
        getEventTemplateUseCase = GetEventTemplateUseCase(templateRepository)
        createEventFromTemplateUseCase = CreateEventFromTemplateUseCase(
            templateRepository, eventRepository, categoryRepository, eventTypeRepository, timeProvider
        )
        deleteEventTemplateUseCase = DeleteEventTemplateUseCase(templateRepository)

        getNotificationProfilesUseCase = GetNotificationProfilesUseCase(notificationProfileRepository)
        createNotificationProfileUseCase = CreateNotificationProfileUseCase(notificationProfileRepository)
        updateNotificationProfileUseCase = UpdateNotificationProfileUseCase(notificationProfileRepository)
        deleteNotificationProfileUseCase = DeleteNotificationProfileUseCase(notificationProfileRepository)
        setDefaultNotificationProfileUseCase = SetDefaultNotificationProfileUseCase(notificationProfileRepository)

        getCustomSoundsUseCase = GetCustomSoundsUseCase(customSoundRepository)
        addCustomSoundUseCase = AddCustomSoundUseCase(customSoundRepository, timeProvider)
        deleteCustomSoundUseCase = DeleteCustomSoundUseCase(customSoundRepository)

        restoreTrashedEventUseCase = RestoreTrashedEventUseCase(eventRepository)
        trashEventUseCase = TrashEventUseCase(timeProvider, eventRepository)
        permanentlyDeleteEventUseCase = PermanentlyDeleteEventUseCase(eventRepository)
        emptyTrashUseCase = EmptyTrashUseCase(eventRepository)
        restoreArchivedEventUseCase = RestoreArchivedEventUseCase(eventRepository)

        detectEventConflictsUseCase = DetectEventConflictsUseCase(
            eventRepository = eventRepository,
            recurrenceEngine = recurrenceEngine,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            timeProvider = timeProvider
        )

        // Seed metadata
        categoryRepository.saveCategory(Category("cat-1", "Health", 0, null, false, 0))
        eventTypeRepository.saveEventType(
            EventType("type-1", "Checkup", "checkup", "cat-1", createdAt = timeProvider.nowInstant())
        )
    }

    // ==========================================
    // 1. Advanced Event Editor Tests
    // ==========================================

    @Test
    fun eventEditor_dateModeSwitching_andTimezoneUpdates() = runTest(testDispatcher) {
        val vm = EventEditorViewModel(
            getEventByIdUseCase = getEventByIdUseCase,
            createEventUseCase = createEventUseCase,
            updateEventUseCase = updateEventUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            getEventGroupsUseCase = getEventGroupsUseCase,
            getTagsUseCase = getTagsUseCase,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider,
            eventRepository = eventRepository
        )

        vm.onAction(EventEditorAction.InitWithDefaults(LocalDate.of(2026, 10, 25), "type-1"))
        testScheduler.advanceUntilIdle()

        // Switch to DATE_ONLY
        vm.onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_ONLY))
        testScheduler.advanceUntilIdle()

        assertEquals(EventDateMode.DATE_ONLY, vm.uiState.value.dateMode)
        assertTrue(vm.uiState.value.temporal is EventDateTime.DateOnly)

        // Switch to DATE_RANGE
        vm.onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_RANGE))
        testScheduler.advanceUntilIdle()

        assertEquals(EventDateMode.DATE_RANGE, vm.uiState.value.dateMode)
        assertTrue(vm.uiState.value.temporal is EventDateTime.DateRange)

        // Switch to DATE_RANGE_TIME
        vm.onAction(EventEditorAction.SetDateMode(EventDateMode.DATE_RANGE_TIME))
        testScheduler.advanceUntilIdle()

        assertEquals(EventDateMode.DATE_RANGE_TIME, vm.uiState.value.dateMode)
        assertTrue(vm.uiState.value.temporal is EventDateTime.DateTimeRange)

        // Timezone update
        vm.onAction(EventEditorAction.UpdateTimezone("America/New_York"))
        testScheduler.advanceUntilIdle()

        assertEquals("America/New_York", vm.uiState.value.timeZoneId)
    }

    @Test
    fun eventEditor_applyTemplate_populatesAllFields() = runTest(testDispatcher) {
        val template = EventTemplate(
            id = "template-1",
            name = "Weekly Sync Template",
            description = "Recurring meeting checklist",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            priority = Priority.HIGH,
            isAllDay = false,
            checklist = Checklist(
                title = "Agenda",
                items = listOf(ChecklistItem(id = "c1", text = "Metrics", isCompleted = false))
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        templateRepository.saveTemplate(template)

        val vm = EventEditorViewModel(
            getEventByIdUseCase = getEventByIdUseCase,
            createEventUseCase = createEventUseCase,
            updateEventUseCase = updateEventUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            getEventGroupsUseCase = getEventGroupsUseCase,
            getTagsUseCase = getTagsUseCase,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider,
            eventRepository = eventRepository,
            getEventTemplatesUseCase = getEventTemplatesUseCase,
            getEventTemplateUseCase = getEventTemplateUseCase
        )

        vm.onAction(EventEditorAction.InitWithDefaults(LocalDate.of(2026, 10, 25), null))
        testScheduler.advanceUntilIdle()

        vm.onAction(EventEditorAction.ApplyTemplate("template-1"))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Weekly Sync Template", state.title)
        assertEquals("Recurring meeting checklist", state.notes)
        assertEquals("cat-1", state.categoryId)
        assertEquals("type-1", state.eventTypeId)
        assertEquals(Priority.HIGH, state.priority)
        assertEquals(1, state.checklist?.items?.size)
        assertEquals("Metrics", state.checklist?.items?.first()?.text)
    }

    @Test
    fun eventEditor_subEvent_initializationAndSaving() = runTest(testDispatcher) {
        val parent = Event(
            id = "parent-10",
            title = "Major Project",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(parent)

        val vm = EventEditorViewModel(
            getEventByIdUseCase = getEventByIdUseCase,
            createEventUseCase = createEventUseCase,
            updateEventUseCase = updateEventUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            getEventGroupsUseCase = getEventGroupsUseCase,
            getTagsUseCase = getTagsUseCase,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider,
            eventRepository = eventRepository
        )

        vm.onAction(
            EventEditorAction.InitWithDefaults(
                initialDate = LocalDate.of(2026, 10, 25),
                initialEventTypeId = "type-1",
                parentEventId = "parent-10"
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals("parent-10", vm.uiState.value.parentEventId)
        assertEquals("Major Project", vm.uiState.value.parentEventTitle)

        vm.onAction(EventEditorAction.UpdateTitle("Milestone A"))
        vm.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isSaved)
        val allEvents = eventRepository.getAllEvents().first()
        val createdChild = allEvents.find { it.title == "Milestone A" }
        assertNotNull(createdChild)
        assertEquals("parent-10", createdChild?.parentEventId)
    }

    // ==========================================
    // 2. Templates Management Tests
    // ==========================================

    @Test
    fun templatesViewModel_loadAndCreateEventFromTemplate() = runTest(testDispatcher) {
        val template = EventTemplate(
            id = "tmpl-1",
            name = "Dental Appointment Template",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        templateRepository.saveTemplate(template)

        val vm = TemplatesViewModel(
            getEventTemplatesUseCase = getEventTemplatesUseCase,
            createEventFromTemplateUseCase = createEventFromTemplateUseCase,
            deleteEventTemplateUseCase = deleteEventTemplateUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.templates.size)
        assertEquals("Dental Appointment Template", vm.uiState.value.templates.first().name)

        // Create Event from Template
        vm.onAction(TemplatesAction.CreateEventFromTemplate("tmpl-1"))
        testScheduler.advanceUntilIdle()

        val createdId = vm.uiState.value.createdEventId
        assertNotNull(createdId)

        val createdEvent = eventRepository.getEvent(createdId!!)
        assertNotNull(createdEvent)
        assertEquals("Dental Appointment Template", createdEvent?.title)

        // Delete Template
        vm.onAction(TemplatesAction.DeleteTemplate("tmpl-1"))
        testScheduler.advanceUntilIdle()

        assertTrue(templateRepository.templates.value.isEmpty())
    }

    // ==========================================
    // 3. Trash Management Tests
    // ==========================================

    @Test
    fun trashViewModel_countdown_restore_andEmptyTrash() = runTest(testDispatcher) {
        val trashedEvent = Event(
            id = "trash-1",
            title = "Old Reminder",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = timeProvider.nowInstant().minus(java.time.Duration.ofDays(5)),
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 20)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(trashedEvent)

        val vm = TrashViewModel(
            eventRepository = eventRepository,
            restoreTrashedEventUseCase = restoreTrashedEventUseCase,
            permanentlyDeleteEventUseCase = permanentlyDeleteEventUseCase,
            emptyTrashUseCase = emptyTrashUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider
        )
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.trashedEvents.size)
        val item = state.trashedEvents.first()
        assertEquals(25, item.daysRemainingBeforePurge) // 30 - 5 = 25 days remaining

        // Restore Event
        vm.onAction(TrashAction.RestoreEvent("trash-1"))
        testScheduler.advanceUntilIdle()

        val restored = eventRepository.getEvent("trash-1")
        assertEquals(EventLifecycleStatus.ACTIVE, restored?.lifecycleStatus)
    }

    // ==========================================
    // 4. Archive Management Tests
    // ==========================================

    @Test
    fun archiveViewModel_loadAndMoveToTrash() = runTest(testDispatcher) {
        val archivedEvent = Event(
            id = "arch-1",
            title = "Completed Project",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            lifecycleStatus = EventLifecycleStatus.ARCHIVED,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 9, 15)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(archivedEvent)

        val vm = ArchiveViewModel(
            eventRepository = eventRepository,
            restoreArchivedEventUseCase = restoreArchivedEventUseCase,
            trashEventUseCase = trashEventUseCase,
            permanentlyDeleteEventUseCase = permanentlyDeleteEventUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            getEventTypesUseCase = getEventTypesUseCase,
            dispatcherProvider = dispatcherProvider
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.archivedEvents.size)

        // Move to Trash
        vm.onAction(ArchiveAction.MoveToTrash("arch-1"))
        testScheduler.advanceUntilIdle()

        val trashed = eventRepository.getEvent("arch-1")
        assertEquals(EventLifecycleStatus.TRASHED, trashed?.lifecycleStatus)
    }

    // ==========================================
    // 5. Notification Profiles & Sounds Tests
    // ==========================================

    @Test
    fun notificationProfilesViewModel_createSetDefaultAndDelete() = runTest(testDispatcher) {
        val vm = NotificationProfilesViewModel(
            getNotificationProfilesUseCase = getNotificationProfilesUseCase,
            createNotificationProfileUseCase = createNotificationProfileUseCase,
            updateNotificationProfileUseCase = updateNotificationProfileUseCase,
            deleteNotificationProfileUseCase = deleteNotificationProfileUseCase,
            setDefaultNotificationProfileUseCase = setDefaultNotificationProfileUseCase,
            getCustomSoundsUseCase = getCustomSoundsUseCase,
            addCustomSoundUseCase = addCustomSoundUseCase,
            deleteCustomSoundUseCase = deleteCustomSoundUseCase,
            dispatcherProvider = dispatcherProvider
        )
        testScheduler.advanceUntilIdle()

        // Create new profile
        vm.onAction(
            NotificationProfilesAction.CreateProfile(
                name = "High Priority Alarm",
                deliveryType = ReminderDeliveryType.ALARM,
                sound = SoundReference(SoundSourceType.DEFAULT),
                vibrationEnabled = true,
                defaultSnoozeMinutes = 5,
                isDefault = false
            )
        )
        testScheduler.advanceUntilIdle()

        val profiles = vm.uiState.value.profiles
        val created = profiles.find { it.name == "High Priority Alarm" }
        assertNotNull(created)
        assertEquals(ReminderDeliveryType.ALARM, created?.deliveryType)

        // Set as Default
        vm.onAction(NotificationProfilesAction.SetDefaultProfile(created!!.id))
        testScheduler.advanceUntilIdle()

        val updatedProfiles = vm.uiState.value.profiles
        assertTrue(updatedProfiles.find { it.id == created.id }?.isDefault == true)

        // Register custom sound
        vm.onAction(
            NotificationProfilesAction.RegisterCustomSound(
                title = "Gentle Chime",
                fileName = "chime.wav",
                mimeType = "audio/wav",
                fileSizeBytes = 12048L
            )
        )
        testScheduler.advanceUntilIdle()

        val sounds = vm.uiState.value.customSounds
        assertEquals(1, sounds.size)
        assertEquals("Gentle Chime", sounds.first().title)

        // Delete sound
        vm.onAction(NotificationProfilesAction.DeleteCustomSound(sounds.first().id))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.customSounds.isEmpty())
    }
}
