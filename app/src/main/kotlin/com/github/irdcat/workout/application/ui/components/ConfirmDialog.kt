package com.github.irdcat.workout.application.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.github.irdcat.workout.application.ui.theme.Colors

/**
 * Asks before a step that can't simply be taken back, e.g. finishing a workout.
 * A [destructive] step, one that deletes what was logged, gets a red confirm button.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Colors.Surface1,
        titleContentColor = Colors.TextPrimary,
        textContentColor = Colors.TextSecondary,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Colors.Critical else Colors.Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Colors.TextPrimary)
            }
        },
    )
}
