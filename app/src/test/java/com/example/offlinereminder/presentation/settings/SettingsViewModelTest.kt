package com.example.offlinereminder.presentation.settings

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.backup.BackupExportResult
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.BackupRepository
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.DateFormatPreference
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.ThemeMode
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.repository.SettingsRepository
import com.example.offlinereminder.domain.usecase.CreateCategoryUseCase
import com.example.offlinereminder.domain.usecase.CreateEventTypeUseCase
import com.example.offlinereminder.domain.usecase.DeleteCategoryUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventTypeUseCase
import com.example.offlinereminder.domain.usecase.ExportBackupUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetSettingsUseCase
import com.example.offlinereminder.domain.usecase.ResetSettingsUseCase
import com.example.offlinereminder.domain.usecase.RestoreBackupUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.UpdateCategoryUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventTypeUseCase
import com.example.offlinereminder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var fakeBackupRepo: FakeBackupRepository
    private lateinit var fakeSettingsRepo: FakeSettingsRepository
    private lateinit var fakeCategoryRepo: FakeCategoryRepository
    private lateinit var fakeEventTypeRepo: FakeEventTypeRepository
    private lateinit var fakeEventRepo: FakeEventRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        fakeBackupRepo = FakeBackupRepository()
        fakeSettingsRepo = FakeSettingsRepository()
        fakeCategoryRepo = FakeCategoryRepository()
        fakeEventTypeRepo = FakeEventTypeRepository()
        fakeEventRepo = FakeEventRepository()

        val timeProvider = TestTimeProvider(Instant.parse("2026-10-20T10:00:00Z"))

        val exportUseCase = ExportBackupUseCase(fakeBackupRepo)
        val restoreUseCase = RestoreBackupUseCase(
            backupRepository = fakeBackupRepo,
            eventRepository = fakeEventRepo,
            occurrenceExceptionRepository = object : OccurrenceExceptionRepository {
                override fun getExceptionsForEvent(eventId: String): Flow<List<OccurrenceException>> = flowOf(emptyList())
                override suspend fun getExceptionByOccurrenceId(occurrenceId: String): OccurrenceException? = null
                override fun getExceptionByOccurrenceIdFlow(occurrenceId: String): Flow<OccurrenceException?> = flowOf(null)
                override suspend fun getExceptionsInRange(startLdt: java.time.LocalDateTime, endLdt: java.time.LocalDateTime): List<OccurrenceException> = emptyList()
                override suspend fun saveException(exception: OccurrenceException): Result<Unit> = Result.Success(Unit)
                override suspend fun deleteException(id: String): Result<Unit> = Result.Success(Unit)
                override suspend fun deleteByOccurrenceId(occurrenceId: String): Result<Unit> = Result.Success(Unit)
            },
            scheduleNotificationsForEventUseCase = ScheduleNotificationsForEventUseCase(
                notificationRepository = object : com.example.offlinereminder.domain.repository.NotificationRepository {
                    override fun getInstancesForEventFlow(eventId: String): Flow<List<com.example.offlinereminder.domain.model.NotificationInstance>> = flowOf(emptyList())
                    override fun getAllScheduledFlow(): Flow<List<com.example.offlinereminder.domain.model.NotificationInstance>> = flowOf(emptyList())
                    override suspend fun getInstanceById(id: String): com.example.offlinereminder.domain.model.NotificationInstance? = null
                    override suspend fun getInstancesForEvent(eventId: String): List<com.example.offlinereminder.domain.model.NotificationInstance> = emptyList()
                    override suspend fun getAllActiveScheduled(): List<com.example.offlinereminder.domain.model.NotificationInstance> = emptyList()
                    override suspend fun saveInstance(instance: com.example.offlinereminder.domain.model.NotificationInstance): Result<Unit> = Result.Success(Unit)
                    override suspend fun saveInstances(instances: List<com.example.offlinereminder.domain.model.NotificationInstance>): Result<Unit> = Result.Success(Unit)
                    override suspend fun updateStatus(id: String, status: com.example.offlinereminder.domain.model.NotificationInstanceStatus): Result<Unit> = Result.Success(Unit)
                    override suspend fun snooze(id: String, snoozeUntil: java.time.Instant, newTriggerMillis: Long): Result<Unit> = Result.Success(Unit)
                    override suspend fun deleteById(id: String): Result<Unit> = Result.Success(Unit)
                    override suspend fun deleteByEventId(eventId: String): Result<Unit> = Result.Success(Unit)
                },
                alarmScheduler = TestAlarmScheduler(),
                timeProvider = timeProvider
            ),
            settingsRepository = fakeSettingsRepo
        )

        viewModel = SettingsViewModel(
            getSettingsUseCase = GetSettingsUseCase(fakeSettingsRepo),
            updateSettingsUseCase = UpdateSettingsUseCase(fakeSettingsRepo),
            resetSettingsUseCase = ResetSettingsUseCase(fakeSettingsRepo),
            getCategoriesUseCase = GetCategoriesUseCase(fakeCategoryRepo),
            createCategoryUseCase = CreateCategoryUseCase(fakeCategoryRepo),
            updateCategoryUseCase = UpdateCategoryUseCase(fakeCategoryRepo),
            deleteCategoryUseCase = DeleteCategoryUseCase(fakeCategoryRepo, fakeEventRepo, fakeEventTypeRepo),
            getEventTypesUseCase = GetEventTypesUseCase(fakeEventTypeRepo),
            createEventTypeUseCase = CreateEventTypeUseCase(fakeEventTypeRepo, fakeCategoryRepo, timeProvider),
            updateEventTypeUseCase = UpdateEventTypeUseCase(fakeEventTypeRepo, fakeCategoryRepo),
            deleteEventTypeUseCase = DeleteEventTypeUseCase(fakeEventTypeRepo, fakeEventRepo),
            exportBackupUseCase = exportUseCase,
            restoreBackupUseCase = restoreUseCase,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun initialState_hasDefaultSettingsAndEmptyState() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(ThemeMode.SYSTEM, state.settings.themeMode)
        assertFalse(state.settings.use24HourFormat)
        assertEquals(RestoreStrategy.REPLACE_ALL, state.selectedStrategy)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
    }

    @Test
    fun updateThemeMode_updatesSettingsState() = runTest(testDispatcher) {
        viewModel.onAction(SettingsAction.UpdateThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)
    }

    @Test
    fun updateTimeFormatAndDateFormat_updatesState() = runTest(testDispatcher) {
        viewModel.onAction(SettingsAction.UpdateTimeFormat(true))
        viewModel.onAction(SettingsAction.UpdateDateFormat(DateFormatPreference.ISO))
        viewModel.onAction(SettingsAction.UpdateFirstDayOfWeek(DayOfWeek.SUNDAY))
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertTrue(settings.use24HourFormat)
        assertEquals(DateFormatPreference.ISO, settings.dateFormat)
        assertEquals(DayOfWeek.SUNDAY, settings.firstDayOfWeek)
    }

    @Test
    fun updateDefaults_updatesEventDefaults() = runTest(testDispatcher) {
        viewModel.onAction(SettingsAction.UpdateDefaultPriority(Priority.HIGH))
        viewModel.onAction(SettingsAction.UpdateDefaultDuration(30))
        viewModel.onAction(SettingsAction.UpdateDefaultSnooze(15))
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertEquals(Priority.HIGH, settings.defaultEventPriority)
        assertEquals(30, settings.defaultEventDurationMinutes)
        assertEquals(15, settings.defaultSnoozeDurationMinutes)
    }

    @Test
    fun toggleSoundAndVibration_updatesSettings() = runTest(testDispatcher) {
        viewModel.onAction(SettingsAction.ToggleSound(false))
        viewModel.onAction(SettingsAction.ToggleVibration(false))
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertFalse(settings.soundEnabled)
        assertFalse(settings.vibrationEnabled)
    }

    @Test
    fun updateQuietHours_updatesQuietHoursSettings() = runTest(testDispatcher) {
        val qh = QuietHours(
            enabled = true,
            startLocalTime = LocalTime.of(23, 0),
            endLocalTime = LocalTime.of(6, 0),
            policy = QuietHoursPolicy.SILENT,
            allowCritical = false
        )
        viewModel.onAction(SettingsAction.UpdateQuietHours(qh))
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertTrue(settings.quietHours.enabled)
        assertEquals(QuietHoursPolicy.SILENT, settings.quietHours.policy)
        assertFalse(settings.quietHours.allowCritical)
    }

    @Test
    fun resetSettings_restoresDefaults() = runTest(testDispatcher) {
        viewModel.onAction(SettingsAction.UpdateThemeMode(ThemeMode.LIGHT))
        viewModel.onAction(SettingsAction.UpdateTimeFormat(true))
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.settings.themeMode)

        viewModel.onAction(SettingsAction.ResetSettings)
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.settings.themeMode)
        assertFalse(viewModel.uiState.value.settings.use24HourFormat)
        assertNotNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun categoryManagement_createAndObserve_succeeds() = runTest(testDispatcher) {
        viewModel.onAction(SettingsAction.CreateCategory("Fitness"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.categories.size)
        assertEquals("Fitness", state.categories[0].name)
        assertNotNull(state.successMessage)
    }

    @Test
    fun eventTypeManagement_createAndObserve_succeeds() = runTest(testDispatcher) {
        // Create category first
        viewModel.onAction(SettingsAction.CreateCategory("Fitness"))
        advanceUntilIdle()
        val catId = viewModel.uiState.value.categories[0].id

        viewModel.onAction(SettingsAction.CreateEventType(name = "Running", categoryId = catId))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.eventTypes.size)
        assertEquals("Running", state.eventTypes[0].name)
        assertNotNull(state.successMessage)
    }

    @Test
    fun exportBackup_success_updatesStateWithResultAndMessage() = runTest(testDispatcher) {
        val out = ByteArrayOutputStream()
        viewModel.onAction(SettingsAction.ExportBackup(out))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isExporting)
        assertNotNull(state.lastExportResult)
        assertNotNull(state.successMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun exportBackup_failure_updatesStateWithErrorMessage() = runTest(testDispatcher) {
        fakeBackupRepo.shouldFailExport = true
        val out = ByteArrayOutputStream()
        viewModel.onAction(SettingsAction.ExportBackup(out))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isExporting)
        assertNull(state.lastExportResult)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun importBackup_success_updatesStateWithSummaryAndMessage() = runTest(testDispatcher) {
        val inStream = ByteArrayInputStream("test".toByteArray())
        viewModel.onAction(SettingsAction.ImportBackup(inStream, RestoreStrategy.REPLACE_ALL))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isImporting)
        assertNotNull(state.lastRestoreSummary)
        assertNotNull(state.successMessage)
        assertNull(state.errorMessage)
    }
}

