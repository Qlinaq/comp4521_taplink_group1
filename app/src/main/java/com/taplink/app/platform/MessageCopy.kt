package com.taplink.app.platform

import android.content.Context
import android.content.res.Resources
import com.taplink.app.R
import com.taplink.app.domain.MessageKind
import com.taplink.app.domain.Money
import com.taplink.app.domain.WalletMessage

/**
 * Renders a stored message in the interface language.
 *
 * Rows written before version 4 have no [MessageKind] and only their stored English text, so the
 * fallback is deliberate rather than an oversight. Expiry messages carry a date in their stored
 * body, which is why only their title is re-rendered; the body is kept as written.
 */
object MessageCopy {
    fun title(resources: Resources, message: WalletMessage): String = when (message.kind) {
        MessageKind.WELCOME -> resources.getString(R.string.msg_welcome_title)
        MessageKind.LOW_BALANCE -> resources.getString(R.string.msg_low_balance_title)
        MessageKind.TOP_UP -> resources.getString(R.string.msg_top_up_title)
        MessageKind.PAYMENT -> resources.getString(R.string.msg_payment_title)
        MessageKind.PASS_PURCHASE -> resources.getString(R.string.msg_pass_purchase_title)
        MessageKind.PASS_EXPIRY -> resources.getString(R.string.msg_pass_expiry_title)
        MessageKind.OFFER_EXPIRY -> resources.getString(R.string.msg_offer_expiry_title)
        MessageKind.SERVICE -> resources.getString(R.string.msg_service_title)
        null -> message.title
    }

    fun body(resources: Resources, message: WalletMessage): String {
        val amount = message.amountCents?.let(Money::format)
        return when (message.kind) {
            MessageKind.WELCOME -> resources.getString(R.string.msg_welcome_body)
            MessageKind.LOW_BALANCE -> if (amount != null) {
                resources.getString(R.string.msg_low_balance_body, amount)
            } else message.body
            MessageKind.TOP_UP -> if (amount != null) {
                resources.getString(R.string.msg_top_up_body, amount)
            } else message.body
            MessageKind.PAYMENT -> if (amount != null) {
                resources.getString(R.string.msg_payment_body, amount)
            } else message.body
            MessageKind.PASS_PURCHASE -> if (amount != null) {
                resources.getString(R.string.msg_pass_purchase_body, amount)
            } else message.body
            // The stored body contains the computed expiry date; translating it would drop that.
            MessageKind.PASS_EXPIRY, MessageKind.OFFER_EXPIRY, MessageKind.SERVICE, null -> message.body
        }
    }

    /** Notification copy, resolved against a context that already carries the chosen locale. */
    fun title(context: Context, message: WalletMessage): String = title(context.resources, message)
    fun body(context: Context, message: WalletMessage): String = body(context.resources, message)
}
