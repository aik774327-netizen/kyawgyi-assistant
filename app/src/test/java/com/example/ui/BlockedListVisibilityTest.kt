package com.example.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.ui.components.ErrorLearningLogViewerCard
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-046 — the viewer's "Failed-Method Memory" section silently
 * truncated the blocked list with .take(2): with 3+ blocked methods the
 * user could not even see that the rest were blocked. Every blocked entry
 * must be rendered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockedListVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    @Test
    fun t17_046_01_allBlockedMethodsAreRendered_notJustTwo() {
        val keys = listOf(
            "v2|app_launch|046a",
            "v2|app_launch|046b",
            "v2|app_launch|046c"
        )
        for (key in keys) {
            ErrorLearningWorkflowEngine.recordErrorLog(
                task = "046 visibility probe",
                commandKey = key,
                expectedResult = "the app opens",
                actualResult = "Package missing: APP_NOT_FOUND_OR_LOW_CONFIDENCE",
                attemptedMethod = "openApplication",
                evidence = "APP_NOT_FOUND_OR_LOW_CONFIDENCE"
            )
        }
        assertEquals(3, ErrorLearningWorkflowEngine.blockedMethodsFlow.value.size)

        composeTestRule.setContent { ErrorLearningLogViewerCard() }

        for (key in keys) {
            composeTestRule.onNodeWithTag("blocked_method_item_$key").assertExists()
        }
    }
}
