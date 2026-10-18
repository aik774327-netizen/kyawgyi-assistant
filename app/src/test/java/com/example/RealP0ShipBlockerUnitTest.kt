package com.example

import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import com.example.service.AudioRecordVoiceActivityDetector
import com.kyawgyi.ir.apikey.ApiKeyStatus
import com.kyawgyi.ir.apikey.ApiKeyTier
import com.kyawgyi.ir.apikey.ApiKeyValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 Real JVM Unit Tests covering:
 * (a) MyanmarVoiceParser — representative Burmese commands & the 4 safety cases from the audit
 *     (destructive/irreversible commands that must require confirmation or be refused).
 * (b) API-key validation, masking and format round-trip logic testable without Android framework.
 * (c) Wake-word state-machine transitions testable without the Android framework.
 */
class RealP0ShipBlockerUnitTest {

  // =========================================================================
  // (a) MyanmarVoiceParser: Representative Commands & 4 Safety Cases
  // =========================================================================

  @Test
  fun testMyanmarVoiceParser_RepresentativeCommands() {
    // 1. Fan Light Toggle
    val fanLight = MyanmarVoiceParser.parse("ပန်ကာ မီးဖွင့်ပါ")
    assertTrue("Should be KennedeFanCommand", fanLight is ParsedIntent.KennedeFanCommand)
    assertEquals("FAN_LIGHT_ON", (fanLight as ParsedIntent.KennedeFanCommand).action)
    assertEquals("light", fanLight.buttonKey)

    // 2. Fan Speed Control
    val fanSpeed = MyanmarVoiceParser.parse("ပန်ကာ အရှိန်မြှင့်ပါ")
    assertTrue("Should be KennedeFanCommand", fanSpeed is ParsedIntent.KennedeFanCommand)
    assertEquals("FAN_SPEED_UP", (fanSpeed as ParsedIntent.KennedeFanCommand).action)

    // 3. EcoFlow AC Port
    val ecoAc = MyanmarVoiceParser.parse("အေစီ ဖွင့်ပါ")
    assertTrue("Should be EcoFlowPortToggle", ecoAc is ParsedIntent.EcoFlowPortToggle)
    val acToggle = ecoAc as ParsedIntent.EcoFlowPortToggle
    assertEquals("AC", acToggle.port)
    assertTrue(acToggle.enabled)

    // 4. Contact Calling
    val call = MyanmarVoiceParser.parse("မေမေ့ဆီ ဖုန်းခေါ်ပေးပါ")
    assertTrue("Should be CallContact", call is ParsedIntent.CallContact)
    assertEquals("မေမေ", (call as ParsedIntent.CallContact).target)

    // 5. System App Launch
    val viber = MyanmarVoiceParser.parse("ဗိုက်ဘာ ဖွင့်ပါ")
    assertTrue("Should be OpenApp", viber is ParsedIntent.OpenApp)
    assertEquals("Viber", (viber as ParsedIntent.OpenApp).appName)
  }

  @Test
  fun testSafetyCase1_FactoryResetWipeData_MustBeRefused() {
    // Audit Case 1: Factory Reset / Wipe Data -> Must be Refused / SafetyBlocked
    val c1 = MyanmarVoiceParser.parse("စက်ရုံထုတ်အတိုင်း ပြန်လုပ်ပါ")
    assertTrue("Factory reset must be SafetyBlocked", c1 is ParsedIntent.SafetyBlocked)
    val blocked1 = c1 as ParsedIntent.SafetyBlocked
    assertTrue(blocked1.reasonBurmese.contains("ပိတ်ပင်ထားပါသည်") || blocked1.reasonBurmese.contains("ခွင့်မပြု"))

    val c2 = MyanmarVoiceParser.parse("ဖုန်းအကုန်ဖျက်ပါ")
    assertTrue("Full phone wipe must be SafetyBlocked", c2 is ParsedIntent.SafetyBlocked)

    val c3 = MyanmarVoiceParser.parse("wipe data factory reset")
    assertTrue("Wipe data must be SafetyBlocked", c3 is ParsedIntent.SafetyBlocked)
  }

