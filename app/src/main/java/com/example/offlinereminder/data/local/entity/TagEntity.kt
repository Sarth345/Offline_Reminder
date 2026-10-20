package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["normalized_name"], unique = true)
    ]
)
data class TagEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val color: Int? = null
)
