package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true

fun Call?.isHD(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO) == true

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
