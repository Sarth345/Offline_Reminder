package com.example.offlinereminder.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity

data class ChecklistWithItems(
    @Embedded
    val checklist: ChecklistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "checklist_id"
    )
    val items: List<ChecklistItemEntity> = emptyList()
)
