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

    /** Opens a web page such as the official sign-language resource. */
    fun openUrl(context: Context, url: String): Boolean {
        if (!url.startsWith("https://")) return false
        return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /**
     * WhatsApp deep link. `wa.me` opens the installed app when present and falls back to the
     * browser, which is why a missing app is not treated as a failure here.
     */
    fun openWhatsApp(context: Context, phoneE164: String, message: String): Boolean {
        val digits = phoneE164.filter(Char::isDigit)
        if (digits.length < 8) return false
        val uri = Uri.parse("https://wa.me/$digits").buildUpon()
            .appendQueryParameter("text", message)
            .build()
        return launch(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /** Pre-fills the dialer; it never places the call by itself. */
    fun dial(context: Context, phone: String): Boolean {
        val digits = phone.filter { it.isDigit() || it == '+' }
        if (digits.length < 8) return false
        return launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
    }

    /** Opens a mail composer; the message is written by the user, never sent silently. */
    fun email(context: Context, address: String, subject: String): Boolean {
        if (!address.contains('@')) return false
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        return launch(context, intent)
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
