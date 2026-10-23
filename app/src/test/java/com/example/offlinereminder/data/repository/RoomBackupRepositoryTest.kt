package com.example.offlinereminder.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TestTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.entity.CategoryEntity
import com.example.offlinereminder.data.local.entity.EventTypeEntity
import com.example.offlinereminder.domain.backup.AttachmentBackupDto
import com.example.offlinereminder.domain.backup.BackupConstants
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.CategoryBackupDto
import com.example.offlinereminder.domain.backup.ChecklistBackupDto
import com.example.offlinereminder.domain.backup.ChecklistItemBackupDto
import com.example.offlinereminder.domain.backup.EventBackupDto
import com.example.offlinereminder.domain.backup.EventDependencyBackupDto
import com.example.offlinereminder.domain.backup.EventRelationshipBackupDto
import com.example.offlinereminder.domain.backup.EventTypeBackupDto
import com.example.offlinereminder.domain.backup.GroupBackupDto
import com.example.offlinereminder.domain.backup.NotificationScheduleBackupDto
import com.example.offlinereminder.domain.backup.OccurrenceExceptionBackupDto
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.SavedLocationBackupDto
import com.example.offlinereminder.domain.backup.TagBackupDto
import com.example.offlinereminder.domain.backup.TemporalBackupDto
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.RelationshipType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class RoomBackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomBackupRepository
    private lateinit var tempDir: File

    private val testInstant = Instant.parse("2026-10-20T10:00:00Z")
    private val testTimeProvider = TestTimeProvider(
        currentInstant = testInstant,
        currentZoneId = ZoneId.of("UTC")
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(context.filesDir, "test_attachments_${System.currentTimeMillis()}").apply { mkdirs() }
        repository = RoomBackupRepository(
            database = db,
            timeProvider = testTimeProvider,
            attachmentsDir = tempDir
        )
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun createBackupPayload_emptyDatabase_returnsValidEmptyPayload() = runTest {
        val result = repository.createBackupPayload()
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(1, payload.backupVersion)
        assertEquals(0, payload.events.size)
        assertEquals(0, payload.categories.size)
    }

    @Test
    fun createBackupPayload_populatedDatabase_exportsAllEntities() = runTest {
        // Pre-populate DB directly
        db.categoryDao().insert(CategoryEntity("cat-1", "Work", isCustom = true, sortOrder = 1))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Meeting", "meeting", "cat-1", createdAt = testInstant))

        val result = repository.createBackupPayload()
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(1, payload.categories.size)
        assertEquals("Work", payload.categories[0].name)
        assertEquals(1, payload.eventTypes.size)
        assertEquals("Meeting", payload.eventTypes[0].name)
    }

    @Test
    fun restoreBackupPayload_replaceAll_restoresCleanly() = runTest {
        // Pre-populate existing state
        db.categoryDao().insert(CategoryEntity("old-cat", "Old Category"))

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = testInstant.toString(),
            categories = listOf(CategoryBackupDto("cat-1", "New Work", sortOrder = 1)),
            eventTypes = listOf(EventTypeBackupDto("type-1", "Task", "task", "cat-1", createdAt = testInstant.toString())),
            groups = listOf(GroupBackupDto("grp-1", "Team Alpha", createdAt = testInstant.toString(), updatedAt = testInstant.toString())),
            tags = listOf(TagBackupDto("tag-1", "important", "important")),
            events = listOf(
                EventBackupDto(
                    id = "evt-1",
                    title = "Alpha Launch",
                    eventTypeId = "type-1",
                    categoryId = "cat-1",
                    temporal = TemporalBackupDto("DATE_ONLY", "2026-10-25"),
                    groupIds = listOf("grp-1"),
                    tagIds = listOf("tag-1"),
                    checklist = ChecklistBackupDto(
                        id = "chk-1",
                        title = "Checklist",
                        items = listOf(ChecklistItemBackupDto("item-1", "Verify infra", isCompleted = true))
                    ),
                    notificationSchedules = listOf(
                        NotificationScheduleBackupDto("notif-1", offsetSeconds = 3600)
                    ),
                    createdAt = testInstant.toString(),
                    updatedAt = testInstant.toString()
                )
            )
        )

        val restoreResult = repository.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isSuccess)
        val summary = restoreResult.getOrThrow()
        assertEquals(1, summary.eventsRestored)
        assertEquals(1, summary.categoriesRestored)
        assertEquals(1, summary.groupsRestored)

        // Verify old category was wiped and new records exist
        assertEquals(null, db.categoryDao().getById("old-cat"))
        val restoredCat = db.categoryDao().getById("cat-1")
        assertNotNull(restoredCat)
        assertEquals("New Work", restoredCat?.name)

        val restoredEvent = db.eventDao().getEventWithDetailsById("evt-1")
        assertNotNull(restoredEvent)
        assertEquals("Alpha Launch", restoredEvent?.event?.title)
        assertEquals(1, restoredEvent?.groups?.size)
        assertEquals(1, restoredEvent?.tags?.size)
        assertEquals(1, restoredEvent?.notificationSchedules?.size)
        assertEquals(1, restoredEvent?.checklistWithItems?.items?.size)
    }

    @Test
    fun restoreBackupPayload_mergeOverwrite_updatesExistingAndAddsNew() = runTest {
        // Pre-existing category and event
        db.categoryDao().insert(CategoryEntity("cat-1", "Old Name"))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Meeting", "meeting", "cat-1", createdAt = testInstant))

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = testInstant.toString(),
            categories = listOf(
                CategoryBackupDto("cat-1", "Updated Name"),
                CategoryBackupDto("cat-2", "Brand New Category")
            ),
            eventTypes = listOf(EventTypeBackupDto("type-1", "Meeting", "meeting", "cat-1", createdAt = testInstant.toString()))
        )

        val res = repository.restoreBackupPayload(payload, RestoreStrategy.MERGE_OVERWRITE)
        assertTrue(res.isSuccess)

        assertEquals("Updated Name", db.categoryDao().getById("cat-1")?.name)
        assertNotNull(db.categoryDao().getById("cat-2"))
    }

    @Test
    fun restoreBackupPayload_mergeKeepExisting_preservesLocalForMatchingIds() = runTest {
        db.categoryDao().insert(CategoryEntity("cat-1", "Original Local Name"))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Meeting", "meeting", "cat-1", createdAt = testInstant))

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = testInstant.toString(),
            categories = listOf(
                CategoryBackupDto("cat-1", "Incoming Name"),
                CategoryBackupDto("cat-2", "New Category")
            ),
            eventTypes = listOf(EventTypeBackupDto("type-1", "Meeting", "meeting", "cat-1", createdAt = testInstant.toString()))
        )

        val res = repository.restoreBackupPayload(payload, RestoreStrategy.MERGE_KEEP_EXISTING)
        assertTrue(res.isSuccess)

        // cat-1 should KEEP local name
        assertEquals("Original Local Name", db.categoryDao().getById("cat-1")?.name)
        // cat-2 should be added
        assertNotNull(db.categoryDao().getById("cat-2"))
    }

    @Test
    fun exportAndImportArchive_zipRoundTrip_succeeds() = runTest {
        // Pre-populate DB
        db.categoryDao().insert(CategoryEntity("cat-1", "Personal"))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Doctor", "doctor", "cat-1", createdAt = testInstant))

        val outStream = ByteArrayOutputStream()
        val exportResult = repository.exportBackupArchive(outStream)
        assertTrue(exportResult.isSuccess)
        val exportMeta = exportResult.getOrThrow()
        assertTrue(exportMeta.archiveSizeBytes > 0)

        // Clear DB
        db.eventTypeDao().deleteAll()
        db.categoryDao().deleteAll()
        assertEquals(0, db.categoryDao().getAll().size)

        // Import ZIP
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val importResult = repository.importBackupArchive(inStream, RestoreStrategy.REPLACE_ALL)
        assertTrue(importResult.isSuccess)

        // Verify restored
        val restoredCat = db.categoryDao().getById("cat-1")
        assertNotNull(restoredCat)
        assertEquals("Personal", restoredCat?.name)
    }

    @Test
    fun importBackupArchive_plainJsonInput_restoresCorrectly() = runTest {
        val jsonString = """
            {
                "backupVersion": 1,
                "appVersion": "1.0",
                "exportedAt": "2026-10-20T10:00:00Z",
                "categories": [
                    { "id": "cat-json", "name": "From Plain JSON", "sortOrder": 0 }
                ]
            }
        """.trimIndent()

        val inStream = ByteArrayInputStream(jsonString.toByteArray(Charsets.UTF_8))
        val res = repository.importBackupArchive(inStream, RestoreStrategy.REPLACE_ALL)
        assertTrue(res.isSuccess)

        val cat = db.categoryDao().getById("cat-json")
        assertNotNull(cat)
        assertEquals("From Plain JSON", cat?.name)
    }

    @Test
    fun importBackupArchive_malformedPayload_failsWithoutMutatingDatabase() = runTest {
        db.categoryDao().insert(CategoryEntity("cat-safe", "Initial Safe State"))

        // Malformed: missing category reference in event
        val badJson = """
            {
                "backupVersion": 1,
                "exportedAt": "2026-10-20T10:00:00Z",
                "categories": [],
                "eventTypes": [
                    { "id": "type-1", "name": "T", "key": "k", "categoryId": "missing-cat", "createdAt": "2026-10-20T10:00:00Z" }
                ],
                "events": [
                    {
                        "id": "evt-1",
                        "title": "Bad Event",
                        "eventTypeId": "type-1",
                        "categoryId": "missing-cat",
                        "temporal": { "type": "DATE_ONLY", "startDate": "2026-10-25" },
                        "createdAt": "2026-10-20T10:00:00Z",
                        "updatedAt": "2026-10-20T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()

        val inStream = ByteArrayInputStream(badJson.toByteArray(Charsets.UTF_8))
        val res = repository.importBackupArchive(inStream, RestoreStrategy.REPLACE_ALL)
        assertTrue(res.isFailure)

        // Database should be UNTOUCHED
        assertNotNull(db.categoryDao().getById("cat-safe"))
    }

    @Test
    fun exportAndImportArchive_withAttachmentFileAndMetadata_roundTripsContentAndStableEntryName() = runTest {
        // Pre-populate DB
        db.categoryDao().insert(CategoryEntity("cat-1", "Personal"))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Doc", "doc", "cat-1", createdAt = testInstant))

        val attId = "att-123"
        val attFileName = "document.pdf"
        val attFile = File(tempDir, "$attId.pdf").apply { writeText("Sample PDF document bytes") }

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = testInstant.toString(),
            categories = listOf(CategoryBackupDto("cat-1", "Personal")),
            eventTypes = listOf(EventTypeBackupDto("type-1", "Doc", "doc", "cat-1", createdAt = testInstant.toString())),
            events = listOf(
                EventBackupDto(
                    id = "evt-with-att",
                    title = "Review Document",
                    eventTypeId = "type-1",
                    categoryId = "cat-1",
                    temporal = TemporalBackupDto("DATE_ONLY", "2026-10-25"),
                    attachments = listOf(
                        AttachmentBackupDto(
                            id = attId,
                            fileName = attFileName,
                            mimeType = "application/pdf",
                            fileSizeBytes = attFile.length(),
                            createdAt = testInstant.toString()
                        )
                    ),
                    createdAt = testInstant.toString(),
                    updatedAt = testInstant.toString()
                )
            )
        )
        repository.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL).getOrThrow()

        // Export archive to ZIP
        val outStream = ByteArrayOutputStream()
        val exportResult = repository.exportBackupArchive(outStream)
        assertTrue(exportResult.isSuccess)
        assertEquals(1, exportResult.getOrThrow().totalAttachments)

        // Verify ZIP entry names: must contain backup.json and attachments/att-123.pdf
        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(outStream.toByteArray())).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        assertTrue("Expected backup.json in ZIP", entryNames.contains(BackupConstants.BACKUP_JSON_ENTRY_NAME))
        assertTrue("Expected attachments/$attId.pdf in ZIP", entryNames.contains("${BackupConstants.ATTACHMENTS_DIR}/$attId.pdf"))

        // Wipe DB and local files
        db.eventDao().deleteAll()
        db.eventTypeDao().deleteAll()
        db.categoryDao().deleteAll()
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        // Import ZIP
        val importResult = repository.importBackupArchive(ByteArrayInputStream(outStream.toByteArray()), RestoreStrategy.REPLACE_ALL)
        assertTrue(importResult.isSuccess)

        // Verify DB attachment metadata
        val eventDetails = db.eventDao().getEventWithDetailsById("evt-with-att")
        assertNotNull(eventDetails)
        assertEquals(1, eventDetails?.attachments?.size)
        val restoredAtt = eventDetails?.attachments?.first()
        assertEquals(attId, restoredAtt?.id)
        assertEquals(attFileName, restoredAtt?.fileName)
        assertEquals("attachments/$attId.pdf", restoredAtt?.localUri)

        // Verify file bytes restored on disk
        val restoredFile = File(tempDir, "$attId.pdf")
        assertTrue("Expected restored attachment file on disk", restoredFile.exists())
        assertEquals("Sample PDF document bytes", restoredFile.readText())
    }

    @Test
    fun exportBackupArchive_missingAttachmentFileOnDisk_exportsMetadataGracefully() = runTest {
        db.categoryDao().insert(CategoryEntity("cat-1", "Personal"))
        db.eventTypeDao().insert(EventTypeEntity("type-1", "Doc", "doc", "cat-1", createdAt = testInstant))

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = testInstant.toString(),
            categories = listOf(CategoryBackupDto("cat-1", "Personal")),
            eventTypes = listOf(EventTypeBackupDto("type-1", "Doc", "doc", "cat-1", createdAt = testInstant.toString())),
            events = listOf(
                EventBackupDto(
                    id = "evt-missing-att",
                    title = "File Missing On Disk",
                    eventTypeId = "type-1",
                    categoryId = "cat-1",
                    temporal = TemporalBackupDto("DATE_ONLY", "2026-10-25"),
                    attachments = listOf(
                        AttachmentBackupDto(
                            id = "att-ghost",
                            fileName = "ghost.png",
                            mimeType = "image/png",
                            fileSizeBytes = 1024L,
                            createdAt = testInstant.toString()
                        )
                    ),
                    createdAt = testInstant.toString(),
                    updatedAt = testInstant.toString()
                )
            )
        )
        repository.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL).getOrThrow()

        val outStream = ByteArrayOutputStream()
        val exportResult = repository.exportBackupArchive(outStream)
        assertTrue(exportResult.isSuccess)
        // totalAttachments bundled should be 0 because physical file was missing, but metadata export succeeded
        assertEquals(0, exportResult.getOrThrow().totalAttachments)
    }

    @Test
    fun importBackupArchive_corruptAttachmentEntry_doesNotAbortDatabaseRestore() = runTest {
        val outStream = ByteArrayOutputStream()
        ZipOutputStream(outStream).use { zipOut ->
            // Write valid backup.json
            zipOut.putNextEntry(ZipEntry(BackupConstants.BACKUP_JSON_ENTRY_NAME))
            val json = """
                {
                    "backupVersion": 1,
                    "exportedAt": "2026-10-20T10:00:00Z",
                    "categories": [
                        { "id": "cat-corrupt-test", "name": "Survives Corrupt Attachment", "sortOrder": 0 }
                    ]
                }
            """.trimIndent()
            zipOut.write(json.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // Write an attachment entry with garbage
            zipOut.putNextEntry(ZipEntry("${BackupConstants.ATTACHMENTS_DIR}/corrupt.bin"))
            zipOut.write("Corrupt file content".toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        val importResult = repository.importBackupArchive(ByteArrayInputStream(outStream.toByteArray()), RestoreStrategy.REPLACE_ALL)
        assertTrue(importResult.isSuccess)

        // Database entity was restored successfully despite any attachment issues
        val cat = db.categoryDao().getById("cat-corrupt-test")
        assertNotNull(cat)
        assertEquals("Survives Corrupt Attachment", cat?.name)
    }

    @Test
    fun exportAndRestore_preservesMultipleGroupsTagsDependenciesRelationshipsAndExceptions() = runTest {
        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = testInstant.toString(),
            categories = listOf(
                CategoryBackupDto("cat-1", "Work"),
                CategoryBackupDto("cat-2", "Personal")
            ),
            eventTypes = listOf(
                EventTypeBackupDto("type-1", "Sprint", "sprint", "cat-1", createdAt = testInstant.toString()),
                EventTypeBackupDto("type-2", "Review", "review", "cat-2", createdAt = testInstant.toString())
            ),
            groups = listOf(
                GroupBackupDto("grp-alpha", "Team Alpha", createdAt = testInstant.toString(), updatedAt = testInstant.toString()),
                GroupBackupDto("grp-beta", "Team Beta", createdAt = testInstant.toString(), updatedAt = testInstant.toString())
            ),
            tags = listOf(
                TagBackupDto("tag-backend", "backend", "backend"),
                TagBackupDto("tag-q4", "q4", "q4")
            ),
            events = listOf(
                EventBackupDto(
                    id = "evt-primary",
                    title = "Primary Event",
                    eventTypeId = "type-1",
                    categoryId = "cat-1",
                    temporal = TemporalBackupDto(
                        type = "DATE_TIME_RANGE",
                        startDate = "2026-10-25",
                        startTime = "09:00:00",
                        endDate = "2026-10-27",
                        endTime = "18:00:00",
                        zoneId = "UTC"
                    ),
                    groupIds = listOf("grp-alpha", "grp-beta"),
                    tagIds = listOf("tag-backend", "tag-q4"),
                    notificationSchedules = listOf(
                        NotificationScheduleBackupDto("sched-1", offsetSeconds = 1800, triggerType = "BEFORE_START"),
                        NotificationScheduleBackupDto("sched-2", offsetSeconds = 300, triggerType = "BEFORE_START")
                    ),
                    checklist = ChecklistBackupDto(
                        id = "chk-primary",
                        title = "Tasks",
                        items = listOf(
                            ChecklistItemBackupDto("item-1", "Item done", isCompleted = true, sortOrder = 0),
                            ChecklistItemBackupDto("item-2", "Item pending", isCompleted = false, sortOrder = 1)
                        )
                    ),
                    createdAt = testInstant.toString(),
                    updatedAt = testInstant.toString()
                ),
                EventBackupDto(
                    id = "evt-secondary",
                    title = "Secondary Event",
                    eventTypeId = "type-2",
                    categoryId = "cat-2",
                    temporal = TemporalBackupDto("DATE_ONLY", "2026-10-28"),
                    createdAt = testInstant.toString(),
                    updatedAt = testInstant.toString()
                )
            ),
            dependencies = listOf(
                EventDependencyBackupDto(sourceEventId = "evt-primary", targetEventId = "evt-secondary", type = "PREREQUISITE")
            ),
            relationships = listOf(
                EventRelationshipBackupDto(sourceEventId = "evt-primary", targetEventId = "evt-secondary", type = "RELATED_TO")
            ),
            occurrenceExceptions = listOf(
                OccurrenceExceptionBackupDto(
                    id = "exc-1",
                    eventId = "evt-primary",
                    originalStartDateTime = "2026-10-25T09:00:00",
                    originalZoneId = "UTC",
                    status = "RESCHEDULED",
                    rescheduledStartDateTime = "2026-10-25T11:00:00"
                )
            )
        )

        // Restore payload
        val restoreResult = repository.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isSuccess)

        // Verify primary event details
        val primary = db.eventDao().getEventWithDetailsById("evt-primary")
        assertNotNull(primary)
        assertEquals(2, primary?.groups?.size)
        assertEquals(setOf("grp-alpha", "grp-beta"), primary?.groups?.map { it.id }?.toSet())

        assertEquals(2, primary?.tags?.size)
        assertEquals(setOf("tag-backend", "tag-q4"), primary?.tags?.map { it.id }?.toSet())

        assertEquals(2, primary?.notificationSchedules?.size)

        val items = primary?.checklistWithItems?.items
        assertEquals(2, items?.size)
        assertTrue(items?.first { it.id == "item-1" }?.isCompleted == true)
        assertTrue(items?.first { it.id == "item-2" }?.isCompleted == false)

        // Verify dependencies
        val deps = db.eventDependencyDao().getAll()
        assertEquals(1, deps.size)
        assertEquals("evt-primary", deps.first().sourceEventId)
        assertEquals("evt-secondary", deps.first().targetEventId)

        // Verify relationships
        val rels = db.eventRelationshipDao().getAll()
        assertEquals(1, rels.size)
        assertEquals("evt-primary", rels.first().sourceEventId)
        assertEquals("evt-secondary", rels.first().targetEventId)

        // Verify occurrence exceptions
        val exceptions = db.occurrenceExceptionDao().getExceptionsForEvent("evt-primary")
        assertEquals(1, exceptions.size)
        assertEquals(LocalDateTime.parse("2026-10-25T11:00:00"), exceptions.first().rescheduledStartDateTime)
    }

    @Test
    fun backupAndRestore_withSettingsRepository_exportsAndRestoresPreferences() = runTest {
        val fakeSettingsRepo = object : com.example.offlinereminder.domain.repository.SettingsRepository {
            var settings = com.example.offlinereminder.domain.model.AppSettings(
                themeMode = com.example.offlinereminder.domain.model.ThemeMode.DARK,
                use24HourFormat = true,
                defaultEventDurationMinutes = 45
            )
            override fun getSettingsFlow(): kotlinx.coroutines.flow.Flow<com.example.offlinereminder.domain.model.AppSettings> =
                kotlinx.coroutines.flow.flowOf(settings)
            override suspend fun getSettings(): com.example.offlinereminder.domain.model.AppSettings = settings
            override suspend fun updateSettings(
                transform: (com.example.offlinereminder.domain.model.AppSettings) -> com.example.offlinereminder.domain.model.AppSettings
            ): com.example.offlinereminder.core.result.Result<com.example.offlinereminder.domain.model.AppSettings> {
                settings = transform(settings)
                return com.example.offlinereminder.core.result.Result.Success(settings)
            }
            override suspend fun resetSettings(): com.example.offlinereminder.core.result.Result<com.example.offlinereminder.domain.model.AppSettings> {
                settings = com.example.offlinereminder.domain.model.AppSettings()
                return com.example.offlinereminder.core.result.Result.Success(settings)
            }
        }

        val repoWithSettings = RoomBackupRepository(
            database = db,
            timeProvider = testTimeProvider,
            attachmentsDir = tempDir,
            settingsRepository = fakeSettingsRepo
        )

        // 1. Export payload
        val exportResult = repoWithSettings.createBackupPayload()
        assertTrue(exportResult.isSuccess)
        val payload = exportResult.getOrThrow()
        assertEquals("DARK", payload.settings.themeMode)
        assertEquals(true, payload.settings.use24HourFormat)
        assertEquals(45, payload.settings.defaultEventDurationMinutes)

        // 2. Modify settings
        fakeSettingsRepo.settings = com.example.offlinereminder.domain.model.AppSettings(
            themeMode = com.example.offlinereminder.domain.model.ThemeMode.LIGHT
        )

        // 3. Restore payload
        val restoreResult = repoWithSettings.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isSuccess)

        // 4. Verify settings restored
        assertEquals(com.example.offlinereminder.domain.model.ThemeMode.DARK, fakeSettingsRepo.settings.themeMode)
        assertEquals(true, fakeSettingsRepo.settings.use24HourFormat)
        assertEquals(45, fakeSettingsRepo.settings.defaultEventDurationMinutes)
    }

    @Test
    fun restoreBackupPayload_whenDatabaseFails_rollsBackSettingsToOriginalSnapshot() = runTest {
        val initialSettings = com.example.offlinereminder.domain.model.AppSettings(
            themeMode = com.example.offlinereminder.domain.model.ThemeMode.LIGHT,
            use24HourFormat = false,
            defaultEventDurationMinutes = 30
        )
        val fakeSettingsRepo = object : com.example.offlinereminder.domain.repository.SettingsRepository {
            var settings = initialSettings
            override fun getSettingsFlow(): kotlinx.coroutines.flow.Flow<com.example.offlinereminder.domain.model.AppSettings> =
                kotlinx.coroutines.flow.flowOf(settings)
            override suspend fun getSettings(): com.example.offlinereminder.domain.model.AppSettings = settings
            override suspend fun updateSettings(
                transform: (com.example.offlinereminder.domain.model.AppSettings) -> com.example.offlinereminder.domain.model.AppSettings
            ): com.example.offlinereminder.core.result.Result<com.example.offlinereminder.domain.model.AppSettings> {
                settings = transform(settings)
                return com.example.offlinereminder.core.result.Result.Success(settings)
            }
            override suspend fun resetSettings(): com.example.offlinereminder.core.result.Result<com.example.offlinereminder.domain.model.AppSettings> {
                settings = com.example.offlinereminder.domain.model.AppSettings()
                return com.example.offlinereminder.core.result.Result.Success(settings)
            }
        }

        val repoWithSettings = RoomBackupRepository(
            database = db,
            timeProvider = testTimeProvider,
            attachmentsDir = tempDir,
            settingsRepository = fakeSettingsRepo
        )

        // Construct a payload where an event references non-existent foreign keys.
        // BackupValidator allows this when categories/types are empty (assuming existing taxonomy),
        // but REPLACE_ALL wipes taxonomy first, guaranteeing a SQLite foreign key constraint failure.
        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = "2026-10-20T10:00:00Z",
            events = listOf(
                EventBackupDto(
                    id = "evt-foreign-key-fail",
                    title = "Test FK Failure",
                    categoryId = "missing-cat-id",
                    eventTypeId = "missing-type-id",
                    temporal = TemporalBackupDto(type = "DATE_ONLY", startDate = "2026-10-20"),
                    createdAt = "2026-10-20T10:00:00Z",
                    updatedAt = "2026-10-20T10:00:00Z"
                )
            ),
            settings = com.example.offlinereminder.domain.backup.SettingsBackupDto(
                themeMode = "DARK",
                use24HourFormat = true,
                defaultEventDurationMinutes = 90
            )
        )

        val restoreResult = repoWithSettings.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isFailure)

        // Verify settings were rolled back to original snapshot
        assertEquals(com.example.offlinereminder.domain.model.ThemeMode.LIGHT, fakeSettingsRepo.settings.themeMode)
        assertEquals(false, fakeSettingsRepo.settings.use24HourFormat)
        assertEquals(30, fakeSettingsRepo.settings.defaultEventDurationMinutes)
    }

    @Test
    fun restoreBackupPayload_whenSettingsFails_abortsBeforeTouchingDatabase() = runTest {
        // Pre-populate database with an entity
        db.categoryDao().insert(CategoryEntity("cat-preserved", "Preserved Category", isCustom = true, sortOrder = 1))

        val fakeFailingSettingsRepo = object : com.example.offlinereminder.domain.repository.SettingsRepository {
            var settings = com.example.offlinereminder.domain.model.AppSettings()
            override fun getSettingsFlow(): kotlinx.coroutines.flow.Flow<com.example.offlinereminder.domain.model.AppSettings> =
                kotlinx.coroutines.flow.flowOf(settings)
            override suspend fun getSettings(): com.example.offlinereminder.domain.model.AppSettings = settings
            override suspend fun updateSettings(
                transform: (com.example.offlinereminder.domain.model.AppSettings) -> com.example.offlinereminder.domain.model.AppSettings
            ): com.example.offlinereminder.core.result.Result<com.example.offlinereminder.domain.model.AppSettings> {
                return com.example.offlinereminder.core.result.Result.Failure(IllegalStateException("DataStore disk I/O error"))
            }
            override suspend fun resetSettings(): com.example.offlinereminder.core.result.Result<com.example.offlinereminder.domain.model.AppSettings> {
                return com.example.offlinereminder.core.result.Result.Failure(IllegalStateException("DataStore disk I/O error"))
            }
        }

        val repoWithSettings = RoomBackupRepository(
            database = db,
            timeProvider = testTimeProvider,
            attachmentsDir = tempDir,
            settingsRepository = fakeFailingSettingsRepo
        )

        val payload = BackupPayload(
            backupVersion = 1,
            exportedAt = "2026-10-20T10:00:00Z",
            categories = listOf(
                CategoryBackupDto("cat-incoming", "Incoming Category", isCustom = true, sortOrder = 2)
            )
        )

        // Restore with REPLACE_ALL strategy (which would wipe cat-preserved if it executed)
        val restoreResult = repoWithSettings.restoreBackupPayload(payload, RestoreStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isFailure)

        // Verify the database was NEVER wiped or modified
        val existingCategory = db.categoryDao().getById("cat-preserved")
        assertNotNull(existingCategory)
        assertEquals("Preserved Category", existingCategory?.name)

        val incomingCategory = db.categoryDao().getById("cat-incoming")
        assertEquals(null, incomingCategory)
    }

    @Test
    fun restoreBackupPayload_savedLocations_roundTripAndStrategies() = runTest {
        // Pre-populate saved location in DB
        val localLoc = com.example.offlinereminder.data.local.entity.SavedLocationEntity(
            id = "loc-local-1",
            name = "Local Office",
            address = "100 Market St",
            building = "Bld 1",
            room = "Room 101",
            notes = "Local notes",
            isFavorite = true,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        db.savedLocationDao().insert(localLoc)

        // Incoming backup payload with loc-local-1 (updated address) and loc-incoming-2
        val incomingLoc1 = SavedLocationBackupDto(
            id = "loc-local-1",
            name = "Updated Office Name",
            address = "200 Mission St",
            building = "Bld 2",
            room = "Room 202",
            isFavorite = false,
            createdAt = testInstant.toString(),
            updatedAt = testInstant.toString()
        )
        val incomingLoc2 = SavedLocationBackupDto(
            id = "loc-incoming-2",
            name = "Coffee Corner",
            address = "15 3rd St",
            isFavorite = true,
            createdAt = testInstant.toString(),
            updatedAt = testInstant.toString()
        )

        val payload = BackupPayload(
            backupVersion = BackupConstants.CURRENT_BACKUP_VERSION,
            exportedAt = "2026-10-21T10:00:00Z",
            savedLocations = listOf(incomingLoc1, incomingLoc2)
        )

        // Test MERGE_KEEP_EXISTING: keep local name and address for loc-local-1
        val mergeKeepRes = repository.restoreBackupPayload(payload, RestoreStrategy.MERGE_KEEP_EXISTING)
        assertTrue(mergeKeepRes.isSuccess)
        val keepLoc1 = db.savedLocationDao().getById("loc-local-1")
        assertEquals("Local Office", keepLoc1?.name)
        assertEquals("100 Market St", keepLoc1?.address)
        val newLoc2 = db.savedLocationDao().getById("loc-incoming-2")
        assertNotNull(newLoc2)
        assertEquals("Coffee Corner", newLoc2?.name)

        // Test MERGE_OVERWRITE: overwrite loc-local-1 with incoming values
        val mergeOverwriteRes = repository.restoreBackupPayload(payload, RestoreStrategy.MERGE_OVERWRITE)
        assertTrue(mergeOverwriteRes.isSuccess)
        val overwriteLoc1 = db.savedLocationDao().getById("loc-local-1")
        assertEquals("Updated Office Name", overwriteLoc1?.name)
        assertEquals("200 Mission St", overwriteLoc1?.address)

        // Test REPLACE_ALL: clears existing and inserts payload
        val replacePayload = BackupPayload(
            backupVersion = BackupConstants.CURRENT_BACKUP_VERSION,
            exportedAt = "2026-10-21T10:00:00Z",
            savedLocations = listOf(incomingLoc2)
        )
        val replaceAllRes = repository.restoreBackupPayload(replacePayload, RestoreStrategy.REPLACE_ALL)
        assertTrue(replaceAllRes.isSuccess)
        val replacedLoc1 = db.savedLocationDao().getById("loc-local-1")
        assertEquals(null, replacedLoc1)
        val replacedLoc2 = db.savedLocationDao().getById("loc-incoming-2")
        assertNotNull(replacedLoc2)
        assertEquals("Coffee Corner", replacedLoc2?.name)
    }

    @Test
    fun restoreBackupPayload_olderBackupWithoutSavedLocations_succeeds() = runTest {
        // Pre-1.1 backup payload without savedLocations field
        val olderJson = """
            {
                "backupVersion": 1,
                "appVersion": "1.0.0",
                "exportedAt": "2026-10-20T10:00:00Z",
                "categories": [
                    { "id": "cat-older", "name": "Legacy Category", "sortOrder": 0 }
                ]
            }
        """.trimIndent()

        val inStream = ByteArrayInputStream(olderJson.toByteArray(Charsets.UTF_8))
        val res = repository.importBackupArchive(inStream, RestoreStrategy.MERGE_KEEP_EXISTING)
        assertTrue(res.isSuccess)

        val restoredCat = db.categoryDao().getById("cat-older")
        assertNotNull(restoredCat)
        assertEquals("Legacy Category", restoredCat?.name)
    }

    @Test
    fun backupPayload_doesNotContainBiometricCredentials() {
        val payload = BackupPayload(
            backupVersion = BackupConstants.CURRENT_BACKUP_VERSION,
            exportedAt = "2026-10-21T10:00:00Z"
        )
        val json = kotlinx.serialization.json.Json.encodeToString(BackupPayload.serializer(), payload)
        assertTrue(!json.contains("biometric", ignoreCase = true))
        assertTrue(!json.contains("password", ignoreCase = true))
        assertTrue(!json.contains("pin", ignoreCase = true))
        assertTrue(!json.contains("credential", ignoreCase = true))
    }
}
