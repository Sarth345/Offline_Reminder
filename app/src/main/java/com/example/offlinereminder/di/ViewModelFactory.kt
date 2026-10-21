package com.example.offlinereminder.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.offlinereminder.presentation.archive.ArchiveViewModel
import com.example.offlinereminder.presentation.calendar.CalendarViewModel
import com.example.offlinereminder.presentation.dashboard.DashboardViewModel
import com.example.offlinereminder.presentation.eventdetail.EventDetailViewModel
import com.example.offlinereminder.presentation.eventeditor.EventEditorViewModel
import com.example.offlinereminder.presentation.eventlist.EventListViewModel
import com.example.offlinereminder.presentation.groups.GroupDetailViewModel
import com.example.offlinereminder.presentation.groups.GroupsViewModel
import com.example.offlinereminder.presentation.notificationprofiles.NotificationProfilesViewModel
import com.example.offlinereminder.presentation.search.SearchViewModel
import com.example.offlinereminder.presentation.settings.SettingsViewModel
import com.example.offlinereminder.presentation.templates.TemplatesViewModel
import com.example.offlinereminder.presentation.trash.TrashViewModel
import com.example.offlinereminder.presentation.upcoming.UpcomingViewModel

/**
 * Standard Android [ViewModelProvider.Factory] supplying application dependencies
 * from [AppContainer] to ViewModels.
 */
