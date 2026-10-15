package com.example.nlu

/**
 * စနစ်အတွင်းရှိ Tools နှင့် Functions များကို စနစ်တကျ ခေါ်ယူအသုံးပြုနိုင်စေရန်
 * သတ်မှတ်ထားသော Function Calling Framework
 */
sealed class AssistantToolCall {
  data class ControlFanTool(val action: String, val param: String) : AssistantToolCall()
  data class PlayMusicTool(val query: String, val candidateIndex: Int? = null) : AssistantToolCall()
  data class ControlEcoFlowTool(val targetPort: String?, val turnOn: Boolean) : AssistantToolCall()
  data class IngestDataTool(val assetType: String, val description: String) : AssistantToolCall()
  data class ScholarReasoningTool(val userQuery: String, val contextDomain: String) : AssistantToolCall()
}

/**
 * တုံ့ပြန်ချက် ရလဒ်နှင့် ခေါ်ယူလိုက်သော Tool အချက်အလက်
 */
data class ConciseAssistantResponse(
  val spokenText: String, // သဘာဝကျပြီး လိုရင်းတိုရှင်း စကားပြောဟန်
  val directActionTaken: String,
  val triggeredTool: AssistantToolCall? = null,
  val newActiveContext: String // "FAN", "MUSIC", "ECOFLOW", "SCHOLAR", "CHAT"
)
