package com.example.nlu

/**
 * မြန်မာဒေသသုံး စကားသံကွဲပြားမှုများ (Regional Dialects)၊
 * စာလုံးပေါင်း အမှားအယွင်းများ (Phonetic / Orthographic variations) နှင့်
 * အသံနေအထား ကွဲပြားမှုများကို တိကျစွာ ပြင်ဆင်ညှိယူပေးသည့် Normalizer ဖြစ်သည်။
 */
object MyanmarDialectAndContextNormalizer {

  // ၁။ ဒေသအလိုက် အသံထွက် ကွဲပြားမှုများနှင့် အစားထိုးစကားလုံးများ
  // (ထားဝယ်၊ မြိတ်၊ ရခိုင်၊ အညာ/မန္တလေး၊ ရန်ကုန်၊ ရှမ်း/ကချင်သံဝဲ)
  // Note: မြန်မာစာတွင် \b word boundary သည် Unicode စာလုံးများအတွက် အလုပ်မလုပ်သောကြောင့်
  // စကားလုံးတိုက်ရိုက် သို့မဟုတ် Regex pattern ဖြင့် အစားထိုးသည်။
  private val directReplacements = listOf(
    // ပန်ကာ (Fan) သံကွဲများနှင့် အသံဝဲများ
    "ဘန်ဂါ" to "ပန်ကာ",
    "ဘန်ကာ" to "ပန်ကာ",
    "ပန်ဂါ" to "ပန်ကာ",
    "ပန်းကာ" to "ပန်ကာ",
    "ပန်ခါ" to "ပန်ကာ",
    "ဖင်ကာ" to "ပန်ကာ",
    
    // ဖွင့်ပါ (Open/Turn on) ဒေသသံများ
    "ဖွင့်ပီး" to "ဖွင့်ပါ",
    "ဖွင့်ပီး" to "ဖွင့်ပါ",
    "ဖွင့်ပေး" to "ဖွင့်ပါ",
    "ဖွင့်ပေး" to "ဖွင့်ပါ",
    "စဖွင့်" to "ဖွင့်ပါ",
    "ဖွင့်လိုက်" to "ဖွင့်ပါ",
    "ဖွင့်ပါဦး" to "ဖွင့်ပါ",
    "ဖွင့်ပါဦး" to "ဖွင့်ပါ",
    
    // ပိတ်ပါ (Turn off/Stop) ဒေသသံများ
    "ပိတ်ပီး" to "ပိတ်ပါ",
    "ပိတ်ပစ်" to "ပိတ်ပါ",
    "ပိတ်လိုက်" to "ပိတ်ပါ",
    "မသုံးတော့ဘူး" to "ပိတ်ပါ",
    "ရပ်ထား" to "ပိတ်ပါ",
    
    // မီး (Light) သံကွဲများ
    "မီးရောင်" to "မီး",
    "မီးချောင်း" to "မီး",
    "မီးလုံး" to "မီး",
    
    // အရှိန်မြှင့် / အရှိန်လျှော့
    "မြန်မြန်" to "အရှိန်မြှင့်",
    "ဟုန်မြှင့်" to "အရှိန်မြှင့်",
    "လေပြင်း" to "အရှိန်မြှင့်",
    "လေအားတင်" to "အရှိန်မြှင့်",
    "ဖြည်းဖြည်း" to "အရှိန်လျှော့",
    "ဟုန်လျှော့" to "အရှိန်လျှော့",
    "လေအေး" to "အရှိန်လျှော့",
    "လေအားချ" to "အရှိန်လျှော့",
    
    // ဘယ်ညာလှည့် (Oscillation / Swing)
    "ခေါင်းလှည့်" to "လှည့်",
    "ဝေ့ပါ" to "လှည့်",
    "မလှည့်နဲ့" to "မလှည့်",
    "မလည်နဲ့" to "မလှည့်",
    
    // သီချင်း (Song / Music) သံကွဲများ
    "သီချင်းလေး" to "သီချင်း",
    "တေးချင်း" to "သီချင်း",
    "တေးသီချင်း" to "သီချင်း",
    "အသံစဥ်" to "သီချင်း",
    "အသံစဉ်" to "သီချင်း",
    
    // ရွေးချယ်မှု စကားသံများ (Candidates)
    "ပထမတစ်ခု" to "ပထမတစ်ပုဒ်",
    "နံပါတ်တစ်" to "ပထမတစ်ပုဒ်",
    "နံပတ်၁" to "ပထမတစ်ပုဒ်",
    "ဒုတိယတစ်ခု" to "ဒုတိယတစ်ပုဒ်",
    "နံပါတ်နှစ်" to "ဒုတိယတစ်ပုဒ်",
    "နံပတ်၂" to "ဒုတိယတစ်ပုဒ်",
    "တတိယတစ်ခု" to "တတိယတစ်ပုဒ်",
    "နံပါတ်သုံး" to "တတိယတစ်ပုဒ်",
    "နံပတ်၃" to "တတိယတစ်ပုဒ်"
  )