class ViewModelFactory(
    val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    getDashboardOccurrencesUseCase = appContainer.getDashboardOccurrencesUseCase,
                    completeOccurrenceActionUseCase = appContainer.completeOccurrenceActionUseCase,
                    skipOccurrenceActionUseCase = appContainer.skipOccurrenceActionUseCase,
                    rescheduleOccurrenceActionUseCase = appContainer.rescheduleOccurrenceActionUseCase,
                    toggleEventFavoriteUseCase = appContainer.toggleEventFavoriteUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(EventListViewModel::class.java) -> {
                EventListViewModel(
                    getEventsUseCase = appContainer.getEventsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    getEventGroupsUseCase = appContainer.getEventGroupsUseCase,
                    getTagsUseCase = appContainer.getTagsUseCase,
                    eventRepository = appContainer.eventRepository,
                    deleteEventUseCase = appContainer.deleteEventUseCase,
                    cancelNotificationsForEventUseCase = appContainer.cancelNotificationsForEventUseCase,
                    scheduleNotificationsForEventUseCase = appContainer.scheduleNotificationsForEventUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(EventDetailViewModel::class.java) -> {
                EventDetailViewModel(
                    getEventByIdUseCase = appContainer.getEventByIdUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    getEventGroupsUseCase = appContainer.getEventGroupsUseCase,
                    getTagsUseCase = appContainer.getTagsUseCase,
                    eventRepository = appContainer.eventRepository,
                    occurrenceExceptionRepository = appContainer.occurrenceExceptionRepository,
                    recurrenceEngine = appContainer.recurrenceEngine,
                    timeProvider = appContainer.timeProvider,
                    completeOccurrenceActionUseCase = appContainer.completeOccurrenceActionUseCase,
                    skipOccurrenceActionUseCase = appContainer.skipOccurrenceActionUseCase,
                    rescheduleOccurrenceActionUseCase = appContainer.rescheduleOccurrenceActionUseCase,
                    deleteEventUseCase = appContainer.deleteEventUseCase,
                    trashEventUseCase = appContainer.trashEventUseCase,
                    restoreTrashedEventUseCase = appContainer.restoreTrashedEventUseCase,
                    archiveEventUseCase = appContainer.archiveEventUseCase,
                    restoreArchivedEventUseCase = appContainer.restoreArchivedEventUseCase,
                    permanentlyDeleteEventUseCase = appContainer.permanentlyDeleteEventUseCase,
                    duplicateEventUseCase = appContainer.duplicateEventUseCase,
                    createEventTemplateUseCase = appContainer.createEventTemplateUseCase,
                    getEventDependenciesUseCase = appContainer.getEventDependenciesUseCase,
                    getEventDependentsUseCase = appContainer.getEventDependentsUseCase,
                    addEventDependencyUseCase = appContainer.addEventDependencyUseCase,
                    removeEventDependencyUseCase = appContainer.removeEventDependencyUseCase,
                    getEventRelationshipsUseCase = appContainer.getEventRelationshipsUseCase,
                    addEventRelationshipUseCase = appContainer.addEventRelationshipUseCase,
                    removeEventRelationshipUseCase = appContainer.removeEventRelationshipUseCase,
                    detectEventConflictsUseCase = appContainer.detectEventConflictsUseCase,
                    getNotificationProfilesUseCase = appContainer.getNotificationProfilesUseCase,
                    getCustomSoundsUseCase = appContainer.getCustomSoundsUseCase,
                    cancelNotificationsForEventUseCase = appContainer.cancelNotificationsForEventUseCase,
                    scheduleNotificationsForEventUseCase = appContainer.scheduleNotificationsForEventUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(EventEditorViewModel::class.java) -> {
                EventEditorViewModel(
                    getEventByIdUseCase = appContainer.getEventByIdUseCase,
                    createEventUseCase = appContainer.createEventUseCase,
                    updateEventUseCase = appContainer.updateEventUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    getEventGroupsUseCase = appContainer.getEventGroupsUseCase,
                    getTagsUseCase = appContainer.getTagsUseCase,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider,
                    settingsRepository = appContainer.settingsRepository,
                    eventRepository = appContainer.eventRepository,
                    getEventTemplatesUseCase = appContainer.getEventTemplatesUseCase,
                    getEventTemplateUseCase = appContainer.getEventTemplateUseCase,
                    getNotificationProfilesUseCase = appContainer.getNotificationProfilesUseCase,
                    getCustomSoundsUseCase = appContainer.getCustomSoundsUseCase,
                    detectEventConflictsUseCase = appContainer.detectEventConflictsUseCase,
                    editRecurringSeriesUseCase = appContainer.editRecurringSeriesUseCase,
                    getSavedLocationsUseCase = appContainer.getSavedLocationsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(UpcomingViewModel::class.java) -> {
                UpcomingViewModel(
                    getUpcomingOccurrencesUseCase = appContainer.getUpcomingOccurrencesUseCase,
                    completeOccurrenceActionUseCase = appContainer.completeOccurrenceActionUseCase,
                    skipOccurrenceActionUseCase = appContainer.skipOccurrenceActionUseCase,
                    rescheduleOccurrenceActionUseCase = appContainer.rescheduleOccurrenceActionUseCase,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(CalendarViewModel::class.java) -> {
                CalendarViewModel(
                    getCalendarOccurrencesUseCase = appContainer.getCalendarOccurrencesUseCase,
                    completeOccurrenceActionUseCase = appContainer.completeOccurrenceActionUseCase,
                    skipOccurrenceActionUseCase = appContainer.skipOccurrenceActionUseCase,
                    rescheduleOccurrenceActionUseCase = appContainer.rescheduleOccurrenceActionUseCase,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(GroupsViewModel::class.java) -> {
                GroupsViewModel(
                    getEventGroupsUseCase = appContainer.getEventGroupsUseCase,
                    saveGroupUseCase = appContainer.saveGroupUseCase,
                    deleteGroupUseCase = appContainer.deleteGroupUseCase,
                    eventGroupRepository = appContainer.eventGroupRepository,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(GroupDetailViewModel::class.java) -> {
                GroupDetailViewModel(
                    getGroupByIdUseCase = appContainer.getGroupByIdUseCase,
                    getActiveEventsForGroupUseCase = appContainer.getActiveEventsForGroupUseCase,
                    saveGroupUseCase = appContainer.saveGroupUseCase,
                    deleteGroupUseCase = appContainer.deleteGroupUseCase,
                    eventGroupRepository = appContainer.eventGroupRepository,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(
                    searchEventsUseCase = appContainer.searchEventsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    getEventGroupsUseCase = appContainer.getEventGroupsUseCase,
                    getTagsUseCase = appContainer.getTagsUseCase,
                    eventRepository = appContainer.eventRepository,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    getSettingsUseCase = appContainer.getSettingsUseCase,
                    updateSettingsUseCase = appContainer.updateSettingsUseCase,
                    resetSettingsUseCase = appContainer.resetSettingsUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    createCategoryUseCase = appContainer.createCategoryUseCase,
                    updateCategoryUseCase = appContainer.updateCategoryUseCase,
                    deleteCategoryUseCase = appContainer.deleteCategoryUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    createEventTypeUseCase = appContainer.createEventTypeUseCase,
                    updateEventTypeUseCase = appContainer.updateEventTypeUseCase,
                    deleteEventTypeUseCase = appContainer.deleteEventTypeUseCase,
                    exportBackupUseCase = appContainer.exportBackupUseCase,
                    restoreBackupUseCase = appContainer.restoreBackupUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(com.example.offlinereminder.presentation.quickadd.QuickAddViewModel::class.java) -> {
                com.example.offlinereminder.presentation.quickadd.QuickAddViewModel(
                    parser = appContainer.quickAddParser,
                    createEventFromQuickAddUseCase = appContainer.createEventFromQuickAddUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(TemplatesViewModel::class.java) -> {
                TemplatesViewModel(
                    getEventTemplatesUseCase = appContainer.getEventTemplatesUseCase,
                    createEventFromTemplateUseCase = appContainer.createEventFromTemplateUseCase,
                    deleteEventTemplateUseCase = appContainer.deleteEventTemplateUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(TrashViewModel::class.java) -> {
                TrashViewModel(
                    eventRepository = appContainer.eventRepository,
                    restoreTrashedEventUseCase = appContainer.restoreTrashedEventUseCase,
                    permanentlyDeleteEventUseCase = appContainer.permanentlyDeleteEventUseCase,
                    emptyTrashUseCase = appContainer.emptyTrashUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    scheduleNotificationsForEventUseCase = appContainer.scheduleNotificationsForEventUseCase,
                    cancelNotificationsForEventUseCase = appContainer.cancelNotificationsForEventUseCase,
                    timeProvider = appContainer.timeProvider,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(ArchiveViewModel::class.java) -> {
                ArchiveViewModel(
                    eventRepository = appContainer.eventRepository,
                    restoreArchivedEventUseCase = appContainer.restoreArchivedEventUseCase,
                    trashEventUseCase = appContainer.trashEventUseCase,
                    permanentlyDeleteEventUseCase = appContainer.permanentlyDeleteEventUseCase,
                    getCategoriesUseCase = appContainer.getCategoriesUseCase,
                    getEventTypesUseCase = appContainer.getEventTypesUseCase,
                    scheduleNotificationsForEventUseCase = appContainer.scheduleNotificationsForEventUseCase,
                    cancelNotificationsForEventUseCase = appContainer.cancelNotificationsForEventUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(NotificationProfilesViewModel::class.java) -> {
                NotificationProfilesViewModel(
                    getNotificationProfilesUseCase = appContainer.getNotificationProfilesUseCase,
                    createNotificationProfileUseCase = appContainer.createNotificationProfileUseCase,
                    updateNotificationProfileUseCase = appContainer.updateNotificationProfileUseCase,
                    deleteNotificationProfileUseCase = appContainer.deleteNotificationProfileUseCase,
                    setDefaultNotificationProfileUseCase = appContainer.setDefaultNotificationProfileUseCase,
                    getCustomSoundsUseCase = appContainer.getCustomSoundsUseCase,
                    addCustomSoundUseCase = appContainer.addCustomSoundUseCase,
                    deleteCustomSoundUseCase = appContainer.deleteCustomSoundUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(com.example.offlinereminder.presentation.locations.SavedLocationsViewModel::class.java) -> {
                com.example.offlinereminder.presentation.locations.SavedLocationsViewModel(
                    getSavedLocationsUseCase = appContainer.getSavedLocationsUseCase,
                    saveSavedLocationUseCase = appContainer.saveSavedLocationUseCase,
                    deleteSavedLocationUseCase = appContainer.deleteSavedLocationUseCase,
                    toggleSavedLocationFavoriteUseCase = appContainer.toggleSavedLocationFavoriteUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(com.example.offlinereminder.presentation.data.ExportViewModel::class.java) -> {
                com.example.offlinereminder.presentation.data.ExportViewModel(
                    exportEventsUseCase = appContainer.exportEventsUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(com.example.offlinereminder.presentation.data.IcsImportViewModel::class.java) -> {
                com.example.offlinereminder.presentation.data.IcsImportViewModel(
                    previewIcsImportUseCase = appContainer.previewIcsImportUseCase,
                    confirmIcsImportUseCase = appContainer.confirmIcsImportUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            modelClass.isAssignableFrom(com.example.offlinereminder.presentation.statistics.StatisticsViewModel::class.java) -> {
                com.example.offlinereminder.presentation.statistics.StatisticsViewModel(
                    getStatisticsUseCase = appContainer.getStatisticsUseCase,
                    getProductivityInsightsUseCase = appContainer.getProductivityInsightsUseCase,
                    dispatcherProvider = appContainer.dispatcherProvider
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
