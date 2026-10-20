package com.example.offlinereminder.di

import android.content.Context
import androidx.room.Room
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.notification.AndroidAlarmScheduler
import com.example.offlinereminder.data.repository.CategoryRepositoryImpl
import com.example.offlinereminder.data.repository.EventGroupRepositoryImpl
import com.example.offlinereminder.data.repository.EventTypeRepositoryImpl
import com.example.offlinereminder.data.repository.OccurrenceExceptionRepositoryImpl
import com.example.offlinereminder.data.repository.OfflineEventRepository
import com.example.offlinereminder.data.repository.OfflineNotificationRepository
import com.example.offlinereminder.data.repository.TagRepositoryImpl
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventGroupRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.repository.TagRepository
import com.example.offlinereminder.domain.usecase.ArchiveEventUseCase
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.DismissNotificationUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetDashboardOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.GetUpcomingOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.ProjectOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.RebootRecoveryUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.RestoreEventUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceUseCase
import com.example.offlinereminder.domain.usecase.SnoozeNotificationUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import com.example.offlinereminder.domain.usecase.ValidateEventUseCase

/**
 * Production implementation of [AppContainer] using lazy-initialized dependencies.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    override val timeProvider: TimeProvider by lazy { SystemTimeProvider() }

    override val dispatcherProvider: DispatcherProvider by lazy { DefaultDispatcherProvider() }

    override val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    override val alarmScheduler: AlarmScheduler by lazy {
        AndroidAlarmScheduler(context.applicationContext)
    }

    override val recurrenceEngine: RecurrenceEngine by lazy {
        DefaultRecurrenceEngine(timeProvider)
    }

    override val eventRepository: EventRepository by lazy {
        OfflineEventRepository(database, timeProvider)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    override val eventTypeRepository: EventTypeRepository by lazy {
        EventTypeRepositoryImpl(database.eventTypeDao())
    }

    override val eventGroupRepository: EventGroupRepository by lazy {
        EventGroupRepositoryImpl(database.eventGroupDao())
    }

    override val tagRepository: TagRepository by lazy {
        TagRepositoryImpl(database.tagDao())
    }

    override val occurrenceExceptionRepository: OccurrenceExceptionRepository by lazy {
        OccurrenceExceptionRepositoryImpl(database.occurrenceExceptionDao())
    }

    override val notificationRepository: NotificationRepository by lazy {
        OfflineNotificationRepository(database.notificationInstanceDao())
    }

    override val validateEventUseCase: ValidateEventUseCase by lazy {
        ValidateEventUseCase()
    }

    override val projectOccurrencesUseCase: ProjectOccurrencesUseCase by lazy {
        ProjectOccurrencesUseCase(recurrenceEngine)
    }

    override val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase by lazy {
        ScheduleNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    override val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase by lazy {
        CancelNotificationsForEventUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler
        )
    }

    override val completeOccurrenceUseCase: CompleteOccurrenceUseCase by lazy {
        CompleteOccurrenceUseCase(timeProvider)
    }

    override val skipOccurrenceUseCase: SkipOccurrenceUseCase by lazy {
        SkipOccurrenceUseCase()
    }

    override val rescheduleOccurrenceUseCase: RescheduleOccurrenceUseCase by lazy {
        RescheduleOccurrenceUseCase()
    }

    override val trashEventUseCase: TrashEventUseCase by lazy {
        TrashEventUseCase(timeProvider)
    }

    override val restoreEventUseCase: RestoreEventUseCase by lazy {
        RestoreEventUseCase(timeProvider)
    }

    override val archiveEventUseCase: ArchiveEventUseCase by lazy {
        ArchiveEventUseCase(timeProvider)
    }

    override val rebootRecoveryUseCase: RebootRecoveryUseCase by lazy {
        RebootRecoveryUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            timeProvider = timeProvider
        )
    }

    override val snoozeNotificationUseCase: SnoozeNotificationUseCase by lazy {
        SnoozeNotificationUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            timeProvider = timeProvider
        )
    }

    override val dismissNotificationUseCase: DismissNotificationUseCase by lazy {
        DismissNotificationUseCase(
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler
        )
    }

    override val createEventUseCase: CreateEventUseCase by lazy {
        CreateEventUseCase(
            eventRepository = eventRepository,
            validateEventUseCase = validateEventUseCase,
            scheduleNotificationsForEventUseCase = scheduleNotificationsForEventUseCase,
            timeProvider = timeProvider
        )
    }

    override val updateEventUseCase: UpdateEventUseCase by lazy {
        UpdateEventUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            validateEventUseCase = validateEventUseCase,
            scheduleNotificationsForEventUseCase = scheduleNotificationsForEventUseCase,
            cancelNotificationsForEventUseCase = cancelNotificationsForEventUseCase,
            timeProvider = timeProvider
        )
    }

    override val deleteEventUseCase: DeleteEventUseCase by lazy {
        DeleteEventUseCase(
            eventRepository = eventRepository,
            cancelNotificationsForEventUseCase = cancelNotificationsForEventUseCase
        )
    }

    override val getEventByIdUseCase: GetEventByIdUseCase by lazy {
        GetEventByIdUseCase(eventRepository)
    }

    override val getEventsUseCase: GetEventsUseCase by lazy {
        GetEventsUseCase(eventRepository)
    }

    override val getCategoriesUseCase: GetCategoriesUseCase by lazy {
        GetCategoriesUseCase(categoryRepository)
    }

    override val getEventTypesUseCase: GetEventTypesUseCase by lazy {
        GetEventTypesUseCase(eventTypeRepository)
    }

    override val getEventGroupsUseCase: GetEventGroupsUseCase by lazy {
        GetEventGroupsUseCase(eventGroupRepository)
    }

    override val getTagsUseCase: GetTagsUseCase by lazy {
        GetTagsUseCase(tagRepository)
    }

    override val getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase by lazy {
        GetDashboardOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    override val getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase by lazy {
        GetUpcomingOccurrencesUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            recurrenceEngine = recurrenceEngine,
            timeProvider = timeProvider
        )
    }

    override val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase by lazy {
        CompleteOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            completeOccurrenceUseCase = completeOccurrenceUseCase,
            timeProvider = timeProvider
        )
    }

    override val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase by lazy {
        SkipOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            notificationRepository = notificationRepository,
            alarmScheduler = alarmScheduler,
            skipOccurrenceUseCase = skipOccurrenceUseCase
        )
    }

    override val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase by lazy {
        RescheduleOccurrenceActionUseCase(
            eventRepository = eventRepository,
            occurrenceExceptionRepository = occurrenceExceptionRepository,
            rescheduleOccurrenceUseCase = rescheduleOccurrenceUseCase,
            scheduleNotificationsForEventUseCase = scheduleNotificationsForEventUseCase,
            timeProvider = timeProvider
        )
    }
}
