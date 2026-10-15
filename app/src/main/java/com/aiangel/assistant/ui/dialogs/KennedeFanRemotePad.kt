package com.aiangel.assistant.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiangel.assistant.viewmodel.FanCommand
import com.aiangel.assistant.viewmodel.FanRemoteViewModel

/**
 * Kennede Fan Remote Pad — the IR half of the "EcoFlow Dialog & Kennede Fan
 * Remote Pad (IR & BLE Live Controls)" screen from the presentation layer.
 * Pure state-driven: every tap goes through FanRemoteViewModel.sendCommand(),
 * which in turn goes through DeviceControlHandler -> RealDeviceActionExecutor.
 */
@Composable
fun KennedeFanRemotePad(
    viewModel: FanRemoteViewModel,
    onDismiss: () -> Unit,
    onOpenImportDialog: (commandLabel: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kennede ပန်ကာ ထိန်းချုပ်မှု") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Speed row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { viewModel.sendCommand(FanCommand.SPEED_DOWN) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text(FanCommand.SPEED_DOWN.displayText) }

                    OutlinedButton(
                        onClick = { viewModel.sendCommand(FanCommand.SPEED_UP) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text(FanCommand.SPEED_UP.displayText) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Power — the primary, largest control
                Button(
                    onClick = { viewModel.sendCommand(FanCommand.POWER) },
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(FanCommand.POWER.displayText) }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { viewModel.sendCommand(FanCommand.OSCILLATE) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text(FanCommand.OSCILLATE.displayText) }

                    OutlinedButton(
                        onClick = { viewModel.sendCommand(FanCommand.TIMER) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text(FanCommand.TIMER.displayText) }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                uiState.statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Command not yet in the DB — offer the JSON code-set import path
                // (see IrCodeImporter) since this phone has no IR receiver to learn from.
                uiState.awaitingImportFor?.let { commandLabel ->
                    TextButton(onClick = { onOpenImportDialog(commandLabel) }) {
                        Text("IR code တင်သွင်းရန်")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ပိတ်ရန်") }
        }
    )
}
