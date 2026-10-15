package com.example

import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import com.example.service.AudioRecordWakeWordDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 Real JVM Unit Tests covering:
 * D3: Voice-activity & energy trigger state machine and honest relabeling assertions.
 * D10: NLU safety:
 *   - Simple confidence scores (0.0-1.0) on parse results.
 *   - Low-confidence matches (< 0.5) return 'Clarify' result asking user to repeat/clarify.
 *   - Argument validation: volume/brightness must be within 0-100; out-of-range rejected with clear message.
 *   - Risky actions (phone calls at minimum) require explicit user confirmation (RequiresConfirmation pattern).
 */
class RealP1UnitTest {

  // =========================================================================
  // D10 (NLU Safety): Confidence Scores & Low Confidence Clarify
  // =========================================================================

  @Test
  fun testConfidenceScores_PresentOnParseResults() {
    // Exact high-confidence fan command
    val fanResult = MyanmarVoiceParser.parseResult("ပန်ကာ မီးဖွင့်ပါ")
    assertTrue("Fan command must have high confidence >= 0.8", fanResult.confidence >= 0.8f)
    assertTrue("Intent must be KennedeFanCommand", fanResult.intent is ParsedIntent.KennedeFanCommand)

    // EcoFlow Port command
    val ecoResult = MyanmarVoiceParser.parseResult("အေစီ ဖွင့်ပါ")
    assertTrue("EcoFlow command confidence >= 0.8", ecoResult.confidence >= 0.8f)
    assertTrue("Intent must be EcoFlowPortToggle", ecoResult.intent is ParsedIntent.EcoFlowPortToggle)

    // General search with valid query
    val searchResult = MyanmarVoiceParser.parseResult("နေပြည်တော် ရာသီဥတု ရှာပါ")
    assertTrue("Search confidence should be in 0.5..1.0", searchResult.confidence in 0.5f..1.0f)
  }

  @Test
  fun testLowConfidence_ReturnsClarifyResult() {
    // Ambiguous noise / unrecognized syllables
    val clarify1 = MyanmarVoiceParser.parse("အင်း ဟိုဒင်း")
    assertTrue("Unrecognized input must return Clarify", clarify1 is ParsedIntent.Clarify)
    assertTrue("Clarify confidence must be < 0.5 threshold", clarify1.confidence < 0.5f)
    val prompt1 = (clarify1 as ParsedIntent.Clarify).promptBurmese
    assertTrue("Prompt must ask user to repeat/clarify", prompt1.contains("ပြန်လည်ပြောကြား") || prompt1.contains("သဲသဲကွဲကွဲ"))

    // Blank input
    val clarifyBlank = MyanmarVoiceParser.parse("   ")
    assertTrue("Blank input must return Clarify", clarifyBlank is ParsedIntent.Clarify)
    assertEquals(0.0f, clarifyBlank.confidence, 0.001f)

    // Random non-intent input
    val result = MyanmarVoiceParser.parseResult("အာ ဟုတ်တယ် ဘာလဲ")
    assertTrue("Ambiguous murmur must result in Clarify", result.intent is ParsedIntent.Clarify)
    assertTrue("Confidence must score below 0.5 threshold", result.confidence < 0.5f)
  }

  // =========================================================================
  // D10 (NLU Safety): Argument Validation (Volume & Brightness 0-100)
  // =========================================================================

  @Test
  fun testVolumeArgumentValidation_ValidRangeAccepted() {
    // Valid volume 50%
    val vol50 = MyanmarVoiceParser.parse("အသံ 50 ရာခိုင်နှုန်း ထားပါ")
    assertTrue("Valid volume 50 must be SetVolume", vol50 is ParsedIntent.SetVolume)
    assertEquals(50, (vol50 as ParsedIntent.SetVolume).percent)

    // Valid volume 100%
    val vol100 = MyanmarVoiceParser.parse("အသံ 100% ချိန်ပေးပါ")
    assertTrue("Valid volume 100 must be SetVolume", vol100 is ParsedIntent.SetVolume)
    assertEquals(100, (vol100 as ParsedIntent.SetVolume).percent)

    // Valid volume 0%
    val vol0 = MyanmarVoiceParser.parse("အသံ 0% ထားပါ")
    assertTrue("Valid volume 0 must be SetVolume", vol0 is ParsedIntent.SetVolume)
    assertEquals(0, (vol0 as ParsedIntent.SetVolume).percent)
  }

  @Test
  fun testVolumeArgumentValidation_OutOfRangeRejected() {
    // Out of range: Volume 150% (rejected with clear message)
    val vol150 = MyanmarVoiceParser.parse("အသံ 150 ရာခိုင်နှုန်း ထားပါ")
    assertTrue("Volume 150% must be rejected via SafetyBlocked", vol150 is ParsedIntent.SafetyBlocked)
    val reason = (vol150 as ParsedIntent.SafetyBlocked).reasonBurmese
    assertTrue("Must explain valid volume is 0-100", reason.contains("၀ မှ ၁၀၀") || reason.contains("0 မှ 100") || reason.contains("သတ်မှတ်ဘောင်"))

    // Out of range: Volume 200%
    val vol200 = MyanmarVoiceParser.parse("volume 200% set")
    assertTrue("Volume 200% must be rejected via SafetyBlocked", vol200 is ParsedIntent.SafetyBlocked)
  }

