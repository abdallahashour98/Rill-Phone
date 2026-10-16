package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 4001

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
        _isPermissionGranted.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            val granted = (grantResult == PackageManager.PERMISSION_GRANTED)
            _isPermissionGranted.value = granted
            Log.i(TAG, "Shizuku permission result: $granted")
            if (granted) {
                grantPrivilegedPermissions()
            }
        }
    }

    private var isInitialized = false

    fun ensureInitialized() {
        if (isInitialized) return
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            isInitialized = true
            checkStatus()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Shizuku listeners", e)
        }
    }

    fun checkStatus() {
        try {
            val ping = Shizuku.pingBinder()
            _isAvailable.value = ping
            if (ping) {
                if (Shizuku.isPreV11()) {
                    _isPermissionGranted.value = false
                } else {
                    val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    _isPermissionGranted.value = granted
                    if (granted) {
                        grantPrivilegedPermissions()
                    }
                }
            } else {
                _isPermissionGranted.value = false
            }
        } catch (e: Exception) {
            _isAvailable.value = false
            _isPermissionGranted.value = false
        }
    }

    fun grantPrivilegedPermissions() {
        try {
            val pkg = "dev.goodwy.rphone"
            executeShell("pm grant $pkg android.permission.CAPTURE_AUDIO_OUTPUT 2>/dev/null; appops set $pkg CAPTURE_AUDIO_OUTPUT allow 2>/dev/null; appops set $pkg RECORD_AUDIO allow 2>/dev/null")
            Log.i(TAG, "Attempted privileged permissions grant via Shizuku")
        } catch (e: Exception) {
            Log.w(TAG, "Failed granting permissions via Shizuku", e)
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    fun executeShell(cmd: String): Pair<Int, String> {
        if (!_isAvailable.value || !_isPermissionGranted.value) {
            return -1 to "Shizuku not ready or permission denied"
        }
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            exitCode to output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shell command via Shizuku: $cmd", e)
            -1 to (e.message ?: "Execution failed")
        }
    }

    fun launchShizukuApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val marketIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(marketIntent)
            }
        } catch (_: Exception) {}
    }
}
