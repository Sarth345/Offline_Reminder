package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "event_types",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["key"], unique = true)
    ]
)
data class EventTypeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val key: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "default_color")
    val defaultColor: Int? = null,
    @ColumnInfo(name = "default_icon")
    val defaultIcon: String? = null,
    val aliases: List<String> = emptyList(),
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
