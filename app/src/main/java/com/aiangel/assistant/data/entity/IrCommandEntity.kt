package com.aiangel.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IrEvidenceState defines the strict 7-stage evidence lifecycle chain:
 * NOT_TESTED -> LEARNING -> LEARNED -> TRANSMITTING -> ACKNOWLEDGED -> OPTICAL_VERIFIED -> APPLIANCE_VERIFIED
 *
 * Strict Discipline:
 * ESP32 ACK != Appliance Verification.
 * Fake or simulated data is strictly prohibited.
 */
enum class IrEvidenceState(
    val stateKey: String,
    val burmeseLabel: String,
    val badgeColorHex: Long
) {
    NOT_TESTED("NOT_TESTED", "စစ်ဆေးရန် ကျန်ရှိ", 0xFF6B7280),
    LEARNING("LEARNING", "ဖမ်းယူနေဆဲ", 0xFF3B82F6),
    LEARNED("LEARNED", "ဖမ်းယူပြီးစီး", 0xFF6366F1),
    TRANSMITTING("TRANSMITTING", "ထုတ်လွှင့်နေဆဲ", 0xFFF59E0B),
    ACKNOWLEDGED("ACKNOWLEDGED", "ESP32 မှ လက်ခံရရှိ", 0xFF06B6D4),
    OPTICAL_VERIFIED("OPTICAL_VERIFIED", "အလင်းလှိုင်း စစ်ဆေးပြီး", 0xFF14B8A6),
    APPLIANCE_VERIFIED("APPLIANCE_VERIFIED", "ပစ္စည်းအစစ် စစ်ဆေးပြီး", 0xFF10B981);

    companion object {
        fun fromKey(key: String): IrEvidenceState {
            return entries.firstOrNull { it.stateKey.equals(key, ignoreCase = true) } ?: NOT_TESTED
        }
    }
}

/**
 * Room Database Entity for storing IR remote commands and associated verification evidence.
 * Enables tracking the full lifecycle of each physical appliance signal as specified
 * in the project requirements.
 */
@Entity(tableName = "ir_commands")
data class IrCommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Logical device this command belongs to, e.g. "KENNEDE KN-5996HRB"
    val deviceName: String,

    // Human/voice-facing label, e.g. "POWER", "LIGHT", "SPEED", "OSCILLATION", "CENTER"
    val commandLabel: String,

    // Carrier frequency in Hz, almost always 38000 for consumer IR
    val frequencyHz: Int = 38000,

    // Comma-separated on/off pulse durations in microseconds, e.g. "9000,4500,560,560,..."
    val pattern: String = "",

    val learnedAt: Long = System.currentTimeMillis(),

    // Optional manufacturer/brand tag, e.g. "KENNEDE"
    val brand: String? = null,

    // Set true ONLY once physical appliance response has been verified
    val isVerified: Boolean = false,

    // Lifecycle state: NOT_TESTED, LEARNING, LEARNED, TRANSMITTING, ACKNOWLEDGED, OPTICAL_VERIFIED, APPLIANCE_VERIFIED
    val evidenceState: String = "NOT_TESTED",

    // Hexadecimal representation of the decoded IR code, e.g. "0x00FF02FD"
    val hexCode: String? = null,

    // Decoded protocol family (e.g. "NEC", "RC5", "RAW")
    val protocol: String? = "NEC",

    // Timestamps for audit trails
    val lastTransmittedAt: Long? = null,
    val acknowledgedAt: Long? = null,
    val opticalVerifiedAt: Long? = null,
    val applianceVerifiedAt: Long? = null,

    // Evidence log, hash, or probe observation note
    val evidenceDetails: String? = null,

    // Last failure reason if transmission or verification failed (used by ErrorLearningEngine)
    val lastError: String? = null,

    // Record last update time
    val updatedAt: Long = System.currentTimeMillis()
)

