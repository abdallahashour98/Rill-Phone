package dev.goodwy.rphone.controller.util.shizuku

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ShellAudioPipeline {
    private companion object {
        const val TAG = "ShellAudioPipeline"
        const val RELAY_BUFFER_SIZE = 32 * 1024
        const val PROCESS_STOP_GRACE_PERIOD_SEC = 2L
    }

    private val isRecordingActive = AtomicBoolean(false)
    private var scrcpyProcess: Process? = null
    private var serverSocket: LocalServerSocket? = null
    private var clientConnection: LocalSocket? = null
    private var audioWriteEnd: ParcelFileDescriptor? = null
    private var shellScope: CoroutineScope? = null
    private var audioPipeRelayJob: Job? = null

    fun startCapture(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String,
        isDebuggingModeEnabled: Boolean
    ): ParcelFileDescriptor? {
        if (isRecordingActive.get()) {
            Log.w(TAG, "startCapture() rejected: a session is already active")
            return null
        }

        try {
            Log.i(TAG, "Initializing ShellAudioPipeline...")

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

            // Create Local Unix domain socket
            val socketName = ScrcpyConfig.getRandomSocketName()
            val serverFullSocketName = ScrcpyConfig.SERVER_SOCKET_NAME_PREFIX + socketName
            serverSocket = LocalServerSocket(serverFullSocketName)
            Log.d(TAG, "Listening on abstract socket: $serverFullSocketName")

            shellScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            spawnAudioRelayCoroutine(isDebuggingModeEnabled)

            // Build and launch scrcpy-server via app_process
            val audioSourceEnum = ScrcpyAudioSource.fromKey(audioSource)
            val audioCodecEnum = ScrcpyAudioCodec.fromKey(audioCodec)
            val serverArgs = ScrcpyConfig.buildServerArgs(socketName, audioSourceEnum, audioCodecEnum, audioBitRate)
            val launchCommand = mutableListOf("app_process", "/", ScrcpyConfig.SERVER_MAIN_CLASS).apply {
                addAll(serverArgs)
            }

            val scrcpyBuilder = ProcessBuilder(launchCommand).apply {
                environment()["CLASSPATH"] = serverPath
                redirectErrorStream(true)
            }

            scrcpyProcess = scrcpyBuilder.start()
            isRecordingActive.set(true)
            Log.i(TAG, "scrcpy-server launched successfully (pid=${scrcpyProcess})")

            spawnLogConsumerCoroutine(scrcpyProcess!!)
            spawnProcessMonitorCoroutine(scrcpyProcess!!)

            Log.i(TAG, "Pipeline established, returning read pipe to app")
            return pipeReadEnd
        } catch (e: Exception) {
            Log.e(TAG, "Failure during pipeline startup", e)
            stopCapture()
            return null
        }
    }

    fun stopCapture() {
        if (!isRecordingActive.compareAndSet(true, false)) {
            Log.d(TAG, "stopCapture() called but no active session")
            return
        }

        Log.i(TAG, "Stopping scrcpy-server process...")
        runCatching { scrcpyProcess?.destroy() }

        try {
            scrcpyProcess?.waitFor(PROCESS_STOP_GRACE_PERIOD_SEC, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted waiting for scrcpy-server exit", e)
        }

        runCatching {
            runBlocking {
                withTimeoutOrNull(2000L) {
                    audioPipeRelayJob?.join()
                }
            }
        }

        runCatching { shellScope?.cancel() }
        runCatching { clientConnection?.close() }
        runCatching { serverSocket?.close() }
        runCatching { audioWriteEnd?.close() }

        scrcpyProcess = null
        clientConnection = null
        serverSocket = null
        audioWriteEnd = null
        shellScope = null
        audioPipeRelayJob = null

        Log.i(TAG, "Recording pipeline stopped and resources released")
    }

    private fun spawnAudioRelayCoroutine(verbose: Boolean) {
        audioPipeRelayJob = shellScope?.launch(Dispatchers.IO) {
            try {
                val connection = serverSocket?.accept() ?: run {
                    Log.w(TAG, "Server socket returned null")
                    return@launch
                }
                clientConnection = connection
                Log.i(TAG, "scrcpy-server connected to local socket")

                val sourceStream = connection.inputStream
                val destinationStream = ParcelFileDescriptor.AutoCloseOutputStream(audioWriteEnd)
                val buffer = ByteArray(RELAY_BUFFER_SIZE)
                var lastLogTimeMs = System.currentTimeMillis()

                while (isActive) {
                    val bytesRead = sourceStream.read(buffer)
                    if (bytesRead == -1) {
                        Log.d(TAG, "Socket EOF from scrcpy-server")
                        break
                    }
                    destinationStream.write(buffer, 0, bytesRead)
                    if (verbose && bytesRead > 0) {
                        val now = System.currentTimeMillis()
                        if (now - lastLogTimeMs >= 2000) {
                            lastLogTimeMs = now
                            Log.v(TAG, "Relayed $bytesRead bytes to pipe")
                        }
                    }
                }
            } catch (e: IOException) {
                if (isRecordingActive.get()) {
                    Log.e(TAG, "Relay I/O error", e)
                }
            } finally {
                stopCapture()
            }
        }
    }

    private fun spawnLogConsumerCoroutine(process: Process) {
        shellScope?.launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (isActive && line != null) {
                        Log.d(TAG, "[scrcpy] $line")
                        line = reader.readLine()
                    }
                }
            } catch (_: InterruptedIOException) {
            } catch (e: Exception) {
                Log.w(TAG, "Log consumer ended: ${e.message}")
            }
        }
    }

    private fun spawnProcessMonitorCoroutine(process: Process) {
        shellScope?.launch(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                if (exitCode != 0 && isRecordingActive.get()) {
                    Log.e(TAG, "scrcpy-server exited unexpectedly with code $exitCode")
                    stopCapture()
                } else {
                    Log.i(TAG, "scrcpy-server exited with code $exitCode")
                }
            } catch (_: InterruptedException) {
            }
        }
    }
}
