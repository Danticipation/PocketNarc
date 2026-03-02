package com.pocketnarc.privacyshield.ui.screens.magnetometer

import android.hardware.SensorManager
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MagnetometerViewModelFactory(
    private val sensorManager: SensorManager,
    private val vibrator: Vibrator
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MagnetometerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MagnetometerViewModel(sensorManager, vibrator) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
