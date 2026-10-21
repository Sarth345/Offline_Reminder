package com.example.offlinereminder.data.sound

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Manages persistent binary audio files for custom notification and alarm sounds.
 *
 * Files are stored strictly within the app's internal private storage directory
 * `files/custom_sounds/` and verified against path traversal exploits.
 */
class CustomSoundStorage(
    private val context: Context
) {
    val soundDirectory: File
        get() = File(context.filesDir, SOUND_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }

    fun getSoundFile(fileName: String): File {
        val sanitized = File(fileName).name
        val target = File(soundDirectory, sanitized)
        validateWithinSoundDirectory(target)
        return target
    }

    fun copySound(sourceStream: InputStream, targetFileName: String): File {
        val targetFile = getSoundFile(targetFileName)
        targetFile.outputStream().use { output ->
            sourceStream.copyTo(output)
        }
        return targetFile
    }

    fun exists(fileName: String): Boolean {
        return try {
            val file = getSoundFile(fileName)
            file.exists() && file.isFile
        } catch (_: SecurityException) {
            false
        }
    }

    fun deleteSound(fileName: String): Boolean {
        return try {
            val file = getSoundFile(fileName)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (_: SecurityException) {
            false
        }
    }

    fun validateWithinSoundDirectory(file: File) {
        val canonicalBase = soundDirectory.canonicalFile
        val canonicalFile = file.canonicalFile
        if (!canonicalFile.toPath().startsWith(canonicalBase.toPath())) {
            throw SecurityException("Path traversal attempt detected: ${file.path}")
        }
    }

    companion object {
        const val SOUND_DIR_NAME = "custom_sounds"
    }
}
