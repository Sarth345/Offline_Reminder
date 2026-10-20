package com.example.offlinereminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.offlinereminder.data.local.converter.RoomTypeConverters
import com.example.offlinereminder.data.local.dao.AttachmentDao
import com.example.offlinereminder.data.local.dao.CategoryDao
import com.example.offlinereminder.data.local.dao.ChecklistDao
import com.example.offlinereminder.data.local.dao.EventDao
import com.example.offlinereminder.data.local.dao.EventDependencyDao
import com.example.offlinereminder.data.local.dao.EventGroupDao
import com.example.offlinereminder.data.local.dao.EventRelationshipDao
import com.example.offlinereminder.data.local.dao.EventTypeDao
import com.example.offlinereminder.data.local.dao.NotificationInstanceDao
import com.example.offlinereminder.data.local.dao.NotificationScheduleDao
import com.example.offlinereminder.data.local.dao.OccurrenceExceptionDao
import com.example.offlinereminder.data.local.dao.TagDao
import com.example.offlinereminder.data.local.entity.AttachmentEntity
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity
import com.example.offlinereminder.data.local.entity.EventDependencyEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.entity.EventRelationshipEntity
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationInstanceEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.OccurrenceExceptionEntity
import com.example.offlinereminder.data.local.entity.TagEntity

@Database(
    entities = [
        CategoryEntity::class,
        EventTypeEntity::class,
        EventGroupEntity::class,
        TagEntity::class,
        EventEntity::class,
        EventGroupCrossRef::class,
        EventTagCrossRef::class,
        ChecklistEntity::class,
        ChecklistItemEntity::class,
        NotificationScheduleEntity::class,
        NotificationInstanceEntity::class,
        OccurrenceExceptionEntity::class,
        AttachmentEntity::class,
        EventDependencyEntity::class,
        EventRelationshipEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun eventTypeDao(): EventTypeDao
    abstract fun eventGroupDao(): EventGroupDao
    abstract fun tagDao(): TagDao
    abstract fun eventDao(): EventDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun notificationScheduleDao(): NotificationScheduleDao
    abstract fun notificationInstanceDao(): NotificationInstanceDao
    abstract fun occurrenceExceptionDao(): OccurrenceExceptionDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun eventDependencyDao(): EventDependencyDao
    abstract fun eventRelationshipDao(): EventRelationshipDao

    companion object {
        const val DATABASE_NAME = "offline_reminder.db"
    }
}
