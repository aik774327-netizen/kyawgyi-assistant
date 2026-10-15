package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyawgyi.ir.repository.IrViewModel

@Composable
fun RemoteControlScreen(
  viewModel: IrViewModel,
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  com.kyawgyi.ir.ui.RemoteControlScreen(
    viewModel = viewModel,
    onNavigateBack = onNavigateBack,
    modifier = modifier
  )
}
