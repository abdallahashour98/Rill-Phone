package dev.goodwy.rphone.controller.util.root

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootUtils {
    private const val TAG = "RootUtils"

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val SU_BINARY_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/apex/com.android.runtime/bin/su"
    )

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val success: Boolean = exitCode == 0
    )

    fun checkStatus() {
        scope.launch {
            val available = checkSuBinaryExists()
            _isRootAvailable.value = available
            if (available) {
                val granted = testSuAccess()
                _isRootGranted.value = granted
            } else {
                _isRootGranted.value = false
            }
        }
    }

    suspend fun checkStatusSuspend(): Boolean = withContext(Dispatchers.IO) {
        val available = checkSuBinaryExists()
        _isRootAvailable.value = available
        if (available) {
            val granted = testSuAccess()
            _isRootGranted.value = granted
            granted
        } else {
            _isRootGranted.value = false
            false
        }
    }

    fun requestRootPermission(onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val granted = testSuAccess()
            _isRootGranted.value = granted
            withContext(Dispatchers.Main) {
                onResult?.invoke(granted)
            }
        }
    }

    private fun checkSuBinaryExists(): Boolean {
        try {
            for (path in SU_BINARY_PATHS) {
                if (File(path).exists()) return true
            }
            // Check PATH via which command
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (_: Exception) {
            return false
        }
    }

    fun testSuAccess(): Boolean {
        return try {
            val result = executeSuCommand("id", timeoutSec = 5)
            val isRoot = result.success && result.stdout.contains("uid=0")
            Log.d(TAG, "testSuAccess: isRoot=$isRoot, output=${result.stdout.trim()}")
            isRoot
        } catch (e: Exception) {
            Log.w(TAG, "testSuAccess failed", e)
            false
        }
    }

    fun executeSuCommand(command: String, timeoutSec: Long = 10): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdout.appendLine(line)
                        }
                    }
                } catch (_: Exception) {}
            }
            readerThread.start()

            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stderr.appendLine(line)
                        }
                    }
                } catch (_: Exception) {}
            }
            errThread.start()

            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return CommandResult(-1, stdout.toString(), "Command timed out after $timeoutSec seconds", false)
            }

            readerThread.join(1000)
            errThread.join(1000)

            CommandResult(process.exitValue(), stdout.toString(), stderr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error executing su command: $command", e)
            CommandResult(-1, "", e.message ?: "Execution error", false)
        }
    }
}
