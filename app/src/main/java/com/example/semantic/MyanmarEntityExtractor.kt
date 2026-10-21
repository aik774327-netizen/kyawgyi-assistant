package com.example.semantic

import com.example.semantic.dictionary.MyanmarAppDictionary
import com.example.semantic.dictionary.MyanmarSynonymDictionary
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import java.util.Locale
import java.util.regex.Pattern

/**
 * Myanmar Entity Extractor for MSIL
 * Extracts typed semantic entities from natural Myanmar speech.
 */
class MyanmarEntityExtractor {

    private val phoneRegex = Pattern.compile("(?:09|\\+?959)[0-9]{7,9}|[0-9]{6,11}")
    private val timeRegex = Pattern.compile("(မနက်|နေ့လယ်|ညနေ|ည)?\\s*([0-9]{1,2})\\s*(?:နာရီ|ခွဲ|:)?\\s*([0-9]{1,2})?\\s*(?:မိနစ်)?")

    fun extractEntities(normalizedText: String, strippedText: String): List<SemanticEntity> {
        val entities = mutableListOf<SemanticEntity>()
        val textLower = normalizedText.lowercase(Locale.ROOT)
        val strippedLower = strippedText.lowercase(Locale.ROOT)

        // 1. Extract Target Reference Pronouns
        for (ref in MyanmarSynonymDictionary.CONTAINER_REFERENCES) {
            if (textLower.contains(ref)) {
                entities.add(
                    SemanticEntity(
                        type = EntityType.TARGET_REFERENCE,
                        value = ref,
                        rawText = ref,
                        metadata = mapOf("refType" to "CONTAINER")
                    )
                )
                break
            }
        }

        for (ref in MyanmarSynonymDictionary.APP_REFERENCES) {
            if (textLower.contains(ref)) {
                entities.add(
                    SemanticEntity(
                        type = EntityType.TARGET_REFERENCE,
                        value = ref,
                        rawText = ref,
                        metadata = mapOf("refType" to "APP")
                    )
                )
                break
            }
        }

        for (ref in MyanmarSynonymDictionary.MEDIA_REFERENCES) {
            if (textLower.contains(ref)) {
                entities.add(
                    SemanticEntity(
                        type = EntityType.TARGET_REFERENCE,
                        value = ref,
                        rawText = ref,
                        metadata = mapOf("refType" to "MEDIA")
                    )
                )
                break
            }
        }

        for (ref in MyanmarSynonymDictionary.CONTACT_REFERENCES) {
            if (textLower.contains(ref)) {
                entities.add(
                    SemanticEntity(
                        type = EntityType.TARGET_REFERENCE,
                        value = ref,
                        rawText = ref,
                        metadata = mapOf("refType" to "CONTACT")
                    )
                )
                break
            }
        }

        // 2. Extract Device Settings (Wi-Fi, Bluetooth, Flashlight, Volume)
        for (wifi in MyanmarSynonymDictionary.WIFI_KEYWORDS) {
            if (textLower.contains(wifi)) {
                val state = if (textLower.contains("ပိတ်") || textLower.contains("off")) "OFF" else "ON"
                entities.add(
                    SemanticEntity(
                        type = EntityType.SETTING_NAME,
                        value = "WIFI",
                        rawText = wifi,
                        metadata = mapOf("state" to state)
                    )
                )
                entities.add(
                    SemanticEntity(
                        type = EntityType.SETTING_STATE,
                        value = state,
                        rawText = state
                    )
                )
                break
            }
        }

        for (bt in MyanmarSynonymDictionary.BLUETOOTH_KEYWORDS) {
            if (textLower.contains(bt)) {
                val state = if (textLower.contains("ပိတ်") || textLower.contains("off")) "OFF" else "ON"
                entities.add(
                    SemanticEntity(
                        type = EntityType.SETTING_NAME,
                        value = "BLUETOOTH",
                        rawText = bt,
                        metadata = mapOf("state" to state)
                    )
                )
                entities.add(
                    SemanticEntity(
                        type = EntityType.SETTING_STATE,
                        value = state,
                        rawText = state
                    )
                )
                break
            }
        }

        for (flash in MyanmarSynonymDictionary.FLASHLIGHT_KEYWORDS) {
            if (textLower.contains(flash)) {
                val state = if (textLower.contains("ပိတ်") || textLower.contains("off")) "OFF" else "ON"
                entities.add(
                    SemanticEntity(
                        type = EntityType.SETTING_NAME,
                        value = "FLASHLIGHT",
                        rawText = flash,
                        metadata = mapOf("state" to state)
                    )
                )
                entities.add(
                    SemanticEntity(
                        type = EntityType.SETTING_STATE,
                        value = state,
                        rawText = state
                    )
                )
                break
            }
        }

        // 3. Extract Volume Settings & Levels
        if (textLower.contains("အသံ") || textLower.contains("volume")) {
            val isDown = textLower.contains("တိုး") || textLower.contains("လျှော့") || textLower.contains("ချ")
            val isUp = textLower.contains("မြှင့်") || textLower.contains("ကျယ်") || textLower.contains("တင်")
            val isLittle = textLower.contains("နည်းနည်း") || textLower.contains("နဲနဲ") || textLower.contains("အနည်းငယ်")
            val isMute = textLower.contains("ပိတ်") || textLower.contains("တိတ်") || textLower.contains("mute")

            val direction = if (isMute) "MUTE" else if (isDown) "DOWN" else if (isUp) "UP" else "ADJUST"
            val step = if (isLittle) "STEP_SMALL" else "STEP_DEFAULT"

            entities.add(
                SemanticEntity(
                    type = EntityType.SETTING_NAME,
                    value = "VOLUME",
                    rawText = "အသံ",
                    metadata = mapOf("direction" to direction, "step" to step)
                )
            )

            if (isLittle) {
                entities.add(
                    SemanticEntity(
                        type = EntityType.VOLUME_LEVEL,
                        value = "SMALL",
                        rawText = "နည်းနည်း"
                    )
                )
            }
        }

        // 4. Extract App Names
        val resolvedApp = MyanmarAppDictionary.resolveApp(strippedText)
        if (resolvedApp != null) {
            entities.add(
                SemanticEntity(
                    type = EntityType.APP_NAME,
                    value = resolvedApp.canonicalName,
                    rawText = resolvedApp.displayNameMM,
                    metadata = mapOf(
                        "packageName" to resolvedApp.defaultPackageName,
                        "displayNameMM" to resolvedApp.displayNameMM
                    )
                )
            )
            entities.add(
                SemanticEntity(
                    type = EntityType.APP_PACKAGE,
                    value = resolvedApp.defaultPackageName,
                    rawText = resolvedApp.defaultPackageName
                )
            )
        }

        // 5. Extract Phone Numbers
        val phoneMatcher = phoneRegex.matcher(textLower)
        if (phoneMatcher.find()) {
            val phone = phoneMatcher.group()
            entities.add(
                SemanticEntity(
                    type = EntityType.PHONE_NUMBER,
                    value = phone,
                    rawText = phone
                )
            )
        }

        // 6. Extract Contact Names (e.g. "မေမေကို ဖုန်းဆက်ပေး", "ဦးဘဆီ ဖုန်းခေါ်")
        val contact = extractContact(strippedText)
        if (contact != null) {
            entities.add(
                SemanticEntity(
                    type = EntityType.CONTACT_NAME,
                    value = contact,
                    rawText = contact
                )
            )
        }

        // 7. Extract Time & Alarm Expressions (e.g. "မနက် ၇ နာရီ Alarm ထားပေး")
        val timeEntity = extractTime(normalizedText)
        if (timeEntity != null) {
            entities.add(timeEntity)
        }

        // 8. Extract Song / Media Title
        val songEntity = extractSongTitle(strippedText)
        if (songEntity != null) {
            entities.add(songEntity)
        }

        // 9. Extract SMS Message Body
        val msgBody = extractMessageBody(strippedText)
        if (msgBody != null) {
            entities.add(
                SemanticEntity(
                    type = EntityType.MESSAGE_BODY,
                    value = msgBody,
                    rawText = msgBody
                )
            )
        }

        return entities
    }

