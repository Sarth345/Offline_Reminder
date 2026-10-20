package com.example.offlinereminder.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.offlinereminder.data.local.entity.AttachmentEntity
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.TagEntity

data class EventWithDetails(
    @Embedded
    val event: EventEntity,

    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryEntity? = null,

    @Relation(
        parentColumn = "event_type_id",
        entityColumn = "id"
    )
    val eventType: EventTypeEntity? = null,

    @Relation(
        entity = ChecklistEntity::class,
        parentColumn = "id",
        entityColumn = "event_id"
    )
    val checklistWithItems: ChecklistWithItems? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "event_id"
    )
    val notificationSchedules: List<NotificationScheduleEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "event_id"
    )
    val attachments: List<AttachmentEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EventGroupCrossRef::class,
            parentColumn = "event_id",
            entityColumn = "group_id"
        )
    )
    val groups: List<EventGroupEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EventTagCrossRef::class,
            parentColumn = "event_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<TagEntity> = emptyList()
)
