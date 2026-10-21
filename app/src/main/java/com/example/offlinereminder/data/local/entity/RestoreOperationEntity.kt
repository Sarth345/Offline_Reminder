package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Internal crash-recovery metadata ledger.
 * Tracks restore operation execution and atomic SQLite commit status.
 * NOT part of public backup.json and NOT wiped by public backup restore operations.
 */
@Entity(tableName = "restore_operations")
data class RestoreOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    val status: String = "COMMITTED",
    @ColumnInfo(name = "backup_version")
    val backupVersion: Int,
    @ColumnInfo(name = "committed_at")
    val committedAt: Instant
)
