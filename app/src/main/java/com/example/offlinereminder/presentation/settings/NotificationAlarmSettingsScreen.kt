package com.example.offlinereminder.presentation.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.offlinereminder.data.sound.BuiltInSoundResourceMapper
import com.example.offlinereminder.data.sound.SoundContentProvider
import com.example.offlinereminder.domain.model.BuiltInSoundCatalog
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import java.io.InputStream
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationAlarmSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNotificationProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Audio preview state
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentlyPlayingKey by remember { mutableStateOf<String?>(null) }

    fun stopAudioPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        currentlyPlayingKey = null
    }

    fun playAudioPreview(sound: SoundReference, playKey: String, isAlarm: Boolean) {
        if (currentlyPlayingKey == playKey) {
            stopAudioPreview()
            return
        }
        stopAudioPreview()
        if (sound.type == SoundSourceType.SILENT) return

        try {
            val uri: Uri = when (sound.type) {
                SoundSourceType.BUILT_IN -> {
                    BuiltInSoundResourceMapper.getSoundUriForKey(context, sound.builtInSoundKey)
                        ?: RingtoneManager.getActualDefaultRingtoneUri(context, if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                }
                SoundSourceType.CUSTOM -> {
                    if (!sound.customSoundId.isNullOrBlank()) {
                        SoundContentProvider.buildSoundUri(context, sound.customSoundId)
                    } else {
                        RingtoneManager.getActualDefaultRingtoneUri(context, if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                            ?: RingtoneManager.getDefaultUri(if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                    }
                }
                SoundSourceType.SYSTEM -> {
                    if (!sound.systemUriString.isNullOrBlank()) {
                        Uri.parse(sound.systemUriString)
                    } else {
                        RingtoneManager.getActualDefaultRingtoneUri(context, if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                            ?: RingtoneManager.getDefaultUri(if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                    }
                }
                SoundSourceType.DEFAULT -> {
                    RingtoneManager.getActualDefaultRingtoneUri(context, if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(if (isAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
                }
                SoundSourceType.SILENT -> Uri.EMPTY
            }

            if (uri != Uri.EMPTY) {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(if (isAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
                var configured = false
                try {
                    val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    if (afd != null) {
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        configured = true
                    }
                } catch (_: Exception) {}
                if (!configured) {
                    player.setDataSource(context, uri)
                }
                player.isLooping = isAlarm
                player.setOnCompletionListener {
                    stopAudioPreview()
                }
                player.prepare()
                player.start()
                previewPlayer = player
                currentlyPlayingKey = playKey
            }
        } catch (_: Exception) {
            stopAudioPreview()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudioPreview()
        }
    }

    // Messages snackbar
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onAction(SettingsAction.DismissMessages)
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onAction(SettingsAction.DismissMessages)
        }
    }

    // Permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onAction(SettingsAction.RefreshCapabilities(isGranted))
    }

    // System Ringtone picker launcher
    var isPickingRingtoneForAlarm by remember { mutableStateOf(false) }
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            }
            if (uri != null) {
                if (isPickingRingtoneForAlarm) {
                    viewModel.onAction(
                        SettingsAction.UpdateAlarmSound(
                            SoundReference(type = SoundSourceType.SYSTEM, systemUriString = uri.toString())
                        )
                    )
                } else {
                    viewModel.onAction(
                        SettingsAction.UpdateNotificationSound(
                            SoundReference(type = SoundSourceType.SYSTEM, systemUriString = uri.toString())
                        )
                    )
                }
            }
        }
    }

    fun launchSystemRingtonePicker(forAlarm: Boolean) {
        stopAudioPreview()
        isPickingRingtoneForAlarm = forAlarm
        val currentUri = if (forAlarm) uiState.settings.alarmSound.systemUriString else uiState.settings.notificationSound.systemUriString
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (forAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            if (!currentUri.isNullOrBlank()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    // File pickers for custom sound
    var isPickingFileForAlarm by remember { mutableStateOf(false) }
    val customSoundFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "custom_sound"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
            val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.onAction(
                    SettingsAction.AddCustomSound(
                        title = fileName.substringBeforeLast('.'),
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSizeBytes = if (fileSize > 0) fileSize else 1024L,
                        inputStream = inputStream,
                        applyToNotification = !isPickingFileForAlarm,
                        applyToAlarm = isPickingFileForAlarm
                    )
                )
            }
        }
    }

    // Dialog state for custom snooze duration
    var showCustomSnoozeDialog by remember { mutableStateOf(false) }
    var customSnoozeValueText by remember { mutableStateOf("15") }

    // Dialog state for quiet hours time pickers
    var showQuietHoursStartPicker by remember { mutableStateOf(false) }
    var showQuietHoursEndPicker by remember { mutableStateOf(false) }

    // Refresh capabilities on lifecycle resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        viewModel.onAction(SettingsAction.RefreshCapabilities(allowed))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Notifications & Alarms") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ====================================================
            // 1. NOTIFICATIONS SECTION
            // ====================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = uiState.settings.notificationsEnabled,
                            onCheckedChange = { viewModel.onAction(SettingsAction.ToggleNotificationsEnabled(it)) }
                        )
                    }

                    // Permission Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permission", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (uiState.isNotificationAllowed) "Notifications are allowed on this device" else "Permission is required to display reminder alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.isNotificationAllowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.isNotificationAllowed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (uiState.isNotificationAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isNotificationAllowed) "Allowed" else "Not allowed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isNotificationAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (!uiState.isNotificationAllowed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(
                                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Grant permission")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("System settings")
                            }
                        }
                    }

                    HorizontalDivider()

                    // Notification Tone Options (Categories A - E)
                    Text("Notification Tone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val currentNotifSound = uiState.settings.notificationSound

                    // Category A: System Default
                    SoundSelectionRow(
                        title = "System Default Tone",
                        subtitle = "Standard Android notification alert",
                        isSelected = currentNotifSound.type == SoundSourceType.DEFAULT,
                        isPlaying = currentlyPlayingKey == "notif_default",
                        onSelect = {
                            stopAudioPreview()
                            viewModel.onAction(SettingsAction.UpdateNotificationSound(SoundReference(SoundSourceType.DEFAULT)))
                        },
                        onPlayPreview = {
                            playAudioPreview(SoundReference(SoundSourceType.DEFAULT), "notif_default", isAlarm = false)
                        }
                    )

                    // Category B: Built-in Planerize Tones
                    Text("Built-in Planerize Tones", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    BuiltInSoundCatalog.NOTIFICATION_TONES.forEach { tone ->
                        val isSelected = currentNotifSound.type == SoundSourceType.BUILT_IN && currentNotifSound.builtInSoundKey == tone.key
                        val soundRef = SoundReference(type = SoundSourceType.BUILT_IN, builtInSoundKey = tone.key)
                        SoundSelectionRow(
                            title = tone.displayName,
                            subtitle = tone.description,
                            isSelected = isSelected,
                            isPlaying = currentlyPlayingKey == "notif_builtin_${tone.key}",
                            onSelect = {
                                stopAudioPreview()
                                viewModel.onAction(SettingsAction.UpdateNotificationSound(soundRef))
                            },
                            onPlayPreview = {
                                playAudioPreview(soundRef, "notif_builtin_${tone.key}", isAlarm = false)
                            }
                        )
                    }

                    // Category C: System Ringtone
                    val isSystemSelected = currentNotifSound.type == SoundSourceType.SYSTEM
                    SoundSelectionRow(
                        title = "System Ringtone",
                        subtitle = if (isSystemSelected && !currentNotifSound.systemUriString.isNullOrBlank()) "Selected: ${currentNotifSound.systemUriString.substringAfterLast('/')}" else "Choose from device ringtones",
                        isSelected = isSystemSelected,
                        isPlaying = currentlyPlayingKey == "notif_system",
                        onSelect = {
                            launchSystemRingtonePicker(forAlarm = false)
                        },
                        onPlayPreview = {
                            playAudioPreview(currentNotifSound, "notif_system", isAlarm = false)
                        },
                        actionLabel = "Choose..."
                    )

                    // Category D: Custom Imported Sound
                    val isCustomSelected = currentNotifSound.type == SoundSourceType.CUSTOM
                    val matchedCustom = uiState.customSounds.firstOrNull { it.id == currentNotifSound.customSoundId }
                    SoundSelectionRow(
                        title = "Custom Imported Sound",
                        subtitle = if (isCustomSelected && matchedCustom != null) "Selected: ${matchedCustom.title}" else "Import offline audio (.mp3, .wav, .ogg)",
                        isSelected = isCustomSelected,
                        isPlaying = currentlyPlayingKey == "notif_custom",
                        onSelect = {
                            isPickingFileForAlarm = false
                            customSoundFilePicker.launch(arrayOf("audio/*"))
                        },
                        onPlayPreview = {
                            playAudioPreview(currentNotifSound, "notif_custom", isAlarm = false)
                        },
                        actionLabel = "Import..."
                    )

                    // Category E: Silent
                    SoundSelectionRow(
                        title = "Silent",
                        subtitle = "Alert banner without sound",
                        isSelected = currentNotifSound.type == SoundSourceType.SILENT,
                        isPlaying = false,
                        onSelect = {
                            stopAudioPreview()
                            viewModel.onAction(SettingsAction.UpdateNotificationSound(SoundReference(SoundSourceType.SILENT)))
                        },
                        onPlayPreview = null
                    )

                    HorizontalDivider()

                    // Sound & Vibration Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audible Sound", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.settings.soundEnabled,
                            onCheckedChange = { viewModel.onAction(SettingsAction.ToggleSound(it)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.settings.vibrationEnabled,
                            onCheckedChange = { viewModel.onAction(SettingsAction.ToggleVibration(it)) }
                        )
                    }
                }
            }

            // ====================================================
            // 2. ALARMS SECTION
            // ====================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Alarms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = uiState.settings.alarmModeEnabled,
                            onCheckedChange = { viewModel.onAction(SettingsAction.ToggleAlarmModeEnabled(it)) }
                        )
                    }

                    Text(
                        "Continuous audio ringing with full-screen alert, dismiss, and snooze controls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Exact Alarm Capability
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exact Alarms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (uiState.isExactAlarmAllowed) "Alarms fire with exact to-the-second precision" else "Exact alarms permission is required for on-time alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.isExactAlarmAllowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.isExactAlarmAllowed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (uiState.isExactAlarmAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isExactAlarmAllowed) "Allowed" else "Not allowed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isExactAlarmAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (!uiState.isExactAlarmAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow exact alarms in system settings")
                        }
                    }

                    HorizontalDivider()

                    // Alarm Tune Options (Categories A - D)
                    Text("Alarm Tune", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val currentAlarmSound = uiState.settings.alarmSound

                    // Category A: System Default Alarm
                    SoundSelectionRow(
                        title = "System Default Alarm",
                        subtitle = "Standard Android alarm tune",
                        isSelected = currentAlarmSound.type == SoundSourceType.DEFAULT,
                        isPlaying = currentlyPlayingKey == "alarm_default",
                        onSelect = {
                            stopAudioPreview()
                            viewModel.onAction(SettingsAction.UpdateAlarmSound(SoundReference(SoundSourceType.DEFAULT)))
                        },
                        onPlayPreview = {
                            playAudioPreview(SoundReference(SoundSourceType.DEFAULT), "alarm_default", isAlarm = true)
                        }
                    )

                    // Category B: Built-in Planerize Alarm Tunes
                    Text("Built-in Planerize Tunes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    BuiltInSoundCatalog.ALARM_TUNES.forEach { tune ->
                        val isSelected = currentAlarmSound.type == SoundSourceType.BUILT_IN && currentAlarmSound.builtInSoundKey == tune.key
                        val soundRef = SoundReference(type = SoundSourceType.BUILT_IN, builtInSoundKey = tune.key)
                        SoundSelectionRow(
                            title = tune.displayName,
                            subtitle = tune.description,
                            isSelected = isSelected,
                            isPlaying = currentlyPlayingKey == "alarm_builtin_${tune.key}",
                            onSelect = {
                                stopAudioPreview()
                                viewModel.onAction(SettingsAction.UpdateAlarmSound(soundRef))
                            },
                            onPlayPreview = {
                                playAudioPreview(soundRef, "alarm_builtin_${tune.key}", isAlarm = true)
                            }
                        )
                    }

                    // Category C: System Ringtone
                    val isAlarmSystemSelected = currentAlarmSound.type == SoundSourceType.SYSTEM
                    SoundSelectionRow(
                        title = "System Ringtone",
                        subtitle = if (isAlarmSystemSelected && !currentAlarmSound.systemUriString.isNullOrBlank()) "Selected: ${currentAlarmSound.systemUriString.substringAfterLast('/')}" else "Choose from device alarm tones",
                        isSelected = isAlarmSystemSelected,
                        isPlaying = currentlyPlayingKey == "alarm_system",
                        onSelect = {
                            launchSystemRingtonePicker(forAlarm = true)
                        },
                        onPlayPreview = {
                            playAudioPreview(currentAlarmSound, "alarm_system", isAlarm = true)
                        },
                        actionLabel = "Choose..."
                    )

                    // Category D: Custom Imported Alarm Sound
                    val isAlarmCustomSelected = currentAlarmSound.type == SoundSourceType.CUSTOM
                    val matchedAlarmCustom = uiState.customSounds.firstOrNull { it.id == currentAlarmSound.customSoundId }
                    SoundSelectionRow(
                        title = "Custom Imported Alarm",
                        subtitle = if (isAlarmCustomSelected && matchedAlarmCustom != null) "Selected: ${matchedAlarmCustom.title}" else "Import offline alarm audio (.mp3, .wav, .ogg)",
                        isSelected = isAlarmCustomSelected,
                        isPlaying = currentlyPlayingKey == "alarm_custom",
                        onSelect = {
                            isPickingFileForAlarm = true
                            customSoundFilePicker.launch(arrayOf("audio/*"))
                        },
                        onPlayPreview = {
                            playAudioPreview(currentAlarmSound, "alarm_custom", isAlarm = true)
                        },
                        actionLabel = "Import..."
                    )

                    HorizontalDivider()

                    // Snooze Duration (Part 10)
                    Text("Snooze Duration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val currentSnooze = uiState.settings.defaultSnoozeDurationMinutes
                    Text(
                        text = "Current: $currentSnooze minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val presets = listOf(5, 10, 15, 30, 60)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.forEach { mins ->
                            val label = if (mins >= 60) "1 hour" else "$mins min"
                            FilterChip(
                                selected = currentSnooze == mins,
                                onClick = { viewModel.onAction(SettingsAction.UpdateDefaultSnooze(mins)) },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = currentSnooze !in presets,
                            onClick = {
                                customSnoozeValueText = currentSnooze.toString()
                                showCustomSnoozeDialog = true
                            },
                            label = { Text(if (currentSnooze !in presets) "Custom: ${currentSnooze}m" else "Custom...") }
                        )
                    }
                }
            }

            // ====================================================
            // 3. QUIET HOURS SECTION
            // ====================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val qh = uiState.settings.quietHours
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quiet Hours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Silence or delay reminders during rest periods (${qh.startLocalTime} - ${qh.endLocalTime})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = qh.enabled,
                            onCheckedChange = { viewModel.onAction(SettingsAction.UpdateQuietHours(qh.copy(enabled = it))) }
                        )
                    }

                    if (qh.enabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showQuietHoursStartPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start: ${qh.startLocalTime}")
                            }
                            OutlinedButton(
                                onClick = { showQuietHoursEndPicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("End: ${qh.endLocalTime}")
                            }
                        }

                        Text("Policy", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            QuietHoursPolicy.entries.forEach { policy ->
                                FilterChip(
                                    selected = qh.policy == policy,
                                    onClick = { viewModel.onAction(SettingsAction.UpdateQuietHours(qh.copy(policy = policy))) },
                                    label = { Text(policy.name) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Allow Critical / High Priority", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = qh.allowCritical,
                                onCheckedChange = { viewModel.onAction(SettingsAction.UpdateQuietHours(qh.copy(allowCritical = it))) }
                            )
                        }
                    }
                }
            }

            // ====================================================
            // 4. BATTERY & OEM RESTRICTIONS NOTICE (Part 12)
            // ====================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery & Background Restrictions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "On devices with aggressive background management (ColorOS, OxygenOS, HyperOS, MIUI), alarms and notifications may be delayed by the OS when the screen is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Recommended setup: In Android Settings, locate Planerize under App Management -> Battery Usage and select 'Allow background activity' / 'Don't optimize'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Battery Optimization Settings")
                    }
                }
            }

            // ====================================================
            // 5. INSTANT VALIDATION TESTS
            // ====================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Device Validation Tests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Schedule an immediate test alert with a 5-second delay to test on this device with locked screen or idle mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onAction(SettingsAction.ScheduleTestNotification(isAlarm = false)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Notification (5s)")
                        }
                        Button(
                            onClick = { viewModel.onAction(SettingsAction.ScheduleTestNotification(isAlarm = true)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Alarm (5s)")
                        }
                    }
                }
            }

            // ====================================================
            // 6. NOTIFICATION PROFILES & CUSTOM SOUNDS
            // ====================================================
            OutlinedButton(
                onClick = onNavigateToNotificationProfiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Notification Profiles & Custom Sounds")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialog: Custom Snooze Duration
    if (showCustomSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSnoozeDialog = false },
            title = { Text("Custom Snooze Duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter duration between 1 and 1440 minutes:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = customSnoozeValueText,
                        onValueChange = { customSnoozeValueText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = customSnoozeValueText.toIntOrNull() ?: 15
                        viewModel.onAction(SettingsAction.UpdateDefaultSnooze(num.coerceIn(1, 1440)))
                        showCustomSnoozeDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSnoozeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Time Pickers for Quiet Hours
    if (showQuietHoursStartPicker) {
        val qh = uiState.settings.quietHours
        val timePickerState = rememberTimePickerState(
            initialHour = qh.startLocalTime.hour,
            initialMinute = qh.startLocalTime.minute
        )
        AlertDialog(
            onDismissRequest = { showQuietHoursStartPicker = false },
            title = { Text("Quiet Hours Start Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                Button(onClick = {
                    val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    viewModel.onAction(SettingsAction.UpdateQuietHours(qh.copy(startLocalTime = newTime)))
                    showQuietHoursStartPicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showQuietHoursStartPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showQuietHoursEndPicker) {
        val qh = uiState.settings.quietHours
        val timePickerState = rememberTimePickerState(
            initialHour = qh.endLocalTime.hour,
            initialMinute = qh.endLocalTime.minute
        )
        AlertDialog(
            onDismissRequest = { showQuietHoursEndPicker = false },
            title = { Text("Quiet Hours End Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                Button(onClick = {
                    val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    viewModel.onAction(SettingsAction.UpdateQuietHours(qh.copy(endLocalTime = newTime)))
                    showQuietHoursEndPicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showQuietHoursEndPicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SoundSelectionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onPlayPreview: (() -> Unit)?,
    actionLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionLabel != null) {
                    TextButton(onClick = onSelect) {
                        Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onPlayPreview != null) {
                    IconButton(
                        onClick = onPlayPreview,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop Preview" else "Play Preview",
                            tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
