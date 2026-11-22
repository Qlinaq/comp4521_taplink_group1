package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.domain.*
import java.time.YearMonth

@Composable
fun TransportScreen(vm: WalletViewModel) {
    val card by vm.selectedCard.collectAsStateWithLifecycle()
    val passes by vm.passes.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val claims by vm.claims.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val monthString by vm.text("transportMonth", YearMonth.now(HongKong).minusMonths(1).toString()).collectAsStateWithLifecycle()
    val month = runCatching { YearMonth.parse(monthString) }.getOrDefault(YearMonth.now(HongKong).minusMonths(1))
    val active = passes.filter { it.cardId == card?.id }.firstOrNull { it.isActive() }
    val spend = WalletAnalytics.monthlySpending(transactions.filter { it.cardId == card?.id && it.category == Category.TRANSPORT }, month).totalCents
    val subsidy = WalletAnalytics.fictionalSubsidy(spend)
    val claimed = claims.any { it.cardId == card?.id && it.month == month.toString() }
    ScreenColumn {
        PageHeading("Transport", "More journeys. One card.", "Selected card: ${card?.name ?: "None"}. Change it from Home.")
        Panel {
            Icon(Icons.Outlined.DirectionsTransit, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("TapLink monthly pass", style = MaterialTheme.typography.titleLarge)
            Text("One calendar month · ${Money.format(PassCatalog.MONTHLY_PASS_PRICE_CENTS)}", style = MaterialTheme.typography.titleMedium)
            Text("A simulated pass for the TapLink demo network. It is not a valid ticket for any real transport service.", style = MaterialTheme.typography.bodyMedium)
            if (active != null) {
                Notice("Your demo pass is active.")
                DetailRow("Valid from", dateTime(active.validFrom))
                DetailRow("Valid until", dateTime(active.validUntil), true)
            } else PrimaryButton("Review pass purchase", card != null && !busy) { vm.startOperation("PASS") }
        }
        Panel {
            SectionHeading("Fictional transport subsidy")
            ChoiceField("Completed month", month.toString(), (1L..12L).map { YearMonth.now(HongKong).minusMonths(it).toString().let { m -> m to m } }, { vm.set("transportMonth", it) })
            DetailRow("Transport spending", Money.format(spend))
            DetailRow("Demo credit", Money.format(subsidy), true)
            Text("Demo rule: 20% of monthly transport spending above HK$200, capped at HK$100 and rounded down to cents. One claim per card per completed month. This is fictional and implies no official entitlement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(if (claimed) "Already claimed" else "Review demo claim", !claimed && subsidy > 0 && card != null && !busy) { vm.startOperation("SUBSIDY", month = month.toString(), amountCents = subsidy) }
        }
        Panel {
            SectionHeading("Claim history")
            val history = claims.filter { it.cardId == card?.id }
            if (history.isEmpty()) EmptyState("No claims yet", "Eligible simulated claims will appear here.")
            history.forEach { DetailRow(it.month, "${Money.format(it.amountCents)} · ${shortDate(it.claimedAt)}") }
        }
        val previous = passes.filter { it.cardId == card?.id && !it.isActive() }
        if (previous.isNotEmpty()) Panel { SectionHeading("Past demo passes"); previous.forEach { DetailRow(it.name, "Expired ${shortDate(it.validUntil)}") } }
    }
}

@Composable
fun OffersScreen(vm: WalletViewModel) {
    val offers by vm.offers.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var favorites by rememberSaveable { mutableStateOf(false) }
    val confirmId by vm.offerDialog.collectAsStateWithLifecycle()
    var detailId by rememberSaveable { mutableStateOf("") }
    ScreenColumn {
        PageHeading("Offers & rewards", "Small treats, on us.", "Discover sample offers from TapLink demo merchants.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!favorites, { favorites = false }, label = { Text("All offers") })
            FilterChip(favorites, { favorites = true }, label = { Text("Saved") }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp)) })
        }
        val visible = offers.filter { !favorites || it.isFavorite }
        if (visible.isEmpty()) EmptyState("No saved offers", "Tap the heart on an offer to keep it here.", Icons.Outlined.LocalOffer)
        visible.forEach { offer ->
            val expired = offer.expiresAt <= System.currentTimeMillis()
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(merchants.find { it.id == offer.merchantId }?.name ?: "TapLink demo merchant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(offer.title, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton({ vm.act(action = { vm.repository.setOfferFavorite(offer.id, !offer.isFavorite) }) }, enabled = !busy) { Icon(if (offer.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (offer.isFavorite) "Remove saved offer" else "Save offer", tint = MaterialTheme.colorScheme.primary) }
                }
                Text(offer.description, style = MaterialTheme.typography.bodyMedium)
                Text("DEMO OFFER · ${if (expired) "Expired" else "Expires"} ${shortDate(offer.expiresAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ detailId = if (detailId == offer.id) "" else offer.id }) { Text(if (detailId == offer.id) "Hide terms" else "View terms") }
                if (detailId == offer.id) Text(offer.terms, style = MaterialTheme.typography.bodySmall)
                if (offer.redeemedAt != null) {
                    Notice("Redeemed · Demo coupon: ${offer.couponCode ?: "Saved"}")
                    Text("${dateTime(offer.redeemedAt)} HKT · This coupon has no real monetary value.", style = MaterialTheme.typography.bodySmall)
                } else PrimaryButton(if (expired) "Offer expired" else "Redeem demo coupon", !expired && !busy) { vm.openOfferDialog(offer.id) }
            }
        }
    }
    val confirm = offers.find { it.id == confirmId }
    if (confirm != null) AlertDialog(onDismissRequest = vm::dismissOfferDialog, title = { Text("Redeem this demo offer?") }, text = { Text("${confirm.title}\n\n${confirm.terms}\n\nYou can redeem this simulated coupon only once. It has no real monetary value.") }, confirmButton = { TextButton(vm::submitOfferDialog, enabled = !busy) { Text(if (busy) "Redeeming…" else "Redeem") } }, dismissButton = { TextButton(vm::dismissOfferDialog, enabled = !busy) { Text("Cancel") } })
}
