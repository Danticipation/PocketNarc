package com.pocketnarc.privacyshield.ui.screens.bluetooth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketnarc.privacyshield.ui.screens.lens.ForensicBullet
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScannerScreen(onNavigateBack: () -> Unit) {
    val viewModel: BluetoothScannerViewModel = viewModel()
    val context = LocalContext.current
    val devices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val progress by viewModel.scanProgress.collectAsState()
    val bluetoothDisabled by viewModel.bluetoothDisabled.collectAsState()

    var showInstructions by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_BLE_HUNT", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showInstructions) {
                BluetoothInstructions(onStart = { showInstructions = false })
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    text = if (isScanning) "BLE_TRACKER_HUNT_ACTIVE" else "SCAN_IDLE",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            
                            if (isScanning) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = Color(0xFF00E676),
                                    trackColor = Color.DarkGray
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${devices.size} proximity beacons identified",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (devices.isEmpty() && !isScanning) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                "NO_BEACONS_IDENTIFIED",
                                color = Color.DarkGray,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
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

                    if (bluetoothDisabled) {
                        Surface(
                            color = Color(0xFF330000),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Bluetooth is disabled. Enable it in Settings to scan for trackers.",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = { viewModel.startScan(context) },
                        enabled = !isScanning,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (isScanning) "INTERROGATING..." else "START BLE AUDIT",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
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
                    text = "VEND: ${device.manufacturer}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RSSI: ${device.rssi} dBm",
                    color = Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (device.isTrackingRisk) {
                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                        Text("RISK", modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(device.lastSeen)),
                    color = Color.DarkGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BluetoothInstructions(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "BLE_HUNT_PROTOCOL",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        ForensicBullet("AWARENESS", "BLE scans detect trackers like AirTags and Tiles via proximity beacons.")
        ForensicBullet("SYSTEM_NOTE", "Android also provides 'Unknown Tracker Alerts' in System Settings.")
        ForensicBullet("FORENSICS", "Watch for high RSSI (signal strength) to hunt physical locations.")
        ForensicBullet("LIMITATION", "Devices with disabled advertising or rotating IDs may be harder to lock.")
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("I UNDERSTAND - START HUNT", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
