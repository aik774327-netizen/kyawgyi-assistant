package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.ui.components.ErrorLearningLogViewerCard
import com.example.ui.components.SttTtsModelAndVoiceSettingsCard
import com.kyawgyi.voice.SttEngineManager
import com.kyawgyi.voice.SttEngineType
import com.kyawgyi.voice.VoiceSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErrorLearningAndVoiceSettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        VoiceSettings.getInstance(context).resetToDefaults()
    }

    @Test
    fun errorLearningLogViewerCard_displaysRecordedEr001AndThreeFactorDialog() {
        val recorded = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Call Emergency Contact",
            commandKey = "ဖုန်းခေါ်ပေးပါ",
            expectedResult = "Place direct phone call",
            actualResult = "SecurityException: CALL_PHONE permission denied",
            attemptedMethod = "TelecomManager.placeCall",
            evidence = "SecurityException: Permission Denial: CALL_PHONE"
        )
        assertEquals("ER-001", recorded.errorId)

        composeTestRule.setContent {
            ErrorLearningLogViewerCard()
        }

        composeTestRule.onNodeWithTag("error_learning_log_viewer_card").assertExists()
        composeTestRule.onNodeWithTag("error_logs_count_badge").assertExists()
        composeTestRule.onNodeWithTag("blocked_methods_count_text").assertExists()
        composeTestRule.onNodeWithTag("blocked_method_item_ဖုန်းခေါ်ပေးပါ").assertExists()
        composeTestRule.onNodeWithTag("error_log_compact_item_ER-001").assertExists()

        // Click 3-Factor inspect button to open the 3-Factor Analysis Detail Dialog
        composeTestRule.onNodeWithTag("inspect_error_log_btn_ER-001").performClick()
        composeTestRule.onNodeWithTag("three_factor_detail_dialog_content").assertExists()
        composeTestRule.onNodeWithTag("close_three_factor_detail_btn").performClick()
    }

    @Test
    fun sttTtsModelAndVoiceSettingsCard_switchesEngineAndResetsDefaults() {
        val voiceSettings = VoiceSettings.getInstance(context)
        val sttManager = SttEngineManager.getInstance(context)

        composeTestRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SttTtsModelAndVoiceSettingsCard()
            }
        }

        composeTestRule.onNodeWithTag("stt_tts_model_and_voice_settings_card").assertExists()
        composeTestRule.onNodeWithTag("sherpa_model_status_box").assertExists()
        composeTestRule.onNodeWithTag("vosk_model_status_box").assertExists()
        composeTestRule.onNodeWithTag("start_model_download_btn").assertExists()

        // Select SHERPA_ONNX STT engine via UI
        composeTestRule.onNodeWithTag("select_stt_engine_sherpa_onnx").performScrollTo().performClick()
        assertEquals(SttEngineType.SHERPA_ONNX, voiceSettings.preferredSttEngine)
        assertEquals(SttEngineType.SHERPA_ONNX, sttManager.activeEngine?.engineType)

        // Select VOSK STT engine via UI
        composeTestRule.onNodeWithTag("select_stt_engine_vosk").performScrollTo().performClick()
        assertEquals(SttEngineType.VOSK, voiceSettings.preferredSttEngine)
        assertEquals(SttEngineType.VOSK, sttManager.activeEngine?.engineType)

        // Reset defaults via UI
        composeTestRule.onNodeWithTag("reset_voice_settings_btn").performScrollTo().performClick()
        assertEquals(SttEngineType.SYSTEM_ON_DEVICE, voiceSettings.preferredSttEngine)
    }
}
