package app.mmmap.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.mmmap.domain.model.Restaurant
import app.mmmap.ui.detail.RestaurantSheet
import app.mmmap.ui.shortLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val nearby by viewModel.nearby.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var selectedRestaurant by remember { mutableStateOf<Restaurant?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Near Me") }) }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(nearby, key = { it.first.id }) { (restaurant, distanceKm) ->
                NearbyRow(
                    restaurant = restaurant,
                    distanceKm = distanceKm,
                    onClick = { selectedRestaurant = restaurant },
                )
                HorizontalDivider()
            }
        }
    }

    if (selectedRestaurant != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedRestaurant = null },
            sheetState = sheetState,
        ) {
            RestaurantSheet(
                restaurant = selectedRestaurant!!,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedRestaurant = null }
                },
            )
        }
    }
}

@Composable
private fun NearbyRow(
    restaurant: Restaurant,
    distanceKm: Float,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(restaurant.name, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(restaurant.distinction.shortLabel(), restaurant.cuisine, restaurant.price)
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (distanceKm < 1f) "${(distanceKm * 1000).toInt()}m" else "${"%.1f".format(distanceKm)}km",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

