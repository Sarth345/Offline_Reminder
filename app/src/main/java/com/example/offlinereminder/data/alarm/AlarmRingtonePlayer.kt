package com.example.offlinereminder.data.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.example.offlinereminder.data.sound.SoundContentProvider
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType

/**
 * Handles continuous audio playback for alarms using [MediaPlayer].
 *
 * Configured with [AudioAttributes.USAGE_ALARM] to route through the alarm audio stream.
 * Gracefully falls back to system default alarm audio if custom or system URIs cannot be resolved.
 */
class AlarmRingtonePlayer(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    fun play(sound: SoundReference, isLooping: Boolean = true): Boolean {
        stop()

        if (sound.type == SoundSourceType.SILENT) {
            return false
        }

        val targetUri = resolveSoundUri(sound)

        return try {
            mediaPlayer = createAndStartPlayer(targetUri, isLooping)
            true
        } catch (e: Exception) {
            // Fallback to default alarm audio if primary sound failed
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (fallbackUri != null && fallbackUri != targetUri) {
                    mediaPlayer = createAndStartPlayer(fallbackUri, isLooping)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun resolveSoundUri(sound: SoundReference): Uri {
        return when (sound.type) {
            SoundSourceType.DEFAULT -> {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Uri.EMPTY
            }
            SoundSourceType.SYSTEM -> {
                if (!sound.systemUriString.isNullOrBlank()) {
                    Uri.parse(sound.systemUriString)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: Uri.EMPTY
                }
            }
            SoundSourceType.CUSTOM -> {
                if (!sound.customSoundId.isNullOrBlank()) {
                    SoundContentProvider.buildSoundUri(context, sound.customSoundId)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: Uri.EMPTY
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
            setDataSource(context, uri)
            this.isLooping = isLooping
            prepare()
            start()
        }
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
    }

    fun release() {
        stop()
    }
}
