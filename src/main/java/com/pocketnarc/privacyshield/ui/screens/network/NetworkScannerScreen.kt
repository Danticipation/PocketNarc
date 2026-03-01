package com.pocketnarc.privacyshield.ui.screens.network

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
                        DeviceCard(device)
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
fun DeviceCard(device: NetworkDevice) {
    val icon = when (device.type) {
        DeviceType.CAMERA -> Icons.Default.Videocam
        DeviceType.SMART_HOME -> Icons.Default.Router
        DeviceType.MOBILE -> Icons.Default.Smartphone
        DeviceType.COMPUTER -> Icons.Default.Computer
        else -> Icons.Default.Devices
    }

    val color = if (device.type == DeviceType.CAMERA) Color.Red else Color(0xFF00E676)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (device.type == DeviceType.CAMERA) Color.Red.copy(alpha = 0.5f) else Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
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
                if (device.ports.isNotEmpty()) {
                    Text(
                        text = "OPEN_PORTS: ${device.ports.joinToString(", ")}",
                        color = color.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            if (device.type == DeviceType.CAMERA) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Threat Detected",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
