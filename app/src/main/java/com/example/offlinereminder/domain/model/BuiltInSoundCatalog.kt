package com.example.offlinereminder.domain.model

/**
 * Authoritative offline sound catalog for Planerize.
 * Stable keys guaranteed across app updates, process restarts, and backups.
 */
object BuiltInSoundCatalog {

    val NOTIFICATION_TONES: List<BuiltInSound> = listOf(
        BuiltInSound(
            key = "planerize_bell",
            displayName = "Planerize Bell",
            category = SoundCategory.NOTIFICATION,
            description = "Signature crystal chime"
        ),
        BuiltInSound(
            key = "soft_bell",
            displayName = "Soft Bell",
            category = SoundCategory.NOTIFICATION,
            description = "Warm acoustic tone"
        ),
        BuiltInSound(
            key = "gentle_chime",
            displayName = "Gentle Chime",
            category = SoundCategory.NOTIFICATION,
            description = "Ascending 3-note glockenspiel"
        ),
        BuiltInSound(
            key = "digital_alert",
            displayName = "Digital Alert",
            category = SoundCategory.NOTIFICATION,
            description = "Crisp electronic dual-pip"
        ),
        BuiltInSound(
            key = "short_beep",
            displayName = "Short Beep",
            category = SoundCategory.NOTIFICATION,
            description = "Minimalist confirmation blip"
        )
    )

    val ALARM_TUNES: List<BuiltInSound> = listOf(
        BuiltInSound(
            key = "planerize_alarm",
            displayName = "Planerize Alarm",
            category = SoundCategory.ALARM,
            description = "Melodic ascending chime sequence"
        ),
        BuiltInSound(
            key = "classic_alarm",
            displayName = "Classic Alarm",
            category = SoundCategory.ALARM,
            description = "Mechanical twin-bell pulse"
        ),
        BuiltInSound(
            key = "digital_alarm",
            displayName = "Digital Alarm",
            category = SoundCategory.ALARM,
            description = "Pulsing digital alarm beep"
        ),
        BuiltInSound(
            key = "rising_alert",
            displayName = "Rising Alert",
            category = SoundCategory.ALARM,
            description = "Energetic rising alert arpeggio"
        ),
        BuiltInSound(
            key = "strong_alarm",
            displayName = "Strong Alarm",
            category = SoundCategory.ALARM,
            description = "High-intensity penetrating alert"
        )
    )

    fun findNotificationTone(key: String?): BuiltInSound? =
        NOTIFICATION_TONES.find { it.key == key }

    fun findAlarmTune(key: String?): BuiltInSound? =
        ALARM_TUNES.find { it.key == key }

    fun findSound(key: String?): BuiltInSound? =
        NOTIFICATION_TONES.find { it.key == key } ?: ALARM_TUNES.find { it.key == key }
}