// ----------------------------------------------------
// Fakes for SettingsViewModelTest
// ----------------------------------------------------
private class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val flow = MutableStateFlow(initial)
    override fun getSettingsFlow(): Flow<AppSettings> = flow
    override suspend fun getSettings(): AppSettings = flow.value
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings): Result<AppSettings> {
        val updated = transform(flow.value)
        flow.value = updated
        return Result.Success(updated)
    }
    override suspend fun resetSettings(): Result<AppSettings> {
        flow.value = AppSettings()
        return Result.Success(flow.value)
    }
}

private class FakeCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow<List<Category>>(emptyList())
    override fun getAllCategories(): Flow<List<Category>> = categories
    override suspend fun getCategory(id: String): Category? = categories.value.find { it.id == id }
    override suspend fun saveCategory(category: Category): Result<Unit> {
        categories.value = categories.value.filter { it.id != category.id } + category
        return Result.Success(Unit)
    }
    override suspend fun deleteCategory(id: String): Result<Unit> {
        categories.value = categories.value.filter { it.id != id }
        return Result.Success(Unit)
    }
}

private class FakeEventTypeRepository : EventTypeRepository {
    private val types = MutableStateFlow<List<EventType>>(emptyList())
    override fun getAllEventTypes(): Flow<List<EventType>> = types
    override fun getEventTypesByCategory(categoryId: String): Flow<List<EventType>> =
        types.map { list -> list.filter { it.categoryId == categoryId } }
    override fun searchEventTypes(query: String): Flow<List<EventType>> =
        types.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }
    override suspend fun getEventType(id: String): EventType? = types.value.find { it.id == id }
    override suspend fun getEventTypeByKey(key: String): EventType? = types.value.find { it.key == key }
    override suspend fun saveEventType(eventType: EventType): Result<Unit> {
        types.value = types.value.filter { it.id != eventType.id } + eventType
        return Result.Success(Unit)
    }
    override suspend fun deleteEventType(id: String): Result<Unit> {
        types.value = types.value.filter { it.id != id }
        return Result.Success(Unit)
    }
}

