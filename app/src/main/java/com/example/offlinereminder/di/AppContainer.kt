package com.example.offlinereminder.di

import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.domain.notification.AlarmScheduler
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
 * Dependency injection container interface providing application-wide singletons,
 * repositories, and use cases.
 */
interface AppContainer {
    val database: AppDatabase
    val timeProvider: TimeProvider
    val dispatcherProvider: DispatcherProvider
    val alarmScheduler: AlarmScheduler
    val recurrenceEngine: RecurrenceEngine

    val eventRepository: EventRepository
    val categoryRepository: CategoryRepository
    val eventTypeRepository: EventTypeRepository
    val eventGroupRepository: EventGroupRepository
    val tagRepository: TagRepository
    val occurrenceExceptionRepository: OccurrenceExceptionRepository
    val notificationRepository: NotificationRepository

    val validateEventUseCase: ValidateEventUseCase
    val projectOccurrencesUseCase: ProjectOccurrencesUseCase
    val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase
    val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase
    val completeOccurrenceUseCase: CompleteOccurrenceUseCase
    val skipOccurrenceUseCase: SkipOccurrenceUseCase
    val rescheduleOccurrenceUseCase: RescheduleOccurrenceUseCase
    val trashEventUseCase: TrashEventUseCase
    val restoreEventUseCase: RestoreEventUseCase
    val archiveEventUseCase: ArchiveEventUseCase
    val rebootRecoveryUseCase: RebootRecoveryUseCase
    val snoozeNotificationUseCase: SnoozeNotificationUseCase
    val dismissNotificationUseCase: DismissNotificationUseCase

    val createEventUseCase: CreateEventUseCase
    val updateEventUseCase: UpdateEventUseCase
    val deleteEventUseCase: DeleteEventUseCase
    val getEventByIdUseCase: GetEventByIdUseCase
    val getEventsUseCase: GetEventsUseCase
    val getCategoriesUseCase: GetCategoriesUseCase
    val getEventTypesUseCase: GetEventTypesUseCase
    val getEventGroupsUseCase: GetEventGroupsUseCase
    val getTagsUseCase: GetTagsUseCase
    val getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase
    val getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase
    val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase
    val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase
    val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase

    val getCalendarOccurrencesUseCase: com.example.offlinereminder.domain.usecase.GetCalendarOccurrencesUseCase
    val saveGroupUseCase: com.example.offlinereminder.domain.usecase.SaveGroupUseCase
    val deleteGroupUseCase: com.example.offlinereminder.domain.usecase.DeleteGroupUseCase
    val getGroupByIdUseCase: com.example.offlinereminder.domain.usecase.GetGroupByIdUseCase
    val getActiveEventsForGroupUseCase: com.example.offlinereminder.domain.usecase.GetActiveEventsForGroupUseCase
    val searchEventsUseCase: com.example.offlinereminder.domain.usecase.SearchEventsUseCase
}
