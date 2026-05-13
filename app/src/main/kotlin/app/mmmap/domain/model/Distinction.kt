package app.mmmap.domain.model

enum class Distinction(val label: String) {
    THREE_STAR("3 Stars"),
    TWO_STAR("2 Stars"),
    ONE_STAR("1 Star"),
    BIB_GOURMAND("Bib Gourmand"),
    SELECTED("Selected Restaurants");

    companion object {
        fun fromAward(award: String?): Distinction = when {
            award == null -> SELECTED
            award.contains("3") -> THREE_STAR
            award.contains("2") -> TWO_STAR
            award.contains("1") -> ONE_STAR
            award.contains("Bib", ignoreCase = true) -> BIB_GOURMAND
            else -> SELECTED
        }
    }
}
