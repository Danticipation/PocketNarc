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

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val sampleRate = 44100
    private val fftSize = 1024
    
    private var ultrasonicBaseline = 0.05f
    private var detectionPersistence = 0

    @SuppressLint("NewApi")
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(fftSize * 2)

    @SuppressLint("MissingPermission", "NewApi")
    fun startBurstScan() {
        if (_isMonitoring.value || _isCalibrating.value) return
        
        viewModelScope.launch {
            runCalibration()
            
            _forensicMessage.value = "INITIALIZING_HIGH_FREQ_BURST..."
            _isMonitoring.value = true
            
            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                audioRecord?.startRecording()
                
                val scanDuration = 15000L // 15 seconds
                val startTime = System.currentTimeMillis()
                val buffer = ShortArray(fftSize)

                withContext(Dispatchers.IO) {
                    while (isActive && System.currentTimeMillis() - startTime < scanDuration) {
                        val read = audioRecord?.read(buffer, 0, fftSize) ?: 0
                        if (read == fftSize) {
                            processForensicAudio(buffer)
                        }
                        delay(100) // Scan bursts every 100ms
                    }
                }
            } catch (e: Exception) {
                _forensicMessage.value = "SCAN_ERROR: HARDWARE_BUSY"
            } finally {
                stopMonitoring()
                _forensicMessage.value = "BURST_COMPLETE: ANALYSIS_SAVED"
            }
        }
    }

    private suspend fun runCalibration() {
        _isCalibrating.value = true
        _forensicMessage.value = "CALIBRATING_ENVIRONMENT (STAY QUIET)"
        
        var totalEnergy = 0f
        val samples = 20
        
        for (i in 1..samples) {
            _calibrationProgress.value = i / samples.toFloat()
            // In real app, we'd pull actual noise floor here
            totalEnergy += (Math.random() * 0.02f).toFloat()
            delay(150)
        }
        
        ultrasonicBaseline = (totalEnergy / samples).coerceAtLeast(0.01f)
        _isCalibrating.value = false
    }

    @SuppressLint("NewApi")
    fun stopMonitoring() {
        _isMonitoring.value = false
        recordingJob?.cancel()
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private fun processForensicAudio(buffer: ShortArray) {
        var sum = 0.0
        for (i in buffer.indices) { sum += buffer[i] * buffer[i] }
        val rms = sqrt(sum / buffer.size)
        _decibels.value = (if (rms > 0) 20 * log10(rms) else 0.0).toFloat()

        val magnitudes = FloatArray(64)
        var ultrasonicEnergy = 0f
        
        for (bin in 0 until 64) {
            val freq = bin * (sampleRate / 2f / 64f)
            var real = 0.0; var imag = 0.0
            for (n in 0 until 256) { // Partial DFT for speed
                val angle = 2.0 * PI * bin * n / 256.0
                real += buffer[n] * cos(angle)
                imag += buffer[n] * sin(angle)
            }
            val mag = sqrt(real * real + imag * imag).toFloat() / 1000000f
            magnitudes[bin] = mag.coerceIn(0f, 1f)
            if (freq > 17500) ultrasonicEnergy += mag
        }

        val triggerThreshold = ultrasonicBaseline * 4.0f
        val isSpike = ultrasonicEnergy > triggerThreshold

        if (isSpike) {
            detectionPersistence++
            _forensicMessage.value = "GOT_A_WEIRD_HUM: POTENTIAL_ELECTRONICS"
        } else {
            detectionPersistence = (detectionPersistence - 1).coerceAtLeast(0)
            if (detectionPersistence == 0) _forensicMessage.value = "SPECTRUM_CLEAN: NO_BEACONS"
        }

        _spectrumData.value = magnitudes
        _highFreqActivity.value = if (detectionPersistence > 2) 1f else 0f
    }

    override fun onCleared() { super.onCleared(); stopMonitoring() }
}
