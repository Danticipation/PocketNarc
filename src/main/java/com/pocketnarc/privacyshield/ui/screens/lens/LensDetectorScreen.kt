package com.pocketnarc.privacyshield.ui.screens.lens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LensDetectorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    var hasFlashlight by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    
    // Detection state
    var detectedPoint by remember { mutableStateOf<Offset?>(null) }
    var detectionIntensity by remember { mutableStateOf(0f) }

    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Pulsing animation for the crosshair
    val infiniteTransition = rememberInfiniteTransition(label = "DetectionPulse")
    val crosshairScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    // Handle vibration on detection
    LaunchedEffect(detectionIntensity > 0.9f) {
        if (detectionIntensity > 0.9f) {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            }
        }
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Real-time Image Analysis
            @Suppress("DEPRECATION")
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(640, 480))
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                
                var maxLuma = 0
                var maxPos = -1
                var sumLuma = 0L
                
                for (i in data.indices) {
                    val luma = data[i].toInt() and 0xFF
                    sumLuma += luma
                    if (luma > maxLuma) {
                        maxLuma = luma
                        maxPos = i
                    }
                }

                val avgLuma = sumLuma / data.size
                
                // SPICED UP SENSITIVITY:
                // Threshold lowered slightly to 250, contrast requirement reduced to 120.
                if (maxLuma >= 250 && (maxLuma - avgLuma) > 120 && maxPos != -1) {
                    val width = imageProxy.width
                    val height = imageProxy.height
                    
                    val x = (maxPos % width).toFloat() / width
                    val y = (maxPos / width).toFloat() / height
                    
                    val finalX = when (rotationDegrees) {
                        90 -> y
                        270 -> 1f - y
                        180 -> 1f - x
                        else -> x
                    }
                    val finalY = when (rotationDegrees) {
                        90 -> 1f - x
                        270 -> x
                        180 -> 1f - y
                        else -> y
                    }
                    
                    detectedPoint = Offset(finalX, finalY)
                    detectionIntensity = 1f
                } else {
                    detectionIntensity = (detectionIntensity - 0.15f).coerceAtLeast(0f)
                    if (detectionIntensity == 0f) detectedPoint = null
                }
                
                imageProxy.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                )
                cameraControl = camera.cameraControl
                hasFlashlight = camera.cameraInfo.hasFlashUnit()
            } catch (e: Exception) {
                Log.e("LensDetector", "Camera binding failed", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_OPTIC_SCAN", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
            if (cameraPermissionState.status.isGranted) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // SPICED UP OVERLAY: Red filter to simulate specialized optic film
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Red.copy(alpha = 0.15f)) // Intense red tint
                )

                // Detection Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    detectedPoint?.let { point ->
                        if (detectionIntensity > 0.1f) {
                            val canvasX = point.x * size.width
                            val canvasY = point.y * size.height
                            
                            drawCircle(
                                color = Color.White.copy(alpha = detectionIntensity), // White circle for contrast
                                radius = 30.dp.toPx() * crosshairScale,
                                center = Offset(canvasX, canvasY),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            
                            drawLine(
                                color = Color.White.copy(alpha = detectionIntensity),
                                start = Offset(canvasX - 20.dp.toPx(), canvasY),
                                end = Offset(canvasX + 20.dp.toPx(), canvasY),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = detectionIntensity),
                                start = Offset(canvasX, canvasY - 20.dp.toPx()),
                                end = Offset(canvasX, canvasY + 20.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                // UI HUD
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (detectionIntensity > 0.8f) {
                        Surface(
                            color = Color.White.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                "!! REFLECTION DETECTED !!",
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "AI OPTIC SCANNER: ACTIVE\nSEEKING HIGH-CONTRAST GLINTS",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E676),
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    if (hasFlashlight) {
                        FloatingActionButton(
                            onClick = {
                                isFlashOn = !isFlashOn
                                cameraControl?.enableTorch(isFlashOn)
                            },
                            containerColor = if (isFlashOn) Color(0xFF00E676) else Color.DarkGray,
                            contentColor = if (isFlashOn) Color.Black else Color.White,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Flashlight"
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("CAMERA ACCESS REQUIRED", color = Color.White, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("GRANT ACCESS", color = Color.Black)
                    }
                }
            }
        }
    }
}
