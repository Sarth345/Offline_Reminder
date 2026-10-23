package com.example.offlinereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinereminder.di.ViewModelFactory
import com.example.offlinereminder.domain.model.ThemeMode
import com.example.offlinereminder.presentation.main.MainScreen
import com.example.offlinereminder.presentation.security.AppLockScreen
import com.example.offlinereminder.presentation.settings.SettingsViewModel
import com.example.offlinereminder.presentation.startup.BrandedStartupScreen
import com.example.offlinereminder.ui.theme.OfflineReminderTheme

class MainActivity : ComponentActivity() {

    private val appContainer by lazy {
        (application as OfflineReminderApplication).container
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModelFactory = ViewModelFactory(appContainer)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settingsState by settingsViewModel.uiState.collectAsState()
            val isDarkTheme = when (settingsState.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val isLocked by appContainer.appLockCoordinator.isLocked.collectAsState()
            var isStartupComplete by rememberSaveable { mutableStateOf(false) }

            OfflineReminderTheme(darkTheme = isDarkTheme) {
                Crossfade(targetState = isStartupComplete, label = "StartupCrossfade") { complete ->
                    if (!complete) {
                        BrandedStartupScreen(onStartupFinished = { isStartupComplete = true })
                    } else if (isLocked) {
                        AppLockScreen(appLockCoordinator = appContainer.appLockCoordinator)
                    } else {
                        MainScreen(viewModelFactory = viewModelFactory)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.appLockCoordinator.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        appContainer.appLockCoordinator.onAppBackgrounded()
    }
}
