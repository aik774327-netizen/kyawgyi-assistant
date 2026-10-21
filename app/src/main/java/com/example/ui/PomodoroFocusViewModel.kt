package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FocusCategory
import com.example.data.FocusSessionEntity
import com.example.service.PomodoroForegroundService
import com.example.utils.PomodoroMode
import com.example.utils.PomodoroTimerSnapshot
import com.example.utils.PomodoroTimerUtility
import com.example.utils.PomodoroWorkScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Daily Focus summary for weekly visualizer
 */
data class DayFocusSummary(
    val dayLabel: String,
    val dateString: String,
    val totalMinutes: Int,
    val sessionCount: Int,
    val timestamp: Long
)

/**
 * Category breakdown summary for tag-based analysis
 */
data class CategoryFocusSummary(
    val category: FocusCategory,
    val totalMinutes: Int,
    val sessionCount: Int,
    val percentage: Float
)

/**
 * UI State for Pomodoro Focus Session Screen
 */
data class PomodoroUiState(
    val snapshot: PomodoroTimerSnapshot = PomodoroTimerSnapshot(),
    val selectedCategory: FocusCategory = FocusCategory.WORK,
    val focusHistory: List<FocusSessionEntity> = emptyList(),
    val weeklyHistory: List<FocusSessionEntity> = emptyList(),
    val dailySummariesLast7Days: List<DayFocusSummary> = emptyList(),
    val categoryBreakdowns: List<CategoryFocusSummary> = emptyList(),
    val totalFocusMinutesToday: Int = 0,
    val totalFocusMinutesThisWeek: Int = 0,
    val showCompletionDialog: Boolean = false,
    val lastSavedSessionMessage: String? = null
)

/**
 * ViewModel managing Pomodoro-style focus session state,
 * category tagging, WorkManager background notification scheduling,
 * and Room DB persistence with 7-day weekly analytics.
 */
class PomodoroFocusViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val focusDao = db.focusSessionDao()

    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()

    // Timer utility initialized with viewModelScope
    val timerUtility = PomodoroTimerUtility(
        coroutineScope = viewModelScope,
        tickIntervalMs = 1000L,
        onSessionCompleted = { completedSnapshot ->
            handleSessionCompleted(completedSnapshot)
        }
    )

    init {
        // Collect timer snapshot changes into UI State
        viewModelScope.launch {
            timerUtility.snapshot.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        snapshot = snapshot,
                        selectedCategory = snapshot.category
                    )
                }
            }
        }

        // Collect Room DB Focus History and calculate 7-day and category analytics
        viewModelScope.launch {
            focusDao.getAllFocusSessions().collect { history ->
                val todayStartMs = getTodayStartTimestamp()
                val sevenDaysAgoMs = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)

                val todayMinutes = history
                    .filter { it.timestamp >= todayStartMs }
                    .sumOf { it.durationMinutes }

                val weeklySessions = history.filter { it.timestamp >= sevenDaysAgoMs }
                val weeklyMinutes = weeklySessions.sumOf { it.durationMinutes }

                val dailySummaries = computeLast7DaysSummaries(history)
                val categorySummaries = computeCategorySummaries(weeklySessions)

                _uiState.update { current ->
                    current.copy(
                        focusHistory = history,
                        weeklyHistory = weeklySessions,
                        dailySummariesLast7Days = dailySummaries,
                        categoryBreakdowns = categorySummaries,
                        totalFocusMinutesToday = todayMinutes,
                        totalFocusMinutesThisWeek = weeklyMinutes
                    )
                }
            }
        }
    }

    /**
     * Select active focus category tag (Work, Study, Personal, Health, Creative)
     */
    fun selectCategory(category: FocusCategory) {
        timerUtility.setCategory(category)
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Start the Pomodoro focus session timer and trigger Foreground Service for Deep Sleep execution
     */
    fun startTimer() {
        timerUtility.start()
        val snapshot = timerUtility.snapshot.value
        val remainingSecs = snapshot.remainingSeconds.toLong()
        val durationMins = maxOf(1, snapshot.totalSeconds / 60)

        PomodoroForegroundService.startTimer(
            context = getApplication(),
            sessionName = snapshot.sessionName,
            totalSeconds = snapshot.totalSeconds,
            categoryName = snapshot.category.displayName,
            colorHex = snapshot.category.colorHex,
            remainingSeconds = snapshot.remainingSeconds
        )

        PomodoroWorkScheduler.scheduleCompletionAlert(
            context = getApplication(),
            remainingSeconds = remainingSecs,
            sessionName = snapshot.sessionName,
            category = snapshot.category.displayName,
            durationMinutes = durationMins
        )
    }

    /**
     * Pause the running focus session timer and pause Foreground Service
     */
    fun pauseTimer() {
        timerUtility.pause()
        PomodoroForegroundService.pauseTimer(getApplication())
        PomodoroWorkScheduler.cancelCompletionAlert(getApplication())
    }

    /**
     * Resume from paused state and resume Foreground Service
     */
    fun resumeTimer() {
        startTimer()
    }

    /**
     * Toggle between start/pause
     */
    fun toggleTimer() {
        if (timerUtility.snapshot.value.isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    /**
     * Reset the timer to default for the current mode and stop Foreground Service
     */
    fun resetTimer(resetCompletedCount: Boolean = false) {
        timerUtility.reset(resetCompletedCount)
        PomodoroForegroundService.stopTimer(getApplication())
        PomodoroWorkScheduler.cancelCompletionAlert(getApplication())
        _uiState.update { it.copy(showCompletionDialog = false, lastSavedSessionMessage = null) }
    }

    /**
     * Switch session mode (e.g. Focus 25m, Short Break 5m, Long Break 15m, Custom)
     */
    fun switchMode(mode: PomodoroMode, customMinutes: Int? = null) {
        timerUtility.setMode(mode, customMinutes)
        PomodoroForegroundService.stopTimer(getApplication())
        PomodoroWorkScheduler.cancelCompletionAlert(getApplication())
        _uiState.update { it.copy(showCompletionDialog = false, lastSavedSessionMessage = null) }
    }

    /**
     * Set a custom duration in minutes
     */
    fun setCustomDuration(minutes: Int) {
        timerUtility.setCustomDurationMinutes(minutes)
        PomodoroForegroundService.stopTimer(getApplication())
        PomodoroWorkScheduler.cancelCompletionAlert(getApplication())
    }

    /**
     * Update session display title
     */
    fun setSessionTitle(title: String) {
        timerUtility.setSessionName(title)
    }

    /**
     * Save completed or manual focus session record to Room DB
     */
    fun saveSessionRecord(
        sessionName: String = _uiState.value.snapshot.sessionName,
        category: FocusCategory = _uiState.value.selectedCategory,
        durationMinutes: Int = maxOf(1, _uiState.value.snapshot.elapsedSeconds / 60),
        notes: String = "Pomodoro Session Log"
    ) {
        viewModelScope.launch {
            val entity = FocusSessionEntity(
                sessionName = sessionName.ifBlank { "${category.displayName} Focus" },
                durationMinutes = durationMinutes,
                category = category.displayName,
                colorHex = category.colorHex,
                timestamp = System.currentTimeMillis(),
                notes = notes
            )
            focusDao.insertFocusSession(entity)
            _uiState.update { it.copy(lastSavedSessionMessage = "Saved ${category.displayName} session ($durationMinutes mins)") }
        }
    }

    /**
     * Delete a session record from Room DB
     */
    fun deleteSessionRecord(session: FocusSessionEntity) {
        viewModelScope.launch {
            focusDao.deleteFocusSession(session)
        }
    }

    /**
     * Dismiss completion dialog / notification banner
     */
    fun dismissCompletionDialog() {
        _uiState.update { it.copy(showCompletionDialog = false) }
    }

    private fun handleSessionCompleted(completedSnapshot: PomodoroTimerSnapshot) {
        val completedMinutes = maxOf(1, completedSnapshot.totalSeconds / 60)
        saveSessionRecord(
            sessionName = completedSnapshot.sessionName,
            category = completedSnapshot.category,
            durationMinutes = completedMinutes,
            notes = "Completed ${completedSnapshot.category.displayName} ${completedSnapshot.mode.displayName}"
        )
        _uiState.update { it.copy(showCompletionDialog = true) }
    }

    private fun computeLast7DaysSummaries(history: List<FocusSessionEntity>): List<DayFocusSummary> {
        val list = mutableListOf<DayFocusSummary>()
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = targetCal.timeInMillis
            val endMs = startMs + (24 * 60 * 60 * 1000)

            val matchingSessions = history.filter { it.timestamp in startMs until endMs }
            val totalMins = matchingSessions.sumOf { it.durationMinutes }

            list.add(
                DayFocusSummary(
                    dayLabel = dayFormat.format(targetCal.time),
                    dateString = dateFormat.format(targetCal.time),
                    totalMinutes = totalMins,
                    sessionCount = matchingSessions.size,
                    timestamp = startMs
                )
            )
        }
        return list
    }

    private fun computeCategorySummaries(sessions: List<FocusSessionEntity>): List<CategoryFocusSummary> {
        val totalMins = sessions.sumOf { it.durationMinutes }.coerceAtLeast(1)
        return FocusCategory.entries.map { category ->
            val matching = sessions.filter {
                it.category.equals(category.displayName, ignoreCase = true) ||
                it.category.equals(category.id, ignoreCase = true)
            }
            val mins = matching.sumOf { it.durationMinutes }
            val count = matching.size
            val percentage = (mins.toFloat() / totalMins.toFloat()) * 100f
            CategoryFocusSummary(
                category = category,
                totalMinutes = mins,
                sessionCount = count,
                percentage = percentage
            )
        }
    }

    private fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    override fun onCleared() {
        super.onCleared()
        timerUtility.release()
    }
}
