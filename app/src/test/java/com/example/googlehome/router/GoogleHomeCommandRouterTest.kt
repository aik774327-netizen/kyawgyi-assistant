package com.example.googlehome.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KyawGyiDatabase
import com.example.googlehome.data.SmartHomeDeviceDao
import com.example.googlehome.data.SmartHomeDeviceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-032 regression tests (Phase 1.5 plan §5), IDs T15-032-*.
 *
 * GoogleHomeCommandRouter evaluated its Turn-On branch before its
 * Turn-Off branch, and a device match with no verb at all fell into a
 * default branch that toggled the device's power — merely mentioning
 * a device flipped it.
 *
 * Evidence note: with a blank apiEndpoint (as seeded here), every
 * execution tier fails and the service's Tier-4 rollback restores the
 * optimistic DB update, so the DB end-state cannot discriminate the
 * branch order. The returned result's targetState/actionType can:
 * they carry the exact power state the router asked the service to
 * apply ("ဖွင့်ခြင်း (ON)" vs "ပိတ်ခြင်း (OFF)").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleHomeCommandRouterTest {

    private lateinit var context: Context
    private lateinit var dao: SmartHomeDeviceDao
    private lateinit var router: GoogleHomeCommandRouter

    private val deviceId = "t15_032_light"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dao = KyawGyiDatabase.getDatabase(context).smartHomeDeviceDao()
        // Isolate this suite: other suites share the same Room DB file.
        dao.getAllDevices().forEach { dao.deleteDevice(it) }
        router = GoogleHomeCommandRouter(context)
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAllDevices().forEach { dao.deleteDevice(it) }
    }

    private suspend fun seedLight(isOn: Boolean) {
        dao.insertDevice(
            SmartHomeDeviceEntity(
                deviceId = deviceId,
                name = "မီး",
                deviceType = "LIGHT",
                isOn = isOn,
                burmeseTriggerPhrases = "မီး",
                apiEndpoint = ""
            )
        )
    }

    // T15-032-01a — mixed markers ("ဖွင့်" AND "ပိတ်" in one sentence):
    // OFF must win. Pre-fix the Turn-On branch was evaluated first and
    // the router requested ON for "turn off the light that is on".
    @Test
    fun t15_032_01a_mixedMarkers_offWins() = runBlocking {
        seedLight(isOn = true)
        val result = router.routeVoiceCommand("မီးဖွင့်ထားတာ ပိတ်ပါ")
        assertNotNull(result)
        assertEquals("POWER_TOGGLE", result!!.actionType)
        assertEquals("ပိတ်ခြင်း (OFF)", result.targetState)
    }

    // T15-032-01b — OFF-only control: a plain off command requests OFF
    // (true before and after the fix).
    @Test
    fun t15_032_01b_offOnly_offRequested() = runBlocking {
        seedLight(isOn = true)
        val result = router.routeVoiceCommand("မီး ပိတ်ပါ")
        assertNotNull(result)
        assertEquals("ပိတ်ခြင်း (OFF)", result!!.targetState)
    }

    // T15-032-01c — ON-only control: a plain on command still requests
    // ON (no over-blocking from the reorder).
    @Test
    fun t15_032_01c_onOnly_onRequested() = runBlocking {
        seedLight(isOn = false)
        val result = router.routeVoiceCommand("မီး ဖွင့်ပါ")
        assertNotNull(result)
        assertEquals("ဖွင့်ခြင်း (ON)", result!!.targetState)
    }

    // T15-032-02 — device-name-only utterance: no verb, no action.
    // Pre-fix the default branch toggled power (a POWER_TOGGLE result
    // requesting the opposite state). Post-fix: a NO_ACTION clarify
    // result and the stored device state is untouched.
    @Test
    fun t15_032_02_deviceNameOnly_noToggle() = runBlocking {
        seedLight(isOn = false)
        val result = router.routeVoiceCommand("မီး")
        assertNotNull(result)
        assertEquals("NO_ACTION", result!!.actionType)
        assertFalse(result.isSuccess)
        assertEquals(
            "device state must be untouched by a verb-less mention",
            false,
            dao.getDeviceById(deviceId)?.isOn
        )
    }
}
