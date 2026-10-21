package com.example.utils

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PomodoroTimerUtilityTest {

    private lateinit var timerUtility: PomodoroTimerUtility
    private var completedCallbackReceived = false
    private var completedSnapshot: PomodoroTimerSnapshot? = null

    @Before
    fun setUp() {
        completedCallbackReceived = false
        completedSnapshot = null
        timerUtility = PomodoroTimerUtility(
            onSessionCompleted = { snapshot ->
                completedCallbackReceived = true
                completedSnapshot = snapshot
            }
        )
    }

    @Test
    fun testInitialState_defaultsTo25MinFocusSession() {
        val snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroMode.FOCUS, snapshot.mode)
        assertEquals(PomodoroStatus.IDLE, snapshot.status)
        assertEquals(25 * 60, snapshot.totalSeconds)
        assertEquals(25 * 60, snapshot.remainingSeconds)
        assertEquals(0, snapshot.elapsedSeconds)
        assertEquals("25:00", snapshot.formattedTime)
        assertEquals(0.0f, snapshot.progress, 0.001f)
        assertFalse(snapshot.isRunning)
        assertFalse(snapshot.isPaused)
        assertFalse(snapshot.isCompleted)
    }

    @Test
    fun testStartTimer_setsStatusToRunning() {
        timerUtility.start()
        val snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroStatus.RUNNING, snapshot.status)
        assertTrue(snapshot.isRunning)
        assertFalse(snapshot.isPaused)
    }

    @Test
    fun testPauseTimer_setsStatusToPaused() {
        timerUtility.start()
        assertTrue(timerUtility.snapshot.value.isRunning)

        timerUtility.pause()
        val snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroStatus.PAUSED, snapshot.status)
        assertTrue(snapshot.isPaused)
        assertFalse(snapshot.isRunning)
    }

    @Test
    fun testResumeTimer_fromPausedState() {
        timerUtility.start()
        timerUtility.pause()
        assertTrue(timerUtility.snapshot.value.isPaused)

        timerUtility.resume()
        val snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroStatus.RUNNING, snapshot.status)
        assertTrue(snapshot.isRunning)
    }

    @Test
    fun testToggleTimer_switchesBetweenRunningAndPaused() {
        // From IDLE -> RUNNING
        timerUtility.toggle()
        assertTrue(timerUtility.snapshot.value.isRunning)

        // From RUNNING -> PAUSED
        timerUtility.toggle()
        assertTrue(timerUtility.snapshot.value.isPaused)

        // From PAUSED -> RUNNING
        timerUtility.toggle()
        assertTrue(timerUtility.snapshot.value.isRunning)
    }

    @Test
    fun testResetTimer_restoresInitialDurationAndIdleStatus() {
        timerUtility.setCustomDurationSeconds(10)
        timerUtility.start()
        timerUtility.manualTick() // 9 remaining, 1 elapsed
        assertEquals(9, timerUtility.snapshot.value.remainingSeconds)
        assertEquals(1, timerUtility.snapshot.value.elapsedSeconds)

        timerUtility.reset()
        val snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroStatus.IDLE, snapshot.status)
        assertEquals(10, snapshot.remainingSeconds)
        assertEquals(0, snapshot.elapsedSeconds)
        assertFalse(snapshot.isRunning)
    }

    @Test
    fun testSwitchMode_shortBreakAndLongBreak() {
        // Short break (5 mins = 300s)
        timerUtility.setMode(PomodoroMode.SHORT_BREAK)
        var snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroMode.SHORT_BREAK, snapshot.mode)
        assertEquals(5 * 60, snapshot.totalSeconds)
        assertEquals(5 * 60, snapshot.remainingSeconds)
        assertEquals("05:00", snapshot.formattedTime)

        // Long break (15 mins = 900s)
        timerUtility.setMode(PomodoroMode.LONG_BREAK)
        snapshot = timerUtility.snapshot.value
        assertEquals(PomodoroMode.LONG_BREAK, snapshot.mode)
        assertEquals(15 * 60, snapshot.totalSeconds)
        assertEquals("15:00", snapshot.formattedTime)
    }

    @Test
    fun testManualTick_progressAndCompletionCallback() {
        // Set a small 3-second session
        timerUtility.setCustomDurationSeconds(3)
        timerUtility.start()

        // Tick 1: 3 -> 2
        timerUtility.manualTick()
        assertEquals(2, timerUtility.snapshot.value.remainingSeconds)
        assertEquals(1, timerUtility.snapshot.value.elapsedSeconds)
        assertEquals(1f / 3f, timerUtility.snapshot.value.progress, 0.01f)
        assertFalse(completedCallbackReceived)

        // Tick 2: 2 -> 1
        timerUtility.manualTick()
        assertEquals(1, timerUtility.snapshot.value.remainingSeconds)
        assertEquals(2, timerUtility.snapshot.value.elapsedSeconds)
        assertFalse(completedCallbackReceived)

        // Tick 3: 1 -> 0 (Completion)
        timerUtility.manualTick()
        val finalSnapshot = timerUtility.snapshot.value
        assertEquals(0, finalSnapshot.remainingSeconds)
        assertEquals(3, finalSnapshot.elapsedSeconds)
        assertEquals(PomodoroStatus.COMPLETED, finalSnapshot.status)
        assertTrue(finalSnapshot.isCompleted)
        assertEquals(1, finalSnapshot.completedCount)
        assertTrue(completedCallbackReceived)
        assertNotNull(completedSnapshot)
        assertEquals(3, completedSnapshot?.totalSeconds)
    }

    @Test
    fun testSetCategory_updatesCategoryTag() {
        assertEquals(com.example.data.FocusCategory.WORK, timerUtility.snapshot.value.category)

        timerUtility.setCategory(com.example.data.FocusCategory.STUDY)
        assertEquals(com.example.data.FocusCategory.STUDY, timerUtility.snapshot.value.category)

        timerUtility.setCategory(com.example.data.FocusCategory.PERSONAL)
        assertEquals(com.example.data.FocusCategory.PERSONAL, timerUtility.snapshot.value.category)
        assertEquals("#10B981", timerUtility.snapshot.value.category.colorHex)
    }
}
