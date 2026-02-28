package com.pocketnarc.privacyshield.ui.screens.magnetometer

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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

    val scale by animateFloatAsState(
        targetValue = if (anomalyScore > 0.7f) 1.2f else 1f,
        animationSpec = tween(800),
        label = "PulseAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EM Field Scanner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()) // FIX: Added scrolling for smaller screens
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Changed from Center to Top for better scrolling
        ) {
            if (!allPermissionsGranted) {
                Text(
                    text = "Vibration permission required for alerts",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                Text(
                    text = "${"%.1f".format(magnitude)} μT",
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = "Baseline: ${"%.1f".format(baseline)} μT",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(24.dp))

                val assetPath = when {
                    anomalyScore > 0.7f -> "file:///android_asset/warning_triangle.png"
                    anomalyScore > 0.3f -> "file:///android_asset/caution_triangle.png"
                    else -> "file:///android_asset/baseline_triangle.png"
                }

                AsyncImage(
                    model = assetPath,
                    contentDescription = "Alert Status",
                    modifier = Modifier
                        .size(200.dp) // Adjusted size
                        .scale(scale)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = when {
                        anomalyScore > 0.7f -> "STRONG ANOMALY DETECTED!"
                        anomalyScore > 0.3f -> "Elevated field detected"
                        else -> "Normal background levels"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (anomalyScore > 0.7f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(32.dp))

                if (isCalibrating) {
                    // Added background for the progress section
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
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
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Calibrating... Hold steady",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${(calibrationProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startCalibration() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Recalibrate")
                    }
                }
                
                // Extra padding at bottom for scroll visibility
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
