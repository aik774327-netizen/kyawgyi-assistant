package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.api.ApiKeyQuotaManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FIX-05 regression tests — key save outcome + settings/service
 * round-trip (requirement: the Settings UI and the request path read
 * ONE storage — the shared ApiKeyQuotaManager pool).
 *
 * Environment note (same fact the existing ApiKeyQuotaManagerTest
 * documents): Robolectric has no AndroidKeyStore provider, so the
 * fail-closed vault cannot persist here and addOrUpdateKey must
 * honestly report saved=false in this environment — while on a real
 * device with a working Keystore it reports the real write result.
 * The pre-fix API returned Unit, making the outcome unobservable and
 * the Settings Toast an unconditional lie.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApiKeySaveOutcomeTest {

    private lateinit var context: Context
    private lateinit var quotaManager: ApiKeyQuotaManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        quotaManager = ApiKeyQuotaManager.getRequiredInstance(context)
        runBlocking {
            quotaManager.resetAllQuotasAndCooldowns()
            val toRemove = quotaManager.poolState.value.keysList
                .filter { it.id != ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
            toRemove.forEach { quotaManager.removeKey(it.id) }
        }
    }

    // T16-05-10: blank keys are rejected and report not-saved.
    @Test
    fun t16_05_10_blankKeyNotSaved() = runBlocking {
        assertFalse(quotaManager.addOrUpdateKey(rawKey = "   ", label = "blank"))
    }

    // T16-05-11: the save outcome is REPORTED (Boolean), and in this
    // vault-less environment it must be false — not hidden. The key
    // still lands in the shared in-memory pool (see T16-05-12).
    @Test
    fun t16_05_11_saveOutcomeReflectsVaultReality() = runBlocking {
        val saved = quotaManager.addOrUpdateKey(rawKey = "dummy-key-fix05", label = "FIX05 outcome")
        assertFalse(
            "Robolectric has no AndroidKeyStore; the vault fails closed, so saved must be false here",
            saved
        )
    }

    // T16-05-12: round-trip through the ONE shared pool — what
    // Settings saved is exactly what the request path reads back as
    // the active key (no divergence between UI list and service pool).
    @Test
    fun t16_05_12_settingsToServiceRoundTrip() = runBlocking {
        quotaManager.addOrUpdateKey(rawKey = "dummy-key-fix05", label = "FIX05 roundtrip")

        val entry = quotaManager.poolState.value.keysList
            .firstOrNull { it.label == "FIX05 roundtrip" }
        assertTrue("saved key must appear in the shared pool the UI shows", entry != null)

        quotaManager.manuallySetActiveKey(entry!!.id)
        assertEquals("dummy-key-fix05", quotaManager.getActiveApiKey())
    }
}
