package com.example.offlinereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinereminder.di.ViewModelFactory
import com.example.offlinereminder.domain.model.ThemeMode
import com.example.offlinereminder.presentation.main.MainScreen
import com.example.offlinereminder.presentation.settings.SettingsViewModel
import com.example.offlinereminder.ui.theme.OfflineReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as OfflineReminderApplication).container
        val viewModelFactory = ViewModelFactory(appContainer)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            val settingsState by settingsViewModel.uiState.collectAsState()
            val isDarkTheme = when (settingsState.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            OfflineReminderTheme(darkTheme = isDarkTheme) {
                MainScreen(viewModelFactory = viewModelFactory)
            }
        }
    }
}
