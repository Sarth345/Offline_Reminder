package com.example.offlinereminder.presentation.settings

import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.domain.backup.BackupExportResult
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.BackupRepository
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.notification.TestAlarmScheduler
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.usecase.ExportBackupUseCase
import com.example.offlinereminder.domain.usecase.RestoreBackupUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var fakeBackupRepo: FakeBackupRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        fakeBackupRepo = FakeBackupRepository()
        val exportUseCase = ExportBackupUseCase(fakeBackupRepo)

        val fakeEventRepo = object : EventRepository {
            override fun getAllActiveEvents(): Flow<List<Event>> = flowOf(emptyList())
            override fun getAllEvents(): Flow<List<Event>> = flowOf(emptyList())
            override fun getEventsByLifecycleStatus(status: com.example.offlinereminder.domain.model.EventLifecycleStatus): Flow<List<Event>> = flowOf(emptyList())
            override fun getEventById(id: String): Flow<Event?> = flowOf(null)
            override suspend fun getEvent(id: String): Event? = null
            override fun getFavoriteEvents(): Flow<List<Event>> = flowOf(emptyList())
            override fun getEventsByCategory(categoryId: String): Flow<List<Event>> = flowOf(emptyList())
            override fun getEventsByEventType(eventTypeId: String): Flow<List<Event>> = flowOf(emptyList())
            override fun getSubEvents(parentEventId: String): Flow<List<Event>> = flowOf(emptyList())
            override fun getEventsInDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): Flow<List<Event>> = flowOf(emptyList())
            override suspend fun saveEvent(event: Event): Result<Unit> = Result.Success(Unit)
            override suspend fun deleteEvent(id: String): Result<Unit> = Result.Success(Unit)
            override suspend fun moveToTrash(id: String): Result<Unit> = Result.Success(Unit)
            override suspend fun restoreFromTrash(id: String): Result<Unit> = Result.Success(Unit)
            override suspend fun setFavorite(id: String, isFavorite: Boolean): Result<Unit> = Result.Success(Unit)
        }

        val fakeExceptionRepo = object : OccurrenceExceptionRepository {
            override fun getExceptionsForEvent(eventId: String): Flow<List<OccurrenceException>> = flowOf(emptyList())
            override suspend fun getExceptionByOccurrenceId(occurrenceId: String): OccurrenceException? = null
            override fun getExceptionByOccurrenceIdFlow(occurrenceId: String): Flow<OccurrenceException?> = flowOf(null)
            override suspend fun getExceptionsInRange(startLdt: java.time.LocalDateTime, endLdt: java.time.LocalDateTime): List<OccurrenceException> = emptyList()
            override suspend fun saveException(exception: OccurrenceException): Result<Unit> = Result.Success(Unit)
            override suspend fun deleteException(id: String): Result<Unit> = Result.Success(Unit)
            override suspend fun deleteByOccurrenceId(occurrenceId: String): Result<Unit> = Result.Success(Unit)
        }

        val fakeScheduleNotifUseCase = ScheduleNotificationsForEventUseCase(
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
            timeProvider = TestTimeProvider()
        )

        val restoreUseCase = RestoreBackupUseCase(
            backupRepository = fakeBackupRepo,
            eventRepository = fakeEventRepo,
            occurrenceExceptionRepository = fakeExceptionRepo,
            scheduleNotificationsForEventUseCase = fakeScheduleNotifUseCase
        )

        viewModel = SettingsViewModel(
            exportBackupUseCase = exportUseCase,
            restoreBackupUseCase = restoreUseCase,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun initialState_hasDefaultValues() {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isExporting)
        assertFalse(state.isImporting)
        assertEquals(RestoreStrategy.REPLACE_ALL, state.selectedStrategy)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
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
        assertTrue(state.errorMessage!!.contains("Simulated export error"))
    }

    @Test
    fun importBackup_success_updatesStateWithSummaryAndMessage() = runTest(testDispatcher) {
        val inStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        viewModel.onAction(SettingsAction.ImportBackup(inStream, RestoreStrategy.REPLACE_ALL))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isImporting)
        assertNotNull(state.lastRestoreSummary)
        assertEquals(5, state.lastRestoreSummary?.eventsRestored)
        assertNotNull(state.successMessage)
    }

    @Test
    fun importBackup_failure_updatesStateWithErrorMessage() = runTest(testDispatcher) {
        fakeBackupRepo.shouldFailImport = true
        val inStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        viewModel.onAction(SettingsAction.ImportBackup(inStream, RestoreStrategy.REPLACE_ALL))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isImporting)
        assertNull(state.lastRestoreSummary)
        assertTrue(state.errorMessage!!.contains("Simulated import error"))
    }

    @Test
    fun selectRestoreStrategy_updatesSelectedStrategy() {
        viewModel.onAction(SettingsAction.SelectRestoreStrategy(RestoreStrategy.MERGE_OVERWRITE))
        assertEquals(RestoreStrategy.MERGE_OVERWRITE, viewModel.uiState.value.selectedStrategy)
    }

    @Test
    fun dismissMessages_clearsErrorAndSuccess() = runTest(testDispatcher) {
        fakeBackupRepo.shouldFailExport = true
        viewModel.onAction(SettingsAction.ExportBackup(ByteArrayOutputStream()))
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.onAction(SettingsAction.DismissMessages)
        assertNull(viewModel.uiState.value.errorMessage)
    }
}

private class FakeBackupRepository : BackupRepository {
    var shouldFailExport = false
    var shouldFailImport = false

    override suspend fun createBackupPayload(): Result<BackupPayload> {
        if (shouldFailExport) return Result.Failure(RuntimeException("Simulated export error"))
        return Result.Success(
            BackupPayload(
                backupVersion = 1,
                exportedAt = "2026-10-20T10:00:00Z"
            )
        )
    }

    override suspend fun restoreBackupPayload(
        payload: BackupPayload,
        strategy: RestoreStrategy
    ): Result<RestoreSummary> {
        if (shouldFailImport) return Result.Failure(RuntimeException("Simulated import error"))
        return Result.Success(RestoreSummary(eventsRestored = 5))
    }

    override suspend fun exportBackupArchive(outputStream: OutputStream): Result<BackupExportResult> {
        if (shouldFailExport) return Result.Failure(RuntimeException("Simulated export error"))
        return Result.Success(
            BackupExportResult(
                backupVersion = 1,
                totalEvents = 5,
                totalCategories = 2,
                totalGroups = 1,
                totalAttachments = 0,
                exportedAt = "2026-10-20T10:00:00Z",
                archiveSizeBytes = 1024L
            )
        )
    }

    override suspend fun importBackupArchive(
        inputStream: InputStream,
        strategy: RestoreStrategy
    ): Result<RestoreSummary> {
        if (shouldFailImport) return Result.Failure(RuntimeException("Simulated import error"))
        return Result.Success(RestoreSummary(eventsRestored = 5, categoriesRestored = 2, groupsRestored = 1))
    }
}
