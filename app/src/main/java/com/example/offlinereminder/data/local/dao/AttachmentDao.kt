package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM event_attachments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM event_attachments WHERE event_id = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("SELECT * FROM event_attachments WHERE id = :id")
    suspend fun getById(id: String): AttachmentEntity?

    @Query("SELECT * FROM event_attachments WHERE event_id = :eventId ORDER BY created_at ASC")
    fun getAttachmentsForEventFlow(eventId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM event_attachments WHERE event_id = :eventId ORDER BY created_at ASC")
    suspend fun getAttachmentsForEvent(eventId: String): List<AttachmentEntity>
}