private class FakeEventRepository : EventRepository {
    private val events = MutableStateFlow<List<Event>>(emptyList())
    override fun getAllActiveEvents(): Flow<List<Event>> = events
    override fun getAllEvents(): Flow<List<Event>> = events
    override fun getEventsByLifecycleStatus(status: com.example.offlinereminder.domain.model.EventLifecycleStatus): Flow<List<Event>> = events
    override fun getEventById(id: String): Flow<Event?> = events.map { list -> list.find { it.id == id } }
    override suspend fun getEvent(id: String): Event? = events.value.find { it.id == id }
    override fun getFavoriteEvents(): Flow<List<Event>> = events
    override fun getEventsByCategory(categoryId: String): Flow<List<Event>> =
        events.map { list -> list.filter { it.categoryId == categoryId } }
    override fun getEventsByEventType(eventTypeId: String): Flow<List<Event>> =
        events.map { list -> list.filter { it.eventTypeId == eventTypeId } }
    override fun getSubEvents(parentEventId: String): Flow<List<Event>> = flowOf(emptyList())
    override fun getEventsInDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): Flow<List<Event>> = flowOf(emptyList())
    override suspend fun saveEvent(event: Event): Result<Unit> {
        events.value = events.value.filter { it.id != event.id } + event
        return Result.Success(Unit)
    }
    override suspend fun deleteEvent(id: String): Result<Unit> {
        events.value = events.value.filter { it.id != id }
        return Result.Success(Unit)
    }
    override suspend fun moveToTrash(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun restoreFromTrash(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun setFavorite(id: String, isFavorite: Boolean): Result<Unit> = Result.Success(Unit)
    override suspend fun countEventsByCategory(categoryId: String): Int = events.value.count { it.categoryId == categoryId }
    override suspend fun countEventsByEventType(eventTypeId: String): Int = events.value.count { it.eventTypeId == eventTypeId }
    override suspend fun archiveEvent(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun restoreFromArchive(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun emptyTrash(): Result<Int> = Result.Success(0)
    override suspend fun purgeExpiredTrash(cutoff: java.time.Instant): Result<Int> = Result.Success(0)
    override suspend fun addDependency(dependency: com.example.offlinereminder.domain.model.EventDependency): Result<Unit> = Result.Success(Unit)
    override suspend fun removeDependency(sourceEventId: String, targetEventId: String): Result<Unit> = Result.Success(Unit)
    override fun getDependenciesForEvent(eventId: String): Flow<List<com.example.offlinereminder.domain.model.EventDependency>> = flowOf(emptyList())
    override fun getDependentsOfEvent(eventId: String): Flow<List<com.example.offlinereminder.domain.model.EventDependency>> = flowOf(emptyList())
    override suspend fun addRelationship(relationship: com.example.offlinereminder.domain.model.EventRelationship): Result<Unit> = Result.Success(Unit)
    override suspend fun removeRelationship(sourceEventId: String, targetEventId: String): Result<Unit> = Result.Success(Unit)
    override fun getRelationshipsForEvent(eventId: String): Flow<List<com.example.offlinereminder.domain.model.EventRelationship>> = flowOf(emptyList())
}

private class FakeBackupRepository : BackupRepository {
    var shouldFailExport = false
    var shouldFailImport = false

    override suspend fun createBackupPayload(): Result<BackupPayload> {
        return Result.Success(BackupPayload(backupVersion = 1, exportedAt = "2026-10-20T10:00:00Z"))
    }

    override suspend fun restoreBackupPayload(
        payload: BackupPayload,
        strategy: RestoreStrategy
    ): Result<RestoreSummary> {
        if (shouldFailImport) return Result.Failure(IllegalArgumentException("Import failed"))
        return Result.Success(RestoreSummary(eventsRestored = 5, categoriesRestored = 2))
    }

    override suspend fun exportBackupArchive(outputStream: OutputStream): Result<BackupExportResult> {
        if (shouldFailExport) return Result.Failure(IllegalStateException("Disk full"))
        outputStream.write("dummy-zip".toByteArray())
        return Result.Success(BackupExportResult(backupVersion = 1, totalEvents = 10, totalCategories = 2, totalGroups = 1, totalAttachments = 0, exportedAt = "2026-10-20T10:00:00Z", archiveSizeBytes = 1024L))
    }

    override suspend fun importBackupArchive(
        inputStream: InputStream,
        strategy: RestoreStrategy
    ): Result<RestoreSummary> {
        if (shouldFailImport) return Result.Failure(IllegalArgumentException("Corrupted ZIP"))
        return Result.Success(RestoreSummary(eventsRestored = 10, categoriesRestored = 2, groupsRestored = 1))
    }
}
