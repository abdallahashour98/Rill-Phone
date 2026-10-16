package dev.goodwy.rphone.controller.util.shizuku

import android.media.MediaFormat
import android.media.MediaMuxer

enum class ScrcpyAudioCodec(
    val cliKey: String,
    val codecFourCC: Int,
    val defaultBitRate: Int,
    val outputFormat: Int,
    val mimeType: String,
    val containerExtension: String
) {
    OPUS(
        cliKey             = "opus",
        codecFourCC        = 0x6F707573,
        defaultBitRate     = 64000,
        outputFormat       = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
        mimeType           = MediaFormat.MIMETYPE_AUDIO_OPUS,
        containerExtension = ".ogg"
    ),
    AAC(
        cliKey             = "aac",
        codecFourCC        = 0x00616163,
        defaultBitRate     = 96000,
        outputFormat       = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        mimeType           = MediaFormat.MIMETYPE_AUDIO_AAC,
        containerExtension = ".m4a"
    );

    companion object {
        fun fromKey(key: String): ScrcpyAudioCodec =
            entries.firstOrNull { it.cliKey == key } ?: AAC

        fun fromFourCC(fourCC: Int): ScrcpyAudioCodec =
            entries.firstOrNull { it.codecFourCC == fourCC } ?: AAC
    }
}
