package com.kyawgyi.assistant.state

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AssistantState represents the distinct states of the KyawGyi Assistant FSM.
 */
enum class AssistantState {
    IDLE,
    WAKE_DETECTED,
    LISTENING,
    TRANSCRIBING,
    UNDERSTANDING,
    THINKING,
    PLANNING,
    SAFETY_CHECK,
    AWAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    RESPONDING,
    SPEAKING,
    SAFE_FAIL,
    ERROR
}

/**
 * AssistantStateStore (Layer 3 - Central Finite State Machine)
 *
 * Thread-safe StateFlow store governing global assistant lifecycle states.
 */
class AssistantStateStore {

    companion object {
        private const val TAG = "AssistantStateStore"

        @Volatile
        private var instance: AssistantStateStore? = null

        fun getInstance(): AssistantStateStore {
            return instance ?: synchronized(this) {
                instance ?: AssistantStateStore().also { instance = it }
            }
        }
    }

    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    @Synchronized
    fun transitionTo(newState: AssistantState) {
        val oldState = _state.value
        Log.i(TAG, "FSM State transition: $oldState -> $newState")
        _state.value = newState
    }

    @Synchronized
    fun resetToIdle() {
        val oldState = _state.value
        if (oldState != AssistantState.IDLE) {
            Log.i(TAG, "FSM State reset from $oldState to IDLE")
            _state.value = AssistantState.IDLE
        }
    }

    fun getCurrentState(): AssistantState = _state.value
}
