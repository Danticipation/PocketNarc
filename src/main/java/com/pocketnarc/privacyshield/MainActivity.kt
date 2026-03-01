package com.pocketnarc.privacyshield

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*
import com.pocketnarc.privacyshield.data.OnboardingRepository
import com.pocketnarc.privacyshield.ui.navigation.PrivacyShieldNavHost
import com.pocketnarc.privacyshield.ui.theme.PrivacyShieldTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val onboardingRepository = OnboardingRepository(this)

        setContent {
            PrivacyShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val permissionsToRequest = mutableListOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }

                    val permissionState = rememberMultiplePermissionsState(permissionsToRequest)

                    if (permissionState.allPermissionsGranted) {
                        PrivacyShieldNavHost(
                            paddingValues = WindowInsets.safeDrawing.asPaddingValues(),
                            onboardingRepository = onboardingRepository
                        )
                    } else {
                        PermissionRequiredScreen(permissionState)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequiredScreen(permissionState: MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SYSTEM_ACCESS_REQUIRED",
            color = Color(0xFF00E676),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "PrivatAid requires system-level permissions to initialize the Digital Forensics suite (Camera, Audio, and Network Analysis).",
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { permissionState.launchMultiplePermissionRequest() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("GRANT_ACCESS", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}
