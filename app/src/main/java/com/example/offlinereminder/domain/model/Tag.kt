package com.example.offlinereminder.domain.model

import java.util.UUID

/**
 * Reusable label for multi-tagging events (e.g. #important, #semester).
 */
data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val normalizedName: String = normalize(name),
    val color: Int? = null
) {
    companion object {
        fun normalize(raw: String): String =
            raw.trim().removePrefix("#").lowercase()
    }
}
