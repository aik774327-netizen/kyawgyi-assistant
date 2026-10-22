package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-035 — canonical error-code handling + root-cause evidence storage.
 *
 * Regression target: ER-007 was classified "#34 Runtime Error" because the
 * launcher emits APP_NOT_FOUND_OR_LOW_CONFIDENCE (underscores) while the
 * classifier matched "not found" (space). Both spellings must now classify
 * identically BY CONSTRUCTION (explicit structured-code mapping + underscore
 * normalization at the classifier boundary), the code must be stored verbatim
 * on the entry, and the original exception class/message (redacted, capped)
 * must be stored — or ORIGINAL_EXCEPTION_UNAVAILABLE when none exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaxonomyEvidenceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    @Test
    fun t17_035_01_underscoreLauncherCode_classifiesResource25_notRuntime34() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (ယူကျု)",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App could not be opened",
            attemptedMethod = "UniversalAppLauncher.resolveAndLaunch(ယူကျု)",
            evidence = "CommandResult(isSuccess=false, action=OPEN_APP, target=ယူကျု, errorCode=APP_NOT_FOUND_OR_LOW_CONFIDENCE)"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            entry.errorType
        )
        assertEquals(25, entry.errorType.id)
        assertEquals("APP_NOT_FOUND_OR_LOW_CONFIDENCE", entry.errorCode)
    }

    @Test
    fun t17_035_02_spacedSpelling_classifiesIdentically() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (ယူကျု)",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found or low confidence",
            attemptedMethod = "UniversalAppLauncher.resolveAndLaunch(ယူကျု)",
            evidence = "launcher reported: app not found or low confidence for the query"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            entry.errorType
        )
    }

    @Test
    fun t17_035_03_threeFactorAnalysis_derivesFromStoredCode_notGenericTemplate() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (ယူကျု)",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App could not be opened",
            attemptedMethod = "UniversalAppLauncher.resolveAndLaunch(ယူကျု)",
            evidence = "errorCode=APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        assertTrue(
            "Technical cause must cite the stored structured code",
            entry.threeFactorAnalysis.technicalCause.contains("APP_NOT_FOUND_OR_LOW_CONFIDENCE")
        )
        assertFalse(
            "Generic runtime template must not be used when a structured code exists",
            entry.threeFactorAnalysis.technicalCause.contains("unhandled runtime condition")
        )
    }

    @Test
    fun t17_035_04_otherLauncherCodes_mapExplicitly() {
        val disabled = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (x)", commandKey = "k1",
            expectedResult = "opens", actualResult = "disabled",
            attemptedMethod = "UniversalAppLauncher",
            evidence = "errorCode=PACKAGE_DISABLED_OR_NOT_FOUND"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            disabled.errorType
        )
        assertEquals("PACKAGE_DISABLED_OR_NOT_FOUND", disabled.errorCode)

        val noIntent = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (y)", commandKey = "k2",
            expectedResult = "opens", actualResult = "no intent",
            attemptedMethod = "UniversalAppLauncher",
            evidence = "errorCode=NO_LAUNCH_INTENT"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            noIntent.errorType
        )

        val ambiguous = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (z)", commandKey = "k3",
            expectedResult = "opens", actualResult = "ambiguous",
            attemptedMethod = "UniversalAppLauncher",
            evidence = "errorCode=AMBIGUOUS_APP_MATCH"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.INTENT_MISUNDERSTANDING_ERROR,
            ambiguous.errorType
        )
        assertEquals("AMBIGUOUS_APP_MATCH", ambiguous.errorCode)
    }

    @Test
    fun t17_035_05_exceptionStored_redactedAndCapped() {
        val longMessage = "failure " + "x".repeat(600)
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Explode", commandKey = "explode_cmd",
            expectedResult = "no explosion", actualResult = "exploded",
            attemptedMethod = "Boom.go()",
            evidence = "something unrelated",
            exception = IllegalStateException(longMessage)
        )
        assertEquals("java.lang.IllegalStateException", entry.exceptionClass)
        assertTrue(entry.exceptionMessage.startsWith("failure"))
        assertTrue(
            "Exception message must be length-capped",
            entry.exceptionMessage.length <= 300
        )
    }

    @Test
    fun t17_035_06_noException_storesOriginalExceptionUnavailable() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Silent failure", commandKey = "silent_cmd",
            expectedResult = "works", actualResult = "did not work",
            attemptedMethod = "Silent.go()",
            evidence = "no exception was captured for this failure"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ORIGINAL_EXCEPTION_UNAVAILABLE,
            entry.exceptionClass
        )
        assertEquals("", entry.exceptionMessage)
    }

    @Test
    fun t17_035_07_jsonRoundTrip_preservesCodeAndExceptionFields() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "OPEN_APP (ယူကျု)", commandKey = "app_launch_ယူကျု",
            expectedResult = "opens", actualResult = "not opened",
            attemptedMethod = "UniversalAppLauncher",
            evidence = "errorCode=APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            exception = SecurityException("denied by policy")
        )
        val restored = ErrorLearningWorkflowEngine.ErrorLogEntry.fromJsonObject(entry.toJsonObject())
        assertEquals("APP_NOT_FOUND_OR_LOW_CONFIDENCE", restored.errorCode)
        assertEquals("java.lang.SecurityException", restored.exceptionClass)
        assertEquals("denied by policy", restored.exceptionMessage)
        val report = restored.toFormattedReport()
        assertTrue(report.contains("APP_NOT_FOUND_OR_LOW_CONFIDENCE"))
        assertTrue(report.contains("java.lang.SecurityException"))
    }

    @Test
    fun t17_035_08_explicitErrorCodeParam_winsOverEvidence() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Toggle", commandKey = "toggle_cmd",
            expectedResult = "toggles", actualResult = "panel only",
            attemptedMethod = "KyawGyiActionExecutor",
            evidence = "some generic text",
            errorCode = "PANEL_ONLY_NO_DIRECT_TOGGLE"
        )
        assertEquals("PANEL_ONLY_NO_DIRECT_TOGGLE", entry.errorCode)
    }
}
