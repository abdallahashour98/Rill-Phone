package dev.goodwy.rphone.controller.util.shizuku

import android.content.Context
import android.os.Build
import java.io.File
import java.security.SecureRandom

object ScrcpyConfig {
    const val SCRCPY_VERSION: String = "4.0"
    const val EXPECTED_SERVER_SHA256: String = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a"
    const val SCRCPY_SERVER_ASSET_NAME: String = "scrcpy-server"
    const val SERVER_MAIN_CLASS: String = "com.genymobile.scrcpy.Server"
    const val SERVER_SOCKET_NAME_PREFIX: String = "scrcpy_"
    const val AUDIO_SAMPLE_RATE: Int = 48000
    const val AUDIO_CHANNELS: Int = 2

    fun getServerPath(context: Context): String {
        val folder = context.getExternalFilesDir(null)
            ?: context.externalCacheDir
            ?: File("/sdcard/Android/data/${context.packageName}/files")
        return File(folder, "scrcpy-${SCRCPY_VERSION}-server.jar").absolutePath
    }

    fun buildServerArgs(
        socketName: String,
        audioSource: ScrcpyAudioSource,
        audioCodec: ScrcpyAudioCodec,
        audioBitRate: Int
    ): List<String> {
        val args = mutableListOf(
            SCRCPY_VERSION,
            "log_level=info",
            "video=false",
            "audio=true",
            "control=false",
            "tunnel_forward=false",
            "send_dummy_byte=false",
            "scid=$socketName",
            "audio_source=${audioSource.cliKey}",
            "audio_codec=${audioCodec.cliKey}",
            "send_device_meta=false",
            "send_frame_meta=true",
            "send_stream_meta=true"
        )
        if (audioBitRate > 0) {
            args.add("audio_bit_rate=$audioBitRate")
        }
        if (audioSource == ScrcpyAudioSource.PLAYBACK && Build.VERSION.SDK_INT >= 33) {
            args.add("audio_dup=true")
        }
        return args
    }

    fun getRandomSocketName(): String {
        return SecureRandom().nextInt(Int.MAX_VALUE).toString(16).padStart(8, '0')
    }
}