  @Test
  fun testSafetyCase2_PermanentDatabaseDeletion_MustRequireConfirmation() {
    // Audit Case 2: Permanent Database deletion -> Must require explicit confirmation
    val res1 = MyanmarVoiceParser.parse("ဒေတာဘေ့စ် ဖျက်ပါ")
    assertTrue("Database drop must require confirmation", res1 is ParsedIntent.RequiresConfirmation)
    val conf1 = res1 as ParsedIntent.RequiresConfirmation
    assertEquals("CLEAR_ALL_DATA", conf1.action)
    assertTrue(conf1.promptBurmese.contains("အတည်ပြုရန် လိုအပ်ပါသည်"))

    val res2 = MyanmarVoiceParser.parse("မှတ်တမ်းအားလုံး ဖျက်ပါ")
    assertTrue("Clear records must require confirmation", res2 is ParsedIntent.RequiresConfirmation)
    val conf2 = res2 as ParsedIntent.RequiresConfirmation
    assertEquals("CLEAR_ALL_DATA", conf2.action)
  }

  @Test
  fun testSafetyCase3_FinancialMoneyTransfer_MustBeRefused() {
    // Audit Case 3: Financial money transfer -> Must be Refused / SafetyBlocked
    val transfer1 = MyanmarVoiceParser.parse("ငွေလွှဲပေးပါ")
    assertTrue("Money transfer must be SafetyBlocked", transfer1 is ParsedIntent.SafetyBlocked)
    val blocked1 = transfer1 as ParsedIntent.SafetyBlocked
    assertTrue(blocked1.reasonBurmese.contains("ဘဏ္ဍာရေး") && blocked1.reasonBurmese.contains("ငြင်းပယ်ပါသည်"))

    val transfer2 = MyanmarVoiceParser.parse("kpay ဖြင့် ပိုက်ဆံလွှဲပါ")
    assertTrue("KPay transfer must be SafetyBlocked", transfer2 is ParsedIntent.SafetyBlocked)

    val transfer3 = MyanmarVoiceParser.parse("wave pay ဖြင့် ငွေလွှဲပါ")
    assertTrue("WavePay transfer must be SafetyBlocked", transfer3 is ParsedIntent.SafetyBlocked)
  }

  @Test
  fun testSafetyCase4_DeleteApiKeyTokens_MustRequireConfirmation() {
    // Audit Case 4: Delete system API keys -> Must require confirmation
    val delKey1 = MyanmarVoiceParser.parse("api key အားလုံး ဖျက်ပါ")
    assertTrue("API key deletion must require confirmation", delKey1 is ParsedIntent.RequiresConfirmation)
    val conf1 = delKey1 as ParsedIntent.RequiresConfirmation
    assertEquals("DELETE_API_KEYS", conf1.action)
    assertTrue(conf1.promptBurmese.contains("ပြန်လည်ပြင်ဆင်၍ မရနိုင်ပါ"))

    val delKey2 = MyanmarVoiceParser.parse("api သော့အားလုံး ဖျက်ပါ")
    assertTrue("API key deletion in Burmese must require confirmation", delKey2 is ParsedIntent.RequiresConfirmation)
    assertEquals("DELETE_API_KEYS", (delKey2 as ParsedIntent.RequiresConfirmation).action)
  }

  // =========================================================================
  // (b) API-Key Storage Round-Trip & Format Validation (Non-Android JVM)
  // =========================================================================

  @Test
  fun testApiKeyValidator_FormatRules() {
    // 1. Empty or blank
    val resEmpty = ApiKeyValidator.validateKeyFormat("")
    assertTrue(resEmpty is ApiKeyValidator.KeyValidationResult.Invalid)

    // 2. Whitespace error
    val resWhitespace = ApiKeyValidator.validateKeyFormat("AIzaSyB123 4567890123")
    assertTrue(resWhitespace is ApiKeyValidator.KeyValidationResult.Invalid)

    // 3. Too short (< 16 chars)
    val resShort = ApiKeyValidator.validateKeyFormat("AIzaSyShort")
    assertTrue(resShort is ApiKeyValidator.KeyValidationResult.Invalid)

    // 4. Invalid characters (e.g. symbols like #, $, %)
    val resInvalidChars = ApiKeyValidator.validateKeyFormat("AIzaSyTestKey_123$#@*!")
    assertTrue(resInvalidChars is ApiKeyValidator.KeyValidationResult.Invalid)

    // 5. Valid key
    val validSecret = "AIzaSyB_TestKey_Valid_Roundtrip_12345"
    val resValid = ApiKeyValidator.validateKeyFormat(validSecret)
    assertTrue(resValid is ApiKeyValidator.KeyValidationResult.Valid)
    assertEquals(validSecret, (resValid as ApiKeyValidator.KeyValidationResult.Valid).normalizedKey)
  }

