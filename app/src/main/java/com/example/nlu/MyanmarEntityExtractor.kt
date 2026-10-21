package com.example.nlu

import java.util.Locale

/**
 * Stage 1 - Myanmar Entity Extractor
 *
 * Extracts structured entities from normalized Myanmar and English text.
 * Entities extracted:
 * - CONTACT
 * - PHONE_NUMBER
 * - APP_NAME
 * - SONG_NAME
 * - ARTIST_NAME
 * - MESSAGE_TEXT
 * - LOCATION
 * - NUMBER
 * - TIME
 * - DATE
 * - MEDIA_TYPE
 * - RAW_QUERY
 */
object MyanmarEntityExtractor {

    // Phone number patterns (Supports Myanmar and ASCII digits, e.g. 09..., +959..., 09-...)
    private val PHONE_PATTERN = Regex("(?:\\+?959|09|9)[0-9\\-]{7,11}")

    // Known common contacts / relations
    private val COMMON_RELATION_CONTACTS = listOf(
        "မောင်မောင်", "စည်သူလွင်", "ညီမလေး", "မောင်လေး", "သူငယ်ချင်း",
        "ဒေါ်ဒေါ်", "ဘွားဘွား", "ဖိုးဖိုး", "သားကြီး", "သမီးကြီး", "ကိုကျော်",
        "မသူဇာ", "ကိုအောင်", "မေမေ", "ဖေဖေ", "အမေ", "အဖေ", "ကိုကို",
        "မမ", "ညီလေး", "အစ်ကို", "အစ်မ", "ဆရာ", "ဆရာမ", "ဦးလေး",
        "ချစ်သူ", "Crush", "Mom", "Dad", "Boss"
    )

    // Known Popular Myanmar Artists
    private val KNOWN_ARTISTS = listOf(
        "လေးဖြူ", "အငဲ", "မျိုးကြီး", "စည်သူလွင်", "စိုင်းစိုင်း", "စိုင်းစိုင်းခမ်းလှိုင်",
        "ဝိုင်းစုခိုင်သိန်း", "နီနီခင်ဇော်", "ပိုပို", "ဖြိုးမြတ်အောင်", "ချမ်းချမ်း",
        "ရဲရင့်အောင်", "ထူးအိမ်သင်", "စိုင်းထီးဆိုင်", "ဇော်ဝင်းထွဋ်", "အောင်လ",
        "Taylor Swift", "Ed Sheeran", "Justin Bieber", "Blackpink", "BTS", "Eminem"
    )

    // Known App Names (Myanmar Loanwords + English)
    private val KNOWN_APPS = mapOf(
        "facebook" to "Facebook",
        "fb" to "Facebook",
        "ဖေ့ဘုတ်" to "Facebook",
        "ဖေ့ဘွတ်" to "Facebook",
        "ဖေ့ဘုတ်ခ်" to "Facebook",
        "ဖေ့စ်ဘွတ်" to "Facebook",
        "youtube" to "YouTube",
        "ယူကျု" to "YouTube",
        "ယူးကျု" to "YouTube",
        "ယူကျူ့ဗ်" to "YouTube",
        "tiktok" to "TikTok",
        "တစ်တော့" to "TikTok",
        "တိတ်တောက်" to "TikTok",
        "တစ်တောက်" to "TikTok",
        "telegram" to "Telegram",
        "တယ်လီဂရမ်" to "Telegram",
        "viber" to "Viber",
        "ဗိုက်ဘာ" to "Viber",
        "messenger" to "Messenger",
        "မက်ဆင်ဂျာ" to "Messenger",
        "kpay" to "KBZPay",
        "kbzpay" to "KBZPay",
        "ကေပီ" to "KBZPay",
        "ကေဘီဇက်ပီ" to "KBZPay",
        "wavemoney" to "WavePay",
        "wave money" to "WavePay",
        "wavepay" to "WavePay",
        "wave pay" to "WavePay",
        "ဝေ့မန်းနီး" to "WavePay",
        "ဝေ့ပီ" to "WavePay",
        "ayapay" to "AYAPay",
        "ဧရာပီ" to "AYAPay",
        "cbpay" to "CBPay",
        "စီဘီပီ" to "CBPay",
        "chrome" to "Google Chrome",
        "ခရုမ်း" to "Google Chrome",
        "browser" to "Browser",
        "camera" to "Camera",
        "ကင်မရာ" to "Camera",
        "photos" to "Photos",
        "ဓာတ်ပုံ" to "Photos",
        "gallery" to "Gallery",
        "ဂယ်လာရီ" to "Gallery",
        "settings" to "Settings",
        "ဆက်တင်" to "Settings",
        "maps" to "Google Maps",
        "မြေပုံ" to "Google Maps",
        "calculator" to "Calculator",
        "ဂဏန်းပေါင်းစက်" to "Calculator",
        "clock" to "Clock",
        "နာရီ" to "Clock",
        "alarm" to "Alarm",
        "နှိုးစက်" to "Alarm",
        "messages" to "Messages",
        "messaging" to "Messages",
        "မက်ဆေ့" to "Messages",
        "မက်ဆေ့ခ်ျ" to "Messages",
        "contacts" to "Contacts",
        "ဖုန်းစာအုပ်" to "Contacts",
        "dialer" to "Phone",
        "phone" to "Phone"
    )

