package com.pocketnarc.privacyshield.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketnarc.privacyshield.data.OnboardingRepository
import com.pocketnarc.privacyshield.ui.screens.home.HomeScreen
import com.pocketnarc.privacyshield.ui.screens.lens.LensDetectorScreen
import com.pocketnarc.privacyshield.ui.screens.magnetometer.MagnetometerScreen
import com.pocketnarc.privacyshield.ui.screens.network.NetworkScannerScreen
import com.pocketnarc.privacyshield.ui.screens.audio.AudioMonitorScreen
import com.pocketnarc.privacyshield.ui.screens.integrity.SystemIntegrityScreen
import com.pocketnarc.privacyshield.ui.screens.bluetooth.BluetoothScannerScreen
import com.pocketnarc.privacyshield.ui.screens.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

object NavRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MAGNETOMETER = "magnetometer"
    const val LENS = "lens"
    const val NETWORK = "network"
    const val AUDIO = "audio"
    const val INTEGRITY = "integrity"
    const val BLUETOOTH = "bluetooth"
}

@Composable
fun PrivacyShieldNavHost(
    paddingValues: PaddingValues,
    onboardingRepository: OnboardingRepository
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val hasCompletedOnboarding by onboardingRepository.hasCompletedOnboarding.collectAsState(initial = null)

    if (hasCompletedOnboarding == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E676))
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = if (hasCompletedOnboarding == true) NavRoutes.HOME else NavRoutes.ONBOARDING,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavRoutes.ONBOARDING) {
                OnboardingScreen(
                    onboardingRepository = onboardingRepository,
                    onComplete = {
                        scope.launch {
                            onboardingRepository.setOnboardingComplete()
                            navController.navigate(NavRoutes.HOME) {
                                popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(NavRoutes.HOME) {
                HomeScreen(
                    paddingValues = paddingValues,
                    onNavigateToMagnetometer = { navController.navigate(NavRoutes.MAGNETOMETER) },
                    onNavigateToLensDetector = { navController.navigate(NavRoutes.LENS) },
                    onNavigateToNetworkScanner = { navController.navigate(NavRoutes.NETWORK) },
                    onNavigateToAudioMonitor = { navController.navigate(NavRoutes.AUDIO) },
                    onNavigateToIntegrity = { navController.navigate(NavRoutes.INTEGRITY) },
                    onNavigateToBluetooth = { navController.navigate(NavRoutes.BLUETOOTH) }
                )
            }

            composable(NavRoutes.MAGNETOMETER) {
                MagnetometerScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.LENS) {
                LensDetectorScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.NETWORK) {
                NetworkScannerScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.AUDIO) {
                AudioMonitorScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.INTEGRITY) {
                SystemIntegrityScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.BLUETOOTH) {
                BluetoothScannerScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
