package com.example.brainreels.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class WidgetPosition(val label: String) {
    TOP_CENTER("Top center"), TOP_LEFT("Top left"), TOP_RIGHT("Top right"), CUSTOM("Custom (where you dragged it)")
}

data class AppSettings(
    val detectionEnabled: Boolean = true,
    val widgetEnabled: Boolean = true,
    val instagramEnabled: Boolean = true,
    val facebookEnabled: Boolean = true,
    val facebookLiteEnabled: Boolean = true,
    val minViewMs: Int = 2000,
    val policy: ConfidencePolicy = ConfidencePolicy.HIGH_ONLY,
    val widgetPosition: WidgetPosition = WidgetPosition.TOP_CENTER,
    /** Fractions of the screen (0..1) used when [widgetPosition] is CUSTOM; survives rotation. */
    val customXFrac: Float = 0.5f,
    val customYFrac: Float = 0.05f,
    val animations: Boolean = true,
    val lightTheme: Boolean = false,
    val onboardingDone: Boolean = false
) {
    fun isEnabled(app: SourceApp): Boolean = when (app) {
        SourceApp.INSTAGRAM -> instagramEnabled
        SourceApp.FACEBOOK -> facebookEnabled
        SourceApp.FACEBOOK_LITE -> facebookLiteEnabled
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brainreels_settings")

object SettingsKeys {
    val DETECTION = booleanPreferencesKey("detection_enabled")
    val WIDGET = booleanPreferencesKey("widget_enabled")
    val INSTAGRAM = booleanPreferencesKey("instagram_enabled")
    val FACEBOOK = booleanPreferencesKey("facebook_enabled")
    val FACEBOOK_LITE = booleanPreferencesKey("facebook_lite_enabled")
    val MIN_VIEW_MS = intPreferencesKey("min_view_ms")
    val POLICY = stringPreferencesKey("confidence_policy")
    val POSITION = stringPreferencesKey("widget_position")
    val CUSTOM_X = floatPreferencesKey("widget_x_frac")
    val CUSTOM_Y = floatPreferencesKey("widget_y_frac")
    val ANIMATIONS = booleanPreferencesKey("animations")
    val LIGHT = booleanPreferencesKey("light_theme")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                detectionEnabled = p[SettingsKeys.DETECTION] ?: d.detectionEnabled,
                widgetEnabled = p[SettingsKeys.WIDGET] ?: d.widgetEnabled,
                instagramEnabled = p[SettingsKeys.INSTAGRAM] ?: d.instagramEnabled,
                facebookEnabled = p[SettingsKeys.FACEBOOK] ?: d.facebookEnabled,
                facebookLiteEnabled = p[SettingsKeys.FACEBOOK_LITE] ?: d.facebookLiteEnabled,
                minViewMs = (p[SettingsKeys.MIN_VIEW_MS] ?: d.minViewMs).coerceIn(500, 10_000),
                policy = runCatching { ConfidencePolicy.valueOf(p[SettingsKeys.POLICY] ?: "") }.getOrDefault(d.policy),
                widgetPosition = runCatching { WidgetPosition.valueOf(p[SettingsKeys.POSITION] ?: "") }.getOrDefault(d.widgetPosition),
                customXFrac = (p[SettingsKeys.CUSTOM_X] ?: d.customXFrac).coerceIn(0f, 1f),
                customYFrac = (p[SettingsKeys.CUSTOM_Y] ?: d.customYFrac).coerceIn(0f, 1f),
                animations = p[SettingsKeys.ANIMATIONS] ?: d.animations,
                lightTheme = p[SettingsKeys.LIGHT] ?: d.lightTheme,
                onboardingDone = p[SettingsKeys.ONBOARDING] ?: d.onboardingDone
            )
        }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    suspend fun setWidgetEnabled(enabled: Boolean) = set(SettingsKeys.WIDGET, enabled)

    suspend fun saveCustomPosition(xFrac: Float, yFrac: Float) {
        store.edit {
            it[SettingsKeys.CUSTOM_X] = xFrac.coerceIn(0f, 1f)
            it[SettingsKeys.CUSTOM_Y] = yFrac.coerceIn(0f, 1f)
            it[SettingsKeys.POSITION] = WidgetPosition.CUSTOM.name
        }
    }
}
