package com.example.offlinereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.offlinereminder.di.ViewModelFactory
import com.example.offlinereminder.presentation.main.MainScreen
import com.example.offlinereminder.ui.theme.OfflineReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as OfflineReminderApplication).container
        val viewModelFactory = ViewModelFactory(appContainer)

        setContent {
            OfflineReminderTheme {
                MainScreen(viewModelFactory = viewModelFactory)
            }
        }
    }
}
