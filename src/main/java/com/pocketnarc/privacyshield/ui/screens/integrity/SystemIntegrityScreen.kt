package com.pocketnarc.privacyshield.ui.screens.integrity

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemIntegrityScreen(onNavigateBack: () -> Unit) {
    val viewModel: SystemIntegrityViewModel = viewModel()
    val context = LocalContext.current
    val checks by viewModel.checks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    // Run audit immediately on enter
    LaunchedEffect(Unit) {
        viewModel.runAudit(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_SYS_INTEGRITY", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Summary Dashboard
            val totalIssues = checks.count { it.status != IntegrityStatus.SECURE }
            val isCompromised = checks.any { it.status == IntegrityStatus.COMPROMISED }

            Surface(
                color = if (isCompromised) Color(0xFF330000) else Color(0xFF1A1A1A),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().border(1.dp, if (isCompromised) Color.Red else Color.DarkGray, MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isCompromised) Icons.Default.GppBad else Icons.Default.GppGood,
                        contentDescription = null,
                        tint = if (isCompromised) Color.Red else Color(0xFF00E676),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (isCompromised) "SYSTEM_COMPROMISED" else "SYSTEM_INTEGRITY_VERIFIED",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (totalIssues == 0) "Environment secured against external hooks." else "$totalIssues potential entry points identified.",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "FORENSIC_AUDIT_LOG",
                color = Color.DarkGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Audit List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(checks) { check ->
                    AuditCard(check)
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.runAudit(context) },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    disabledContainerColor = Color.DarkGray
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (isScanning) "AUDITING..." else "REFRESH INTEGRITY AUDIT",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AuditCard(check: IntegrityCheck) {
    val statusColor = when (check.status) {
        IntegrityStatus.SECURE -> Color(0xFF00E676)
        IntegrityStatus.WARNING -> Color.Yellow
        IntegrityStatus.COMPROMISED -> Color.Red
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = check.title,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = check.description,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
