package com.example.offlinereminder.presentation.eventeditor

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.repository.SettingsRepository
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private val utcZone = ZoneId.of("UTC")
    private val timeProvider = TestTimeProvider(
        currentInstant = Instant.parse("2026-10-20T12:00:00Z"),
        currentZoneId = utcZone
    )

    private val eventRepository = InMemoryEventRepository()
    private val categoryRepository = InMemoryCategoryRepository()
    private val eventTypeRepository = InMemoryEventTypeRepository()
    private val eventGroupRepository = InMemoryEventGroupRepository()
    private val tagRepository = InMemoryTagRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()

    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var updateEventUseCase: UpdateEventUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase

    @Before
    fun setUp() {
        eventRepository.clear()
        categoryRepository.categories.value = emptyMap()
        eventTypeRepository.types.value = emptyMap()
        eventGroupRepository.groups.value = emptyMap()
        tagRepository.tags.value = emptyMap()

        getEventByIdUseCase = GetEventByIdUseCase(eventRepository)
        val validateUseCase = ValidateEventUseCase()
        createEventUseCase = CreateEventUseCase(
            eventRepository = eventRepository,
            validateEventUseCase = validateUseCase,
            timeProvider = timeProvider
        )
        updateEventUseCase = UpdateEventUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            validateEventUseCase = validateUseCase,
            timeProvider = timeProvider
        )
        getCategoriesUseCase = GetCategoriesUseCase(categoryRepository)
        getEventTypesUseCase = GetEventTypesUseCase(eventTypeRepository)
        getEventGroupsUseCase = GetEventGroupsUseCase(eventGroupRepository)
        getTagsUseCase = GetTagsUseCase(tagRepository)
    }

    private fun createViewModel(
        settingsRepository: SettingsRepository? = null
    ): EventEditorViewModel = EventEditorViewModel(
        getEventByIdUseCase = getEventByIdUseCase,
        createEventUseCase = createEventUseCase,
        updateEventUseCase = updateEventUseCase,
        getCategoriesUseCase = getCategoriesUseCase,
        getEventTypesUseCase = getEventTypesUseCase,
        getEventGroupsUseCase = getEventGroupsUseCase,
        getTagsUseCase = getTagsUseCase,
        timeProvider = timeProvider,
        dispatcherProvider = dispatcherProvider,
        settingsRepository = settingsRepository
    )

    @Test
    fun saveEvent_blankTitle_setsValidationError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.UpdateTitle("   "))
        viewModel.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Event title must not be blank", state.validationError)
        assertFalse(state.isSaved)
    }

    @Test
    fun saveEvent_validNewEvent_savesSuccessfully() = runTest(testDispatcher) {
        categoryRepository.saveCategory(Category("cat_1", "General", 0, null, false, 0))
        eventTypeRepository.saveEventType(EventType("t_reminder", "Reminder", "reminder", "cat_1", createdAt = timeProvider.nowInstant()))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.UpdateTitle("Buy Milk"))
        viewModel.onAction(EventEditorAction.UpdateNotes("2% organic milk"))
        viewModel.onAction(EventEditorAction.SelectCategory("cat_1"))
        viewModel.onAction(EventEditorAction.SelectEventType("t_reminder"))
        viewModel.onAction(EventEditorAction.UpdateTemporal(EventDateTime.DateOnly(LocalDate.of(2026, 10, 21))))
        viewModel.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.validationError)
        assertNull(state.errorMessage)
        assertTrue(state.isSaved)

        val allEvents = eventRepository.events.value.values
        assertEquals(1, allEvents.size)
        val saved = allEvents.first()
        assertEquals("Buy Milk", saved.title)
        assertEquals("2% organic milk", saved.description)
    }

    @Test
    fun searchEventType_filtersCatalogs() = runTest(testDispatcher) {
        eventTypeRepository.saveEventType(EventType("t1", "Meeting", "meeting", "cat_1", createdAt = timeProvider.nowInstant()))
        eventTypeRepository.saveEventType(EventType("t2", "Doctor Appointment", "doctor", "cat_1", createdAt = timeProvider.nowInstant()))
        eventTypeRepository.saveEventType(EventType("t3", "Birthday", "birthday", "cat_1", createdAt = timeProvider.nowInstant()))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.availableEventTypes.size)

        viewModel.onAction(EventEditorAction.SearchEventType("Doc"))
        assertEquals(1, viewModel.uiState.value.filteredEventTypes.size)
        assertEquals("Doctor Appointment", viewModel.uiState.value.filteredEventTypes.first().name)
    }

    @Test
    fun initWithEvent_populatesExistingEventFields_and_updates() = runTest(testDispatcher) {
        val existing = Event(
            id = "existing_1",
            title = "Existing Title",
            description = "Existing Description",
            eventTypeId = "type_x",
            categoryId = "cat_x",
            priority = Priority.CRITICAL,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(existing)

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.InitWithEvent("existing_1"))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditing)
        assertEquals("Existing Title", state.title)
        assertEquals("Existing Description", state.notes)
        assertEquals(Priority.CRITICAL, state.priority)

        // Update title and save
        viewModel.onAction(EventEditorAction.UpdateTitle("Updated Title"))
        viewModel.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val saved = eventRepository.getEvent("existing_1")
        assertEquals("Updated Title", saved?.title)
    }

    @Test
    fun initWithEvent_null_usesSettingsDefaultsForPriorityAndDuration() = runTest(testDispatcher) {
        val fakeSettingsRepo = object : SettingsRepository {
            override fun getSettingsFlow(): kotlinx.coroutines.flow.Flow<AppSettings> =
                kotlinx.coroutines.flow.flow { emit(getSettings()) }
            override suspend fun getSettings(): AppSettings = AppSettings(
                defaultEventPriority = Priority.HIGH,
                defaultEventDurationMinutes = 45
            )
            override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) =
                com.example.offlinereminder.core.result.Result.Success(getSettings())
            override suspend fun resetSettings() =
                com.example.offlinereminder.core.result.Result.Success(getSettings())
        }

        val viewModel = createViewModel(fakeSettingsRepo)
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.InitWithEvent(null))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Priority.HIGH, state.priority)
        assertNotNull(state.temporal)
        assertTrue(state.temporal is EventDateTime.DateTimeRange)
        val range = state.temporal as EventDateTime.DateTimeRange
        val expectedEnd = range.startDateTime.plusMinutes(45)
        assertEquals(expectedEnd, range.endDateTime)
    }

    @Test
    fun initWithDefaults_usesSettingsDefaultsForPriorityAndDuration() = runTest(testDispatcher) {
        val fakeSettingsRepo = object : SettingsRepository {
            override fun getSettingsFlow(): kotlinx.coroutines.flow.Flow<AppSettings> =
                kotlinx.coroutines.flow.flow { emit(getSettings()) }
            override suspend fun getSettings(): AppSettings = AppSettings(
                defaultEventPriority = Priority.CRITICAL,
                defaultEventDurationMinutes = 60
            )
            override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) =
                com.example.offlinereminder.core.result.Result.Success(getSettings())
            override suspend fun resetSettings() =
                com.example.offlinereminder.core.result.Result.Success(getSettings())
        }

        val viewModel = createViewModel(fakeSettingsRepo)
        testScheduler.advanceUntilIdle()

        val date = LocalDate.of(2026, 11, 15)
        viewModel.onAction(EventEditorAction.InitWithDefaults(date, "t_reminder"))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Priority.CRITICAL, state.priority)
        assertEquals("t_reminder", state.eventTypeId)
        assertTrue(state.temporal is EventDateTime.DateTimeRange)
        val range = state.temporal as EventDateTime.DateTimeRange
        assertEquals(date, range.startDate)
        val expectedEnd = range.startDateTime.plusMinutes(60)
        assertEquals(expectedEnd, range.endDateTime)
    }

    @Test
    fun simplestValidEvent_titleEntered_canSaveIsTrueAndSavesSuccessfully() = runTest(testDispatcher) {
        // Pre-condition: standard General category and General event type exist
        categoryRepository.saveCategory(Category("cat_general", "General", 0, null, false, 0))
        eventTypeRepository.saveEventType(EventType("type_general", "General", "general_reminder", "cat_general", createdAt = timeProvider.nowInstant()))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // User opens "New Reminder"
        viewModel.onAction(EventEditorAction.InitWithDefaults(null, null))
        testScheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        // Initially, title is blank, so canSave is false with clear explanation
        assertFalse(state.canSave)
        assertEquals("Title is required", state.missingFieldExplanation)
        assertEquals("cat_general", state.categoryId)
        assertEquals("type_general", state.eventTypeId)
        assertEquals(Priority.NORMAL, state.priority)
        assertNotNull(state.temporal)

        // User enters title "Test Event"
        viewModel.onAction(EventEditorAction.UpdateTitle("Test Event"))
        testScheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        // NOW canSave MUST be true!
        assertTrue(state.canSave)
        assertNull(state.missingFieldExplanation)
        assertEquals("Test Event", state.title)

        // User clicks Save
        viewModel.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertNull(state.validationError)

        // Verify the event was saved in repository
        val allEvents = eventRepository.getAllActiveEvents()
        val saved = allEvents.first().firstOrNull()
        assertNotNull(saved)
        assertEquals("Test Event", saved?.title)
        assertEquals("cat_general", saved?.categoryId)
        assertEquals("type_general", saved?.eventTypeId)
        assertEquals(Priority.NORMAL, saved?.priority)
    }

    @Test
    fun selectCategory_switchesDefaultEventTypeToMatchingCategory() = runTest(testDispatcher) {
        categoryRepository.saveCategory(Category("cat_general", "General", 0, null, false, 0))
        categoryRepository.saveCategory(Category("cat_work", "Work", 0, null, false, 1))

        eventTypeRepository.saveEventType(EventType("type_general", "General", "general_reminder", "cat_general", createdAt = timeProvider.nowInstant()))
        eventTypeRepository.saveEventType(EventType("type_meeting", "Meeting", "meeting", "cat_work", createdAt = timeProvider.nowInstant()))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.InitWithDefaults(null, null))
        testScheduler.advanceUntilIdle()

        assertEquals("cat_general", viewModel.uiState.value.categoryId)
        assertEquals("type_general", viewModel.uiState.value.eventTypeId)

        // User switches category to Work
        viewModel.onAction(EventEditorAction.SelectCategory("cat_work"))
        testScheduler.advanceUntilIdle()

        assertEquals("cat_work", viewModel.uiState.value.categoryId)
        assertEquals("type_meeting", viewModel.uiState.value.eventTypeId)
    }

    @Test
    fun basicReminder_requiresOnlyTitleAndDate_categoryAndTypeAreOptional() = runTest(testDispatcher) {
        categoryRepository.saveCategory(Category("cat_general", "General", 0, null, false, 0))
        eventTypeRepository.saveEventType(EventType("type_general", "General", "general_reminder", "cat_general", createdAt = timeProvider.nowInstant()))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.InitWithDefaults(null, null))
        testScheduler.advanceUntilIdle()

        // Clear categoryId and eventTypeId to simulate zero user selection
        viewModel.onAction(EventEditorAction.SelectCategory(""))
        viewModel.onAction(EventEditorAction.SelectEventType(""))
        viewModel.onAction(EventEditorAction.UpdateTitle("Doctor appointment"))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        // User perspective: canSave MUST be true even without category or event type selected
        assertTrue(state.canSave)
        assertNull(state.missingFieldExplanation)

        viewModel.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        val savedState = viewModel.uiState.value
        assertTrue(savedState.isSaved)
        assertNull(savedState.validationError)

        val allEvents = eventRepository.getAllActiveEvents().first()
        val created = allEvents.firstOrNull()
        assertNotNull(created)
        assertEquals("Doctor appointment", created?.title)
        // Background resolution defaulted to valid categories and event types
        assertEquals("cat_general", created?.categoryId)
        assertEquals("type_general", created?.eventTypeId)
    }

    @Test
    fun advancedOptions_categoryPriorityLocationAndRepeat_persistSuccessfully() = runTest(testDispatcher) {
        categoryRepository.saveCategory(Category("cat_health", "Health", 0, null, false, 0))
        eventTypeRepository.saveEventType(EventType("type_doc", "Doctor", "doctor", "cat_health", createdAt = timeProvider.nowInstant()))

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.InitWithDefaults(null, null))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(EventEditorAction.UpdateTitle("Doctor appointment"))
        viewModel.onAction(EventEditorAction.SelectCategory("cat_health"))
        viewModel.onAction(EventEditorAction.SelectEventType("type_doc"))
        viewModel.onAction(EventEditorAction.UpdatePriority(Priority.HIGH))
        viewModel.onAction(EventEditorAction.UpdateLocation("City Hospital, Room 402"))
        viewModel.onAction(
            EventEditorAction.UpdateRecurrence(
                com.example.offlinereminder.domain.model.RecurrenceRule(
                    frequency = com.example.offlinereminder.domain.model.RecurrenceFrequency.WEEKLY,
                    interval = 1,
                    daysOfWeek = setOf(java.time.DayOfWeek.MONDAY)
                )
            )
        )
        viewModel.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)

        val created = eventRepository.getAllActiveEvents().first().firstOrNull()
        assertNotNull(created)
        assertEquals("Doctor appointment", created?.title)
        assertEquals("cat_health", created?.categoryId)
        assertEquals("type_doc", created?.eventTypeId)
        assertEquals(Priority.HIGH, created?.priority)
        assertEquals("City Hospital, Room 402", created?.location?.placeName)
        assertEquals(com.example.offlinereminder.domain.model.RecurrenceFrequency.WEEKLY, created?.recurrenceRule?.frequency)
    }
}
