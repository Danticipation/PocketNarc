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
import kotlin.math.abs
import kotlin.math.sqrt

class MagnetometerViewModel(
    private val sensorManager: SensorManager,
    private val vibrator: Vibrator
) : ViewModel(), DefaultLifecycleObserver, SensorEventListener {

    private val _rawField = MutableStateFlow(Triple(0f, 0f, 0f))
    val rawField: StateFlow<Triple<Float, Float, Float>> = _rawField.asStateFlow()

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

    // Configurable constants (tune these)
    private val filterAlpha = 0.92f           // IIR smoothing factor
    private val calibrationSampleCount = 120  // ~10 seconds at UI delay
    private val outlierThreshold = 3.0f       // z-score outlier rejection
    private val alertThreshold = 3.5f         // z-score for vibration/alert

    private var smoothedMagnitude = 0f
    private val calibrationSamples = mutableListOf<Float>()

    init {
        // Optional auto-calibrate on first start
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

        _rawField.value = Triple(x, y, z)

        // Raw magnitude
        val rawMagnitude = sqrt(x*x + y*y + z*z)

        // IIR low-pass filter (industrial-grade smoothing)
        smoothedMagnitude = filterAlpha * rawMagnitude + (1 - filterAlpha) * smoothedMagnitude
        _filteredMagnitude.value = smoothedMagnitude

        if (_isCalibrating.value) {
            // Collect samples, reject outliers
            if (calibrationSamples.isEmpty() || abs(smoothedMagnitude - calibrationSamples.average().toFloat()) < outlierThreshold * calibrationSamples.stddev()) {
                calibrationSamples.add(smoothedMagnitude)
                _calibrationProgress.value = calibrationSamples.size.toFloat() / calibrationSampleCount
            }

            if (calibrationSamples.size >= calibrationSampleCount) {
                // Final baseline = trimmed mean (remove top/bottom 10%)
                calibrationSamples.sort()
                val trim = calibrationSamples.size / 10
                _calibratedBaseline.value = calibrationSamples.subList(trim, calibrationSamples.size - trim).average().toFloat()
                _isCalibrating.value = false
            }
        } else {
            val baseline = _calibratedBaseline.value
            if (baseline > 0f) {
                val deviation = smoothedMagnitude - baseline
                val zScore = deviation / (baseline * 0.15f) // empirical std dev factor
                _anomalyScore.value = (zScore / alertThreshold).coerceIn(-1f, 1f)

                // Trigger vibration + alert if above threshold
                if (zScore > alertThreshold) {
                    triggerAlert()
                }
            }
        }
    }

    private fun triggerAlert() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Future: handle low accuracy
    }

    private fun List<Float>.stddev(): Float {
        if (isEmpty()) return 0f
        val mean = average().toFloat()
        val variance = map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
