package com.example.offlinereminder.presentation.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.offlinereminder.di.ViewModelFactory
import com.example.offlinereminder.presentation.navigation.OfflineReminderNavGraph
import com.example.offlinereminder.presentation.navigation.Screen

data class BottomNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
)

val BottomNavItems = listOf(
    BottomNavItem(title = "Today", route = Screen.Dashboard.route, icon = Icons.Default.Home),
    BottomNavItem(title = "Upcoming", route = Screen.Upcoming.route, icon = Icons.Default.DateRange),
    BottomNavItem(title = "Reminders", route = Screen.EventList.route, icon = Icons.AutoMirrored.Filled.List),
    BottomNavItem(title = "Completed", route = Screen.Completed.route, icon = Icons.Default.Check)
)

@Composable
fun MainScreen(
    viewModelFactory: ViewModelFactory,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show BottomBar and FAB only on primary top-level tabs
    val isTopLevelDestination = currentRoute in listOf(
        Screen.Dashboard.route,
        Screen.Upcoming.route,
        Screen.EventList.route,
        Screen.Completed.route,
        Screen.Calendar.route
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    BottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            modifier = Modifier.semantics {
                                contentDescription = "Navigate to ${item.title}"
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isTopLevelDestination) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(Screen.EventEditor.createRoute())
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.semantics { contentDescription = "Add new reminder" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        OfflineReminderNavGraph(
            navController = navController,
            viewModelFactory = viewModelFactory,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
