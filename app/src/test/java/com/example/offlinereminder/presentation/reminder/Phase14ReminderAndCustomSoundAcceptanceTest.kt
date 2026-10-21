package com.example.offlinereminder.presentation.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.dispatcher.TestDispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.alarm.AlarmRingtonePlayer
import com.example.offlinereminder.data.notification.NotificationPoster
import com.example.offlinereminder.data.repository.InMemoryCategoryRepository
import com.example.offlinereminder.data.repository.InMemoryCustomSoundRepository
import com.example.offlinereminder.data.repository.InMemoryEventGroupRepository
import com.example.offlinereminder.data.repository.InMemoryEventRepository
import com.example.offlinereminder.data.repository.InMemoryEventTypeRepository
import com.example.offlinereminder.data.repository.InMemoryNotificationProfileRepository
import com.example.offlinereminder.data.repository.InMemoryOccurrenceExceptionRepository
import com.example.offlinereminder.data.repository.InMemoryTagRepository
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.usecase.AddCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.CreateNotificationProfileUseCase
import com.example.offlinereminder.domain.usecase.DeleteCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetNotificationProfilesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase
import com.example.offlinereminder.presentation.eventeditor.EventEditorAction
import com.example.offlinereminder.presentation.eventeditor.EventEditorViewModel
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Phase14ReminderAndCustomSoundAcceptanceTest {

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
    private val customSoundRepository = InMemoryCustomSoundRepository()
    private val notificationProfileRepository = InMemoryNotificationProfileRepository()
    private val occurrenceExceptionRepository = InMemoryOccurrenceExceptionRepository()

    private lateinit var getEventByIdUseCase: GetEventByIdUseCase
    private lateinit var createEventUseCase: CreateEventUseCase
    private lateinit var updateEventUseCase: UpdateEventUseCase
    private lateinit var getCategoriesUseCase: GetCategoriesUseCase
    private lateinit var getEventTypesUseCase: GetEventTypesUseCase
    private lateinit var getEventGroupsUseCase: GetEventGroupsUseCase
    private lateinit var getTagsUseCase: GetTagsUseCase
    private lateinit var addCustomSoundUseCase: AddCustomSoundUseCase
    private lateinit var deleteCustomSoundUseCase: DeleteCustomSoundUseCase
    private lateinit var getCustomSoundsUseCase: GetCustomSoundsUseCase
    private lateinit var getNotificationProfilesUseCase: GetNotificationProfilesUseCase
    private lateinit var createNotificationProfileUseCase: CreateNotificationProfileUseCase

    @Before
    fun setUp() = runTest(testDispatcher) {
        eventRepository.clear()
        customSoundRepository.sounds.value = emptyMap()
        notificationProfileRepository.clear()

        getEventByIdUseCase = GetEventByIdUseCase(eventRepository)
        val validateEventUseCase = ValidateEventUseCase()
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

        addCustomSoundUseCase = AddCustomSoundUseCase(customSoundRepository, timeProvider)
        deleteCustomSoundUseCase = DeleteCustomSoundUseCase(
            repository = customSoundRepository,
            profileRepository = notificationProfileRepository,
            eventRepository = eventRepository
        )
        getCustomSoundsUseCase = GetCustomSoundsUseCase(customSoundRepository)
        getNotificationProfilesUseCase = GetNotificationProfilesUseCase(notificationProfileRepository)
        createNotificationProfileUseCase = CreateNotificationProfileUseCase(notificationProfileRepository)

        categoryRepository.saveCategory(
            com.example.offlinereminder.domain.model.Category(
                id = "cat-1",
                name = "General",
                color = 0,
                icon = null,
                isCustom = false,
                sortOrder = 0
            )
        )
        eventTypeRepository.saveEventType(
            com.example.offlinereminder.domain.model.EventType(
                id = "type-1",
                name = "Meeting",
                key = "meeting",
                categoryId = "cat-1",
                createdAt = timeProvider.nowInstant()
            )
        )
    }

    private fun createEditorViewModel(): EventEditorViewModel {
        return EventEditorViewModel(
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
            getNotificationProfilesUseCase = getNotificationProfilesUseCase,
            getCustomSoundsUseCase = getCustomSoundsUseCase
        )
    }

    @Test
    fun reminders_addEditDelete_inEventEditor_persistsDeliveryAndSoundConfigurations() = runTest(testDispatcher) {
        val vm = createEditorViewModel()
        vm.onAction(EventEditorAction.InitWithDefaults(LocalDate.of(2026, 10, 25), "type-1"))
        vm.onAction(EventEditorAction.SelectCategory("cat-1"))
        vm.onAction(EventEditorAction.UpdateTitle("Team Standup"))
        testScheduler.advanceUntilIdle()

        // 1. Add NORMAL notification reminder (10 minutes before)
        val normalSchedule = NotificationSchedule(
            id = "sched-normal",
            offsetDuration = Duration.ofMinutes(10),
            triggerType = NotificationTriggerType.BEFORE_START,
            deliveryType = ReminderDeliveryType.NOTIFICATION,
            sound = SoundReference(type = SoundSourceType.DEFAULT)
        )
        vm.onAction(EventEditorAction.AddNotificationSchedule(normalSchedule))
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.notificationSchedules.size)
        assertEquals(ReminderDeliveryType.NOTIFICATION, vm.uiState.value.notificationSchedules.first().deliveryType)

        // 2. Add full ALARM reminder with custom sound (at start)
        val alarmSchedule = NotificationSchedule(
            id = "sched-alarm",
            offsetDuration = Duration.ZERO,
            triggerType = NotificationTriggerType.AT_START,
            deliveryType = ReminderDeliveryType.ALARM,
            sound = SoundReference(type = SoundSourceType.CUSTOM, customSoundId = "snd-loud-chime"),
            profileId = "profile-vip"
        )
        vm.onAction(EventEditorAction.AddNotificationSchedule(alarmSchedule))
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.notificationSchedules.size)

        // 3. Save event
        vm.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaved)

        val savedEvents = eventRepository.getAllEvents().first()
        val savedEvent = savedEvents.find { it.title == "Team Standup" }
        assertNotNull(savedEvent)
        assertEquals(2, savedEvent?.schedules?.size)

        val retrievedAlarm = savedEvent?.schedules?.find { it.id == "sched-alarm" }
        assertNotNull(retrievedAlarm)
        assertEquals(ReminderDeliveryType.ALARM, retrievedAlarm?.deliveryType)
        assertEquals(SoundSourceType.CUSTOM, retrievedAlarm?.sound?.type)
        assertEquals("snd-loud-chime", retrievedAlarm?.sound?.customSoundId)
        assertEquals("profile-vip", retrievedAlarm?.profileId)

        // 4. Remove a reminder
        vm.onAction(EventEditorAction.RemoveNotificationSchedule("sched-normal"))
        vm.onAction(EventEditorAction.SaveEvent)
        testScheduler.advanceUntilIdle()

        val updated = eventRepository.getEvent(savedEvent!!.id)
        assertEquals(1, updated?.schedules?.size)
        assertEquals("sched-alarm", updated?.schedules?.first()?.id)
    }

    @Test
    fun customSound_referencePersistence_andSafeDeletionProtection() = runTest(testDispatcher) {
        // 1. Register custom sound
        val addResult = addCustomSoundUseCase(
            title = "Gentle Marimba",
            fileName = "marimba.mp3",
            mimeType = "audio/mpeg",
            fileSizeBytes = 45000L,
            id = "sound-marimba"
        )
        assertTrue(addResult is Result.Success)

        // 2. Assign sound to a NotificationProfile
        val profileResult = createNotificationProfileUseCase(
            name = "Work Focus",
            deliveryType = ReminderDeliveryType.NOTIFICATION,
            sound = SoundReference(SoundSourceType.CUSTOM, customSoundId = "sound-marimba"),
            vibrationEnabled = false,
            defaultSnoozeDurationMinutes = 10,
            isDefault = false
        )
        assertTrue(profileResult is Result.Success)

        // 3. Attempt unsafe deletion while referenced by profile
        val unsafeDeleteResult = deleteCustomSoundUseCase("sound-marimba")
        assertTrue(unsafeDeleteResult is Result.Failure)
        val errorMessage = (unsafeDeleteResult as Result.Failure).error.message
        assertTrue(errorMessage?.contains("referenced by profile") == true)
        // Sound is not deleted
        assertNotNull(customSoundRepository.getSoundById("sound-marimba"))

        // 4. Test protection when referenced by an active event
        val addResult2 = addCustomSoundUseCase(
            title = "Warning Horn",
            fileName = "horn.ogg",
            mimeType = "audio/ogg",
            fileSizeBytes = 12000L,
            id = "sound-horn"
        )
        assertTrue(addResult2 is Result.Success)

        val activeEvent = Event(
            id = "event-horn",
            title = "Urgent Fire Drill",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 11, 1)),
            notificationSchedules = listOf(
                NotificationSchedule(
                    id = "sched-horn",
                    sound = SoundReference(SoundSourceType.CUSTOM, customSoundId = "sound-horn")
                )
            ),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        eventRepository.saveEvent(activeEvent)

        val unsafeEventDeleteResult = deleteCustomSoundUseCase("sound-horn")
        assertTrue(unsafeEventDeleteResult is Result.Failure)
        val eventErrorMsg = (unsafeEventDeleteResult as Result.Failure).error.message
        assertTrue(eventErrorMsg?.contains("referenced by event") == true)
        assertNotNull(customSoundRepository.getSoundById("sound-horn"))

        // 5. Valid deletion when sound is completely unreferenced
        val unreferencedSound = addCustomSoundUseCase(
            title = "Unused Chime",
            fileName = "unused.wav",
            mimeType = "audio/wav",
            fileSizeBytes = 5000L,
            id = "sound-unused"
        )
        assertTrue(unreferencedSound is Result.Success)

        val safeDeleteResult = deleteCustomSoundUseCase("sound-unused")
        assertTrue(safeDeleteResult is Result.Success)
        assertEquals(null, customSoundRepository.getSoundById("sound-unused"))
    }

    @Test
    fun missingCustomSound_fallsBackGracefullyInAudioComponents() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val testEvent = Event(
            id = "e1",
            title = "Test Reminder",
            categoryId = "cat-1",
            eventTypeId = "type-1",
            temporal = EventDateTime.DateOnly(LocalDate.of(2026, 10, 25)),
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
        val testInstance = NotificationInstance(
            id = "inst-1",
            eventId = "e1",
            occurrenceOriginalStartDateTime = java.time.LocalDateTime.of(2026, 10, 25, 10, 0),
            occurrenceOriginalZoneId = ZoneId.of("UTC"),
            requestCode = 101,
            triggerEpochMillis = timeProvider.nowInstant().toEpochMilli(),
            status = NotificationInstanceStatus.SCHEDULED
        )

        // 1. NotificationPoster handles posting with custom sound or blank sound without crashing
        val poster = NotificationPoster(context)
        poster.createNotificationChannels()

        val blankCustomSound = SoundReference(SoundSourceType.CUSTOM, customSoundId = "")
        val result1 = poster.postNotification(
            event = testEvent,
            instance = testInstance,
            sound = blankCustomSound
        )
        assertTrue(result1)

        // 2. Silent sound routing
        val silentSound = SoundReference(SoundSourceType.SILENT)
        val result2 = poster.postNotification(
            event = testEvent,
            instance = testInstance.copy(id = "inst-2", requestCode = 102),
            sound = silentSound,
            isSilent = true
        )
        assertTrue(result2)

        // 3. AlarmRingtonePlayer handles missing or silent custom sound without uncaught exceptions
        val player = AlarmRingtonePlayer(context)
        val silentResult = player.play(SoundReference(SoundSourceType.SILENT))
        assertFalse(silentResult)
        assertFalse(player.isPlaying)
    }
}
