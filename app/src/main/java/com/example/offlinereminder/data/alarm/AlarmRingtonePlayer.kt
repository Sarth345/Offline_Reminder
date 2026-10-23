package com.example.offlinereminder.data.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.offlinereminder.data.sound.SoundContentProvider
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType

/**
 * Handles continuous audio playback for alarms using [MediaPlayer] with [Ringtone] fallback.
 *
 * Configured with [AudioAttributes.USAGE_ALARM] to route through the alarm audio stream.
 * Resolves physical media streams via ContentResolver AssetFileDescriptor or direct URI,
 * and gracefully falls back to default system alarm ringtones.
 */
class AlarmRingtonePlayer(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true || (fallbackRingtone?.isPlaying == true)
        } catch (_: Exception) {
            false
        }

    fun play(sound: SoundReference, isLooping: Boolean = true): Boolean {
        stop()

        if (sound.type == SoundSourceType.SILENT) {
            Log.d("PlanerizeAlarm", "Alarm sound is configured as SILENT. Playback suppressed.")
            return false
        }

        val targetUri = resolveSoundUri(sound)
        Log.d("PlanerizeAlarm", "Attempting alarm playback with targetUri: '$targetUri' (soundType: ${sound.type})")

        if (targetUri != Uri.EMPTY) {
            try {
                mediaPlayer = createAndStartPlayer(targetUri, isLooping)
                Log.d("PlanerizeAlarm", "MediaPlayer started successfully with targetUri: '$targetUri'")
                return true
            } catch (e: Exception) {
                Log.w("PlanerizeAlarm", "MediaPlayer failed with targetUri: '$targetUri'. Trying fallback.", e)
            }
        }

        // Fallback strategy: actual default alarm, notification, or ringtone URI
        val fallbackUris = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )

        for (fallbackUri in fallbackUris) {
            if (fallbackUri == targetUri) continue
            try {
                mediaPlayer = createAndStartPlayer(fallbackUri, isLooping)
                Log.d("PlanerizeAlarm", "Fallback MediaPlayer started successfully with URI: '$fallbackUri'")
                return true
            } catch (e: Exception) {
                Log.w("PlanerizeAlarm", "Fallback MediaPlayer failed with URI: '$fallbackUri'", e)
            }

            // Secondary fallback: Ringtone API
            try {
                val ringtone = RingtoneManager.getRingtone(context, fallbackUri)
                if (ringtone != null) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.isLooping = isLooping
                    }
                    ringtone.play()
                    fallbackRingtone = ringtone
                    Log.d("PlanerizeAlarm", "Fallback Ringtone API started successfully with URI: '$fallbackUri'")
                    return true
                }
            } catch (e: Exception) {
                Log.e("PlanerizeAlarm", "Fallback Ringtone API failed for URI: '$fallbackUri'", e)
            }
        }

        Log.e("PlanerizeAlarm", "All alarm audio playback options failed.")
        return false
    }

    private fun resolveSoundUri(sound: SoundReference): Uri {
        return when (sound.type) {
            SoundSourceType.DEFAULT -> {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Uri.EMPTY
            }
            SoundSourceType.BUILT_IN -> {
                com.example.offlinereminder.data.sound.BuiltInSoundResourceMapper.getSoundUriForKey(context, sound.builtInSoundKey)
                    ?: resolveSoundUri(SoundReference(SoundSourceType.DEFAULT))
            }
            SoundSourceType.SYSTEM -> {
                if (!sound.systemUriString.isNullOrBlank()) {
                    Uri.parse(sound.systemUriString)
                } else {
                    resolveSoundUri(SoundReference(SoundSourceType.DEFAULT))
                }
            }
            SoundSourceType.CUSTOM -> {
                if (!sound.customSoundId.isNullOrBlank()) {
                    SoundContentProvider.buildSoundUri(context, sound.customSoundId)
                } else {
                    resolveSoundUri(SoundReference(SoundSourceType.DEFAULT))
                }
            }
            SoundSourceType.SILENT -> Uri.EMPTY
        }
    }

    private fun createAndStartPlayer(uri: Uri, isLooping: Boolean): MediaPlayer {
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        var sourceConfigured = false
        try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (afd != null) {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                sourceConfigured = true
            }
        } catch (_: Exception) {
        }

        if (!sourceConfigured) {
            player.setDataSource(context, uri)
        }

        player.isLooping = isLooping
        player.prepare()
        player.start()
        return player
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
        try {
            fallbackRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (_: Exception) {
        } finally {
            fallbackRingtone = null
        }
    }

    fun release() {
        stop()
    }
}
