package com.example.offlinereminder.data.repository

import androidx.room.withTransaction
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.result.getOrThrow
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.local.AppDatabase
import com.example.offlinereminder.data.local.mapper.toBackupDto
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.data.local.mapper.toGroupCrossRefs
import com.example.offlinereminder.data.local.mapper.toTagCrossRefs
import com.example.offlinereminder.domain.backup.BackupConstants
import com.example.offlinereminder.domain.backup.BackupExportResult
import com.example.offlinereminder.domain.backup.BackupMigrator
import com.example.offlinereminder.domain.backup.BackupPayload
import com.example.offlinereminder.domain.backup.BackupRepository
import com.example.offlinereminder.domain.backup.BackupValidator
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.domain.backup.SettingsBackupDto
import com.example.offlinereminder.domain.repository.SettingsRepository
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Robust, Room-transactional implementation of [BackupRepository].
 * Handles format detection (ZIP archive vs plain JSON), schema validation, migration,
 * portable attachment bundling, and deterministic atomic database restoration.
 */
class RoomBackupRepository(
    private val database: AppDatabase,
    private val timeProvider: TimeProvider,
    private val attachmentsDir: File? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val validator: BackupValidator = BackupValidator(),
    private val migrator: BackupMigrator = BackupMigrator()
) : BackupRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val eventDao = database.eventDao()
    private val categoryDao = database.categoryDao()
    private val eventTypeDao = database.eventTypeDao()
    private val eventGroupDao = database.eventGroupDao()
    private val tagDao = database.tagDao()
    private val checklistDao = database.checklistDao()
    private val notificationScheduleDao = database.notificationScheduleDao()
    private val occurrenceExceptionDao = database.occurrenceExceptionDao()
    private val attachmentDao = database.attachmentDao()
    private val eventDependencyDao = database.eventDependencyDao()
    private val eventRelationshipDao = database.eventRelationshipDao()
    private val notificationInstanceDao = database.notificationInstanceDao()
    private val savedLocationDao = database.savedLocationDao()

    override suspend fun createBackupPayload(): Result<BackupPayload> = Result.runCatching {
        val categories = categoryDao.getAll().map { it.toBackupDto() }
        val eventTypes = eventTypeDao.getAll().map { it.toBackupDto() }
        val groups = eventGroupDao.getAll().map { it.toBackupDto() }
        val tags = tagDao.getAll().map { it.toBackupDto() }
        val eventsWithDetails = eventDao.getAllWithDetails()
        val events = eventsWithDetails.map { it.toDomain().toBackupDto() }
        val exceptions = occurrenceExceptionDao.getAll().map { it.toBackupDto() }
        val dependencies = eventDependencyDao.getAll().map { it.toBackupDto() }
        val relationships = eventRelationshipDao.getAll().map { it.toBackupDto() }
        val savedLocations = savedLocationDao.getAll().map { it.toDomain().toBackupDto() }
        val settings = settingsRepository?.getSettings()?.toBackupDto() ?: SettingsBackupDto()

        BackupPayload(
            backupVersion = BackupConstants.CURRENT_BACKUP_VERSION,
            appVersion = BackupConstants.DEFAULT_APP_VERSION,
            exportedAt = timeProvider.nowInstant().toString(),
            categories = categories,
            eventTypes = eventTypes,
            groups = groups,
            tags = tags,
            events = events,
            occurrenceExceptions = exceptions,
            dependencies = dependencies,
            relationships = relationships,
            savedLocations = savedLocations,
            settings = settings
        )
    }

    override suspend fun restoreBackupPayload(
        payload: BackupPayload,
        strategy: RestoreStrategy
    ): Result<RestoreSummary> = Result.runCatching {
        // Validate before touching database or persistent settings
        validator.validate(payload).getOrThrow()

        // Capture settings snapshot for atomicity rollback
        val originalSettings = settingsRepository?.getSettings()

        // 1. Update settings first. If this fails, the database is never touched.
        if (settingsRepository != null) {
            settingsRepository.updateSettings { payload.settings.toDomain() }.getOrThrow()
        }

        // 2. Perform Room restoration within transaction.
        val summary = try {
            database.withTransaction {
                var restoredEvents = 0
            var restoredCats = 0
            var restoredGroups = 0
            var restoredTypes = 0
            var restoredTags = 0
            var restoredExceptions = 0
            var restoredDeps = 0
            var restoredRels = 0
            var restoredAtts = 0

            val domainEvents = payload.events.map { it.toDomain() }

            when (strategy) {
                RestoreStrategy.REPLACE_ALL -> {
                    // 1. Wipe dependent entities first to maintain referential cleanliness
                    eventDependencyDao.deleteAll()
                    eventRelationshipDao.deleteAll()
                    occurrenceExceptionDao.deleteAll()
                    notificationInstanceDao.deleteAll()
                    notificationScheduleDao.deleteAll()
                    checklistDao.deleteAllItems()
                    checklistDao.deleteAllChecklists()
                    attachmentDao.deleteAll()
                    eventGroupDao.deleteAllCrossRefs()
                    tagDao.deleteAllCrossRefs()
                    eventDao.deleteAll()
                    eventGroupDao.deleteAll()
                    tagDao.deleteAll()
                    eventTypeDao.deleteAll()
                    categoryDao.deleteAll()

                    // 2. Insert master taxonomy in order
                    categoryDao.insertAll(payload.categories.map { it.toEntity() })
                    restoredCats = payload.categories.size

                    eventTypeDao.insertAll(payload.eventTypes.map { it.toEntity() })
                    restoredTypes = payload.eventTypes.size

                    eventGroupDao.insertAll(payload.groups.map { it.toEntity() })
                    restoredGroups = payload.groups.size

                    tagDao.insertAll(payload.tags.map { it.toEntity() })
                    restoredTags = payload.tags.size

                    // 3. Insert events and embedded detail tables
                    eventDao.insertAll(domainEvents.map { it.toEntity() })
                    restoredEvents = domainEvents.size

                    for (event in domainEvents) {
                        if (event.groupIds.isNotEmpty()) {
                            eventGroupDao.insertCrossRefs(event.toGroupCrossRefs())
                        }
                        if (event.tagIds.isNotEmpty()) {
                            tagDao.insertCrossRefs(event.toTagCrossRefs())
                        }
                        if (event.notificationSchedules.isNotEmpty()) {
                            notificationScheduleDao.insertAll(event.notificationSchedules.map { it.toEntity(event.id) })
                        }
                        event.checklist?.let { checklist ->
                            checklistDao.insertChecklist(checklist.toEntity(event.id))
                            if (checklist.items.isNotEmpty()) {
                                checklistDao.insertItems(checklist.items.map { it.toEntity(checklist.id) })
                            }
                        }
                        if (event.attachments.isNotEmpty()) {
                            attachmentDao.insertAll(event.attachments.map { it.toEntity(event.id) })
                            restoredAtts += event.attachments.size
                        }
                    }

                    // 4. Insert occurrence exceptions, dependencies, relationships
                    if (payload.occurrenceExceptions.isNotEmpty()) {
                        occurrenceExceptionDao.insertAll(payload.occurrenceExceptions.map { it.toEntity() })
                        restoredExceptions = payload.occurrenceExceptions.size
                    }

                    if (payload.dependencies.isNotEmpty()) {
                        eventDependencyDao.insertAll(payload.dependencies.map { it.toEntity() })
                        restoredDeps = payload.dependencies.size
                    }

                    if (payload.relationships.isNotEmpty()) {
                        eventRelationshipDao.insertAll(payload.relationships.map { it.toEntity() })
                        restoredRels = payload.relationships.size
                    }

                    savedLocationDao.deleteAll()
                    if (payload.savedLocations.isNotEmpty()) {
                        savedLocationDao.insertAll(payload.savedLocations.map { it.toDomain().toEntity() })
                    }
                }

                RestoreStrategy.MERGE_OVERWRITE -> {
                    // Categories & EventTypes & Groups & Tags: upsert
                    categoryDao.upsertAll(payload.categories.map { it.toEntity() })
                    restoredCats = payload.categories.size

                    eventTypeDao.upsertAll(payload.eventTypes.map { it.toEntity() })
                    restoredTypes = payload.eventTypes.size

                    eventGroupDao.upsertAll(payload.groups.map { it.toEntity() })
                    restoredGroups = payload.groups.size

                    tagDao.upsertAll(payload.tags.map { it.toEntity() })
                    restoredTags = payload.tags.size

                    // Events: upsert each event and re-sync details
                    for (event in domainEvents) {
                        eventDao.upsert(event.toEntity())
                        restoredEvents++

                        eventGroupDao.removeAllGroupsFromEvent(event.id)
                        if (event.groupIds.isNotEmpty()) {
                            eventGroupDao.insertCrossRefs(event.toGroupCrossRefs())
                        }

                        tagDao.removeAllTagsFromEvent(event.id)
                        if (event.tagIds.isNotEmpty()) {
                            tagDao.insertCrossRefs(event.toTagCrossRefs())
                        }

                        notificationScheduleDao.deleteByEventId(event.id)
                        if (event.notificationSchedules.isNotEmpty()) {
                            notificationScheduleDao.insertAll(event.notificationSchedules.map { it.toEntity(event.id) })
                        }

                        checklistDao.deleteChecklistByEventId(event.id)
                        event.checklist?.let { checklist ->
                            checklistDao.insertChecklist(checklist.toEntity(event.id))
                            if (checklist.items.isNotEmpty()) {
                                checklistDao.insertItems(checklist.items.map { it.toEntity(checklist.id) })
                            }
                        }

                        attachmentDao.deleteByEventId(event.id)
                        if (event.attachments.isNotEmpty()) {
                            attachmentDao.insertAll(event.attachments.map { it.toEntity(event.id) })
                            restoredAtts += event.attachments.size
                        }
                    }

                    if (payload.occurrenceExceptions.isNotEmpty()) {
                        occurrenceExceptionDao.insertAll(payload.occurrenceExceptions.map { it.toEntity() })
                        restoredExceptions = payload.occurrenceExceptions.size
                    }

                    if (payload.dependencies.isNotEmpty()) {
                        eventDependencyDao.insertAll(payload.dependencies.map { it.toEntity() })
                        restoredDeps = payload.dependencies.size
                    }

                    if (payload.relationships.isNotEmpty()) {
                        eventRelationshipDao.insertAll(payload.relationships.map { it.toEntity() })
                        restoredRels = payload.relationships.size
                    }

                    if (payload.savedLocations.isNotEmpty()) {
                        savedLocationDao.insertAll(payload.savedLocations.map { it.toDomain().toEntity() })
                    }
                }

                RestoreStrategy.MERGE_KEEP_EXISTING -> {
                    val existingCatIds = categoryDao.getAll().map { it.id }.toSet()
                    val newCats = payload.categories.filter { it.id !in existingCatIds }
                    categoryDao.insertAll(newCats.map { it.toEntity() })
                    restoredCats = newCats.size

                    val existingTypeIds = eventTypeDao.getAll().map { it.id }.toSet()
                    val newTypes = payload.eventTypes.filter { it.id !in existingTypeIds }
                    eventTypeDao.insertAll(newTypes.map { it.toEntity() })
                    restoredTypes = newTypes.size

                    val existingGroupIds = eventGroupDao.getAll().map { it.id }.toSet()
                    val newGroups = payload.groups.filter { it.id !in existingGroupIds }
                    eventGroupDao.insertAll(newGroups.map { it.toEntity() })
                    restoredGroups = newGroups.size

                    val existingTagIds = tagDao.getAll().map { it.id }.toSet()
                    val newTags = payload.tags.filter { it.id !in existingTagIds }
                    tagDao.insertAll(newTags.map { it.toEntity() })
                    restoredTags = newTags.size

                    val existingEventIds = eventDao.getAllWithDetails().map { it.event.id }.toSet()
                    val newEvents = domainEvents.filter { it.id !in existingEventIds }

                    for (event in newEvents) {
                        eventDao.insert(event.toEntity())
                        restoredEvents++

                        if (event.groupIds.isNotEmpty()) {
                            eventGroupDao.insertCrossRefs(event.toGroupCrossRefs())
                        }
                        if (event.tagIds.isNotEmpty()) {
                            tagDao.insertCrossRefs(event.toTagCrossRefs())
                        }
                        if (event.notificationSchedules.isNotEmpty()) {
                            notificationScheduleDao.insertAll(event.notificationSchedules.map { it.toEntity(event.id) })
                        }
                        event.checklist?.let { checklist ->
                            checklistDao.insertChecklist(checklist.toEntity(event.id))
                            if (checklist.items.isNotEmpty()) {
                                checklistDao.insertItems(checklist.items.map { it.toEntity(checklist.id) })
                            }
                        }
                        if (event.attachments.isNotEmpty()) {
                            attachmentDao.insertAll(event.attachments.map { it.toEntity(event.id) })
                            restoredAtts += event.attachments.size
                        }
                    }

                    val existingExcIds = occurrenceExceptionDao.getAll().map { it.id }.toSet()
                    val newExceptions = payload.occurrenceExceptions.filter { it.id !in existingExcIds }
                    if (newExceptions.isNotEmpty()) {
                        occurrenceExceptionDao.insertAll(newExceptions.map { it.toEntity() })
                        restoredExceptions = newExceptions.size
                    }

                    if (payload.dependencies.isNotEmpty()) {
                        eventDependencyDao.insertAll(payload.dependencies.map { it.toEntity() })
                        restoredDeps = payload.dependencies.size
                    }

                    if (payload.relationships.isNotEmpty()) {
                        eventRelationshipDao.insertAll(payload.relationships.map { it.toEntity() })
                        restoredRels = payload.relationships.size
                    }

                    val existingLocIds = savedLocationDao.getAll().map { it.id }.toSet()
                    val newLocations = payload.savedLocations.filter { it.id !in existingLocIds }
                    if (newLocations.isNotEmpty()) {
                        savedLocationDao.insertAll(newLocations.map { it.toDomain().toEntity() })
                    }
                }
            }

            RestoreSummary(
                eventsRestored = restoredEvents,
                categoriesRestored = restoredCats,
                groupsRestored = restoredGroups,
                eventTypesRestored = restoredTypes,
                tagsRestored = restoredTags,
                exceptionsRestored = restoredExceptions,
                dependenciesRestored = restoredDeps,
                relationshipsRestored = restoredRels,
                attachmentsRestored = restoredAtts
            )
        }
        } catch (e: Throwable) {
            // Room transaction is automatically rolled back by Room.
            // Compensate settings back to original snapshot so settings and database remain consistent.
            if (settingsRepository != null && originalSettings != null) {
                runCatching {
                    settingsRepository?.updateSettings { originalSettings }
                }
            }
            throw e
        }

        summary
    }

    override suspend fun exportBackupArchive(
        outputStream: OutputStream
    ): Result<BackupExportResult> = Result.runCatching {
        val payload = createBackupPayload().getOrThrow()
        val jsonString = json.encodeToString(BackupPayload.serializer(), payload)
        var totalAttachments = 0
        var bytesWritten = 0L

        ZipOutputStream(outputStream).use { zipOut ->
            // 1. Write backup.json
            val jsonEntry = ZipEntry(BackupConstants.BACKUP_JSON_ENTRY_NAME)
            zipOut.putNextEntry(jsonEntry)
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            zipOut.write(jsonBytes)
            zipOut.closeEntry()
            bytesWritten += jsonBytes.size

            // 2. Package attachments if files exist
            for (event in payload.events) {
                for (att in event.attachments) {
                    val file = findAttachmentFile(att.id, att.fileName, att.relativePath)
                    if (file != null && file.exists() && file.isFile) {
                        val ext = if (att.fileName.contains(".")) att.fileName.substringAfterLast(".") else ""
                        val stableName = if (ext.isNotEmpty()) "${att.id}.$ext" else att.id
                        val entryName = "${BackupConstants.ATTACHMENTS_DIR}/$stableName"
                        zipOut.putNextEntry(ZipEntry(entryName))
                        val fileBytes = file.readBytes()
                        zipOut.write(fileBytes)
                        zipOut.closeEntry()
                        totalAttachments++
                        bytesWritten += fileBytes.size
                    }
                }
            }
            zipOut.finish()
            zipOut.flush()
        }

        BackupExportResult(
            backupVersion = payload.backupVersion,
            totalEvents = payload.events.size,
            totalCategories = payload.categories.size,
            totalGroups = payload.groups.size,
            totalAttachments = totalAttachments,
            exportedAt = payload.exportedAt,
            archiveSizeBytes = bytesWritten
        )
    }

    override suspend fun importBackupArchive(
        inputStream: InputStream,
        strategy: RestoreStrategy
    ): Result<RestoreSummary> = Result.runCatching {
        val inputBytes = inputStream.readBytes()
        if (inputBytes.isEmpty()) {
            throw IllegalArgumentException("Empty backup input provided.")
        }

        // Format detection: ZIP magic bytes vs JSON
        val isZip = inputBytes.size >= 4 &&
                inputBytes[0] == 0x50.toByte() &&
                inputBytes[1] == 0x4B.toByte() &&
                inputBytes[2] == 0x03.toByte() &&
                inputBytes[3] == 0x04.toByte()

        var rawJson: String? = null

        if (isZip) {
            ZipInputStream(ByteArrayInputStream(inputBytes)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == BackupConstants.BACKUP_JSON_ENTRY_NAME) {
                        rawJson = zipIn.bufferedReader(Charsets.UTF_8).readText()
                    } else if (entry.name.startsWith("${BackupConstants.ATTACHMENTS_DIR}/")) {
                        // Extract attachment safely
                        val safeFileName = File(entry.name).name
                        if (attachmentsDir != null && safeFileName.isNotBlank()) {
                            try {
                                if (!attachmentsDir.exists()) attachmentsDir.mkdirs()
                                val destFile = File(attachmentsDir, safeFileName)
                                destFile.outputStream().use { zipIn.copyTo(it) }
                            } catch (_: Exception) {
                                // Gracefully continue if an individual file entry is corrupted
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } else {
            // Treat as raw JSON text
            rawJson = inputBytes.toString(Charsets.UTF_8)
        }

        if (rawJson.isNullOrBlank()) {
            throw IllegalArgumentException("No valid ${BackupConstants.BACKUP_JSON_ENTRY_NAME} found in archive.")
        }

        // Migrate older versions if applicable
        val payload = migrator.migrateToCurrent(rawJson!!)

        // Validate payload before database mutation
        validator.validate(payload).getOrThrow()

        // Transactional restore
        restoreBackupPayload(payload, strategy).getOrThrow()
    }

    private fun findAttachmentFile(id: String, fileName: String, relativePath: String?): File? {
        if (attachmentsDir == null) return null
        val ext = if (fileName.contains(".")) fileName.substringAfterLast(".") else ""
        val stableName = if (ext.isNotEmpty()) "$id.$ext" else id

        val candidates = listOfNotNull(
            File(attachmentsDir, stableName),
            File(attachmentsDir, "${id}_$fileName"),
            File(attachmentsDir, fileName),
            relativePath?.let { File(attachmentsDir, File(it).name) },
            relativePath?.let { File(it) }
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }
}
