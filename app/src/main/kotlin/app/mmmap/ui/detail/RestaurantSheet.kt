package app.mmmap.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.Restaurant
import app.mmmap.ui.theme.BibBrown
import app.mmmap.ui.theme.MichelinRed
import app.mmmap.ui.theme.SelectedGray
import app.mmmap.ui.theme.StarGold

@Composable
fun RestaurantSheet(
    restaurant: Restaurant,
    onDismiss: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val enrichment by viewModel.enrichment.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(restaurant.id) { viewModel.loadEnrichment(restaurant) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Photo
        val photoUrl = enrichment?.photoUrl
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(bottom = 12.dp),
            )
        } else if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
        }

        // Distinction badge
        Text(
            text = restaurant.distinction.badgeLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = restaurant.distinction.badgeColor(),
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(2.dp))

        Text(restaurant.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        restaurant.cuisine?.let {
            Text("$it${restaurant.price?.let { p -> "  ·  $p" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        Text(restaurant.address, style = MaterialTheme.typography.bodySmall)
        restaurant.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        // Opening hours
        enrichment?.openingHours?.takeIf { it.isNotEmpty() }?.let { hours ->
            Spacer(Modifier.height(12.dp))
            val statusLabel = when (enrichment?.isOpenNow) {
                true -> "Open now"
                false -> "Closed"
                null -> ""
            }
            if (statusLabel.isNotEmpty()) {
                Text(statusLabel, style = MaterialTheme.typography.labelMedium,
                    color = if (enrichment?.isOpenNow == true) androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.error)
            }
            hours.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        }

        // Description
        restaurant.description?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))

        // Action row
        Row(verticalAlignment = Alignment.CenterVertically) {
            val phone = enrichment?.phone ?: restaurant.phoneNumber
            if (phone != null) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }) { Icon(Icons.Default.Phone, contentDescription = "Call") }
            }
            restaurant.websiteUrl?.let { url ->
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Icon(Icons.Default.Public, contentDescription = "Website") }
            }
            IconButton(onClick = {
                val uri = Uri.parse("geo:${restaurant.latitude},${restaurant.longitude}?q=${Uri.encode(restaurant.name)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }) { Icon(Icons.Default.DirectionsCar, contentDescription = "Directions") }
            Spacer(Modifier.width(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Michelin Guide deep-link
        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(restaurant.michelinUrl)))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open in MICHELIN Guide")
        }

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = {
                val subject = "Wrong info for ${restaurant.name} on MMMap"
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }
                context.startActivity(Intent.createChooser(intent, "Report"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Wrong info?")
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun Distinction.badgeLabel() = when (this) {
    Distinction.THREE_STAR -> "★★★  3 Stars"
    Distinction.TWO_STAR -> "★★  2 Stars"
    Distinction.ONE_STAR -> "★  1 Star"
    Distinction.BIB_GOURMAND -> "Bib Gourmand"
    Distinction.SELECTED -> "MICHELIN Selected"
}

@Composable
private fun Distinction.badgeColor() = when (this) {
    Distinction.THREE_STAR, Distinction.TWO_STAR, Distinction.ONE_STAR -> StarGold
    Distinction.BIB_GOURMAND -> BibBrown
    Distinction.SELECTED -> SelectedGray
}
