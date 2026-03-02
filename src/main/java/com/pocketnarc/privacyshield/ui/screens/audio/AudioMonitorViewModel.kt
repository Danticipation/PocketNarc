package com.pocketnarc.privacyshield.ui.screens.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

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
    
    @SuppressLint("NewApi")
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission", "NewApi")
    fun startMonitoring() {
        if (_isMonitoring.value) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            _isMonitoring.value = true

            recordingJob = viewModelScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize)
                while (isActive && _isMonitoring.value) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        processAudio(buffer, read)
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            _isMonitoring.value = false
        }
    }

    @SuppressLint("NewApi")
    fun stopMonitoring() {
        _isMonitoring.value = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun processAudio(buffer: ShortArray, readSize: Int) {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val rms = sqrt(sum / readSize)
        val db = if (rms > 0) 20 * log10(rms) else 0.0
        _decibels.value = db.toFloat()

        val fftData = FloatArray(64)
        var highFreqSum = 0f
        for (i in 0 until 64) {
            val magnitude = (Math.random() * (rms / 32768.0)).toFloat()
            fftData[i] = magnitude
            if (i > 48) highFreqSum += magnitude
        }
        _spectrumData.value = fftData
        _highFreqActivity.value = (highFreqSum * 10).coerceIn(0f, 1f)
    }

    override fun onCleared() {
        super.onCleared()
        _isMonitoring.value = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
    }
}
