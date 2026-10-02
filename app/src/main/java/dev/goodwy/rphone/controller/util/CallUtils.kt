package dev.goodwy.rphone.controller.util

import android.telecom.Call

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true

fun Call?.isHD(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO) == true

fun Call?.isWifi(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_WIFI) == true