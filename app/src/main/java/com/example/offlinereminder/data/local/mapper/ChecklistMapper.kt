package com.example.offlinereminder.data.local.mapper

import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity
import com.example.offlinereminder.data.local.relation.ChecklistWithItems
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem

fun ChecklistItemEntity.toDomain(): ChecklistItem = ChecklistItem(
    id = id,
    text = text,
    isCompleted = isCompleted,
    sortOrder = sortOrder
)

fun ChecklistItem.toEntity(checklistId: String): ChecklistItemEntity = ChecklistItemEntity(
    id = id,
    checklistId = checklistId,
    text = text,
    isCompleted = isCompleted,
    sortOrder = sortOrder
)

fun ChecklistWithItems.toDomain(): Checklist = Checklist(
    id = checklist.id,
    title = checklist.title,
    items = items.sortedBy { it.sortOrder }.map { it.toDomain() }
)

fun Checklist.toEntity(eventId: String): ChecklistEntity = ChecklistEntity(
    id = id,
    eventId = eventId,
    title = title
)
