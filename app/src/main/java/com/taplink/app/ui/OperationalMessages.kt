package com.taplink.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.taplink.app.R

/**
 * The wallet view model keeps its operational messages as stable English text so they survive the
 * saved-state round trip and stay unchanged by a language switch mid-flow. This table maps each of
 * those exact strings to a resource, which is what makes them localisable.
 *
 * A production refactor would have the view model emit the resource id directly; the mapping is
 * kept isolated here so that change is mechanical.
 */
private val OPERATIONAL_MESSAGES: Map<String, Int> = mapOf(
    "Your wallet could not be loaded. Please try again." to R.string.msg_wallet_load_failed,
    "This receipt could not be found. Your transaction history is still available." to R.string.msg_receipt_missing,
    "The receipt could not be loaded. Please try again." to R.string.msg_receipt_load_failed,
    "Resolve your previous request before starting another transaction. Its details and request ID have been kept." to R.string.msg_resolve_previous,
    "Choose a card to continue." to R.string.msg_choose_card,
    "This card is frozen. Unfreeze it from Home first." to R.string.msg_card_frozen_home,
    "Enter a positive HKD amount with at most two decimal places." to R.string.msg_enter_amount,
    "Choose a merchant to continue." to R.string.msg_choose_merchant,
    "This card has insufficient balance. Top up or choose another card." to R.string.msg_insufficient_balance,
    "Please start a new transaction." to R.string.msg_start_new,
    "The outcome is not confirmed. Check for a saved receipt or retry this same request. Your original details and request ID are retained." to R.string.msg_outcome_unconfirmed,
    "That change could not be saved. Please try again." to R.string.msg_save_failed,
    "Choose a district, or use your approximate location." to R.string.msg_choose_district,
    "Approximate location only. Distances are straight-line estimates." to R.string.msg_location_approx,
    "Finding an approximate location…" to R.string.msg_locating,
    "Location is unavailable. Choose a district to continue." to R.string.msg_location_unavailable_choose,
    "Location request stopped. Choose a district or try again." to R.string.msg_location_stopped,
    "Location unavailable. Select a district above." to R.string.msg_location_unavailable_select,
    "Manual location mode. Showing the selected district above." to R.string.msg_manual_location,
    "Approximate device location. Straight-line distances to demo locations are estimates." to R.string.msg_location_device_approx,
    "Location request paused. Choose a district or try again." to R.string.msg_location_paused,
    "Location permission is off; showing your selected district." to R.string.msg_location_permission_off,
    "Location is unavailable; showing your selected district." to R.string.msg_location_unavailable_district,
    "Device location is off; showing your selected district." to R.string.msg_location_device_off,
    "Approximate location is unavailable; showing your selected district." to R.string.msg_location_approx_unavailable,
    "No current location fix; showing your selected district." to R.string.msg_location_no_fix,
    "Location permission changed; showing your selected district." to R.string.msg_location_permission_changed,
    "Card created" to R.string.msg_card_created,
    "Card renamed" to R.string.msg_card_renamed,
    "Card updated" to R.string.msg_card_updated,
    "Demo coupon redeemed" to R.string.msg_coupon_redeemed
)

@Composable
fun localizedMessage(text: String?): String? {
    if (text == null) return null
    val id = OPERATIONAL_MESSAGES[text] ?: return text
    return stringResource(id)
}
