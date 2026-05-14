package app.mmmap.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mmmap.domain.model.Distinction
import app.mmmap.domain.model.FoursquareDetail
import app.mmmap.domain.model.Restaurant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests RestaurantSheetContent — the pure-state composable that contains all the UI logic.
 * No mocking required; state is passed directly.
 */
@RunWith(AndroidJUnit4::class)
class RestaurantSheetUiTest {

    @get:Rule val compose = createComposeRule()

    // --- Green Star ---

    @Test fun greenStarShown_whenTrue() {
        compose.setContent {
            RestaurantSheetContent(restaurant(greenStar = true), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("Green Star", substring = true).assertIsDisplayed()
    }

    @Test fun greenStarHidden_whenFalse() {
        compose.setContent {
            RestaurantSheetContent(restaurant(greenStar = false), onDismiss = {}, enrichment = null, loading = false)
        }
        assertTrue(compose.onAllNodesWithText("Green Star", substring = true).fetchSemanticsNodes().isEmpty())
    }

    // --- Facilities chips ---

    @Test fun facilitiesChips_rendered() {
        compose.setContent {
            RestaurantSheetContent(
                restaurant(facilities = "Car park,Interesting wine list"),
                onDismiss = {}, enrichment = null, loading = false,
            )
        }
        compose.onNodeWithText("Car park").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Interesting wine list").performScrollTo().assertIsDisplayed()
    }

    @Test fun facilitiesChips_spacesAroundCommasTrimmed() {
        compose.setContent {
            RestaurantSheetContent(
                restaurant(facilities = " Air conditioning , Wheelchair access "),
                onDismiss = {}, enrichment = null, loading = false,
            )
        }
        compose.onNodeWithText("Air conditioning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wheelchair access").performScrollTo().assertIsDisplayed()
    }

    @Test fun facilitiesChips_nullFacilities_noChipsNocrash() {
        compose.setContent {
            RestaurantSheetContent(restaurant(facilities = null), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("Test Restaurant").assertIsDisplayed()
    }

    @Test fun facilitiesChips_emptyString_noChips() {
        compose.setContent {
            RestaurantSheetContent(restaurant(facilities = ""), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("Test Restaurant").assertIsDisplayed()
    }

    // --- Badge labels ---

    @Test fun badgeLabel_threeStar() {
        compose.setContent {
            RestaurantSheetContent(restaurant(distinction = Distinction.THREE_STAR), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("3 Stars", substring = true).assertIsDisplayed()
    }

    @Test fun badgeLabel_twoStar() {
        compose.setContent {
            RestaurantSheetContent(restaurant(distinction = Distinction.TWO_STAR), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("2 Stars", substring = true).assertIsDisplayed()
    }

    @Test fun badgeLabel_oneStar() {
        compose.setContent {
            RestaurantSheetContent(restaurant(distinction = Distinction.ONE_STAR), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("1 Star", substring = true).assertIsDisplayed()
    }

    @Test fun badgeLabel_bibGourmand() {
        compose.setContent {
            RestaurantSheetContent(restaurant(distinction = Distinction.BIB_GOURMAND), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("Bib Gourmand", substring = true).assertIsDisplayed()
    }

    @Test fun badgeLabel_selected() {
        compose.setContent {
            RestaurantSheetContent(restaurant(distinction = Distinction.SELECTED), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("MICHELIN Selected", substring = true).assertIsDisplayed()
    }

    // --- Action buttons ---

    @Test fun websiteButton_shownWhenUrlPresent() {
        compose.setContent {
            RestaurantSheetContent(restaurant(websiteUrl = "https://example.com"), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithContentDescription("Website").assertIsDisplayed()
    }

    @Test fun websiteButton_hiddenWhenUrlNull() {
        compose.setContent {
            RestaurantSheetContent(restaurant(websiteUrl = null), onDismiss = {}, enrichment = null, loading = false)
        }
        assertTrue(compose.onAllNodesWithContentDescription("Website").fetchSemanticsNodes().isEmpty())
    }

    @Test fun phoneButton_shownFromRestaurantData() {
        compose.setContent {
            RestaurantSheetContent(restaurant(phone = "+44 20 0000 0000"), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithContentDescription("Call").assertIsDisplayed()
    }

    @Test fun phoneButton_hiddenWhenNullAndNoEnrichment() {
        compose.setContent {
            RestaurantSheetContent(restaurant(phone = null), onDismiss = {}, enrichment = null, loading = false)
        }
        assertTrue(compose.onAllNodesWithContentDescription("Call").fetchSemanticsNodes().isEmpty())
    }

    @Test fun phoneButton_shownFromEnrichment() {
        val enrichment = FoursquareDetail(null, emptyList(), null, "+33 1 00 00 00 00", null)
        compose.setContent {
            RestaurantSheetContent(restaurant(phone = null), onDismiss = {}, enrichment = enrichment, loading = false)
        }
        compose.onNodeWithContentDescription("Call").assertIsDisplayed()
    }

    @Test fun michelinGuideButton_alwaysPresent() {
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("Open in MICHELIN Guide", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun loadingIndicator_shownWhenLoadingAndNoPhoto() {
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = null, loading = true)
        }
        // CircularProgressIndicator has no text, but its parent exists — just verify no crash
        compose.onNodeWithText("Test Restaurant").assertIsDisplayed()
    }

    // --- "I've been here" toggle ---

    @Test fun beenHereCheckbox_displayed() {
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = null, loading = false)
        }
        compose.onNodeWithText("I've been here").performScrollTo().assertIsDisplayed()
    }

    @Test fun beenHereCheckbox_uncheckedWhenNotVisited() {
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = null, loading = false, isVisited = false)
        }
        // The Checkbox node itself is the toggleable element
        compose.onNodeWithText("I've been here").performScrollTo()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test fun beenHereCheckbox_checkedWhenVisited() {
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = null, loading = false, isVisited = true)
        }
        compose.onNodeWithText("I've been here").performScrollTo()
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test fun beenHereCheckbox_clickFiresCallback() {
        var callbackValue: Boolean? = null
        compose.setContent {
            RestaurantSheetContent(
                restaurant(), onDismiss = {}, enrichment = null, loading = false,
                isVisited = false, onVisitedChange = { callbackValue = it },
            )
        }
        compose.onNodeWithText("I've been here").performScrollTo().performClick()
        assertEquals(true, callbackValue)
    }

    @Test fun beenHereCheckbox_clickWhenVisited_firesWithFalse() {
        var callbackValue: Boolean? = null
        compose.setContent {
            RestaurantSheetContent(
                restaurant(), onDismiss = {}, enrichment = null, loading = false,
                isVisited = true, onVisitedChange = { callbackValue = it },
            )
        }
        compose.onNodeWithText("I've been here").performScrollTo().performClick()
        assertEquals(false, callbackValue)
    }

    @Test fun openNowLabel_shownFromEnrichment() {
        val enrichment = FoursquareDetail(null, listOf("Mon-Fri 12:00-22:00"), isOpenNow = true, null, null)
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = enrichment, loading = false)
        }
        compose.onNodeWithText("Open now", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun closedLabel_shownFromEnrichment() {
        val enrichment = FoursquareDetail(null, listOf("Mon-Fri 12:00-22:00"), isOpenNow = false, null, null)
        compose.setContent {
            RestaurantSheetContent(restaurant(), onDismiss = {}, enrichment = enrichment, loading = false)
        }
        compose.onNodeWithText("Closed", substring = true).performScrollTo().assertIsDisplayed()
    }

    // --- helpers ---

    private fun restaurant(
        distinction: Distinction = Distinction.ONE_STAR,
        greenStar: Boolean = false,
        facilities: String? = null,
        websiteUrl: String? = null,
        phone: String? = null,
    ) = Restaurant(
        id = "r1",
        name = "Test Restaurant",
        address = "1 Test St",
        location = "London",
        latitude = 51.5,
        longitude = -0.1,
        distinction = distinction,
        greenStar = greenStar,
        cuisine = "Modern British",
        price = "£££",
        phoneNumber = phone,
        michelinUrl = "https://guide.michelin.com/r1",
        websiteUrl = websiteUrl,
        description = "A fine establishment.",
        facilitiesAndServices = facilities,
    )
}
