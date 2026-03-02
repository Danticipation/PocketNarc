package com.pocketnarc.privacyshield.ui.screens.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class AudioMonitorViewModel : ViewModel() {

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    private val _decibels = MutableStateFlow(0f)
    val decibels: StateFlow<Float> = _decibels.asStateFlow()

    private val _highFreqActivity = MutableStateFlow(0f)
    val highFreqActivity: StateFlow<Float> = _highFreqActivity.asStateFlow()

    private val _spectrumData = MutableStateFlow(FloatArray(64))
    val spectrumData: StateFlow<FloatArray> = _spectrumData.asStateFlow()

    private val _forensicMessage = MutableStateFlow("ACOUSTIC_SYSTEM_IDLE")
    val forensicMessage: StateFlow<String> = _forensicMessage.asStateFlow()

    private val _lastScanSummary = MutableStateFlow<String?>(null)
    val lastScanSummary: StateFlow<String?> = _lastScanSummary.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var scanJob: Job? = null

    private val sampleRate = 44100
    private val fftSize = 1024
    
    private var ultrasonicBaseline = 0.02f
    private var detectionPersistence = 0
    private var peakDb = 0f
    private var totalSpikes = 0

    @SuppressLint("NewApi")
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(fftSize * 2)

    @SuppressLint("MissingPermission", "NewApi")
    fun startBurstScan() {
        if (_isMonitoring.value || _isCalibrating.value) return
        
        scanJob = viewModelScope.launch {
            _lastScanSummary.value = null
            peakDb = 0f
            totalSpikes = 0
            
            val calibrated = runCalibration()
            if (!calibrated) {
                _forensicMessage.value = "HARDWARE_FAILURE: MIC_UNAVAILABLE"
                return@launch
            }
            
            _forensicMessage.value = "INITIALIZING_HIGH_FREQ_BURST..."
            _isMonitoring.value = true
            
            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    throw Exception("Failed to init AudioRecord")
                }
                audioRecord?.startRecording()
                
                val scanDuration = 15000L 
                val startTime = System.currentTimeMillis()
                val buffer = ShortArray(fftSize)

                withContext(Dispatchers.IO) {
                    while (isActive && _isMonitoring.value && System.currentTimeMillis() - startTime < scanDuration) {
                        val read = audioRecord?.read(buffer, 0, fftSize) ?: 0
                        if (read == fftSize) {
                            processForensicAudio(buffer)
                        }
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                _forensicMessage.value = "SCAN_ERROR: HARDWARE_CONFLIT"
            } finally {
                stopMonitoring()
                _lastScanSummary.value = "PEAK: ${peakDb.toInt()}dB | SPIKES: $totalSpikes"
                _forensicMessage.value = "BURST_COMPLETE: ANALYSIS_SAVED"
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun runCalibration(): Boolean {
        _isCalibrating.value = true
        _forensicMessage.value = "CALIBRATING_ENVIRONMENT (STAY_QUIET)"
        
        var totalEnergy = 0f
        val samples = 30
        val tempBuffer = ShortArray(fftSize)
        
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return false
            audioRecord?.startRecording()
            
            for (i in 1..samples) {
                _calibrationProgress.value = i / samples.toFloat()
                val read = audioRecord?.read(tempBuffer, 0, fftSize) ?: 0
                if (read == fftSize) {
                    var sum = 0.0
                    for (s in tempBuffer) sum += s * s.toDouble()
                    totalEnergy += sqrt(sum / fftSize).toFloat() / 32768f
                }
                delay(100)
            }
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            ultrasonicBaseline = (totalEnergy / samples).coerceAtLeast(0.005f)
            _isCalibrating.value = false
            return true
        } catch (e: Exception) {
            _isCalibrating.value = false
            return false
        }
    }

    @SuppressLint("NewApi")
    fun stopMonitoring() {
        _isMonitoring.value = false
        _isCalibrating.value = false
        scanJob?.cancel()
        try { 
            audioRecord?.stop()
            audioRecord?.release() 
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun processForensicAudio(buffer: ShortArray) {
        var sum = 0.0
        for (s in buffer) sum += s * s.toDouble()
        val currentDb = (20 * log10(sqrt(sum / buffer.size).coerceAtLeast(1.0))).toFloat().coerceIn(0f, 120f)
        _decibels.value = currentDb
        if (currentDb > peakDb) peakDb = currentDb

        val magnitudes = FloatArray(64)
        var highFreqEnergy = 0f
        
        for (bin in 0 until 64) {
            val freq = bin * (sampleRate.toFloat() / fftSize)
            var real = 0f; var imag = 0f
            val step = fftSize / 256
            for (n in 0 until 256 step step) {
                val angle = 2f * PI.toFloat() * bin * n / 256f
                real += buffer[n] * cos(angle)
                imag += buffer[n] * sin(angle)
            }
            val mag = sqrt(real * real + imag * imag) / 32768f
            magnitudes[bin] = (mag * 10f).coerceIn(0f, 1f)
            
            if (freq in 14000f..20000f) highFreqEnergy += mag
        }

        val triggerThreshold = ultrasonicBaseline * 5f
        val isSpike = highFreqEnergy > triggerThreshold

        if (isSpike) {
            detectionPersistence = (detectionPersistence + 1).coerceAtMost(5)
            if (detectionPersistence > 3) totalSpikes++
            _forensicMessage.value = if (detectionPersistence > 3) "SUSTAINED_HIGH_FREQ: POSSIBLE_ELECTRONICS" else "HIGH_FREQ_ANOMALY"
        } else {
            detectionPersistence = (detectionPersistence - 1).coerceAtLeast(0)
            if (detectionPersistence == 0) _forensicMessage.value = "SPECTRUM_CLEAN"
        }

        _spectrumData.value = magnitudes
        _highFreqActivity.value = detectionPersistence / 5f
    }

    override fun onCleared() { 
        super.onCleared()
        stopMonitoring() 
    }
}
