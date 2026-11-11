package dev.goodwy.rphone.controller.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import dev.goodwy.rphone.controller.util.root.RootCallRecorderEngine
import dev.goodwy.rphone.controller.util.root.RootUtils
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioCodec
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioSource
import dev.goodwy.rphone.controller.util.shizuku.ShizukuCallRecorderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CallRecorder {
    private const val TAG = "CallRecorder"

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isUsingShizukuEngine = false
    private var isUsingRootEngine = false

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
            // Store recordings in app-specific external files dir or internal files dir
            val recordDir = getRecordingsDir(context)

            val cleanPhone = phoneNumber.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
            val fileName = "Call_${cleanPhone}_${System.currentTimeMillis()}.m4a"
            val outputFile = File(recordDir, fileName)

            val prefs = PreferenceManager(context)
            val selectedEngine = prefs.getString("call_recording_engine_mode", "auto") ?: "auto"
            val savedSource = prefs.getString("call_recording_audio_source", "voice-call") ?: "voice-call"
            val audioSourceKey = if (savedSource == "mic-voice-communication" && !prefs.getBoolean("call_recording_audio_source_explicit_comm", false)) {
                "voice-call"
            } else {
                savedSource
            }
            val audioSource = ScrcpyAudioSource.fromKey(audioSourceKey)

            // 1. Try Root Engine if selected or auto
            val tryRoot = selectedEngine == "root" || selectedEngine == "auto"
            if (tryRoot) {
                val hasRoot = RootUtils.testSuAccess()
                if (hasRoot) {
                    Log.i(TAG, "Root (su) available. Attempting 2-way call recording via Root engine (with Bluetooth support)...")
                    val rootStarted = RootCallRecorderEngine.startRecording(
                        context = context,
                        outputFile = outputFile,
                        audioSource = audioSource,
                        codec = ScrcpyAudioCodec.AAC,
                        bitRate = 96000
                    )
                    if (rootStarted) {
                        isUsingRootEngine = true
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
                        Log.i(TAG, "Call recording started via Root engine: ${outputFile.absolutePath}")
                        return true
                    } else {
                        Log.w(TAG, "Root engine failed to start, falling back to next engine")
                    }
                } else if (selectedEngine == "root") {
                    Log.e(TAG, "Root engine selected but root access not granted")
                }
            }

            // 2. Try Shizuku Engine if selected or auto
            val tryShizuku = selectedEngine == "shizuku" || (selectedEngine == "auto" && !isUsingRootEngine)
            if (tryShizuku) {
                ShizukuManager.ensureInitialized()
                val isShizukuReady = ShizukuManager.isAvailable.value && ShizukuManager.isPermissionGranted.value
                if (isShizukuReady) {
                    Log.i(TAG, "Shizuku ready. Attempting two-way call recording via Shizuku engine...")

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

        if (isUsingRootEngine) {
            try {
                RootCallRecorderEngine.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping Root engine", e)
            } finally {
                isUsingRootEngine = false
            }
        }

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
        val dirType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.DIRECTORY_RECORDINGS
        } else {
            "Recordings"
        }
        val baseDir = context.getExternalFilesDir(dirType)
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

class ProximityAudioRouter(private val context: Context) : SensorEventListener {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var wakeLock: PowerManager.WakeLock? = null
    private var isListening = false
    private var audioTrackRef: WeakReference<AudioTrack>? = null

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true
            ) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "RillPhone:RecordingProximityWakeLock"
                )
            }
        } catch (_: Exception) {}
    }

    fun attachAudioTrack(track: AudioTrack?) {
        audioTrackRef = if (track != null) WeakReference(track) else null
        applyRouting(isNear = false)
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        try {
            proximitySensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {}
        applyRouting(isNear = false)
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
        releaseWakeLock()
        resetAudioRouting()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
        val distance = event.values.firstOrNull() ?: return
        val maxRange = event.sensor.maximumRange
        val isNear = distance < 4.0f && distance < maxRange
        applyRouting(isNear)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun isHeadsetConnected(): Boolean {
        val am = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        } else {
            @Suppress("DEPRECATION")
            return am.isWiredHeadsetOn || am.isBluetoothScoOn || am.isBluetoothA2dpOn
        }
    }

    private fun applyRouting(isNear: Boolean) {
        val am = audioManager ?: return
        val audioTrack = audioTrackRef?.get()

        if (isHeadsetConnected()) {
            releaseWakeLock()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioTrack?.preferredDevice = null
                }
            } catch (_: Exception) {}
            return
        }

        if (isNear) {
            // Held to ear -> Route to earpiece & turn screen off
            acquireWakeLock()
            try {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        am.setCommunicationDevice(earpiece)
                    }
                }
                am.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val earpiece = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        audioTrack?.preferredDevice = earpiece
                    }
                }
            } catch (e: Exception) {
                Log.w("ProximityAudioRouter", "Error routing to earpiece", e)
            }
        } else {
            // Away from ear -> Route to loudspeaker & screen on
            releaseWakeLock()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val speaker = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        audioTrack?.preferredDevice = speaker
                    } else {
                        audioTrack?.preferredDevice = null
                    }
                }
            } catch (e: Exception) {
                Log.w("ProximityAudioRouter", "Error routing to speaker", e)
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun resetAudioRouting() {
        val am = audioManager ?: return
        val audioTrack = audioTrackRef?.get()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
            am.mode = AudioManager.MODE_NORMAL
            am.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioTrack?.preferredDevice = null
            }
        } catch (_: Exception) {}
    }
}

