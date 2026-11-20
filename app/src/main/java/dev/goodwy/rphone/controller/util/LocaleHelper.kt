package dev.goodwy.rphone.controller.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    const val KEY_APP_LANGUAGE = "app_language"
    const val LANGUAGE_AUTO = "auto"
    const val LANGUAGE_ARABIC = "ar"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_GERMAN = "de"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_SPANISH = "es"

    data class LanguageOption(
        val code: String,
        val displayName: String,
        val nativeName: String,
        val flag: String
    )

    val supportedLanguages = listOf(
        LanguageOption(LANGUAGE_AUTO, "System Default", "افتراضي النظام", "🌐"),
        LanguageOption(LANGUAGE_ARABIC, "Arabic", "العربية", "🇪🇬"),
        LanguageOption(LANGUAGE_ENGLISH, "English", "English", "🇬🇧"),
        LanguageOption(LANGUAGE_RUSSIAN, "Russian", "Русский", "🇷🇺"),
        LanguageOption(LANGUAGE_GERMAN, "German", "Deutsch", "🇩🇪"),
        LanguageOption(LANGUAGE_FRENCH, "French", "Français", "🇫🇷"),
        LanguageOption(LANGUAGE_SPANISH, "Spanish", "Español", "🇪🇸"),
    )

    fun getSelectedLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                val tag = locales[0]?.language
                if (!tag.isNullOrBlank()) {
                    return tag
                }
            }
        }
        val sp = context.getSharedPreferences("rill_language_prefs", Context.MODE_PRIVATE)
        return sp.getString(KEY_APP_LANGUAGE, LANGUAGE_AUTO) ?: LANGUAGE_AUTO
    }

    fun getSelectedLanguageLabel(context: Context): String {
        val code = getSelectedLanguage(context)
        val option = supportedLanguages.find { it.code == code }
        return if (option != null) {
            "${option.flag} ${option.nativeName}"
        } else {
            "🌐 افتراضي النظام"
        }
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun setLanguage(context: Context, langCode: String) {
        val sp = context.getSharedPreferences("rill_language_prefs", Context.MODE_PRIVATE)
        sp.edit().putString(KEY_APP_LANGUAGE, langCode).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                val localeList = if (langCode == LANGUAGE_AUTO || langCode.isEmpty()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(langCode)
                }
                localeManager.applicationLocales = localeList
            }
        }
        findActivity(context)?.recreate()
    }

    fun wrapContext(context: Context): Context {
        val langCode = getSelectedLanguage(context)
        if (langCode == LANGUAGE_AUTO || langCode.isEmpty()) return context
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
