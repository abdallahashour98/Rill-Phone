package dev.goodwy.rphone.controller.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.goodwy.rphone.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

data class ReleaseInfo(
    val tagName: String,
    val apkUrl: String?,
    val releaseNotes: String? = null,
    val publishedAt: String? = null
)

private fun parseReleaseJson(json: JSONObject): ReleaseInfo {
    val tag = json.optString("tag_name", "")
    val assets = json.optJSONArray("assets")
    var apkUrl: String? = null
    if (assets != null) {
        val currentFlavor = dev.goodwy.rphone.BuildConfig.FLAVOR.lowercase()
        var fallbackApkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "").lowercase()
            if (name.endsWith(".apk")) {
                val url = asset.optString("browser_download_url")
                if (fallbackApkUrl == null) fallbackApkUrl = url
                if (currentFlavor.isNotEmpty() && name.contains(currentFlavor)) {
                    apkUrl = url
                    break
                }
            }
        }
        if (apkUrl == null) apkUrl = fallbackApkUrl
    }
    val notes = json.optString("body", "").trim().ifBlank { null }
    val publishedAt = json.optString("published_at", "").ifBlank { null }
    return ReleaseInfo(
        tagName = tag.trimStart('v', 'V'),
        apkUrl = apkUrl,
        releaseNotes = notes,
        publishedAt = publishedAt,
    )
}

suspend fun fetchLatestRelease(apiUrl: String): ReleaseInfo? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (connection.responseCode != 200) return@withContext null
        val body = connection.inputStream.bufferedReader().readText()
        parseReleaseJson(JSONObject(body))
    } catch (_: Exception) { null }
}

/**
 * Fetches the release whose tag matches [version] (with or without a leading "v")
 * from the repo's full releases list. Used to show release notes for the
 * currently-installed version, so the user can compare it against the latest.
 */
suspend fun fetchReleaseForVersion(apiListUrl: String, version: String): ReleaseInfo? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(apiListUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (connection.responseCode != 200) return@withContext null
        val body = connection.inputStream.bufferedReader().readText()
        val array = org.json.JSONArray(body)
        val target = version.trimStart('v', 'V')
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val tag = obj.optString("tag_name", "").trimStart('v', 'V')
            if (tag == target) return@withContext parseReleaseJson(obj)
        }
        null
    } catch (_: Exception) { null }
}

fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(v: String) = v.split(".").mapNotNull { part ->
        val match = Regex("^\\d+").find(part)
        match?.value?.toIntOrNull()
    }
    val l = parts(latest); val c = parts(current)
    val len = maxOf(l.size, c.size)
    for (i in 0 until len) {
        val lp = l.getOrElse(i) { 0 }; val cp = c.getOrElse(i) { 0 }
        if (lp > cp) return true; if (lp < cp) return false
    }
    return false
}

private const val APK_FILE_NAME = "RPhone_update.apk"

/** App external downloads folder for APK updates. */
fun getApkDestinationFile(context: Context): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
    return File(dir, APK_FILE_NAME)
}

/**
 * Enqueue an APK download to the app's external downloads directory.
 * Returns the DownloadManager download ID, or null on failure.
 * Progress should be polled via DownloadManager.Query from the caller.
 */
fun enqueueApkDownload(context: Context, apkUrl: String): Long? {
    val appContext = context.applicationContext
    return try {
        val file = getApkDestinationFile(appContext)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(apkUrl.toUri()).apply {
            setTitle(APK_FILE_NAME)
            setDescription(appContext.getString(R.string.downloading_update))
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(file))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (_: Exception) { null }
}

/**
 * Installs the downloaded APK by directly launching the Android package installer.
 * If permission to install unknown apps is not granted, it redirects
 * the user to the app's unknown sources settings screen.
 */
fun installDownloadedApk(context: Context, downloadId: Long? = null) {
    try {
        val appContext = context.applicationContext
        val file = getApkDestinationFile(appContext)

        // 1. Unknown app sources permission check
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, context.getString(R.string.allow_install_unknown_apps_then_retry), Toast.LENGTH_LONG).show()
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
            return
        }

        // 2. Resolve content URI
        var contentUri: Uri? = null
        if (file.exists() && file.length() > 100_000L) { // Ensure file is valid (>100KB)
            contentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } catch (_: Exception) { null }
        }

        if (contentUri == null && downloadId != null) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            contentUri = dm?.getUriForDownloadedFile(downloadId)
        }

        if (contentUri != null) {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } else {
            Toast.makeText(context, context.getString(R.string.open_downloads_folder_manually), Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.open_downloads_folder_manually), Toast.LENGTH_SHORT).show()
    }
}

