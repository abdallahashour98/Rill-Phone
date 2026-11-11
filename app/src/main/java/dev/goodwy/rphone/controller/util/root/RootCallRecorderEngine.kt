package dev.goodwy.rphone.controller.util.root

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioCodec
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioMuxer
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioSource
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyClient
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyConfig
import dev.goodwy.rphone.controller.util.shizuku.ServerExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object RootCallRecorderEngine {
    private const val TAG = "RootCallRecorderEngine"

    private var audioPipeline: RootAudioPipeline? = null
    private var audioPipePfd: ParcelFileDescriptor? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var client: ScrcpyClient? = null
    private var muxer: ScrcpyAudioMuxer? = null
    private var clientJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isRecording: Boolean
        get() = audioPipeline != null

    fun isBluetoothHeadsetConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking bluetooth device status", e)
            false
        }
    }

    suspend fun startRecording(
        context: Context,
        outputFile: File,
        audioSource: ScrcpyAudioSource = ScrcpyAudioSource.VOICE_CALL,
        codec: ScrcpyAudioCodec = ScrcpyAudioCodec.AAC,
        bitRate: Int = 96000
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Preparing to start call recording via Root engine...")

            // Check and extract scrcpy-server JAR
            val serverPath = ScrcpyConfig.getServerPath(context)
            val serverOk = ServerExtractor.ensureServerFile(context, serverPath)
            if (!serverOk) {
                Log.e(TAG, "scrcpy-server JAR not available or failed verification")
                return@withContext false
            }

            // Verify Root access is available
            val hasRoot = RootUtils.testSuAccess()
            if (!hasRoot) {
                Log.e(TAG, "Root (su) access not granted. Cannot start Root call recorder.")
                return@withContext false
            }

            // Check for Bluetooth headset presence
            val isBtConnected = isBluetoothHeadsetConnected(context)
            if (isBtConnected) {
                Log.i(TAG, "Bluetooth headset is active during call. Ensuring full 2-way capture via Root...")
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                try {
                    @Suppress("DEPRECATION")
                    if (audioManager?.isBluetoothScoOn == false) {
                        audioManager.startBluetoothSco()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Notice on Bluetooth SCO activation", e)
                }
            }

            val pipeline = RootAudioPipeline()
            val readPfd = pipeline.startCapture(
                audioSource = audioSource.cliKey,
                audioCodec = codec.cliKey,
                audioBitRate = bitRate,
                serverPath = serverPath,
                isDebuggingModeEnabled = BuildConfig.DEBUG
            ) ?: run {
                Log.e(TAG, "RootAudioPipeline startCapture returned null pipe")
                return@withContext false
            }

            audioPipeline = pipeline
            audioPipePfd = readPfd

            val outPfd = ParcelFileDescriptor.open(
                outputFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
            )
            outputPfd = outPfd

            val audioMuxer = ScrcpyAudioMuxer(outPfd.fileDescriptor, outputFile.absolutePath)
            audioMuxer.initialize(codec)
            muxer = audioMuxer

            val scrcpyClient = ScrcpyClient(
                inputPfd = readPfd,
                expectedCodec = codec,
                listener = object : ScrcpyClient.AudioPacketListener {
                    override fun onMetadataReceived(codec: ScrcpyAudioCodec) {
                        audioMuxer.initialize(codec)
                    }

                    override fun onAudioPacket(packet: ScrcpyClient.AudioPacket) {
                        audioMuxer.writePacket(packet, codec)
                    }

                    override fun onStreamEnd(error: String?) {
                        Log.d(TAG, "Root client audio stream ended (error=$error)")
                    }
                }
            )
            client = scrcpyClient

            clientJob = scope.launch(Dispatchers.IO) {
                scrcpyClient.start()
            }

            Log.i(TAG, "Root call recording started successfully: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Root call recording", e)
            cleanup()
            false
        }
    }

    fun stopRecording(): Boolean {
        return try {
            audioPipeline?.stopCapture()
            cleanup()
            Log.i(TAG, "Root call recording stopped successfully")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Root call recording", e)
            false
        }
    }

    private fun cleanup() {
        runCatching { client?.close() }
        runCatching { clientJob?.cancel() }
        runCatching { muxer?.close() }
        runCatching { audioPipePfd?.close() }
        runCatching { outputPfd?.close() }

        audioPipeline = null
        client = null
        clientJob = null
        muxer = null
        audioPipePfd = null
        outputPfd = null
    }
}
