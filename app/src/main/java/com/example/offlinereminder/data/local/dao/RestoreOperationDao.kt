package com.example.offlinereminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinereminder.data.local.entity.RestoreOperationEntity

@Dao
interface RestoreOperationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: RestoreOperationEntity)

    @Query("SELECT * FROM restore_operations WHERE operation_id = :operationId")
    suspend fun getById(operationId: String): RestoreOperationEntity?

    @Query("SELECT * FROM restore_operations ORDER BY committed_at DESC")
    suspend fun getAll(): List<RestoreOperationEntity>

    @Query("DELETE FROM restore_operations WHERE operation_id = :operationId")
    suspend fun deleteById(operationId: String)

    @Query("DELETE FROM restore_operations")
    suspend fun deleteAll()
}
