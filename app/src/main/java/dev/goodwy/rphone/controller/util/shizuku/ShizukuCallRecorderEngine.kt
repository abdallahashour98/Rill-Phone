package dev.goodwy.rphone.controller.util.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.IShellService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuCallRecorderEngine {
    private const val TAG = "ShizukuCallRecorder"

    @Volatile
    private var activeService: IShellService? = null
    private var activeConnection: ServiceConnection? = null
    private var audioPipePfd: ParcelFileDescriptor? = null
    private var outputPfd: ParcelFileDescriptor? = null
    private var client: ScrcpyClient? = null
    private var muxer: ScrcpyAudioMuxer? = null
    private var clientJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isServiceConnected: Boolean
        get() = activeService != null

    private fun getUserServiceArgs(context: Context): Shizuku.UserServiceArgs {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) { 1 }

        return Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("ElevatedShellService")
            .debuggable(BuildConfig.DEBUG)
            .version(version)
    }

    suspend fun getShellService(context: Context): IShellService = suspendCancellableCoroutine { continuation ->
        activeService?.let {
            continuation.resume(it)
            return@suspendCancellableCoroutine
        }

        val args = getUserServiceArgs(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null) {
                    val proxy = IShellService.Stub.asInterface(binder)
                    activeService = proxy
                    Log.i(TAG, "ShellService connected successfully via Shizuku")
                    if (continuation.isActive) continuation.resume(proxy)
                } else {
                    val err = IllegalStateException("Shizuku returned null binder")
                    Log.e(TAG, "Service connected with null binder", err)
                    if (continuation.isActive) continuation.resumeWithException(err)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "ShellService disconnected")
                activeService = null
                activeConnection = null
            }
        }

        activeConnection = connection
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UserService via Shizuku", e)
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        continuation.invokeOnCancellation {
            unbindUserService(context)
        }
    }

    fun unbindUserService(context: Context) {
        val conn = activeConnection ?: return
        try {
            if (Shizuku.pingBinder()) {
                val args = getUserServiceArgs(context)
                Shizuku.unbindUserService(args, conn, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding UserService", e)
        } finally {
            activeConnection = null
            activeService = null
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
            val serverPath = ScrcpyConfig.getServerPath(context)
            val serverOk = ServerExtractor.ensureServerFile(context, serverPath)
            if (!serverOk) {
                Log.e(TAG, "scrcpy-server JAR not available or failed verification")
                return@withContext false
            }

            // Ensure ShellService is available asynchronously without blocking UI thread
            val service = activeService ?: withTimeoutOrNull(3000L) {
                getShellService(context)
            } ?: run {
                Log.e(TAG, "ShellService not bound, cannot record via Shizuku")
                return@withContext false
            }

            val outPfd = ParcelFileDescriptor.open(
                outputFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
            )
            outputPfd = outPfd

            val readPfd = service.startRecording(
                audioSource.cliKey,
                codec.cliKey,
                bitRate,
                serverPath,
                BuildConfig.DEBUG
            ) ?: run {
                Log.e(TAG, "ShellService startRecording returned null pipe")
                outPfd.close()
                outputPfd = null
                return@withContext false
            }
            audioPipePfd = readPfd

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
                        Log.d(TAG, "Client stream ended (error=$error)")
                    }
                }
            )
            client = scrcpyClient

            clientJob = scope.launch(Dispatchers.IO) {
                scrcpyClient.start()
            }

            Log.i(TAG, "Shizuku call recording started successfully: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Shizuku call recording", e)
            cleanup()
            false
        }
    }

    fun stopRecording(): Boolean {
        return try {
            try { activeService?.stopRecording() } catch (_: Exception) {}
            cleanup()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recording", e)
            false
        }
    }

    private fun cleanup() {
        runCatching { client?.close() }
        runCatching { clientJob?.cancel() }
        runCatching { muxer?.close() }
        runCatching { audioPipePfd?.close() }
        runCatching { outputPfd?.close() }

        client = null
        clientJob = null
        muxer = null
        audioPipePfd = null
        outputPfd = null
    }
}
