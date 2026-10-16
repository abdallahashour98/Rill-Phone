package dev.goodwy.rphone.controller.util.shizuku

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import dev.goodwy.rphone.IShellService
import kotlin.system.exitProcess

@Keep
class ShellService : IShellService.Stub {
    private val audioPipeline by lazy { ShellAudioPipeline() }

    @Keep
    constructor() : this(null)

    @Keep
    constructor(context: Context?) {
        Log.i("ShellService", "ShellService started in UID=${android.os.Process.myUid()}")
    }

    override fun startRecording(
        audioSource: String,
        audioCodec: String,
        audioBitRate: Int,
        serverPath: String,
        isDebuggingModeEnabled: Boolean
    ): ParcelFileDescriptor? {
        return audioPipeline.startCapture(audioSource, audioCodec, audioBitRate, serverPath, isDebuggingModeEnabled)
    }

    override fun stopRecording() {
        audioPipeline.stopCapture()
    }

    override fun destroy() {
        Log.i("ShellService", "ShellService.destroy() called - terminating shell process")
        stopRecording()
        exitProcess(0)
    }
}
