package com.example.offlinereminder.domain.model

data class SoundReference(
    val type: SoundSourceType = SoundSourceType.DEFAULT,
    val customSoundId: String? = null,
    val systemUriString: String? = null
)
