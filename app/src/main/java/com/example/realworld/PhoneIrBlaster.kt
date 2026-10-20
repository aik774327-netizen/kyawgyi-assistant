package com.example.realworld

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.util.Log
import com.example.models.IrButtonProfile
import com.example.models.KennedeButton

/**
 * Handles physical Infrared transmission on Android devices equipped with an IR Blaster
 * (e.g. Xiaomi, Redmi, Poco, Huawei, etc.) via ConsumerIrManager.
 * Also provides NEC protocol encoding for the KENNEDE KN-5996HRB rechargeable fan.
 */
class PhoneIrBlaster(private val context: Context) {

  private val irManager: ConsumerIrManager? by lazy {
    context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
  }

  val hasIrEmitter: Boolean
    get() = irManager?.hasIrEmitter() == true

  fun getSupportedFrequencyRanges(): List<ConsumerIrManager.CarrierFrequencyRange> {
    val mgr = irManager ?: return emptyList()
    if (!mgr.hasIrEmitter()) return emptyList()
    return try {
      mgr.carrierFrequencies?.toList().orEmpty()
    } catch (e: Exception) {
      Log.w("PhoneIrBlaster", "Failed to query carrier frequencies: ${e.message}")
      emptyList()
    }
  }

  fun isFrequencySupported(frequencyHz: Int): Boolean {
    val ranges = getSupportedFrequencyRanges()
    if (ranges.isEmpty()) return true // Allow transmit attempt on OEM HALs returning empty list
    return ranges.any { frequencyHz in it.minFrequency..it.maxFrequency }
  }

  /**
   * Transmits raw pulse array on physical IR emitter.
   * [pattern] must have even length (alternating mark and space durations in microseconds).
   */
  fun transmitPattern(frequencyHz: Int, pattern: IntArray): Result<Unit> {
    val mgr = irManager
      ?: return Result.failure(IllegalStateException("ဤစက်တွင် IR Blaster Hardware မပါရှိပါ"))

    if (!mgr.hasIrEmitter()) {
      return Result.failure(IllegalStateException("IR Emitter ကို စနစ်တွင် ရှာမတွေ့ပါ"))
    }

    if (!isFrequencySupported(frequencyHz)) {
      return Result.failure(IllegalArgumentException("$frequencyHz Hz carrier frequency ကို ဖုန်းမှ support မလုပ်ပါ"))
    }

    if (pattern.isEmpty() || pattern.size % 2 != 0) {
      return Result.failure(IllegalArgumentException("IR Pattern မမှန်ကန်ပါ (On/Off စုံတွဲ ဖြစ်ရပါမည်)"))
    }

    return try {
      mgr.transmit(frequencyHz, pattern)
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Encodes standard NEC protocol (38 kHz) into microsecond pulse timing array.
   * Format:
   *   - 9000us mark, 4500us space (Header)
   *   - 8-bit address, 8-bit ~address
   *   - 8-bit command, 8-bit ~command
   *   - 560us mark (Stop bit)
   */
  fun encodeNecPattern(address: Int, command: Int): IntArray {
    val pulses = ArrayList<Int>(68)

    // Header
    pulses.add(9000)
    pulses.add(4500)

    val addrByte = address and 0xFF
    val addrInv = (addrByte.inv()) and 0xFF
    val cmdByte = command and 0xFF
    val cmdInv = (cmdByte.inv()) and 0xFF

    val fullData = (cmdInv shl 24) or (cmdByte shl 16) or (addrInv shl 8) or addrByte

    // 32 data bits (LSB first)
    for (i in 0 until 32) {
      val bit = (fullData ushr i) and 1
      pulses.add(560) // Mark
      if (bit == 1) {
        pulses.add(1690) // Space for 1
      } else {
        pulses.add(560) // Space for 0
      }
    }

    // Stop bit
    pulses.add(560)
    pulses.add(40000) // Trailing quiet space

    return pulses.toIntArray()
  }

  /**
   * Built-in preset NEC codes for KENNEDE KN-5996HRB rechargeable fan.
   * Address: 0x00, Carrier: 38 kHz (38,000 Hz)
   *
   * Audit A4 (honesty): these are built-in ESTIMATES shipped with the app.
   * They were NOT captured from a physical remote and are NOT factory
   * verified — no capture provenance exists. They are transmittable so the
   * user can try them immediately; any button that does not respond should
   * be replaced with a real code via ESP32 Learning Mode. (Previously this
   * KDoc claimed "Verified factory preset" and the codes were persisted
   * with isVerified = true — both claims were false.)
   */
  fun getKennedePresetProfiles(): Map<KennedeButton, IrButtonProfile> {
    val presets = mapOf(
      KennedeButton.POWER to 0x08,
      KennedeButton.SPEED to 0x18,
      KennedeButton.OSCILLATION to 0x02,
      KennedeButton.LIGHT to 0x0A,
      KennedeButton.TIMER to 0x1A,
      KennedeButton.MODE to 0x0C,
      KennedeButton.VOLUME_UP to 0x05,
      KennedeButton.VOLUME_DOWN to 0x06,
      KennedeButton.MUTE to 0x07,
      KennedeButton.CENTER to 0x01
    )

    val address = 0x00
    val result = mutableMapOf<KennedeButton, IrButtonProfile>()

    for ((btn, cmd) in presets) {
      val pattern = encodeNecPattern(address, cmd)
      val timingList = pattern.toList()
      val hexData = "0x%02X%02X%02X%02X".format(
        (cmd.inv()) and 0xFF,
        cmd and 0xFF,
        (address.inv()) and 0xFF,
        address and 0xFF
      )

      result[btn] = IrButtonProfile(
        captured = true,
        protocol = "NEC",
        address = "0x%02X".format(address),
        command = "0x%02X".format(cmd),
        rawData = hexData,
        bits = 32,
        rawTiming = timingList,
        repeatCount = 0
      )
    }

    return result
  }
}
