package com.example.offlinereminder

import android.app.Application
import com.example.offlinereminder.di.AppContainer
import com.example.offlinereminder.di.DefaultAppContainer

/**
 * Application class for the Offline Reminder app.
 * Owns the dependency injection [AppContainer].
 */
class OfflineReminderApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
