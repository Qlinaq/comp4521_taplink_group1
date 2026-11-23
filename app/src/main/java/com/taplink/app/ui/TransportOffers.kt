package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
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
        PageHeading( stringResource(R.string.ui_transport) , stringResource(R.string.ui_more_journeys_one_card) , stringResource(R.string.ui_selected_card_note, card?.name ?: stringResource(R.string.ui_none)) )
        Panel {
            Icon(Icons.Outlined.DirectionsTransit, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.ui_taplink_monthly_pass), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_one_calendar_month_price, Money.format(PassCatalog.MONTHLY_PASS_PRICE_CENTS)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_a_simulated_pass_for_the_taplink_demo_networ), style = MaterialTheme.typography.bodyMedium)
            if (active != null) {
                Notice( stringResource(R.string.ui_your_demo_pass_is_active) )
                DetailRow( stringResource(R.string.ui_valid_from) , dateTime(active.validFrom))
                DetailRow( stringResource(R.string.ui_valid_until) , dateTime(active.validUntil), true)
            } else PrimaryButton( stringResource(R.string.ui_review_pass_purchase) , card != null && !busy) { vm.startOperation("PASS") }
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_fictional_transport_subsidy) )
            ChoiceField( stringResource(R.string.ui_completed_month) , month.toString(), (1L..12L).map { YearMonth.now(HongKong).minusMonths(it).toString().let { m -> m to monthLabel(m) } }, { vm.set("transportMonth", it) })
            DetailRow( stringResource(R.string.ui_transport_spending) , Money.format(spend))
            DetailRow( stringResource(R.string.ui_demo_credit) , Money.format(subsidy), true)
            Text(stringResource(R.string.ui_demo_rule_20_of_monthly_transport_spending_a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(if (claimed) stringResource(R.string.ui_already_claimed) else stringResource(R.string.ui_review_demo_claim), !claimed && subsidy > 0 && card != null && !busy) { vm.startOperation("SUBSIDY", month = month.toString(), amountCents = subsidy) }
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_claim_history) )
            val history = claims.filter { it.cardId == card?.id }
            if (history.isEmpty()) EmptyState( stringResource(R.string.ui_no_claims_yet) , stringResource(R.string.ui_eligible_simulated_claims_will_appear_here) )
            history.forEach { DetailRow(monthLabel(it.month), "${Money.format(it.amountCents)} · ${shortDate(it.claimedAt)}") }
        }
        val previous = passes.filter { it.cardId == card?.id && !it.isActive() }
        if (previous.isNotEmpty()) Panel { SectionHeading( stringResource(R.string.ui_past_demo_passes) ); previous.forEach { DetailRow(it.name, stringResource(R.string.ui_expired_on, shortDate(it.validUntil))) } }
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
        PageHeading( stringResource(R.string.ui_offers_rewards) , stringResource(R.string.ui_small_treats_on_us) , stringResource(R.string.ui_discover_sample_offers_from_taplink_demo_mer) )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!favorites, { favorites = false }, label = { Text(stringResource(R.string.ui_all_offers)) })
            FilterChip(favorites, { favorites = true }, label = { Text(stringResource(R.string.ui_saved)) }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp)) })
        }
        val visible = offers.filter { !favorites || it.isFavorite }
        if (visible.isEmpty()) EmptyState( stringResource(R.string.ui_no_saved_offers) , stringResource(R.string.ui_tap_the_heart_on_an_offer_to_keep_it_here) , Icons.Outlined.LocalOffer)
        visible.forEach { offer ->
            val expired = offer.expiresAt <= System.currentTimeMillis()
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(merchants.find { it.id == offer.merchantId }?.name ?: stringResource(R.string.ui_taplink_demo_merchant), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(offer.title, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton({ vm.act(action = { vm.repository.setOfferFavorite(offer.id, !offer.isFavorite) }) }, enabled = !busy) { Icon(if (offer.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (offer.isFavorite) stringResource(R.string.ui_remove_saved_offer) else stringResource(R.string.ui_save_offer), tint = MaterialTheme.colorScheme.primary) }
                }
                Text(offer.description, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (expired) R.string.ui_demo_offer_expired_on else R.string.ui_demo_offer_expires_on, shortDate(offer.expiresAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ detailId = if (detailId == offer.id) "" else offer.id }) { Text(if (detailId == offer.id) stringResource(R.string.ui_hide_terms) else stringResource(R.string.ui_view_terms)) }
                if (detailId == offer.id) Text(offer.terms, style = MaterialTheme.typography.bodySmall)
                if (offer.redeemedAt != null) {
                    Notice(stringResource(R.string.ui_redeemed_demo_coupon, offer.couponCode ?: stringResource(R.string.ui_coupon_saved)))
                    Text(stringResource(R.string.ui_redeemed_at_no_value, dateTime(offer.redeemedAt)), style = MaterialTheme.typography.bodySmall)
                } else PrimaryButton(if (expired) stringResource(R.string.ui_offer_expired) else stringResource(R.string.ui_redeem_demo_coupon), !expired && !busy) { vm.openOfferDialog(offer.id) }
            }
        }
    }
    val confirm = offers.find { it.id == confirmId }
    if (confirm != null) AlertDialog(onDismissRequest = vm::dismissOfferDialog, title = { Text(stringResource(R.string.ui_redeem_this_demo_offer)) }, text = { Text(stringResource(R.string.ui_offer_dialog_body, confirm.title, confirm.terms)) }, confirmButton = { TextButton(vm::submitOfferDialog, enabled = !busy) { Text(if (busy) stringResource(R.string.ui_redeeming) else stringResource(R.string.ui_redeem)) } }, dismissButton = { TextButton(vm::dismissOfferDialog, enabled = !busy) { Text(stringResource(R.string.account_cancel)) } })
}
