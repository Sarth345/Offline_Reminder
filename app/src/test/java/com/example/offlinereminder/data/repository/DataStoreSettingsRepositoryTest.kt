package com.example.offlinereminder.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.offlinereminder.domain.model.DateFormatPreference
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun getSettings_returnsInitialDefaultSettings() = runTest {
        val file = File(tempFolder.root, "settings_default.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        val settings = repository.getSettings()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertFalse(settings.use24HourFormat)
        assertEquals(DateFormatPreference.SYSTEM_DEFAULT, settings.dateFormat)
        assertEquals(DayOfWeek.MONDAY, settings.firstDayOfWeek)
        assertEquals(Priority.NORMAL, settings.defaultEventPriority)
        assertEquals(60, settings.defaultEventDurationMinutes)
        assertEquals(15, settings.defaultSnoozeDurationMinutes)
        assertTrue(settings.soundEnabled)
        assertTrue(settings.vibrationEnabled)
        assertTrue(settings.notificationsEnabled)
        assertTrue(settings.alarmModeEnabled)
        assertEquals(SoundSourceType.DEFAULT, settings.notificationSound.type)
        assertEquals(SoundSourceType.DEFAULT, settings.alarmSound.type)
        assertFalse(settings.quietHours.enabled)
    }

    @Test
    fun updateSettings_persistsAndObservesChanges() = runTest {
        val file = File(tempFolder.root, "settings_update.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        val updateRes = repository.updateSettings { current ->
            current.copy(
                themeMode = ThemeMode.DARK,
                use24HourFormat = true,
                dateFormat = DateFormatPreference.ISO,
                firstDayOfWeek = DayOfWeek.SUNDAY,
                defaultEventPriority = Priority.HIGH,
                defaultEventDurationMinutes = 30,
                defaultSnoozeDurationMinutes = 15,
                soundEnabled = false,
                vibrationEnabled = false,
                quietHours = QuietHours(
                    enabled = true,
                    startLocalTime = LocalTime.of(23, 0),
                    endLocalTime = LocalTime.of(6, 30),
                    policy = QuietHoursPolicy.SILENT,
                    allowCritical = false
                )
            )
        }
        assertTrue(updateRes.isSuccess)

        val updated = repository.getSettings()
        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertTrue(updated.use24HourFormat)
        assertEquals(DateFormatPreference.ISO, updated.dateFormat)
        assertEquals(DayOfWeek.SUNDAY, updated.firstDayOfWeek)
        assertEquals(Priority.HIGH, updated.defaultEventPriority)
        assertEquals(30, updated.defaultEventDurationMinutes)
        assertEquals(15, updated.defaultSnoozeDurationMinutes)
        assertFalse(updated.soundEnabled)
        assertFalse(updated.vibrationEnabled)
        assertTrue(updated.quietHours.enabled)
        assertEquals(LocalTime.of(23, 0), updated.quietHours.startLocalTime)
        assertEquals(LocalTime.of(6, 30), updated.quietHours.endLocalTime)
        assertEquals(QuietHoursPolicy.SILENT, updated.quietHours.policy)
        assertFalse(updated.quietHours.allowCritical)
    }

    @Test
    fun resetSettings_clearsPreferencesBackToDefaults() = runTest {
        val file = File(tempFolder.root, "settings_reset.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        val resetRes = repository.resetSettings()
        assertTrue(resetRes.isSuccess)

        val settings = repository.getSettings()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertFalse(settings.use24HourFormat)
        assertEquals(DateFormatPreference.SYSTEM_DEFAULT, settings.dateFormat)
        assertEquals(DayOfWeek.MONDAY, settings.firstDayOfWeek)
        assertEquals(Priority.NORMAL, settings.defaultEventPriority)
        assertEquals(60, settings.defaultEventDurationMinutes)
        assertEquals(15, settings.defaultSnoozeDurationMinutes)
        assertTrue(settings.soundEnabled)
        assertTrue(settings.vibrationEnabled)
        assertTrue(settings.notificationsEnabled)
        assertTrue(settings.alarmModeEnabled)
        assertEquals(SoundSourceType.DEFAULT, settings.notificationSound.type)
        assertEquals(SoundSourceType.DEFAULT, settings.alarmSound.type)
        assertFalse(settings.quietHours.enabled)
    }

    @Test
    fun persistence_survivesRepositoryRecreation_simulatingProcessRestart() = runTest {
        val file = File(tempFolder.root, "settings_restart.preferences_pb")

        // 1. Initial process lifetime: write changes
        val scope1 = CoroutineScope(backgroundScope.coroutineContext + Job())
        val dataStore1 = PreferenceDataStoreFactory.create(
            scope = scope1,
            produceFile = { file }
        )
        val repository1 = DataStoreSettingsRepository(dataStore1)
        repository1.updateSettings {
            it.copy(
                themeMode = ThemeMode.DARK,
                defaultEventPriority = Priority.CRITICAL
            )
        }

        // Cancel scope1 to simulate process termination
        scope1.cancel()

        // 2. Restarted process lifetime: recreate DataStore pointing to same file
        val scope2 = CoroutineScope(backgroundScope.coroutineContext + Job())
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = scope2,
            produceFile = { file }
        )
        val repository2 = DataStoreSettingsRepository(dataStore2)

        // 3. Verify settings persisted across simulated process restart
        val reloadedSettings = repository2.getSettings()
        assertEquals(ThemeMode.DARK, reloadedSettings.themeMode)
        assertEquals(Priority.CRITICAL, reloadedSettings.defaultEventPriority)

        scope2.cancel()
    }

    @Test
    fun updateSettings_persistsSoundAndAlarmConfigurations() = runTest {
        val file = File(tempFolder.root, "settings_sound.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        val customNotificationSound = SoundReference(type = SoundSourceType.SYSTEM, systemUriString = "content://media/internal/audio/media/42")
        val customAlarmSound = SoundReference(type = SoundSourceType.SYSTEM, systemUriString = "content://media/internal/audio/media/99")

        val updateRes = repository.updateSettings { current ->
            current.copy(
                notificationsEnabled = false,
                alarmModeEnabled = false,
                defaultSnoozeDurationMinutes = 30,
                notificationSound = customNotificationSound,
                alarmSound = customAlarmSound
            )
        }
        assertTrue(updateRes.isSuccess)

        val updated = repository.getSettings()
        assertFalse(updated.notificationsEnabled)
        assertFalse(updated.alarmModeEnabled)
        assertEquals(30, updated.defaultSnoozeDurationMinutes)
        assertEquals(customNotificationSound, updated.notificationSound)
        assertEquals(customAlarmSound, updated.alarmSound)
    }
}
