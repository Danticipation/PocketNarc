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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val statusColor by animateColorAsState(
        targetValue = when {
            anomalyScore > 0.7f -> Color(0xFFFF3333)
            anomalyScore > 0.3f -> Color(0xFFFFBB33)
            else -> Color(0xFF00E676)
        },
        animationSpec = tween(400),
        label = "StatusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "TechAnimations")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (anomalyScore > 0.7f) 1.15f else if (anomalyScore > 0.3f) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (anomalyScore > 0.7f) 350 else 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val flickerAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (anomalyScore > 0.7f) 0.85f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Flicker"
    )

    val scanlineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scanline"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_MAG_SCANNER", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = statusColor,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
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
                Spacer(Modifier.height(48.dp))
                Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                    Text("INITIALIZE SENSORS")
                }
            } else {
                // INTEGRATED LOGO
                AsyncImage(
                    model = "file:///android_asset/Logo_1.png",
                    contentDescription = "PrivatAid Logo",
                    modifier = Modifier
                        .height(40.dp)
                        .alpha(0.6f),
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "FIELD MAGNITUDE",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${"%.1f".format(magnitude)} μT",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "BASELINE: ${"%.1f".format(baseline)} μT",
                    color = statusColor.copy(alpha = flickerAlpha),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to statusColor.copy(alpha = 0.4f * (if(anomalyScore > 0.7f) flickerAlpha else 1f)),
                                    0.8f to Color.Transparent
                                ),
                                radius = size.minDimension / 1.1f
                            )
                            
                            val y = scanlineOffset * size.height
                            drawLine(
                                color = statusColor.copy(alpha = 0.2f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2.dp.toPx()
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
                        contentDescription = "Status",
                        modifier = Modifier
                            .size(200.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .border(1.dp, statusColor.copy(alpha = 0.15f), CircleShape)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // FIX FOR TEXT SHIFTING/WRAPPING
                Text(
                    text = when {
                        anomalyScore > 0.7f -> "!! ANOMALY DETECTED !!"
                        anomalyScore > 0.3f -> "> ELEVATED LEVELS <"
                        else -> "SYSTEM_SECURE"
                    },
                    style = MaterialTheme.typography.titleMedium, // Reduced from titleLarge
                    textAlign = TextAlign.Center,
                    color = statusColor.copy(alpha = flickerAlpha),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(40.dp))

                if (isCalibrating) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { calibrationProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = statusColor,
                            trackColor = Color.DarkGray
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "CALIBRATING SENSORS...", 
                            color = Color.LightGray, 
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.startCalibration() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("RE-CALIBRATE SYSTEM", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
