package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kyaw_gyi_language_prefs")

/**
 * Metadata representation of a supported voice language in Kyaw Gyi AI.
 */
data class VoiceLanguage(
    val code: String,
    val displayNameMM: String,
    val displayNameEN: String,
    val nativeName: String,
    val flagEmoji: String,
    val isDefault: Boolean = false,
    val sampleGreeting: String = "မင်္ဂလာပါ၊ ကျော်ကြီး အသင့်ရှိပါတယ်ခင်ဗျာ"
)

/**
 * LanguagePreferencesRepository
 *
 * Persists the user's preferred voice language choice (e.g., "my-MM", "en-US")
 * into Android Jetpack DataStore Preferences.
 *
 * Guarantees:
 * 1. Asynchronous reactive updates via Kotlin [Flow] and [StateFlow].
 * 2. Instant synchronous fallback for early voice pipeline initialization (STT & TTS).
 * 3. Observer pattern allowing voice engines (SpeechHandler, VoiceSessionManager,
 *    KyawGyiCoreEngine, MyanmarTTSEngine) to reconfigure on locale change.
 */
class LanguagePreferencesRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LanguagePrefsRepo"

        val PREF_KEY_LANGUAGE = stringPreferencesKey("voice_preferred_language")
        const val DEFAULT_LANGUAGE_CODE = "my-MM"

        val SUPPORTED_LANGUAGES = listOf(
            VoiceLanguage(
                code = "my-MM",
                displayNameMM = "မြန်မာ (ဗမာဘာသာ)",
                displayNameEN = "Myanmar (Burmese)",
                nativeName = "မြန်မာစာ/စကား",
                flagEmoji = "🇲🇲",
                isDefault = true,
                sampleGreeting = "မင်္ဂလာပါခင်ဗျာ၊ ကျော်ကြီး အဆင်သင့်ရှိပါတယ်"
            ),
            VoiceLanguage(
                code = "en-US",
                displayNameMM = "အင်္ဂလိပ် (ယူအက်စ်)",
                displayNameEN = "English (United States)",
                nativeName = "English (US)",
                flagEmoji = "🇺🇸",
                isDefault = false,
                sampleGreeting = "Hello! Kyaw Gyi voice assistant is ready."
            ),
            VoiceLanguage(
                code = "en-GB",
                displayNameMM = "အင်္ဂလိပ် (ယူကေ)",
                displayNameEN = "English (United Kingdom)",
                nativeName = "English (UK)",
                flagEmoji = "🇬🇧",
                isDefault = false,
                sampleGreeting = "Good day! Kyaw Gyi speech engine is ready."
            ),
            VoiceLanguage(
                code = "th-TH",
                displayNameMM = "ထိုင်း (ရှမ်း/ထိုင်းနယ်စပ်)",
                displayNameEN = "Thai (Thailand)",
                nativeName = "ภาษาไทย",
                flagEmoji = "🇹🇭",
                isDefault = false,
                sampleGreeting = "สวัสดีครับ ကျော်ကြီး พร้อมให้บริการครับ"
            )
        )

        @Volatile
        private var instance: LanguagePreferencesRepository? = null

        fun getInstance(context: Context): LanguagePreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: LanguagePreferencesRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = CopyOnWriteArrayList<(String, Locale) -> Unit>()

    // In-memory cache for ultra-fast, non-blocking synchronous access during voice pipeline init
    @Volatile
    private var cachedLanguage: String = DEFAULT_LANGUAGE_CODE

    private val _selectedLanguageState = MutableStateFlow(DEFAULT_LANGUAGE_CODE)
    val selectedLanguageState: StateFlow<String> = _selectedLanguageState.asStateFlow()

    val selectedLanguageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading language preferences from DataStore", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PREF_KEY_LANGUAGE] ?: DEFAULT_LANGUAGE_CODE
        }

    init {
        // Collect DataStore updates in background and keep cached value & StateFlow in sync
        scope.launch {
            selectedLanguageFlow.collect { langCode ->
                val validatedCode = validateLanguageCode(langCode)
                cachedLanguage = validatedCode
                _selectedLanguageState.value = validatedCode
                val locale = createLocale(validatedCode)
                notifyListeners(validatedCode, locale)
                Log.i(TAG, "Active voice language updated from DataStore: $validatedCode (${locale.toLanguageTag()})")
            }
        }
    }

    /**
     * Persist the user's preferred language choice into DataStore.
     */
    suspend fun savePreferredLanguage(languageCode: String) {
        val validatedCode = validateLanguageCode(languageCode)
        Log.i(TAG, "Saving preferred language to DataStore: $validatedCode")
        try {
            context.dataStore.edit { preferences ->
                preferences[PREF_KEY_LANGUAGE] = validatedCode
            }
            cachedLanguage = validatedCode
            _selectedLanguageState.value = validatedCode
            notifyListeners(validatedCode, createLocale(validatedCode))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist preferred language to DataStore: ${e.message}", e)
        }
    }

    suspend fun saveLanguage(languageCode: String) = savePreferredLanguage(languageCode)

    /**
     * Synchronous blocking or in-memory getter for voice pipeline bootstrap
     */
    fun getSelectedLanguageSync(): String {
        return cachedLanguage
    }

    /**
     * Get the corresponding Java/Android [Locale] instance for the selected language.
     */
    fun getSelectedLocaleSync(): Locale {
        return createLocale(cachedLanguage)
    }

    /**
     * Register a callback to be notified immediately when the voice language changes.
     */
    fun addOnLanguageChangeListener(listener: (langCode: String, locale: Locale) -> Unit) {
        listeners.add(listener)
        // Immediately invoke with current state
        listener(cachedLanguage, createLocale(cachedLanguage))
    }

    fun removeOnLanguageChangeListener(listener: (langCode: String, locale: Locale) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(langCode: String, locale: Locale) {
        for (listener in listeners) {
            try {
                listener(langCode, locale)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking language change listener: ${e.message}")
            }
        }
    }

    private fun validateLanguageCode(code: String): String {
        val matched = SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
        return matched?.code ?: DEFAULT_LANGUAGE_CODE
    }

    fun getSampleGreeting(languageCode: String): String {
        return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }?.sampleGreeting
            ?: "မင်္ဂလာပါ၊ ကျော်ကြီး အသင့်ရှိပါတယ်ခင်ဗျာ"
    }

    fun createLocale(languageCode: String): Locale {
        return when {
            languageCode.equals("my-MM", ignoreCase = true) || languageCode.equals("my", ignoreCase = true) -> {
                Locale.forLanguageTag("my-MM")
            }
            languageCode.equals("en-US", ignoreCase = true) -> Locale.US
            languageCode.equals("en-GB", ignoreCase = true) -> Locale.UK
            languageCode.equals("th-TH", ignoreCase = true) || languageCode.equals("th", ignoreCase = true) -> {
                Locale.forLanguageTag("th-TH")
            }
            else -> Locale.forLanguageTag(languageCode)
        }
    }
}
