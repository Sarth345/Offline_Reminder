package com.example.offlinereminder.data.notification

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.offlinereminder.data.local.mapper.toBackupDto
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.repository.DataStoreSettingsRepository
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.QuietHoursBackupDto
import com.example.offlinereminder.domain.backup.SettingsBackupDto
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.BuiltInSoundCatalog
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.NotificationConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationAndAlarmConfigurationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun channelSelection_resolvesCorrectChannelIds() {
        assertEquals("offline_reminders_default", NotificationConstants.CHANNEL_ID_DEFAULT)
        assertEquals("offline_reminders_silent", NotificationConstants.CHANNEL_ID_SILENT)
        assertEquals("offline_reminders_alarm", NotificationConstants.CHANNEL_ID_ALARM)
    }

    @Test
    fun defaultSettings_hasFifteenMinuteSnoozeAndSoundDefaults() = runTest {
        val file = File(tempFolder.root, "notif_defaults.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)
        val settings = repository.getSettings()

        assertTrue(settings.notificationsEnabled)
        assertTrue(settings.alarmModeEnabled)
        assertEquals(15, settings.defaultSnoozeDurationMinutes)
        assertEquals(SoundSourceType.DEFAULT, settings.notificationSound.type)
        assertEquals(SoundSourceType.DEFAULT, settings.alarmSound.type)
    }

    @Test
    fun snoozeDurations_persistsCustomDuration() = runTest {
        val file = File(tempFolder.root, "snooze_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        val updateCustom = repository.updateSettings { it.copy(defaultSnoozeDurationMinutes = 45) }
        assertTrue(updateCustom.isSuccess)
        assertEquals(45, repository.getSettings().defaultSnoozeDurationMinutes)
    }

    @Test
    fun soundReference_persistsVariousSoundSourceTypes() = runTest {
        val file = File(tempFolder.root, "sounds_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        // 1. System sound URI
        val systemSound = SoundReference(
            type = SoundSourceType.SYSTEM,
            systemUriString = "content://media/internal/audio/media/105"
        )
        repository.updateSettings { it.copy(notificationSound = systemSound) }
        assertEquals(systemSound, repository.getSettings().notificationSound)

        // 2. Custom local file sound
        val customSound = SoundReference(
            type = SoundSourceType.CUSTOM,
            customSoundId = "snd_alarm_chime_01"
        )
        repository.updateSettings { it.copy(alarmSound = customSound) }
        assertEquals(customSound, repository.getSettings().alarmSound)

        // 3. Silent sound
        val silentSound = SoundReference(type = SoundSourceType.SILENT)
        repository.updateSettings { it.copy(notificationSound = silentSound) }
        assertEquals(SoundSourceType.SILENT, repository.getSettings().notificationSound.type)

        // 4. Reset to default
        val defaultSound = SoundReference(type = SoundSourceType.DEFAULT)
        repository.updateSettings { it.copy(alarmSound = defaultSound) }
        assertEquals(SoundSourceType.DEFAULT, repository.getSettings().alarmSound.type)
    }

    @Test
    fun backupAndRestore_settingsDtoMappingPreservesAllNotificationAndAlarmFields() {
        val appSettings = AppSettings(
            notificationsEnabled = true,
            alarmModeEnabled = true,
            defaultSnoozeDurationMinutes = 15,
            notificationSound = SoundReference(
                type = SoundSourceType.SYSTEM,
                systemUriString = "content://media/internal/audio/media/42"
            ),
            alarmSound = SoundReference(
                type = SoundSourceType.CUSTOM,
                customSoundId = "custom_bell_1"
            )
        )

        val dto = appSettings.toBackupDto()
        assertEquals(15, dto.defaultSnoozeDurationMinutes)
        assertEquals(true, dto.notificationsEnabled)
        assertEquals(true, dto.alarmModeEnabled)
        assertEquals("SYSTEM", dto.notificationSoundType)
        assertEquals("content://media/internal/audio/media/42", dto.notificationSoundSysUri)
        assertEquals("CUSTOM", dto.alarmSoundType)
        assertEquals("custom_bell_1", dto.alarmSoundCustomId)

        val restored = dto.toDomain()
        assertEquals(appSettings.notificationsEnabled, restored.notificationsEnabled)
        assertEquals(appSettings.alarmModeEnabled, restored.alarmModeEnabled)
        assertEquals(appSettings.defaultSnoozeDurationMinutes, restored.defaultSnoozeDurationMinutes)
        assertEquals(appSettings.notificationSound, restored.notificationSound)
        assertEquals(appSettings.alarmSound, restored.alarmSound)
    }

    @Test
    fun backupAndRestore_legacyBackupWithoutSoundFields_restoresGracefullyWithDefaults() {
        val legacyDto = SettingsBackupDto(
            themeMode = "SYSTEM",
            use24HourFormat = false,
            dateFormat = "SYSTEM_DEFAULT",
            firstDayOfWeek = "MONDAY",
            defaultEventPriority = "NORMAL",
            defaultEventDurationMinutes = 60,
            defaultSnoozeDurationMinutes = 10,
            soundEnabled = true,
            vibrationEnabled = true,
            quietHours = QuietHoursBackupDto(false, "22:00", "07:00", "SILENT", true)
            // sound and notification fields absent (null)
        )

        val restored = legacyDto.toDomain()
        assertTrue(restored.notificationsEnabled)
        assertTrue(restored.alarmModeEnabled)
        assertEquals(10, restored.defaultSnoozeDurationMinutes)
        assertEquals(SoundSourceType.DEFAULT, restored.notificationSound.type)
        assertEquals(SoundSourceType.DEFAULT, restored.alarmSound.type)
    }

    @Test
    fun backupPayload_serializationRoundtripWithNewSettings() {
        val settingsDto = AppSettings(
            defaultSnoozeDurationMinutes = 15,
            notificationSound = SoundReference(type = SoundSourceType.SYSTEM, systemUriString = "content://notif"),
            alarmSound = SoundReference(type = SoundSourceType.SYSTEM, systemUriString = "content://alarm")
        ).toBackupDto()

        val payload = BackupPayload(
            backupVersion = 1,
            appVersion = "1.1",
            exportedAt = "2026-10-23T19:00:00Z",
            settings = settingsDto
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        assertTrue(encoded.contains("defaultSnoozeDurationMinutes"))
        assertTrue(encoded.contains("content://notif"))

        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertNotNull(decoded.settings)
        assertEquals(15, decoded.settings?.defaultSnoozeDurationMinutes)
        assertEquals("content://notif", decoded.settings?.notificationSoundSysUri)
        assertEquals("content://alarm", decoded.settings?.alarmSoundSysUri)
    }

    @Test
    fun builtInCatalog_containsExpectedTonesAndTunes() {
        val tones = BuiltInSoundCatalog.NOTIFICATION_TONES
        assertEquals(5, tones.size)
        assertEquals(listOf("planerize_bell", "soft_bell", "gentle_chime", "digital_alert", "short_beep"), tones.map { it.key })
        tones.forEach { tone ->
            assertTrue(tone.displayName.isNotBlank())
        }

        val tunes = BuiltInSoundCatalog.ALARM_TUNES
        assertEquals(5, tunes.size)
        assertEquals(listOf("planerize_alarm", "classic_alarm", "digital_alarm", "rising_alert", "strong_alarm"), tunes.map { it.key })
        tunes.forEach { tune ->
            assertTrue(tune.displayName.isNotBlank())
        }
    }

    @Test
    fun soundReference_builtInSound_persistedAndRetrievedInDatastore() = runTest {
        val file = File(tempFolder.root, "builtin_sound_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        val builtInNotif = SoundReference(type = SoundSourceType.BUILT_IN, builtInSoundKey = "planerize_bell")
        val builtInAlarm = SoundReference(type = SoundSourceType.BUILT_IN, builtInSoundKey = "classic_alarm")

        repository.updateSettings {
            it.copy(
                notificationSound = builtInNotif,
                alarmSound = builtInAlarm
            )
        }

        val settings = repository.getSettings()
        assertEquals(SoundSourceType.BUILT_IN, settings.notificationSound.type)
        assertEquals("planerize_bell", settings.notificationSound.builtInSoundKey)
        assertEquals(SoundSourceType.BUILT_IN, settings.alarmSound.type)
        assertEquals("classic_alarm", settings.alarmSound.builtInSoundKey)
    }

    @Test
    fun backupAndRestore_builtInSoundMappingPreserved() {
        val appSettings = AppSettings(
            notificationSound = SoundReference(type = SoundSourceType.BUILT_IN, builtInSoundKey = "gentle_chime"),
            alarmSound = SoundReference(type = SoundSourceType.BUILT_IN, builtInSoundKey = "strong_alarm")
        )

        val dto = appSettings.toBackupDto()
        assertEquals("BUILT_IN", dto.notificationSoundType)
        assertEquals("gentle_chime", dto.notificationSoundBuiltInKey)
        assertEquals("BUILT_IN", dto.alarmSoundType)
        assertEquals("strong_alarm", dto.alarmSoundBuiltInKey)

        val restored = dto.toDomain()
        assertEquals(SoundSourceType.BUILT_IN, restored.notificationSound.type)
        assertEquals("gentle_chime", restored.notificationSound.builtInSoundKey)
        assertEquals(SoundSourceType.BUILT_IN, restored.alarmSound.type)
        assertEquals("strong_alarm", restored.alarmSound.builtInSoundKey)
    }
}
