package com.kyawgyi.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * ProperNounHotwordDataset (Additional 12 — Myanmar Cities, Townships, Honorifics & Contacts Hotword Lexicon)
 *
 * Covers Item #9 of the Production Burmese Voice AI Checklist:
 * - Major Myanmar cities, states/regions, and Yangon/Mandalay townships.
 * - Burmese kinship terms, honorifics (`ဦး`, `ဒေါ်`, `ကို`, `မ`, `ဆရာ`, `ဆရာမ`, `ဗိုလ်`), and common person names.
 * - Optional runtime loader to import user's device contacts (when `READ_CONTACTS` is granted)
 *   into [HotwordBiasing] so voice dialing (`"ဦးကျော်ကြီးကို ဖုန်းခေါ်ပေးပါ"`) achieves high accuracy.
 */
object ProperNounHotwordDataset {

    val MYANMAR_CITIES_AND_TOWNSHIPS: List<HotwordEntry> = listOf(
        HotwordEntry("ရန်ကုန်", "ရန်ကုန်", 2.4f, "PLACE"),
        HotwordEntry("မန္တလေး", "မန္တလေး", 2.4f, "PLACE"),
        HotwordEntry("နေပြည်တော်", "နေပြည်တော်", 2.4f, "PLACE"),
        HotwordEntry("တောင်ကြီး", "တောင်ကြီး", 2.3f, "PLACE"),
        HotwordEntry("မော်လမြိုင်", "မော်လမြိုင်", 2.3f, "PLACE"),
        HotwordEntry("ပဲခူး", "ပဲခူး", 2.2f, "PLACE"),
        HotwordEntry("ပုသိမ်", "ပုသိမ်", 2.2f, "PLACE"),
        HotwordEntry("မုံရွာ", "မုံရွာ", 2.2f, "PLACE"),
        HotwordEntry("မကွေး", "မကွေး", 2.2f, "PLACE"),
        HotwordEntry("ပြင်ဦးလွင်", "ပြင်ဦးလွင်", 2.3f, "PLACE"),
        HotwordEntry("ပုဂံ", "ပုဂံ", 2.3f, "PLACE"),
        HotwordEntry("မြစ်ကြီးနား", "မြစ်ကြီးနား", 2.2f, "PLACE"),
        HotwordEntry("ဘားအံ", "ဘားအံ", 2.2f, "PLACE"),
        HotwordEntry("ထားဝယ်", "ထားဝယ်", 2.2f, "PLACE"),
        HotwordEntry("စစ်တွေ", "စစ်တွေ", 2.2f, "PLACE"),
        HotwordEntry("လားရှိုး", "လားရှိုး", 2.2f, "PLACE"),
        HotwordEntry("ပြည်", "ပြည်", 2.1f, "PLACE"),
        HotwordEntry("မိတ္ထီလာ", "မိတ္ထီလာ", 2.2f, "PLACE"),
        // Major Townships & Landmarks
        HotwordEntry("ကမာရွတ်", "ကမာရွတ်", 2.2f, "TOWNSHIP"),
        HotwordEntry("စမ်းချောင်း", "စမ်းချောင်း", 2.2f, "TOWNSHIP"),
        HotwordEntry("လှိုင်", "လှိုင်", 2.1f, "TOWNSHIP"),
        HotwordEntry("တာမွေ", "တာမွေ", 2.2f, "TOWNSHIP"),
        HotwordEntry("ဗဟန်း", "ဗဟန်း", 2.2f, "TOWNSHIP"),
        HotwordEntry("အင်းစိန်", "အင်းစိန်", 2.2f, "TOWNSHIP"),
        HotwordEntry("မြောက်ဥက္ကလာပ", "မြောက်ဥက္ကလာပ", 2.3f, "TOWNSHIP"),
        HotwordEntry("တောင်ဥက္ကလာပ", "တောင်ဥက္ကလာပ", 2.3f, "TOWNSHIP"),
        HotwordEntry("သင်္ဃန်းကျွန်း", "သင်္ဃန်းကျွန်း", 2.3f, "TOWNSHIP"),
        HotwordEntry("လှည်းတန်း", "လှည်းတန်း", 2.3f, "LANDMARK"),
        HotwordEntry("မြေနီကုန်း", "မြေနီကုန်း", 2.3f, "LANDMARK"),
        HotwordEntry("ဆူးလေ", "ဆူးလေ", 2.3f, "LANDMARK"),
        HotwordEntry("ရွှေတိဂုံ", "ရွှေတိဂုံ", 2.4f, "LANDMARK")
    )

    val BURMESE_HONORIFICS_AND_NAMES: List<HotwordEntry> = listOf(
        HotwordEntry("ဖေဖေ", "ဖေဖေ", 2.5f, "KINSHIP"),
        HotwordEntry("မေမေ", "မေမေ", 2.5f, "KINSHIP"),
        HotwordEntry("အဖေ", "အဖေ", 2.4f, "KINSHIP"),
        HotwordEntry("အမေ", "အမေ", 2.4f, "KINSHIP"),
        HotwordEntry("အစ်ကို", "အစ်ကို", 2.3f, "KINSHIP"),
        HotwordEntry("အစ်မ", "အစ်မ", 2.3f, "KINSHIP"),
        HotwordEntry("ညီလေး", "ညီလေး", 2.3f, "KINSHIP"),
        HotwordEntry("ညီမလေး", "ညီမလေး", 2.3f, "KINSHIP"),
        HotwordEntry("ဦးကျော်", "ဦးကျော်", 2.3f, "PERSON"),
        HotwordEntry("ကိုအောင်", "ကိုအောင်", 2.3f, "PERSON"),
        HotwordEntry("ကိုမင်း", "ကိုမင်း", 2.3f, "PERSON"),
        HotwordEntry("ဒေါ်မြ", "ဒေါ်မြ", 2.3f, "PERSON"),
        HotwordEntry("မစု", "မစု", 2.3f, "PERSON"),
        HotwordEntry("ဆရာတော်", "ဆရာတော်", 2.3f, "HONORIFIC")
    )

    fun allDefaultProperNouns(): List<HotwordEntry> =
        MYANMAR_CITIES_AND_TOWNSHIPS + BURMESE_HONORIFICS_AND_NAMES

    /**
     * Populates [hotwordBiasing] with all default Myanmar cities, townships, honorifics,
     * and optionally device contacts (if `READ_CONTACTS` permission is already granted).
     */
    fun populateHotwordBiasing(
        context: Context,
        hotwordBiasing: HotwordBiasing,
        maxDeviceContacts: Int = 100
    ): Int {
        var addedCount = 0
        for (entry in allDefaultProperNouns()) {
            hotwordBiasing.registerHotword(
                phrase = entry.phrase,
                canonicalReplacement = entry.canonicalReplacement,
                boostScore = entry.boostScore,
                category = entry.category
            )
            addedCount++
        }

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactsPermission) {
            val contactNames = queryDeviceContactNames(context, maxDeviceContacts)
            hotwordBiasing.registerDynamicContacts(contactNames)
            addedCount += contactNames.size
        }

        return addedCount
    }

    private fun queryDeviceContactNames(context: Context, limit: Int): List<String> =
        runCatching {
            val names = mutableListOf<String>()
            val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Contacts.STARRED} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (cursor.moveToNext() && names.size < limit) {
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)?.trim()
                        if (!name.isNullOrBlank() && name.length in 2..40) {
                            names.add(name)
                        }
                    }
                }
            }
            names
        }.getOrDefault(emptyList())
}
