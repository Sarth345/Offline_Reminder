package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.CategoryDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getCategory(id: String): Category? =
        categoryDao.getById(id)?.toDomain()

    override suspend fun saveCategory(category: Category): Result<Unit> =
        Result.runCatching {
            categoryDao.insert(category.toEntity())
        }

    override suspend fun deleteCategory(id: String): Result<Unit> =
        Result.runCatching {
            categoryDao.deleteById(id)
        }
}
