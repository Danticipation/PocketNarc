package com.pocketnarc.privacyshield.ui.screens.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.*
import java.util.concurrent.ConcurrentHashMap

data class NetworkDevice(
    val ip: String,
    val hostname: String = "Unknown Device",
    val type: DeviceType = DeviceType.GENERIC,
    val ports: List<Int> = emptyList(),
    val manufacturer: String = "Unknown",
    val modelName: String = "Unknown",
    val isThreat: Boolean = false,
    val upnpLocation: String? = null
)

data class DeepScanResult(
    val ip: String,
    val details: List<String> = emptyList(),
    val isScanning: Boolean = false
)

enum class DeviceType {
    GENERIC, CAMERA, SMART_HOME, MOBILE, COMPUTER, ROUTER, PRINTER
}

class NetworkScannerViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val devices: StateFlow<List<NetworkDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _currentStatus = MutableStateFlow("READY_FOR_AUDIT")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val _deepScanResults = MutableStateFlow<Map<String, DeepScanResult>>(emptyMap())
    val deepScanResults: StateFlow<Map<String, DeepScanResult>> = _deepScanResults.asStateFlow()

    private val foundDevicesMap = ConcurrentHashMap<String, NetworkDevice>()
    private var nsdManager: NsdManager? = null
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun startScan(context: Context) {
        if (_isScanning.value) return

        viewModelScope.launch {
            try {
                _isScanning.value = true
                _devices.value = emptyList()
                _scanProgress.value = 0f
                foundDevicesMap.clear()

                val ipString = getLocalIpAddress(context) ?: "0.0.0.0"
                if (ipString == "0.0.0.0") {
                    _currentStatus.value = "ERROR: NO_WIFI_DETECTED"
                    _isScanning.value = false
                    return@launch
                }

                val prefix = ipString.substring(0, ipString.lastIndexOf(".") + 1)
                startDiscovery(context)
                launch(Dispatchers.IO) { discoverUPnP() }

                withContext(Dispatchers.IO) {
                    val totalIps = 254
                    var scannedCount = 0
                    
                    // Throttled parallel scan (30 at a time)
                    (1..254).chunked(30).forEach { chunk ->
                        chunk.map { i ->
                            launch {
                                val testIp = prefix + i
                                _currentStatus.value = "INTERROGATING_$testIp"
                                
                                if (isLikelyPresent(testIp)) {
                                    val openPorts = checkCommonPorts(testIp)
                                    val existing = foundDevicesMap[testIp]
                                    
                                    val device = NetworkDevice(
                                        ip = testIp,
                                        hostname = existing?.hostname ?: "Identifying...",
                                        type = identifyDevice(testIp, existing?.hostname ?: "", openPorts),
                                        ports = openPorts,
                                        manufacturer = existing?.manufacturer ?: "Unknown",
                                        isThreat = isPotentialSpyDevice(existing?.hostname ?: "", openPorts),
                                        upnpLocation = existing?.upnpLocation
                                    )
                                    foundDevicesMap[testIp] = device
                                    updateDeviceList()
                                }
                                
                                synchronized(this@NetworkScannerViewModel) {
                                    scannedCount++
                                    _scanProgress.value = scannedCount / totalIps.toFloat()
                                }
                            }
                        }.joinAll()
                        delay(50) // Hardware cool-down
                    }
                }
            } finally {
                stopDiscovery()
                _currentStatus.value = "NETWORK_AUDIT_COMPLETE"
                _isScanning.value = false
                _scanProgress.value = 1f
            }
        }
    }

    private suspend fun isLikelyPresent(ip: String): Boolean {
        // Double-check: ICMP Ping + Common Ports
        val isPingable = try { InetAddress.getByName(ip).isReachable(300) } catch (_: Exception) { false }
        if (isPingable) return true
        
        val quickPorts = listOf(80, 443, 554, 8000, 8080)
        return quickPorts.any { port ->
            try { Socket().use { it.connect(InetSocketAddress(ip, port), 150) }; true } catch (_: Exception) { false }
        }
    }

    private fun isPotentialSpyDevice(hostname: String, ports: List<Int>): Boolean {
        val lowHost = hostname.lowercase()
        val spyPorts = listOf(554, 1935, 8000, 37777, 34567, 9000)
        val spyKeywords = listOf("ring", "nest", "cam", "camera", "nvr", "hikvision", "reolink", "arlo")
        return ports.any { it in spyPorts } || spyKeywords.any { lowHost.contains(it) }
    }

    private fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: Network? = connectivityManager.activeNetwork
        val capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(network)
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(network)
            return linkProperties?.linkAddresses?.find { it.address is Inet4Address }?.address?.hostAddress
        }
        return null
    }

    private fun discoverUPnP() {
        val ssdpQuery = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: ssdp:all\r\n\r\n"
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 3000
                val group = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(ssdpQuery.toByteArray(), ssdpQuery.length, group, 1900)
                socket.send(packet)
                val buffer = ByteArray(2048)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000) {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        val ip = receivePacket.address.hostAddress ?: continue
                        val existing = foundDevicesMap[ip]
                        
                        val serverLine = response.lines().find { it.contains("SERVER:", ignoreCase = true) }?.substringAfter(":")?.trim()
                        val locationLine = response.lines().find { it.startsWith("LOCATION:", ignoreCase = true) }?.substringAfter(":")?.trim()
                        
                        if (serverLine != null || locationLine != null) {
                            val manufacturer = when {
                                serverLine?.contains("Ring", true) == true -> "Ring (Amazon)"
                                serverLine?.contains("Apple", true) == true -> "Apple Inc."
                                serverLine?.contains("HP", true) == true -> "HP"
                                serverLine != null -> serverLine.take(30)
                                else -> existing?.manufacturer ?: "Unknown"
                            }
                            foundDevicesMap[ip] = (existing ?: NetworkDevice(ip)).copy(
                                manufacturer = manufacturer,
                                type = if (manufacturer.contains("Ring") || manufacturer.contains("Cam")) DeviceType.CAMERA else (existing?.type ?: DeviceType.GENERIC),
                                isThreat = existing?.isThreat ?: manufacturer.contains("Ring"),
                                upnpLocation = locationLine ?: existing?.upnpLocation
                            )
                            updateDeviceList()
                        }
                    } catch (_: Exception) { break }
                }
            }
        } catch (_: Exception) {}
    }

    private fun updateDeviceList() {
        _devices.value = foundDevicesMap.values.sortedWith(
            compareByDescending<NetworkDevice> { it.isThreat }.thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
        )
    }

    fun startDeepScan(ip: String) {
        viewModelScope.launch {
            _deepScanResults.value = _deepScanResults.value + (ip to DeepScanResult(ip, isScanning = true))
            val details = mutableListOf<String>()
            var forensicType: DeviceType? = null
            var forensicVend = "Unknown"
            var forensicName = "Unknown Device"

            val existing = foundDevicesMap[ip]

            withContext(Dispatchers.IO) {
                existing?.upnpLocation?.let { urlString ->
                    try {
                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 800
                        val xmlText = connection.inputStream.bufferedReader().use { it.readText() }
                        val friendlyName = xmlText.substringAfter("<friendlyName>", "").substringBefore("</friendlyName>")
                        val manufacturer = xmlText.substringAfter("<manufacturer>", "").substringBefore("</manufacturer>")
                        if (friendlyName.isNotEmpty()) { details.add("FRIENDLY_NAME: $friendlyName"); forensicName = friendlyName }
                        if (manufacturer.isNotEmpty()) { details.add("MANUFACTURER: $manufacturer"); forensicVend = manufacturer }
                    } catch (_: Exception) {}
                }

                val forensicMap = mapOf(62078 to "Apple Service", 554 to "RTSP (SURVEILLANCE)", 8008 to "Google Cast", 9100 to "Printer Port", 137 to "NetBIOS")
                for ((port, desc) in forensicMap) {
                    try {
                        Socket().use { it.connect(InetSocketAddress(ip, port), 250) }
                        details.add("ACTIVE_SERVICE: $desc")
                        when (port) {
                            62078 -> { forensicType = DeviceType.MOBILE; forensicVend = "Apple" }
                            8008 -> { forensicType = DeviceType.SMART_HOME; forensicVend = "Google" }
                            554 -> { forensicType = DeviceType.CAMERA; forensicVend = "Security Cam" }
                            9100 -> { forensicType = DeviceType.PRINTER; forensicVend = "Printer" }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (existing != null) {
                foundDevicesMap[ip] = existing.copy(hostname = forensicName, type = forensicType ?: existing.type, manufacturer = forensicVend)
                updateDeviceList()
            }
            if (details.isEmpty()) details.add("HIGH STEALTH: Listening but providing zero forensic signatures.")
            _deepScanResults.value = _deepScanResults.value + (ip to DeepScanResult(ip, details, isScanning = false))
        }
    }

    private fun startDiscovery(context: Context) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val serviceTypes = listOf("_http._tcp", "_googlecast._tcp", "_apple-mobdev2._tcp", "_airplay._tcp", "_ring._tcp")
        serviceTypes.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        nsdManager?.registerServiceInfoCallback(service, { it.run() }, object : NsdManager.ServiceInfoCallback {
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                            override fun onServiceUpdated(si: NsdServiceInfo) {
                                val host = si.hostAddresses.firstOrNull() ?: return
                                updateDeviceInfo(host.hostAddress ?: return, si, type)
                            }
                            override fun onServiceLost() {}
                            override fun onServiceInfoCallbackUnregistered() {}
                        })
                    } else {
                        @Suppress("DEPRECATION")
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(si: NsdServiceInfo, err: Int) {}
                            override fun onServiceResolved(si: NsdServiceInfo) {
                                val host = si.host ?: return
                                updateDeviceInfo(host.hostAddress ?: return, si, type)
                            }
                        })
                    }
                }
                override fun onServiceLost(s: NsdServiceInfo) {}
                override fun onDiscoveryStopped(r: String) {}
                override fun onStartDiscoveryFailed(r: String, e: Int) {}
                override fun onStopDiscoveryFailed(r: String, e: Int) {}
            }
            discoveryListeners.add(listener)
            try { nsdManager?.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) {}
        }
    }

    private fun updateDeviceInfo(ip: String, si: NsdServiceInfo, type: String) {
        val existing = foundDevicesMap[ip] ?: NetworkDevice(ip)
        val name = si.serviceName
        val (deviceType, vendor) = when {
            name.contains("Ring", true) -> DeviceType.CAMERA to "Ring (Amazon)"
            name.contains("Apple", true) || type.contains("apple") || type.contains("airplay") -> DeviceType.MOBILE to "Apple"
            name.contains("Google", true) || name.contains("Nest", true) || type.contains("google") -> DeviceType.SMART_HOME to "Google"
            else -> existing.type to existing.manufacturer
        }
        foundDevicesMap[ip] = existing.copy(hostname = name, type = deviceType, manufacturer = vendor, isThreat = vendor.contains("Ring"))
        updateDeviceList()
    }

    private fun stopDiscovery() {
        discoveryListeners.forEach { try { nsdManager?.stopServiceDiscovery(it) } catch (_: Exception) {} }
        discoveryListeners.clear()
        nsdManager = null
    }

    override fun onCleared() { super.onCleared(); stopDiscovery() }

    private fun checkCommonPorts(ip: String): List<Int> {
        val ports = listOf(80, 443, 554, 1935, 8000, 8080, 62078, 8008, 9100)
        val open = mutableListOf<Int>()
        for (port in ports) {
            try { Socket().use { it.connect(InetSocketAddress(ip, port), 100); open.add(port) } } catch (_: Exception) {}
        }
        return open
    }

    private fun identifyDevice(ip: String, hostname: String, openPorts: List<Int>): DeviceType {
        val low = hostname.lowercase()
        return when {
            ip.endsWith(".1") -> DeviceType.ROUTER
            low.contains("cam") || low.contains("ring") || openPorts.contains(554) -> DeviceType.CAMERA
            low.contains("apple") || low.contains("iphone") || openPorts.contains(62078) -> DeviceType.MOBILE
            low.contains("google") || low.contains("nest") || openPorts.contains(8008) -> DeviceType.SMART_HOME
            low.contains("printer") || openPorts.contains(9100) -> DeviceType.PRINTER
            else -> DeviceType.GENERIC
        }
    }
}
