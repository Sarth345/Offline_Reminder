package com.example.offlinereminder.domain.model

/**
 * Pure Kotlin immutable model of App Lock privacy & security configuration.
 * Completely decoupled from Android BiometricPrompt and persistence frameworks.
 *
 * Never stores biometric credentials or PIN/password secrets.
 */
data class AppLockSettings(
    val isEnabled: Boolean = false,
    val timeoutMinutes: Int = 0, // 0 = Immediately, 1, 5, 15
    val lockOnBackground: Boolean = true
) {
    val isImmediatelyLocked: Boolean
        get() = timeoutMinutes <= 0
}
