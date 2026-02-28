package com.pocketnarc.privacyshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketnarc.privacyshield.data.OnboardingRepository
import com.pocketnarc.privacyshield.ui.navigation.PrivacyShieldNavHost
import com.pocketnarc.privacyshield.ui.theme.PrivacyShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val onboardingRepository = OnboardingRepository(this)  // Create instance here

        setContent {
            PrivacyShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrivacyShieldNavHost(
                        paddingValues = WindowInsets.safeDrawing.asPaddingValues(),
                        onboardingRepository = onboardingRepository
                    )
                }
            }
        }
    }
}