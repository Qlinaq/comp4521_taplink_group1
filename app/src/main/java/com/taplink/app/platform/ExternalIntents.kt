package com.taplink.app.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.taplink.app.domain.Receipt
import com.taplink.app.domain.ServiceLocation

object ExternalIntents {
    /** Opens the user's maps app. The label explicitly identifies the supplied point as demo data. */
    fun openMap(context: Context, location: ServiceLocation): Boolean {
        if (!validCoordinates(location.latitude, location.longitude)) return false
        val point = "${location.latitude},${location.longitude}"
        val query = Uri.encode("$point (${location.name} — demo location)")
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$query")))
    }

    fun shareReceipt(context: Context, receipt: Receipt): Boolean = shareText(context, receipt.shareText())

    /** Opens the Android chooser; never sends to a recipient automatically. */
    fun shareText(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return launch(context, Intent.createChooser(send, "Share TapLink demo"))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Direct launch avoids package-visibility queries; missing handlers are handled below.
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) { false }
    catch (_: SecurityException) { false }

    internal fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
