package com.example.offlinereminder.domain.model

import java.util.UUID

/**
 * Top-level classification category (Personal, Work, Education, Health, etc.).
 */
data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int? = null,
    val icon: String? = null,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0
)
