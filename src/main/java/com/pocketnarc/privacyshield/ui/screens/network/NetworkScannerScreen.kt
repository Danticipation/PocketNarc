package com.pocketnarc.privacyshield.ui.screens.network

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScannerScreen(onNavigateBack: () -> Unit) {
    val viewModel: NetworkScannerViewModel = viewModel()
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val deepScanResults by viewModel.deepScanResults.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_NET_SCAN", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
            // Status Header
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
                                .background(if (isScanning) Color.Yellow else Color(0xFF00E676))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isScanning) "SCAN_IN_PROGRESS..." else "NETWORK_SECURE",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${devices.size} devices identified on local network",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    if (isScanning) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = Color(0xFF00E676),
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Device List
            if (devices.isEmpty() && !isScanning) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "READY TO INITIALIZE SCAN",
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
                        ForensicDeviceCard(
                            device = device,
                            deepScanResult = deepScanResults[device.ip],
                            onStartDeepScan = { viewModel.startDeepScan(device.ip) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Scan Button
            Button(
                onClick = { viewModel.startScan(context) },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    disabledContainerColor = Color.DarkGray
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (isScanning) "SCANNING..." else "START NETWORK SCAN",
                    color = if (isScanning) Color.Gray else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ForensicDeviceCard(
    device: NetworkDevice,
    deepScanResult: DeepScanResult?,
    onStartDeepScan: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val icon = when (device.type) {
        DeviceType.CAMERA -> Icons.Default.Videocam
        DeviceType.SMART_HOME -> Icons.Default.Router
        DeviceType.MOBILE -> Icons.Default.Smartphone
        DeviceType.COMPUTER -> Icons.Default.Computer
        DeviceType.ROUTER -> Icons.Default.SettingsEthernet
        else -> Icons.Default.Devices
    }

    val color = when (device.type) {
        DeviceType.CAMERA -> Color(0xFFFF5252)
        DeviceType.ROUTER -> Color(0xFF448AFF)
        else -> Color(0xFF00E676)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (device.type == DeviceType.CAMERA) Color.Red.copy(alpha = 0.5f) else Color.DarkGray
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.hostname,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "IP: ${device.ip}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                if (device.type == DeviceType.CAMERA) {
                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                        Text("THREAT", modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.DarkGray
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 12.dp))
                    
                    ForensicRow("TYPE", device.type.name)
                    if (device.manufacturer != null) {
                        ForensicRow("VEND", device.manufacturer)
                    }
                    
                    if (device.ports.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "OPEN_PORTS:",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        device.ports.forEach { port ->
                            PortDetail(port)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    // Vulnerability Scan Section
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "VULNERABILITY_REPORT",
                                    color = Color(0xFF00E676),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (deepScanResult?.isScanning == true) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF00E676), strokeWidth = 1.dp)
                                } else {
                                    Text(
                                        "RUN SCAN",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.clickable { onStartDeepScan() }
                                    )
                                }
                            }
                            
                            if (deepScanResult != null && deepScanResult.details.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                deepScanResult.details.forEach { detail ->
                                    Text(
                                        text = "> $detail",
                                        color = if (detail.contains("OPEN")) Color.Yellow else Color.Gray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    if (device.type == DeviceType.CAMERA) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = Color.Red.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "WARNING: Potential surveillance device identified. High risk of unauthorized data transmission.",
                                color = Color.Red,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForensicRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
fun PortDetail(port: Int) {
    val description = when (port) {
        80 -> "HTTP (Web Server)"
        443 -> "HTTPS (Secure Web)"
        554 -> "RTSP (Streaming - CAMERA)"
        1935 -> "RTMP (Streaming - CAMERA)"
        8000 -> "ONVIF/Common Cam"
        8080 -> "HTTP Alt"
        37777 -> "Dahua/Lorex Default"
        else -> "General Service"
    }
    
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Adjust, null, tint = Color(0xFF00E676), modifier = Modifier.size(8.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$port - $description",
            color = if (port in listOf(554, 1935, 8000, 37777)) Color(0xFFFF5252) else Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}
