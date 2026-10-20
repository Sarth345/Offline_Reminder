package com.example.offlinereminder.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Metadata referencing a local file attachment.
 * Stored locally in app-private storage; never uploaded to cloud.
 */
data class AttachmentMetadata(
    val id: String = UUID.randomUUID().toString(),
    val localUri: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long = 0L,
    val createdAt: Instant
)
