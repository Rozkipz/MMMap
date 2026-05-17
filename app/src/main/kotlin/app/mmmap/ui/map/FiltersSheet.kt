package app.mmmap.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mmmap.domain.model.Distinction
import app.mmmap.ui.chipLabel
import app.mmmap.ui.dotColor

internal val PRICE_TIERS = listOf(1 to "$", 2 to "$$", 3 to "$$$", 4 to "$$$$")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(
    filters: MapFilters,
    availableCuisines: List<String>,
    mode: MapMode = MapMode.MICHELIN,
    customCollectionLabel: String? = null,
    onModeChange: (MapMode) -> Unit = {},
    onFiltersChange: (MapFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var cuisineQuery by rememberSaveable { mutableStateOf("") }

    val displayCuisines = if (cuisineQuery.isBlank()) availableCuisines
                          else availableCuisines.filter { it.contains(cuisineQuery, ignoreCase = true) }
    val allCuisinesSelected = filters.cuisines == null
    val anyActive = mode != MapMode.MICHELIN || filters.distinctions != null ||
            filters.cuisines != null || filters.priceTiers != null || filters.visitedFilter != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {

            // ── Mode (only shown when a custom collection is configured) ────
            if (customCollectionLabel != null) {
                item {
                    SectionHeader("Mode")
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = mode == MapMode.MICHELIN,
                            onClick = { onModeChange(MapMode.MICHELIN) },
                            label = { Text("MICHELIN") },
                        )
                        FilterChip(
                            selected = mode == MapMode.CUSTOM,
                            onClick = { onModeChange(MapMode.CUSTOM) },
                            label = { Text(customCollectionLabel) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Award ──────────────────────────────────────────────────────
            if (mode == MapMode.MICHELIN) item {
                SectionHeader("Award")
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Distinction.entries.forEach { d ->
                        val distinctions = filters.distinctions
                        FilterChip(
                            selected = distinctions != null && d in distinctions,
                            onClick = {
                                val newSet = if (distinctions == null) setOf(d)
                                             else if (d in distinctions) distinctions - d else distinctions + d
                                onFiltersChange(filters.copy(
                                    distinctions = if (newSet.isEmpty() || newSet.size == Distinction.entries.size) null else newSet,
                                ))
                            },
                            label = { Text(d.chipLabel()) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = d.dotColor().copy(alpha = 0.15f),
                                labelColor = d.dotColor(),
                                selectedContainerColor = d.dotColor().copy(alpha = 0.4f),
                                selectedLabelColor = d.dotColor(),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Price ──────────────────────────────────────────────────────
            if (mode == MapMode.MICHELIN) item {
                HorizontalDivider()
                SectionHeader("Price")
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRICE_TIERS.forEach { (tier, label) ->
                        val tiers = filters.priceTiers
                        FilterChip(
                            selected = tiers != null && tier in tiers,
                            onClick = {
                                val newTiers = if (tiers == null) setOf(tier)
                                              else if (tier in tiers) tiers - tier else tiers + tier
                                onFiltersChange(filters.copy(
                                    priceTiers = if (newTiers.isEmpty() || newTiers.size == PRICE_TIERS.size) null else newTiers
                                ))
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Visits ─────────────────────────────────────────────────────
            item {
                HorizontalDivider()
                SectionHeader("Visits")
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filters.visitedFilter == VisitedFilter.VISITED_ONLY,
                        onClick = {
                            onFiltersChange(filters.copy(
                                visitedFilter = if (filters.visitedFilter == VisitedFilter.VISITED_ONLY) null
                                                else VisitedFilter.VISITED_ONLY,
                            ))
                        },
                        label = { Text("Visited") },
                    )
                    FilterChip(
                        selected = filters.visitedFilter == VisitedFilter.UNVISITED_ONLY,
                        onClick = {
                            onFiltersChange(filters.copy(
                                visitedFilter = if (filters.visitedFilter == VisitedFilter.UNVISITED_ONLY) null
                                                else VisitedFilter.UNVISITED_ONLY,
                            ))
                        },
                        label = { Text("Unvisited") },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Cuisine header + search (sticky so it stays visible while scrolling) ──
            if (mode == MapMode.MICHELIN) stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Cuisine",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 16.dp, bottom = 8.dp),
                        )
                        TextButton(onClick = {
                            onFiltersChange(filters.copy(cuisines = if (allCuisinesSelected) emptySet() else null))
                        }) {
                            Text(if (allCuisinesSelected) "Clear all" else "Select all")
                        }
                    }
                    OutlinedTextField(
                        value = cuisineQuery,
                        onValueChange = { cuisineQuery = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── Cuisine list ───────────────────────────────────────────────
            if (mode == MapMode.MICHELIN) items(displayCuisines) { cuisine ->
                val checked = allCuisinesSelected || cuisine in (filters.cuisines ?: emptySet())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = if (allCuisinesSelected) {
                                availableCuisines.filter { it != cuisine }.toSet()
                            } else {
                                val current = filters.cuisines ?: emptySet()
                                if (cuisine in current) current - cuisine else current + cuisine
                            }
                            onFiltersChange(filters.copy(
                                cuisines = if (newSet.size == availableCuisines.size) null else newSet
                            ))
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(
                        cuisine,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // ── Clear all ──────────────────────────────────────────────────
            if (anyActive) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    TextButton(
                        onClick = {
                            onFiltersChange(MapFilters())
                            onModeChange(MapMode.MICHELIN)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("Clear all filters")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
