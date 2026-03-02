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

    private val _decibels = MutableStateFlow(0f)
    val decibels: StateFlow<Float> = _decibels.asStateFlow()

    private val _highFreqActivity = MutableStateFlow(0f)
    val highFreqActivity: StateFlow<Float> = _highFreqActivity.asStateFlow()

    private val _spectrumData = MutableStateFlow(FloatArray(64))
    val spectrumData: StateFlow<FloatArray> = _spectrumData.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val sampleRate = 44100
    private val fftSize = 1024
    
    // Baseline for ultrasonic detection to prevent "flickering"
    private var ultrasonicBaseline = 0f
    private var detectionPersistence = 0

    @SuppressLint("NewApi")
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(fftSize * 2)

    @SuppressLint("MissingPermission", "NewApi")
    fun startMonitoring() {
        if (_isMonitoring.value) return
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            audioRecord?.startRecording()
            _isMonitoring.value = true
            recordingJob = viewModelScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(fftSize)
                while (isActive && _isMonitoring.value) {
                    val read = audioRecord?.read(buffer, 0, fftSize) ?: 0
                    if (read == fftSize) {
                        processForensicAudio(buffer)
                    }
                    delay(30)
                }
            }
        } catch (e: Exception) { _isMonitoring.value = false }
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
        val db = if (rms > 0) 20 * log10(rms) else 0.0
        _decibels.value = db.toFloat()

        val magnitudes = FloatArray(64)
        var ultrasonicEnergy = 0f
        
        for (bin in 0 until 64) {
            val freq = bin * (sampleRate / 2f / 64f)
            var real = 0.0; var imag = 0.0
            for (n in buffer.indices) {
                val angle = 2.0 * PI * bin * n / 64.0
                real += buffer[n] * cos(angle)
                imag += buffer[n] * sin(angle)
            }
            val mag = sqrt(real * real + imag * imag).toFloat() / 1000000f
            magnitudes[bin] = mag.coerceIn(0f, 1f)
            if (freq > 17500) ultrasonicEnergy += mag
        }

        // DYNAMIC BASELINE FILTERING
        // We slowly adapt to the room's high-frequency noise floor
        ultrasonicBaseline = (ultrasonicBaseline * 0.95f) + (ultrasonicEnergy * 0.05f)
        
        // DETECTION LOGIC: Only alert if energy is 3x higher than the baseline floor
        val currentDetection = (ultrasonicEnergy > (ultrasonicBaseline * 3.0f) && ultrasonicEnergy > 0.05f)
        
        if (currentDetection) {
            detectionPersistence = (detectionPersistence + 1).coerceAtMost(10)
        } else {
            detectionPersistence = (detectionPersistence - 1).coerceAtLeast(0)
        }

        _spectrumData.value = magnitudes
        // Stable alert: Only show "DETECTION" if signal is persistent for ~150ms
        _highFreqActivity.value = if (detectionPersistence > 4) 1f else 0f
    }

    override fun onCleared() { super.onCleared(); stopMonitoring() }
}
