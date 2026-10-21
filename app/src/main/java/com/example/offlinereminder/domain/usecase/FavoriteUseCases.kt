package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow

class SetEventFavoriteUseCase(
    private val repository: EventRepository
) {
    suspend operator fun invoke(id: String, isFavorite: Boolean): Result<Unit> {
        if (id.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return repository.setFavorite(id, isFavorite)
    }
}

class ToggleEventFavoriteUseCase(
    private val repository: EventRepository
) {
    suspend operator fun invoke(id: String): Result<Boolean> {
        if (id.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        val event = repository.getEvent(id)
            ?: return Result.Failure(IllegalArgumentException("Event with id $id not found"))

        val newFavoriteState = !event.isFavorite
        return when (val result = repository.setFavorite(id, newFavoriteState)) {
            is Result.Success -> Result.Success(newFavoriteState)
            is Result.Failure -> result
        }
    }
}

class GetFavoriteEventsUseCase(
    private val repository: EventRepository
) {
    operator fun invoke(): Flow<List<Event>> = repository.getFavoriteEvents()
}
