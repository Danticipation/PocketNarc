package com.pocketnarc.privacyshield.ui.screens.magnetometer

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketnarc.privacyshield.ui.screens.lens.ForensicBullet
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetometerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sensorManager = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager }
    val vibrator = remember { 
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
    }
    
    val viewModel: MagnetometerViewModel = viewModel(factory = MagnetometerViewModelFactory(sensorManager, vibrator))
    
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(viewModel)
        onDispose { lifecycleOwner.lifecycle.removeObserver(viewModel) }
    }

    val status by viewModel.status.collectAsState()
    val deviation by viewModel.deviation.collectAsState()
    val isCalibrating by viewModel.isCalibrating.collectAsState()
    val progress by viewModel.calibrationProgress.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val history by viewModel.anomalyHistory.collectAsState()
    
    var showInstructions by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MAGNETIC_ANOMALY_SCANNER", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                MagneticInstructions(onStart = { 
                    showInstructions = false
                    viewModel.startCalibration()
                })
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    
                    Surface(
                        color = Color(0xFF1A1A1A),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, MaterialTheme.shapes.medium)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = when(status) {
                                    MagneticStatus.STABLE -> Color(0xFF00E676)
                                    MagneticStatus.ELEVATED -> Color.Yellow
                                    MagneticStatus.ANOMALY -> Color.Red
                                    MagneticStatus.INTERFERENCE -> Color.Blue
                                }
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (isCalibrating) "CALIBRATING_BASELINE..." else if (status == MagneticStatus.INTERFERENCE) "SIGNAL_INTERFERENCE" else status.name,
                                    color = if (status == MagneticStatus.INTERFERENCE) Color.Blue else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            
                            if (isCalibrating) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = Color.Yellow, trackColor = Color.DarkGray)
                            } else {
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    ForensicMetric("STABLE_DEV", "${deviation.toInt()} μT")
                                    ForensicMetric("SENSITIVITY", sensitivity.name)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                        AnomalyGauge(deviation, status)
                    }

                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)).padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SensitivityLevel.values().forEach { level ->
                            val isSelected = sensitivity == level
                            TextButton(
                                onClick = { viewModel.setSensitivity(level) },
                                modifier = Modifier.weight(1f).background(if (isSelected) Color(0xFF00E676) else Color.Transparent, RoundedCornerShape(4.dp)),
                                colors = ButtonDefaults.textButtonColors(contentColor = if (isSelected) Color.Black else Color.Gray)
                            ) {
                                Text(level.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Anomaly Log with Clear Button
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("FORENSIC_LOG", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        if (history.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearLogs() }, contentPadding = PaddingValues(0.dp)) {
                                Text("CLEAR_LOG", color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { log ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (log.status == MagneticStatus.ANOMALY) Color.Red else Color.Yellow))
                                    Spacer(Modifier.width(8.dp))
                                    Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.time)), color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                Text("> ${log.magnitude} μT SHIFT", color = if (log.status == MagneticStatus.ANOMALY) Color.Red else Color.Yellow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.startCalibration() },
                        enabled = !isCalibrating,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("RE-CALIBRATE BASELINE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun AnomalyGauge(deviation: Float, status: MagneticStatus) {
    val color = when(status) {
        MagneticStatus.STABLE -> Color(0xFF00E676)
        MagneticStatus.ELEVATED -> Color.Yellow
        MagneticStatus.ANOMALY -> Color.Red
        MagneticStatus.INTERFERENCE -> Color.Blue
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2
        drawCircle(color = Color.DarkGray, radius = radius, style = Stroke(1.dp.toPx()))
        val normalizedPulse = (abs(deviation).coerceIn(0f, 150f) / 150f)
        val pulseRadius = (radius * normalizedPulse).coerceAtLeast(10f)
        drawCircle(color = color.copy(alpha = 0.2f), radius = pulseRadius)
        drawCircle(color = color, radius = pulseRadius, style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun ForensicMetric(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun MagneticInstructions(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Waves, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "MAGNETIC_ANOMALY_SCAN",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        ForensicBullet("CALIBRATION", "Hold phone in open air away from metal during init.")
        ForensicBullet("SLOW_SWEEP", "Move phone 5-15cm over surfaces slowly.")
        ForensicBullet("CROSS_CHECK", "Combine with Bluetooth scanner for active transmitters.")
        ForensicBullet("LIMITATION", "Will flag outlets, speakers, and wall studs.")
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("INITIALIZE SCANNER", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
