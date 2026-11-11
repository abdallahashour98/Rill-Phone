package dev.goodwy.rphone.controller.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

fun isAlreadyDefaultDialer(context: Context): Boolean {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
    val isRoleHeld = try {
        roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } catch (_: Exception) {
        false
    }
    val isTelecomDefault = try {
        telecomManager?.defaultDialerPackage == context.packageName
    } catch (_: Exception) {
        false
    }
    return isRoleHeld || isTelecomDefault
}

fun getDefaultDialerIntent(context: Context): Intent {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
        try {
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } catch (_: Exception) {}
    }
    @Suppress("DEPRECATION")
    return Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
        putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }
}

fun Context.isPackageInstalled(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    return try {
        packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (_: Exception) {
        false
    }
}