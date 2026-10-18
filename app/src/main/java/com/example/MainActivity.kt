package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.permissions.DirectRouteManager
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.kyawgyi.ir.utils.PermissionManager

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()
  private lateinit var permissionManager: PermissionManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    handleIncomingRouteIntent(intent)

    permissionManager = PermissionManager(this) { allGranted, _ ->
      if (allGranted || PermissionManager.hasMicrophonePermission(this)) {
        permissionManager.startVoiceServiceIfPermitted()
      }
    }
    permissionManager.requestEssentialPermissions()

    setContent {
      MyApplicationTheme {
        MainScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingRouteIntent(intent)
  }

  private fun handleIncomingRouteIntent(incomingIntent: Intent?) {
    DirectRouteManager.handleIntent(
      intent = incomingIntent,
      context = this,
      onNavigate = { routeKey ->
        viewModel.navigateToDirectRoute(routeKey)
      }
    )
  }

  override fun onResume() {
    super.onResume()
    permissionManager.startVoiceServiceIfPermitted()
    viewModel.permissionManager.refreshAll(
      gattConnected = viewModel.bleConnected.value,
      protocolVerified = viewModel.bleConnected.value,
      authenticated = viewModel.bleConnected.value
    )
  }
}
