package com.example.realworld

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Item 3: Dynamic IP Discovery Engine for ESP32 Wi-Fi IR Blaster.
 * Eliminates the need for manual static IP entry.
 *
 * Strategies employed concurrently:
 * 1. Android NSD / mDNS Service Discovery (_http._tcp / _esp32._tcp)
 * 2. UDP Broadcast Beacon Listener & Active Ping Probe on Port 8266 / 4210 / 8888
 * 3. Local Subnet Fast Parallel Prober (scans /24 subnet for /api/status endpoint)
 */
data class Esp32DiscoveredDevice(
  val ip: String,
  val hostName: String = "esp32-ir-blaster",
  val port: Int = 80,
  val discoveryMethod: String = "mDNS",
  val isVerified: Boolean = false
)

data class NetworkEnvironmentInfo(
  val isWifiConnected: Boolean,
  val isCellularConnected: Boolean,
  val isEthernetConnected: Boolean,
  val currentIp: String?,
  val currentSubnet: String?,
  val environmentDescription: String,
  val isCloudOrRemote: Boolean
)

data class Esp32DiscoveryState(
  val isScanning: Boolean = false,
  val isSubnetScanning: Boolean = false,
  val statusMessage: String = "စတင်ရှာဖွေရန် အသင့်ရှိပါသည်",
  val discoveredDevices: List<Esp32DiscoveredDevice> = emptyList(),
  val activeIp: String? = null,
  val mdnsFailedDueToIsolation: Boolean = false,
  val technicalWarning: String? = null,
  val suggestedFallbackIp: String? = "192.168.1.150",
  val detectedSubnet: String? = null,
  val detectedLocalIp: String? = null
)

