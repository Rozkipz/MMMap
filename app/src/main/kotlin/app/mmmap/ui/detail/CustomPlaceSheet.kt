package app.mmmap.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.mmmap.domain.model.CustomPlace
import app.mmmap.ui.theme.StarGold
import app.mmmap.ui.map.MapViewModel

/** ViewModel-connected entry point. */
@Composable
fun CustomPlaceSheet(
    place: CustomPlace,
    onDismiss: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val visitedIds by viewModel.visitedRestaurantIds.collectAsState()
    val isVisited = place.id in visitedIds
    CustomPlaceSheetContent(
        place = place,
        isVisited = isVisited,
        onToggleVisited = { viewModel.setCustomPlaceVisited(place, !isVisited) },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun CustomPlaceSheetContent(
    place: CustomPlace,
    isVisited: Boolean = false,
    onToggleVisited: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            Text(
                text = place.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            place.address?.let { address ->
                Spacer(Modifier.height(4.dp))
                Text(address, style = MaterialTheme.typography.bodySmall)
            }

            place.description?.let { desc ->
                Spacer(Modifier.height(12.dp))
                Text(desc, style = MaterialTheme.typography.bodyMedium)
            }

            place.notes?.let { notes ->
                Spacer(Modifier.height(8.dp))
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleVisited()
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (isVisited) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (isVisited) "Remove visited mark" else "Mark as visited",
                        modifier = Modifier.size(28.dp),
                        tint = if (isVisited) StarGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        val uri = Uri.parse(
                            "geo:${place.latitude},${place.longitude}?q=${Uri.encode(place.name)}"
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = "Directions",
                        modifier = Modifier.size(28.dp),
                    )
                }
                place.link?.let { url ->
                    IconButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = "Open website",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
