package com.example.realworld

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

data class DeviceLocationInfo(
  val isAvailable: Boolean,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val accuracyMeters: Float? = null,
  val provider: String? = null,
  val descriptionBurmese: String
)

class RealLocationManager(private val context: Context) {

  private val locationManager by lazy {
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  }

  fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fine || coarse
  }

  @SuppressLint("MissingPermission")
  fun getCurrentLocation(): DeviceLocationInfo {
    if (!hasLocationPermission()) {
      return DeviceLocationInfo(
        isAvailable = false,
        descriptionBurmese = "တည်နေရာ ခွင့်ပြုချက် (Location Permission) မရရှိသေးပါ"
      )
    }

    val lm = locationManager ?: return DeviceLocationInfo(
      isAvailable = false,
      descriptionBurmese = "စက်ပစ္စည်း၏ Location Service ကို ရယူ၍ မရပါ"
    )

    val isGpsEnabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
    val isNetworkEnabled = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }

    if (!isGpsEnabled && !isNetworkEnabled) {
      return DeviceLocationInfo(
        isAvailable = false,
        descriptionBurmese = "ဖုန်း၏ GPS/Location ကို ဆက်တင်တွင် ဖွင့်ထားခြင်း မရှိပါ"
      )
    }

    val gpsLocation = if (isGpsEnabled) {
      try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
    } else null

    val networkLocation = if (isNetworkEnabled) {
      try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
    } else null

    val bestLocation: Location? = when {
      gpsLocation != null && networkLocation != null -> {
        if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
      }
      gpsLocation != null -> gpsLocation
      networkLocation != null -> networkLocation
      else -> null
    }

    return if (bestLocation != null) {
      val lat = bestLocation.latitude
      val lon = bestLocation.longitude
      val acc = bestLocation.accuracy
      DeviceLocationInfo(
        isAvailable = true,
        latitude = lat,
        longitude = lon,
        accuracyMeters = acc,
        provider = bestLocation.provider,
        descriptionBurmese = "လက်ရှိ တည်နေရာ လတ္တီတွဒ်: ${"%.4f".format(lat)}°, လောင်ဂျီတွဒ်: ${"%.4f".format(lon)}° (တိကျမှု: ${"%.0f".format(acc)} မီတာ)"
      )
    } else {
      DeviceLocationInfo(
        isAvailable = false,
        descriptionBurmese = "GPS တည်နေရာ ရှာဖွေနေဆဲဖြစ်ပါသည် (နောက်ဆုံးသိရှိသော တည်နေရာ မရှိသေးပါ)"
      )
    }
  }
}
