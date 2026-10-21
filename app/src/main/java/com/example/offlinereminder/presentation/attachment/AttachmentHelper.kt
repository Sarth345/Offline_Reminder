package com.example.offlinereminder.presentation.attachment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.AttachmentMetadata
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

/**
 * Android-side helper for safe, local-only attachment file operations.
 * Handles copying external URIs into app-private internal storage,
 * opening attachments via FileProvider, and verifying local availability.
 * Never requires INTERNET permission.
 */
object AttachmentHelper {

    fun saveAttachmentFromUri(
        context: Context,
        sourceUri: Uri
    ): Result<AttachmentMetadata> = Result.runCatching {
        var fileName = "attachment_${System.currentTimeMillis()}"
        var sizeBytes = 0L
        val contentResolver = context.contentResolver

        if (sourceUri.scheme == "file") {
            sourceUri.lastPathSegment?.let { if (it.isNotBlank()) fileName = it }
        } else {
            contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        // Sanitize fileName to ensure it is purely a basename without directory separators
        fileName = fileName.substringAfterLast('/').substringAfterLast('\\')

        val ext = if (fileName.contains(".")) fileName.substringAfterLast(".").lowercase() else ""
        val mimeType = runCatching {
            contentResolver.getType(sourceUri)
                ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }.getOrNull() ?: when (ext) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
        val attachmentId = UUID.randomUUID().toString()

        val storageFileName = if (ext.isNotEmpty()) "${attachmentId}_$fileName" else attachmentId
        val destinationFile = File(context.filesDir, storageFileName)
        destinationFile.parentFile?.mkdirs()

        val inputStream = if (sourceUri.scheme == "file" && sourceUri.path != null) {
            val path = sourceUri.path!!
            val rawPath = if (path.startsWith("/") && path.length > 2 && path[2] == ':') path.substring(1) else path
            val file = File(rawPath)
            if (file.exists()) java.io.FileInputStream(file) else contentResolver.openInputStream(sourceUri)
        } else {
            contentResolver.openInputStream(sourceUri)
        } ?: throw IllegalStateException("Unable to read input stream from URI: $sourceUri")

        inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }

        if (sizeBytes <= 0L) {
            sizeBytes = destinationFile.length()
        }

        AttachmentMetadata(
            id = attachmentId,
            localUri = destinationFile.name, // Stored as relative filename in filesDir for maximum portability
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = sizeBytes,
            createdAt = Instant.now()
        )
    }

    fun getLocalFile(context: Context, attachment: AttachmentMetadata): File? {
        val candidates = listOf(
            File(context.filesDir, attachment.localUri),
            File(context.filesDir, attachment.fileName),
            File(context.filesDir, "${attachment.id}_${attachment.fileName}")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    fun isAttachmentAvailable(context: Context, attachment: AttachmentMetadata): Boolean {
        return getLocalFile(context, attachment) != null
    }

    fun openAttachment(context: Context, attachment: AttachmentMetadata): Result<Unit> = Result.runCatching {
        val file = getLocalFile(context, attachment)
            ?: throw IllegalStateException("Attachment file not found on device: ${attachment.fileName}")

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, attachment.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun deleteAttachmentFile(context: Context, attachment: AttachmentMetadata) {
        val file = getLocalFile(context, attachment)
        if (file != null && file.exists()) {
            file.delete()
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
        }
    }
}
