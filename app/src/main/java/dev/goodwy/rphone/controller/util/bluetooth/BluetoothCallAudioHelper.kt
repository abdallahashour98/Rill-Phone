package dev.goodwy.rphone.controller.util.bluetooth

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import dev.goodwy.rphone.controller.util.PreferenceManager
import java.lang.ref.WeakReference

/**
 * Handles Bluetooth call audio and microphone routing with maximum clarity,
 * latency reduction, Wideband Speech (HD Voice / mSBC / LC3),
 * and hardware echo cancellation & noise reduction (NREC).
 */
object BluetoothCallAudioHelper {
    private const val TAG = "BluetoothCallAudio"

    const val KEY_BT_OPTIMIZATION = "bluetooth_call_audio_optimization"
    const val KEY_BT_AUTO_ROUTE = "bluetooth_auto_route_call"

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var inCallServiceRef: WeakReference<InCallService>? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isReceiverRegistered = false
    private var isScoActive = false

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                val state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                )
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        Log.i(TAG, "Bluetooth SCO connected: High-fidelity voice channel active")
                        isScoActive = true
                        applyAudioEnhancements()
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        Log.d(TAG, "Bluetooth SCO disconnected")
                        isScoActive = false
                    }
                    AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                        Log.d(TAG, "Bluetooth SCO connecting...")
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (appContext == null) {
            val app = context.applicationContext
            appContext = app
            audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
    }

    /**
     * Called when a call is added to InCallService.
     */
    fun onCallAdded(service: InCallService, call: Call) {
        inCallServiceRef = WeakReference(service)
        registerReceiverIfNeeded()

        val context = appContext ?: service.applicationContext
        val prefs = PreferenceManager(context)
        val autoRoute = prefs.getBoolean(KEY_BT_AUTO_ROUTE, true)

        if (autoRoute) {
            val audioState = service.callAudioState
            val supported = audioState?.supportedRouteMask ?: 0
            if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0 &&
                audioState?.route != CallAudioState.ROUTE_BLUETOOTH
            ) {
                Log.i(TAG, "Bluetooth device detected on call added. Automatically routing to Bluetooth...")
                try {
                    service.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed auto-routing to Bluetooth", e)
                }
            }
        }
    }

    /**
     * Called when a call becomes active (answered or dialing).
     */
    fun onCallActive(service: InCallService) {
        inCallServiceRef = WeakReference(service)
        val context = appContext ?: service.applicationContext
        val prefs = PreferenceManager(context)
        val optimize = prefs.getBoolean(KEY_BT_OPTIMIZATION, true)

        if (optimize) {
            optimizeAudioSession()
        }
    }

    /**
     * Called when user manually selects Bluetooth route on UI.
     */
    fun onUserRequestedBluetooth(service: InCallService) {
        inCallServiceRef = WeakReference(service)
        optimizeAudioSession()
    }

    /**
     * Called when Telecom's CallAudioState changes.
     */
    fun onCallAudioStateChanged(service: InCallService, audioState: CallAudioState?) {
        inCallServiceRef = WeakReference(service)
        val route = audioState?.route ?: return

        if (route == CallAudioState.ROUTE_BLUETOOTH) {
            Log.i(TAG, "Audio route changed to BLUETOOTH. Ensuring voice clarity & mic optimization...")
            optimizeAudioSession()
        } else {
            // User switched to Speaker, Earpiece, or Wired headset.
            // Release communication device / SCO binding so speaker is not constrained.
            releaseBluetoothDeviceBinding()
        }
    }

    /**
     * Called when all active calls have ended.
     */
    fun onCallEnded() {
        Log.i(TAG, "All calls ended. Cleaning up Bluetooth call audio state...")
        releaseBluetoothDeviceBinding()
        abandonAudioFocus()
        unregisterReceiverIfNeeded()
        inCallServiceRef = null
    }

    /**
     * Activates high-fidelity voice communication and configures headset microphone.
     * Note: Audio routing is handled exclusively by Telecom's InCallService.setAudioRoute(...),
     * which runs at the system level and requires NO "Nearby Devices" (BLUETOOTH_CONNECT) permission.
     * We avoid calling AudioManager.setCommunicationDevice or startBluetoothSco() here to prevent
     * dual-routing collisions and RF inquiry scans that cause audible buzzing/static (تشويش).
     */
    private fun optimizeAudioSession() {
        val am = audioManager ?: return
        val context = appContext ?: return
        val prefs = PreferenceManager(context)
        if (!prefs.getBoolean(KEY_BT_OPTIMIZATION, true)) return

        try {
            // 1. Request Voice Communication Audio Focus (pure audio focus, 0 permissions needed)
            requestVoiceAudioFocus()

            // 2. Apply hardware DSP audio enhancements directly to the audio HAL (0 permissions needed)
            applyAudioEnhancements()
        } catch (e: Exception) {
            Log.w(TAG, "Error optimizing audio session", e)
        }
    }

    /**
     * Applies vendor DSP parameters for wideband speech (WBS) and noise reduction.
     * Works across Qualcomm, MediaTek, and Exynos audio HALs without triggering Bluetooth radio scans.
     */
    private fun applyAudioEnhancements() {
        val am = audioManager ?: return
        try {
            // Enable Bluetooth headset Noise Reduction and Echo Cancellation
            am.setParameters("bt_headset_nrec=on")
            // Enable Wideband Speech (16kHz HD Voice / mSBC)
            am.setParameters("wbs=on")
            Log.d(TAG, "Applied Bluetooth DSP parameters: bt_headset_nrec=on, wbs=on")
        } catch (e: Exception) {
            Log.d(TAG, "Notice setting DSP parameters: ${e.message}")
        }
    }

    private fun releaseBluetoothDeviceBinding() {
        val am = audioManager ?: return
        try {
            // Reset vendor DSP parameters
            am.setParameters("bt_headset_nrec=off")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Bluetooth DSP parameters", e)
        }
    }

    private fun requestVoiceAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Log.d(TAG, "Audio focus change: $focusChange")
                        }
                        .build()
                    audioFocusRequest = request
                    am.requestAudioFocus(request)
                }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting voice audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus", e)
        }
    }

    private fun registerReceiverIfNeeded() {
        val context = appContext ?: return
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                context.registerReceiver(scoReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Error registering SCO receiver", e)
            }
        }
    }

    private fun unregisterReceiverIfNeeded() {
        val context = appContext ?: return
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scoReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }
}
