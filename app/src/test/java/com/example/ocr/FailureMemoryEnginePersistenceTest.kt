package com.example.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CommandLogEntity
import com.example.data.FailureCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FailureMemoryEnginePersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            FailureMemoryEngine.clearAllPersistently(context)
        }
        FailureMemoryEngine.initialize(context)
    }

    @Test
    fun testRecordFailureAndPersistenceToRoom() = runBlocking {
        // Record a new failure
        val record = FailureMemoryEngine.recordFailure(
            component = "FLASHLIGHT_ACTION",
            operation = "ENABLE_TORCH",
            category = FailureCategory.PRAGMATIC_CONTEXTUAL,
            rootCauseMm = "ကင်မရာ ဟာ့ဒ်ဝဲကို အခြားအက်ပ်မှ သုံးစွဲနေပါသည်",
            technicalDetails = "CameraAccessException: CAMERA_IN_USE",
            suggestedRemediationMm = "ကင်မရာဖွင့်ထားသော အက်ပ်များကို ပိတ်ပြီးမှ ထပ်မံကြိုးစားပါ"
        )

        assertNotNull(record)
        assertEquals(1, record.occurrences)
        assertTrue(FailureMemoryEngine.hasPreviousFailure("FLASHLIGHT_ACTION", "ENABLE_TORCH"))

        // Wait brief moment for async IO persistence
        delay(200)

        // Verify it was persisted to Room DB
        val db = AppDatabase.getDatabase(context)
        val logs = db.commandLogDao().getAllFailuresForMemoryRestore()
        assertTrue("Expected at least 1 failed log in DB", logs.isNotEmpty())
        val saved = logs.firstOrNull { it.commandText == "ENABLE_TORCH" }
        assertNotNull(saved)
        assertEquals("FLASHLIGHT_ACTION", saved?.commandType)
        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL.code, saved?.failureCategory)
        assertEquals("ကင်မရာ ဟာ့ဒ်ဝဲကို အခြားအက်ပ်မှ သုံးစွဲနေပါသည်", saved?.failureReason)
    }

    @Test
    fun testRestoreFromDatabaseAfterCacheClear() = runBlocking {
        val db = AppDatabase.getDatabase(context)

        // Insert a simulated failure log directly in Room DB (as if from a previous session before app restart)
        val pastLog = CommandLogEntity(
            commandText = "OPEN_CUSTOM_APP",
            commandType = "APPLICATION_ROUTER",
            status = "FAILED",
            actionDetail = "ActivityNotFoundException: No intent match",
            timestamp = System.currentTimeMillis() - 10000,
            failureCategory = FailureCategory.PRAGMATIC_CONTEXTUAL.code,
            failureReason = "အဆိုပါအက်ပ် ဖုန်းထဲတွင် မရှိပါ",
            suggestedAlternative = "Google Play Store မှ ရှာဖွေသွင်းယူပါ",
            isRepeatedFailure = false
        )
        db.commandLogDao().insertCommandLog(pastLog)

        // Clear in-memory cache completely (simulate process restart)
        FailureMemoryEngine.clearAll()
        assertFalse(FailureMemoryEngine.hasPreviousFailure("APPLICATION_ROUTER", "OPEN_CUSTOM_APP"))

        // Call restoreFailuresFromDatabase
        FailureMemoryEngine.restoreFailuresFromDatabase(context)

        // Verify the in-memory engine successfully restored the failure
        assertTrue(FailureMemoryEngine.hasPreviousFailure("APPLICATION_ROUTER", "OPEN_CUSTOM_APP"))
        val restored = FailureMemoryEngine.getLatestFailure("APPLICATION_ROUTER", "OPEN_CUSTOM_APP")
        assertNotNull(restored)
        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL, restored?.failureCategory)
        assertEquals("အဆိုပါအက်ပ် ဖုန်းထဲတွင် မရှိပါ", restored?.rootCauseMm)

        // Verify warning generation works as required by prompt
        val warning = FailureMemoryEngine.getRepeatWarningIfFailed("APPLICATION_ROUTER", "OPEN_CUSTOM_APP")
        assertNotNull(warning)
        assertTrue(warning?.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်") == true)
        assertTrue(warning?.contains("အဆိုပါအက်ပ် ဖုန်းထဲတွင် မရှိပါ") == true)
    }
}