    private fun extractContact(text: String): String? {
        val patterns = listOf(
            Pattern.compile("(.+?)(?:ကို|ဆီ|ထံ|အား|သို့|ရဲ့|၏)\\s*(?:ဖုန်းဆက်|ဖုန်းခေါ်|ဆက်|ခေါ်|မက်ဆေ့ပို့|စာပို့|ခေါ်မယ်|ဆက်မယ်)"),
            Pattern.compile("(?:ဖုန်းဆက်|ဖုန်းခေါ်|ခေါ်|ဆက်)\\s*(?:ပေးပါဦး|ပေးပါ|ပါဦး|ပေး|ပါ|ဦး|မယ်)?\\s*(?:ဆီ|ကို)?\\s*(.+)"),
            Pattern.compile("(?i)(?:call|dial|phone)\\s+(?:to\\s+)?(.+)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                var candidate = matcher.group(1)?.trim() ?: ""
                // Strip common auxiliary endings if present
                for (aux in listOf("ပေးပါဦး", "ပေးပါ", "ပါဦး", "ပေး", "ပါ", "ဦး", "လိုက်ပါ", "လိုက်", "လေး", "မယ်", "ပါရစေ", "စမ်းပါ")) {
                    if (candidate.endsWith(" $aux") || candidate.endsWith(aux)) {
                        candidate = if (candidate.endsWith(" $aux")) {
                            candidate.substring(0, candidate.length - aux.length - 1).trim()
                        } else {
                            candidate.substring(0, candidate.length - aux.length).trim()
                        }
                    }
                }
                // Normalize Burmese possessive suffix (e.g. မောင်မောင့် -> မောင်မောင်, ကိုကို့ -> ကိုကို, အမေ့ -> အမေ)
                if (candidate.endsWith("့")) {
                    candidate = candidate.dropLast(1).trim()
                }
                if (candidate.isNotBlank() && !isActionOrSystemKeyword(candidate)) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun extractTime(text: String): SemanticEntity? {
        val matcher = timeRegex.matcher(text)
        if (matcher.find()) {
            val period = matcher.group(1) ?: "" // မနက်, နေ့လယ်, ညနေ, ည
            val hourStr = matcher.group(2) ?: ""
            val minStr = matcher.group(3) ?: "00"

            val hour = hourStr.toIntOrNull() ?: return null
            val isPM = period == "ညနေ" || period == "ည" || period == "နေ့လယ်"
            val hour24 = if (isPM && hour < 12) hour + 12 else if (!isPM && period == "မနက်" && hour == 12) 0 else hour

            val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hour24, minStr.toIntOrNull() ?: 0)
            val raw = matcher.group(0) ?: formattedTime
            return SemanticEntity(
                type = EntityType.TIME_EXPRESSION,
                value = formattedTime,
                rawText = raw,
                metadata = mapOf(
                    "period" to period,
                    "hour" to hourStr,
                    "minute" to minStr,
                    "hour24" to hour24.toString()
                )
            )
        }
        return null
    }

    private fun extractSongTitle(text: String): SemanticEntity? {
        if (!text.contains("သီချင်း")) return null

        val patterns = listOf(
            Pattern.compile("သီချင်း\\s*(?:တစ်ပုဒ်|တပုဒ်)"),
            Pattern.compile("(.+?)\\s*(?:ရဲ့|၏)?\\s*(?:သီချင်း|song)"),
            Pattern.compile("(?:သီချင်း|song)\\s*(.+)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val title = matcher.group(0)?.trim() ?: ""
                return SemanticEntity(
                    type = EntityType.SONG_TITLE,
                    value = title,
                    rawText = title
                )
            }
        }
        return null
    }

    private fun extractMessageBody(text: String): String? {
        val pattern = Pattern.compile("(?:မက်ဆေ့ခ်ျ|မက်ဆေ့|စာတို|စာ)\\s*([\"'])(.*?)\\1")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(2)?.trim()
        }
        return null
    }

    private fun isActionOrSystemKeyword(word: String): Boolean {
        val commonKeywords = setOf("ဖုန်း", "အသံ", "သီချင်း", "ကင်မရာ", "အက်ပ်", "app", "video", "photo")
        return commonKeywords.contains(word.trim().lowercase(Locale.ROOT))
    }
}