  private val regexReplacements = listOf(
    Regex("lay\\s*phyu", RegexOption.IGNORE_CASE) to "လေးဖြူ",
    Regex("myo\\s*gyi", RegexOption.IGNORE_CASE) to "မျိုးကြီး",
    Regex("zaw\\s*win\\s*htut", RegexOption.IGNORE_CASE) to "ဇော်ဝင်းထွဋ်",
    Regex("ai\\s*angel|အိန်ဂျယ်|အင်ဂျယ်", RegexOption.IGNORE_CASE) to "angel"
  )

  /**
   * အသုံးပြုသူ၏ မူရင်းအသံထွက် သို့မဟုတ် စာသားကို
   * ဒေသစကားသံနှင့် အမှားအယွင်းများ ကင်းစင်စေရန် ပုံမှန်စံအဖြစ် ပြောင်းလဲပေးသည်။
   */
  fun normalize(input: String): String {
    var text = input.trim()
    // Unicode Zero-width space နှင့် အတူတူဖြစ်သော သင်္ကေတများ ရှင်းထုတ်ခြင်း
    text = text.replace("\u200B", "").replace("\uFEFF", "")

    // Direct String Replacements (မြန်မာစာလုံးများအတွက် အတိအကျ အလုပ်လုပ်သည်)
    for ((from, to) in directReplacements) {
      text = text.replace(from, to)
    }

    // Regex Replacements
    for ((regex, replacement) in regexReplacements) {
      text = regex.replace(text, replacement)
    }

    return text
  }

  /**
   * ရှေ့နောက် စကားဝိုင်း Context ပေါ်မူတည်၍ ဝိုးတဝါးဖြစ်နေသော အမိန့်များကို ရှင်းလင်းဆုံးဖြတ်ခြင်း
   * ဥပမာ- "ဖွင့်ပါ" သို့မဟုတ် "အရှိန်မြှင့်" ဟုသာ ကွက်တိပြောလာပါက
   * ယခင် context က Fan ဖြစ်နေလျှင် Fan Command အဖြစ်၊ Music ဖြစ်နေလျှင် Music Command အဖြစ် ခွဲခြားသည်။
   */
  fun disambiguateWithContext(
    rawText: String,
    lastActiveContext: String? // "FAN", "MUSIC", "ECOFLOW", "CHAT"
  ): String {
    val normalized = normalize(rawText)

    // အကယ်၍ အကြောင်းအရာ တိုက်ရိုက်မပါဘဲ Action သာ ပါလာပါက Context ထည့်သွင်းပေးခြင်း
    if (!normalized.contains("ပန်ကာ") && !normalized.contains("သီချင်း") && !normalized.contains("ecoflow")) {
      when (lastActiveContext) {
        "FAN" -> {
          if (normalized.contains("ဖွင့်") || normalized.contains("ပိတ်") || 
              normalized.contains("အရှိန်") || normalized.contains("လှည့်") || normalized.contains("မီး")) {
            return "ပန်ကာ $normalized"
          }
        }
        "MUSIC" -> {
          if (normalized.contains("ဖွင့်") || normalized.contains("ရပ်") || normalized.contains("ဆက်")) {
            return "သီချင်း $normalized"
          }
        }
        "ECOFLOW" -> {
          if (normalized.contains("ဖွင့်") || normalized.contains("ပိတ်") || normalized.contains("အား")) {
            return "ecoflow $normalized"
          }
        }
      }
    }

    return normalized
  }
}