  @Test
  fun testBrightnessArgumentValidation_ValidRangeAccepted() {
    // Valid brightness 75%
    val bright75 = MyanmarVoiceParser.parse("မျက်နှာပြင် အလင်းရောင် 75 ရာခိုင်နှုန်း ထားပါ")
    assertTrue("Valid brightness 75 must be SetBrightness", bright75 is ParsedIntent.SetBrightness)
    assertEquals(75, (bright75 as ParsedIntent.SetBrightness).percent)

    // Valid brightness 10%
    val bright10 = MyanmarVoiceParser.parse("brightness 10% set")
    assertTrue("Valid brightness 10 must be SetBrightness", bright10 is ParsedIntent.SetBrightness)
    assertEquals(10, (bright10 as ParsedIntent.SetBrightness).percent)
  }

  @Test
  fun testBrightnessArgumentValidation_OutOfRangeRejected() {
    // Out of range: Brightness 120%
    val bright120 = MyanmarVoiceParser.parse("မျက်နှာပြင် အလင်း 120 ရာခိုင်နှုန်း ထားပါ")
    assertTrue("Brightness 120% must be rejected via SafetyBlocked", bright120 is ParsedIntent.SafetyBlocked)
    val reason = (bright120 as ParsedIntent.SafetyBlocked).reasonBurmese
    assertTrue("Must explain valid brightness is 0-100", reason.contains("၀ မှ ၁၀၀") || reason.contains("သတ်မှတ်ဘောင်"))
  }

  // =========================================================================
  // D10 (NLU Safety): Risky Actions (Phone Calls) Require User Confirmation
  // =========================================================================

  @Test
  fun testRiskyAction_PhoneCallsRequireExplicitConfirmation() {
    // 1. Calling by relationship tag
    val callMom = MyanmarVoiceParser.parse("မေမေ့ဆီ ဖုန်းခေါ်ပေးပါ")
    assertTrue("CallContact must implement RequiresConfirmation", callMom is ParsedIntent.RequiresConfirmation)
    val req1 = callMom as ParsedIntent.RequiresConfirmation
    assertEquals("CALL_CONTACT:မေမေ", req1.action)
    assertTrue("Prompt must ask for confirmation", req1.promptBurmese.contains("အတည်ပြုရန် လိုအပ်ပါသည်"))

    // 2. Calling friend
    val callFriend = MyanmarVoiceParser.parse("သူငယ်ချင်းဆီ ဖုန်းဆက်ပါ")
    assertTrue("Call friend must require confirmation", callFriend is ParsedIntent.RequiresConfirmation)
    assertTrue((callFriend as ParsedIntent.RequiresConfirmation).promptBurmese.contains("အတည်ပြုရန်"))

    // 3. Calling direct phone number digits
    val callNumber = MyanmarVoiceParser.parse("0912345678 ဆီ ဖုန်းခေါ်ပါ")
    assertTrue("Call digits must require confirmation", callNumber is ParsedIntent.RequiresConfirmation)
    val req3 = callNumber as ParsedIntent.RequiresConfirmation
    assertEquals("CALL_CONTACT:0912345678", req3.action)
  }

  // =========================================================================
  // D3 (Honest Wake-Word): Energy Trigger Conservatism & State Machine
  // =========================================================================

  @Test
  fun testVoiceActivityStateMachine_HonestTriggerRelabel() {
    val stateMachine = AudioRecordWakeWordDetector.WakeWordStateMachine()
    assertEquals(AudioRecordWakeWordDetector.AcousticState.IDLE, stateMachine.currentState)

    var simulatedTime = 10000L

    // S1: Syllable entrance
    stateMachine.processFrame(simulatedTime, isSpeechBand = true, spectralTilt = 1.0f, snr = 15.0f)
    assertEquals(AudioRecordWakeWordDetector.AcousticState.SYLLABLE_KYAW, stateMachine.currentState)

    // Dip
    simulatedTime += 200
    stateMachine.processFrame(simulatedTime, isSpeechBand = true, spectralTilt = 2.0f, snr = 3.0f)
    assertEquals(AudioRecordWakeWordDetector.AcousticState.INTER_SYLLABLE_DIP, stateMachine.currentState)

    // S2
    simulatedTime += 100
    stateMachine.processFrame(simulatedTime, isSpeechBand = true, spectralTilt = 1.2f, snr = 16.0f)
    assertEquals(AudioRecordWakeWordDetector.AcousticState.SYLLABLE_GYI, stateMachine.currentState)

    // Second syllable sustained trigger
    simulatedTime += 250
    stateMachine.processFrame(simulatedTime, isSpeechBand = true, spectralTilt = 1.0f, snr = 18.0f)
    assertEquals(AudioRecordWakeWordDetector.AcousticState.IDLE, stateMachine.currentState)

    assertNotNull("Trigger event must fire", stateMachine.detectedEvent)
    val (keyword, confidence) = stateMachine.detectedEvent!!
    assertTrue("Keyword describes Kyaw Gyi voice activity trigger", keyword.contains("Kyaw Gyi") || keyword.contains("ကျော်ကြီး"))
    assertTrue("Confidence exceeds threshold", confidence >= 0.70f)
  }
}
