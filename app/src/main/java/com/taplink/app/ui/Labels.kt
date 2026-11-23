package com.taplink.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.taplink.app.R
import com.taplink.app.domain.Category
import com.taplink.app.domain.LocationType
import com.taplink.app.domain.MessageType
import com.taplink.app.domain.ThemeMode
import com.taplink.app.domain.TransactionType

/**
 * Labels for routes and enum values. These are not user data: records keep the wording they were
 * created with, but navigation, filters and category names follow the interface language.
 */
@StringRes
private fun labelRes(value: Enum<*>): Int = when (value) {
    Category.TRANSPORT -> R.string.label_category_transport
    Category.FOOD -> R.string.label_category_food
    Category.SHOPPING -> R.string.label_category_shopping
    Category.SERVICES -> R.string.label_category_services
    Category.OTHER -> R.string.label_category_other

    TransactionType.PAYMENT -> R.string.label_type_payment
    TransactionType.TOP_UP -> R.string.label_type_top_up
    TransactionType.AUTO_RELOAD -> R.string.label_type_auto_reload
    TransactionType.PASS_PURCHASE -> R.string.label_type_pass_purchase
    TransactionType.SUBSIDY_CREDIT -> R.string.label_type_subsidy_credit

    ThemeMode.SYSTEM -> R.string.label_theme_system
    ThemeMode.LIGHT -> R.string.label_theme_light
    ThemeMode.DARK -> R.string.label_theme_dark

    MessageType.INFO -> R.string.label_message_info
    MessageType.LOW_BALANCE -> R.string.label_message_low_balance
    MessageType.PASS_EXPIRY -> R.string.label_message_pass_expiry
    MessageType.OFFER_EXPIRY -> R.string.label_message_offer_expiry
    MessageType.TRANSACTION -> R.string.label_message_transaction
    MessageType.SERVICE -> R.string.label_message_service

    LocationType.TOP_UP -> R.string.label_location_top_up
    LocationType.MERCHANT -> R.string.label_location_merchant
    LocationType.TRANSPORT -> R.string.label_location_transport

    else -> R.string.label_category_other
}

/** Replaces the old English-only enum label while keeping the same call sites. */
@Composable
fun Enum<*>.label(): String = stringResource(labelRes(this))

/** Screen titles. Route names are internal keys and never shown as-is. */
@Composable
fun routeLabel(route: String): String = stringResource(
    when (route) {
        "Home" -> R.string.label_home
        "Activity" -> R.string.label_activity
        "Explore" -> R.string.label_explore
        "More" -> R.string.label_more
        "Form" -> R.string.label_transaction_form
        "Review" -> R.string.label_review
        "Receipt" -> R.string.label_receipt
        "Transaction" -> R.string.ui_activity_details
        "Messages" -> R.string.label_messages
        "Transport" -> R.string.label_transport
        "Offers" -> R.string.label_offers
        "Settings" -> R.string.label_settings
        "Account" -> R.string.account_heading
        "Notifications" -> R.string.label_notification_settings
        "Accessibility" -> R.string.a11y_appbar
        "Contact" -> R.string.label_contact_us
        "Support" -> R.string.label_support_chat
        "SignLanguage" -> R.string.label_sign_language
        else -> R.string.app_name
    }
)
