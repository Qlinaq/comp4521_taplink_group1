package com.taplink.app.domain

/**
 * Owner key stored on every wallet row.
 *
 * One database file holds several wallets: the demo wallet used without an account, and one per
 * signed-in account. Rows are never shared between owners, so signing in as a different account
 * cannot show (or spend) another account's balance.
 */
object WalletOwner {
    const val GUEST = "guest"
}
