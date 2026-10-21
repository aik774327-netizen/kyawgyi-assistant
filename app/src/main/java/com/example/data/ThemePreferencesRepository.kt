package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "kyaw_gyi_theme_prefs")

enum class ThemeMode(val title: String, val myanmarLabel: String) {
    SYSTEM("System Default", "စနစ်အတိုင်း"),
    LIGHT("Light Mode", "နေ့ဘက် (Light)"),
    DARK("Dark Mode", "ညဘက် (Dark)")
}

class ThemePreferencesRepository private constructor(private val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _themeModeFlow = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    val themeFlow: Flow<ThemeMode> = context.themeDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val modeName = preferences[KEY_THEME_MODE] ?: ThemeMode.DARK.name
            try {
                ThemeMode.valueOf(modeName)
            } catch (e: Exception) {
                ThemeMode.DARK
            }
        }

    init {
        applicationScope.launch {
            try {
                themeFlow.collect { mode ->
                    _themeModeFlow.value = mode
                }
            } catch (e: Exception) {
                android.util.Log.e("ThemePreferencesRepo", "Error collecting theme preference", e)
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        _themeModeFlow.value = mode
        context.themeDataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun toggleLightDark() {
        val nextMode = when (_themeModeFlow.value) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(nextMode)
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")

        @Volatile
        private var INSTANCE: ThemePreferencesRepository? = null

        fun getInstance(context: Context): ThemePreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
