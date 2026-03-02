package com.pocketnarc.privacyshield.ui.screens.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import androidx.core.util.size

data class BluetoothDevice(
    val address: String,
    val name: String = "Unknown Tracker",
    val rssi: Int,
    val manufacturer: String = "Unknown",
    val isTrackingRisk: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

class BluetoothScannerViewModel : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BluetoothDevice>()
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val name = try { result.device.name ?: "Unknown" } catch (_: SecurityException) { "Protected Device" }
            val rssi = result.rssi
            
            // Detect common tracking risk keywords
            val isRisk = name.lowercase().contains("tag") || 
                         name.lowercase().contains("track") || 
                         name.lowercase().contains("beacon") ||
                         (result.scanRecord?.manufacturerSpecificData?.size ?: 0) > 0

            val manufacturer = when {
                name.contains("Apple", true) || name.contains("AirTag", true) -> "Apple (AirTag/FindMy)"
                name.contains("Tile", true) -> "Tile Tracker"
                else -> "Generic BLE"
            }

            val device = BluetoothDevice(
                address = address,
                name = name,
                rssi = rssi,
                manufacturer = manufacturer,
                isTrackingRisk = isRisk
            )
            
            deviceMap[address] = device
            _discoveredDevices.value = deviceMap.values.sortedByDescending { it.rssi }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        if (_isScanning.value) return

        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = manager.adapter

            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
                _isScanning.value = true
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothScanner", "Permission denied mid-scan", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        _isScanning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
