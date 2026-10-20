package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin repository interface for Category persistence and observation.
 */
interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun getCategory(id: String): Category?
    suspend fun saveCategory(category: Category): Result<Unit>
    suspend fun deleteCategory(id: String): Result<Unit>
}
