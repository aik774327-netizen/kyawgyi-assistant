package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.components.SettingsScreen as ComponentsSettingsScreen

/**
 * SettingsScreen composable using DataStore to persist user language preferences (my-MM or en-US)
 * and updating the local state to ensure the voice pipeline initializes with the chosen locale.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    selectedLanguage: String? = null,
    onLanguageSelected: ((String) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    ComponentsSettingsScreen(
        modifier = modifier,
        selectedLanguage = selectedLanguage,
        onLanguageSelected = onLanguageSelected,
        onNavigateBack = onNavigateBack
    )
}
