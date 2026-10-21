package com.example.utils

import com.example.data.FocusCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Supported Pomodoro Session Modes
 */
enum class PomodoroMode(val defaultMinutes: Int, val displayName: String) {
    FOCUS(25, "Focus Session"),
    SHORT_BREAK(5, "Short Break"),
    LONG_BREAK(15, "Long Break"),
    CUSTOM(25, "Custom Session")
}

/**
 * Status of the Pomodoro Timer
 */
enum class PomodoroStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

/**
 * State Snapshot for Pomodoro Focus Session
 */
data class PomodoroTimerSnapshot(
    val mode: PomodoroMode = PomodoroMode.FOCUS,
    val status: PomodoroStatus = PomodoroStatus.IDLE,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val elapsedSeconds: Int = 0,
    val sessionName: String = "Focus Session",
    val category: FocusCategory = FocusCategory.WORK,
    val completedCount: Int = 0
) {
    val progress: Float
        get() = if (totalSeconds > 0) {
            ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedTime: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
        }

    val isRunning: Boolean
        get() = status == PomodoroStatus.RUNNING

    val isPaused: Boolean
        get() = status == PomodoroStatus.PAUSED

    val isCompleted: Boolean
        get() = status == PomodoroStatus.COMPLETED
}

/**
 * Pomodoro Timer Utility
 * Manages timer ticks, countdown calculations, start, pause, resume, and reset functionalities.
 */
class PomodoroTimerUtility(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val tickIntervalMs: Long = 1000L,
    private val onSessionCompleted: ((PomodoroTimerSnapshot) -> Unit)? = null
) {
    private val _snapshot = MutableStateFlow(PomodoroTimerSnapshot())
    val snapshot: StateFlow<PomodoroTimerSnapshot> = _snapshot.asStateFlow()

    private var timerJob: Job? = null

    /**
     * Start or resume the timer
     */
    fun start() {
        if (_snapshot.value.status == PomodoroStatus.RUNNING) return

        if (_snapshot.value.status == PomodoroStatus.COMPLETED || _snapshot.value.remainingSeconds <= 0) {
            // Reset to current mode duration before starting
            val defaultSecs = _snapshot.value.mode.defaultMinutes * 60
            _snapshot.value = _snapshot.value.copy(
                totalSeconds = defaultSecs,
                remainingSeconds = defaultSecs,
                elapsedSeconds = 0,
                status = PomodoroStatus.RUNNING
            )
        } else {
            _snapshot.value = _snapshot.value.copy(status = PomodoroStatus.RUNNING)
        }

        startTimerJob()
    }

    /**
     * Pause the active timer
     */
    fun pause() {
        if (_snapshot.value.status != PomodoroStatus.RUNNING) return
        stopTimerJob()
        _snapshot.value = _snapshot.value.copy(status = PomodoroStatus.PAUSED)
    }

    /**
     * Resume from paused state
     */
    fun resume() {
        if (_snapshot.value.status == PomodoroStatus.PAUSED) {
            start()
        }
    }

    /**
     * Toggle between start/resume and pause
     */
    fun toggle() {
        if (_snapshot.value.status == PomodoroStatus.RUNNING) {
            pause()
        } else {
            start()
        }
    }

    /**
     * Reset the timer to default for current mode or configured total duration
     */
    fun reset(resetCompletedCount: Boolean = false) {
        stopTimerJob()
        val resetSecs = if (_snapshot.value.mode == PomodoroMode.CUSTOM) {
            _snapshot.value.totalSeconds
        } else {
            _snapshot.value.mode.defaultMinutes * 60
        }
        _snapshot.value = _snapshot.value.copy(
            status = PomodoroStatus.IDLE,
            totalSeconds = resetSecs,
            remainingSeconds = resetSecs,
            elapsedSeconds = 0,
            completedCount = if (resetCompletedCount) 0 else _snapshot.value.completedCount
        )
    }

    /**
     * Switch session mode (FOCUS, SHORT_BREAK, LONG_BREAK, CUSTOM)
     */
    fun setMode(mode: PomodoroMode, customMinutes: Int? = null) {
        stopTimerJob()
        val durationMins = if (mode == PomodoroMode.CUSTOM && customMinutes != null) customMinutes else mode.defaultMinutes
        val totalSecs = (durationMins * 60).coerceAtLeast(1)

        _snapshot.value = _snapshot.value.copy(
            mode = mode,
            status = PomodoroStatus.IDLE,
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            elapsedSeconds = 0,
            sessionName = mode.displayName
        )
    }

    /**
     * Set custom duration in minutes
     */
    fun setCustomDurationMinutes(minutes: Int) {
        setMode(PomodoroMode.CUSTOM, minutes)
    }

    /**
     * Set custom duration in seconds directly (useful for tests and fine-grained control)
     */
    fun setCustomDurationSeconds(seconds: Int) {
        stopTimerJob()
        val secs = seconds.coerceAtLeast(1)
        _snapshot.value = _snapshot.value.copy(
            mode = PomodoroMode.CUSTOM,
            status = PomodoroStatus.IDLE,
            totalSeconds = secs,
            remainingSeconds = secs,
            elapsedSeconds = 0
        )
    }

    /**
     * Update session display title/name
     */
    fun setSessionName(name: String) {
        _snapshot.value = _snapshot.value.copy(sessionName = name)
    }

    /**
     * Update session category tag
     */
    fun setCategory(category: FocusCategory) {
        _snapshot.value = _snapshot.value.copy(category = category)
    }

    /**
     * Manually perform one tick (useful for deterministic unit tests)
     */
    fun manualTick() {
        val current = _snapshot.value
        if (current.remainingSeconds > 1) {
            _snapshot.value = current.copy(
                remainingSeconds = current.remainingSeconds - 1,
                elapsedSeconds = current.elapsedSeconds + 1
            )
        } else if (current.remainingSeconds == 1) {
            val completedSnapshot = current.copy(
                remainingSeconds = 0,
                elapsedSeconds = current.elapsedSeconds + 1,
                status = PomodoroStatus.COMPLETED,
                completedCount = current.completedCount + 1
            )
            _snapshot.value = completedSnapshot
            stopTimerJob()
            onSessionCompleted?.invoke(completedSnapshot)
        }
    }

    private fun startTimerJob() {
        stopTimerJob()
        timerJob = coroutineScope.launch {
            while (isActive && _snapshot.value.status == PomodoroStatus.RUNNING) {
                delay(tickIntervalMs)
                manualTick()
            }
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Cancel coroutine jobs on cleanup
     */
    fun release() {
        stopTimerJob()
    }
}
