package com.pocketnarc.privacyshield.ui.screens.integrity

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketnarc.privacyshield.ui.screens.lens.ForensicBullet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemIntegrityScreen(onNavigateBack: () -> Unit) {
    val viewModel: SystemIntegrityViewModel = viewModel()
    val context = LocalContext.current
    val checks by viewModel.checks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val integrityScore by viewModel.integrityScore.collectAsState()

    var showInstructions by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRIVATAID_SYS_AUDIT", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
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
            if (showInstructions) {
                IntegrityInstructions(onStart = { 
                    showInstructions = false
                    viewModel.runAudit(context)
                })
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Weighted Integrity Score
                    Surface(
                        color = if (integrityScore < 70) Color(0xFF330000) else Color(0xFF1A1A1A),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().border(1.dp, if (integrityScore < 70) Color.Red else Color.DarkGray, MaterialTheme.shapes.medium)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "INTEGRITY_INDEX",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "$integrityScore/100",
                                color = if (integrityScore < 70) Color.Red else Color(0xFF00E676),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (integrityScore == 100) "ENV_HARDENED" else if (integrityScore >= 70) "ENV_STABLE" else "ENV_COMPROMISED",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
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

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(checks) { check ->
                            DetailedAuditCard(check)
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
                            text = if (isScanning) "RE-AUDITING..." else "REFRESH SECURITY AUDIT",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedAuditCard(check: IntegrityCheck) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (check.status) {
        IntegrityStatus.SECURE -> Color(0xFF00E676)
        IntegrityStatus.WARNING -> Color.Yellow
        IntegrityStatus.COMPROMISED -> Color.Red
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(check.status) {
                        IntegrityStatus.SECURE -> Icons.Default.GppGood
                        IntegrityStatus.WARNING -> Icons.Default.GppMaybe
                        IntegrityStatus.COMPROMISED -> Icons.Default.GppBad
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
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
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.DarkGray
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "MITIGATION_STRATEGY:",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = check.mitigation,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun IntegrityInstructions(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "SYS_INTEGRITY_PROTOCOL",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        ForensicBullet("AUDIT_LEVEL", "Performs surface-level binary and service checks. Modern cloaking may bypass detection.")
        ForensicBullet("PLAY_INTEGRITY", "Uses Google Play APIs to verify kernel and app authenticity.")
        ForensicBullet("FALSE_ALERTS", "Developer options or custom icons may trigger low-severity warnings.")
        ForensicBullet("LIMITATION", "This is a diagnostic aid, not a guarantee of total OS security.")
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("I UNDERSTAND - START AUDIT", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