  @Test
  fun testApiKeyValidator_MaskingAndSerializationRoundTrip() {
    val rawSecret = "AIzaSyD_Secret_Key_For_AiStudio_9981"
    val masked = ApiKeyValidator.maskKey(rawSecret)

    // Assert secret is properly masked for UI display (•••• + last 4 only)
    assertEquals("••••9981", masked)
    assertFalse("Masked key must not reveal middle secret", masked.contains("Secret_Key"))

    // Serialize pure ManagedApiKey model
    val record = ApiKeyValidator.serializeKeyRecord(
      id = "key_test_01",
      label = "Primary Test Key",
      rawKey = rawSecret,
      tier = ApiKeyTier.PRO_TIER,
      maxQuota = 2500
    )

    assertEquals("key_test_01", record.id)
    assertEquals("Primary Test Key", record.label)
    assertEquals(masked, record.maskedKey)
    assertEquals(ApiKeyTier.PRO_TIER, record.tier)
    assertEquals(2500, record.maxQuota)
    assertEquals(0, record.usedQuota)
    assertEquals(ApiKeyStatus.HEALTHY_ACTIVE, record.status)
    assertTrue("Fresh key should be usable", record.canBeUsed)
    assertEquals(0f, record.usagePercentage, 0.001f)
    assertEquals(2500, record.remainingQuota)
  }

  // =========================================================================
  // (c) Wake-Word State-Machine Transitions (Non-Android JVM)
  // =========================================================================

  @Test
  fun testWakeWordStateMachine_Transitions_IdleToDetected() {
    val stateMachine = AudioRecordVoiceActivityDetector.WakeWordStateMachine()
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.IDLE, stateMachine.currentState)

    // Ensure initial timestamp is well past any 0L cooldown
    var simulatedTime = 5000L

    // Frame 1: Non-speech noise -> Stays in IDLE
    val s1 = stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = false,
      spectralTilt = 0.5f,
      snr = 2.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.IDLE, s1)
    assertEquals(null, stateMachine.detectedEvent)

    // Frame 2: First syllable "Kyaw" detected (speech band, good spectral tilt, high SNR)
    simulatedTime += 32
    val s2 = stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = true,
      spectralTilt = 1.0f,
      snr = 15.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.SYLLABLE_KYAW, s2)
    assertEquals(null, stateMachine.detectedEvent)

    // Frame 3: Inter-syllable dip arrives within valid duration (200ms later, low SNR / high tilt dip)
    simulatedTime += 200
    val s3 = stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = true,
      spectralTilt = 2.0f,
      snr = 3.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.INTER_SYLLABLE_DIP, s3)
    assertEquals(null, stateMachine.detectedEvent)

    // Frame 4: Second syllable "Gyi" arrives within dip window (100ms later)
    simulatedTime += 100
    val s4 = stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = true,
      spectralTilt = 1.2f,
      snr = 16.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.SYLLABLE_GYI, s4)

    // Frame 5: Second syllable sustains (250ms later) -> Triggers detection & transitions back to IDLE
    simulatedTime += 250
    val s5 = stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = true,
      spectralTilt = 1.0f,
      snr = 18.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.IDLE, s5)
    assertNotNull("Wake word event must be fired", stateMachine.detectedEvent)
    val (keyword, confidence) = stateMachine.detectedEvent!!
    assertTrue("Keyword must identify Kyaw Gyi", keyword.contains("Kyaw Gyi") || keyword.contains("ကျော်ကြီး"))
    assertTrue("Confidence must exceed threshold", confidence >= 0.70f)
  }

  @Test
  fun testWakeWordStateMachine_Timeout_ResetsToIdle() {
    val stateMachine = AudioRecordVoiceActivityDetector.WakeWordStateMachine()
    var simulatedTime = 5000L

    // Enter first syllable
    stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = true,
      spectralTilt = 1.0f,
      snr = 15.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.SYLLABLE_KYAW, stateMachine.currentState)

    // If syllable lasts > 500ms without dip -> Aborts back to IDLE
    simulatedTime += 600
    val sTimeout = stateMachine.processFrame(
      now = simulatedTime,
      isSpeechBand = true,
      spectralTilt = 1.0f,
      snr = 15.0f
    )
    assertEquals(AudioRecordVoiceActivityDetector.AcousticState.IDLE, sTimeout)
    assertEquals(null, stateMachine.detectedEvent)
  }
}
