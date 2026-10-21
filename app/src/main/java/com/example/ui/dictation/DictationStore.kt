package com.example.ui.dictation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dictationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kyawgyi_dictation_prefs"
)

/**
 * Local persistence for the dictation document, following the same
 * DataStore Preferences pattern as LanguagePreferencesRepository and
 * TaskBackupDataStore. Stores only the document model (text, language,
 * toggles, timestamp) — no audio is ever persisted.
 */
class DictationStore(context: Context) {

    private val appContext: Context = context.applicationContext

    suspend fun load(): DictationDocument {
        val prefs = appContext.dictationDataStore.data.first()
        val values = mapOf(
            DictationDocument.KEY_TEXT to (prefs[KEY_TEXT] ?: ""),
            DictationDocument.KEY_LANGUAGE to (prefs[KEY_LANGUAGE] ?: ""),
            DictationDocument.KEY_AUTO_RESTART to (prefs[KEY_AUTO_RESTART]?.toString() ?: ""),
            DictationDocument.KEY_AUTO_SAVE to (prefs[KEY_AUTO_SAVE]?.toString() ?: ""),
            DictationDocument.KEY_UPDATED_AT to (prefs[KEY_UPDATED_AT]?.toString() ?: "")
        )
        return DictationDocument.decode(values)
    }

    suspend fun save(document: DictationDocument) {
        appContext.dictationDataStore.edit { prefs ->
            prefs[KEY_TEXT] = document.text
            prefs[KEY_LANGUAGE] = document.language
            prefs[KEY_AUTO_RESTART] = document.autoRestart
            prefs[KEY_AUTO_SAVE] = document.autoSave
            prefs[KEY_UPDATED_AT] = document.updatedAtMs
        }
    }

    companion object {
        private val KEY_TEXT = stringPreferencesKey(DictationDocument.KEY_TEXT)
        private val KEY_LANGUAGE = stringPreferencesKey(DictationDocument.KEY_LANGUAGE)
        private val KEY_AUTO_RESTART = booleanPreferencesKey(DictationDocument.KEY_AUTO_RESTART)
        private val KEY_AUTO_SAVE = booleanPreferencesKey(DictationDocument.KEY_AUTO_SAVE)
        private val KEY_UPDATED_AT = longPreferencesKey(DictationDocument.KEY_UPDATED_AT)
    }
}
