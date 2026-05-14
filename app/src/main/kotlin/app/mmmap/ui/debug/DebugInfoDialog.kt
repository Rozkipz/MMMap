package app.mmmap.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mmmap.ui.map.DebugState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugInfoDialog(state: DebugState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Diagnostics") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Section("Database")
                DiagRow("Restaurants stored", state.dbRestaurantCount.toString())
                DiagRow("In current viewport", state.viewportCount.toString())

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Section("Sync")
                DiagRow("Worker", state.workerState)
                DiagRow("Last sync", state.lastSyncAt?.let { formatTime(it) } ?: "never")
                DiagRow("CSV SHA", state.lastCsvSha ?: "none")

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Section("Viewport bounds")
                if (state.bounds != null) {
                    DiagRow("Lat", "%.4f → %.4f".format(state.bounds.minLat, state.bounds.maxLat))
                    DiagRow("Lon", "%.4f → %.4f".format(state.bounds.minLon, state.bounds.maxLon))
                } else {
                    InfoRow("(map not moved yet)")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Section("Filters")
                DiagRow("Distinction", state.filters.distinction?.label ?: "none")
                DiagRow("Cuisine", state.filters.cuisine ?: "none")
                DiagRow("Price", state.filters.price ?: "none")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    return when {
        diff < 60_000L       -> "just now"
        diff < 3_600_000L    -> "${diff / 60_000}m ago"
        diff < 86_400_000L   -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}
