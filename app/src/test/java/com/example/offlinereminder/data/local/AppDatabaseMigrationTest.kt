package com.example.offlinereminder.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.data.local.entity.CustomSoundEntity
import com.example.offlinereminder.data.local.entity.EventTemplateEntity
import com.example.offlinereminder.data.local.entity.NotificationProfileEntity
import com.example.offlinereminder.data.local.entity.NotificationScheduleEntity
import com.example.offlinereminder.data.local.entity.RestoreOperationEntity
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundSourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_migration_app.db"
    private var roomDb: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @After
    fun tearDown() {
        roomDb?.close()
        context.getDatabasePath(dbName).delete()
    }

    private fun createV1Database(): SupportSQLiteDatabase {
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createV1Schema(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        return helper.writableDatabase
    }

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `color` INTEGER,
                `icon` TEXT,
                `is_custom` INTEGER NOT NULL,
                `sort_order` INTEGER NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_types` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `key` TEXT NOT NULL,
                `category_id` TEXT NOT NULL,
                `default_color` INTEGER,
                `default_icon` TEXT,
                `aliases` TEXT NOT NULL,
                `is_custom` INTEGER NOT NULL,
                `is_pinned` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT
            );
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_types_key` ON `event_types`(`key`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_types_category_id` ON `event_types`(`category_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_groups` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `color` INTEGER,
                `icon` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tags` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `normalized_name` TEXT NOT NULL,
                `color` INTEGER
            );
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalized_name` ON `tags`(`normalized_name`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `events` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `event_type_id` TEXT NOT NULL,
                `category_id` TEXT NOT NULL,
                `priority` TEXT NOT NULL,
                `temporal_type` TEXT NOT NULL,
                `start_date` TEXT NOT NULL,
                `start_time` TEXT,
                `end_date` TEXT,
                `end_time` TEXT,
                `zone_id` TEXT NOT NULL,
                `location_place_name` TEXT,
                `location_address_text` TEXT,
                `location_building` TEXT,
                `location_room` TEXT,
                `location_latitude` REAL,
                `location_longitude` REAL,
                `has_recurrence` INTEGER NOT NULL,
                `recurrence_frequency` TEXT,
                `recurrence_interval` INTEGER,
                `recurrence_days_of_week` TEXT,
                `recurrence_day_of_month` INTEGER,
                `recurrence_set_position` INTEGER,
                `recurrence_month_of_year` INTEGER,
                `recurrence_end_type` TEXT,
                `recurrence_until_date` TEXT,
                `recurrence_max_occurrences` INTEGER,
                `recurrence_excluded_dates` TEXT,
                `lifecycle_status` TEXT NOT NULL,
                `is_favorite` INTEGER NOT NULL,
                `parent_event_id` TEXT,
                `template_id` TEXT,
                `custom_metadata` TEXT NOT NULL,
                `deleted_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT,
                FOREIGN KEY(`event_type_id`) REFERENCES `event_types`(`id`) ON DELETE RESTRICT,
                FOREIGN KEY(`parent_event_id`) REFERENCES `events`(`id`) ON DELETE SET NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_category_id` ON `events`(`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_event_type_id` ON `events`(`event_type_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_lifecycle_status` ON `events`(`lifecycle_status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_parent_event_id` ON `events`(`parent_event_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_start_date` ON `events`(`start_date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_is_favorite` ON `events`(`is_favorite`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_group_cross_ref` (
                `event_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                PRIMARY KEY(`event_id`, `group_id`),
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`group_id`) REFERENCES `event_groups`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_group_cross_ref_group_id` ON `event_group_cross_ref`(`group_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_tag_cross_ref` (
                `event_id` TEXT NOT NULL,
                `tag_id` TEXT NOT NULL,
                PRIMARY KEY(`event_id`, `tag_id`),
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_tag_cross_ref_tag_id` ON `event_tag_cross_ref`(`tag_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `checklists` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `event_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklists_event_id` ON `checklists`(`event_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `checklist_items` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `checklist_id` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `is_completed` INTEGER NOT NULL,
                `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`checklist_id`) REFERENCES `checklists`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_checklist_id` ON `checklist_items`(`checklist_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `notification_schedules` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `event_id` TEXT NOT NULL,
                `offset_duration_millis` INTEGER NOT NULL,
                `trigger_type` TEXT NOT NULL,
                `profile_id` TEXT,
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_schedules_event_id` ON `notification_schedules`(`event_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `notification_instances` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `event_id` TEXT NOT NULL,
                `occurrence_original_start_date_time` TEXT NOT NULL,
                `occurrence_original_zone_id` TEXT NOT NULL,
                `schedule_id` TEXT,
                `trigger_epoch_millis` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `snooze_count` INTEGER NOT NULL,
                `snooze_until` INTEGER,
                `request_code` INTEGER NOT NULL,
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`schedule_id`) REFERENCES `notification_schedules`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_instances_event_id` ON `notification_instances`(`event_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_instances_schedule_id` ON `notification_instances`(`schedule_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_instances_trigger_epoch_millis` ON `notification_instances`(`trigger_epoch_millis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_instances_status` ON `notification_instances`(`status`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `occurrence_exceptions` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `event_id` TEXT NOT NULL,
                `original_start_date_time` TEXT NOT NULL,
                `original_zone_id` TEXT NOT NULL,
                `occurrence_id` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `rescheduled_start_date_time` TEXT,
                `rescheduled_end_date_time` TEXT,
                `rescheduled_zone_id` TEXT,
                `actual_execution_date_time` TEXT,
                `completed_at` INTEGER,
                `title_override` TEXT,
                `notes_override` TEXT,
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrence_exceptions_event_id` ON `occurrence_exceptions`(`event_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_occurrence_exceptions_occurrence_id` ON `occurrence_exceptions`(`occurrence_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_occurrence_exceptions_event_id_original_start_date_time_original_zone_id` ON `occurrence_exceptions`(`event_id`, `original_start_date_time`, `original_zone_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_attachments` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `event_id` TEXT NOT NULL,
                `local_uri` TEXT NOT NULL,
                `file_name` TEXT NOT NULL,
                `mime_type` TEXT NOT NULL,
                `file_size_bytes` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_attachments_event_id` ON `event_attachments`(`event_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_dependencies` (
                `source_event_id` TEXT NOT NULL,
                `target_event_id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                PRIMARY KEY(`source_event_id`, `target_event_id`),
                FOREIGN KEY(`source_event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`target_event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_dependencies_target_event_id` ON `event_dependencies`(`target_event_id`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `event_relationships` (
                `source_event_id` TEXT NOT NULL,
                `target_event_id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                PRIMARY KEY(`source_event_id`, `target_event_id`),
                FOREIGN KEY(`source_event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`target_event_id`) REFERENCES `events`(`id`) ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_relationships_target_event_id` ON `event_relationships`(`target_event_id`)")
    }

    @Test
    fun migration1To2_preservesAllV1DataAndSetsDefaults() = runTest {
        // 1. Create and populate v1 database
        val v1Db = createV1Database()
        val nowMillis = Instant.parse("2026-10-21T00:00:00Z").toEpochMilli()

        v1Db.execSQL("INSERT INTO categories VALUES ('cat-work', 'Work', -16776961, 'icon_work', 1, 1)")
        v1Db.execSQL("INSERT INTO event_types VALUES ('type-meeting', 'Meeting', 'meeting_key', 'cat-work', NULL, NULL, '[]', 0, 0, $nowMillis)")
        v1Db.execSQL("INSERT INTO event_groups VALUES ('grp-1', 'Project Alpha', 'Important team tasks', NULL, NULL, $nowMillis, $nowMillis)")
        v1Db.execSQL("INSERT INTO tags VALUES ('tag-urgent', 'Urgent', 'urgent', -65536)")

        v1Db.execSQL("""
            INSERT INTO events VALUES (
                'evt-1', 'Budget Review', 'Q4 Budget', 'type-meeting', 'cat-work', 'HIGH',
                'DATE_ONLY', '2026-10-22', NULL, NULL, NULL, 'UTC',
                'Office HQ', 'Main Street 1', 'Tower A', 'Room 101', 37.77, -122.41,
                0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                'ACTIVE', 1, NULL, NULL, '{}', NULL, $nowMillis, $nowMillis
            )
        """.trimIndent())

        v1Db.execSQL("""
            INSERT INTO events VALUES (
                'evt-2', 'Board Meeting', 'Annual Review', 'type-meeting', 'cat-work', 'CRITICAL',
                'DATE_ONLY', '2026-10-23', NULL, NULL, NULL, 'UTC',
                'Office HQ', 'Main Street 1', 'Tower A', 'Room 102', 37.77, -122.41,
                0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                'ACTIVE', 0, NULL, NULL, '{}', NULL, $nowMillis, $nowMillis
            )
        """.trimIndent())

        v1Db.execSQL("INSERT INTO event_group_cross_ref VALUES ('evt-1', 'grp-1')")
        v1Db.execSQL("INSERT INTO event_tag_cross_ref VALUES ('evt-1', 'tag-urgent')")
        v1Db.execSQL("INSERT INTO checklists VALUES ('chk-1', 'evt-1', 'Prep Checklist')")
        v1Db.execSQL("INSERT INTO checklist_items VALUES ('item-1', 'chk-1', 'Print slides', 1, 0)")
        v1Db.execSQL("INSERT INTO event_attachments VALUES ('att-1', 'evt-1', 'attachments/slides.pdf', 'slides.pdf', 'application/pdf', 1024, $nowMillis)")
        v1Db.execSQL("INSERT INTO event_dependencies VALUES ('evt-1', 'evt-2', 'PREREQUISITE')")
        v1Db.execSQL("INSERT INTO event_relationships VALUES ('evt-1', 'evt-2', 'RELATED_TO')")

        // v1 notification schedule
        v1Db.execSQL("INSERT INTO notification_schedules VALUES ('sched-1', 'evt-1', 600000, 'BEFORE_START', 'prof-1')")
        v1Db.execSQL("INSERT INTO notification_instances VALUES ('inst-1', 'evt-1', '2026-10-22T09:00:00', 'UTC', 'sched-1', $nowMillis, 'SCHEDULED', 0, NULL, 1001)")
        v1Db.execSQL("INSERT INTO occurrence_exceptions VALUES ('exc-1', 'evt-1', '2026-10-22T09:00:00', 'UTC', 'evt-1_2026-10-22T09:00:00_UTC', 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, NULL)")

        v1Db.close()

        // 2. Open with Room at current version and apply migrations
        roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // 3. Verify events survived
        val event = roomDb!!.eventDao().getById("evt-1")
        assertNotNull(event)
        assertEquals("Budget Review", event?.title)
        assertEquals(Priority.HIGH, event?.priority)
        assertEquals(EventLifecycleStatus.ACTIVE, event?.lifecycleStatus)
        assertEquals(true, event?.isFavorite)

        val event2 = roomDb!!.eventDao().getById("evt-2")
        assertNotNull(event2)
        assertEquals("Board Meeting", event2?.title)
        assertEquals(Priority.CRITICAL, event2?.priority)

        // 4. Verify categories, event types, groups, tags survived
        val category = roomDb!!.categoryDao().getById("cat-work")
        assertNotNull(category)
        assertEquals("Work", category?.name)

        val eventType = roomDb!!.eventTypeDao().getById("type-meeting")
        assertNotNull(eventType)
        assertEquals("Meeting", eventType?.name)

        val group = roomDb!!.eventGroupDao().getById("grp-1")
        assertNotNull(group)
        assertEquals("Project Alpha", group?.name)

        val tag = roomDb!!.tagDao().getById("tag-urgent")
        assertNotNull(tag)
        assertEquals("Urgent", tag?.name)

        // 5. Verify event memberships (groups & tags cross refs)
        val eventWithDetails = roomDb!!.eventDao().getEventWithDetailsById("evt-1")
        assertNotNull(eventWithDetails)
        assertEquals(1, eventWithDetails?.groups?.size)
        assertEquals("grp-1", eventWithDetails?.groups?.get(0)?.id)
        assertEquals(1, eventWithDetails?.tags?.size)
        assertEquals("tag-urgent", eventWithDetails?.tags?.get(0)?.id)

        // 6. Verify dependencies and relationships survived
        val deps = roomDb!!.eventDependencyDao().getAll()
        assertEquals(1, deps.size)
        assertEquals("evt-1", deps[0].sourceEventId)
        assertEquals("evt-2", deps[0].targetEventId)

        val rels = roomDb!!.eventRelationshipDao().getAll()
        assertEquals(1, rels.size)
        assertEquals("evt-1", rels[0].sourceEventId)
        assertEquals("evt-2", rels[0].targetEventId)

        // 7. Verify occurrence exceptions survived
        val exceptions = roomDb!!.occurrenceExceptionDao().getExceptionsForEvent("evt-1")
        assertEquals(1, exceptions.size)
        assertEquals("exc-1", exceptions[0].id)

        // 8. Verify notification instances survived
        val instance = roomDb!!.notificationInstanceDao().getById("inst-1")
        assertNotNull(instance)
        assertEquals("sched-1", instance?.scheduleId)

        // 9. Verify notification schedule survived with Phase 12 defaults
        val schedules = roomDb!!.notificationScheduleDao().getSchedulesForEvent("evt-1")
        assertEquals(1, schedules.size)
        val schedule = schedules[0]
        assertEquals("sched-1", schedule.id)
        assertEquals("evt-1", schedule.eventId)
        assertEquals(600000L, schedule.offsetDurationMillis)
        assertEquals(NotificationTriggerType.BEFORE_START, schedule.triggerType)
        assertEquals("prof-1", schedule.profileId)
        assertEquals(ReminderDeliveryType.NOTIFICATION, schedule.deliveryType)
        assertEquals(SoundSourceType.DEFAULT, schedule.soundSource)
        assertNull(schedule.customSoundId)
        assertNull(schedule.systemSoundUri)

        // 10. Verify related tables survived (checklists & attachments)
        val checklist = roomDb!!.checklistDao().getChecklistWithItemsByEventId("evt-1")
        assertNotNull(checklist)
        assertEquals("Prep Checklist", checklist?.checklist?.title)
        assertEquals(1, checklist?.items?.size)
        assertEquals("Print slides", checklist?.items?.get(0)?.text)
        assertEquals(true, checklist?.items?.get(0)?.isCompleted)

        val attachments = roomDb!!.attachmentDao().getAttachmentsForEvent("evt-1")
        assertEquals(1, attachments.size)
        assertEquals("slides.pdf", attachments[0].fileName)

        // 11. Verify restore_operations table exists and is empty
        val restoreOps = roomDb!!.restoreOperationDao().getAll()
        assertEquals(0, restoreOps.size)

        // 12. Verify new Phase 12 tables exist and are empty
        val customSounds = roomDb!!.customSoundDao().getAll()
        assertEquals(0, customSounds.size)

        val profiles = roomDb!!.notificationProfileDao().getAll()
        assertEquals(0, profiles.size)

        val templates = roomDb!!.eventTemplateDao().getAll()
        assertEquals(0, templates.size)
    }

    @Test
    fun migration1To2_foreignKeyIntegrityAfterMigration() = runTest {
        // Run migration on v1 database
        val v1Db = createV1Database()
        val nowMillis = Instant.parse("2026-10-21T00:00:00Z").toEpochMilli()
        v1Db.execSQL("INSERT INTO categories VALUES ('cat-work', 'Work', -16776961, 'icon_work', 1, 1)")
        v1Db.execSQL("INSERT INTO event_types VALUES ('type-meeting', 'Meeting', 'meeting_key', 'cat-work', NULL, NULL, '[]', 0, 0, $nowMillis)")
        v1Db.execSQL("""
            INSERT INTO events VALUES (
                'evt-fk', 'Test Event', '', 'type-meeting', 'cat-work', 'NORMAL',
                'DATE_ONLY', '2026-10-22', NULL, NULL, NULL, 'UTC',
                NULL, NULL, NULL, NULL, NULL, NULL,
                0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                'ACTIVE', 0, NULL, NULL, '{}', NULL, $nowMillis, $nowMillis
            )
        """.trimIndent())
        v1Db.close()

        roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val testInstant = Instant.parse("2026-10-21T10:00:00Z")

        // 1. Insert a custom sound
        val sound = CustomSoundEntity(
            id = "snd-bell",
            title = "Gentle Bell",
            fileName = "gentle_bell.mp3",
            relativePath = "sounds/gentle_bell.mp3",
            mimeType = "audio/mpeg",
            fileSizeBytes = 20480,
            durationMillis = 3500,
            addedAt = testInstant
        )
        roomDb!!.customSoundDao().insert(sound)

        // 2. Insert notification profile referencing custom sound
        val profile = NotificationProfileEntity(
            id = "prof-vip",
            name = "VIP Profile",
            deliveryType = ReminderDeliveryType.ALARM,
            soundSource = SoundSourceType.CUSTOM,
            customSoundId = "snd-bell",
            vibrationEnabled = true,
            defaultSnoozeDurationMinutes = 15,
            isDefault = false
        )
        roomDb!!.notificationProfileDao().insert(profile)

        // 3. Insert schedule referencing custom sound and event
        val schedule = NotificationScheduleEntity(
            id = "sched-custom-sound",
            eventId = "evt-fk",
            offsetDurationMillis = 900000,
            triggerType = NotificationTriggerType.BEFORE_START,
            profileId = "prof-vip",
            deliveryType = ReminderDeliveryType.ALARM,
            soundSource = SoundSourceType.CUSTOM,
            customSoundId = "snd-bell"
        )
        roomDb!!.notificationScheduleDao().insert(schedule)

        // Verify inserted
        val fetchedSchedule = roomDb!!.notificationScheduleDao().getSchedulesForEvent("evt-fk")[0]
        assertEquals("snd-bell", fetchedSchedule.customSoundId)
        assertEquals(ReminderDeliveryType.ALARM, fetchedSchedule.deliveryType)

        // 4. Delete custom sound -> foreign key must SET_NULL on schedule and profile
        roomDb!!.customSoundDao().deleteById("snd-bell")

        val scheduleAfterSoundDelete = roomDb!!.notificationScheduleDao().getSchedulesForEvent("evt-fk")[0]
        assertNull(scheduleAfterSoundDelete.customSoundId)
        // Schedule itself survived!
        assertEquals("sched-custom-sound", scheduleAfterSoundDelete.id)

        val profileAfterSoundDelete = roomDb!!.notificationProfileDao().getById("prof-vip")
        assertNotNull(profileAfterSoundDelete)
        assertNull(profileAfterSoundDelete?.customSoundId)

        // 5. Delete parent event -> foreign key must CASCADE delete schedule
        roomDb!!.eventDao().deleteById("evt-fk")
        val schedulesAfterEventDelete = roomDb!!.notificationScheduleDao().getSchedulesForEvent("evt-fk")
        assertEquals(0, schedulesAfterEventDelete.size)
    }

    @Test
    fun migration1To2_restoreOperationsCanBeRecorded() = runTest {
        val v1Db = createV1Database()
        v1Db.close()

        roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val now = Instant.parse("2026-10-21T12:00:00Z")
        val op = RestoreOperationEntity(
            operationId = "op-restore-1",
            status = "COMMITTED",
            backupVersion = 2,
            committedAt = now
        )

        roomDb!!.restoreOperationDao().insert(op)

        val retrieved = roomDb!!.restoreOperationDao().getById("op-restore-1")
        assertNotNull(retrieved)
        assertEquals("op-restore-1", retrieved?.operationId)
        assertEquals("COMMITTED", retrieved?.status)
        assertEquals(2, retrieved?.backupVersion)
        assertEquals(now, retrieved?.committedAt)

        val all = roomDb!!.restoreOperationDao().getAll()
        assertEquals(1, all.size)
    }

    @Test
    fun migration1To2_eventTemplatePersistence() = runTest {
        val v1Db = createV1Database()
        val nowMillis = Instant.parse("2026-10-21T00:00:00Z").toEpochMilli()
        v1Db.execSQL("INSERT INTO categories VALUES ('cat-work', 'Work', -16776961, 'icon_work', 1, 1)")
        v1Db.execSQL("INSERT INTO event_types VALUES ('type-meeting', 'Meeting', 'meeting_key', 'cat-work', NULL, NULL, '[]', 0, 0, $nowMillis)")
        v1Db.close()

        roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val now = Instant.parse("2026-10-21T12:00:00Z")
        val template = EventTemplateEntity(
            id = "tmpl-1",
            name = "Weekly Sync Template",
            description = "Standard 30min sync",
            eventTypeId = "type-meeting",
            categoryId = "cat-work",
            priority = Priority.NORMAL,
            defaultDurationMillis = 1800000L,
            isAllDay = false,
            locationName = "Meeting Room B",
            hasRecurrence = true,
            createdAt = now,
            updatedAt = now
        )

        roomDb!!.eventTemplateDao().insert(template)

        val retrieved = roomDb!!.eventTemplateDao().getById("tmpl-1")
        assertNotNull(retrieved)
        assertEquals("Weekly Sync Template", retrieved?.name)
        assertEquals("Meeting Room B", retrieved?.locationName)
        assertEquals(1800000L, retrieved?.defaultDurationMillis)

        val byCat = roomDb!!.eventTemplateDao().countTemplatesByCategory("cat-work")
        assertEquals(1, byCat)

        val byType = roomDb!!.eventTemplateDao().countTemplatesByEventType("type-meeting")
        assertEquals(1, byType)
    }

    @Test
    fun migration2To3_realisticPre1_1DataRemainsIntactAndSavedLocationsWork() = runTest {
        val v1Db = createV1Database()
        val nowMillis = Instant.parse("2026-10-21T00:00:00Z").toEpochMilli()
        v1Db.execSQL("INSERT INTO categories VALUES ('cat-pre11', 'Personal', -16776961, 'icon_personal', 1, 1)")
        v1Db.execSQL("INSERT INTO event_types VALUES ('type-task', 'Task', 'task_key', 'cat-pre11', NULL, NULL, '[]', 0, 0, $nowMillis)")
        v1Db.execSQL("""
            INSERT INTO events VALUES (
                'evt-pre11-1', 'Pay Mortgage', 'Important financial task', 'type-task', 'cat-pre11', 'HIGH',
                'DATE_ONLY', '2026-10-31', NULL, NULL, NULL, 'UTC',
                'Bank Downtown', NULL, NULL, NULL, NULL, NULL,
                0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                'ACTIVE', 0, NULL, NULL, '{}', NULL, $nowMillis, $nowMillis
            )
        """.trimIndent())
        v1Db.close()

        roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // Verify existing V2 / pre-1.1 event data is intact
        val eventEntity = roomDb!!.eventDao().getById("evt-pre11-1")
        assertNotNull(eventEntity)
        assertEquals("Pay Mortgage", eventEntity?.title)
        assertEquals("Important financial task", eventEntity?.description)
        assertEquals("HIGH", eventEntity?.priority?.name)
        assertEquals("Bank Downtown", eventEntity?.locationPlaceName)

        // Verify new saved_locations table in V3
        val savedLoc = com.example.offlinereminder.data.local.entity.SavedLocationEntity(
            id = "loc-mig-1",
            name = "Headquarters",
            address = "500 Market St",
            building = "Tower A",
            room = "Rm 401",
            latitude = 37.79,
            longitude = -122.40,
            notes = "Security card needed",
            isFavorite = true,
            createdAt = Instant.ofEpochMilli(nowMillis),
            updatedAt = Instant.ofEpochMilli(nowMillis)
        )
        roomDb!!.savedLocationDao().insert(savedLoc)

        val retrievedLoc = roomDb!!.savedLocationDao().getById("loc-mig-1")
        assertNotNull(retrievedLoc)
        assertEquals("Headquarters", retrievedLoc?.name)
        assertEquals("Tower A", retrievedLoc?.building)
        assertTrue(retrievedLoc?.isFavorite == true)

        val searchLoc = roomDb!!.savedLocationDao().searchFlow("market").first()
        assertEquals(1, searchLoc.size)
        assertEquals("Headquarters", searchLoc.first().name)
    }
}
