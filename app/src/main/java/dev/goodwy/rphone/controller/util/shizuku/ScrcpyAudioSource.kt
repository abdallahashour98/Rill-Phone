package dev.goodwy.rphone.controller.util.shizuku

enum class ScrcpyAudioSource(
    val cliKey: String,
    val title: String
) {
    VOICE_CALL("voice-call", "Voice Call (System 2-Way)"),
    VOICE_CALL_DOWNLINK("voice-call-downlink", "Voice Call (Downlink/Caller)"),
    VOICE_CALL_UPLINK("voice-call-uplink", "Voice Call (Uplink/Mic)"),
    VOICE_COMMUNICATION("mic-voice-communication", "Voice Communication (Mic with Echo Cancellation)"),
    MIC("mic", "Microphone"),
    PLAYBACK("playback", "Playback (Internal Audio)");

    companion object {
        fun fromKey(key: String): ScrcpyAudioSource =
            entries.firstOrNull { it.cliKey == key } ?: VOICE_CALL
    }
}
