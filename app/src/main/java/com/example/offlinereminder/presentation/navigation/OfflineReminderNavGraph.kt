package com.example.offlinereminder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.offlinereminder.di.ViewModelFactory
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.presentation.archive.ArchiveScreen
import com.example.offlinereminder.presentation.archive.ArchiveViewModel
import com.example.offlinereminder.presentation.calendar.CalendarScreen
import com.example.offlinereminder.presentation.calendar.CalendarViewModel
import com.example.offlinereminder.presentation.dashboard.DashboardScreen
import com.example.offlinereminder.presentation.dashboard.DashboardViewModel
import com.example.offlinereminder.presentation.eventdetail.EventDetailScreen
import com.example.offlinereminder.presentation.eventdetail.EventDetailViewModel
import com.example.offlinereminder.presentation.eventeditor.EventEditorScreen
import com.example.offlinereminder.presentation.eventeditor.EventEditorViewModel
import com.example.offlinereminder.presentation.eventlist.EventListAction
import com.example.offlinereminder.presentation.eventlist.EventListScreen
import com.example.offlinereminder.presentation.eventlist.EventListViewModel
import com.example.offlinereminder.presentation.groups.GroupDetailScreen
import com.example.offlinereminder.presentation.groups.GroupDetailViewModel
import com.example.offlinereminder.presentation.groups.GroupsScreen
import com.example.offlinereminder.presentation.groups.GroupsViewModel
import com.example.offlinereminder.presentation.notificationprofiles.NotificationProfilesScreen
import com.example.offlinereminder.presentation.notificationprofiles.NotificationProfilesViewModel
import com.example.offlinereminder.presentation.search.SearchScreen
import com.example.offlinereminder.presentation.search.SearchViewModel
import com.example.offlinereminder.presentation.settings.NotificationAlarmSettingsScreen
import com.example.offlinereminder.presentation.settings.SettingsScreen
import com.example.offlinereminder.presentation.settings.SettingsViewModel
import com.example.offlinereminder.presentation.templates.TemplatesScreen
import com.example.offlinereminder.presentation.templates.TemplatesViewModel
import com.example.offlinereminder.presentation.trash.TrashScreen
import com.example.offlinereminder.presentation.trash.TrashViewModel
import com.example.offlinereminder.presentation.upcoming.UpcomingScreen
import com.example.offlinereminder.presentation.upcoming.UpcomingViewModel

