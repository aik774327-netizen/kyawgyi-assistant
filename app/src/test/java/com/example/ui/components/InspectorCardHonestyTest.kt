package com.example.ui.components

import com.example.utils.TtsDiagnosticReport
import com.example.utils.TtsSupportStatus
import com.kyawgyi.voice.SttEngineType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE-041 — the STT/TTS inspector card must REPORT live diagnostics, not
 * ASSERT capabilities. Before this fix the subtitle was the hard-coded
 * claim "System STT • Google my-MM TTS • NLU & G2P Verifier" (shown even
 * when the my-MM voice data was missing), the card advertised the removed
 * phonetic G2P fallback, and Refresh toasted success unconditionally.
 */
class InspectorCardHonestyTest {

    @Test
    fun t17_041_01_missingData_subtitleReportsMissing_neverClaimsGoogleMyMmTts() {
        val report = TtsDiagnosticReport(
            status = TtsSupportStatus.MISSING_DATA,
            currentEnginePackage = "com.google.android.tts"
        )
        val subtitle = inspectorEngineSubtitle(
            sttAvailable = true,
            sttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
            report = report
        )
        assertTrue(subtitle.contains("my-MM voice data missing"))
        assertFalse(subtitle.contains("Google my-MM TTS"))
        assertTrue(subtitle.contains("System STT"))
    }

    @Test
    fun t17_041_02_ready_subtitleReportsReady_refreshToastCelebrates() {
        val report = TtsDiagnosticReport(
            status = TtsSupportStatus.READY,
            currentEnginePackage = "com.google.android.tts"
        )
        val subtitle = inspectorEngineSubtitle(
            sttAvailable = true,
            sttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
            report = report
        )
        assertTrue(subtitle.contains("my-MM voice ready"))
        assertTrue(inspectorRefreshToast(report).contains("အဆင်သင့်"))
    }

    @Test
    fun t17_041_03_refreshToast_missingData_doesNotClaimSuccess() {
        val report = TtsDiagnosticReport(status = TtsSupportStatus.MISSING_DATA)
        val toast = inspectorRefreshToast(report)
        assertFalse(toast.contains("✓"))
        assertTrue(toast.contains("မရှိသေး"))
    }

    @Test
    fun t17_041_04_voiceSummary_missingData_noFallbackAdvertised() {
        val report = TtsDiagnosticReport(status = TtsSupportStatus.MISSING_DATA)
        val summary = inspectorVoiceSummary(report)
        assertFalse(summary.contains("Universal Phonetic G2P Fallback"))
        assertTrue(summary.contains("phonetic fallback မရှိပါ"))
    }

    @Test
    fun t17_041_05_sttUnavailable_subtitleReportsUnavailable() {
        val report = TtsDiagnosticReport(status = TtsSupportStatus.CHECKING)
        val subtitle = inspectorEngineSubtitle(
            sttAvailable = false,
            sttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
            report = report
        )
        assertTrue(subtitle.contains("unavailable"))
        assertTrue(subtitle.contains("စစ်ဆေးနေပါသည်"))
    }
}
