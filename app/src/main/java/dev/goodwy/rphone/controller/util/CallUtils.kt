package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

import android.media.ToneGenerator
import android.telecom.DisconnectCause

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true

fun Call?.isHD(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO) == true

/**
 * Checks if the DisconnectCause represents a Busy state (line busy / user busy).
 * Inspects standard DisconnectCause.BUSY, ToneGenerator busy tones, reason codes,
 * and carrier description/label strings in both English and Arabic.
 */
fun DisconnectCause?.isBusy(): Boolean {
    if (this == null) return false
    if (code == DisconnectCause.BUSY) return true
    try {
        if (tone == ToneGenerator.TONE_SUP_BUSY || tone == ToneGenerator.TONE_SUP_CONGESTION) return true
    } catch (_: Exception) {}

    val r = reason.orEmpty().lowercase()
    val d = description?.toString().orEmpty().lowercase()
    val l = label?.toString().orEmpty().lowercase()

    return r.contains("busy") || r.contains("486") || r.contains("600") || r.contains("مشغول") ||
           d.contains("busy") || d.contains("486") || d.contains("مشغول") ||
           l.contains("busy") || l.contains("مشغول")
}

/**
 * Checks if the DisconnectCause represents a Rejected / Declined state.
 * Inspects standard DisconnectCause.REJECTED, reason codes, and carrier descriptions.
 */
fun DisconnectCause?.isRejected(): Boolean {
    if (this == null) return false
    if (code == DisconnectCause.REJECTED) return true

    val r = reason.orEmpty().lowercase()
    val d = description?.toString().orEmpty().lowercase()
    val l = label?.toString().orEmpty().lowercase()

    return r.contains("reject") || r.contains("decline") || r.contains("603") || r.contains("رفض") ||
           d.contains("reject") || d.contains("decline") || d.contains("رفض") ||
           l.contains("reject") || l.contains("decline") || l.contains("رفض")
}

fun Call?.isBusy(): Boolean = this?.details?.disconnectCause.isBusy()

fun Call?.isRejected(): Boolean = this?.details?.disconnectCause.isRejected()

/**
 * Accurately detects whether a Bundle contains network supplementary service notification
 * or carrier call-waiting flags indicating that the remote called party is on another call.
 */
fun isCallWaitingBundle(extras: android.os.Bundle?): Boolean {
    if (extras == null) return false
    try {
        // Notification code: 3 = CODE_1_CALL_IS_WAITING (3GPP TS 24.083 / TS 27.007)
        val codeKeys = listOf(
            "android.telephony.extra.NOTIFICATION_CODE",
            "EXTRA_NOTIFICATION_CODE",
            "notification_code",
            "notificationCode",
            "extra_notification_code",
            "code",
            "NotificationCode"
        )
        for (key in codeKeys) {
            if (extras.containsKey(key)) {
                val value = extras.get(key)
                if (value == 3 || value == "3") return true
            }
        }

        // Boolean call-waiting flags across AOSP and OEM chipsets (Qualcomm, Samsung, MediaTek)
        val boolKeys = listOf(
            "android.telecom.extra.CALL_WAITING",
            "call_waiting",
            "is_call_waiting",
            "isCallWaiting",
            "remote_call_waiting",
            "is_remote_call_waiting",
            "RemoteCallWaiting",
            "CallWaiting",
            "org.codeaurora.extra.CALL_WAITING",
            "com.samsung.telecom.extra.CALL_WAITING",
            "mediatek.telecom.extra.CALL_WAITING",
            "ims_call_waiting"
        )
        for (key in boolKeys) {
            if (extras.containsKey(key)) {
                val value = extras.get(key)
                if (value == true || value == 1 || value == "true" || value == "1") return true
            }
        }

        // Notification message text matching "waiting" or Arabic "الانتظار" / "أخرى"
        val msgKeys = listOf(
            "android.telephony.extra.NOTIFICATION_MESSAGE",
            "EXTRA_NOTIFICATION_MESSAGE",
            "message",
            "description"
        )
        for (key in msgKeys) {
            val msg = extras.getCharSequence(key)?.toString()?.lowercase()
            if (msg != null && (msg.contains("waiting") || msg.contains("الانتظار") || msg.contains("مكالمة أخرى") || msg.contains("another call"))) {
                return true
            }
        }
    } catch (_: Exception) {}
    return false
}

/**
 * Checks whether this Call has received indication that the called party is on another call (Call Waiting).
 */
