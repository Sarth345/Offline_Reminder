package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.offlinereminder.data.local.entity.ChecklistEntity
import com.example.offlinereminder.data.local.entity.ChecklistItemEntity
import com.example.offlinereminder.data.local.relation.ChecklistWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: ChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklists(checklists: List<ChecklistEntity>)

    @Update
    suspend fun updateChecklist(checklist: ChecklistEntity)

    @Delete
    suspend fun deleteChecklist(checklist: ChecklistEntity)

    @Query("DELETE FROM checklists WHERE event_id = :eventId")
    suspend fun deleteChecklistByEventId(eventId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ChecklistItemEntity>)

    @Update
    suspend fun updateItem(item: ChecklistItemEntity)

    @Delete
    suspend fun deleteItem(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM checklist_items WHERE checklist_id = :checklistId")
    suspend fun deleteItemsForChecklist(checklistId: String)

    @Query("UPDATE checklist_items SET is_completed = :isCompleted WHERE id = :itemId")
    suspend fun setItemCompleted(itemId: String, isCompleted: Boolean)

    @Transaction
    @Query("SELECT * FROM checklists WHERE event_id = :eventId LIMIT 1")
    suspend fun getChecklistWithItemsByEventId(eventId: String): ChecklistWithItems?

    @Transaction
    @Query("SELECT * FROM checklists WHERE event_id = :eventId LIMIT 1")
    fun getChecklistWithItemsByEventIdFlow(eventId: String): Flow<ChecklistWithItems?>

    @Query("SELECT * FROM checklist_items WHERE checklist_id = :checklistId ORDER BY sort_order ASC")
    fun getItemsForChecklistFlow(checklistId: String): Flow<List<ChecklistItemEntity>>

    @Query("DELETE FROM checklists")
    suspend fun deleteAllChecklists()

    @Query("DELETE FROM checklist_items")
    suspend fun deleteAllItems()
}
