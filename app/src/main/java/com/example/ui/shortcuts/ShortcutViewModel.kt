package com.example.ui.shortcuts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.ShortcutEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShortcutViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = KyawGyiDatabase.getDatabase(application).shortcutDao()

    // Room Database မှ Shortcuts များကို Reactive StateFlow အဖြစ် ရယူခြင်း
    val shortcuts: StateFlow<List<ShortcutEntity>> = dao.getAllActiveShortcuts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addShortcut(triggerPhrase: String, actionType: String, payload: String) {
        viewModelScope.launch {
            val shortcut = ShortcutEntity(
                triggerPhrase = triggerPhrase.trim().lowercase(),
                actionType = actionType,
                payload = payload.trim()
            )
            dao.insertShortcut(shortcut)
        }
    }

    fun toggleShortcutStatus(shortcut: ShortcutEntity) {
        viewModelScope.launch {
            dao.insertShortcut(shortcut.copy(isEnabled = !shortcut.isEnabled))
        }
    }

    fun deleteShortcut(shortcut: ShortcutEntity) {
        viewModelScope.launch {
            dao.deleteShortcut(shortcut)
        }
    }

    fun updateShortcut(shortcut: ShortcutEntity) {
        viewModelScope.launch {
            dao.insertShortcut(shortcut)
        }
    }
}
