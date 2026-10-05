package dev.goodwy.rphone.controller.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object CallRecorder {
    private const val TAG = "CallRecorder"

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Synchronized
    fun startRecording(context: Context, phoneNumber: String): Boolean {
        if (_isRecording.value) return true

        return try {
            // Store recordings in app-specific external files dir or public recordings dir
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
                ?: File(context.filesDir, "recordings")
            val recordDir = File(baseDir, "RillPhoneRecordings").apply {
                if (!exists()) mkdirs()
            }

            val cleanPhone = phoneNumber.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
            val fileName = "Call_${cleanPhone}_${System.currentTimeMillis()}.m4a"
            val outputFile = File(recordDir, fileName)

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Attempt VOICE_COMMUNICATION first for crystal-clear two-way audio.
            // Fall back to MIC if the OEM or hardware blocks VOICE_COMMUNICATION.
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } catch (e: Exception) {
                Log.w(TAG, "VOICE_COMMUNICATION audio source unavailable, falling back to MIC", e)
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }

            recorder.apply {
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentOutputFile = outputFile
            _isRecording.value = true
            _recordingDuration.value = 0L

            timerJob?.cancel()
            timerJob = scope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingDuration.value += 1
                }
            }
            Log.i(TAG, "Call recording started: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording", e)
            cleanup()
            false
        }
    }

    @Synchronized
    fun stopRecording(): File? {
        val file = currentOutputFile
        cleanup()
        Log.i(TAG, "Call recording stopped: ${file?.absolutePath}")
        return file
    }

    private fun cleanup() {
        timerJob?.cancel()
        timerJob = null
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up MediaRecorder", e)
        } finally {
            mediaRecorder = null
            _isRecording.value = false
            _recordingDuration.value = 0L
            currentOutputFile = null
        }
    }

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}
