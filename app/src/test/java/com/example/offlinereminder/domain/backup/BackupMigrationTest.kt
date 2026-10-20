package com.example.offlinereminder.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMigrationTest {

    private val migrator = BackupMigrator()
    private val validator = BackupValidator()

    @Test
    fun migrateToCurrent_v0Payload_migratesToV1WithDefaults() {
        val legacyV0Json = """
            {
                "backupVersion": 0,
                "exportedAt": "2026-10-20T10:00:00Z",
                "categories": [
                    {
                        "id": "cat-v0",
                        "name": "General"
                    }
                ],
                "eventTypes": [
                    {
                        "id": "type-v0",
                        "name": "Reminder",
                        "key": "reminder",
                        "categoryId": "cat-v0",
                        "createdAt": "2026-10-20T10:00:00Z"
                    }
                ],
                "events": [
                    {
                        "id": "evt-v0",
                        "title": "Legacy Reminder",
                        "eventTypeId": "type-v0",
                        "categoryId": "cat-v0",
                        "temporal": {
                            "type": "DATE_ONLY",
                            "startDate": "2026-10-25"
                        },
                        "createdAt": "2026-10-20T10:00:00Z",
                        "updatedAt": "2026-10-20T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()

        val migrated = migrator.migrateToCurrent(legacyV0Json)

        assertEquals(1, migrated.backupVersion)
        assertEquals("1.0", migrated.appVersion)
        assertEquals(1, migrated.categories.size)
        assertEquals(0, migrated.categories[0].sortOrder) // Default added
        assertEquals(1, migrated.events.size)
        assertEquals("ACTIVE", migrated.events[0].lifecycleStatus) // Default added
        assertFalse(migrated.events[0].isFavorite) // Default added

        // Ensure migrated payload passes validator
        val validationResult = validator.validate(migrated)
        assertTrue(validationResult.isSuccess)
    }

    @Test(expected = UnsupportedBackupVersionException::class)
    fun migrateToCurrent_unsupportedFutureVersion_throwsException() {
        val futureJson = """
            {
                "backupVersion": 42,
                "exportedAt": "2026-10-20T10:00:00Z"
            }
        """.trimIndent()

        migrator.migrateToCurrent(futureJson)
    }

    @Test
    fun migrateToCurrent_v1Payload_returnsEquivalentPayloadWithoutMutation() {
        val currentV1Json = """
            {
                "backupVersion": 1,
                "appVersion": "1.0",
                "exportedAt": "2026-10-20T10:00:00Z",
                "categories": [
                    {
                        "id": "cat-1",
                        "name": "Personal",
                        "sortOrder": 5
                    }
                ],
                "eventTypes": [
                    {
                        "id": "type-1",
                        "name": "Birthday",
                        "key": "birthday",
                        "categoryId": "cat-1",
                        "createdAt": "2026-10-20T10:00:00Z"
                    }
                ],
                "events": [
                    {
                        "id": "evt-1",
                        "title": "Party",
                        "eventTypeId": "type-1",
                        "categoryId": "cat-1",
                        "lifecycleStatus": "ACTIVE",
                        "isFavorite": true,
                        "temporal": {
                            "type": "DATE_ONLY",
                            "startDate": "2026-10-30"
                        },
                        "createdAt": "2026-10-20T10:00:00Z",
                        "updatedAt": "2026-10-20T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()

        val payload = migrator.migrateToCurrent(currentV1Json)
        assertEquals(1, payload.backupVersion)
        assertEquals(5, payload.categories[0].sortOrder)
        assertTrue(payload.events[0].isFavorite)
    }
}