class Esp32DynamicDiscoveryEngine(
  private val context: Context,
  private val externalScope: CoroutineScope
) {
  private val tag = "Esp32Discovery"

  fun getNetworkEnvironment(): NetworkEnvironmentInfo {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeNet = cm?.activeNetwork
    val caps = cm?.getNetworkCapabilities(activeNet)
    val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
    var currentIp = if (ipInt != 0) {
      String.format(
        java.util.Locale.US,
        "%d.%d.%d.%d",
        ipInt and 0xff,
        ipInt shr 8 and 0xff,
        ipInt shr 16 and 0xff,
        ipInt shr 24 and 0xff
      )
    } else null

    var currentSubnet = if (ipInt != 0) {
      String.format(
        java.util.Locale.US,
        "%d.%d.%d",
        ipInt and 0xff,
        ipInt shr 8 and 0xff,
        ipInt shr 16 and 0xff
      )
    } else null

    // Fallback using NetworkInterface for Android 10+ (API 29+) when WifiManager returns 0
    if (currentIp == null || currentIp == "0.0.0.0") {
      try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
          val intf = interfaces.nextElement()
          if (intf.isLoopback || !intf.isUp) continue
          val addrs = intf.inetAddresses
          while (addrs.hasMoreElements()) {
            val addr = addrs.nextElement()
            if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
              val host = addr.hostAddress
              if (host != null && !host.startsWith("127.")) {
                val parts = host.split(".")
                if (parts.size == 4) {
                  currentIp = host
                  currentSubnet = "${parts[0]}.${parts[1]}.${parts[2]}"
                  break
                }
              }
            }
          }
          if (currentIp != null) break
        }
      } catch (_: Exception) {}
    }

    val isCloudOrRemote = !isWifi
    val desc = when {
      isWifi -> "Local Wi-Fi ကွန်ရက် (Subnet: ${currentSubnet ?: "192.168.x"}.x)"
      isCellular -> "Mobile Cellular Data (Local LAN မရှိပါ)"
      isEthernet -> "Cloud / Ethernet Bridge (Virtual Subnet)"
      else -> "ကွန်ရက် ချိတ်ဆက်မထားပါ"
    }

    return NetworkEnvironmentInfo(
      isWifiConnected = isWifi,
      isCellularConnected = isCellular,
      isEthernetConnected = isEthernet,
      currentIp = currentIp,
      currentSubnet = currentSubnet,
      environmentDescription = desc,
      isCloudOrRemote = isCloudOrRemote
    )
  }

  private val _state = MutableStateFlow(
    run {
      val env = getNetworkEnvironment()
      Esp32DiscoveryState(
        detectedSubnet = env.currentSubnet,
        detectedLocalIp = env.currentIp
      )
    }
  )
  val state: StateFlow<Esp32DiscoveryState> = _state.asStateFlow()

  private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
  private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
  private var multicastLock: WifiManager.MulticastLock? = null

  private var scanJob: Job? = null
  private var subnetScanJob: Job? = null

  private val probeClient = OkHttpClient.Builder()
    .connectTimeout(600, TimeUnit.MILLISECONDS)
    .readTimeout(800, TimeUnit.MILLISECONDS)
    .build()

  var onDeviceAutoConnected: ((String) -> Unit)? = null
  var onMdnsTimeoutDueToIsolation: (() -> Unit)? = null

  /**
   * Start comprehensive dynamic discovery across mDNS, UDP broadcast, and subnet probe.
   */
  fun startDiscovery(onFound: ((String) -> Unit)? = null) {
    if (_state.value.isScanning) {
      Log.d(tag, "Discovery is already active.")
      return
    }

    _state.value = _state.value.copy(
      isScanning = true,
      statusMessage = "ESP32 စက်များကို Local Wi-Fi ကွန်ရက်တွင် အလိုအလျောက် ရှာဖွေနေပါသည်..."
    )

    acquireMulticastLock()

    scanJob = externalScope.launch(Dispatchers.IO) {
      // 1. Launch NSD / mDNS discovery
      startNsdDiscovery()

      // 2. Launch UDP Broadcast beacon prober
      val udpJob = launch { probeUdpBroadcast() }

      // 3. Launch Subnet fast HTTP sweep
      val sweepJob = launch { sweepLocalSubnet() }

      // Run discovery for 10 seconds or until verified device found
      val startTime = System.currentTimeMillis()
      while (isActive && System.currentTimeMillis() - startTime < 10_000L) {
        val found = _state.value.discoveredDevices.firstOrNull { it.isVerified }
        if (found != null) {
          Log.i(tag, "Verified ESP32 discovered dynamically at ${found.ip} via ${found.discoveryMethod}")
          _state.value = _state.value.copy(
            statusMessage = "ESP32 IR Blaster ကို ${found.ip} တွင် အောင်မြင်စွာ ချိတ်ဆက်တွေ့ရှိပါသည်!",
            activeIp = found.ip
          )
          onFound?.invoke(found.ip)
          onDeviceAutoConnected?.invoke(found.ip)
          break
        }
        delay(500L)
      }

      udpJob.cancel()
      sweepJob.cancel()
      stopNsdDiscovery()
      releaseMulticastLock()

      val foundVerified = _state.value.discoveredDevices.any { it.isVerified }
      if (!foundVerified) {
        Log.w(tag, "Discovery timed out without finding ESP32. Possible AP Isolation or Multicast Blocking.")
        _state.value = _state.value.copy(
          isScanning = false,
          mdnsFailedDueToIsolation = true,
          technicalWarning = "Router / Mobile Hotspot တွင် Client Isolation (AP Isolation) သို့မဟုတ် Multicast Blocking ရှိနေသဖြင့် mDNS/UDP မအောင်မြင်ပါ (နည်းပညာပိုင်းဆိုင်ရာ အကန့်အသတ်)",
          statusMessage = "⚠️ mDNS အချိန်ကုန်သွားပါသည် (Router Isolation သံသယရှိ)။ Subnet Ping Scanner သို့မဟုတ် Direct IP ကို သုံးပါ"
        )
        onMdnsTimeoutDueToIsolation?.invoke()
      } else {
        _state.value = _state.value.copy(
          isScanning = false,
          mdnsFailedDueToIsolation = false,
          technicalWarning = null,
          statusMessage = "ရှာဖွေမှုပြီးဆုံး: စက် ${_state.value.discoveredDevices.size} ခု တွေ့ရှိထားပါသည်"
        )
      }
    }
  }

  fun stopDiscovery() {
    scanJob?.cancel()
    scanJob = null
    stopNsdDiscovery()
    releaseMulticastLock()
    _state.value = _state.value.copy(
      isScanning = false,
      statusMessage = "ရှာဖွေခြင်း ရပ်တန့်လိုက်ပါသည်"
    )
  }

  /**
   * Subnet Ping Scanner: Direct Unicast HTTP sweep over /24 subnets.
   * Completely bypasses Router AP Isolation / Multicast Blocking because it uses direct unicast TCP probes.
   */
  fun startSubnetPingScan(onFound: ((String) -> Unit)? = null) {
    if (_state.value.isSubnetScanning) {
      Log.d(tag, "Subnet Ping Scanner is already active.")
      return
    }

    subnetScanJob?.cancel()
    _state.value = _state.value.copy(
      isSubnetScanning = true,
      statusMessage = "Subnet Ping Scanner စတင်နေပါသည် (Unicast HTTP Direct Probe)..."
    )

    subnetScanJob = externalScope.launch(Dispatchers.IO) {
      val env = getNetworkEnvironment()
      val subnetsToScan = mutableListOf<String>()

      if (env.currentSubnet != null && !subnetsToScan.contains(env.currentSubnet)) {
        subnetsToScan.add(env.currentSubnet)
      }

      // Common gateway & hotspot subnets (including Android Hotspot 192.168.43.x)
      val commonSubnets = listOf("192.168.1", "192.168.0", "192.168.43", "192.168.8", "192.168.100")
      for (sub in commonSubnets) {
        if (!subnetsToScan.contains(sub)) {
          subnetsToScan.add(sub)
        }
      }

      var discoveredIp: String? = null
      for (subnet in subnetsToScan) {
        if (!isActive || discoveredIp != null) break

        _state.value = _state.value.copy(
          statusMessage = "Subnet $subnet.x တွင် ESP32 စက်များကို Ping ထောက်လှမ်းနေပါသည်..."
        )

        // Probe high-probability IPs first (static IP .150, .100, .200, .2 ~ .20)
        val targetHosts = listOf(150, 100, 101, 200, 2, 3, 4, 10, 20, 50, 254)
        for (host in targetHosts) {
          if (!isActive || discoveredIp != null) break
          val testIp = "$subnet.$host"
          val isOk = testEsp32Endpoint(testIp, 80)
          if (isOk) {
            discoveredIp = testIp
            verifyAndAddDevice(testIp, "ESP32-SubnetScanner", 80, "Subnet Ping Scanner")
            Log.i(tag, "Subnet Ping Scanner found ESP32 at $testIp (bypassed AP isolation)")
            break
          }
        }
      }

      if (discoveredIp != null) {
        _state.value = _state.value.copy(
          isSubnetScanning = false,
          mdnsFailedDueToIsolation = false,
          technicalWarning = null,
          activeIp = discoveredIp,
          statusMessage = "Subnet Ping Scanner မှ ESP32 ($discoveredIp) ကို အောင်မြင်စွာ တွေ့ရှိချိတ်ဆက်ပါသည်!"
        )
        onFound?.invoke(discoveredIp)
        onDeviceAutoConnected?.invoke(discoveredIp)
      } else {
        _state.value = _state.value.copy(
          isSubnetScanning = false,
          statusMessage = "Subnet Ping Scanner ဖြင့် မတွေ့ပါ။ IP Direct Manual Input တွင် ESP32 IP ထည့်သွင်းပါ"
        )
      }
    }
  }

  fun stopSubnetPingScan() {
    subnetScanJob?.cancel()
    subnetScanJob = null
    _state.value = _state.value.copy(
      isSubnetScanning = false,
      statusMessage = "Subnet Ping Scanner ရပ်တန့်လိုက်ပါသည်"
    )
  }

  /**
   * mDNS (Network Service Discovery)
   */
  private fun startNsdDiscovery() {
    try {
      nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
          Log.d(tag, "NSD Service discovery started: $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
          Log.d(tag, "NSD Service found: ${service.serviceName} (${service.serviceType})")
          val nameLower = service.serviceName.lowercase()
          if (nameLower.contains("esp") || nameLower.contains("kennede") || nameLower.contains("ir")) {
            resolveService(service)
          }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
          Log.d(tag, "NSD Service lost: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(serviceType: String) {
          Log.d(tag, "NSD Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          Log.w(tag, "NSD Start discovery failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
          Log.w(tag, "NSD Stop discovery failed: $errorCode")
        }
      }

      nsdManager?.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
    } catch (e: Exception) {
      Log.w(tag, "Failed to start NSD discovery", e)
    }
  }

  private fun resolveService(serviceInfo: NsdServiceInfo) {
    try {
      nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
          Log.w(tag, "Resolve failed: $errorCode for ${service.serviceName}")
        }

        override fun onServiceResolved(service: NsdServiceInfo) {
          val hostAddress = service.host?.hostAddress ?: return
          Log.i(tag, "NSD Service resolved: ${service.serviceName} at $hostAddress:${service.port}")
          externalScope.launch(Dispatchers.IO) {
            verifyAndAddDevice(hostAddress, service.serviceName, service.port, "mDNS/ZeroConf")
          }
        }
      })
    } catch (e: Exception) {
      Log.w(tag, "Error resolving NSD service", e)
    }
  }

  private fun stopNsdDiscovery() {
    try {
      nsdDiscoveryListener?.let {
        nsdManager?.stopServiceDiscovery(it)
      }
    } catch (e: Exception) {
      Log.w(tag, "Error stopping NSD discovery", e)
    } finally {
      nsdDiscoveryListener = null
    }
  }

  /**
   * UDP Broadcast: Sends a discovery ping to 255.255.255.255 and listens for reply
   */
  private suspend fun probeUdpBroadcast() = withContext(Dispatchers.IO) {
    var socket: DatagramSocket? = null
    try {
      socket = DatagramSocket().apply {
        broadcast = true
        soTimeout = 1500
      }

      val probeMsg = "KENNEDE_IR_DISCOVER_REQ"
      val data = probeMsg.toByteArray()
      val ports = listOf(8266, 4210, 8888, 5000)

      for (p in ports) {
        try {
          val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), p)
          socket.send(packet)
        } catch (_: Exception) {}
      }

      val buf = ByteArray(1024)
      val recvPacket = DatagramPacket(buf, buf.size)
      val deadline = System.currentTimeMillis() + 4000L

      while (isActive && System.currentTimeMillis() < deadline) {
        try {
          socket.receive(recvPacket)
          val reply = String(recvPacket.data, 0, recvPacket.length)
          val replyIp = recvPacket.address.hostAddress ?: continue
          Log.d(tag, "Received UDP response from $replyIp: $reply")
          verifyAndAddDevice(replyIp, "ESP32-UDP", 80, "UDP Broadcast")
        } catch (_: Exception) {
          // timeout, continue listening
        }
      }
    } catch (e: Exception) {
      Log.w(tag, "UDP discovery error: ${e.message}")
    } finally {
      socket?.close()
    }
  }

  /**
   * Local Subnet Sweeper: Checks common ESP32 /api/status endpoints in current /24 subnet.
   */
  private suspend fun sweepLocalSubnet() = withContext(Dispatchers.IO) {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
    if (ipInt == 0) {
      // Fallback: probe common gateway subnets
      probeSubnetRange("192.168.1")
      probeSubnetRange("192.168.0")
      return@withContext
    }

    val subnet = String.format(
      java.util.Locale.US,
      "%d.%d.%d",
      ipInt and 0xff,
      ipInt shr 8 and 0xff,
      ipInt shr 16 and 0xff
    )
    probeSubnetRange(subnet)
  }

  private suspend fun probeSubnetRange(subnetPrefix: String) = withContext(Dispatchers.IO) {
    // Priority targets: common static/DHCP IPs
    val priorityIps = listOf(
      "$subnetPrefix.150",
      "$subnetPrefix.100",
      "$subnetPrefix.101",
      "$subnetPrefix.200",
      "$subnetPrefix.2",
      "$subnetPrefix.3",
      "$subnetPrefix.4"
    )

    for (target in priorityIps) {
      if (!isActive) break
      launch {
        verifyAndAddDevice(target, "ESP32", 80, "Subnet Sweep")
      }
    }
  }

  /**
   * Validates device by querying its /api/status endpoint to ensure it's a real KENNEDE ESP32 controller
   */
  suspend fun verifyAndAddDevice(ip: String, hostName: String, port: Int, method: String): Boolean {
    val existing = _state.value.discoveredDevices.find { it.ip == ip }
    if (existing != null && existing.isVerified) return true

    val isOk = testEsp32Endpoint(ip, port)
    val device = Esp32DiscoveredDevice(
      ip = ip,
      hostName = hostName,
      port = port,
      discoveryMethod = method,
      isVerified = isOk
    )

    if (isOk) {
      val list = _state.value.discoveredDevices.filter { it.ip != ip } + device
      _state.value = _state.value.copy(
        discoveredDevices = list,
        mdnsFailedDueToIsolation = false,
        technicalWarning = null
      )
      Log.i(tag, "Successfully verified ESP32 at $ip via $method")
      return true
    }
    return false
  }

  private fun testEsp32Endpoint(ip: String, port: Int): Boolean {
    return try {
      val req = Request.Builder()
        .url("http://$ip:$port/api/status")
        .get()
        .build()
      val res = probeClient.newCall(req).execute()
      val ok = res.isSuccessful
      res.close()
      ok
    } catch (_: Exception) {
      false
    }
  }

  private fun acquireMulticastLock() {
    try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      multicastLock = wifiManager?.createMulticastLock("esp32_multicast_lock")?.apply {
        setReferenceCounted(true)
        acquire()
      }
    } catch (e: Exception) {
      Log.w(tag, "Could not acquire MulticastLock", e)
    }
  }

  private fun releaseMulticastLock() {
    try {
      if (multicastLock?.isHeld == true) {
        multicastLock?.release()
      }
    } catch (e: Exception) {
      Log.w(tag, "Could not release MulticastLock", e)
    } finally {
      multicastLock = null
    }
  }
}
