package dev.goodwy.rphone.controller.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioCodec
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioSource
import dev.goodwy.rphone.controller.util.shizuku.ShizukuCallRecorderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object CallRecorder {
    private const val TAG = "CallRecorder"

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isUsingShizukuEngine = false

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startRecording(context: Context, phoneNumber: String, onResult: ((Boolean) -> Unit)? = null) {
        if (_isRecording.value) {
            onResult?.invoke(true)
            return
        }

        scope.launch(Dispatchers.IO) {
            val started = doStartRecording(context, phoneNumber)
            withContext(Dispatchers.Main) {
                onResult?.invoke(started)
            }
        }
    }

    suspend fun startRecordingSuspend(context: Context, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        doStartRecording(context, phoneNumber)
    }

    private suspend fun doStartRecording(context: Context, phoneNumber: String): Boolean {
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

            // Check if Shizuku elevated engine is available for two-way recording
            ShizukuManager.ensureInitialized()
            val isShizukuReady = ShizukuManager.isAvailable.value && ShizukuManager.isPermissionGranted.value
            if (isShizukuReady) {
                Log.i(TAG, "Shizuku ready. Attempting two-way call recording via Shizuku engine...")
                val prefs = PreferenceManager(context)
                val savedSource = prefs.getString("call_recording_audio_source", "voice-call") ?: "voice-call"
                val audioSourceKey = if (savedSource == "mic-voice-communication" && !prefs.getBoolean("call_recording_audio_source_explicit_comm", false)) {
                    "voice-call"
                } else {
                    savedSource
                }
                val audioSource = ScrcpyAudioSource.fromKey(audioSourceKey)

                val shizukuStarted = ShizukuCallRecorderEngine.startRecording(
                    context = context,
                    outputFile = outputFile,
                    audioSource = audioSource,
                    codec = ScrcpyAudioCodec.AAC,
                    bitRate = 96000
                )
                if (shizukuStarted) {
                    isUsingShizukuEngine = true
                    currentOutputFile = outputFile
                    _isRecording.value = true
                    _recordingDuration.value = 0L

                    timerJob?.cancel()
                    timerJob = scope.launch(Dispatchers.Main) {
                        while (_isRecording.value) {
                            delay(1000)
                            _recordingDuration.value += 1
                        }
                    }
                    Log.i(TAG, "Call recording started via Shizuku engine: ${outputFile.absolutePath}")
                    return true
                } else {
                    Log.w(TAG, "Shizuku engine start failed, falling back to standard MediaRecorder")
                }
            }

            // Fallback: standard MediaRecorder on background thread
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
                MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
                MediaRecorder.AudioSource.MIC to "MIC",
                MediaRecorder.AudioSource.DEFAULT to "DEFAULT"
            )

            var activeRecorder: MediaRecorder? = null
            for ((source, sourceName) in audioSources) {
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                try {
                    rec.apply {
                        setAudioSource(source)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioChannels(1)
                        setAudioEncodingBitRate(96000)
                        setAudioSamplingRate(44100)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                    }
                    Log.i(TAG, "Call recording started successfully with audio source: $sourceName")
                    activeRecorder = rec
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Audio source $sourceName unavailable, trying next fallback: ${e.message}")
                    try {
                        rec.reset()
                        rec.release()
                    } catch (_: Exception) {}
                }
            }

            if (activeRecorder == null) {
                Log.e(TAG, "All audio sources failed for call recording")
                cleanup()
                return false
            }

            mediaRecorder = activeRecorder
            currentOutputFile = outputFile
            _isRecording.value = true
            _recordingDuration.value = 0L

            timerJob?.cancel()
            timerJob = scope.launch(Dispatchers.Main) {
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
        invalidateCache()
        Log.i(TAG, "Call recording stopped: ${file?.absolutePath}")
        return file
    }

    private fun cleanup() {
        timerJob?.cancel()
        timerJob = null

        if (isUsingShizukuEngine) {
            try {
                ShizukuCallRecorderEngine.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping Shizuku engine", e)
            } finally {
                isUsingShizukuEngine = false
            }
        }

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

    data class RecordingMeta(
        val file: File,
        val cleanPhone: String,
        val last7Phone: String,
        val timestamp: Long,
        val lastModified: Long
    )

    private val cacheLock = Any()
    @Volatile
    private var cachedMeta: List<RecordingMeta>? = null
    @Volatile
    private var lastCacheTime = 0L

    fun invalidateCache() {
        synchronized(cacheLock) {
            cachedMeta = null
            lastCacheTime = 0L
        }
    }

    private fun getCachedRecordings(context: Context): List<RecordingMeta> {
        val now = System.currentTimeMillis()
        val current = cachedMeta
        if (current != null && (now - lastCacheTime) < 15_000L) {
            return current
        }

        return synchronized(cacheLock) {
            val doubleCheck = cachedMeta
            if (doubleCheck != null && (now - lastCacheTime) < 15_000L) {
                return doubleCheck
            }

            val dir = getRecordingsDir(context)
            val files = dir.listFiles { f ->
                f.isFile && f.name.startsWith("Call_") &&
                (f.name.endsWith(".m4a") || f.name.endsWith(".ogg") || f.name.endsWith(".aac"))
            } ?: emptyArray()

            val list = files.mapNotNull { file ->
                val nameNoExt = file.nameWithoutExtension
                val parts = nameNoExt.split("_")
                if (parts.size >= 3) {
                    val filePhone = parts[1].filter { it.isDigit() }
                    val fileTimestamp = parts.last().toLongOrNull() ?: 0L
                    val last7File = if (filePhone.length >= 7) filePhone.takeLast(7) else filePhone
                    RecordingMeta(file, filePhone, last7File, fileTimestamp, file.lastModified())
                } else null
            }
            cachedMeta = list
            lastCacheTime = now
            list
        }
    }

    fun getRecordingsDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
            ?: File(context.filesDir, "recordings")
        return File(baseDir, "RillPhoneRecordings").apply {
            if (!exists()) mkdirs()
        }
    }

    fun findRecordingForCall(
        context: Context,
        phoneNumber: String,
        callDate: Long,
        durationSeconds: Long = 0L
    ): File? {
        val cleanTarget = phoneNumber.filter { it.isDigit() }
        if (cleanTarget.isEmpty()) return null
        val last7Target = if (cleanTarget.length >= 7) cleanTarget.takeLast(7) else cleanTarget

        val recordings = getCachedRecordings(context)
        if (recordings.isEmpty()) return null

        val maxAllowedDiff = ((durationSeconds + 180).coerceAtLeast(300)) * 1000L

        return recordings.firstOrNull { meta ->
            val numberMatches = last7Target.isNotEmpty() && (meta.last7Phone == last7Target || meta.cleanPhone == cleanTarget)
            val timeDiff = kotlin.math.abs(meta.timestamp - callDate)
            numberMatches && timeDiff <= maxAllowedDiff
        }?.file
    }

    fun getRecordingsForNumber(context: Context, phoneNumber: String): List<File> {
        val cleanTarget = phoneNumber.filter { it.isDigit() }
        if (cleanTarget.isEmpty()) return emptyList()
        val last7Target = if (cleanTarget.length >= 7) cleanTarget.takeLast(7) else cleanTarget

        val recordings = getCachedRecordings(context)
        return recordings.filter { meta ->
            last7Target.isNotEmpty() && (meta.last7Phone == last7Target || meta.cleanPhone == cleanTarget)
        }.sortedByDescending { it.lastModified }.map { it.file }
    }

    fun getAllRecordings(context: Context): List<File> {
        return getCachedRecordings(context).sortedByDescending { it.lastModified }.map { it.file }
    }

    fun getAudioDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}

