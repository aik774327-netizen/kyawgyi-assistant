package com.example.hardware

import com.example.models.IrButtonProfile
import com.example.models.KennedeButton
import java.util.Locale

/**
 * Absolute Hex Discipline & Zero-Hallucination Protocol Validator.
 *
 * Enforces strict verification policies:
 * 1. Zero-Hallucination: Prevents transmission of unlearned, synthetic, or corrupted codes.
 * 2. NEC Protocol Parity: Verifies 32-bit NEC address and command inversion checks (Bit Parity).
 * 3. 38 kHz Pulse Waveform Engine: Validates and generates accurate mark/space microsecond timings.
 * 4. Export Formats: Generates C++ Arduino sendRaw arrays and JSON diagnostic backups.
 */
object HexDisciplineValidator {

  data class NecValidationResult(
    val isValid: Boolean,
    val isZeroHallucinationSafe: Boolean,
    val address: Int = 0,
    val invertedAddress: Int = 0,
    val command: Int = 0,
    val invertedCommand: Int = 0,
    val addressParityValid: Boolean = false,
    val commandParityValid: Boolean = false,
    val binaryRepresentation: String = "",
    val messageBurmese: String = "",
    val messageEnglish: String = ""
  )

  /**
   * Validates a 32-bit NEC hex code and checks inverted bit parity.
   * Standard NEC frame:
   * [8-bit Address] [8-bit Inverted Address] [8-bit Command] [8-bit Inverted Command]
   */
  fun validateNecHex(rawHex: String?): NecValidationResult {
    if (rawHex.isNullOrBlank()) {
      return NecValidationResult(
        isValid = false,
        isZeroHallucinationSafe = false,
        messageBurmese = "IR ကုဒ် မရှိသေးပါ (Zero-Hallucination: ပို့လွှတ်ခွင့် ပိတ်ပင်ထားပါသည်)",
        messageEnglish = "IR code is empty (Transmission blocked by Hex Discipline)"
      )
    }

    val cleanHex = rawHex.trim().removePrefix("0x").removePrefix("0X")
    if (cleanHex.length > 8 || cleanHex.any { it !in "0123456789abcdefABCDEF" }) {
      return NecValidationResult(
        isValid = false,
        isZeroHallucinationSafe = false,
        messageBurmese = "မမှန်ကန်သော Hex ဖော်မတ် ဖြစ်နေပါသည် (0x00000000 မှ 0xFFFFFFFF သာဖြစ်ရမည်)",
        messageEnglish = "Invalid hex string format (Must be valid 32-bit hex)"
      )
    }

    val rawValue = try {
      cleanHex.toLong(16)
    } catch (e: Exception) {
      return NecValidationResult(
        isValid = false,
        isZeroHallucinationSafe = false,
        messageBurmese = "Hex တန်ဖိုး ဖတ်ရှုမရပါ: ${e.message}",
        messageEnglish = "Failed to parse hex: ${e.message}"
      )
    }

    // Extract NEC 8-bit sections
    val addr = ((rawValue shr 24) and 0xFF).toInt()
    val invAddr = ((rawValue shr 16) and 0xFF).toInt()
    val cmd = ((rawValue shr 8) and 0xFF).toInt()
    val invCmd = (rawValue and 0xFF).toInt()

    val addrParity = (addr xor invAddr) == 0xFF
    val cmdParity = (cmd xor invCmd) == 0xFF
    val allParity = addrParity && cmdParity

    val binStr = String.format(
      Locale.US,
      "%8s %8s %8s %8s",
      Integer.toBinaryString(addr).padStart(8, '0'),
      Integer.toBinaryString(invAddr).padStart(8, '0'),
      Integer.toBinaryString(cmd).padStart(8, '0'),
      Integer.toBinaryString(invCmd).padStart(8, '0')
    )

    val burmeseMsg = if (allParity) {
      "✅ NEC Protocol စစ်မှန်မှု အောင်မြင်ပါသည် (Address: 0x%02X, Command: 0x%02X, Parity: OK)".format(addr, cmd)
    } else {
      "⚠️ Inverted Bits Parity ကွဲလွဲနေပါသည် (Extended NEC သို့မဟုတ် အချက်ပြလှိုင်း မပြည့်စုံမှု ဖြစ်နိုင်ပါသည်)"
    }

    val englishMsg = if (allParity) {
      "Valid NEC 32-bit packet (Address: 0x%02X, Command: 0x%02X)".format(addr, cmd)
    } else {
      "Extended NEC or unverified bit inversion"
    }

    return NecValidationResult(
      isValid = true,
      isZeroHallucinationSafe = true,
      address = addr,
      invertedAddress = invAddr,
      command = cmd,
      invertedCommand = invCmd,
      addressParityValid = addrParity,
      commandParityValid = cmdParity,
      binaryRepresentation = binStr,
      messageBurmese = burmeseMsg,
      messageEnglish = englishMsg
    )
  }

