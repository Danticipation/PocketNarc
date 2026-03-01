package com.pocketnarc.privacyshield.ui.screens.lens

import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LensDetectorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var hasFlashlight by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    
    // Detection state with smoothing
    var targetPoint by remember { mutableStateOf<Offset?>(null) }
    var lastDetectedPoint by remember { mutableStateOf<Offset?>(null) }
    var persistenceCount by remember { mutableIntStateOf(0) }
    var displayIntensity by remember { mutableStateOf(0f) }

    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Smoothly animate the crosshair to the target
    val animatedX by animateFloatAsState(targetValue = targetPoint?.x ?: 0.5f, label = "X")
    val animatedY by animateFloatAsState(targetValue = targetPoint?.y ?: 0.5f, label = "Y")

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val rotation = imageProxy.imageInfo.rotationDegrees
                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                
                var maxLuma = 0
                var maxPos = -1
                var sumLuma = 0L
                for (i in data.indices) {
                    val luma = data[i].toInt() and 0xFF
                    sumLuma += luma
                    if (luma > maxLuma) { maxLuma = luma; maxPos = i }
                }
                val avgLuma = sumLuma / data.size

                val threshold = if (avgLuma < 20) 254 else 250
                val contrastReq = if (avgLuma < 20) 180 else 120

                if (maxLuma >= threshold && (maxLuma - avgLuma) > contrastReq) {
                    val w = imageProxy.width; val h = imageProxy.height
                    val rawX = (maxPos % w).toFloat() / w
                    val rawY = (maxPos / w).toFloat() / h
                    
                    val curPoint = when (rotation) {
                        90 -> Offset(rawY, 1f - rawX)
                        270 -> Offset(1f - rawY, rawX)
                        else -> Offset(rawX, rawY)
                    }

                    if (lastDetectedPoint != null && 
                        abs(curPoint.x - lastDetectedPoint!!.x) < 0.05f && 
                        abs(curPoint.y - lastDetectedPoint!!.y) < 0.05f) {
                        persistenceCount++
                    } else {
                        persistenceCount = 0
                    }
                    lastDetectedPoint = curPoint

                    if (persistenceCount > 4) {
                        targetPoint = curPoint
                        displayIntensity = 1f
                    }
                } else {
                    displayIntensity = (displayIntensity - 0.1f).coerceAtLeast(0f)
                    if (displayIntensity <= 0f) {
                        targetPoint = null
                        persistenceCount = 0
                    }
                }
                imageProxy.close()
            }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                cameraControl = camera.cameraControl
                hasFlashlight = camera.cameraInfo.hasFlashUnit()
            } catch (e: Exception) { Log.e("Lens", "Binding error", e) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_OPTIC_SCAN", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color(0xFF00E676), navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (cameraPermissionState.status.isGranted) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.1f)))

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (displayIntensity > 0.1f) {
                        drawCircle(Color.White.copy(alpha = displayIntensity), 35.dp.toPx(), Offset(animatedX * size.width, animatedY * size.height), style = Stroke(2.dp.toPx()))
                        drawLine(Color.White.copy(alpha = displayIntensity), Offset(animatedX * size.width - 20.dp.toPx(), animatedY * size.height), Offset(animatedX * size.width + 20.dp.toPx(), animatedY * size.height), 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = displayIntensity), Offset(animatedX * size.width, animatedY * size.height - 20.dp.toPx()), Offset(animatedX * size.width, animatedY * size.height + 20.dp.toPx()), 1.dp.toPx())
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (displayIntensity > 0.8f) {
                        Surface(color = Color.White, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(bottom = 16.dp)) {
                            Text("!! OPTIC REFLECTION !!", color = Color.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(color = Color.Black.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Text("ASSISTIVE SCANNER: ACTIVE\nSEEKING STABLE PINPOINT GLINTS", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF00E676), fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(24.dp))
                    if (hasFlashlight) {
                        FloatingActionButton(onClick = { isFlashOn = !isFlashOn; cameraControl?.enableTorch(isFlashOn) }, containerColor = if (isFlashOn) Color(0xFF00E676) else Color.DarkGray, shape = CircleShape) {
                            Icon(if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash")
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
