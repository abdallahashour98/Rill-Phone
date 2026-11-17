package dev.goodwy.rphone.controller.util.bluetooth

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import dev.goodwy.rphone.controller.util.PreferenceManager

/** Telecom owns cellular call audio, including the headset microphone and its noise processing. */
object BluetoothCallAudioHelper {
    private const val TAG = "BluetoothCallAudio"

    const val KEY_BT_AUTO_ROUTE = "bluetooth_auto_route_call"

    fun onCallAdded(service: InCallService, call: Call) {
        val prefs = PreferenceManager(service.applicationContext)
        if (!prefs.getBoolean(KEY_BT_AUTO_ROUTE, true)) return

        // A second call must keep the route the user chose for the existing call.
        if (service.calls.any { it !== call && it.state != Call.STATE_DISCONNECTED }) return

        val audioState = service.callAudioState ?: return
        if (audioState.route == CallAudioState.ROUTE_BLUETOOTH ||
            audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH == 0
        ) return

        try {
            service.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to route call to Bluetooth", e)
        }
    }
}
