package com.pocketnarc.privacyshield.ui.screens.magnetometer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MagnetometerViewModel(
    private val sensorManager: SensorManager,
    private val vibrator: Vibrator
) : ViewModel(), DefaultLifecycleObserver, SensorEventListener {

    private val _filteredMagnitude = MutableStateFlow(0f)
    val filteredMagnitude: StateFlow<Float> = _filteredMagnitude.asStateFlow()

    private val _calibratedBaseline = MutableStateFlow(0f)
    val calibratedBaseline: StateFlow<Float> = _calibratedBaseline.asStateFlow()

    private val _anomalyScore = MutableStateFlow(0f) // normalized 0-1
    val anomalyScore: StateFlow<Float> = _anomalyScore.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f) // 0-1
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    // Configurable constants
    private val filterAlpha = 0.92f           
    private val calibrationSampleCount = 100  
    private val alertThreshold = 1.2f         
    private val sensitivityFactor = 0.08f     

    private var smoothedMagnitude = 0f
    private val calibrationSamples = mutableListOf<Float>()

    init {
        startCalibration()
    }

    fun startCalibration() {
        viewModelScope.launch {
            _isCalibrating.value = true
            _calibrationProgress.value = 0f
            calibrationSamples.clear()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_UI
        )
    }

    override fun onStop(owner: LifecycleOwner) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Raw magnitude
        val rawMagnitude = sqrt(x*x + y*y + z*z)

        // IIR low-pass filter
        if (smoothedMagnitude == 0f) {
            smoothedMagnitude = rawMagnitude
        } else {
            smoothedMagnitude = filterAlpha * rawMagnitude + (1 - filterAlpha) * smoothedMagnitude
        }
        _filteredMagnitude.value = smoothedMagnitude

        if (_isCalibrating.value) {
            calibrationSamples.add(smoothedMagnitude)
            _calibrationProgress.value = (calibrationSamples.size.toFloat() / calibrationSampleCount).coerceIn(0f, 1f)

            if (calibrationSamples.size >= calibrationSampleCount) {
                calibrationSamples.sort()
                val trim = calibrationSamples.size / 10
                val trimmedList = calibrationSamples.subList(trim, calibrationSamples.size - trim)
                _calibratedBaseline.value = trimmedList.average().toFloat()
                _isCalibrating.value = false
            }
        } else {
            val baseline = _calibratedBaseline.value
            if (baseline > 0f) {
                val deviation = smoothedMagnitude - baseline
                
                val zScore = deviation / (baseline * sensitivityFactor) 
                val score = (zScore / 3.0f).coerceIn(0f, 1f)
                _anomalyScore.value = score

                if (zScore > alertThreshold) {
                    triggerAlert(score)
                }
            }
        }
    }

    private var lastAlertTime = 0L
    private fun triggerAlert(score: Float) {
        val now = System.currentTimeMillis()
        
        // Dynamic pulse speed based on score (threat level)
        // Strong Anomaly (>0.7) = 150ms throttle (fast buzz)
        // Elevated Levels (>0.3) = 600ms throttle (slow thump)
        val throttle = if (score > 0.7f) 150L else 600L
        
        if (now - lastAlertTime < throttle) return
        
        if (vibrator.hasVibrator()) {
            lastAlertTime = now
            val duration = if (score > 0.7f) 100L else 200L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