  /**
   * Enforces Zero-Hallucination Policy:
   * Only allows transmission if signal is captured or verified with physical timings.
   */
  fun isZeroHallucinationSafe(profile: IrButtonProfile?): Boolean {
    if (profile == null) return false
    if (!profile.captured) return false
    // Must have either verified protocol & command or raw microsecond timings
    val hasValidCommand = !profile.command.isNullOrBlank() && !profile.protocol.isNullOrBlank()
    val hasValidTimings = profile.rawTiming.isNotEmpty()
    return hasValidCommand || hasValidTimings
  }

  /**
   * Generates standard 38 kHz NEC pulse-distance timings in microseconds.
   * Total length: 1 leader mark + 1 leader space + 32 data bits * 2 + 1 stop mark = 67 elements.
   */
  fun generateNecTimingArray(address: Int, command: Int): IntArray {
    val timings = ArrayList<Int>(67)
    // 1. Leader pulse: 9000us MARK, 4500us SPACE
    timings.add(9000)
    timings.add(4500)

    val invAddress = address.inv() and 0xFF
    val invCommand = command.inv() and 0xFF
    val full32 = ((address and 0xFF) shl 24) or
      ((invAddress and 0xFF) shl 16) or
      ((command and 0xFF) shl 8) or
      (invCommand and 0xFF)

    // 2. 32 data bits (MSB first for timing generation)
    for (i in 31 downTo 0) {
      val bit = (full32 ushr i) and 1
      timings.add(560) // Mark is always 560us
      if (bit == 1) {
        timings.add(1690) // Logical 1 space: 1690us
      } else {
        timings.add(560) // Logical 0 space: 560us
      }
    }

    // 3. Stop bit
    timings.add(560)
    return timings.toIntArray()
  }

  /**
   * Converts raw timings to ready-to-use C++ Arduino / ESP32 sendRaw array code.
   */
  fun generateCppRawSnippet(buttonKey: String, timings: List<Int>): String {
    if (timings.isEmpty()) return "// No raw pulse timings recorded for $buttonKey"
    val sb = StringBuilder()
    sb.append("// KENNEDE KN-5996HRB - $buttonKey IR Timing (${timings.size} ticks @ 38 kHz)\n")
    sb.append("const uint16_t rawData_${buttonKey.lowercase(Locale.ROOT)}[${timings.size}] PROGMEM = {\n  ")
    timings.forEachIndexed { index, us ->
      sb.append(us)
      if (index < timings.size - 1) {
        sb.append(", ")
        if ((index + 1) % 10 == 0) sb.append("\n  ")
      }
    }
    sb.append("\n};\n")
    sb.append("// IrSender.sendRaw(rawData_${buttonKey.lowercase(Locale.ROOT)}, ${timings.size}, 38);")
    return sb.toString()
  }

  /**
   * Generates formatted JSON snippet for individual button export.
   */
  fun generateJsonSnippet(button: KennedeButton, profile: IrButtonProfile): String {
    val timingsStr = profile.rawTiming.joinToString(", ")
    return """
    "${button.key}": {
      "captured": ${profile.captured},
      "protocol": ${profile.protocol?.let { "\"$it\"" } ?: "null"},
      "address": ${profile.address?.let { "\"$it\"" } ?: "null"},
      "command": ${profile.command?.let { "\"$it\"" } ?: "null"},
      "rawData": ${profile.rawData?.let { "\"$it\"" } ?: "null"},
      "bits": ${profile.bits ?: profile.rawTiming.size},
      "rawTiming": [$timingsStr],
      "repeatCount": ${profile.repeatCount}
    }
    """.trimIndent()
  }
}
