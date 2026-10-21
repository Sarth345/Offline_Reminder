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
    val customSoundRepository: com.example.offlinereminder.domain.repository.CustomSoundRepository
    val notificationProfileRepository: com.example.offlinereminder.domain.repository.NotificationProfileRepository
    val eventTemplateRepository: com.example.offlinereminder.domain.repository.EventTemplateRepository

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
    val backupRepository: com.example.offlinereminder.domain.backup.BackupRepository
    val exportBackupUseCase: com.example.offlinereminder.domain.usecase.ExportBackupUseCase
    val restoreBackupUseCase: com.example.offlinereminder.domain.usecase.RestoreBackupUseCase

    val settingsRepository: com.example.offlinereminder.domain.repository.SettingsRepository
    val getSettingsUseCase: com.example.offlinereminder.domain.usecase.GetSettingsUseCase
    val updateSettingsUseCase: com.example.offlinereminder.domain.usecase.UpdateSettingsUseCase
    val resetSettingsUseCase: com.example.offlinereminder.domain.usecase.ResetSettingsUseCase
    val createCategoryUseCase: com.example.offlinereminder.domain.usecase.CreateCategoryUseCase
    val updateCategoryUseCase: com.example.offlinereminder.domain.usecase.UpdateCategoryUseCase
    val deleteCategoryUseCase: com.example.offlinereminder.domain.usecase.DeleteCategoryUseCase
    val createEventTypeUseCase: com.example.offlinereminder.domain.usecase.CreateEventTypeUseCase
    val updateEventTypeUseCase: com.example.offlinereminder.domain.usecase.UpdateEventTypeUseCase
    val deleteEventTypeUseCase: com.example.offlinereminder.domain.usecase.DeleteEventTypeUseCase

    // Phase 12B Use Cases
    val createNotificationProfileUseCase: com.example.offlinereminder.domain.usecase.CreateNotificationProfileUseCase
    val updateNotificationProfileUseCase: com.example.offlinereminder.domain.usecase.UpdateNotificationProfileUseCase
    val getNotificationProfilesUseCase: com.example.offlinereminder.domain.usecase.GetNotificationProfilesUseCase
    val getNotificationProfileUseCase: com.example.offlinereminder.domain.usecase.GetNotificationProfileUseCase
    val deleteNotificationProfileUseCase: com.example.offlinereminder.domain.usecase.DeleteNotificationProfileUseCase
    val setDefaultNotificationProfileUseCase: com.example.offlinereminder.domain.usecase.SetDefaultNotificationProfileUseCase

    val addCustomSoundUseCase: com.example.offlinereminder.domain.usecase.AddCustomSoundUseCase
    val updateCustomSoundUseCase: com.example.offlinereminder.domain.usecase.UpdateCustomSoundUseCase
    val getCustomSoundsUseCase: com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
    val getCustomSoundUseCase: com.example.offlinereminder.domain.usecase.GetCustomSoundUseCase
    val deleteCustomSoundUseCase: com.example.offlinereminder.domain.usecase.DeleteCustomSoundUseCase

    val createEventTemplateUseCase: com.example.offlinereminder.domain.usecase.CreateEventTemplateUseCase
    val updateEventTemplateUseCase: com.example.offlinereminder.domain.usecase.UpdateEventTemplateUseCase
    val getEventTemplatesUseCase: com.example.offlinereminder.domain.usecase.GetEventTemplatesUseCase
    val getEventTemplateUseCase: com.example.offlinereminder.domain.usecase.GetEventTemplateUseCase
    val deleteEventTemplateUseCase: com.example.offlinereminder.domain.usecase.DeleteEventTemplateUseCase
    val createEventFromTemplateUseCase: com.example.offlinereminder.domain.usecase.CreateEventFromTemplateUseCase

    val setEventFavoriteUseCase: com.example.offlinereminder.domain.usecase.SetEventFavoriteUseCase
    val toggleEventFavoriteUseCase: com.example.offlinereminder.domain.usecase.ToggleEventFavoriteUseCase
    val getFavoriteEventsUseCase: com.example.offlinereminder.domain.usecase.GetFavoriteEventsUseCase

    val restoreArchivedEventUseCase: com.example.offlinereminder.domain.usecase.RestoreArchivedEventUseCase
    val restoreTrashedEventUseCase: com.example.offlinereminder.domain.usecase.RestoreTrashedEventUseCase
    val permanentlyDeleteEventUseCase: com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
    val emptyTrashUseCase: com.example.offlinereminder.domain.usecase.EmptyTrashUseCase
    val purgeExpiredTrashUseCase: com.example.offlinereminder.domain.usecase.PurgeExpiredTrashUseCase

    val duplicateEventUseCase: com.example.offlinereminder.domain.usecase.DuplicateEventUseCase
    val detectEventConflictsUseCase: com.example.offlinereminder.domain.usecase.DetectEventConflictsUseCase

    val addEventDependencyUseCase: com.example.offlinereminder.domain.usecase.AddEventDependencyUseCase
    val removeEventDependencyUseCase: com.example.offlinereminder.domain.usecase.RemoveEventDependencyUseCase
    val getEventDependenciesUseCase: com.example.offlinereminder.domain.usecase.GetEventDependenciesUseCase
    val getEventDependentsUseCase: com.example.offlinereminder.domain.usecase.GetEventDependentsUseCase
    val addEventRelationshipUseCase: com.example.offlinereminder.domain.usecase.AddEventRelationshipUseCase
    val removeEventRelationshipUseCase: com.example.offlinereminder.domain.usecase.RemoveEventRelationshipUseCase
    val getEventRelationshipsUseCase: com.example.offlinereminder.domain.usecase.GetEventRelationshipsUseCase
}
