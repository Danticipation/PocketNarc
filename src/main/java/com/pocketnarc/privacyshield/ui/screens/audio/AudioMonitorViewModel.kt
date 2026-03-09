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
import org.jtransforms.fft.FloatFFT_1D

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
    private val spectrumBins = 64

    private val fft = FloatFFT_1D(fftSize.toLong())
    private val window = FloatArray(fftSize) { i ->
        (0.5f * (1 - cos(2 * PI.toFloat() * i / (fftSize - 1)))).toFloat() // Hann window
    }
    private val fftBuffer = FloatArray(fftSize)

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
                _forensicMessage.value = "SCAN_ERROR: HARDWARE_CONFLICT"
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
        
        var totalHighFreqEnergy = 0f
        var validSamples = 0
        val samples = 30
        val tempBuffer = ShortArray(fftSize)
        val halfFft = fftSize / 2
        val bin14k = (14000f * fftSize / sampleRate).toInt()
        val bin20k = (20000f * fftSize / sampleRate).toInt().coerceAtMost(halfFft)
        
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return false
            audioRecord?.startRecording()
            
            for (i in 1..samples) {
                _calibrationProgress.value = i / samples.toFloat()
                val read = audioRecord?.read(tempBuffer, 0, fftSize) ?: 0
                if (read == fftSize) {
                    for (j in 0 until fftSize) {
                        fftBuffer[j] = (tempBuffer[j] / 32768f) * window[j]
                    }
                    fft.realForward(fftBuffer)
                    var hfEnergy = 0f
                    for (k in bin14k..bin20k) {
                        val re = when (k) {
                            0 -> fftBuffer[0]
                            halfFft -> fftBuffer[1]
                            else -> fftBuffer[2 * k]
                        }
                        val im = when (k) {
                            0, halfFft -> 0f
                            else -> fftBuffer[2 * k + 1]
                        }
                        hfEnergy += sqrt(re * re + im * im) / fftSize
                    }
                    totalHighFreqEnergy += hfEnergy
                    validSamples++
                }
                delay(100)
            }
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            ultrasonicBaseline = (totalHighFreqEnergy / validSamples.coerceAtLeast(1)).coerceAtLeast(0.001f)
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
        // RMS for decibel level
        var sum = 0.0
        for (s in buffer) sum += s * s.toDouble()
        val currentDb = (20 * log10(sqrt(sum / buffer.size).coerceAtLeast(1.0))).toFloat().coerceIn(0f, 120f)
        _decibels.value = currentDb
        if (currentDb > peakDb) peakDb = currentDb

        // Convert PCM to float, apply Hann window, run FFT
        for (i in 0 until fftSize) {
            fftBuffer[i] = (buffer[i] / 32768f) * window[i]
        }
        fft.realForward(fftBuffer)

        // Extract magnitudes from packed output (JTransforms realForward format):
        // a[0]=DC, a[1]=Nyquist, a[2k]=Re[k], a[2k+1]=Im[k] for k=1..n/2-1
        val halfFft = fftSize / 2
        val fftMagnitudes = FloatArray(halfFft + 1) { k ->
            val (re, im) = when (k) {
                0 -> fftBuffer[0] to 0f
                halfFft -> fftBuffer[1] to 0f
                else -> fftBuffer[2 * k] to fftBuffer[2 * k + 1]
            }
            sqrt(re * re + im * im) / fftSize
        }

        // Downsample to spectrumBins for display (avg each group)
        val magnitudes = FloatArray(spectrumBins) { displayBin ->
            val startBin = (displayBin * (halfFft + 1)) / spectrumBins
            val endBin = ((displayBin + 1) * (halfFft + 1)) / spectrumBins
            var sum = 0f
            for (k in startBin until endBin) sum += fftMagnitudes[k]
            (sum / (endBin - startBin).coerceAtLeast(1) * 10f).coerceIn(0f, 1f)
        }

        // High-freq energy: 14–20 kHz (bins 325–464)
        val bin14k = (14000f * fftSize / sampleRate).toInt()
        val bin20k = (20000f * fftSize / sampleRate).toInt().coerceAtMost(halfFft)
        var highFreqEnergy = 0f
        for (k in bin14k..bin20k) {
            highFreqEnergy += fftMagnitudes[k]
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
