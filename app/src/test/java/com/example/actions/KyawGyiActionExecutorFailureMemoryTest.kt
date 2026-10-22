package com.example.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KyawGyiActionExecutorFailureMemoryTest {

    private lateinit var context: Context
    private lateinit var executor: KyawGyiActionExecutor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = KyawGyiActionExecutor(context)
        runBlocking {
            FailureMemoryEngine.clearAllPersistently(context)
        }
        FailureMemoryEngine.initialize(context)
        // ISSUE-036: FailureMemoryEngine records now also land in the unified
        // Error-Learning memory; isolate it per method as well.
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    @Test
    fun testFlashlightPreWarningWhenPreviousFailureExists() {
        // Record prior failure
        FailureMemoryEngine.recordFailure(
            component = "FLASHLIGHT_ACTION",
            operation = "ENABLE_TORCH",
            category = FailureCategory.PRAGMATIC_CONTEXTUAL,
            rootCauseMm = "ဓာတ်မီး ဟာ့ဒ်ဝဲကို အခြားအက်ပ်မှ အသုံးပြုနေခြင်းကြောင့် ဖြစ်ပါသည်",
            technicalDetails = "CameraAccessException: CAMERA_IN_USE",
            suggestedRemediationMm = "ကင်မရာဖွင့်ထားသော အက်ပ်များကို ပိတ်ပြီးမှ ထပ်မံကြိုးစားပါ"
        )

        // Attempting to set flashlight should detect previous failure and inject warning
        val result = executor.setFlashlight(true)
        assertNotNull(result.failurePreWarning)
        assertTrue(result.failurePreWarning!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(result.userMessage.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(result.userMessage.contains("လက်တွေ့မကိုက်ညီမှု/အခြေအနေအရ ချို့ယွင်းချက်"))
    }

    @Test
    fun testWifiPreWarningWhenPreviousFailureExists() {
        FailureMemoryEngine.recordFailure(
            component = "WIFI_CONTROL",
            operation = "TURN_ON_WIFI",
            category = FailureCategory.TECHNICAL,
            rootCauseMm = "ဝိုင်ဖိုင် စနစ် ပြောင်းလဲရာတွင် နည်းပညာပိုင်း ချို့ယွင်းချက် ဖြစ်ပေါ်ပါသည်",
            technicalDetails = "SecurityException: Restricted toggle",
            suggestedRemediationMm = "စနစ် ဆက်တင် (Settings) မှတစ်ဆင့် တိုက်ရိုက် ကြိုးစားပါ"
        )

        val result = executor.setWifi(true)
        assertNotNull(result.failurePreWarning)
        assertTrue(result.failurePreWarning!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(result.userMessage.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(result.userMessage.contains("နည်းပညာပိုင်းဆိုင်ရာ ချို့ယွင်းချက်"))
    }

    @Test
    fun testAppLaunchPreWarningWhenPreviousFailureExists() {
        val appName = "NonExistentApp"
        FailureMemoryEngine.recordFailure(
            component = "APP_LAUNCH",
            operation = appName,
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အပလီကေးရှင်း '$appName' အား စက်ထဲတွင် မတွေ့ရှိပါ",
            technicalDetails = "PackageNotFoundException",
            suggestedRemediationMm = "Google Play မှ ထည့်သွင်းပါ"
        )

        val result = executor.openApplication(appName, appName)
        assertNotNull(result.failurePreWarning)
        assertTrue(result.failurePreWarning!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(result.userMessage.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        // ISSUE-036 contract change: openApplication now ENFORCES the unified
        // Failed-Method Memory before executing (previously it only prepended
        // a warning and attempted the launch anyway — the display-only block
        // the audit flagged). The recorded failure therefore blocks the retry
        // outright with the mandatory warning banner.
        assertTrue(!result.isSuccess)
        assertTrue(!result.executionStarted)
        assertEquals("FAILED_METHOD_BLOCKED", result.errorCode)
    }
}
