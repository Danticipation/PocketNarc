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
import com.pocketnarc.privacyshield.ui.screens.lens.ForensicBullet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScannerScreen(onNavigateBack: () -> Unit) {
    val viewModel: NetworkScannerViewModel = viewModel()
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val currentStatus by viewModel.currentStatus.collectAsState()
    val deepScanResults by viewModel.deepScanResults.collectAsState()

    var showInstructions by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_NET_AUDIT", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
                NetworkInstructions(onStart = { showInstructions = false })
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    text = if (isScanning) currentStatus else "AUDIT_COMPLETE",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            
                            if (isScanning) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { scanProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = Color(0xFF00E676),
                                    trackColor = Color.DarkGray
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${devices.size} nodes identified on local subnet",
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
                                "NO_FOREIGN_DEVICES_DETECTED",
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
                            text = if (isScanning) "AUDIT_IN_PROGRESS..." else "INITIALIZE NETWORK SCAN",
                            color = Color.Black,
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
fun ForensicDeviceCard(
    device: NetworkDevice,
    deepScanResult: DeepScanResult?,
    onStartDeepScan: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isThreat = device.isThreat || (device.type == DeviceType.CAMERA)
    
    val icon = when (device.type) {
        DeviceType.CAMERA -> Icons.Default.Videocam
        DeviceType.SMART_HOME -> Icons.Default.Router
        DeviceType.MOBILE -> Icons.Default.Smartphone
        DeviceType.COMPUTER -> Icons.Default.Computer
        DeviceType.ROUTER -> Icons.Default.SettingsEthernet
        DeviceType.PRINTER -> Icons.Default.Print
        else -> Icons.Default.Devices
    }

    val color = when {
        isThreat -> Color(0xFFFF5252)
        device.type == DeviceType.PRINTER -> Color(0xFFFFB74D)
        else -> Color(0xFF00E676)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isThreat) Color.Red.copy(alpha = 0.5f) else Color.DarkGray
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
                
                if (isThreat) {
                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                        Text(if (device.type == DeviceType.CAMERA) "CAMERA" else "THREAT", modifier = Modifier.padding(horizontal = 4.dp))
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
                    if (device.manufacturer != "Unknown") {
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
                    
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "DEEP_PROBE_REPORT",
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
                                        "RUN_AUDIT",
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
                                        color = if (detail.contains("HIGH STEALTH")) Color.Yellow else if (detail.contains("OPEN")) Color.Red else Color.Gray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    if (isThreat) {
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "FORENSIC WARNING: Device signature matches surveillance hardware profiles. High probability of unauthorized monitoring.",
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
fun NetworkInstructions(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "NETWORK_AUDIT_PROTOCOL",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        ForensicBullet("CALIBRATION", "Only scans the local Wi-Fi subnet. Cellular and VPN should be inactive.")
        ForensicBullet("LIMITATION", "Stealth devices (cloud-only) may not respond to local discovery.")
        ForensicBullet("LEGAL_NOTICE", "Only scan networks you have explicit permission to audit.")
        ForensicBullet("PRO_TIP", "Use 2.4 GHz bands if possible; IoT devices rarely use 5 GHz.")
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("I UNDERSTAND - START AUDIT", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ForensicRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "$label: ", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
fun PortDetail(port: Int) {
    val description = when (port) {
        80 -> "HTTP (Web Server)"
        443 -> "HTTPS (Secure Web)"
        554 -> "RTSP (Streaming - CAMERA)"
        1935 -> "RTMP (Streaming - CAMERA)"
        8000 -> "Hikvision Service"
        8080 -> "Web Admin Portal"
        9100 -> "Printer Port"
        else -> "Active Service"
    }
    Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Adjust, null, tint = Color(0xFF00E676), modifier = Modifier.size(8.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = "$port - $description", color = if (port in listOf(554, 1935, 8000)) Color.Red else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
