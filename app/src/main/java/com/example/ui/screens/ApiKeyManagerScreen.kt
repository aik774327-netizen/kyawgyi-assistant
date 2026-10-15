package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ApiKeyManagerScreen(
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  com.kyawgyi.ir.ui.ApiKeyManagerScreen(
    onNavigateBack = onNavigateBack,
    modifier = modifier
  )
}