@Composable
fun OfflineReminderNavGraph(
    navController: NavHostController,
    viewModelFactory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        // 1. Dashboard / Today
        composable(route = Screen.Dashboard.route) {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                },
                onNavigateToAddEvent = {
                    navController.navigate(Screen.EventEditor.createRoute())
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // 2. Upcoming Agenda
        composable(route = Screen.Upcoming.route) {
            val upcomingViewModel: UpcomingViewModel = viewModel(factory = viewModelFactory)
            UpcomingScreen(
                viewModel = upcomingViewModel,
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                },
                onNavigateToAddEvent = {
                    navController.navigate(Screen.EventEditor.createRoute())
                }
            )
        }

        // 3. All Events / Reminders
        composable(route = Screen.EventList.route) {
            val eventListViewModel: EventListViewModel = viewModel(factory = viewModelFactory)
            EventListScreen(
                viewModel = eventListViewModel,
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                },
                onNavigateToAddEvent = {
                    navController.navigate(Screen.EventEditor.createRoute())
                }
            )
        }

        // 4. Completed / Archived Reminders
        composable(route = Screen.Completed.route) {
            val eventListViewModel: EventListViewModel = viewModel(
                key = "completed_list",
                factory = viewModelFactory
            )
            eventListViewModel.onAction(EventListAction.FilterStatus(EventLifecycleStatus.ARCHIVED))
            EventListScreen(
                viewModel = eventListViewModel,
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                },
                onNavigateToAddEvent = {
                    navController.navigate(Screen.EventEditor.createRoute())
                }
            )
        }

        // 5. Calendar
        composable(route = Screen.Calendar.route) {
            val calendarViewModel: CalendarViewModel = viewModel(factory = viewModelFactory)
            CalendarScreen(
                viewModel = calendarViewModel,
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                },
                onNavigateToAddEventWithDate = { localDate ->
                    navController.navigate(Screen.EventEditor.createRoute(initialDate = localDate.toString()))
                }
            )
        }

        // 6. Groups List
        composable(route = Screen.Groups.route) {
            val groupsViewModel: GroupsViewModel = viewModel(factory = viewModelFactory)
            GroupsScreen(
                viewModel = groupsViewModel,
                onNavigateToGroupDetails = { groupId ->
                    navController.navigate(Screen.GroupDetails.createRoute(groupId))
                }
            )
        }

        // 7. Group Detail
        composable(
            route = Screen.GroupDetails.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupDetailViewModel: GroupDetailViewModel = viewModel(
                key = "group_detail_$groupId",
                factory = viewModelFactory
            )
            GroupDetailScreen(
                groupId = groupId,
                viewModel = groupDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                }
            )
        }

        // 6. Event Detail
        composable(
            route = Screen.EventDetails.route,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            val eventDetailViewModel: EventDetailViewModel = viewModel(
                key = "detail_$eventId",
                factory = viewModelFactory
            )
            EventDetailScreen(
                eventId = eventId,
                viewModel = eventDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { editId ->
                    navController.navigate(Screen.EventEditor.createRoute(eventId = editId))
                },
                onNavigateToEvent = { targetId ->
                    navController.navigate(Screen.EventDetails.createRoute(targetId))
                },
                onNavigateToAddSubEvent = { parentId ->
                    navController.navigate(Screen.EventEditor.createRoute())
                }
            )
        }

        // 7. Event Editor (Create / Edit)
        composable(
            route = Screen.EventEditor.route,
            arguments = listOf(
                navArgument("eventId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("initialDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("eventTypeId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId")
            val initialDate = backStackEntry.arguments?.getString("initialDate")
            val eventTypeId = backStackEntry.arguments?.getString("eventTypeId")

            val eventEditorViewModel: EventEditorViewModel = viewModel(
                key = "editor_${eventId ?: "new"}",
                factory = viewModelFactory
            )
            EventEditorScreen(
                eventId = eventId,
                initialDate = initialDate,
                initialEventTypeId = eventTypeId,
                viewModel = eventEditorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 8. Search & Advanced Discovery
        composable(route = Screen.Search.route) {
            val searchViewModel: SearchViewModel = viewModel(factory = viewModelFactory)
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetails = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                }
            )
        }

        // 9. Settings & Data Management / Backup
        composable(route = Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotificationAlarmSettings = { navController.navigate(Screen.NotificationAlarmSettings.route) },
                onNavigateToTemplates = { navController.navigate(Screen.Templates.route) },
                onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                onNavigateToArchive = { navController.navigate(Screen.Archive.route) },
                onNavigateToNotificationProfiles = { navController.navigate(Screen.NotificationProfiles.route) },
                onNavigateToAppLock = { navController.navigate(Screen.AppLockSettings.route) },
                onNavigateToSavedLocations = { navController.navigate(Screen.SavedLocations.route) },
                onNavigateToExport = { navController.navigate(Screen.Export.route) },
                onNavigateToIcsImport = { navController.navigate(Screen.IcsImport.route) },
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) }
            )
        }

        // 9b. Notifications & Alarms Settings
        composable(route = Screen.NotificationAlarmSettings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            NotificationAlarmSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotificationProfiles = { navController.navigate(Screen.NotificationProfiles.route) }
            )
        }

        // 10. Templates Management
        composable(route = Screen.Templates.route) {
            val templatesViewModel: TemplatesViewModel = viewModel(factory = viewModelFactory)
            TemplatesScreen(
                viewModel = templatesViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEvent = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                }
            )
        }

        // 11. Trash Management
        composable(route = Screen.Trash.route) {
            val trashViewModel: TrashViewModel = viewModel(factory = viewModelFactory)
            TrashScreen(
                viewModel = trashViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 12. Archive Management
        composable(route = Screen.Archive.route) {
            val archiveViewModel: ArchiveViewModel = viewModel(factory = viewModelFactory)
            ArchiveScreen(
                viewModel = archiveViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEvent = { eventId ->
                    navController.navigate(Screen.EventDetails.createRoute(eventId))
                }
            )
        }

        // 13. Notification Profiles & Sound Management
        composable(route = Screen.NotificationProfiles.route) {
            val profilesViewModel: NotificationProfilesViewModel = viewModel(factory = viewModelFactory)
            NotificationProfilesScreen(
                viewModel = profilesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 14. App Lock Security Settings
        composable(route = Screen.AppLockSettings.route) {
            com.example.offlinereminder.presentation.security.AppLockSettingsScreen(
                getAppLockSettingsUseCase = viewModelFactory.appContainer.getAppLockSettingsUseCase,
                updateAppLockSettingsUseCase = viewModelFactory.appContainer.updateAppLockSettingsUseCase,
                appLockCoordinator = viewModelFactory.appContainer.appLockCoordinator,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 15. Saved Locations Management
        composable(route = Screen.SavedLocations.route) {
            val savedLocationsViewModel: com.example.offlinereminder.presentation.locations.SavedLocationsViewModel =
                viewModel(factory = viewModelFactory)
            com.example.offlinereminder.presentation.locations.SavedLocationsScreen(
                viewModel = savedLocationsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 16. Standalone Export
        composable(route = Screen.Export.route) {
            val exportViewModel: com.example.offlinereminder.presentation.data.ExportViewModel =
                viewModel(factory = viewModelFactory)
            com.example.offlinereminder.presentation.data.ExportScreen(
                viewModel = exportViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 17. iCalendar (.ics) Import
        composable(route = Screen.IcsImport.route) {
            val icsImportViewModel: com.example.offlinereminder.presentation.data.IcsImportViewModel =
                viewModel(factory = viewModelFactory)
            com.example.offlinereminder.presentation.data.IcsImportScreen(
                viewModel = icsImportViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 18. Statistics & Insights
        composable(route = Screen.Statistics.route) {
            val statisticsViewModel: com.example.offlinereminder.presentation.statistics.StatisticsViewModel =
                viewModel(factory = viewModelFactory)
            com.example.offlinereminder.presentation.statistics.StatisticsScreen(
                viewModel = statisticsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
