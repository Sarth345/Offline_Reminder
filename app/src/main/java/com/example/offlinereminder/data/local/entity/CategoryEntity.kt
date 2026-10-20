package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val color: Int? = null,
    val icon: String? = null,
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)
