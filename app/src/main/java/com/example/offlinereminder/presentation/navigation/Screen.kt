package com.example.offlinereminder.presentation.navigation

/**
 * Type-safe navigation destinations for the Offline Reminder application.
 */
sealed class Screen(val route: String) {

    // Primary Bottom Navigation Destinations
    data object Dashboard : Screen("dashboard")
    data object Upcoming : Screen("upcoming")
    data object EventList : Screen("events")
    data object Calendar : Screen("calendar")
    data object Groups : Screen("groups")
    data object Completed : Screen("completed")
    data object Settings : Screen("settings")

    // Secondary / Modal Destinations
    data object Search : Screen("search")
    data object Archive : Screen("archive")
    data object Trash : Screen("trash")
    data object Templates : Screen("templates")
    data object NotificationProfiles : Screen("notification_profiles")
    data object NotificationAlarmSettings : Screen("notification_alarm_settings")
    data object AppLock : Screen("app_lock")
    data object AppLockSettings : Screen("app_lock_settings")
    data object SavedLocations : Screen("saved_locations")
    data object Export : Screen("export")
    data object IcsImport : Screen("ics_import")
    data object Statistics : Screen("statistics")

    data object EventEditor : Screen("event_editor?eventId={eventId}&initialDate={initialDate}&eventTypeId={eventTypeId}") {
        fun createRoute(
            eventId: String? = null,
            initialDate: String? = null,
            eventTypeId: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (eventId != null) params.add("eventId=$eventId")
            if (initialDate != null) params.add("initialDate=$initialDate")
            if (eventTypeId != null) params.add("eventTypeId=$eventTypeId")
            return if (params.isEmpty()) "event_editor" else "event_editor?${params.joinToString("&")}"
        }
    }

    data object EventDetails : Screen("event_details/{eventId}") {
        fun createRoute(eventId: String): String = "event_details/$eventId"
    }

    data object GroupDetails : Screen("group_details/{groupId}") {
        fun createRoute(groupId: String): String = "group_details/$groupId"
    }
}
