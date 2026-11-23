package com.taplink.app.domain

/**
 * Support channels shown in the app. The number is the one supplied for this project; the app is a
 * teaching prototype and is not an official Octopus or government service, which the contact screen
 * states explicitly.
 *
 * The sign-language video slot is a real player fed by `assets/sign_language`. The prototype ships
 * without footage, so the screen falls back to a full written transcript instead of pretending a
 * video exists.
 */
object SupportContacts {
    const val WHATSAPP_DISPLAY = "2520 1314"
    const val WHATSAPP_E164 = "85225201314"
    const val HOTLINE_DISPLAY = "2520 1314"
    const val HOTLINE_DIAL = "+85225201314"
    const val EMAIL = "support@taplink.example"

    /** Chapters are ordered by how often a first-time user needs them. */
    val SIGN_CHAPTERS = listOf("topup", "pay", "pass", "password")
}
