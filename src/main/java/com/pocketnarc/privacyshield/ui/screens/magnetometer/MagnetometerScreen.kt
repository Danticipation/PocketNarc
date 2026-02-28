package com.pocketnarc.privacyshield.ui.screens.magnetometer

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.pocketnarc.privacyshield.utils.rememberVibratePermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MagnetometerScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    val viewModel: MagnetometerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MagnetometerViewModel(sensorManager, vibrator) as T
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(viewModel)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(viewModel)
        }
    }

    val permissionState = rememberVibratePermissionState()
    val allPermissionsGranted = permissionState.permissions.all { it.status.isGranted }

    val anomalyScore by viewModel.anomalyScore.collectAsState()
    val magnitude by viewModel.filteredMagnitude.collectAsState()
    val baseline by viewModel.calibratedBaseline.collectAsState()
    val isCalibrating by viewModel.isCalibrating.collectAsState()
    val calibrationProgress by viewModel.calibrationProgress.collectAsState()

    // Determine status colors
    val statusColor by animateColorAsState(
        targetValue = when {
            anomalyScore > 0.7f -> Color(0xFFFF4444) // Intense Red
            anomalyScore > 0.3f -> Color(0xFFFFBB33) // Warning Orange
            else -> Color(0xFF00C851) // Safe Green
        },
        animationSpec = tween(500),
        label = "StatusColor"
    )

    // Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            anomalyScore > 0.7f -> 1.12f
            anomalyScore > 0.3f -> 1.05f
            else -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(if (anomalyScore > 0.7f) 400 else 800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = when {
            anomalyScore > 0.7f -> 0.6f
            anomalyScore > 0.3f -> 0.3f
            else -> 0.1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(if (anomalyScore > 0.7f) 400 else 800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EM Field Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black // Set background to Black to integrate the asset boxes
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!allPermissionsGranted) {
                // ... Permission UI ...
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "Vibration permission required for alerts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permission")
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Field Magnitude",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.LightGray
                )
                Text(
                    text = "${"%.1f".format(magnitude)} μT",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Baseline: ${"%.1f".format(baseline)} μT",
                    style = MaterialTheme.typography.bodyLarge,
                    color = statusColor, // Baseline text color shows the current status
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(40.dp))

                // Image Container with integrated glow and animation
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .drawBehind {
                            // Radial glow effect behind the image
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to statusColor.copy(alpha = glowAlpha),
                                    0.7f to statusColor.copy(alpha = 0f)
                                ),
                                radius = size.minDimension / 1.2f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val assetPath = when {
                        anomalyScore > 0.7f -> "file:///android_asset/warning_triangle.png"
                        anomalyScore > 0.3f -> "file:///android_asset/caution_triangle.png"
                        else -> "file:///android_asset/baseline_triangle.png"
                    }

                    AsyncImage(
                        model = assetPath,
                        contentDescription = "Alert Status",
                        modifier = Modifier
                            .size(220.dp)
                            .scale(pulseScale)
                            .clip(CircleShape) // Helps if assets have hard corners
                    )
                    
                    // Outer ring for "tech" feel
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .border(2.dp, statusColor.copy(alpha = 0.3f), CircleShape)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = when {
                        anomalyScore > 0.7f -> "STRONG ANOMALY DETECTED!"
                        anomalyScore > 0.3f -> "Elevated field detected"
                        else -> "Normal background levels"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(48.dp))

                if (isCalibrating) {
                    Surface(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { calibrationProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = statusColor,
                                trackColor = Color.Gray
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Calibrating... Hold steady",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startCalibration() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Recalibrate", 
                            color = if (statusColor == Color(0xFFFFBB33)) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
