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
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

enum class MagneticStatus {
    STABLE, ELEVATED, ANOMALY, INTERFERENCE
}

enum class SensitivityLevel(val multiplier: Float) {
    LOW(1.5f), MED(1.0f), HIGH(0.7f)
}

data class AnomalyLog(
    val id: String = UUID.randomUUID().toString(),
    val magnitude: Int,
    val status: MagneticStatus,
    val time: Long = System.currentTimeMillis()
)

class MagnetometerViewModel(
    private val sensorManager: SensorManager,
    private val vibrator: Vibrator
) : ViewModel(), DefaultLifecycleObserver, SensorEventListener {

    private val _filteredMagnitude = MutableStateFlow(0f)
    val filteredMagnitude: StateFlow<Float> = _filteredMagnitude.asStateFlow()

    private val _deviation = MutableStateFlow(0f)
    val deviation: StateFlow<Float> = _deviation.asStateFlow()

    private val _status = MutableStateFlow(MagneticStatus.STABLE)
    val status: StateFlow<MagneticStatus> = _status.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    private val _sensitivity = MutableStateFlow(SensitivityLevel.MED)
    val sensitivity: StateFlow<SensitivityLevel> = _sensitivity.asStateFlow()

    private val _anomalyHistory = MutableStateFlow<List<AnomalyLog>>(emptyList())
    val anomalyHistory: StateFlow<List<AnomalyLog>> = _anomalyHistory.asStateFlow()

    private val filterAlpha = 0.92f           // Weight for smoothed value (low-pass: 92% old, 8% new)
    private val calibrationSampleCount = 150
    private var smoothedMagnitude = 0f
    private val calibrationSamples = mutableListOf<Float>()
    private var baselineValue = 0f

    private val devHistory = LinkedList<Float>()
    private val historySize = 24             // ~1.5s at SENSOR_DELAY_GAME for stable averaging

    fun startCalibration() {
        viewModelScope.launch {
            _isCalibrating.value = true
            _calibrationProgress.value = 0f
            calibrationSamples.clear()
            _status.value = MagneticStatus.STABLE
            devHistory.clear()
        }
    }

    fun setSensitivity(level: SensitivityLevel) {
        _sensitivity.value = level
        // Snap status update
        if (!_isCalibrating.value && baselineValue > 0f) {
            updateStatus(_deviation.value)
        }
    }

    fun clearLogs() {
        _anomalyHistory.value = emptyList()
    }

    override fun onStart(owner: LifecycleOwner) {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onStop(owner: LifecycleOwner) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return
        
        val rawMagnitude = sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2])
        smoothedMagnitude = if (smoothedMagnitude == 0f) rawMagnitude else filterAlpha * smoothedMagnitude + (1 - filterAlpha) * rawMagnitude
        _filteredMagnitude.value = smoothedMagnitude

        if (_isCalibrating.value) {
            calibrationSamples.add(smoothedMagnitude)
            _calibrationProgress.value = (calibrationSamples.size.toFloat() / calibrationSampleCount)
            if (calibrationSamples.size >= calibrationSampleCount) {
                calibrationSamples.sort()
                val trim = calibrationSamples.size / 10
                baselineValue = calibrationSamples.subList(trim, calibrationSamples.size - trim).average().toFloat()
                _isCalibrating.value = false
            }
        } else if (baselineValue > 0f) {
            val currentDev = smoothedMagnitude - baselineValue
            
            devHistory.add(currentDev)
            if (devHistory.size > historySize) devHistory.removeFirst()
            val stableDev = devHistory.average().toFloat()
            
            _deviation.value = stableDev
            updateStatus(stableDev)
        }
    }

    private fun updateStatus(stableDev: Float) {
        val mult = _sensitivity.value.multiplier
        val newStatus = when {
            abs(stableDev) > (150f * mult) -> MagneticStatus.INTERFERENCE
            stableDev > (45f * mult) -> MagneticStatus.ANOMALY
            stableDev > (15f * mult) -> MagneticStatus.ELEVATED
            else -> MagneticStatus.STABLE
        }

        if (newStatus != _status.value) {
            val oldStatus = _status.value
            _status.value = newStatus
            
            if (newStatus == MagneticStatus.ANOMALY || newStatus == MagneticStatus.ELEVATED) {
                logAnomaly(stableDev.toInt(), newStatus)
                triggerHaptic(newStatus) // Vibrate on transition
            }
        }
    }

    private fun logAnomaly(magnitude: Int, status: MagneticStatus) {
        val current = _anomalyHistory.value.toMutableList()
        // Prevent duplicate logs for same elevation burst
        if (current.isNotEmpty() && current.first().status == status && System.currentTimeMillis() - current.first().time < 2000) return
        
        current.add(0, AnomalyLog(magnitude = magnitude, status = status))
        _anomalyHistory.value = current.take(10)
    }

    private var lastVibrate = 0L
    private fun triggerHaptic(status: MagneticStatus) {
        val now = System.currentTimeMillis()
        if (now - lastVibrate < 500L) return // Basic throttle for rapid transitions
        
        lastVibrate = now
        val duration = if (status == MagneticStatus.ANOMALY) 100L else 40L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // SensorManager.SENSOR_STATUS_UNRELIABLE(0) or SENSOR_STATUS_ACCURACY_LOW(1) = noisy readings
        // When unreliable, readings may be sporadic; consider re-calibrating when accuracy improves
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE && !_isCalibrating.value) {
            devHistory.clear()  // Flush noisy deviation history
        }
    }
    override fun onCleared() { sensorManager.unregisterListener(this); super.onCleared() }
}
