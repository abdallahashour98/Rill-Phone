package dev.goodwy.rphone.controller

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.ViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.view.screen.DialpadDraftHolder

sealed class NavigationTarget {
    object Recents : NavigationTarget()
    data class Dialpad(val number: String) : NavigationTarget()
    data class ContactDetails(val contactId: String) : NavigationTarget()
    data class ContactEdit(val contactId: String? = null, val initialName: String? = null, val initialPhone: String? = null) : NavigationTarget()
}

class MainViewModel(private val prefs: PreferenceManager) : ViewModel() {

    fun getNavigationTarget(intent: Intent, context: Context): NavigationTarget? {
        val data = intent.data
        val action = intent.action ?: return null

        return when (action) {
            "dev.goodwy.rphone.ACTION_VIEW_RECENTS" -> NavigationTarget.Recents
            Intent.ACTION_VIEW -> {
                val mimeType = intent.type
                if (mimeType == "vnd.android.cursor.dir/calls" ||
                    data?.toString()?.contains("call_log") == true ||
                    data?.toString()?.contains("calls") == true) {
                    NavigationTarget.Recents
                } else {
                    val number = extractPhoneNumber(intent, context)
                    if (number != null) {
                        DialpadDraftHolder.pendingNumber = number
                        NavigationTarget.Dialpad(number)
                    } else if (data?.toString()?.contains("contacts") == true ||
                        data?.toString()?.contains("com.android.contacts") == true ||
                        intent.hasExtra("contact_id")) {
                        val id = data?.lastPathSegment ?: intent.getStringExtra("contact_id")
                        if (id != null) {
                            NavigationTarget.ContactDetails(id)
                        } else null
                    } else null
                }
            }
            Intent.ACTION_DIAL -> {
                val number = extractPhoneNumber(intent, context) ?: ""
                DialpadDraftHolder.pendingNumber = number
                NavigationTarget.Dialpad(number)
            }
            Intent.ACTION_CALL -> {
                val number = extractPhoneNumber(intent, context)
                if (number != null) {
                    DialpadDraftHolder.pendingNumber = number
                    if (isAlreadyDefaultDialer(context)) {
                        makeCall(context, number = number)
                        null
                    } else {
                        NavigationTarget.Dialpad(number)
                    }
                } else {
                    NavigationTarget.Dialpad("")
                }
            }
            "android.intent.action.CALL_BUTTON" -> {
                NavigationTarget.Dialpad("")
            }
            Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                NavigationTarget.ContactEdit(initialName = name, initialPhone = phone)
            }
            Intent.ACTION_EDIT -> {
                val id = data?.lastPathSegment
                if (id != null) {
                    NavigationTarget.ContactEdit(contactId = id)
                } else null
            }
            else -> {
                val number = extractPhoneNumber(intent, context)
                if (number != null) {
                    DialpadDraftHolder.pendingNumber = number
                    NavigationTarget.Dialpad(number)
                } else null
            }
        }
    }

    private fun extractPhoneNumber(intent: Intent, context: Context): String? {
        // 1. Try standard Android PhoneNumberUtils
        try {
            val num = PhoneNumberUtils.getNumberFromIntent(intent, context)
            if (!num.isNullOrBlank()) {
                val sanitized = sanitizePhoneNumber(num)
                if (sanitized.isNotBlank()) return sanitized
            }
        } catch (_: Exception) {}

        // 2. Try intent.data
        val data = intent.data
        if (data != null) {
            val scheme = data.scheme?.lowercase()
            if (scheme == "tel" || scheme == "callto" || scheme == "sip" || scheme == "sms" || scheme == "smsto") {
                val part = data.schemeSpecificPart
                if (!part.isNullOrBlank()) {
                    val sanitized = sanitizePhoneNumber(part)
                    if (sanitized.isNotBlank()) return sanitized
                }
            }
            val dataStr = data.toString()
            if (dataStr.contains(':')) {
                val afterColon = dataStr.substringAfter(':')
                val sanitized = sanitizePhoneNumber(afterColon)
                if (sanitized.isNotBlank()) return sanitized
            }
        }

        // 3. Try common extras passed by third-party apps (inDrive, Uber, etc.)
        val extraKeys = listOf(
            Intent.EXTRA_PHONE_NUMBER,
            "android.intent.extra.PHONE_NUMBER",
            "phone",
            "phoneNumber",
            "number",
            "tel",
            "contact_number",
            "address"
        )
        for (key in extraKeys) {
            try {
                val extra = intent.getStringExtra(key)
                if (!extra.isNullOrBlank()) {
                    val sanitized = sanitizePhoneNumber(extra)
                    if (sanitized.isNotBlank()) return sanitized
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun sanitizePhoneNumber(raw: String): String {
        val decoded = try {
            Uri.decode(raw)
        } catch (_: Exception) {
            raw
        }
        val withoutQuery = decoded.substringBefore('?').substringBefore('#')
        val trimmed = withoutQuery.trimStart('/', ' ').trim()
        val filtered = buildString {
            for (ch in trimmed) {
                if (ch.isDigit() || ch == '+' || ch == '*' || ch == '#' || ch == ',' || ch == ';') {
                    append(ch)
                }
            }
        }
        return filtered
    }
}