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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class NetworkDevice(
    val ip: String,
    val hostname: String = "Unknown Device",
    val type: DeviceType = DeviceType.GENERIC,
    val ports: List<Int> = emptyList(),
    val manufacturer: String = "Unknown",
    val modelName: String = "Unknown"
)

data class DeepScanResult(
    val ip: String,
    val details: List<String> = emptyList(),
    val isScanning: Boolean = false
)

enum class DeviceType {
    GENERIC, CAMERA, SMART_HOME, MOBILE, COMPUTER, ROUTER
}

class NetworkScannerViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val devices: StateFlow<List<NetworkDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

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
                    _isScanning.value = false
                    return@launch
                }

                val prefix = ipString.substring(0, ipString.lastIndexOf(".") + 1)
                
                startDiscovery(context)
                launch(Dispatchers.IO) { discoverUPnP() }

                withContext(Dispatchers.IO) {
                    val jobs = mutableListOf<Job>()
                    val totalIps = 254
                    var scannedCount = 0

                    for (i in 1..254) {
                        val testIp = prefix + i
                        val job = launch {
                            try {
                                val address = InetAddress.getByName(testIp)
                                if (address.isReachable(500)) {
                                    val openPorts = checkCommonPorts(testIp)
                                    val existing = foundDevicesMap[testIp]
                                    
                                    val device = NetworkDevice(
                                        ip = testIp,
                                        hostname = existing?.hostname ?: address.canonicalHostName,
                                        type = identifyDevice(testIp, existing?.hostname ?: address.canonicalHostName, openPorts),
                                        ports = openPorts,
                                        manufacturer = existing?.manufacturer ?: "Unknown",
                                        modelName = existing?.modelName ?: "Unknown"
                                    )
                                    foundDevicesMap[testIp] = device
                                    updateDeviceList()
                                }
                            } catch (_: Exception) { }
                            
                            synchronized(this@NetworkScannerViewModel) {
                                scannedCount++
                                _scanProgress.value = scannedCount / totalIps.toFloat()
                            }
                        }
                        jobs.add(job)
                        if (i % 30 == 0) delay(20)
                    }
                    jobs.joinAll()
                }
            } catch (e: Exception) {
                Log.e("NetworkScanner", "Scan failed", e)
            } finally {
                stopDiscovery()
                _isScanning.value = false
                _scanProgress.value = 1f
            }
        }
    }

    private fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network: Network? = connectivityManager.activeNetwork
        val capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(network)
        
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(network)
            return linkProperties?.linkAddresses?.find { it.address is java.net.Inet4Address }?.address?.hostAddress
        }
        return null
    }

    private fun discoverUPnP() {
        val ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: ssdp:all\r\n\r\n"

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
                        
                        val serverLine = response.lines().find { it.startsWith("SERVER:", ignoreCase = true) }
                        if (serverLine != null) {
                            val manufacturer = serverLine.substringAfter("SERVER:").trim()
                            val existing = foundDevicesMap[ip]
                            if (existing != null) {
                                foundDevicesMap[ip] = existing.copy(manufacturer = manufacturer)
                                updateDeviceList()
                            }
                        }
                    } catch (_: Exception) { break }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkScanner", "UPnP Scan failed", e)
        }
    }

    private fun updateDeviceList() {
        _devices.value = foundDevicesMap.values.sortedBy { 
            it.ip.substringAfterLast(".").toIntOrNull() ?: 0 
        }
    }

    fun startDeepScan(ip: String) {
        viewModelScope.launch {
            _deepScanResults.value = _deepScanResults.value + (ip to DeepScanResult(ip, isScanning = true))
            
            val details = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val forensicPorts = mapOf(
                    21 to "FTP", 22 to "SSH", 23 to "Telnet", 554 to "RTSP (CAMERA)",
                    1935 to "RTMP (CAMERA)", 37777 to "Dahua/Lorex", 8000 to "Hikvision",
                    8080 to "Web Admin", 9000 to "IP Cam"
                )

                for ((port, desc) in forensicPorts) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 250)
                            details.add("PORT $port: OPEN ($desc)")
                        }
                    } catch (_: Exception) {}
                }
                
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, 80), 500)
                        val out = socket.getOutputStream()
                        out.write("GET / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n".toByteArray())
                        val reader = socket.getInputStream().bufferedReader()
                        for (idx in 1..20) {
                            val line = reader.readLine() ?: break
                            if (line.contains("Server:", ignoreCase = true)) details.add("IDENTITY: ${line.trim()}")
                            if (line.contains("<title>", ignoreCase = true)) {
                                val title = line.substringAfter("<title>").substringBefore("</title>").trim()
                                details.add("PAGE_TITLE: \"$title\"")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            
            if (details.isEmpty()) details.add("No additional markers found.")
            _deepScanResults.value = _deepScanResults.value + (ip to DeepScanResult(ip, details, isScanning = false))
        }
    }

    private fun startDiscovery(context: Context) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val serviceTypes = listOf("_http._tcp", "_googlecast._tcp", "_axis-video._tcp", "_onvif._tcp")
        
        serviceTypes.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        nsdManager?.registerServiceInfoCallback(service, { runnable -> runnable.run() }, object : NsdManager.ServiceInfoCallback {
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                updateService(serviceInfo, serviceType)
                            }
                            override fun onServiceLost() {}
                            override fun onServiceInfoCallbackUnregistered() {}
                        })
                    } else {
                        @Suppress("DEPRECATION")
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                updateService(serviceInfo, serviceType)
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(regType: String) {}
                override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
            }
            discoveryListeners.add(listener)
            try { nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) {}
        }
    }

    private fun updateService(serviceInfo: NsdServiceInfo, serviceType: String) {
        val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host
        }
        
        val ip = host?.hostAddress ?: return
        val existing = foundDevicesMap[ip]
        if (existing != null) {
            foundDevicesMap[ip] = existing.copy(
                hostname = serviceInfo.serviceName ?: "Unknown",
                type = if (serviceType.contains("video") || serviceType.contains("onvif")) DeviceType.CAMERA else existing.type
            )
            updateDeviceList()
        }
    }

    private fun stopDiscovery() {
        discoveryListeners.forEach { listener -> try { nsdManager?.stopServiceDiscovery(listener) } catch (_: Exception) {} }
        discoveryListeners.clear()
        nsdManager = null
    }

    override fun onCleared() { super.onCleared(); stopDiscovery() }

    private fun checkCommonPorts(ip: String): List<Int> {
        val ports = listOf(80, 443, 554, 1935, 8000, 8080, 37777)
        val open = mutableListOf<Int>()
        for (port in ports) {
            try { Socket().use { it.connect(InetSocketAddress(ip, port), 120); open.add(port) } } catch (_: Exception) {}
        }
        return open
    }

    private fun identifyDevice(ip: String, hostname: String, openPorts: List<Int>): DeviceType {
        val low = hostname.lowercase()
        return when {
            ip.endsWith(".1") -> DeviceType.ROUTER
            low.contains("cam") || low.contains("camera") || openPorts.contains(554) || openPorts.contains(8000) -> DeviceType.CAMERA
            low.contains("smart") || low.contains("echo") || low.contains("hub") || low.contains("tv") -> DeviceType.SMART_HOME
            low.contains("phone") || low.contains("android") || low.contains("ios") -> DeviceType.MOBILE
            else -> DeviceType.GENERIC
        }
    }
}
