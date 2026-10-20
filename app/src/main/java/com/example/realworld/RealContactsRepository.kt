package com.example.realworld

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.models.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real-world Android System Contacts Provider Repository.
 *
 * Reads actual system contacts from Android ContentResolver using ContactsContract.CommonDataKinds.Phone.
 * Handles:
 * 1. Runtime permission checks (READ_CONTACTS).
 * 2. Deduplication and normalization of phone numbers.
 * 3. Relationship / phonetic matching in Burmese and English (e.g. "အမေ", "အဖေ", "မိတ်ဆွေ", "ရုံး").
 * 4. Graceful handling of empty or restricted permission states.
 */
class RealContactsRepository(private val context: Context) {

  companion object {
    private const val TAG = "RealContactsRepository"
    // Audit B13: a DEFAULT_SEED_CONTACTS list with invented phone numbers
    // (e.g. "အမေ" -> 09778899111) used to live here and could be returned —
    // or loaded into UI state — indistinguishable from real contacts.
    // Removed: this repository returns ONLY real ContactsContract data;
    // an empty result is reported honestly as empty.
  }

  fun hasContactsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * Fetches real phone contacts from device ContactsContract.
   * If permission is denied or the device has no contacts, returns an
   * empty list — never fabricated entries.
   */
  suspend fun getContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
    if (!hasContactsPermission()) {
      Log.i(TAG, "READ_CONTACTS permission not granted. Returning empty list.")
      return@withContext emptyList()
    }

    val realContacts = mutableListOf<ContactItem>()
    val seenNumbers = HashSet<String>()

    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone._ID,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
      ContactsContract.CommonDataKinds.Phone.TYPE,
      ContactsContract.CommonDataKinds.Phone.LABEL
    )

    val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"

    var cursor: Cursor? = null
    try {
      cursor = context.contentResolver.query(
        uri,
        projection,
        null,
        null,
        sortOrder
      )

      cursor?.let { c ->
        val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
        val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
        val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
        val labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

        while (c.moveToNext()) {
          val id = if (idIdx != -1) c.getString(idIdx) else ""
          val name = (if (nameIdx != -1) c.getString(nameIdx) else "") ?: "Unknown"
          val rawNumber = (if (numIdx != -1) c.getString(numIdx) else "") ?: ""
          val cleanNumber = rawNumber.trim().filter { it.isDigit() || it == '+' }

          if (cleanNumber.isNotEmpty() && seenNumbers.add(cleanNumber)) {
            val phoneType = if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
            val customLabel = if (labelIdx != -1) c.getString(labelIdx) ?: "" else ""

            val relationship = deriveRelationship(name, phoneType, customLabel)
            realContacts.add(
              ContactItem(
                id = id.ifEmpty { "real_${realContacts.size + 1}" },
                name = name,
                phoneNumber = rawNumber.trim(),
                relationship = relationship
              )
            )
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error querying device contacts", e)
    } finally {
      cursor?.close()
    }

    realContacts
  }

  /**
   * Smart Burmese & English contact matching.
   */
  fun findContactByTarget(target: String, contacts: List<ContactItem>): ContactItem? {
    val clean = target.trim().lowercase()
    if (clean.isEmpty()) return null

    // Direct exact or partial match
    val directMatch = contacts.find {
      it.name.equals(clean, ignoreCase = true) ||
        it.relationship.equals(clean, ignoreCase = true)
    }
    if (directMatch != null) return directMatch

    // Burmese synonyms / relationship mappings
    val aliases = when {
      clean in listOf("အမေ", "မေမေ", "အမေ့", "mom", "mother") -> listOf("အမေ", "မေမေ", "mother", "mom")
      clean in listOf("အဖေ", "ဖေဖေ", "အဖေ့", "dad", "father") -> listOf("အဖေ", "ဖေဖေ", "father", "dad")
      clean in listOf("မိတ်ဆွေ", "သူငယ်ချင်း", "အဆွေ", "friend") -> listOf("မိတ်ဆွေ", "သူငယ်ချင်း", "friend")
      clean in listOf("ရုံး", "အလုပ်", "office", "work") -> listOf("ရုံး", "အလုပ်", "office", "work")
      clean in listOf("ညီမ", "မမ", "အစ်မ", "sister") -> listOf("ညီမ", "မမ", "အစ်မ", "sister")
      clean in listOf("အစ်ကို", "ကိုကို", "ညီ", "brother") -> listOf("အစ်ကို", "ကိုကို", "ညီ", "brother")
      else -> listOf(clean)
    }

    return contacts.find { contact ->
      val cName = contact.name.lowercase()
      val cRel = contact.relationship.lowercase()
      aliases.any { alias ->
        cName.contains(alias) || cRel.contains(alias) || alias.contains(cRel)
      }
    }
  }

  /**
   * Filter contacts by search query.
   */
  fun searchContacts(query: String, contacts: List<ContactItem>): List<ContactItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return contacts
    return contacts.filter {
      it.name.lowercase().contains(q) ||
        it.phoneNumber.contains(q) ||
        it.relationship.lowercase().contains(q)
    }
  }

  /**
   * Derives friendly Burmese/English relationship label based on contact name or phone type.
   */
  private fun deriveRelationship(name: String, type: Int, customLabel: String): String {
    val lower = name.lowercase()
    return when {
      lower.contains("အမေ") || lower.contains("mom") || lower.contains("mother") || lower.contains("မေမေ") -> "အမေ"
      lower.contains("အဖေ") || lower.contains("dad") || lower.contains("father") || lower.contains("ဖေဖေ") -> "အဖေ"
      lower.contains("ရုံး") || lower.contains("office") || lower.contains("work") || lower.contains("အလုပ်") -> "ရုံး"
      lower.contains("သူငယ်ချင်း") || lower.contains("friend") || lower.contains("မိတ်ဆွေ") -> "သူငယ်ချင်း"
      lower.contains("ညီမ") || lower.contains("sister") || lower.contains("မမ") -> "ညီမ/အစ်မ"
      lower.contains("အစ်ကို") || lower.contains("brother") || lower.contains("ကိုကို") -> "အစ်ကို/ညီ"
      customLabel.isNotEmpty() -> customLabel
      type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "မိုဘိုင်းလ်"
      type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "အိမ်"
      type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "အလုပ်"
      else -> "အခြား"
    }
  }
}
