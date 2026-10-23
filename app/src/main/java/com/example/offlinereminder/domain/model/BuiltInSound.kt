package com.example.offlinereminder.domain.model

enum class SoundCategory {
    NOTIFICATION,
    ALARM
}

/**
 * Pure domain representation of a bundled offline sound.
 * Zero Android framework or resource ID dependencies.
 */
data class BuiltInSound(
    val key: String,
    val displayName: String,
    val category: SoundCategory,
    val description: String = ""
)
