package com.example.offlinereminder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.offlinereminder.data.local.converter.RoomTypeConverters
import com.example.offlinereminder.data.local.dao.AttachmentDao
import com.example.offlinereminder.data.local.dao.CategoryDao
import com.example.offlinereminder.data.local.dao.ChecklistDao
import com.example.offlinereminder.data.local.dao.CustomSoundDao
import com.example.offlinereminder.data.local.dao.EventDao
import com.example.offlinereminder.data.local.dao.EventDependencyDao
import com.example.offlinereminder.data.local.dao.EventGroupDao
import com.example.offlinereminder.data.local.dao.EventRelationshipDao
import com.example.offlinereminder.data.local.dao.EventTemplateDao
import com.example.offlinereminder.data.local.dao.EventTypeDao
import com.example.offlinereminder.data.local.dao.NotificationInstanceDao
import com.example.offlinereminder.data.local.dao.NotificationProfileDao
import com.example.offlinereminder.data.local.dao.NotificationScheduleDao
import com.example.offlinereminder.data.local.dao.OccurrenceExceptionDao
import com.example.offlinereminder.data.local.dao.RestoreOperationDao
import com.example.offlinereminder.data.local.dao.SavedLocationDao
import com.example.offlinereminder.data.local.dao.TagDao
import com.example.offlinereminder.data.local.entity.AttachmentEntity
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity
import com.example.offlinereminder.data.local.entity.CustomSoundEntity
import com.example.offlinereminder.data.local.entity.EventDependencyEntity
import com.example.offlinereminder.data.local.entity.EventEntity
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.entity.EventGroupEntity
import com.example.offlinereminder.data.local.entity.EventRelationshipEntity
import com.example.offlinereminder.data.local.entity.EventTagCrossRef
import com.example.offlinereminder.data.local.entity.EventTemplateEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.data.local.entity.NotificationInstanceEntity
import com.example.offlinereminder.data.local.entity.NotificationProfileEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.OccurrenceExceptionEntity
import com.example.offlinereminder.data.local.entity.RestoreOperationEntity
import com.example.offlinereminder.data.local.entity.SavedLocationEntity
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
        EventRelationshipEntity::class,
        RestoreOperationEntity::class,
        CustomSoundEntity::class,
        NotificationProfileEntity::class,
        EventTemplateEntity::class,
        SavedLocationEntity::class
    ],
    version = 3,
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
    abstract fun restoreOperationDao(): RestoreOperationDao
    abstract fun customSoundDao(): CustomSoundDao
    abstract fun notificationProfileDao(): NotificationProfileDao
    abstract fun eventTemplateDao(): EventTemplateDao
    abstract fun savedLocationDao(): SavedLocationDao

    companion object {
        const val DATABASE_NAME = "offline_reminder.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create restore_operations table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `restore_operations` (
                        `operation_id` TEXT NOT NULL PRIMARY KEY,
                        `status` TEXT NOT NULL,
                        `backup_version` INTEGER NOT NULL,
                        `committed_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 2. Create custom_sounds table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_sounds` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `relative_path` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `file_size_bytes` INTEGER NOT NULL,
                        `duration_millis` INTEGER NOT NULL,
                        `added_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 3. Create notification_profiles table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_profiles` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `delivery_type` TEXT NOT NULL,
                        `sound_source` TEXT NOT NULL,
                        `custom_sound_id` TEXT DEFAULT NULL,
                        `system_sound_uri` TEXT DEFAULT NULL,
                        `vibration_enabled` INTEGER NOT NULL,
                        `default_snooze_duration_minutes` INTEGER NOT NULL,
                        `is_default` INTEGER NOT NULL,
                        FOREIGN KEY(`custom_sound_id`) REFERENCES `custom_sounds`(`id`) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_profiles_custom_sound_id` ON `notification_profiles`(`custom_sound_id`)")

                // 4. Create event_templates table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `event_templates` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `event_type_id` TEXT NOT NULL,
                        `category_id` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `default_duration_millis` INTEGER NOT NULL,
                        `is_all_day` INTEGER NOT NULL,
                        `location_name` TEXT,
                        `location_address` TEXT,
                        `has_recurrence` INTEGER NOT NULL,
                        `recurrence_rule_json` TEXT,
                        `notification_schedules_json` TEXT,
                        `checklist_json` TEXT,
                        `tag_ids_json` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT,
                        FOREIGN KEY(`event_type_id`) REFERENCES `event_types`(`id`) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_templates_category_id` ON `event_templates`(`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_templates_event_type_id` ON `event_templates`(`event_type_id`)")

                // 5. Rebuild notification_schedules to add columns and foreign keys cleanly
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_schedules_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `event_id` TEXT NOT NULL,
                        `offset_duration_millis` INTEGER NOT NULL,
                        `trigger_type` TEXT NOT NULL,
                        `profile_id` TEXT,
                        `delivery_type` TEXT NOT NULL,
                        `sound_source` TEXT NOT NULL,
                        `custom_sound_id` TEXT,
                        `system_sound_uri` TEXT,
                        FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`custom_sound_id`) REFERENCES `custom_sounds`(`id`) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `notification_schedules_new` (
                        `id`, `event_id`, `offset_duration_millis`, `trigger_type`, `profile_id`,
                        `delivery_type`, `sound_source`, `custom_sound_id`, `system_sound_uri`
                    )
                    SELECT `id`, `event_id`, `offset_duration_millis`, `trigger_type`, `profile_id`,
                           'NOTIFICATION', 'DEFAULT', NULL, NULL
                    FROM `notification_schedules`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `notification_schedules`")
                db.execSQL("ALTER TABLE `notification_schedules_new` RENAME TO `notification_schedules`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_schedules_event_id` ON `notification_schedules`(`event_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_schedules_custom_sound_id` ON `notification_schedules`(`custom_sound_id`)")

                // 6. Create index on events for 30-day trash expiration queries
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_lifecycle_status_deleted_at` ON `events`(`lifecycle_status`, `deleted_at`)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_locations` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `building` TEXT NOT NULL,
                        `room` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `notes` TEXT NOT NULL,
                        `is_favorite` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_locations_name` ON `saved_locations` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_locations_is_favorite` ON `saved_locations` (`is_favorite`)")
            }
        }

        fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        }
    }
}
