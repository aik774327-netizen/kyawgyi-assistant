package com.example.discovery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.engine.BurmesePhoneticNormalizer
import com.example.semantic.MyanmarTextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Data structure representing a discovered device contact.
 */
data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val normalizedName: String,
    val phoneticKey: String = "",
    val photoUri: String? = null,
    val isZawgyi: Boolean = false,
    val unicodeDisplayName: String = displayName
)

/**
 * Outcome of matching a contact query.
 */
sealed class ContactMatchResult {
    data class Found(
        val contact: DeviceContact,
        val confidence: Float,
        val matchType: String
    ) : ContactMatchResult()

    data class Ambiguous(
        val candidates: List<DeviceContact>,
        val query: String
    ) : ContactMatchResult()

    data class NotFound(
        val query: String,
        val reasonMM: String = "ဖုန်းအဆက်အသွယ် (Contacts) ထဲတွင် ရှာမတွေ့ပါခင်ဗျာ။"
    ) : ContactMatchResult()

    data class PermissionRequired(
        val permission: String = Manifest.permission.READ_CONTACTS,
        val reasonMM: String = "ဖုန်းအဆက်အသွယ်များကို ဖတ်ရှုရန် Contacts ခွင့်ပြုချက် လိုအပ်ပါသည်ခင်ဗျာ။"
    ) : ContactMatchResult()
}

data class DirectCallOutcome(
    val isSuccess: Boolean,
    val phoneNumber: String,
    val contactName: String,
    val isDirectCall: Boolean, // true if ACTION_CALL, false if ACTION_DIAL fallback
    val userMessageMM: String
)

data class DirectSmsOutcome(
    val isSuccess: Boolean,
    val phoneNumber: String,
    val contactName: String,
    val message: String,
    val isDirectSend: Boolean, // true if sent via SmsManager, false if opened composer
    val userMessageMM: String
)

/**
 * DeviceContactResolver
 * Scans, indexes, and resolves phone contacts on-device.
 * Enables direct voice calling ("မောင်မောင့်ဆီ ဖုန်းခေါ်ပေးပါ", "Call Mom", "09123456789 ကို ခေါ်မယ်")
 */
