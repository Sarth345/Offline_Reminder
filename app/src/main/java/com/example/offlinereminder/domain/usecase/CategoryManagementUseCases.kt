package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.EventTemplateRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Use case to create a new category with duplicate-name validation.
 */
class CreateCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        name: String,
        color: Int? = null,
        icon: String = "category",
        sortOrder: Int = 0
    ): Result<Category> = Result.runCatching {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Category name cannot be blank")
        }

        val existing = categoryRepository.getAllCategories().first()
        if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
            throw IllegalArgumentException("A category with name '$trimmed' already exists")
        }

        val category = Category(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            color = color,
            icon = icon,
            isCustom = true,
            sortOrder = sortOrder
        )
        categoryRepository.saveCategory(category)
        category
    }
}

/**
 * Use case to update an existing category with duplicate-name validation.
 */
class UpdateCategoryUseCase(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(category: Category): Result<Unit> = Result.runCatching {
        val trimmed = category.name.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Category name cannot be blank")
        }

        val existing = categoryRepository.getAllCategories().first()
        if (existing.any { it.id != category.id && it.name.equals(trimmed, ignoreCase = true) }) {
            throw IllegalArgumentException("Another category with name '$trimmed' already exists")
        }

        categoryRepository.saveCategory(category.copy(name = trimmed))
    }
}

/**
 * Use case to safely delete a category, verifying referential safety before deletion.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val eventRepository: EventRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val eventTemplateRepository: EventTemplateRepository? = null
) {
    suspend operator fun invoke(categoryId: String): Result<Unit> = Result.runCatching {
        val category = categoryRepository.getCategory(categoryId)
            ?: throw IllegalArgumentException("Category not found")

        // 1. Check if any events reference this category across all lifecycle states (ACTIVE, ARCHIVED, TRASHED)
        val totalEventCount = eventRepository.countEventsByCategory(categoryId)
        if (totalEventCount > 0) {
            throw IllegalStateException(
                "Cannot delete category '${category.name}' because it is in use by $totalEventCount reminder(s)."
            )
        }

        // 2. Check if any event types reference this category
        val referencedTypes = eventTypeRepository.getEventTypesByCategory(categoryId).first()
        if (referencedTypes.isNotEmpty()) {
            throw IllegalStateException(
                "Cannot delete category '${category.name}' because it is in use by ${referencedTypes.size} event type(s)."
            )
        }

        // 3. Check if any event templates reference this category
        val referencedTemplatesCount = eventTemplateRepository?.countTemplatesByCategory(categoryId) ?: 0
        if (referencedTemplatesCount > 0) {
            throw IllegalStateException(
                "Cannot delete category '${category.name}' because it is in use by $referencedTemplatesCount event template(s)."
            )
        }

        categoryRepository.deleteCategory(categoryId)
    }
}
