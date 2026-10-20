package com.example.offlinereminder.domain.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Migration engine for versioned backup payloads.
 * Translates older supported schema versions into the current canonical [BackupPayload].
 */
class BackupMigrator(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }
) {

    /**
     * Inspects the raw backup JSON, verifies version compatibility, and sequentially migrates
     * any older supported formats to [BackupConstants.CURRENT_BACKUP_VERSION].
     */
    fun migrateToCurrent(rawJson: String): BackupPayload {
        val rootElement = json.parseToJsonElement(rawJson).jsonObject
        val version = rootElement["backupVersion"]?.jsonPrimitive?.intOrNull ?: 0

        if (version > BackupConstants.CURRENT_BACKUP_VERSION) {
            throw UnsupportedBackupVersionException(
                "Backup version $version is not supported by this application (max supported: ${BackupConstants.CURRENT_BACKUP_VERSION})."
            )
        }

        var currentTree = rootElement
        var currentVersion = version

        while (currentVersion < BackupConstants.CURRENT_BACKUP_VERSION) {
            currentTree = when (currentVersion) {
                0 -> migrateV0ToV1(currentTree)
                else -> throw IllegalStateException("No migration path from version $currentVersion")
            }
            currentVersion++
        }

        return json.decodeFromJsonElement(BackupPayload.serializer(), currentTree)
    }

    /**
     * Migration path from Version 0 to Version 1.
     * Enforces required fields introduced in Version 1:
     * - backupVersion set to 1
     * - Default lifecycleStatus = "ACTIVE" for events
     * - Default isFavorite = false for events
     * - Default sortOrder = 0 for categories
     */
    private fun migrateV0ToV1(v0: JsonObject): JsonObject {
        val updatedMap = v0.toMutableMap()
        updatedMap["backupVersion"] = JsonPrimitive(1)

        // Ensure appVersion exists
        if (!updatedMap.containsKey("appVersion")) {
            updatedMap["appVersion"] = JsonPrimitive(BackupConstants.DEFAULT_APP_VERSION)
        }

        // Migrate events
        val events = v0["events"] as? JsonArray
        if (events != null) {
            val migratedEvents = events.map { eventEl ->
                val eventObj = eventEl.jsonObject.toMutableMap()
                if (!eventObj.containsKey("lifecycleStatus")) {
                    eventObj["lifecycleStatus"] = JsonPrimitive("ACTIVE")
                }
                if (!eventObj.containsKey("isFavorite")) {
                    eventObj["isFavorite"] = JsonPrimitive(false)
                }
                JsonObject(eventObj)
            }
            updatedMap["events"] = JsonArray(migratedEvents)
        }

        // Migrate categories
        val categories = v0["categories"] as? JsonArray
        if (categories != null) {
            val migratedCategories = categories.map { catEl ->
                val catObj = catEl.jsonObject.toMutableMap()
                if (!catObj.containsKey("sortOrder")) {
                    catObj["sortOrder"] = JsonPrimitive(0)
                }
                JsonObject(catObj)
            }
            updatedMap["categories"] = JsonArray(migratedCategories)
        }

        return JsonObject(updatedMap)
    }
}
