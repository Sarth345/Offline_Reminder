package com.example.offlinereminder.domain.security

import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.data.repository.InMemorySettingsRepository
import com.example.offlinereminder.domain.model.AppLockSettings
import com.example.offlinereminder.domain.usecase.CheckAppLockRequiredUseCase
import com.example.offlinereminder.domain.usecase.GetAppLockSettingsUseCase
import com.example.offlinereminder.domain.usecase.UpdateAppLockSettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AppLockTest {

    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var getSettingsUseCase: GetAppLockSettingsUseCase
    private lateinit var updateSettingsUseCase: UpdateAppLockSettingsUseCase
    private lateinit var checkRequiredUseCase: CheckAppLockRequiredUseCase

    @Before
    fun setUp() {
        settingsRepository = InMemorySettingsRepository()
        timeProvider = TestTimeProvider(Instant.ofEpochMilli(1_000_000L))
        getSettingsUseCase = GetAppLockSettingsUseCase(settingsRepository)
        updateSettingsUseCase = UpdateAppLockSettingsUseCase(settingsRepository)
        checkRequiredUseCase = CheckAppLockRequiredUseCase(settingsRepository, timeProvider)
    }

    @Test
    fun appLockPolicy_disabledByDefault() {
        val settings = AppLockSettings(isEnabled = false)
        val shouldLock = AppLockPolicy.shouldLock(
            settings = settings,
            lastActiveEpochMillis = 500_000L,
            currentEpochMillis = 1_000_000L,
            isAppReturningFromBackground = true
        )
        assertFalse(shouldLock)
    }

    @Test
    fun appLockPolicy_immediateLockOnBackground() {
        val settings = AppLockSettings(isEnabled = true, timeoutMinutes = 0, lockOnBackground = true)
        val shouldLock = AppLockPolicy.shouldLock(
            settings = settings,
            lastActiveEpochMillis = 999_000L,
            currentEpochMillis = 1_000_000L,
            isAppReturningFromBackground = true
        )
        assertTrue(shouldLock)
    }

    @Test
    fun appLockPolicy_timeoutExceeded() {
        val settings = AppLockSettings(isEnabled = true, timeoutMinutes = 5, lockOnBackground = true)
        // 5 minutes = 300,000 millis
        val shouldLock = AppLockPolicy.shouldLock(
            settings = settings,
            lastActiveEpochMillis = 100_000L,
            currentEpochMillis = 500_000L, // 400s elapsed
            isAppReturningFromBackground = true
        )
        assertTrue(shouldLock)
    }

    @Test
    fun appLockPolicy_withinTimeout_notLocked() {
        val settings = AppLockSettings(isEnabled = true, timeoutMinutes = 5, lockOnBackground = true)
        val shouldLock = AppLockPolicy.shouldLock(
            settings = settings,
            lastActiveEpochMillis = 900_000L,
            currentEpochMillis = 1_000_000L, // 100s elapsed < 300s
            isAppReturningFromBackground = true
        )
        assertFalse(shouldLock)
    }

    @Test
    fun updateAndCheckAppLockUseCases() = runTest {
        var settings = getSettingsUseCase().first()
        assertFalse(settings.isEnabled)

        updateSettingsUseCase(isEnabled = true, timeoutMinutes = 10, lockOnBackground = true)
        settings = getSettingsUseCase().first()
        assertTrue(settings.isEnabled)
        assertEquals(10, settings.timeoutMinutes)
        assertTrue(settings.lockOnBackground)

        val lockRequired = checkRequiredUseCase(
            lastActiveEpochMillis = 100_000L,
            isAppReturningFromBackground = true
        )
        assertTrue(lockRequired)
    }
}