fun Call?.isRemoteCallWaiting(event: String? = null, extras: android.os.Bundle? = null): Boolean {
    if (this == null) return false

    if (event != null) {
        val ev = event.lowercase()
        if (ev.contains("supplementary_service") ||
            ev.contains("call_waiting") ||
            ev.contains("call_is_waiting")
        ) {
            if (extras == null || isCallWaitingBundle(extras)) return true
        }
    }

    if (isCallWaitingBundle(extras)) return true

    if (isCallWaitingBundle(details?.extras)) return true

    return false
}

/**
 * Accurately detects whether this call is a Wi-Fi Calling (VoWiFi) call.
 * Uses a multi-tiered approach:
 * 1. Native Telecom Call property (Call.Details.PROPERTY_WIFI)
 * 2. Vendor/carrier call extras (Qualcomm, MediaTek, Samsung, Google, Xiaomi)
 * 3. Network type IWLAN (Interworking Wireless LAN - 3GPP standard for VoWiFi, value 18)
 * 4. Active Wi-Fi connection + TelephonyManager.isWifiCallingAvailable (Android 11+)
 * 5. Active Wi-Fi connection in Airplane Mode
 */
fun Call?.isWifi(context: Context? = null): Boolean {
    if (this == null) return false

    // 1. Direct Telecom Call property
    if (details?.hasProperty(Call.Details.PROPERTY_WIFI) == true) {
        return true
    }

    // 2. Vendor and Carrier extras on the Call object
    details?.extras?.let { extras ->
        try {
            if (extras.getBoolean("android.telecom.extra.CALL_WIFI", false)) return true
            if (extras.getBoolean("call_is_wifi", false)) return true
            if (extras.getBoolean("org.codeaurora.extra.CALL_IS_WIFI", false)) return true
            if (extras.getBoolean("is_vowifi", false)) return true
            if (extras.getBoolean("isWifi", false)) return true
            if (extras.getBoolean("CallTechnologyIsWifi", false)) return true

            // Network type 18 is TelephonyManager.NETWORK_TYPE_IWLAN
            val netTypeKeys = listOf(
                "android.telecom.extra.CALL_NETWORK_TYPE",
                "call_network_type",
                "extra_call_network_type",
                "network_type"
            )
            for (key in netTypeKeys) {
                if (extras.containsKey(key)) {
                    val type = extras.get(key)
                    if (type is Int && type == 18) return true
                    if (type is String && (type == "18" || type.contains("IWLAN", ignoreCase = true) || type.contains("WIFI", ignoreCase = true))) return true
                }
            }

            val tech = extras.getString("CallTechnology")
            if (tech != null && (tech.contains("WIFI", ignoreCase = true) || tech.contains("IWLAN", ignoreCase = true))) {
                return true
            }
        } catch (_: Exception) {}
    }

    // 3. System Telephony and Connectivity inspection
    if (context != null) {
        try {
            if (isWifiConnected(context)) {
                // If Airplane mode is ON and Wi-Fi is connected, any cellular-style call is guaranteed VoWiFi
                if (isAirplaneModeOn(context)) {
                    return true
                }

                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null) {
                    val callTm = getTelephonyManagerForCall(context, telephonyManager, this) ?: telephonyManager

                    // Voice network type: 18 = NETWORK_TYPE_IWLAN
                    val voiceNetworkType = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                            callTm.voiceNetworkType
                        } else {
                            @Suppress("DEPRECATION")
                            callTm.networkType
                        }
                    } catch (_: Exception) { 0 }

                    if (voiceNetworkType == TelephonyManager.NETWORK_TYPE_IWLAN || voiceNetworkType == 18) {
                        return true
                    }

                    // Data network type: 18 = NETWORK_TYPE_IWLAN
                    val dataNetworkType = try {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                            callTm.dataNetworkType
                        } else 0
                    } catch (_: Exception) { 0 }

                    if (dataNetworkType == TelephonyManager.NETWORK_TYPE_IWLAN || dataNetworkType == 18) {
                        return true
                    }

                    // Check isWifiCallingAvailable via reflection (public/hidden on various OEM builds)
                    try {
                        val method = callTm.javaClass.getMethod("isWifiCallingAvailable")
                        if (method.invoke(callTm) as? Boolean == true) {
                            return true
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    return false
}

private fun getTelephonyManagerForCall(context: Context, baseTm: TelephonyManager, call: Call): TelephonyManager? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
    return try {
        val handle = call.details?.accountHandle ?: return null
        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val subList = subManager.activeSubscriptionInfoList
            val matchingSub = subList?.firstOrNull { it.iccId == handle.id || it.subscriptionId.toString() == handle.id }
            if (matchingSub != null) {
                baseTm.createForSubscriptionId(matchingSub.subscriptionId)
            } else null
        } else null
    } catch (_: Exception) { null }
}
