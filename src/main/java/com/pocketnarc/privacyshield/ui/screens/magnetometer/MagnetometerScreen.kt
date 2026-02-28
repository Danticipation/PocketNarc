package com.pocketnarc.privacyshield.ui.screens.magnetometer

import android.content.Context
import android.hardware.SensorManager
import android.os.Vibrator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    val viewModel: MagnetometerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MagnetometerViewModel(sensorManager, vibrator) as T
            }
        }
    )

    val permissionState = rememberVibratePermissionState(
        onGranted = {
            if (viewModel.calibratedBaseline.value <= 0f) {
                viewModel.startCalibration()
            }
        }
    )

    val allPermissionsGranted = permissionState.permissions.all { it.status.isGranted }

    val anomalyScore by viewModel.anomalyScore.collectAsState()
    val magnitude by viewModel.filteredMagnitude.collectAsState()
    val baseline by viewModel.calibratedBaseline.collectAsState()
    val isCalibrating by viewModel.isCalibrating.collectAsState()
    val calibrationProgress by viewModel.calibrationProgress.collectAsState()

    // Animation for strong anomaly (pulse effect)
    val scale by animateFloatAsState(
        targetValue = if (anomalyScore > 0.7f) 1.15f else 1f,
        animationSpec = tween(800),
        label = "PulseAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EM Field Scanner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!allPermissionsGranted) {
                Text(
                    text = "Vibration permission required for alerts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permission")
                }
            } else {
                Text(
                    text = "Field Magnitude",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${"%.1f".format(magnitude)} μT",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Baseline: ${"%.1f".format(baseline)} μT",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(32.dp))

                // Dynamic alert icon + pulse animation on strong anomaly
                val alertIcon = when {
                    anomalyScore > 0.7f -> "file:///android_asset/warning_triangle.png"
                    anomalyScore > 0.3f -> "file:///android_asset/caution_triangle.png"
                    else -> "file:///android_asset/baseline_triangle.png"
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(alertIcon)
                        .build(),
                    contentDescription = "Alert Status",
                    modifier = Modifier
                        .size(160.dp)
                        .scale(scale),
                    colorFilter = ColorFilter.tint(
                        if (anomalyScore > 0.7f) MaterialTheme.colorScheme.error
                        else if (anomalyScore > 0.3f) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = when {
                        anomalyScore > 0.7f -> "STRONG ANOMALY DETECTED!"
                        anomalyScore > 0.3f -> "Elevated field detected"
                        else -> "Normal background levels"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (anomalyScore > 0.7f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(32.dp))

                if (isCalibrating) {
                    LinearProgressIndicator(
                        progress = { calibrationProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Calibrating... Hold steady (${(calibrationProgress * 100).toInt()}%)")
                } else {
                    Button(onClick = { viewModel.startCalibration() }) {
                        Text("Recalibrate")
                    }
                }
            }
        }
    }
}
