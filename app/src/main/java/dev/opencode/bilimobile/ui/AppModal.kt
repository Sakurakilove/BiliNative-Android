package dev.opencode.bilimobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AppModal(
    title: String,
    dismiss: () -> Unit,
    primaryLabel: String,
    primary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    secondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(18.dp).widthIn(max = 430.dp).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(dismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Spacer(Modifier.height(10.dp))
                content()
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (secondaryLabel != null && secondary != null) TextButton(secondary) { Text(secondaryLabel) }
                    Spacer(Modifier.width(8.dp))
                    Button(primary, enabled = primaryEnabled) { Text(primaryLabel) }
                }
            }
        }
    }
}
