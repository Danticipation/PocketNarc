package com.pocketnarc.privacyshield.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    onNavigateToAudioMonitor: () -> Unit
) {
    val scanOptions = listOf(
        ScanOption("EM Field Scanner", "Magnetometer-based detection", onNavigateToMagnetometer),
        ScanOption("Lens Reflection", "Camera + flashlight detection", onNavigateToLensDetector),
        ScanOption("Network Scanner", "Wi-Fi & Bluetooth devices", onNavigateToNetworkScanner),
        ScanOption("Audio Monitor", "Microphone dB analysis", onNavigateToAudioMonitor)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Privacy Shield", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = innerPadding,  // Scaffold insets
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()  // ← added missing import / reference
                .padding(paddingValues)  // Outer insets from activity
        ) {
            items(scanOptions) { option ->
                Card(
                    onClick = option.onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}