package com.example.nlu

sealed class ParsedIntent(open val confidence: Float = 1.0f) {
  // EcoFlow Intents
  data class EcoFlowPower(val enabled: Boolean, override val confidence: Float = 0.95f) : ParsedIntent(confidence)
  data class EcoFlowPortToggle(val port: String, val enabled: Boolean, override val confidence: Float = 0.95f) : ParsedIntent(confidence)
  data class EcoFlowChargingSpeed(val watts: Int, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class EcoFlowXBoost(val enabled: Boolean, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class EcoFlowBeep(val enabled: Boolean, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class EcoFlowAcTimeout(val hours: Int, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class EcoFlowQueryStatus(val parameter: String, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  object EcoFlowSafetyHalt : ParsedIntent(1.0f)

  // App & System Intents
  data class KennedeFanCommand(val action: String, val buttonKey: String, val description: String, override val confidence: Float = 0.95f) : ParsedIntent(confidence)
  data class InspectIrWaveform(val buttonKey: String? = null, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class KennedeFanAutomationIntent(val action: String, val minutes: Int = 0, val param: String = "", override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class OpenApp(val appName: String, val urlScheme: String? = null, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class CloseApp(val appName: String, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  object GoHome : ParsedIntent(0.95f)
  data class ToggleBackgroundListening(val enabled: Boolean, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  object OpenTtsSettings : ParsedIntent(0.90f)
  object OpenPermissionsSettings : ParsedIntent(0.90f)
  object QueryLocation : ParsedIntent(0.90f)
  data class ToggleSystemSetting(val settingName: String, val enabled: Boolean, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class SetVolume(val percent: Int, override val confidence: Float = 0.95f) : ParsedIntent(confidence)
  data class SetBrightness(val percent: Int, override val confidence: Float = 0.95f) : ParsedIntent(confidence)

  // Safety & High-Risk Confirmation Intents (Audit Cases)
  data class SafetyBlocked(val rawCommand: String, val reasonBurmese: String, override val confidence: Float = 1.0f) : ParsedIntent(confidence)

  open class RequiresConfirmation(
    open val action: String,
    open val promptBurmese: String,
    override val confidence: Float = 0.95f
  ) : ParsedIntent(confidence) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is RequiresConfirmation) return false
      return action == other.action && promptBurmese == other.promptBurmese
    }
    override fun hashCode(): Int = 31 * action.hashCode() + promptBurmese.hashCode()
    override fun toString(): String = "RequiresConfirmation(action='$action', promptBurmese='$promptBurmese', confidence=$confidence)"
  }

  // Risky action: phone calls require explicit user confirmation (reusing RequiresConfirmation pattern)
  class CallContact(
    val target: String,
    override val confidence: Float = 0.90f
  ) : RequiresConfirmation(
    action = "CALL_CONTACT:$target",
    promptBurmese = "\"$target\" ထံသို့ ဖုန်းခေါ်ဆိုရန် သေချာပါသလား။ အတည်ပြုရန် လိုအပ်ပါသည်။",
    confidence = confidence
  ) {
    override fun equals(other: Any?): Boolean = other is CallContact && target == other.target
    override fun hashCode(): Int = target.hashCode()
    override fun toString(): String = "CallContact(target='$target', confidence=$confidence)"
  }

  data class GeneralSearch(val query: String, override val confidence: Float = 0.70f) : ParsedIntent(confidence)
  data class CustomResponse(val responseText: String, override val confidence: Float = 0.85f) : ParsedIntent(confidence)

  // AI Angel, Music & Ingestion Intents
  data class PlayMusic(val query: String, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  data class PlayCandidateIndex(val index: Int, override val confidence: Float = 0.95f) : ParsedIntent(confidence)
  object PlayNextMusic : ParsedIntent(0.95f)
  object PlayPreviousMusic : ParsedIntent(0.95f)
  object PauseMusic : ParsedIntent(0.95f)
  object ResumeMusic : ParsedIntent(0.95f)
  object PlayLatestMusic : ParsedIntent(0.95f)
  object IngestMusicIntent : ParsedIntent(0.90f)
  data class IngestAssetIntent(val assetType: String, override val confidence: Float = 0.90f) : ParsedIntent(confidence)
  object OpenAngelChatbox : ParsedIntent(0.95f)
  object OpenMusicLibrary : ParsedIntent(0.95f)
  object OpenAssetDiagnostics : ParsedIntent(0.95f)

  // Clarify intent for low confidence (< 0.5 threshold)
  data class Clarify(
    val promptBurmese: String = "သဲသဲကွဲကွဲ မကြားရပါခင်ဗျာ။ ကျေးဇူးပြု၍ ပြန်လည်ပြောကြားပေးပါ",
    val rawText: String = "",
    override val confidence: Float = 0.20f
  ) : ParsedIntent(confidence)

  data class Unknown(val rawText: String, override val confidence: Float = 0.0f) : ParsedIntent(confidence)
}

data class ParseResult(val intent: ParsedIntent, val confidence: Float)

object MyanmarVoiceParser {

  fun parse(rawInput: String, lastActiveContext: String? = null): ParsedIntent {
    if (rawInput.isBlank()) {
      return ParsedIntent.Clarify(
        promptBurmese = "သဲသဲကွဲကွဲ မကြားရပါခင်ဗျာ။ ကျေးဇူးပြု၍ ပြန်လည်ပြောကြားပေးပါ",
        rawText = rawInput,
        confidence = 0.0f
      )
    }

    // ဒေသစကားသံ၊ အသံဝဲနှင့် စာလုံးပေါင်း အမှားအယွင်းများကို Context ဖြင့် ညှိယူခြင်း
    val clean = MyanmarDialectAndContextNormalizer.disambiguateWithContext(rawInput, lastActiveContext).lowercase()
    if (clean.isBlank()) {
      return ParsedIntent.Clarify(
        promptBurmese = "သဲသဲကွဲကွဲ မကြားရပါခင်ဗျာ။ ကျေးဇူးပြု၍ ပြန်လည်ပြောကြားပေးပါ",
        rawText = rawInput,
        confidence = 0.0f
      )
    }

    // 00. Safety Guard & Destructive / Irreversible Commands Check (Audit Safety Cases)
    // Case 1: Factory Reset / Wipe Data (Blocked / Refused)
    if (clean.contains("စက်ရုံထုတ်အတိုင်း ပြန်လုပ်") || clean.contains("factory reset") ||
      clean.contains("ဖုန်းအကုန်ဖျက်") || clean.contains("wipe data") ||
      (clean.contains("ဒေတာအားလုံး") && clean.contains("ဖျက်"))
    ) {
      return ParsedIntent.SafetyBlocked(
        rawCommand = rawInput,
        reasonBurmese = "လုံခြုံရေးအရ စနစ်ဒေတာအားလုံး အပြီးတိုင်ဖျက်ဆီးခြင်း (Factory Reset) အမိန့်ကို အသံဖြင့် တိုက်ရိုက်လုပ်ဆောင်ခွင့် ပိတ်ပင်ထားပါသည်"
      )
    }

    // Case 2: Permanent Database / App Data Deletion (Requires explicit user confirmation)
    if (clean.contains("ဒေတာဘေ့စ် ဖျက်") || clean.contains("database ဖျက်") ||
      clean.contains("အချက်အလက်အားလုံး ဖျက်") || clean.contains("မှတ်တမ်းအားလုံး ဖျက်") ||
      clean.contains("clear all database")
    ) {
      return ParsedIntent.RequiresConfirmation(
        action = "CLEAR_ALL_DATA",
        promptBurmese = "မှတ်တမ်းနှင့် ဒေတာအားလုံးကို အပြီးတိုင်ဖျက်ရန် သေချာပါသလား။ အတည်ပြုရန် လိုအပ်ပါသည်။"
      )
    }

    // Case 3: Emergency Financial / Money Transfer (Refused / Blocked via Voice)
    if (clean.contains("ငွေလွှဲပေးပါ") || clean.contains("ငွေထုတ်ပါ") ||
      clean.contains("ပိုက်ဆံလွှဲ") || clean.contains("transfer money") ||
      (clean.contains("kpay") && (clean.contains("လွှဲ") || clean.contains("send money"))) ||
      (clean.contains("wave") && (clean.contains("လွှဲ") || clean.contains("send money")))
    ) {
      return ParsedIntent.SafetyBlocked(
        rawCommand = rawInput,
        reasonBurmese = "ဘဏ္ဍာရေးဆိုင်ရာ ငွေလွှဲပြောင်းမှုများကို အသံဖြင့် တိုက်ရိုက်ခွင့်မပြုပါ။ ငွေပေးချေမှု အက်ပ်ထဲတွင် ကိုယ်တိုင် ဆောင်ရွက်ပါရန် ငြင်းပယ်ပါသည်"
      )
    }

    // Case 4: Delete System API Keys / Security Tokens (Requires confirmation)
    if ((clean.contains("api key") || clean.contains("သော့")) && (clean.contains("ဖျက်") || clean.contains("delete key")) ||
      clean.contains("api သော့အားလုံး ဖျက်")
    ) {
      return ParsedIntent.RequiresConfirmation(
        action = "DELETE_API_KEYS",
        promptBurmese = "လုံခြုံရေး API သော့အားလုံးကို ဖျက်ဆီးရန် သေချာပါသလား။ ဤလုပ်ဆောင်ချက်သည် ပြန်လည်ပြင်ဆင်၍ မရနိုင်ပါ။"
      )
    }

    // Argument Validation for Tool Calls: Volume & Brightness (0-100 Range Check)
    if (clean.contains("အသံ") || clean.contains("volume") || clean.contains("vol")) {
      val digits = Regex("\\d+").find(clean)?.value?.toIntOrNull()
      if (digits != null && (clean.contains("ရာခိုင်နှုန်း") || clean.contains("%") || clean.contains("ထား") || clean.contains("set") || clean.contains("ချိန်"))) {
        if (digits !in 0..100) {
          return ParsedIntent.SafetyBlocked(
            rawCommand = rawInput,
            reasonBurmese = "အသံပမာဏ (Volume) သည် ၀ မှ ၁၀၀ ကြားသာ ဖြစ်ရပါမည် (လက်ရှိတောင်းဆိုချက်: $digits%)။ သတ်မှတ်ဘောင် ကျော်လွန်သဖြင့် ငြင်းပယ်ပါသည်"
          )
        }
        return ParsedIntent.SetVolume(digits)
      }
    }

    if (clean.contains("အလင်း") || clean.contains("brightness") || clean.contains("မီးရောင်")) {
      val digits = Regex("\\d+").find(clean)?.value?.toIntOrNull()
      if (digits != null && (clean.contains("ရာခိုင်နှုန်း") || clean.contains("%") || clean.contains("ထား") || clean.contains("set") || clean.contains("ချိန်"))) {
        if (digits !in 0..100) {
          return ParsedIntent.SafetyBlocked(
            rawCommand = rawInput,
            reasonBurmese = "မျက်နှာပြင် အလင်းရောင် (Brightness) သည် ၀ မှ ၁၀၀ ကြားသာ ဖြစ်ရပါမည် (လက်ရှိတောင်းဆိုချက်: $digits%)။ သတ်မှတ်ဘောင် ကျော်လွန်သဖြင့် ငြင်းပယ်ပါသည်"
          )
        }
        return ParsedIntent.SetBrightness(digits)
      }
    }

    // 0. AI Angel Chatbox & Diagnostics Navigation
    if (clean.contains("angel") || clean.contains("chatbox") || clean.contains("အိန်ဂျယ်") || clean.contains("စကားပြော")) {
      return ParsedIntent.OpenAngelChatbox
    }
    if (clean.contains("သီချင်းစာကြည့်တိုက်") || clean.contains("music library") || clean.contains("သီချင်းများ")) {
      return ParsedIntent.OpenMusicLibrary
    }
    if (clean.contains("asset diagnostics") || clean.contains("ဖိုင်စစ်ဆေး") || clean.contains("ဒေတာစစ်ဆေး")) {
      return ParsedIntent.OpenAssetDiagnostics
    }

    // 1. Music Ingestion & Asset Ingestion Commands
    if (clean.contains("ဒီသီချင်းကို သိမ်းပါ") || clean.contains("ဒီ mp3 ကို music ထဲထည့်ပါ") || clean.contains("သီချင်းသိမ်း") || clean.contains("mp3 သိမ်း")) {
      return ParsedIntent.IngestMusicIntent
    }
    if (clean.contains("ဒီပုံကို project assets ထဲထည့်ပါ") || clean.contains("ပုံသိမ်းပါ")) {
      return ParsedIntent.IngestAssetIntent("IMAGE")
    }
    if (clean.contains("ဒီ json ကို configuration အဖြစ်ထည့်ပါ") || clean.contains("json သိမ်းပါ") || clean.contains("config ထည့်ပါ")) {
      return ParsedIntent.IngestAssetIntent("PROJECT_CONFIG")
    }

    // 2. Music Playback Controls
    if (clean.contains("နောက်ဆုံးတင်ထားတဲ့ သီချင်းဖွင့်ပါ") || clean.contains("နောက်ဆုံးသီချင်းဖွင့်")) {
      return ParsedIntent.PlayLatestMusic
    }
    if (clean.contains("နောက်သီချင်း") || clean.contains("next song") || clean.contains("next track")) {
      return ParsedIntent.PlayNextMusic
    }
    if (clean.contains("ရှေ့သီချင်း") || clean.contains("previous song") || clean.contains("prev track")) {
      return ParsedIntent.PlayPreviousMusic
    }
    if (clean.contains("သီချင်းရပ်") || clean.contains("သီချင်း ရပ်") || clean.contains("သီချင်းပိတ်") || clean.contains("သီချင်း ပိတ်") || clean.contains("stop music") || clean.contains("pause music")) {
      return ParsedIntent.PauseMusic
    }
    if (clean.contains("သီချင်းဆက်ဖွင့်") || clean.contains("resume music")) {
      return ParsedIntent.ResumeMusic
    }

    // Candidate selection for disambiguation
    if (clean.contains("ပထမတစ်ပုဒ်") || clean == "1" || clean == "ပထမ") {
      return ParsedIntent.PlayCandidateIndex(0)
    }
    if (clean.contains("ဒုတိယတစ်ပုဒ်") || clean == "2" || clean == "ဒုတိယ") {
      return ParsedIntent.PlayCandidateIndex(1)
    }
    if (clean.contains("တတိယတစ်ပုဒ်") || clean == "3" || clean == "တတိယ") {
      return ParsedIntent.PlayCandidateIndex(2)
    }

    // Music Search & Playback e.g. "လေးဖြူသီချင်းဖွင့်ပါ", "သီချင်းဖွင့်ပါ", "play lay phyu"
    if (clean.contains("သီချင်း") || (clean.contains("ဖွင့်") && (clean.contains("လေးဖြူ") || clean.contains("မျိုးကြီး") || clean.contains("ဇော်ဝင်းထွဋ်") || clean.contains("song")))) {
      val query = clean.replace("သီချင်းဖွင့်ပေးပါ", "")
        .replace("သီချင်းဖွင့်ပါ", "")
        .replace("သီချင်းဖွင့်", "")
        .replace("သီချင်း", "")
        .replace("ဖွင့်ပါ", "")
        .replace("ဖွင့်", "")
        .replace("play", "")
        .replace("song", "")
        .trim()
      return ParsedIntent.PlayMusic(query.ifEmpty { "လေးဖြူ" })
    }

    // 0a. KENNEDE Fan Smart Automation, Timer Schedules & EcoFlow Link (အချက် ၆)
    if (clean.contains("အချိန်မှတ်") || clean.contains("timer") || clean.contains("sleep timer") ||
      (clean.contains("ပန်ကာ") && (clean.contains("မိနစ်") || clean.contains("နာရီ")))
    ) {
      if (clean.contains("ဖျက်") || clean.contains("ရပ်") || clean.contains("မမှတ်") || clean.contains("cancel") || clean.contains("stop")) {
        return ParsedIntent.KennedeFanAutomationIntent(action = "CANCEL_TIMER")
      }
      val minutes = when {
        clean.contains("၁၅") || clean.contains("15") -> 15
        clean.contains("၃၀") || clean.contains("30") -> 30
        clean.contains("၄၅") || clean.contains("45") -> 45
        clean.contains("၆၀") || clean.contains("60") || clean.contains("၁ နာရီ") || clean.contains("1 နာရီ") || clean.contains("တစ်နာရီ") || clean.contains("1 hr") -> 60
        clean.contains("၁၂၀") || clean.contains("120") || clean.contains("၂ နာရီ") || clean.contains("2 နာရီ") || clean.contains("နှစ်နာရီ") || clean.contains("2 hr") -> 120
        clean.contains("၁၀") || clean.contains("10") -> 10
        clean.contains("၅") || clean.contains("5") -> 5
        else -> 30 // default 30 mins
      }
      return ParsedIntent.KennedeFanAutomationIntent(action = "SET_TIMER", minutes = minutes)
    }

    if (clean.contains("ဘက်ထရီချွေတာ") || clean.contains("စွမ်းအင်ချွေတာ") || clean.contains("power link") ||
      (clean.contains("ecoflow") && clean.contains("ချိတ်ဆက်"))
    ) {
      val isDisable = clean.contains("ပိတ်") || clean.contains("မသုံး") || clean.contains("disable")
      return ParsedIntent.KennedeFanAutomationIntent(action = "TOGGLE_ECOFLOW_LINK", param = (!isDisable).toString())
    }

    if (clean.contains("ညဘက်") || clean.contains("အိပ်စက်ချိန်") || clean.contains("night mode") || clean.contains("အသံတိတ် မုဒ်")) {
      val isDisable = clean.contains("ပိတ်") || clean.contains("မသုံး") || clean.contains("disable")
      return ParsedIntent.KennedeFanAutomationIntent(action = "TOGGLE_NIGHT_MODE", param = (!isDisable).toString())
    }

    if (clean.contains("calibration") || clean.contains("ခလုတ်အားလုံး သင်ယူ") || clean.contains("အဆင့်ဆင့် သင်ယူ") || clean.contains("ခလုတ်အားလုံး စတင်သင်ယူ")) {
      if (clean.contains("ရပ်") || clean.contains("ပိတ်") || clean.contains("ဖျက်")) {
        return ParsedIntent.KennedeFanAutomationIntent(action = "CANCEL_BATCH_CALIBRATION")
      }
      return ParsedIntent.KennedeFanAutomationIntent(action = "START_BATCH_CALIBRATION")
    }

    if ((clean.contains("ပန်ကာ") && clean.contains("အလိုအလျောက်")) || clean.contains("fan automation") || clean.contains("ပန်ကာ automation")) {
      return ParsedIntent.KennedeFanAutomationIntent(action = "OPEN_AUTOMATION_PANEL")
    }

    // 0b. IR Waveform & Hex Discipline Voice Intent (အချက် ၅)
    if (clean.contains("လှိုင်းပုံစံ") || clean.contains("အချက်ပြလှိုင်း") || clean.contains("waveform") ||
      clean.contains("hex စစ်ဆေး") || (clean.contains("ir") && (clean.contains("စစ်ဆေး") || clean.contains("စစ်ပါ") || clean.contains("ပြပါ") || clean.contains("ကုဒ်")))
    ) {
      val targetButton = when {
        clean.contains("ပါဝါ") || clean.contains("power") -> "power"
        clean.contains("မီး") || clean.contains("light") -> "light"
        clean.contains("အရှိန်") || clean.contains("speed") -> "speed"
        clean.contains("လှည့်") || clean.contains("oscillation") -> "oscillation"
        clean.contains("အလယ်") || clean.contains("center") -> "center"
        clean.contains("အသံ") || clean.contains("volume") -> "volume_up"
        clean.contains("အချိန်") || clean.contains("timer") -> "timer"
        else -> null
      }
      return ParsedIntent.InspectIrWaveform(targetButton)
    }

    // 0c. KENNEDE KN-5996HRB Fan Burmese Voice Commands
    if (clean.contains("ပန်ကာ") || clean.contains("fan") || clean.contains("kennede")) {
      if (clean.contains("မီးဖွင့်") || clean.contains("မီးဖွင့်") || clean.contains("light on")) {
        return ParsedIntent.KennedeFanCommand("FAN_LIGHT_ON", "light", "ပန်ကာ မီးဖွင့်ရန်")
      }
      if (clean.contains("မီးပိတ်") || clean.contains("light off")) {
        return ParsedIntent.KennedeFanCommand("FAN_LIGHT_OFF", "light", "ပန်ကာ မီးပိတ်ရန်")
      }
      if (clean.contains("အရှိန်မြှင့်") || clean.contains("speed up") || clean.contains("မြန်") || clean.contains("မြှင့်")) {
        return ParsedIntent.KennedeFanCommand("FAN_SPEED_UP", "speed", "ပန်ကာ အရှိန်မြှင့်ရန်")
      }
      if (clean.contains("အရှိန်လျှော့") || clean.contains("speed down") || clean.contains("ဖြည်း") || clean.contains("လျှော့")) {
        return ParsedIntent.KennedeFanCommand("FAN_SPEED_DOWN", "speed", "ပန်ကာ အရှိန်လျှော့ရန်")
      }
      if (clean.contains("မလှည့်") || clean.contains("မလှည့်") || clean.contains("ရပ်လှည့်")) {
        return ParsedIntent.KennedeFanCommand("FAN_OSCILLATION_OFF", "oscillation", "ပန်ကာ လှည့်ခြင်း ပိတ်ရန်")
      }
      if (clean.contains("လှည့်") || clean.contains("လှည့်") || clean.contains("လည်") || clean.contains("swing") || clean.contains("oscillation")) {
        return ParsedIntent.KennedeFanCommand("FAN_OSCILLATION_ON", "oscillation", "ပန်ကာ ဘယ်/ညာ လှည့်ရန်")
      }
      if (clean.contains("ဖွင့်") || clean.contains("ဖွင့်") || clean.contains("on") || clean.contains("စတင်")) {
        return ParsedIntent.KennedeFanCommand("FAN_POWER_ON", "power", "ပန်ကာ ဖွင့်ရန်")
      }
      if (clean.contains("ပိတ်") || clean.contains("off") || clean.contains("ရပ်")) {
        return ParsedIntent.KennedeFanCommand("FAN_POWER_OFF", "power", "ပန်ကာ ပိတ်ရန်")
      }
      if (clean.contains("အလယ်") || clean.contains("center")) {
        return ParsedIntent.KennedeFanCommand("FAN_CENTER", "center", "ပန်ကာ အလယ်ခလုတ်")
      }
      if (clean.contains("အသံကျယ်") || clean.contains("အသံတိုးမြှင့်") || clean.contains("volume up") || clean.contains("vol+")) {
        return ParsedIntent.KennedeFanCommand("FAN_VOLUME_UP", "volume_up", "ပန်ကာ အသံတိုးမြှင့်ရန်")
      }
      if (clean.contains("အသံတိုး") || clean.contains("အသံလျှော့") || clean.contains("volume down") || clean.contains("vol-")) {
        return ParsedIntent.KennedeFanCommand("FAN_VOLUME_DOWN", "volume_down", "ပန်ကာ အသံလျှော့ရန်")
      }
      if (clean.contains("အသံပိတ်") || clean.contains("mute")) {
        return ParsedIntent.KennedeFanCommand("FAN_MUTE", "mute", "ပန်ကာ အသံပိတ်ရန်")
      }
      if (clean.contains("မုဒ်") || clean.contains("mode")) {
        return ParsedIntent.KennedeFanCommand("FAN_MODE", "mode", "ပန်ကာ မုဒ်ပြောင်းလဲရန်")
      }
      if (clean.contains("အချိန်မှတ်") || clean.contains("timer")) {
        return ParsedIntent.KennedeFanCommand("FAN_TIMER", "timer", "ပန်ကာ အချိန်မှတ်စနစ်")
      }
    }

    // Direct Fan Light Commands without explicit "ပန်ကာ"
    if (clean.contains("မီးဖွင့်") || clean.contains("မီးဖွင့်") || clean.contains("မီးလင်း")) {
      return ParsedIntent.KennedeFanCommand("FAN_LIGHT_ON", "light", "ပန်ကာ မီးဖွင့်ရန်")
    }
    if (clean.contains("မီးပိတ်") || clean.contains("မီးမှိတ်")) {
      return ParsedIntent.KennedeFanCommand("FAN_LIGHT_OFF", "light", "ပန်ကာ မီးပိတ်ရန်")
    }

    // 1. EcoFlow Emergency Safety Halt
    if (clean.contains("အရေးပေါ်") || clean.contains("emergency") || clean.contains("ရပ်တန့်") || clean.contains("halt")) {
      return ParsedIntent.EcoFlowSafetyHalt
    }

    // 2. EcoFlow Master Power / All Ports
    if (clean.contains("ecoflow") || clean.contains("ပါဝါ") || clean.contains("စက်")) {
      if (clean.contains("ဖွင့်") || clean.contains("ဖွင့်") || clean.contains("on") || clean.contains("စတင်")) {
        if (!clean.contains("ac") && !clean.contains("dc") && !clean.contains("usb")) {
          return ParsedIntent.EcoFlowPower(true)
        }
      } else if (clean.contains("ပိတ်") || clean.contains("off") || clean.contains("ရပ်")) {
        if (!clean.contains("ac") && !clean.contains("dc") && !clean.contains("usb")) {
          return ParsedIntent.EcoFlowPower(false)
        }
      }
    }

    // 3. AC Port
    if (clean.contains("ac") || clean.contains("အေစီ") || clean.contains("မီးပလပ်") || clean.contains("220v")) {
      return if (clean.contains("ဖွင့်") || clean.contains("ဖွင့်") || clean.contains("on")) {
        ParsedIntent.EcoFlowPortToggle("AC", true)
      } else if (clean.contains("ပိတ်") || clean.contains("off")) {
        ParsedIntent.EcoFlowPortToggle("AC", false)
      } else {
        ParsedIntent.EcoFlowPortToggle("AC", true)
      }
    }

    // 4. DC Port
    if (clean.contains("dc") || clean.contains("ဒီစီ") || clean.contains("ကား") || clean.contains("12v")) {
      return if (clean.contains("ဖွင့်") || clean.contains("ဖွင့်") || clean.contains("on")) {
        ParsedIntent.EcoFlowPortToggle("DC", true)
      } else {
        ParsedIntent.EcoFlowPortToggle("DC", false)
      }
    }

    // 5. USB Port
    if (clean.contains("usb") || clean.contains("ဖုန်းအားသွင်း") || clean.contains("type-c") || clean.contains("ယူအက်စ်ဘီ")) {
      return if (clean.contains("ဖွင့်") || clean.contains("ဖွင့်") || clean.contains("on")) {
        ParsedIntent.EcoFlowPortToggle("USB", true)
      } else {
        ParsedIntent.EcoFlowPortToggle("USB", false)
      }
    }

    // 6. Charging speed
    if (clean.contains("အားသွင်းနှုန်း") || clean.contains("charge") || clean.contains("watt") || clean.contains("ဝပ်")) {
      val digits = Regex("\\d+").find(clean)?.value?.toIntOrNull()
      return if (digits != null) {
        ParsedIntent.EcoFlowChargingSpeed(digits.coerceIn(200, 1800))
      } else {
        ParsedIntent.EcoFlowChargingSpeed(800)
      }
    }

    // 7. X-Boost
    if (clean.contains("boost") || clean.contains("ဘုစ်")) {
      return if (clean.contains("ပိတ်") || clean.contains("off")) {
        ParsedIntent.EcoFlowXBoost(false)
      } else {
        ParsedIntent.EcoFlowXBoost(true)
      }
    }

    // 8. Beep sound
    if (clean.contains("beep") || clean.contains("အသံမြည်") || clean.contains("ဘီးပ်")) {
      return if (clean.contains("ပိတ်") || clean.contains("mute") || clean.contains("off")) {
        ParsedIntent.EcoFlowBeep(false)
      } else {
        ParsedIntent.EcoFlowBeep(true)
      }
    }

    // 9. Status & Battery query
    if (clean.contains("ဘက်ထရီ") || clean.contains("battery") || clean.contains("အား") || clean.contains("ကျန်")) {
      return ParsedIntent.EcoFlowQueryStatus("battery_percentage")
    }
    if (clean.contains("အခြေအနေ") || clean.contains("status") || clean.contains("ပြပါ")) {
      return ParsedIntent.EcoFlowQueryStatus("all")
    }

    // 10. Direct Settings & Background Listening Intents
    if (clean.contains("tts") || clean.contains("အသံထွက်") || clean.contains("စကားပြောသံ") || clean.contains("voice setting")) {
      return ParsedIntent.OpenTtsSettings
    }
    if (clean.contains("permission") || clean.contains("ခွင့်ပြုချက်")) {
      return ParsedIntent.OpenPermissionsSettings
    }
    if (clean.contains("နောက်ကွယ်") && (clean.contains("အသံ") || clean.contains("မိုက်"))) {
      return if (clean.contains("ပိတ်") || clean.contains("stop")) {
        ParsedIntent.ToggleBackgroundListening(false)
      } else {
        ParsedIntent.ToggleBackgroundListening(true)
      }
    }

    // 11. WiFi / Bluetooth toggles
    if (clean.contains("wifi") || clean.contains("ဝိုင်ဖိုင်")) {
      return ParsedIntent.ToggleSystemSetting("WiFi", clean.contains("ဖွင့်") || clean.contains("on"))
    }
    if (clean.contains("bluetooth") || clean.contains("ဘလူးတုသ်")) {
      return ParsedIntent.ToggleSystemSetting("Bluetooth", clean.contains("ဖွင့်") || clean.contains("on"))
    }

    // 12. App Closing & Home Screen (App ပိတ်ခြင်း)
    if (clean.contains("home") || clean.contains("ပင်မ") || clean.contains("အိမ်မျက်နှာပြင်") || (clean.contains("အက်ပ်") && clean.contains("အားလုံး") && clean.contains("ပိတ်"))) {
      return ParsedIntent.GoHome
    }
    if (clean.contains("ပိတ်") || clean.contains("close") || clean.contains("exit")) {
      if (clean.contains("viber") || clean.contains("ဗိုက်ဘာ") || clean.contains("ဗိုင်းဘာ")) {
        return ParsedIntent.CloseApp("Viber")
      }
      if (clean.contains("telegram") || clean.contains("တယ်လီဂရမ်")) {
        return ParsedIntent.CloseApp("Telegram")
      }
      if (clean.contains("facebook") || clean.contains("ဖေ့စ်ဘုတ်") || clean.contains("ဖေ့ဘုတ်")) {
        return ParsedIntent.CloseApp("Facebook")
      }
      if (clean.contains("youtube") || clean.contains("ယူကျု") || clean.contains("ယူကျူ")) {
        return ParsedIntent.CloseApp("YouTube")
      }
      if (clean.contains("kpay") || clean.contains("ကေပေး") || clean.contains("kbzpay")) {
        return ParsedIntent.CloseApp("KBZPay")
      }
      if (clean.contains("wave") || clean.contains("ဝေ့ပေး")) {
        return ParsedIntent.CloseApp("WavePay")
      }
      if (clean.contains("အက်ပ်") || clean.contains("app")) {
        return ParsedIntent.GoHome
      }
    }

    // 12b. Location Query (တည်နေရာ ရှာဖွေစစ်ဆေးခြင်း)
    if (clean.contains("တည်နေရာ") || clean.contains("location") || clean.contains("gps") || (clean.contains("ဘယ်မှာလဲ") && !clean.contains("အက်ပ်"))) {
      return ParsedIntent.QueryLocation
    }

    // 13. App Opening (App ဖွင့်ခြင်း)
    if (clean.contains("viber") || clean.contains("ဗိုက်ဘာ") || clean.contains("ဗိုင်းဘာ")) {
      return ParsedIntent.OpenApp("Viber", "https://www.viber.com")
    }
    if (clean.contains("facebook") || clean.contains("ဖေ့စ်ဘုတ်") || clean.contains("ဖေ့ဘုတ်")) {
      return ParsedIntent.OpenApp("Facebook", "https://www.facebook.com")
    }
    if (clean.contains("youtube") || clean.contains("ယူကျု") || clean.contains("ယူကျူ")) {
      return ParsedIntent.OpenApp("YouTube", "https://www.youtube.com")
    }
    if (clean.contains("telegram") || clean.contains("တယ်လီဂရမ်")) {
      return ParsedIntent.OpenApp("Telegram", "https://t.me")
    }
    if (clean.contains("kpay") || clean.contains("ကေပေး") || clean.contains("kbzpay")) {
      return ParsedIntent.OpenApp("KBZPay", "kbzpay://")
    }
    if (clean.contains("wave") || clean.contains("ဝေ့ပေး")) {
      return ParsedIntent.OpenApp("WavePay")
    }
    if (clean.contains("camera") || clean.contains("ကင်မရာ")) {
      return ParsedIntent.OpenApp("Camera")
    }
    if (clean.contains("chrome") || clean.contains("ခရုမ်း") || clean.contains("browser") || clean.contains("ဘရောက်ဇာ")) {
      return ParsedIntent.OpenApp("Chrome", "https://www.google.com")
    }
    if (clean.contains("phone") || clean.contains("ဖုန်းခေါ်") || clean.contains("ဖုန်းဆက်")) {
      val target = clean
        .replace("ကျော်ကြီး", "")
        .replace("ဖုန်းခေါ်ဆိုပေးပါ", "")
        .replace("ဖုန်းခေါ်ပေးပါ", "")
        .replace("ဖုန်းဆက်ပေးပါ", "")
        .replace("ဖုန်းခေါ်ပါ", "")
        .replace("ဖုန်းဆက်ပါ", "")
        .replace("ဖုန်းခေါ်", "")
        .replace("ဖုန်းဆက်", "")
        .replace("ပေးပါ", "")
        .replace("ပေး", "")
        .replace("စမ်းပါ", "")
        .replace("ပါ", "")
        .replace("ဆီသို့", "")
        .replace("ဆီ", "")
        .replace("ကို", "")
        .trim()
        .removeSuffix("့")
      return if (target.isNotEmpty()) ParsedIntent.CallContact(target) else ParsedIntent.OpenApp("Phone", "tel:")
    }

    // Fallback search
    if (clean.contains("ရှာ") || clean.contains("search") || clean.contains("google")) {
      val q = clean.replace("ရှာပေး", "").replace("ရှာပါ", "").replace("search", "").trim()
      if (q.isNotEmpty()) {
        return ParsedIntent.GeneralSearch(q, confidence = 0.70f)
      }
    }

    // Low confidence match (< 0.5 threshold): return 'clarify' result asking the user to repeat/clarify
    return ParsedIntent.Clarify(
      promptBurmese = "သဲသဲကွဲကွဲ မကြားရပါခင်ဗျာ။ ကျေးဇူးပြု၍ ပြန်လည်ပြောကြားပေးပါ",
      rawText = rawInput,
      confidence = 0.20f
    )
  }

  fun parseResult(rawInput: String, lastActiveContext: String? = null): ParseResult {
    val intent = parse(rawInput, lastActiveContext)
    return ParseResult(intent, intent.confidence)
  }
}
