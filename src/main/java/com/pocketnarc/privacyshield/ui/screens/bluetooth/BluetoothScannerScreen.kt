package com.pocketnarc.privacyshield.ui.screens.bluetooth

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScannerScreen(onNavigateBack: () -> Unit) {
    val viewModel: BluetoothScannerViewModel = viewModel()
    val context = LocalContext.current
    val devices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_BLE_SCAN", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color(0xFF00E676),
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Dashboard Summary
            Surface(
                color = Color(0xFF1A1A1A),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isScanning) Color.Blue else Color(0xFF00E676))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isScanning) "BLE_TRACKER_HUNT_ACTIVE" else "SYSTEM_IDLE",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${devices.size} proximity beacons identified",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Device List
            if (devices.isEmpty() && !isScanning) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "INITIALIZE BEACON SCAN",
                        color = Color.DarkGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices) { device ->
                        BluetoothDeviceCard(device)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Control Button
            Button(
                onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan(context) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.Bluetooth, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isScanning) "TERMINATE SCAN" else "START BLE SCAN",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BluetoothDeviceCard(device: BluetoothDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (device.isTrackingRisk) Color.Red.copy(alpha = 0.5f) else Color.DarkGray
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isTrackingRisk) Icons.Default.Radar else Icons.Default.BluetoothDrive,
                contentDescription = null,
                tint = if (device.isTrackingRisk) Color.Red else Color(0xFF00E676),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "ID: ${device.address}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RSSI: ${device.rssi} dBm (SIGNAL_STRENGTH)",
                    color = Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            if (device.isTrackingRisk) {
                Badge(containerColor = Color.Red, contentColor = Color.White) {
                    Text("RISK", modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}
