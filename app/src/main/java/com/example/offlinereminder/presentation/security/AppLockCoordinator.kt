package com.example.offlinereminder.presentation.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.usecase.CheckAppLockRequiredUseCase
import com.example.offlinereminder.domain.usecase.GetAppLockSettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Coordinates app security, lock state transitions, and BiometricPrompt / KeyguardManager
 * device authentication across the application lifecycle.
 */
class AppLockCoordinator(
    private val checkAppLockRequiredUseCase: CheckAppLockRequiredUseCase,
    private val getAppLockSettingsUseCase: GetAppLockSettingsUseCase,
    private val timeProvider: TimeProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastActiveEpochMillis: Long = 0L
    private var isAppInBackground: Boolean = false
    private var currentCancellationSignal: CancellationSignal? = null

    fun onAppForegrounded() {
        scope.launch {
            val settings = getAppLockSettingsUseCase().first()
            if (settings.isEnabled) {
                val shouldLock = if (lastActiveEpochMillis == 0L) {
                    // Initial launch
                    true
                } else {
                    checkAppLockRequiredUseCase(
                        lastActiveEpochMillis = lastActiveEpochMillis,
                        isAppReturningFromBackground = isAppInBackground
                    )
                }
                if (shouldLock) {
                    _isLocked.value = true
                }
            } else {
                _isLocked.value = false
            }
            isAppInBackground = false
        }
    }

    fun onAppBackgrounded() {
        lastActiveEpochMillis = timeProvider.nowInstant().toEpochMilli()
        isAppInBackground = true
    }

    fun unlock() {
        _isLocked.value = false
        lastActiveEpochMillis = timeProvider.nowInstant().toEpochMilli()
    }

    fun lockImmediately() {
        _isLocked.value = true
    }

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    /**
     * Checks if the device has biometric or device credential security enabled.
     */
    fun isDeviceSecurityAvailable(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    /**
     * Initiates system BiometricPrompt or device credential confirmation.
     */
    fun authenticate(
        activity: Activity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        if (keyguardManager?.isDeviceSecure != true) {
            // No device PIN / pattern / biometric set up
            onError("No device screen lock is set up. Please enable a PIN, pattern, or biometric in system settings.")
            return
        }

        currentCancellationSignal?.cancel()
        val cancellationSignal = CancellationSignal()
        currentCancellationSignal = cancellationSignal

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val promptBuilder = BiometricPrompt.Builder(activity)
                .setTitle("Unlock Planerize")
                .setSubtitle("Confirm your identity to access your private calendar")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                promptBuilder.setDeviceCredentialAllowed(true)
            }

            val prompt = promptBuilder.build()
            val executor = activity.mainExecutor

            prompt.authenticate(
                cancellationSignal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        unlock()
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        ) {
                            onError(errString?.toString() ?: "Authentication failed")
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        // Single attempt failure, prompt remains open for retries
                    }
                }
            )
        } else {
            // API 26-27: Fallback to KeyguardManager confirm credential
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Unlock Planerize",
                "Confirm your device lock to access your calendar"
            )
            if (intent != null) {
                activity.startActivity(intent)
            } else {
                unlock()
                onSuccess()
            }
        }
    }
}
