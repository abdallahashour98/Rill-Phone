package dev.goodwy.rphone.controller.util.alias

import android.content.Context
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

object CallerAliasManager {
    private const val KEY_CALLER_ALIASES = "caller_id_aliases_json"
    private const val KEY_FEATURE_ENABLED = "caller_id_aliases_enabled"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _aliases = MutableStateFlow<List<CallerAlias>>(emptyList())
    val aliases: StateFlow<List<CallerAlias>> = _aliases.asStateFlow()

    private val _isFeatureEnabled = MutableStateFlow(true)
    val isFeatureEnabled: StateFlow<Boolean> = _isFeatureEnabled.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val prefs = PreferenceManager(context)
        _isFeatureEnabled.value = prefs.getBoolean(KEY_FEATURE_ENABLED, true)

        val jsonStr = prefs.getString(KEY_CALLER_ALIASES, "[]") ?: "[]"
        _aliases.value = parseAliasesJson(jsonStr)
    }

    private fun parseAliasesJson(jsonStr: String): List<CallerAlias> {
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CallerAlias>()
            for (i in 0 until arr.length()) {
                CallerAlias.fromJson(arr.getString(i))?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAliases(context: Context, list: List<CallerAlias>) {
        _aliases.value = list
        scope.launch {
            try {
                val arr = JSONArray()
                for (alias in list) {
                    arr.put(alias.toJson())
                }
                val prefs = PreferenceManager(context)
                prefs.setString(KEY_CALLER_ALIASES, arr.toString())
            } catch (_: Exception) {}
        }
    }

    fun setFeatureEnabled(context: Context, enabled: Boolean) {
        _isFeatureEnabled.value = enabled
        scope.launch {
            val prefs = PreferenceManager(context)
            prefs.setBoolean(KEY_FEATURE_ENABLED, enabled)
        }
    }

    fun addOrUpdateAlias(context: Context, alias: CallerAlias) {
        val current = _aliases.value.toMutableList()
        val index = current.indexOfFirst { it.id == alias.id }
        if (index >= 0) {
            current[index] = alias
        } else {
            // Also check if an entry with same clean number exists to avoid duplicates
            val existingNumIndex = current.indexOfFirst { it.cleanNumber == alias.cleanNumber }
            if (existingNumIndex >= 0) {
                current[existingNumIndex] = alias
            } else {
                current.add(0, alias)
            }
        }
        saveAliases(context, current)
    }

    fun deleteAlias(context: Context, id: String) {
        val current = _aliases.value.filterNot { it.id == id }
        saveAliases(context, current)
    }

    fun toggleAliasEnabled(context: Context, id: String, enabled: Boolean) {
        val current = _aliases.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveAliases(context, current)
    }

    /**
     * Resolves an alias for an incoming or outgoing number.
     * Matches exact clean phone number or last 7 digits for international format resilience.
     */
    fun getAliasForNumber(rawNumber: String): CallerAlias? {
        if (!_isFeatureEnabled.value) return null
        val clean = CallerAlias.cleanPhoneNumber(rawNumber)
        if (clean.isBlank()) return null
        val last7 = CallerAlias.extractLast7(clean)

        return _aliases.value.firstOrNull { alias ->
            alias.isEnabled && (
                alias.cleanNumber == clean ||
                (last7.length >= 7 && alias.last7Digits == last7)
            )
        }
    }

    /**
     * Resolves an alias for a Contact by checking all its phone numbers and phone details.
     */
    fun getAliasForContact(contact: dev.goodwy.rphone.modal.data.Contact): CallerAlias? {
        if (!_isFeatureEnabled.value) return null
        for (num in contact.phoneNumbers) {
            val alias = getAliasForNumber(num)
            if (alias != null) return alias
        }
        for (detail in contact.phoneDetails) {
            val alias = getAliasForNumber(detail.number)
            if (alias != null) return alias
        }
        return null
    }
}

