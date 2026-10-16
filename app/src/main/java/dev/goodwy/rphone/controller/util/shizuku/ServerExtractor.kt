package dev.goodwy.rphone.controller.util.shizuku

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ServerExtractor {
    private const val TAG = "ServerExtractor"

    fun ensureServerFile(context: Context, serverPath: String): Boolean {
        val file = File(serverPath)
        if (file.exists() && verifyServerHash(file)) {
            Log.d(TAG, "Server file already verified at $serverPath")
            return true
        }
        Log.d(TAG, "Extracting server file to $serverPath")
        return extractFromAssets(context, file)
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFromAssets(context: Context, destFile: File): Boolean {
        return try {
            destFile.parentFile?.mkdirs()
            context.assets.open(ScrcpyConfig.SCRCPY_SERVER_ASSET_NAME).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = input.read(buffer)
                    while (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        bytesRead = input.read(buffer)
                    }
                }
            }
            destFile.setReadable(true, false)
            val verified = verifyServerHash(destFile)
            Log.i(TAG, "Server extraction result: verified=$verified at ${destFile.path}")
            verified
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract server file from assets", e)
            false
        }
    }

    fun verifyServerHash(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val matches = actualHash.equals(ScrcpyConfig.EXPECTED_SERVER_SHA256, ignoreCase = true)
            if (!matches) {
                Log.w(TAG, "Server hash mismatch: expected=${ScrcpyConfig.EXPECTED_SERVER_SHA256}, actual=$actualHash")
            }
            matches
        } catch (e: Exception) {
            Log.e(TAG, "Hash verification error", e)
            false
        }
    }
}
