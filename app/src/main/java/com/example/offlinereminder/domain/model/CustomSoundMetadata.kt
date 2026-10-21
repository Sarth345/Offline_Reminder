package com.example.offlinereminder.domain.model

import java.time.Instant

data class CustomSoundMetadata(
    val id: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationMillis: Long = 0L,
    val addedAt: Instant
)
