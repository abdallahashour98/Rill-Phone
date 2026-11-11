package dev.goodwy.rphone.controller.util.root

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioCodec
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyAudioSource
import dev.goodwy.rphone.controller.util.shizuku.ScrcpyConfig
import dev.goodwy.rphone.controller.util.shizuku.ServerExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RootAudioPipeline {
    private companion object {
        const val TAG = "RootAudioPipeline"
        const val RELAY_BUFFER_SIZE = 32 * 1024
        const val PROCESS_STOP_GRACE_PERIOD_SEC = 2L
    }

    private val isRecordingActive = AtomicBoolean(false)
    private var suProcess: Process? = null
    private var serverSocket: LocalServerSocket? = null
    private var clientConnection: LocalSocket? = null
    private var audioWriteEnd: ParcelFileDescriptor? = null
    private var pipelineScope: CoroutineScope? = null
    private var audioPipeRelayJob: Job? = null

    fun startCapture(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String,
        isDebuggingModeEnabled: Boolean = false
    ): ParcelFileDescriptor? {
        if (isRecordingActive.get()) {
            Log.w(TAG, "startCapture() rejected: a session is already active")
            return null
        }

        try {
            Log.i(TAG, "Initializing RootAudioPipeline via direct su...")

            val serverJarFile = File(serverPath)
            if (!serverJarFile.exists() || !ServerExtractor.verifyServerHash(serverJarFile)) {
                Log.w(TAG, "Server JAR missing or hash mismatch at $serverPath")
                return null
            }

            // Create Kernel pipe
            val pipe = ParcelFileDescriptor.createPipe()
            val pipeReadEnd = pipe[0]
            val pipeWriteEnd = pipe[1]
            audioWriteEnd = pipeWriteEnd

            // Create Local Unix domain socket in abstract namespace
            val socketName = ScrcpyConfig.getRandomSocketName()
            val serverFullSocketName = ScrcpyConfig.SERVER_SOCKET_NAME_PREFIX + socketName
            serverSocket = LocalServerSocket(serverFullSocketName)
            Log.d(TAG, "Root pipeline listening on abstract socket: $serverFullSocketName")

            pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            spawnAudioRelayCoroutine(isDebuggingModeEnabled)

            // Build args for scrcpy-server
            val audioSourceEnum = ScrcpyAudioSource.fromKey(audioSource)
            val audioCodecEnum = ScrcpyAudioCodec.fromKey(audioCodec)
            val serverArgs = ScrcpyConfig.buildServerArgs(socketName, audioSourceEnum, audioCodecEnum, audioBitRate)

            // Launch via su command
            val escapedArgs = serverArgs.joinToString(" ")
            val suCommand = "export CLASSPATH=\"$serverPath\"; exec app_process / ${ScrcpyConfig.SERVER_MAIN_CLASS} $escapedArgs"
            Log.i(TAG, "Executing su command: $suCommand")

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", suCommand))
            suProcess = process
            isRecordingActive.set(true)

            spawnLogConsumerCoroutine(process)
            spawnProcessMonitorCoroutine(process)

            Log.i(TAG, "Root pipeline established, returning read pipe")
            return pipeReadEnd
        } catch (e: Exception) {
            Log.e(TAG, "Failure during root pipeline startup", e)
            stopCapture()
            return null
        }
    }

    fun stopCapture() {
        if (!isRecordingActive.compareAndSet(true, false)) {
            Log.d(TAG, "stopCapture() called but no active session")
            return
        }

        Log.i(TAG, "Stopping Root audio capture process...")
        runCatching { suProcess?.destroy() }

        try {
            suProcess?.waitFor(PROCESS_STOP_GRACE_PERIOD_SEC, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted waiting for su process exit", e)
        }

        // Kill any orphaned scrcpy-server process via su
        Thread {
            try {
                val killProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f com.genymobile.scrcpy.Server"))
                killProcess.waitFor(2, TimeUnit.SECONDS)
            } catch (_: Exception) {}
        }.start()

        runCatching {
            runBlocking {
                withTimeoutOrNull(2000L) {
                    audioPipeRelayJob?.join()
                }
            }
        }

        runCatching { pipelineScope?.cancel() }
        runCatching { clientConnection?.close() }
        runCatching { serverSocket?.close() }
        runCatching { audioWriteEnd?.close() }

        suProcess = null
        clientConnection = null
        serverSocket = null
        audioWriteEnd = null
        pipelineScope = null
        audioPipeRelayJob = null

        Log.i(TAG, "Root recording pipeline stopped and resources released")
    }

    private fun spawnAudioRelayCoroutine(verbose: Boolean) {
        audioPipeRelayJob = pipelineScope?.launch(Dispatchers.IO) {
            try {
                val connection = serverSocket?.accept() ?: run {
                    Log.w(TAG, "Server socket returned null")
                    return@launch
                }
                clientConnection = connection
                Log.i(TAG, "scrcpy-server connected to local socket via Root")

                val sourceStream = connection.inputStream
                val sinkStream = ParcelFileDescriptor.AutoCloseOutputStream(audioWriteEnd)

                val buffer = ByteArray(RELAY_BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesTransferred = 0L

                while (isActive && isRecordingActive.get()) {
                    bytesRead = try {
                        sourceStream.read(buffer)
                    } catch (e: InterruptedIOException) {
                        break
                    } catch (e: IOException) {
                        if (isRecordingActive.get()) {
                            Log.e(TAG, "Socket read failed: ${e.message}")
                        }
                        break
                    }

                    if (bytesRead == -1) {
                        Log.i(TAG, "EOF encountered on scrcpy-server socket")
                        break
                    }

                    try {
                        sinkStream.write(buffer, 0, bytesRead)
                        totalBytesTransferred += bytesRead
                    } catch (e: IOException) {
                        if (isRecordingActive.get()) {
                            Log.e(TAG, "Pipe write failed: ${e.message}")
                        }
                        break
                    }
                }

                sinkStream.flush()
                Log.i(TAG, "Audio relay finished: transferred $totalBytesTransferred bytes")
            } catch (e: Exception) {
                if (isRecordingActive.get()) {
                    Log.e(TAG, "Unhandled error in audio relay coroutine", e)
                }
            } finally {
                runCatching { clientConnection?.close() }
                clientConnection = null
            }
        }
    }

    private fun spawnLogConsumerCoroutine(process: Process) {
        pipelineScope?.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var logLine: String? = null
                    while (isActive && reader.readLine().also { logLine = it } != null) {
                        Log.d(TAG, "[scrcpy-root] $logLine")
                    }
                }
            } catch (_: Exception) {}
        }
        pipelineScope?.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var logLine: String? = null
                    while (isActive && reader.readLine().also { logLine = it } != null) {
                        Log.w(TAG, "[scrcpy-root-err] $logLine")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun spawnProcessMonitorCoroutine(process: Process) {
        pipelineScope?.launch(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                Log.i(TAG, "Root scrcpy process terminated with exitCode=$exitCode")
            } catch (_: InterruptedException) {}
        }
    }
}
