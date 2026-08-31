package app.mmmap.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Launches [intent], showing a toast instead of crashing when nothing can handle it.
 *
 * Mmmap deliberately targets devices without Google Play Services, where there is often no
 * handler for `geo:` at all — and the upstream dataset contains malformed website URLs
 * (e.g. `https//:example.it`, which parses to a scheme-less Uri that resolves to nothing).
 * An unguarded [Context.startActivity] throws [ActivityNotFoundException] in both cases,
 * killing the process from a simple button tap.
 */
fun Context.launchOrToast(intent: Intent, failureMessage: String) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

/** Convenience for `ACTION_VIEW` on a URL string. */
fun Context.openUrl(url: String, failureMessage: String = "No app can open this link") =
    launchOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(url)), failureMessage)
