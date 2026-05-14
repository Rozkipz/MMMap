package app.mmmap.ui

import androidx.compose.ui.graphics.Color
import app.mmmap.domain.model.Distinction
import app.mmmap.ui.theme.BibBrown
import app.mmmap.ui.theme.BibGreen
import app.mmmap.ui.theme.MichelinRed
import app.mmmap.ui.theme.OneStarCyan
import app.mmmap.ui.theme.SelectedBlue
import app.mmmap.ui.theme.StarGold
import app.mmmap.ui.theme.TwoStarViolet

fun Distinction.shortLabel() = when (this) {
    Distinction.THREE_STAR   -> "★★★"
    Distinction.TWO_STAR     -> "★★"
    Distinction.ONE_STAR     -> "★"
    Distinction.BIB_GOURMAND -> "Bib"
    Distinction.SELECTED     -> "Selected"
}

fun Distinction.chipLabel() = when (this) {
    Distinction.THREE_STAR   -> "3★"
    Distinction.TWO_STAR     -> "2★"
    Distinction.ONE_STAR     -> "1★"
    Distinction.BIB_GOURMAND -> "Bib"
    Distinction.SELECTED     -> "Selected"
}

fun Distinction.dotColor(): Color = when (this) {
    Distinction.THREE_STAR   -> MichelinRed
    Distinction.TWO_STAR     -> TwoStarViolet
    Distinction.ONE_STAR     -> OneStarCyan
    Distinction.BIB_GOURMAND -> BibGreen
    Distinction.SELECTED     -> SelectedBlue
}

fun Distinction.badgeLabel() = when (this) {
    Distinction.THREE_STAR   -> "★★★  3 Stars"
    Distinction.TWO_STAR     -> "★★  2 Stars"
    Distinction.ONE_STAR     -> "★  1 Star"
    Distinction.BIB_GOURMAND -> "Bib Gourmand"
    Distinction.SELECTED     -> "MICHELIN Selected"
}

fun Distinction.badgeColor(): Color = when (this) {
    Distinction.THREE_STAR,
    Distinction.TWO_STAR,
    Distinction.ONE_STAR     -> StarGold
    Distinction.BIB_GOURMAND -> BibBrown
    Distinction.SELECTED     -> SelectedBlue
}
