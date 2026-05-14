package app.mmmap.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mmmap.BuildConfig

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mmmap") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("A map for exploring Michelin Guide restaurants worldwide.")

                HorizontalDivider()

                Text("Data sources", style = MaterialTheme.typography.titleSmall)
                LabelledLink(
                    label = "Restaurant data",
                    linkText = "ngshiheng/michelin-my-maps",
                    url = "https://github.com/ngshiheng/michelin-my-maps",
                    onOpen = ::open,
                )
                LabelledLink(
                    label = "Light map tiles",
                    linkText = "OpenFreeMap",
                    url = "https://openfreemap.org",
                    onOpen = ::open,
                )
                LabelledLink(
                    label = "Dark map tiles",
                    linkText = "© CARTO (CC-BY 3.0)",
                    url = "https://carto.com/attributions",
                    onOpen = ::open,
                )
                LabelledLink(
                    label = "Venue details",
                    linkText = "Foursquare Places API",
                    url = "https://foursquare.com/developer",
                    onOpen = ::open,
                )
                Text(
                    "Map data © OpenStreetMap contributors",
                    style = MaterialTheme.typography.bodySmall,
                )

                HorizontalDivider()

                Text("Source code", style = MaterialTheme.typography.titleSmall)
                Link(
                    text = "github.com/Rozkipz/Mmmap",
                    url = "https://github.com/Rozkipz/Mmmap",
                    onOpen = ::open,
                )

                HorizontalDivider()

                Text(
                    "Released under the MIT License",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun LabelledLink(label: String, linkText: String, url: String, onOpen: (String) -> Unit) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodySmall)
        Link(text = linkText, url = url, onOpen = onOpen)
    }
}

@Composable
private fun Link(text: String, url: String, onOpen: (String) -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onOpen(url) },
    )
}