    // Known Locations
    private val KNOWN_LOCATIONS = listOf(
        "ရန်ကုန်", "မန္တလေး", "နေပြည်တော်", "တောင်ကြီး", "လှည်းတန်း",
        "စမ်းချောင်း", "ရမည်းသင်း", "ပုဂံ", "မော်လမြိုင်", "ပဲခူး", "အိမ်", "ရုံး", "ဆိုင်"
    )

    // Date keywords
    private val DATE_KEYWORDS = listOf(
        "ဒီနေ့", "မနက်ဖြန်", "သဘက်ခါ", "မနေ့က", "တနင်္လာနေ့", "အင်္ဂါနေ့", "ဗုဒ္ဓဟူးနေ့",
        "ကြာသပတေးနေ့", "သောကြာနေ့", "စနေနေ့", "တနင်္ဂနွေနေ့", "today", "tomorrow"
    )

    // Time patterns
    private val TIME_PATTERNS = listOf(
        Regex("(မနက်|ညနေ|ည|နေ့လယ်)?\\s*(\\d{1,2})\\s*(နာရီ|ခွဲ|မိနစ်)"),
        Regex("(\\d{1,2}:\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE),
        Regex("(အခု|ခဏနေ|ချက်ချင်း)")
    )

    /**
     * Extracts all entities from the text into a map of EntityType to extracted string value.
     */
    fun extractEntities(originalText: String, normalizedText: String): Map<EntityType, String> {
        val entities = mutableMapOf<EntityType, String>()

        // 1. Phone Number Extraction
        val textWithDigits = MyanmarTextNormalizer.normalizeSpokenPhoneSequence(originalText)
        val phoneMatch = PHONE_PATTERN.find(textWithDigits)
        if (phoneMatch != null) {
            entities[EntityType.PHONE_NUMBER] = phoneMatch.value
        }

        // 2. Contact Name Extraction
        extractContact(originalText, normalizedText)?.let {
            entities[EntityType.CONTACT] = it
        }

        // 3. App Name Extraction
        extractAppName(originalText, normalizedText)?.let {
            entities[EntityType.APP_NAME] = it
        }

        // 4. Media Type Extraction
        extractMediaType(originalText, normalizedText)?.let {
            entities[EntityType.MEDIA_TYPE] = it
        }

        // 5. Artist Name Extraction
        extractArtist(originalText, normalizedText)?.let {
            entities[EntityType.ARTIST_NAME] = it
        }

        // 6. Song Name Extraction
        extractSongName(originalText, normalizedText, entities[EntityType.ARTIST_NAME])?.let {
            entities[EntityType.SONG_NAME] = it
        }

        // 7. Message Text Extraction (for SMS/Messaging intents)
        extractMessageText(originalText, normalizedText)?.let {
            entities[EntityType.MESSAGE_TEXT] = it
        }

        // 8. Location Extraction
        extractLocation(originalText, normalizedText)?.let {
            entities[EntityType.LOCATION] = it
        }

        // 9. Date Extraction
        extractDate(originalText, normalizedText)?.let {
            entities[EntityType.DATE] = it
        }

        // 10. Time Extraction
        extractTime(originalText, normalizedText)?.let {
            entities[EntityType.TIME] = it
        }

        // 11. Number Extraction
        extractNumber(textWithDigits)?.let {
            entities[EntityType.NUMBER] = it
        }

        return entities
    }

    /**
     * Converts the entity map into a list of KyawGyiEntity
     */
    fun extractEntityList(originalText: String, normalizedText: String): List<KyawGyiEntity> {
        val map = extractEntities(originalText, normalizedText)
        return map.map { (type, value) ->
            KyawGyiEntity(type = type, value = value, rawText = value, confidence = 0.95f)
        }
    }

    private fun extractContact(originalText: String, normalizedText: String): String? {
        val textWithoutTones = originalText.replace("့", "")
        val normWithoutTones = normalizedText.replace("့", "")

        // Pattern 1: Known relations sorted by longest string first
        val sortedContacts = COMMON_RELATION_CONTACTS.sortedByDescending { it.length }
        for (rel in sortedContacts) {
            val relBase = rel.replace("့", "")
            if (textWithoutTones.contains(relBase) || normWithoutTones.contains(relBase)) {
                return rel
            }
        }

        // Pattern 2: English "to Dad", "Call Mom", "Send message to Dad"
        val engMatch = Regex("(?:call|to)\\s+([A-Z][a-zA-Z]+)", RegexOption.IGNORE_CASE).find(originalText)
        if (engMatch != null) {
            val name = engMatch.groupValues[1].trim()
            if (name.isNotEmpty() && !KNOWN_APPS.containsKey(name.lowercase())) {
                return name
            }
        }

        // Pattern 3: "... ကို ဖုန်းဆက် / ဆီ ဖုန်းခေါ် / ကို call / ကို စာပို့"
        val targetMatch = Regex("([\\p{L}0-9_\\s]+?)(?:ကို|ဆီ|ထံ|အား)(?:ဖုန်း|call|ဖုန်းဆက်|ဖုန်းခေါ်|message|sms|စာ)?", RegexOption.IGNORE_CASE).find(originalText)
        if (targetMatch != null) {
            val candidate = targetMatch.groupValues[1].trim()
                .replace(Regex("^(မင်္ဂလာပါ|ဟေး|ကျော်ကြီးရေ|ကျော်ကြီး|ဟေ့|ကို|ဆီ|ခဏလောက်|တစ်ချက်လောက်)\\s*"), "")
                .trim()
            if (candidate.isNotEmpty() && !candidate.contains("ဖွင့်") && !candidate.contains("ပိတ်") && candidate.length <= 20) {
                return cleanContactName(candidate)
            }
        }

        return null
    }

    private fun cleanContactName(name: String): String {
        return name
            .replace(Regex("(?:ကို|ဆီ|ထံ|အား)$"), "")
            .replace("့", "")
            .trim()
    }

    private fun extractAppName(originalText: String, normalizedText: String): String? {
        val combined = "$originalText $normalizedText".lowercase(Locale.ROOT)
        // Sort by length descending to match longest key first (e.g. "wave money" before "wave")
        val sortedKeys = KNOWN_APPS.keys.sortedByDescending { it.length }
        for (key in sortedKeys) {
            if (combined.contains(key)) {
                return KNOWN_APPS[key]
            }
        }
        return null
    }

    private fun extractMediaType(originalText: String, normalizedText: String): String? {
        val text = "$originalText $normalizedText"
        return when {
            text.contains("သီချင်း") || text.contains("music") || text.contains("song") || text.contains("audio") -> "MUSIC"
            text.contains("ဗီဒီယို") || text.contains("video") || text.contains("ရုပ်ရှင်") || text.contains("movie") -> "VIDEO"
            text.contains("ဓာတ်ပုံ") || text.contains("photo") || text.contains("ပုံ") -> "PHOTO"
            text.contains("သတင်း") || text.contains("news") -> "NEWS"
            text.contains("podcast") -> "PODCAST"
            else -> null
        }
    }

    private fun extractArtist(originalText: String, normalizedText: String): String? {
        for (artist in KNOWN_ARTISTS) {
            if (originalText.contains(artist, ignoreCase = true) || normalizedText.contains(artist, ignoreCase = true)) {
                return artist
            }
        }

        // Pattern: "... ရဲ့ သီချင်း"
        val match = Regex("([\\p{L}\\s]+?)ရဲ့\\s*(?:သီချင်း|သီချင်းတွေ|music)", RegexOption.IGNORE_CASE).find(originalText)
        if (match != null) {
            val candidate = match.groupValues[1].trim()
            if (candidate.isNotEmpty() && candidate.length <= 25) {
                return candidate
            }
        }
        return null
    }

    private fun extractSongName(originalText: String, normalizedText: String, foundArtist: String?): String? {
        val text = originalText.trim()
        // Pattern 1: "<song_name> သီချင်း ဖွင့်..." / "<song_name> ဆိုတဲ့ သီချင်း ဖွင့်..."
        val p1 = Regex("^(?:ဟေး\\s*ကျော်ကြီး\\s*)?(.+?)(?:\\s+(?:ဆိုတဲ့\\s*)?သီချင်း|\\s*သီချင်း|\\s+music)(?:\\s*(?:ဖွင့်|ရှာ|play|ပြ|ဖွင့်ပြ|ဖွင့်ပေး))", RegexOption.IGNORE_CASE).find(text)
        if (p1 != null) {
            val candidate = p1.groupValues[1].trim()
            if (candidate.isNotEmpty() && candidate != foundArtist && candidate != "သီချင်း" && candidate.length <= 30) {
                return candidate
            }
        }

        // Pattern 2: "<song_name> ကို ဖွင့်ပေး"
        val p2 = Regex("^(?:ဟေး\\s*ကျော်ကြီး\\s*)?(.+?)(?:ကို|လေး)?\\s*(?:ရှာပြီး\\s*)?(?:ဖွင့်|play)", RegexOption.IGNORE_CASE).find(text)
        if (p2 != null) {
            val candidate = p2.groupValues[1].trim()
            if (candidate.isNotEmpty() && candidate != foundArtist && candidate != "သီချင်း" && !KNOWN_APPS.containsKey(candidate.lowercase()) && candidate.length <= 25) {
                if (!candidate.contains("ကင်မရာ") && !candidate.contains("မီး") && !candidate.contains("အသံ")) {
                    return candidate
                }
            }
        }

        return null
    }

    private fun extractMessageText(originalText: String, normalizedText: String): String? {
        // Pattern 1: "... လို့ message ပို့" / "... ဆိုပြီး message ပို့" / "... လို့ စာပို့"
        val msgMatch = Regex("(?:.*?(?:ကို|ဆီ|ထံ)\\s*)?[\"']?(.+?)[\"']?\\s*(?:လို့|ဆိုပြီး|ဟု|ဆိုတဲ့စာ)\\s*(?:message|sms|စာ)?\\s*(?:ပို့|ပေးပို့|send)", RegexOption.IGNORE_CASE).find(originalText)
        if (msgMatch != null) {
            val text = msgMatch.groupValues[1].trim()
            if (text.isNotEmpty() && !text.contains("မေမေ့ကို") && !text.contains("ဖေဖေ့ဆီ")) {
                return text
            }
        }

        // Pattern 2: "message ပို့: ..."
        val directMatch = Regex("(?:message|sms|စာပို့)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE).find(originalText)
        if (directMatch != null) {
            return directMatch.groupValues[1].trim()
        }

        return null
    }

    private fun extractLocation(originalText: String, normalizedText: String): String? {
        for (loc in KNOWN_LOCATIONS) {
            if (originalText.contains(loc) || normalizedText.contains(loc)) {
                return loc
            }
        }
        val mapMatch = Regex("([\\p{L}0-9\\s]+?)(?:လမ်း|မြို့|ရပ်ကွက်|သွားရာလမ်း|မြေပုံ)", RegexOption.IGNORE_CASE).find(originalText)
        if (mapMatch != null) {
            return mapMatch.groupValues[1].trim()
        }
        return null
    }

    private fun extractDate(originalText: String, normalizedText: String): String? {
        for (dateKw in DATE_KEYWORDS) {
            if (originalText.contains(dateKw, ignoreCase = true) || normalizedText.contains(dateKw, ignoreCase = true)) {
                return dateKw
            }
        }
        return null
    }

    private fun extractTime(originalText: String, normalizedText: String): String? {
        for (pattern in TIME_PATTERNS) {
            val match = pattern.find(originalText) ?: pattern.find(normalizedText)
            if (match != null) {
                return match.value.trim()
            }
        }
        return null
    }

    private fun extractNumber(text: String): String? {
        val numMatch = Regex("\\b\\d+\\b").find(text)
        return numMatch?.value
    }
}
