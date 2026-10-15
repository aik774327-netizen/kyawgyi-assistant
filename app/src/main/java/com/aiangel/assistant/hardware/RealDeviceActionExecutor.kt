package com.aiangel.assistant.hardware

import android.content.Context
import android.hardware.ConsumerIrManager
import com.aiangel.assistant.data.dao.IrCommandDao
import com.aiangel.assistant.data.entity.IrCommandEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of any IR operation, carrying a user-facing (Burmese) reason on failure. */
sealed class IrResult {
    object Success : IrResult()
    data class Failure(val reason: String) : IrResult()
}

/**
 * Owns all direct hardware calls to the device's infrared blaster.
 * Wraps android.hardware.ConsumerIrManager and persists/replays commands
 * via IrCommandDao so the voice pipeline never touches raw pulse arrays.
 */
class RealDeviceActionExecutor(
    context: Context,
    private val irCommandDao: IrCommandDao
) {
    private val appContext = context.applicationContext
    private val irManager: ConsumerIrManager? =
        appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /** True only if this specific phone has IR hardware AND it's active. */
    val isIrAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    /** Carrier frequency ranges the hardware reports supporting, for pre-transmit validation. */
    fun supportedFrequencyRanges(): List<ConsumerIrManager.CarrierFrequencyRange> {
        val manager = irManager ?: return emptyList()
        if (!manager.hasIrEmitter()) return emptyList()
        return manager.carrierFrequencies?.toList().orEmpty()
    }

    private fun isFrequencySupported(frequencyHz: Int): Boolean {
        val ranges = supportedFrequencyRanges()
        // Some OEM HALs return an empty range list even with a working emitter —
        // in that case we can't validate up front, so allow the transmit attempt.
        if (ranges.isEmpty()) return true
        return ranges.any { frequencyHz in it.minFrequency..it.maxFrequency }
    }

    /**
     * Voice-pipeline entry point: sends a previously learned command by
     * device + label, e.g. sendCommand("Kennede Fan", "POWER").
     */
    suspend fun sendCommand(deviceName: String, commandLabel: String): IrResult =
        withContext(Dispatchers.IO) {
            val manager = irManager
                ?: return@withContext IrResult.Failure("ဒီဖုန်းမှာ IR blaster hardware မရှိပါ")
            if (!manager.hasIrEmitter()) {
                return@withContext IrResult.Failure("IR emitter ကို detect မတွေ့ပါ")
            }

            val command = irCommandDao.findCommand(deviceName, commandLabel)
                ?: return@withContext IrResult.Failure(
                    "\"$deviceName\" အတွက် \"$commandLabel\" command ကို database ထဲမတွေ့ပါ"
                )

            transmit(command.frequencyHz, command.pattern)
        }

    /**
     * Low-level transmit: raw frequency + CSV pulse pattern, no DB lookup.
     * Used internally by sendCommand(), and directly when replaying a
     * command the caller already has in hand (e.g. right after learning it).
     */
    suspend fun transmit(frequencyHz: Int, patternCsv: String): IrResult =
        withContext(Dispatchers.IO) {
            val manager = irManager
                ?: return@withContext IrResult.Failure("ဒီဖုန်းမှာ IR blaster hardware မရှိပါ")
            if (!manager.hasIrEmitter()) {
                return@withContext IrResult.Failure("IR emitter ကို detect မတွေ့ပါ")
            }
            if (!isFrequencySupported(frequencyHz)) {
                return@withContext IrResult.Failure("$frequencyHz Hz frequency ကို ဒီဖုန်းက support မလုပ်ပါ")
            }

            val pattern = patternCsv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toIntArray()

            if (pattern.isEmpty() || pattern.size % 2 != 0) {
                return@withContext IrResult.Failure(
                    "IR pulse pattern format မမှန်ပါ (on/off တွဲချ ဖြစ်ရမယ်)"
                )
            }

            try {
                manager.transmit(frequencyHz, pattern)
                IrResult.Success
            } catch (e: Exception) {
                IrResult.Failure("Transmit failure: ${e.message}")
            }
        }

    /**
     * Persists a newly captured or manually-entered command so it can be
     * replayed later by voice via sendCommand(). Call this once during
     * "learning mode" (capturing from an existing physical remote) or when
     * seeding known manufacturer codes.
     */
    suspend fun learnAndStoreCommand(
        deviceName: String,
        commandLabel: String,
        frequencyHz: Int,
        pattern: IntArray,
        brand: String? = null
    ): Long = withContext(Dispatchers.IO) {
        irCommandDao.insert(
            IrCommandEntity(
                deviceName = deviceName,
                commandLabel = commandLabel,
                frequencyHz = frequencyHz,
                pattern = pattern.joinToString(","),
                brand = brand,
                isVerified = false
            )
        )
    }

    suspend fun markVerified(command: IrCommandEntity) = withContext(Dispatchers.IO) {
        irCommandDao.insert(command.copy(isVerified = true))
    }

    suspend fun listKnownDevices(): List<String> = withContext(Dispatchers.IO) {
        irCommandDao.getKnownDevices()
    }

    suspend fun hasCommand(deviceName: String, commandLabel: String): Boolean =
        withContext(Dispatchers.IO) {
            irCommandDao.commandExists(deviceName, commandLabel) > 0
        }
}
