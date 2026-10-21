package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Material 3 DatePickerDialog to pick specific due dates for tasks or task filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDatePickerDialog(
    initialDateMillis: Long? = null,
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateSelected(datePickerState.selectedDateMillis)
                },
                modifier = Modifier.testTag("date_picker_confirm_button")
            ) {
                Text("သတ်မှတ်မည် (Confirm)")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("date_picker_dismiss_button")
            ) {
                Text("ပယ်ဖျက် (Cancel)")
            }
        },
        modifier = modifier.testTag("material3_date_picker_dialog")
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = "သတ်မှတ်ရက် ရွေးချယ်ပါ (Pick Due Date)",
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        )
    }
}
