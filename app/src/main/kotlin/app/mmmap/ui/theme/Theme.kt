package app.mmmap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = MichelinRed,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = StarGold,
)

@Composable
fun MMMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
