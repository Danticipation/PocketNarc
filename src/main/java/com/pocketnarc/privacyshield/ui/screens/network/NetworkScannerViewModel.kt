package com.pocketnarc.privacyshield.ui.screens.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class NetworkDevice(
    val ip: String,
    val hostname: String = "Unknown Device",
    val type: DeviceType = DeviceType.GENERIC,
    val ports: List<Int> = emptyList()
)

enum class DeviceType {
    GENERIC, CAMERA, SMART_HOME, MOBILE, COMPUTER
}

class NetworkScannerViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val devices: StateFlow<List<NetworkDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    fun startScan(context: Context) {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            _devices.value = emptyList()
            _scanProgress.value = 0f

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wifiManager.connectionInfo
            val ipAddress = connectionInfo.ipAddress
            val ipString = Formatter.formatIpAddress(ipAddress)

            if (ipString == "0.0.0.0") {
                _isScanning.value = false
                return@launch
            }

            val prefix = ipString.substring(0, ipString.lastIndexOf(".") + 1)
            
            val foundDevices = mutableListOf<NetworkDevice>()

            withContext(Dispatchers.IO) {
                // Scanning 1 to 254
                for (i in 1..254) {
                    val testIp = prefix + i
                    _scanProgress.value = i / 254f
                    
                    try {
                        val address = InetAddress.getByName(testIp)
                        if (address.isReachable(300)) {
                            // Check for common camera ports
                            val openPorts = checkCommonPorts(testIp)
                            val device = NetworkDevice(
                                ip = testIp,
                                hostname = address.canonicalHostName,
                                type = identifyDevice(address.canonicalHostName, openPorts),
                                ports = openPorts
                            )
                            foundDevices.add(device)
                            _devices.value = foundDevices.toList()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            _isScanning.value = false
            _scanProgress.value = 1f
        }
    }

    private fun checkCommonPorts(ip: String): List<Int> {
        val portsToScan = listOf(80, 443, 554, 8080, 1935, 8000, 37777) // Common camera/web ports
        val openPorts = mutableListOf<Int>()
        
        for (port in portsToScan) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 100)
                socket.close()
                openPorts.add(port)
            } catch (e: Exception) {
                // Port closed
            }
        }
        return openPorts
    }

    private fun identifyDevice(hostname: String, openPorts: List<Int>): DeviceType {
        val lowerHost = hostname.lowercase()
        return when {
            lowerHost.contains("cam") || lowerHost.contains("camera") || lowerHost.contains("ipview") || 
            openPorts.contains(554) || openPorts.contains(1935) || openPorts.contains(37777) -> DeviceType.CAMERA
            lowerHost.contains("smart") || lowerHost.contains("hub") || lowerHost.contains("echo") || lowerHost.contains("nest") -> DeviceType.SMART_HOME
            lowerHost.contains("iphone") || lowerHost.contains("android") || lowerHost.contains("galaxy") -> DeviceType.MOBILE
            lowerHost.contains("mac") || lowerHost.contains("pc") || lowerHost.contains("desktop") || lowerHost.contains("laptop") -> DeviceType.COMPUTER
            else -> DeviceType.GENERIC
        }
    }
}
