package com.example.offlinereminder.presentation.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.offlinereminder.domain.usecase.GetAppLockSettingsUseCase
import com.example.offlinereminder.domain.usecase.UpdateAppLockSettingsUseCase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    getAppLockSettingsUseCase: GetAppLockSettingsUseCase,
    updateAppLockSettingsUseCase: UpdateAppLockSettingsUseCase,
    appLockCoordinator: AppLockCoordinator,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLockSettings by getAppLockSettingsUseCase().collectAsState(initial = com.example.offlinereminder.domain.model.AppLockSettings())
    val isSecurityAvailable = appLockCoordinator.isDeviceSecurityAvailable(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Lock") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Master Enable Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Require Authentication",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Protect your schedule with biometric or device lock credentials",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = appLockSettings.isEnabled,
                    enabled = isSecurityAvailable,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            updateAppLockSettingsUseCase(
                                isEnabled = enabled,
                                timeoutMinutes = appLockSettings.timeoutMinutes,
                                lockOnBackground = appLockSettings.lockOnBackground
                            )
                        }
                    }
                )
            }

            if (!isSecurityAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No device screen lock is configured. Set a PIN, password, or biometric in Android Settings to enable App Lock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (appLockSettings.isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Lock Timeout",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val timeoutOptions = listOf(
                    0 to "Immediately",
                    1 to "After 1 minute",
                    5 to "After 5 minutes",
                    15 to "After 15 minutes"
                )

                timeoutOptions.forEach { (minutes, label) ->
                    val isSelected = appLockSettings.timeoutMinutes == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        updateAppLockSettingsUseCase(
                                            isEnabled = appLockSettings.isEnabled,
                                            timeoutMinutes = minutes,
                                            lockOnBackground = appLockSettings.lockOnBackground
                                        )
                                    }
                                }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock when returning from background",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Re-check lock timeout when app returns to foreground",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = appLockSettings.lockOnBackground,
                        onCheckedChange = { lockOnBg ->
                            scope.launch {
                                updateAppLockSettingsUseCase(
                                    isEnabled = appLockSettings.isEnabled,
                                    timeoutMinutes = appLockSettings.timeoutMinutes,
                                    lockOnBackground = lockOnBg
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
