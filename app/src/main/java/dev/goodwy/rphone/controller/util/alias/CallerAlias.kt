package dev.goodwy.rphone.controller.util.alias

import org.json.JSONObject
import java.util.UUID

data class CallerAlias(
    val id: String = UUID.randomUUID().toString(),
    val rawNumber: String,
    val cleanNumber: String = cleanPhoneNumber(rawNumber),
    val last7Digits: String = extractLast7(cleanNumber),
    val aliasName: String,
    val hideNumber: Boolean = false,
    val isEnabled: Boolean = true
) {
    companion object {
        fun cleanPhoneNumber(number: String): String =
            number.filter { it.isDigit() }

        fun extractLast7(cleaned: String): String =
            if (cleaned.length >= 7) cleaned.takeLast(7) else cleaned

        fun fromJson(jsonStr: String): CallerAlias? {
            return try {
                val obj = JSONObject(jsonStr)
                val raw = obj.optString("rawNumber", "")
                val clean = obj.optString("cleanNumber", cleanPhoneNumber(raw))
                CallerAlias(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    rawNumber = raw,
                    cleanNumber = clean,
                    last7Digits = obj.optString("last7Digits", extractLast7(clean)),
                    aliasName = obj.optString("aliasName", ""),
                    hideNumber = obj.optBoolean("hideNumber", false),
                    isEnabled = obj.optBoolean("isEnabled", true)
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("rawNumber", rawNumber)
        obj.put("cleanNumber", cleanNumber)
        obj.put("last7Digits", last7Digits)
        obj.put("aliasName", aliasName)
        obj.put("hideNumber", hideNumber)
        obj.put("isEnabled", isEnabled)
        return obj.toString()
    }
}
