package com.example.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OneClickSystemDiagnosticEngineTest {

    @Test
    fun testFullDiagnosticExecution() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OneClickSystemDiagnosticEngine.getInstance(context)

        assertNotNull(engine)

        // Execute 1-click full system diagnostic
        engine.runFullDiagnostic()

        val report = engine.reportFlow.value
        assertTrue("Report should be marked complete", report.isComplete)
        assertEquals("Total checks should be 10", 10, report.totalChecks)
        assertEquals("10 items should be in results", 10, report.results.size)
        assertTrue("Health score should be between 0 and 100", report.healthScore in 0..100)

        // Verify summary text generation
        val reportText = engine.generateDiagnosticReportText()
        assertTrue("Report text should contain header", reportText.contains("KYAW GYI VOICE AI"))
        assertTrue("Report text should contain health score", reportText.contains("${report.healthScore}%"))
    }
}
