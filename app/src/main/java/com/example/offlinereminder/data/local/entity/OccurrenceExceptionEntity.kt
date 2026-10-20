package com.example.offlinereminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.offlinereminder.domain.model.OccurrenceStatus
import java.time.Instant
import java.time.LocalDateTime

@Entity(
    tableName = "occurrence_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["occurrence_id"], unique = true),
        Index(value = ["event_id", "original_start_date_time", "original_zone_id"], unique = true)
    ]
)
data class OccurrenceExceptionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "original_start_date_time")
    val originalStartDateTime: LocalDateTime,
    @ColumnInfo(name = "original_zone_id")
    val originalZoneId: String,
    @ColumnInfo(name = "occurrence_id")
    val occurrenceId: String,
    val status: OccurrenceStatus,
    @ColumnInfo(name = "rescheduled_start_date_time")
    val rescheduledStartDateTime: LocalDateTime? = null,
    @ColumnInfo(name = "rescheduled_end_date_time")
    val rescheduledEndDateTime: LocalDateTime? = null,
    @ColumnInfo(name = "rescheduled_zone_id")
    val rescheduledZoneId: String? = null,
    @ColumnInfo(name = "actual_execution_date_time")
    val actualExecutionDateTime: LocalDateTime? = null,
    @ColumnInfo(name = "completed_at")
    val completedAt: Instant? = null,
    @ColumnInfo(name = "title_override")
    val titleOverride: String? = null,
    @ColumnInfo(name = "notes_override")
    val notesOverride: String? = null
)
