package app.mmmap.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import app.mmmap.ui.badgeColor
import app.mmmap.ui.badgeLabel
import app.mmmap.ui.theme.GreenStar
import app.mmmap.ui.theme.StarGold

/** ViewModel-connected entry point used by the navigation graph. */
@Composable
fun RestaurantSheet(
    restaurant: Restaurant,
    onDismiss: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val isVisited by viewModel.isVisited.collectAsState()
    LaunchedEffect(restaurant.id) { viewModel.setCurrentRestaurant(restaurant) }
    RestaurantSheetContent(
        restaurant = restaurant,
        onDismiss = onDismiss,
        isVisited = isVisited,
        onVisitedChange = { viewModel.setVisited(restaurant, it) },
    )
}

/** Pure-state composable — no ViewModel dependency, directly testable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestaurantSheetContent(
    restaurant: Restaurant,
    onDismiss: () -> Unit,
    isVisited: Boolean = false,
    onVisitedChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // Padded content
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

        // Distinction badge + optional Green Star
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = restaurant.distinction.badgeLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = restaurant.distinction.badgeColor(),
                fontWeight = FontWeight.Bold,
            )
            if (restaurant.greenStar) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "🌿 Green Star",
                    style = MaterialTheme.typography.labelMedium,
                    color = GreenStar,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(restaurant.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        restaurant.cuisine?.let { cuisine ->
            Row {
                Text(
                    cuisine,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = TextDecoration.Underline,
                )
                restaurant.price?.let { price ->
                    Text("  ·  $price", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(restaurant.address, style = MaterialTheme.typography.bodySmall)
        restaurant.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        // Description
        restaurant.description?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        // Facilities & services chips
        restaurant.facilitiesAndServices
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { facilities ->
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    facilities.forEach { facility ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                facility,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

        Spacer(Modifier.height(16.dp))

        // Action row
        val haptic = LocalHapticFeedback.current
        val toggle: (Boolean) -> Unit = { newValue ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onVisitedChange(newValue)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { toggle(!isVisited) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (isVisited) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (isVisited) "Remove visited mark" else "Mark as visited",
                    modifier = Modifier.size(28.dp),
                    tint = if (isVisited) StarGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val phone = restaurant.phoneNumber
            if (phone != null) {
                IconButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Default.Phone, contentDescription = "Call", modifier = Modifier.size(28.dp)) }
            }
            restaurant.websiteUrl?.let { url ->
                IconButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Default.Public, contentDescription = "Website", modifier = Modifier.size(28.dp)) }
            }
            IconButton(
                onClick = {
                    val uri = Uri.parse(
                        "geo:${restaurant.latitude},${restaurant.longitude}?q=${Uri.encode(restaurant.name)}"
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Default.DirectionsCar, contentDescription = "Directions", modifier = Modifier.size(28.dp)) }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(restaurant.michelinUrl)))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open in MICHELIN Guide")
        }

        Spacer(Modifier.height(24.dp))
        } // end padded Column
    }
}