object RecordingPlayer {
    private const val TAG = "RecordingPlayer"
    private var mediaPlayer: android.media.MediaPlayer? = null

    private val _currentPlayingFile = MutableStateFlow<File?>(null)
    val currentPlayingFile: StateFlow<File?> = _currentPlayingFile.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun playOrPause(file: File, context: Context? = null) {
        if (_currentPlayingFile.value?.absolutePath == file.absolutePath) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
        } else {
            play(file, context)
        }
    }

    fun play(file: File, context: Context? = null) {
        stop()
        try {
            if (context != null) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager?.mode != android.media.AudioManager.MODE_NORMAL) {
                    audioManager?.mode = android.media.AudioManager.MODE_NORMAL
                }
            }
            val player = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    stopProgressTracking()
                }
                start()
            }
            mediaPlayer = player
            _currentPlayingFile.value = file
            _isPlaying.value = true
            _duration.value = player.duration
            _currentPosition.value = 0
            startProgressTracking()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing recording", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressTracking()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            it.seekTo(positionMs)
            _currentPosition.value = positionMs
        }
    }

    fun stop() {
        stopProgressTracking()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingFile.value = null
        _currentPosition.value = 0
        _duration.value = 0
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        _currentPosition.value = player.currentPosition
                    } else {
                        break
                    }
                } catch (_: Exception) {
                    break
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun shareRecording(context: Context, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(dev.goodwy.rphone.R.string.share_recording)))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing recording", e)
        }
    }

    fun deleteRecording(file: File): Boolean {
        try {
            if (_currentPlayingFile.value?.absolutePath == file.absolutePath) {
                stop()
            }
            val result = if (file.exists()) {
                file.delete()
            } else {
                true
            }
            if (result) {
                CallRecorder.invalidateCache()
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recording", e)
            return false
        }
    }
}
