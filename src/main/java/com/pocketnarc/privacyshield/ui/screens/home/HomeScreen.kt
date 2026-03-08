package com.pocketnarc.privacyshield.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnarc.privacyshield.R

data class ForensicTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
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
    val tools = listOf(
        ForensicTool("Magnetic Field Detector", "Ferrous & Electronic Anomalies", Icons.Default.Waves, "magnetometer", onNavigateToMagnetometer),
        ForensicTool("Lens Reflection Scanner", "Guided Manual Optical Sweep", Icons.Default.Camera, "lens", onNavigateToLensDetector),
        ForensicTool("Network Device Discovery", "Proactive UPnP/mDNS Scan", Icons.Default.Dns, "network", onNavigateToNetworkScanner),
        ForensicTool("Bluetooth & AirTag Tracker", "Proximity Beacon Analysis", Icons.Default.Radar, "bluetooth", onNavigateToBluetooth),
        ForensicTool("Ultrasonic & Audio Analyzer", "High-Frequency Burst Detection", Icons.Default.GraphicEq, "audio", onNavigateToAudioMonitor),
        ForensicTool("Device Security Audit", "Kernel & Play Integrity Check", Icons.Default.Security, "integrity", onNavigateToIntegrity)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.privataid_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
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
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(224.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.privataid_logo),
                            contentDescription = "Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(242.dp)
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
            Text(
                "FORENSIC_SUITE_ACTIVE",
                color = Color(0xFF00E676),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tools) { tool ->
                    ForensicToolCard(tool)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No data leaves your device • Sensor-based detection only",
                    color = Color.DarkGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "v1.0.4 Forensic Build",
                    color = Color.DarkGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ForensicToolCard(tool: ForensicTool) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        onClick = tool.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(28.dp)
                )
                
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF00E676).copy(alpha = alpha), CircleShape)
                )
            }
            
            Column {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
