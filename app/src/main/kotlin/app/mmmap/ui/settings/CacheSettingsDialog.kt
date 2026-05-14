package app.mmmap.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mmmap.data.prefs.MapCachePreferences

private fun labelFor(mb: Long): String = when (mb) {
    1024L -> "1 GB"
    else  -> "$mb MB"
}

@Composable
fun CacheSettingsDialog(
    currentSizeMb: Long,
    onSizeSelected: (Long) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map tile cache") },
        text = {
            Column {
                Text(
                    "Tiles loaded on Wi-Fi are cached for offline use. " +
                    "Changing the limit clears the existing cache.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                MapCachePreferences.OPTIONS_MB.forEach { mb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSizeSelected(mb) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mb == currentSizeMb,
                            onClick = { onSizeSelected(mb) },
                        )
                        Text(
                            text = labelFor(mb),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    onClick = { onClearCache(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Clear cache now",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