class DeviceContactResolver(
    private val context: Context,
    private val textNormalizer: MyanmarTextNormalizer = MyanmarTextNormalizer()
) {

    companion object {
        private const val TAG = "DeviceContactResolver"

        @Volatile
        private var instance: DeviceContactResolver? = null

        fun getInstance(context: Context): DeviceContactResolver {
            return instance ?: synchronized(this) {
                instance ?: DeviceContactResolver(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private val MYANMAR_RELATION_MAP = mapOf(
            "အမေ" to listOf("မေမေ", "အမေ", "mom", "mother", "mummy", "mama"),
            "မေမေ" to listOf("မေမေ", "အမေ", "mom", "mother", "mummy", "mama"),
            "mom" to listOf("မေမေ", "အမေ", "mom", "mother", "mummy", "mama"),
            "အဖေ" to listOf("ဖေဖေ", "အဖေ", "dad", "father", "daddy", "papa"),
            "ဖေဖေ" to listOf("ဖေဖေ", "အဖေ", "dad", "father", "daddy", "papa"),
            "dad" to listOf("ဖေဖေ", "အဖေ", "dad", "father", "daddy", "papa"),
            "ညီမ" to listOf("ညီမလေး", "ညီမ", "sister", "sis"),
            "ညီမလေး" to listOf("ညီမလေး", "ညီမ", "sister", "sis"),
            "အစ်မ" to listOf("မမ", "အစ်မ", "မကြီး", "sister"),
            "မမ" to listOf("မမ", "အစ်မ", "မကြီး", "sister"),
            "အစ်ကို" to listOf("ကိုကို", "အစ်ကို", "ကိုကြီး", "brother", "bro"),
            "ကိုကို" to listOf("ကိုကို", "အစ်ကို", "ကိုကြီး", "brother", "bro"),
            "ညီလေး" to listOf("ညီလေး", "ညီ", "brother")
        )
    }

    private val cachedContacts = mutableListOf<DeviceContact>()
    private var lastScanTimestamp: Long = 0L
    private val CACHE_TTL_MS = 60_000L // 1 minute cache

    /**
     * Check if contacts permission is granted
     */
    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if call phone permission is granted
     */
    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if send SMS permission is granted
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Scan and load all contacts from the Android Contacts Provider.
     */
    suspend fun getAllContacts(forceRefresh: Boolean = false): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return@withContext emptyList()
        }

        val currentTime = System.currentTimeMillis()
        if (!forceRefresh && cachedContacts.isNotEmpty() && (currentTime - lastScanTimestamp < CACHE_TTL_MS)) {
            return@withContext cachedContacts
        }

        val contactsList = mutableListOf<DeviceContact>()
        val seenKeys = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val id = if (idIndex >= 0) it.getString(idIndex) ?: "" else ""
                    val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    val photo = if (photoIndex >= 0) it.getString(photoIndex) else null

                    if (name.isNotBlank() && number.isNotBlank()) {
                        val cleanNumber = number.replace("[^0-9+]".toRegex(), "")
                        val uniqueKey = "$name:$cleanNumber"
                        if (seenKeys.add(uniqueKey)) {
                            // Zawgyi Font & Unicode Auto-Detection
                            val isZg = ZawgyiDetector.isZawgyi(name)
                            val unicodeName = if (isZg) ZawgyiDetector.zawgyiToUnicode(name) else name
                            val normName = textNormalizer.normalize(unicodeName).cleanedText.lowercase(Locale.ROOT).trim()
                            val phonetic = BurmesePhoneticNormalizer.normalizeBurmeseText(normName)
                            contactsList.add(
                                DeviceContact(
                                    id = id,
                                    displayName = name,
                                    phoneNumber = number,
                                    normalizedName = normName,
                                    phoneticKey = phonetic,
                                    photoUri = photo,
                                    isZawgyi = isZg,
                                    unicodeDisplayName = unicodeName
                                )
                            )
                        }
                    }
                }
            }

            synchronized(cachedContacts) {
                cachedContacts.clear()
                cachedContacts.addAll(contactsList)
                lastScanTimestamp = currentTime
            }

            Log.i(TAG, "Scanned ${contactsList.size} contacts successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }

        return@withContext contactsList
    }

    /**
     * Resolve contact from query (supports phone numbers, names, Burmese nicknames/relations)
     */
    suspend fun resolveContact(query: String): ContactMatchResult = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext ContactMatchResult.NotFound(query)
        }

        // 1. Direct phone number check
        val phoneRegex = "(?:09|\\+?959|01)[0-9]{6,10}|[0-9]{6,12}".toRegex()
        val directDigits = cleanQuery.replace("[^0-9+]".toRegex(), "")
        if (phoneRegex.matches(cleanQuery) || (directDigits.length in 7..12 && directDigits.startsWith("09"))) {
            return@withContext ContactMatchResult.Found(
                contact = DeviceContact(
                    id = "direct_number",
                    displayName = directDigits,
                    phoneNumber = directDigits,
                    normalizedName = directDigits,
                    unicodeDisplayName = directDigits
                ),
                confidence = 1.0f,
                matchType = "DIRECT_PHONE_NUMBER"
            )
        }

        if (!hasContactsPermission()) {
            return@withContext ContactMatchResult.PermissionRequired()
        }

        val allContacts = getAllContacts()
        if (allContacts.isEmpty()) {
            return@withContext ContactMatchResult.NotFound(
                query = cleanQuery,
                reasonMM = "ဖုန်းအဆက်အသွယ် (Contacts) စာရင်းထဲတွင် မည်သည့် အဆက်အသွယ်မှ မရှိသေးပါခင်ဗျာ။"
            )
        }

        // Auto-detect and normalize query font (Zawgyi or Unicode)
        val isQueryZawgyi = ZawgyiDetector.isZawgyi(cleanQuery)
        val unicodeQuery = if (isQueryZawgyi) ZawgyiDetector.zawgyiToUnicode(cleanQuery) else cleanQuery
        val normQuery = textNormalizer.normalize(unicodeQuery).cleanedText.lowercase(Locale.ROOT)
        val strippedQuery = stripHonorifics(normQuery)
        val queryPhonetic = BurmesePhoneticNormalizer.normalizeBurmeseText(normQuery)

        // A. Exact Name Match (matching normalizedName, strippedQuery, original displayName, or unicodeDisplayName)
        val exactMatch = allContacts.firstOrNull {
            it.normalizedName == normQuery ||
            it.normalizedName == strippedQuery ||
            it.unicodeDisplayName.lowercase(Locale.ROOT) == normQuery ||
            it.displayName.lowercase(Locale.ROOT) == cleanQuery.lowercase(Locale.ROOT) ||
            stripHonorifics(it.normalizedName) == strippedQuery
        }
        if (exactMatch != null) {
            return@withContext ContactMatchResult.Found(exactMatch, 1.0f, "EXACT_NAME")
        }

        // B. Relationship / Nickname Alias Match (e.g. "မေမေ", "အမေ", "Mom", "Dad")
        val relationAliases = MYANMAR_RELATION_MAP[strippedQuery] ?: MYANMAR_RELATION_MAP[normQuery]
        if (relationAliases != null) {
            val relationMatch = allContacts.firstOrNull { contact ->
                relationAliases.any { alias ->
                    val aliasLower = alias.lowercase(Locale.ROOT)
                    contact.normalizedName.contains(aliasLower) ||
                    contact.unicodeDisplayName.lowercase(Locale.ROOT).contains(aliasLower)
                }
            }
            if (relationMatch != null) {
                return@withContext ContactMatchResult.Found(relationMatch, 0.95f, "RELATION_ALIAS")
            }
        }

        // C. Substring / Prefix / Suffix Match
        val partialMatches = allContacts.filter {
            it.normalizedName.contains(strippedQuery) || strippedQuery.contains(it.normalizedName) ||
            it.displayName.lowercase(Locale.ROOT).contains(strippedQuery) ||
            it.unicodeDisplayName.lowercase(Locale.ROOT).contains(strippedQuery)
        }
        if (partialMatches.size == 1) {
            return@withContext ContactMatchResult.Found(partialMatches.first(), 0.90f, "PARTIAL_MATCH")
        } else if (partialMatches.size > 1) {
            return@withContext ContactMatchResult.Ambiguous(partialMatches.take(5), cleanQuery)
        }

        // D. Phonetic Matching (Burmese Soundex/Phonetic equivalence)
        if (queryPhonetic.isNotBlank()) {
            val phoneticMatch = allContacts.firstOrNull {
                it.phoneticKey.isNotBlank() && (it.phoneticKey == queryPhonetic || it.phoneticKey.contains(queryPhonetic) || queryPhonetic.contains(it.phoneticKey))
            }
            if (phoneticMatch != null) {
                return@withContext ContactMatchResult.Found(phoneticMatch, 0.88f, "PHONETIC_MATCH")
            }
        }

        // E. Fuzzy Match
        var bestContact: DeviceContact? = null
        var highestScore = 0.0f

        for (contact in allContacts) {
            val score1 = calculateFuzzySimilarity(strippedQuery, contact.normalizedName)
            val score2 = calculateFuzzySimilarity(strippedQuery, stripHonorifics(contact.normalizedName))
            val score = maxOf(score1, score2)
            if (score > highestScore) {
                highestScore = score
                bestContact = contact
            }
        }

        if (bestContact != null && highestScore >= 0.70f) {
            return@withContext ContactMatchResult.Found(bestContact, highestScore, "FUZZY_MATCH")
        }

        return@withContext ContactMatchResult.NotFound(cleanQuery, "‘$cleanQuery’ အမည်ရှိသော အဆက်အသွယ်ကို Contacts တွင် ရှာမတွေ့ပါခင်ဗျာ။")
    }

    /**
     * Make a direct phone call or open the dialer with the target number.
     */
    fun makePhoneCall(phoneNumber: String, contactName: String = ""): DirectCallOutcome {
        val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanPhone.isBlank()) {
            return DirectCallOutcome(
                isSuccess = false,
                phoneNumber = phoneNumber,
                contactName = contactName,
                isDirectCall = false,
                userMessageMM = "ဖုန်းနံပါတ် မမှန်ကန်ပါခင်ဗျာ။"
            )
        }

        val displayName = if (contactName.isNotBlank() && contactName != cleanPhone) contactName else cleanPhone

        return try {
            if (hasCallPermission()) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$cleanPhone")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                DirectCallOutcome(
                    isSuccess = true,
                    phoneNumber = cleanPhone,
                    contactName = displayName,
                    isDirectCall = true,
                    userMessageMM = "ဟုတ်ကဲ့၊ $displayName ($cleanPhone) သို့ ဖုန်းတိုက်ရိုက် ခေါ်ဆိုပေးနေပါပြီ ခင်ဗျာ။"
                )
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$cleanPhone")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                DirectCallOutcome(
                    isSuccess = true,
                    phoneNumber = cleanPhone,
                    contactName = displayName,
                    isDirectCall = false,
                    userMessageMM = "ဖုန်းတိုက်ရိုက်ခေါ်ဆိုခွင့် (CALL_PHONE) မရရှိသေးသဖြင့် $displayName ($cleanPhone) သို့ ခေါ်ဆိုရန် ဖုန်းခေါ်ဆိုမှု စာမျက်နှာ (Dialer) တွင် ဖွင့်ပေးထားပါသည် ခင်ဗျာ။"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing phone call to $cleanPhone", e)
            DirectCallOutcome(
                isSuccess = false,
                phoneNumber = cleanPhone,
                contactName = displayName,
                isDirectCall = false,
                userMessageMM = "ဖုန်းခေါ်ဆိုရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Send an SMS text message directly if SEND_SMS permission is granted,
     * or seamlessly launch the system SMS Composer (Messages app) with prefilled recipient & text.
     */
    fun sendSmsOrOpenComposer(phoneNumber: String, message: String, contactName: String = ""): DirectSmsOutcome {
        val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanPhone.isBlank()) {
            return DirectSmsOutcome(
                isSuccess = false,
                phoneNumber = phoneNumber,
                contactName = contactName,
                message = message,
                isDirectSend = false,
                userMessageMM = "မက်ဆေ့ခ်ျ ပို့ရန် ဖုန်းနံပါတ် မမှန်ကန်ပါခင်ဗျာ။"
            )
        }

        val displayName = if (contactName.isNotBlank() && contactName != cleanPhone) contactName else cleanPhone
        // Ensure message text is formatted cleanly (Unicode)
        val cleanMessage = ZawgyiDetector.ensureUnicode(message)

        return try {
            if (hasSmsPermission() && cleanMessage.isNotBlank()) {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(cleanMessage)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(cleanPhone, null, cleanMessage, null, null)
                }

                DirectSmsOutcome(
                    isSuccess = true,
                    phoneNumber = cleanPhone,
                    contactName = displayName,
                    message = cleanMessage,
                    isDirectSend = true,
                    userMessageMM = "ဟုတ်ကဲ့၊ $displayName ($cleanPhone) ထံသို့ မက်ဆေ့ခ်ျ ပို့ဆောင်ပေးလိုက်ပါပြီ ခင်ဗျာ။"
                )
            } else {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$cleanPhone")
                    if (cleanMessage.isNotBlank()) {
                        putExtra("sms_body", cleanMessage)
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
                DirectSmsOutcome(
                    isSuccess = true,
                    phoneNumber = cleanPhone,
                    contactName = displayName,
                    message = cleanMessage,
                    isDirectSend = false,
                    userMessageMM = "ဟုတ်ကဲ့၊ $displayName ($cleanPhone) ဆီသို့ မက်ဆေ့ခ်ျ ရေးသားပေးပို့ရန် SMS အက်ပ်တွင် ဖွင့်ပေးထားပါသည် ခင်ဗျာ။"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS to $cleanPhone", e)
            DirectSmsOutcome(
                isSuccess = false,
                phoneNumber = cleanPhone,
                contactName = displayName,
                message = cleanMessage,
                isDirectSend = false,
                userMessageMM = "မက်ဆေ့ခ်ျ ပေးပို့ရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}"
            )
        }
    }

    private fun stripHonorifics(text: String): String {
        var result = text.trim()
        val honorifics = listOf("ဦး", "ဒေါ်", "ကို", "မ", "ဆရာ", "ဆရာမ", "dr.", "dr ", "u ", "daw ", "ko ", "ma ")
        for (h in honorifics) {
            if (result.startsWith(h)) {
                result = result.substring(h.length).trim()
                break
            }
        }
        return result
    }

    private fun calculateFuzzySimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        if (s1 == s2) return 1.0f

        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0f - (distance.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length

        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1) { 0 }

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}
