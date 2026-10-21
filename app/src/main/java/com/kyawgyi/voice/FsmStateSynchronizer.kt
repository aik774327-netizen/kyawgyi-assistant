package com.kyawgyi.voice

import android.util.Log
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FsmStateSynchronizer (Layer 2 ➔ Layer 3 Integration Bridge)
 *
 * Bridges the lower-level Speech-to-Text Engine lifecycle (SttEngineManager in Layer 2)
 * with the Central Assistant Finite State Machine (AssistantStateStore in Layer 3).
 *
 * Mapping Rules:
 * - SttEngineState.LISTENING   ➔ AssistantState.LISTENING (User is speaking into Mic)
 * - SttEngineState.PROCESSING  ➔ AssistantState.THINKING (Processing ASR/NLU hypothesis)
 * - SttEngineState.SPEAKING    ➔ AssistantState.SPEAKING (TTS audio output in progress)
 * - SttEngineState.IDLE        ➔ AssistantState.IDLE (Session concluded / sleeping)
 * - SttEngineState.ERROR       ➔ AssistantState.ERROR (Recognition error encountered)
 *
 * Safeguards:
 * Preserves high-priority Layer 4/5 states such as `AWAITING_CONFIRMATION` and `EXECUTING`
 * from being prematurely overridden by background STT state resets.
 */
class FsmStateSynchronizer(
    private val sttEngineManager: SttEngineManager,
    private val assistantStateStore: AssistantStateStore = AssistantStateStore.getInstance(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    companion object {
        private const val TAG = "FsmStateSynchronizer"

        @Volatile
        private var instance: FsmStateSynchronizer? = null

        fun getInstance(sttEngineManager: SttEngineManager): FsmStateSynchronizer {
            return instance ?: synchronized(this) {
                instance ?: FsmStateSynchronizer(sttEngineManager).also { instance = it }
            }
        }
    }

    private val mutex = Mutex()
    private val isSyncing = AtomicBoolean(false)
    private var syncJob: Job? = null

    /**
     * Starts continuous synchronization between Layer 2 STT state and Layer 3 FSM state store.
     */
    suspend fun startSynchronization() = mutex.withLock {
        if (isSyncing.get()) {
            Log.d(TAG, "FSM state synchronization is already active.")
            return@withLock
        }

        Log.i(TAG, "Starting FsmStateSynchronizer...")

        syncJob = scope.launch {
            try {
                sttEngineManager.engineState.collect { sttState ->
                    syncSttState(sttState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in FSM State Synchronizer collector: ${e.message}", e)
            }
        }

        isSyncing.set(true)
        Log.i(TAG, "FsmStateSynchronizer engaged.")
    }

    /**
     * Synchronizes a specific SttEngineState to the AssistantStateStore FSM.
     */
    fun syncSttState(sttState: SttEngineState) {
        val currentFsmState = assistantStateStore.getCurrentState()

        // Do not override high-priority transactional safety states
        if (currentFsmState == AssistantState.AWAITING_CONFIRMATION ||
            currentFsmState == AssistantState.EXECUTING
        ) {
            Log.d(TAG, "Bypassing STT state change ($sttState) while in protected state ($currentFsmState)")
            return
        }

        val targetFsmState = when (sttState) {
            SttEngineState.LISTENING -> AssistantState.LISTENING
            SttEngineState.PROCESSING -> AssistantState.THINKING
            SttEngineState.SPEAKING -> AssistantState.SPEAKING
            SttEngineState.IDLE -> AssistantState.IDLE
            SttEngineState.ERROR -> AssistantState.ERROR
        }

        if (currentFsmState != targetFsmState) {
            Log.d(TAG, "FSM Synchronizer mapping: STT[$sttState] ➔ FSM[$targetFsmState]")
            assistantStateStore.transitionTo(targetFsmState)
        }
    }

    /**
     * Stops synchronization.
     */
    suspend fun stopSynchronization() = mutex.withLock {
        if (!isSyncing.getAndSet(false)) return@withLock
        Log.i(TAG, "Stopping FsmStateSynchronizer...")
        syncJob?.cancel()
        syncJob = null
    }

    fun isSynchronizing(): Boolean = isSyncing.get()

    fun release() {
        scope.launch {
            stopSynchronization()
            scope.coroutineContext.cancelChildren()
        }
    }
}
