package com.example.offlinereminder.data.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlarmSnapshot(
    val instanceId: String? = null,
    val eventId: String? = null,
    val state: AlarmState = AlarmState.IDLE,
    val startedAtEpochMillis: Long = 0L
)

/**
 * Thread-safe manager for tracking active in-memory alarm playback state.
 * Works alongside Room persistence (which remains authoritative for notification lifecycle).
 */
class AlarmStateManager {

    private val _stateFlow = MutableStateFlow(AlarmSnapshot())
    val stateFlow: StateFlow<AlarmSnapshot> = _stateFlow.asStateFlow()

    @Synchronized
    fun currentState(): AlarmState = _stateFlow.value.state

    @Synchronized
    fun currentSnapshot(): AlarmSnapshot = _stateFlow.value

    @Synchronized
    fun currentInstanceId(): String? = _stateFlow.value.instanceId

    @Synchronized
    fun transitionTo(
        instanceId: String,
        eventId: String?,
        newState: AlarmState,
        startedAt: Long = System.currentTimeMillis()
    ) {
        _stateFlow.value = AlarmSnapshot(
            instanceId = instanceId,
            eventId = eventId,
            state = newState,
            startedAtEpochMillis = startedAt
        )
    }

    @Synchronized
    fun reset() {
        _stateFlow.value = AlarmSnapshot()
    }

    companion object {
        @Volatile
        private var instance: AlarmStateManager? = null

        fun getInstance(): AlarmStateManager {
            return instance ?: synchronized(this) {
                instance ?: AlarmStateManager().also { instance = it }
            }
        }
    }
}
