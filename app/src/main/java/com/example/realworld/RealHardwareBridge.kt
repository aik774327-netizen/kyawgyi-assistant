package com.example.realworld

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.aiangel.assistant.hardware.ecoflow.EcoFlowApiClient
import com.aiangel.assistant.hardware.ecoflow.EcoFlowResult
import com.example.database.EcoFlowConfigEntity
import com.example.database.ErrorCategory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class HardwareCheckResult {
  data class Available(val detail: String) : HardwareCheckResult()
  data class Unavailable(
    val category: ErrorCategory,
    val reasonDetail: String,
    val alternativeAdvice: String
  ) : HardwareCheckResult()
}

class RealHardwareBridge(private val context: Context) {

  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    bm?.adapter
  }

  fun checkBluetoothState(): HardwareCheckResult {
    val adapter = bluetoothAdapter
    return when {
      adapter == null -> {
        HardwareCheckResult.Unavailable(
          category = ErrorCategory.REAL_WORLD_MISMATCH,
          reasonDetail = "ဤစက်ပစ္စည်းတွင် Bluetooth Hardware အထောက်အပံ့ မပါရှိပါ သို့မဟုတ် Emulator ဖြစ်သည်",
          alternativeAdvice = "EcoFlow Cloud Open API (Wi-Fi/Internet) နည်းလမ်းကို အသုံးပြုပါ"
        )
      }
      !adapter.isEnabled -> {
        HardwareCheckResult.Unavailable(
          category = ErrorCategory.RESOURCE,
          reasonDetail = "ဖုန်း၏ Bluetooth ကို ပိတ်ထားပါသည် (Off)",
          alternativeAdvice = "ဆက်တင်မှ Bluetooth ကို အရင်ဖွင့်ပြီးမှ EcoFlow စက်ကို ချိတ်ဆက်ပါ"
        )
      }
      else -> {
        val pairedCount = try { adapter.bondedDevices.size } catch (_: SecurityException) { 0 }
        HardwareCheckResult.Available("Bluetooth ဖွင့်ထားပါသည် (တွဲချိတ်ပြီးစက်: $pairedCount ခု)")
      }
    }
  }

  fun verifyEcoFlowCloudApi(config: EcoFlowConfigEntity): HardwareCheckResult {
    if (config.accessKey.isBlank() || config.secretKey.isBlank()) {
      return HardwareCheckResult.Unavailable(
        category = ErrorCategory.RESOURCE,
        reasonDetail = "EcoFlow Developer Open API Key နှင့် Secret Key မထည့်သွင်းရသေးပါ (အရင်းအမြစ် လိုအပ်ချက်)",
        alternativeAdvice = "developer.ecoflow.com မှ ရယူထားသော Access Key & Secret Key ကို ဆက်တင်တွင် ထည့်ပါ သို့မဟုတ် Offline/BLE စနစ်သုံးပါ"
      )
    }

    if (config.deviceSn.isBlank()) {
      return HardwareCheckResult.Unavailable(
        category = ErrorCategory.REAL_WORLD_MISMATCH,
        reasonDetail = "EcoFlow Device Serial Number (SN) သတ်မှတ်ထားခြင်း မရှိပါ",
        alternativeAdvice = "သင့် EcoFlow စက်ပေါ်ရှိ SN နံပါတ် (ဥပမာ- R331...) ကို ရိုက်ထည့်ပါ"
      )
    }

    return HardwareCheckResult.Available("EcoFlow Open API Credentials သတ်မှတ်ပြီးပါပြီ")
  }

  suspend fun verifyAndPingEcoFlowCloud(config: EcoFlowConfigEntity): HardwareCheckResult {
    val basicCheck = verifyEcoFlowCloudApi(config)
    if (basicCheck is HardwareCheckResult.Unavailable) {
      return basicCheck
    }

    val host = if (config.hostUrl.isNotBlank()) config.hostUrl else "https://api-a.ecoflow.com"
    val client = EcoFlowApiClient(config.accessKey.trim(), config.secretKey.trim(), host)
    val testResult = client.testConnection(config.deviceSn.trim())

    return when (testResult) {
      is EcoFlowResult.Success -> {
        HardwareCheckResult.Available(testResult.data.message)
      }
      is EcoFlowResult.Failure -> {
        val category = when (testResult.errorCode) {
          "NO_KEYS", "MISSING_KEYS", "401" -> ErrorCategory.RESOURCE
          "NO_SN", "MISSING_SN", "20001", "404" -> ErrorCategory.REAL_WORLD_MISMATCH
          else -> ErrorCategory.TECHNICAL
        }
        HardwareCheckResult.Unavailable(
          category = category,
          reasonDetail = testResult.reason,
          alternativeAdvice = when (category) {
            ErrorCategory.RESOURCE -> "developer.ecoflow.com မှ Developer Access Key & Secret Key အား ပြန်လည်စစ်ဆေး ထည့်သွင်းပါ"
            ErrorCategory.REAL_WORLD_MISMATCH -> "စက်၏ Serial Number (SN) ကို စစ်ဆေးပါ သို့မဟုတ် EcoFlow app တွင် Developer account နှင့် ချိတ်ဆက်ထားခြင်း ရှိမရှိ စစ်ဆေးပါ"
            else -> "ဖုန်း၏ Wi-Fi/အင်တာနက် လိုင်း နှင့် EcoFlow စက်၏ Wi-Fi အခြေအနေကို စစ်ဆေးပါ"
          }
        )
      }
    }
  }

  fun checkPhoneIrState(): HardwareCheckResult {
    val phoneIrBlaster = PhoneIrBlaster(context)
    return when {
      !phoneIrBlaster.hasIrEmitter -> {
        HardwareCheckResult.Unavailable(
          category = ErrorCategory.REAL_WORLD_MISMATCH,
          reasonDetail = "ဤဖုန်းတွင် အနီအောက်ရောင်ခြည် (IR Blaster) ဟာ့ဒ်ဝဲ မပါရှိပါ",
          alternativeAdvice = "ESP32 Wi-Fi to IR Transmitter Module ကို အသုံးပြုပါ သို့မဟုတ် IR Blaster ပါသောဖုန်း (Xiaomi/Poco) တွင် အသုံးပြုပါ"
        )
      }
      else -> {
        val ranges = phoneIrBlaster.getSupportedFrequencyRanges()
        HardwareCheckResult.Available("ဖုန်း Built-in IR Emitter အသင့်ရှိပါသည် (Ranges: ${ranges.size})")
      }
    }
  }

  fun generateEcoFlowHmacSha256(secretKey: String, params: Map<String, String>, nonce: String, timestamp: Long): String {
    return try {
      val sortedParams = params.toSortedMap().map { "${it.key}=${it.value}" }.joinToString("&")
      val signStr = "$sortedParams&nonce=$nonce&timestamp=$timestamp"
      val mac = Mac.getInstance("HmacSHA256")
      val secretKeySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
      mac.init(secretKeySpec)
      val hash = mac.doFinal(signStr.toByteArray(Charsets.UTF_8))
      hash.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
      ""
    }
  }
}
