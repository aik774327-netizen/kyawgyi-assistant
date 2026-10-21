package com.example.service

import org.junit.Assert.*
import org.junit.Test

class PomodoroForegroundServiceTest {

    @Test
    fun testServiceState_defaults() {
        val state = PomodoroForegroundService.Companion.ServiceState()
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(0, state.remainingSeconds)
        assertEquals(0, state.totalSeconds)
        assertEquals(0f, state.progress, 0.001f)
    }

    @Test
    fun testServiceState_progressCalculation() {
        val runningState = PomodoroForegroundService.Companion.ServiceState(
            isRunning = true,
            isPaused = false,
            remainingSeconds = 10 * 60,
            totalSeconds = 25 * 60,
            sessionName = "Deep Work",
            category = "Work",
            colorHex = "#3B82F6"
        )
        assertTrue(runningState.isRunning)
        assertFalse(runningState.isPaused)
        assertEquals(0.6f, runningState.progress, 0.001f)
        assertEquals("Work", runningState.category)
        assertEquals("#3B82F6", runningState.colorHex)
    }
}
