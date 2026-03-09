package com.pocketnarc.privacyshield.ui.screens.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
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
    val lastSeen: Long = System.currentTimeMillis(),
    val signature: String // Name + Manufacturer Data hash to track rotating IDs
)

class BluetoothScannerViewModel : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _bluetoothDisabled = MutableStateFlow(false)
    val bluetoothDisabled: StateFlow<Boolean> = _bluetoothDisabled.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BluetoothDevice>()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val name = try { result.device.name ?: "Unknown Beacon" } catch (_: SecurityException) { "Protected Device" }
            val rssi = result.rssi
            val mfgData = result.scanRecord?.manufacturerSpecificData
            
            // Forensic Signature (to track devices that rotate MAC addresses)
            val mfgString = mfgData?.let { data ->
                val sb = StringBuilder()
                for (i in 0 until data.size) {
                    sb.append(data.keyAt(i))
                    sb.append(data.valueAt(i).contentToString())
                }
                sb.toString()
            } ?: ""
            val signature = "$name|$mfgString"

            // Improved Risk Detection (Manufacturer Parsing)
            val appleData = mfgData?.get(0x004C) // Apple ID
            val isAppleTracker = appleData != null && appleData.size >= 2
            
            val isRisk = isAppleTracker || 
                         name.contains("Tag", true) || 
                         name.contains("Tile", true) || 
                         name.contains("Tracker", true) ||
                         name.contains("FindMy", true)

            val manufacturer = when {
                isAppleTracker -> "Apple (AirTag/FindMy)"
                name.contains("Tile", true) -> "Tile Tracker"
                name.contains("Samsung", true) -> "Samsung SmartTag"
                (mfgData?.size ?: 0) > 0 -> "Active BLE Beacon"
                else -> "Generic Device"
            }

            val device = BluetoothDevice(
                address = address,
                name = name,
                rssi = rssi,
                manufacturer = manufacturer,
                isTrackingRisk = isRisk,
                signature = signature
            )
            
            // If we find a new address with the SAME signature, it's likely a rotating ID tracker
            val existingBySig = deviceMap.values.find { it.signature == signature && it.address != address }
            if (existingBySig != null) {
                // Keep the record but flag it as potentially rotating
                Log.d("Forensics", "Likely rotating ID detected for signature: $signature")
            }

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

            if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
                _bluetoothDisabled.value = true
                return
            }
            _bluetoothDisabled.value = false

            deviceMap.clear()
            _discoveredDevices.value = emptyList()
            bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            _isScanning.value = true

            // Automatic 60-second timeout for battery safety
            scanJob = viewModelScope.launch {
                val duration = 60000L
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < duration) {
                    _scanProgress.value = (System.currentTimeMillis() - startTime) / duration.toFloat()
                    delay(500)
                }
                stopScan()
            }
        } catch (e: SecurityException) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        _isScanning.value = false
        _scanProgress.value = 1f
        scanJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
