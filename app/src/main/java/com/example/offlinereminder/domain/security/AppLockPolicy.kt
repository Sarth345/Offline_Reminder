package com.example.offlinereminder.domain.security

import com.example.offlinereminder.domain.model.AppLockSettings

/**
 * Pure Kotlin policy evaluating whether application access should be locked.
 * Zero Android or hardware dependencies.
 */
object AppLockPolicy {

    /**
     * Determines whether authentication is required based on user settings,
     * inactivity duration, and background/foreground transition state.
     */
    fun shouldLock(
        settings: AppLockSettings,
        lastActiveEpochMillis: Long,
        currentEpochMillis: Long,
        isAppReturningFromBackground: Boolean
    ): Boolean {
        if (!settings.isEnabled) return false
        if (settings.isImmediatelyLocked && isAppReturningFromBackground) return true
        if (settings.lockOnBackground && isAppReturningFromBackground) {
            val timeoutMillis = settings.timeoutMinutes * 60 * 1000L
            val elapsed = currentEpochMillis - lastActiveEpochMillis
            return elapsed >= timeoutMillis
        }
        return false
    }
}
