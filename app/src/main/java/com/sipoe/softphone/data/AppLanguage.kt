package com.sipoe.softphone.data

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.sipoe.softphone.R
import java.util.Locale

enum class AppLanguage(
    val storageKey: String,
    val languageTag: String,
    @StringRes val labelRes: Int,
) {
    SYSTEM("system", "", R.string.settings_language_system),
    CHINESE("zh", "zh-CN", R.string.settings_language_zh),
    ENGLISH("en", "en", R.string.settings_language_en),
}

object AppLanguageStore {
    private const val PREFS = "sipoe_settings"
    private const val KEY_LANGUAGE = "app_language"

    fun get(context: Context): AppLanguage {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.storageKey == raw } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE, language.storageKey) }
    }
}

object LocaleSupport {

    fun setLanguage(context: Context, language: AppLanguage) {
        AppLanguageStore.set(context, language)
        (context.applicationContext as? Application)?.let { applyToApplication(it) }
    }

    fun wrap(base: Context): Context {
        val locales = appLocales(AppLanguageStore.get(base)) ?: return base
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocales(locales) },
        )
    }

    fun applyToApplication(application: Application) {
        val locales = appLocales(AppLanguageStore.get(application))
            ?: Resources.getSystem().configuration.locales
        if (locales.isEmpty) return
        Locale.setDefault(locales[0])
        @Suppress("DEPRECATION")
        application.resources.updateConfiguration(
            Configuration(application.resources.configuration).apply { setLocales(locales) },
            application.resources.displayMetrics,
        )
    }

    private fun appLocales(language: AppLanguage): LocaleList? =
        if (language == AppLanguage.SYSTEM) {
            null
        } else {
            LocaleList(Locale.forLanguageTag(language.languageTag))
        }
}
