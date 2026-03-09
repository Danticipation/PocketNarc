package com.pocketnarc.privacyshield.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketnarc.privacyshield.R
import com.pocketnarc.privacyshield.data.OnboardingRepository

@Composable
fun OnboardingScreen(
    onboardingRepository: OnboardingRepository,
    onComplete: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = "file:///android_asset/Logo_1.png",
            contentDescription = "PrivatAid Logo",
            modifier = Modifier.size(120.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "PrivatAid",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            color = Color(0xFF1A1A1A),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.disclaimer_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.disclaimer_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF00E676),
                    uncheckedColor = Color.Gray
                )
            )
            Text(
                text = stringResource(R.string.disclaimer_acknowledge),
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onComplete() },
            enabled = checked,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E676),
                disabledContainerColor = Color.DarkGray
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = stringResource(R.string.btn_continue),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}