class MonoAudioDecoderPlayer(
    private val file: File,
    private val onProgress: (positionMs: Int) -> Unit,
    private val onCompletion: () -> Unit,
    private val onError: (Exception) -> Unit
) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null

    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    @Volatile private var isStopped = false
    @Volatile private var pendingSeekMs: Int? = null

    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var durationMs: Int = 0
        private set

    fun getAudioTrack(): AudioTrack? = audioTrack

    fun start(): Boolean {
        return try {
            val ex = MediaExtractor().apply { setDataSource(file.absolutePath) }
            extractor = ex

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioIndex = i
                    format = f
                    break
                }
            }

            if (audioIndex < 0 || format == null) {
                ex.release()
                return false
            }

            ex.selectTrack(audioIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            durationMs = (durationUs / 1000L).toInt()

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(format, null, null, 0)
            dec.start()

            audioTrack = track
            codec = dec
            track.play()
            isPlaying = true
            isPaused = false
            isStopped = false

            playbackJob = scope.launch {
                runPlaybackLoop(sampleRate, channelCount)
            }
            true
        } catch (e: Exception) {
            Log.e("MonoAudioDecoderPlayer", "Error initializing player", e)
            release()
            false
        }
    }

    private suspend fun runPlaybackLoop(sampleRate: Int, initialChannelCount: Int) {
        val dec = codec ?: return
        val ex = extractor ?: return
        val track = audioTrack ?: return

        var activeChannels = initialChannelCount
        val info = MediaCodec.BufferInfo()
        var isEOS = false

        try {
            while (scope.isActive && !isStopped) {
                val seek = pendingSeekMs
                if (seek != null) {
                    pendingSeekMs = null
                    try {
                        ex.seekTo(seek.toLong() * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        dec.flush()
                        track.flush()
                        isEOS = false
                    } catch (_: Exception) {}
                }

                if (isPaused) {
                    delay(40)
                    continue
                }

                // Feed input to decoder
                if (!isEOS) {
                    val inIdx = dec.dequeueInputBuffer(5000L)
                    if (inIdx >= 0) {
                        val inBuf = dec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = ex.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                dec.queueInputBuffer(inIdx, 0, sampleSize, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                }

                // Drain output from decoder
                val outIdx = dec.dequeueOutputBuffer(info, 5000L)
                if (outIdx >= 0) {
                    val outBuf = dec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)

                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val numShorts = shortBuf.remaining()

                        if (numShorts > 0) {
                            val outBytes: ByteArray
                            if (activeChannels >= 2) {
                                val numFrames = numShorts / activeChannels
                                outBytes = ByteArray(numFrames * 4)
                                val byteBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
                                for (f in 0 until numFrames) {
                                    val left = shortBuf.get(f * activeChannels).toInt()
                                    val right = shortBuf.get(f * activeChannels + 1).toInt()
                                    val mixed = ((left + right) / 2).toShort()
                                    byteBuf.putShort(mixed)
                                    byteBuf.putShort(mixed)
                                }
                            } else {
                                val numFrames = numShorts
                                outBytes = ByteArray(numFrames * 4)
                                val byteBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
                                for (f in 0 until numFrames) {
                                    val sample = shortBuf.get(f)
                                    byteBuf.putShort(sample)
                                    byteBuf.putShort(sample)
                                }
                            }

                            track.write(outBytes, 0, outBytes.size)
                        }

                        val currentMs = (info.presentationTimeUs / 1000L).toInt()
                        if (currentMs in 0..durationMs) {
                            onProgress(currentMs)
                        }
                    }
                    dec.releaseOutputBuffer(outIdx, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        delay(250)
                        if (!isStopped) {
                            withContext(Dispatchers.Main) {
                                onCompletion()
                            }
                        }
                        break
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = dec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        activeChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } catch (e: Exception) {
            if (!isStopped) {
                Log.e("MonoAudioDecoderPlayer", "Playback loop exception", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun pause() {
        if (!isPlaying || isPaused) return
        isPaused = true
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        try {
            audioTrack?.play()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        pendingSeekMs = positionMs
    }

    fun stop() {
        isStopped = true
        isPlaying = false
        isPaused = false
        playbackJob?.cancel()
        release()
    }

    private fun release() {
        try {
            audioTrack?.apply {
                pause()
                flush()
                stop()
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null

        try {
            codec?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        codec = null

        try {
            extractor?.release()
        } catch (_: Exception) {}
        extractor = null
    }
}

object RecordingPlayer {
    private const val TAG = "RecordingPlayer"
    private var monoPlayer: MonoAudioDecoderPlayer? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var proximityRouter: ProximityAudioRouter? = null
    private var appContext: Context? = null

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
        if (context != null) {
            appContext = context.applicationContext
        }

        val ctx = appContext
        if (ctx != null) {
            val router = ProximityAudioRouter(ctx)
            proximityRouter = router
            router.startListening()
        }

        _currentPlayingFile.value = file

        // Attempt downmixed mono playback via MonoAudioDecoderPlayer
        val decoderPlayer = MonoAudioDecoderPlayer(
            file = file,
            onProgress = { posMs ->
                _currentPosition.value = posMs
            },
            onCompletion = {
                _isPlaying.value = false
                _currentPosition.value = 0
                proximityRouter?.stopListening()
            },
            onError = { e ->
                Log.w(TAG, "Mono player error, falling back to MediaPlayer: ${e.message}")
                playFallbackMediaPlayer(file, ctx)
            }
        )

        if (decoderPlayer.start()) {
            monoPlayer = decoderPlayer
            proximityRouter?.attachAudioTrack(decoderPlayer.getAudioTrack())
            _isPlaying.value = true
            _duration.value = decoderPlayer.durationMs
            _currentPosition.value = 0
        } else {
            Log.w(TAG, "Mono decoder initialization failed, falling back to MediaPlayer")
            playFallbackMediaPlayer(file, ctx)
        }
    }

    private fun playFallbackMediaPlayer(file: File, context: Context?) {
        try {
            val player = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    stopProgressTracking()
                    proximityRouter?.stopListening()
                }
                start()
            }
            mediaPlayer = player
            _isPlaying.value = true
            _duration.value = player.duration
            _currentPosition.value = 0
            startProgressTracking()
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback MediaPlayer", e)
            stop()
        }
    }

    fun pause() {
        monoPlayer?.pause()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        _isPlaying.value = false
        stopProgressTracking()
        proximityRouter?.stopListening()
    }

    fun resume() {
        val player = monoPlayer
        if (player != null) {
            player.resume()
            _isPlaying.value = true
            proximityRouter?.startListening()
        } else {
            mediaPlayer?.let {
                it.start()
                _isPlaying.value = true
                proximityRouter?.startListening()
                startProgressTracking()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        _currentPosition.value = positionMs
        monoPlayer?.seekTo(positionMs)
        mediaPlayer?.seekTo(positionMs)
    }

    fun stop() {
        stopProgressTracking()
        proximityRouter?.stopListening()
        proximityRouter = null

        monoPlayer?.stop()
        monoPlayer = null

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
            context.startActivity(
                android.content.Intent.createChooser(
                    intent,
                    context.getString(dev.goodwy.rphone.R.string.share_recording)
                )
            )
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
