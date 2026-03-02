package com.pocketnarc.privacyshield.ui.screens.audio

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pocketnarc.privacyshield.ui.screens.lens.ForensicBullet

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudioMonitorScreen(onNavigateBack: () -> Unit) {
    val viewModel: AudioMonitorViewModel = viewModel()
    val micPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val isCalibrating by viewModel.isCalibrating.collectAsState()
    val calibrationProgress by viewModel.calibrationProgress.collectAsState()
    val forensicMessage by viewModel.forensicMessage.collectAsState()
    val decibels by viewModel.decibels.collectAsState()
    val highFreqActivity by viewModel.highFreqActivity.collectAsState()
    val spectrumData by viewModel.spectrumData.collectAsState()
    val summary by viewModel.lastScanSummary.collectAsState()

    var showInstructions by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_AUDIO_AUDIT", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
                AudioInstructions(onStart = { showInstructions = false })
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Dashboard
                    Surface(
                        color = Color(0xFF1A1A1A),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, MaterialTheme.shapes.medium)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = if (highFreqActivity > 0.7f) Color.Red else if (isCalibrating) Color.Yellow else Color(0xFF00E676)
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = forensicMessage,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            
                            if (isCalibrating) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { calibrationProgress },
                                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                                    color = Color.Yellow,
                                    trackColor = Color.DarkGray
                                )
                            } else {
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    ForensicStat("PEAK_AMP", "${decibels.toInt()} dB")
                                    ForensicStat("HF_ACTIVITY", if (highFreqActivity > 0.7f) "CRITICAL" else "NOMINAL")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Spectrum with Red Alert Pulse
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF050505))
                            .border(1.dp, if (highFreqActivity > 0.7f) Color.Red else Color.DarkGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isMonitoring && !isCalibrating) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("READY_FOR_ACOUSTIC_PROBE", color = Color.DarkGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                if (summary != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(summary!!, color = Color(0xFF00E676), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                            }
                        } else {
                            SpectrumCanvas(spectrumData, highFreqActivity)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Control
                    if (micPermissionState.status.isGranted) {
                        Button(
                            onClick = { viewModel.startBurstScan() },
                            enabled = !isMonitoring && !isCalibrating,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                disabledContainerColor = Color.DarkGray
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(if (isMonitoring) Icons.Default.GraphicEq else Icons.Default.Mic, null, tint = Color.Black)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (isCalibrating) "CALIBRATING..." else if (isMonitoring) "INTERROGATING..." else "START FORENSIC BURST",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Button(onClick = { micPermissionState.launchPermissionRequest() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)) {
                            Text("GRANT_MIC_ACCESS", color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Cross-reference sustained high-frequency spikes with physical Lens and Magnetic sweeps.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioInstructions(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "ACOUSTIC_PROBE_PROTOCOL",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        ForensicBullet("CALIBRATION", "App will map room background noise for 5 seconds. Remain perfectly silent.")
        ForensicBullet("LIMITATION", "Phone mics roll off above 18kHz. True ultrasound may be missed.")
        ForensicBullet("DETECTION", "Flags near-ultrasonic hums, electronic whines, and tracker beacons.")
        ForensicBullet("CROSS_CHECK", "If suspicious hum detected, investigate with physical lens sweep.")
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("I UNDERSTAND - START PROBE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun SpectrumCanvas(data: FloatArray, highFreqActivity: Float) {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val barWidth = size.width / data.size
        val brush = Brush.verticalGradient(
            colors = listOf(
                if (highFreqActivity > 0.7f) Color.Red else Color(0xFF00E676),
                Color(0xFF004D40)
            )
        )
        
        data.forEachIndexed { index, value ->
            val barHeight = (value * size.height).coerceAtMost(size.height)
            drawRect(
                brush = brush,
                topLeft = Offset(index * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 2.dp.toPx(), barHeight)
            )
        }
    }
}

@Composable
fun ForensicStat(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
