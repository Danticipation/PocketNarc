package com.pocketnarc.privacyshield.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

data class ScanOption(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    onNavigateToMagnetometer: () -> Unit,
    onNavigateToLensDetector: () -> Unit,
    onNavigateToNetworkScanner: () -> Unit,
    onNavigateToAudioMonitor: () -> Unit,
    onNavigateToIntegrity: () -> Unit,
    onNavigateToBluetooth: () -> Unit
) {
    val scanOptions = listOf(
        ScanOption("EM Field Scanner", "Magnetometer-based detection", onNavigateToMagnetometer),
        ScanOption("Lens Reflection", "Camera + flashlight detection", onNavigateToLensDetector),
        ScanOption("Network Scanner", "Wi-Fi & Forensic Analysis", onNavigateToNetworkScanner),
        ScanOption("Bluetooth Hunt", "BLE Tracker & Beacon discovery", onNavigateToBluetooth),
        ScanOption("Audio Monitor", "Ultrasonic & dB Analysis", onNavigateToAudioMonitor),
        ScanOption("System Integrity", "Root & ADB security audit", onNavigateToIntegrity)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = "file:///android_asset/Logo_1.png",
                            contentDescription = "Logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "PrivatAid", 
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(scanOptions) { option ->
                    Card(
                        onClick = option.onClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
