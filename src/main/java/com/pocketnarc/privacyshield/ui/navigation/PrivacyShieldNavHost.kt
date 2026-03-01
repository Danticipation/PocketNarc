package com.pocketnarc.privacyshield.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketnarc.privacyshield.data.OnboardingRepository
import com.pocketnarc.privacyshield.ui.screens.home.HomeScreen
import com.pocketnarc.privacyshield.ui.screens.lens.LensDetectorScreen
import com.pocketnarc.privacyshield.ui.screens.magnetometer.MagnetometerScreen
import com.pocketnarc.privacyshield.ui.screens.network.NetworkScannerScreen
import com.pocketnarc.privacyshield.ui.screens.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

object NavRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MAGNETOMETER = "magnetometer"
    const val LENS = "lens"
    const val NETWORK = "network"
    const val AUDIO = "audio"
}

@Composable
fun PrivacyShieldNavHost(
    paddingValues: PaddingValues,
    onboardingRepository: OnboardingRepository  // Pass from outside (e.g. MainActivity)
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val hasCompletedOnboarding by onboardingRepository.hasCompletedOnboarding.collectAsState(initial = false)

    NavHost(
        navController = navController,
        startDestination = if (hasCompletedOnboarding) NavRoutes.HOME else NavRoutes.ONBOARDING,
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
                onNavigateToAudioMonitor = { navController.navigate(NavRoutes.AUDIO) }
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
            Text("Audio Monitor - Coming Soon")
        }
    }
}
