package com.pocketnarc.privacyshield.ui.screens.lens

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class CandidateSpot(
    val x: Float,
    val y: Float,
    val confidence: Float
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LensDetectorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var hasFlashlight by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    
    var isSweepStarted by remember { mutableStateOf(false) }
    var isFrozen by remember { mutableStateOf(false) }
    val isFrozenAtomic = remember { AtomicBoolean(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var currentCandidates by remember { mutableStateOf<List<CandidateSpot>>(emptyList()) }
    
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var lastVibrationTime by remember { mutableLongStateOf(0L) }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    LaunchedEffect(isSweepStarted, cameraPermissionState.status.isGranted) {
        if (isSweepStarted && cameraPermissionState.status.isGranted) {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build()

            var lastCandidates = mutableListOf<CandidateSpot>()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                if (isFrozenAtomic.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val rotation = imageProxy.imageInfo.rotationDegrees
                val yBuffer = imageProxy.planes[0].buffer
                val yData = ByteArray(yBuffer.remaining())
                yBuffer.get(yData)
                
                val w = imageProxy.width
                val h = imageProxy.height
                val frameCandidates = mutableListOf<CandidateSpot>()

                for (y in 10 until h - 10 step 4) {
                    for (x in 10 until w - 10 step 4) {
                        val idx = y * w + x
                        val centerLuma = yData[idx].toInt() and 0xFF
                        if (centerLuma > 235) {
                            val nIdx = (y-6).coerceAtLeast(0) * w + x
                            val sIdx = (y+6).coerceAtMost(h-1) * w + x
                            val wIdx = y * w + (x-6).coerceAtLeast(0)
                            val eIdx = y * w + (x+6).coerceAtMost(w-1)
                            val avgNeighbor = ((yData[nIdx].toInt() and 0xFF) + (yData[sIdx].toInt() and 0xFF) + (yData[wIdx].toInt() and 0xFF) + (yData[eIdx].toInt() and 0xFF)) / 4f
                            if (centerLuma - avgNeighbor > 70) {
                                val normalizedX = x.toFloat() / w
                                val normalizedY = y.toFloat() / h
                                val correctedPoint = when (rotation) {
                                    90 -> Offset(1f - normalizedY, normalizedX)
                                    270 -> Offset(normalizedY, 1f - normalizedX)
                                    else -> Offset(normalizedX, normalizedY)
                                }
                                frameCandidates.add(CandidateSpot(correctedPoint.x, correctedPoint.y, 1f))
                            }
                        }
                    }
                }

                val persistent = frameCandidates.filter { cur ->
                    lastCandidates.any { prev -> abs(cur.x - prev.x) < 0.04f && abs(cur.y - prev.y) < 0.04f }
                }
                
                currentCandidates = persistent.take(6)
                lastCandidates = frameCandidates.toMutableList()

                if (persistent.isNotEmpty() && System.currentTimeMillis() - lastVibrationTime > 1500) {
                    lastVibrationTime = System.currentTimeMillis()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(40)
                    }
                }
                
                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                cameraControl = camera.cameraControl
                hasFlashlight = camera.cameraInfo.hasFlashUnit()
                if (hasFlashlight) {
                    cameraControl?.enableTorch(true)
                    isFlashOn = true
                }
            } catch (e: Exception) { Log.e("Lens", "Binding error", e) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LENS_REFLECTION_SWEEP", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color(0xFF00E676), navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isSweepStarted) {
                PreSweepInstructions(onStart = { isSweepStarted = true })
            } else if (!cameraPermissionState.status.isGranted) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("CAMERA_ACCESS_REQUIRED", color = Color.White, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("Lens sweep requires camera access to detect optical glints.", color = Color.Gray, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)), shape = MaterialTheme.shapes.medium) {
                        Text("GRANT_PERMISSION", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { isSweepStarted = false }) {
                        Text("BACK", color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                    }
                }
            } else if (cameraPermissionState.status.isGranted) {
                if (isFrozen && frozenBitmap != null) {
                    Image(
                        bitmap = frozenBitmap!!.asImageBitmap(),
                        contentDescription = "Frozen Analysis",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AndroidView(
                        factory = { previewView }, 
                        modifier = Modifier.fillMaxSize().clickable { 
                            frozenBitmap = previewView.bitmap
                            isFrozen = true
                            isFrozenAtomic.set(true)
                        }
                    )
                }
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)))
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    currentCandidates.forEach { spot ->
                        drawCircle(
                            color = Color.Yellow.copy(alpha = 0.8f),
                            radius = 20.dp.toPx(),
                            center = Offset(spot.x * size.width, spot.y * size.height),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Surface(color = Color.Black.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, MaterialTheme.shapes.medium)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isFrozen) "FRAME_CAPTURED: ANALYZE GLINT" else "SWEEPING... TAP TO CAPTURE GLINT",
                                color = if (isFrozen) Color.Yellow else Color(0xFF00E676),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "DISCLAIMER: Yellow circles indicate high-contrast optical peaks. Manually verify lenses.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (hasFlashlight && !isFrozen) {
                            FloatingActionButton(
                                onClick = { isFlashOn = !isFlashOn; cameraControl?.enableTorch(isFlashOn) },
                                containerColor = if (isFlashOn) Color(0xFF00E676) else Color.DarkGray,
                                shape = CircleShape
                            ) {
                                Icon(if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash")
                            }
                        }
                        if (isFrozen) {
                            Button(
                                onClick = { 
                                    isFrozen = false
                                    isFrozenAtomic.set(false)
                                    frozenBitmap = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("RESUME SWEEP", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun PreSweepInstructions(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "LENS_REFLECTION_SWEEP",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        ForensicBullet("DIM_LIGHTS", "Best results in near-dark environments.")
        ForensicBullet("MAINTAIN_RANGE", "Hold phone 1–3 meters from target.")
        ForensicBullet("SLOW_PAN", "Slowly scan suspicious areas like smoke detectors or vents.")
        ForensicBullet("TAP_TO_FREEZE", "Tap the screen to freeze and analyze candidate spots.")
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("INITIALIZE SWEEP", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ForensicBullet(label: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("> $label:", color = Color(0xFF00E676), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(120.dp))
        Text(desc, color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}
