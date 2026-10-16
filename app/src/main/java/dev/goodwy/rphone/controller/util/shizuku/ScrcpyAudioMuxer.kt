package dev.goodwy.rphone.controller.util.shizuku

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.Closeable
import java.io.FileDescriptor
import java.nio.ByteBuffer

class ScrcpyAudioMuxer(
    private val outputFileDescriptor: FileDescriptor,
    private val outputDisplayPath: String
) : Closeable {
    private companion object {
        const val TAG = "ScrcpyAudioMuxer"
        const val GAP_THRESHOLD_NANOS = 400_000_000L
        const val GAP_SLACK_NANOS = 25_000_000L
    }

    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var firstPacketTimeNanos: Long = -1L
    private var lastWrittenPtsUs: Long = -1L
    private var lastPacketWallClockNanos: Long = -1L
    private var totalIgnoredGapNanos: Long = 0L

    fun initialize(codec: ScrcpyAudioCodec) {
        if (muxer != null) return
        Log.d(TAG, "Initializing MediaMuxer: codec=${codec.cliKey} path=$outputDisplayPath")
        muxer = MediaMuxer(outputFileDescriptor, codec.outputFormat)
    }

    fun writePacket(packet: ScrcpyClient.AudioPacket, codec: ScrcpyAudioCodec) {
        if (packet.isConfigPacket) {
            if (audioTrackIndex < 0) {
                addAudioTrack(configData = packet.data, codec = codec)
            }
            return
        }

        if (!isMuxerStarted || audioTrackIndex < 0) {
            return
        }

        val nowNanos = System.nanoTime()
        if (firstPacketTimeNanos == -1L) {
            firstPacketTimeNanos = nowNanos
            lastPacketWallClockNanos = nowNanos
        } else {
            val gapNanos = nowNanos - lastPacketWallClockNanos
            if (gapNanos > GAP_THRESHOLD_NANOS) {
                val ignoredNanos = gapNanos - GAP_SLACK_NANOS
                totalIgnoredGapNanos += ignoredNanos
            }
            lastPacketWallClockNanos = nowNanos
        }

        val wallClockPtsUs = (nowNanos - firstPacketTimeNanos - totalIgnoredGapNanos) / 1000L
        val normalizedPtsUs = if (wallClockPtsUs > lastWrittenPtsUs) wallClockPtsUs else lastWrittenPtsUs + 1L

        val bufferInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = packet.data.size
            presentationTimeUs = normalizedPtsUs
        }
        lastWrittenPtsUs = normalizedPtsUs

        try {
            muxer?.writeSampleData(audioTrackIndex, ByteBuffer.wrap(packet.data), bufferInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing sample data: ${e.message}")
        }
    }

    private fun addAudioTrack(configData: ByteArray, codec: ScrcpyAudioCodec) {
        val csdBytes = configData.takeIf { it.isNotEmpty() } ?: return
        val mediaFormat = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, codec.mimeType)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, ScrcpyConfig.AUDIO_SAMPLE_RATE)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, ScrcpyConfig.AUDIO_CHANNELS)
            setByteBuffer("csd-0", ByteBuffer.wrap(csdBytes))
        }

        try {
            audioTrackIndex = muxer?.addTrack(mediaFormat) ?: -1
            if (audioTrackIndex >= 0) {
                muxer?.start()
                isMuxerStarted = true
                Log.i(TAG, "Audio track added (track=$audioTrackIndex, mime=${codec.mimeType}), muxer started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaMuxer", e)
        }
    }

    override fun close() {
        if (isMuxerStarted) {
            Log.d(TAG, "Stopping MediaMuxer for $outputDisplayPath")
            runCatching { muxer?.stop() }.onFailure { e ->
                Log.w(TAG, "MediaMuxer stop warning: ${e.message}")
            }
        }
        runCatching { muxer?.release() }
        muxer = null
        isMuxerStarted = false
        audioTrackIndex = -1
        firstPacketTimeNanos = -1L
        lastWrittenPtsUs = -1L
        lastPacketWallClockNanos = -1L
        totalIgnoredGapNanos = 0L
    }
}
