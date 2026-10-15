package com.example.permissions

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Dedicated Bluetooth Permission & State Verification Manager.
 * Separates hardware availability, runtime permissions, adapter state, and GATT connection.
 */
class BluetoothPermissionManager(private val context: Context) {

  companion object {
    const val MYANMAR_RATIONALE = "EcoFlow ပါဝါစတေရှင်နှင့် အနီးရှိ BLE စက်ပစ္စည်းများကို ရှာဖွေချိတ်ဆက်နိုင်ရန် Bluetooth ခွင့်ပြုချက် လိုအပ်ပါသည်။"
    const val ENGLISH_RATIONALE = "Bluetooth permission is required to discover and connect to EcoFlow power stations and BLE devices."
    const val MYANMAR_DENIED_FALLBACK = "Bluetooth ခွင့်ပြုချက် မရရှိပါက BLE တိုက်ရိုက်ချိတ်ဆက်မှုကို အသုံးမပြုနိုင်ပါ။ သို့သော် EcoFlow Cloud (အင်တာနက်) နှင့် IR ခလုတ်ထိန်းချုပ်မှုများကို ပုံမှန်အတိုင်း ဆက်လက်အသုံးပြုနိုင်ပါသည်။"
    const val ENGLISH_DENIED_FALLBACK = "Without Bluetooth permission, local BLE connection is unavailable. EcoFlow Cloud and IR controls remain functional."
  }

  fun checkReadiness(isGattConnected: Boolean = false): BluetoothReadiness {
    val pm = context.packageManager
    val hasBtFeature = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    val hasBleFeature = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    if (!hasBtFeature || !hasBleFeature) {
      return BluetoothReadiness.BLUETOOTH_UNSUPPORTED
    }

    val permissionsGranted = hasBluetoothPermissions()
    if (!permissionsGranted) {
      return BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED
    }

    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    if (adapter == null) {
      return BluetoothReadiness.BLUETOOTH_UNSUPPORTED
    }

    if (!adapter.isEnabled) {
      return BluetoothReadiness.BLUETOOTH_DISABLED
    }

    return if (isGattConnected) {
      BluetoothReadiness.GATT_CONNECTED
    } else {
      BluetoothReadiness.BLUETOOTH_READY
    }
  }

  fun hasBluetoothPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val scan = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN
      ) == PackageManager.PERMISSION_GRANTED

      val connect = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED

      scan && connect
    } else {
      val bt = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH
      ) == PackageManager.PERMISSION_GRANTED

      val btAdmin = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_ADMIN
      ) == PackageManager.PERMISSION_GRANTED

      bt && btAdmin
    }
  }

  fun isBluetoothEnabled(): Boolean {
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    return adapter?.isEnabled == true
  }
}
