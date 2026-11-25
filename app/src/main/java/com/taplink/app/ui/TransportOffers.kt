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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OffersScreen(vm: WalletViewModel) {
    val offers by vm.offers.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    // 0 = all, 1 = saved, 2 = usable now. An index survives recreation without a custom Saver.
    var filter by rememberSaveable { mutableStateOf(0) }
    val confirmId by vm.offerDialog.collectAsStateWithLifecycle()
    var detailId by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis()
    ScreenColumn {
        PageHeading(
            stringResource(R.string.ui_offers_rewards),
            stringResource(R.string.offer_demo_heading),
            stringResource(R.string.offer_demo_intro)
        )
        // Wraps instead of squeezing: at 150% text the third chip used to be pressed into a
        // one-character-wide column.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == 0, { filter = 0 }, label = { Text(stringResource(R.string.ui_all_offers)) })
            FilterChip(filter == 1, { filter = 1 }, label = { Text(stringResource(R.string.ui_saved)) }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp)) })
            // The one list a demo needs: coupons that can reduce the next payment.
            FilterChip(filter == 2, { filter = 2 }, label = { Text(stringResource(R.string.coupon_filter_usable)) }, leadingIcon = { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)) })
        }
        val visible = offers
            .filter { offer ->
                when (filter) {
                    1 -> offer.isFavorite
                    2 -> offer.status(now) == CouponStatus.USABLE
                    else -> true
                }
            }
            // Coupons that can still be spent come first; an expired or spent one is kept for
            // reference but never leads the list, because there is nothing to do with it.
            .sortedWith(compareBy({ statusRank(it.status(now)) }, { it.expiresAt }))
        if (visible.isEmpty()) {
            when (filter) {
                2 -> EmptyState(stringResource(R.string.coupon_empty_usable), stringResource(R.string.coupon_empty_usable_body), Icons.Outlined.LocalOffer)
                else -> EmptyState(stringResource(R.string.ui_no_saved_offers), stringResource(R.string.ui_tap_the_heart_on_an_offer_to_keep_it_here), Icons.Outlined.LocalOffer)
            }
        }
        visible.forEach { offer ->
            val status = offer.status(now)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(merchants.find { it.id == offer.merchantId }?.name ?: stringResource(R.string.ui_taplink_demo_merchant), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(offerDisplayTitle(offer), style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton({ vm.toggleOfferFavorite(offer) }, enabled = !busy) { Icon(if (offer.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (offer.isFavorite) stringResource(R.string.ui_remove_saved_offer) else stringResource(R.string.ui_save_offer), tint = MaterialTheme.colorScheme.primary) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(couponValueLabel(offer), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    CouponStatusPill(status)
                }
                couponConditionLabel(offer)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    stringResource(
                        if (status == CouponStatus.EXPIRED) R.string.ui_demo_offer_expired_on else R.string.ui_demo_offer_expires_on,
                        shortDate(offer.expiresAt)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton({ detailId = if (detailId == offer.id) "" else offer.id }) { Text(if (detailId == offer.id) stringResource(R.string.ui_hide_terms) else stringResource(R.string.ui_view_terms)) }
                if (detailId == offer.id) {
                    Text(stringResource(R.string.offer_demo_terms), style = MaterialTheme.typography.bodySmall)
                }
                when (status) {
                    CouponStatus.USABLE -> {
                        Notice(stringResource(R.string.ui_redeemed_demo_coupon, offer.couponCode ?: stringResource(R.string.ui_coupon_saved)))
                        Text(stringResource(R.string.ui_redeemed_at_no_value, dateTime(offer.redeemedAt ?: now)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryButton(stringResource(R.string.coupon_use), !busy) { vm.useCoupon(offer) }
                    }
                    CouponStatus.USED -> Text(
                        listOfNotNull(
                            offer.couponCode?.let { stringResource(R.string.ui_redeemed_demo_coupon, it) },
                            offer.usedAt?.let { stringResource(R.string.coupon_used_on, dateTime(it)) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Expired coupons keep their place in the list, greyed out, so a user can see why
                    // a coupon no longer reduces anything instead of it silently disappearing.
                    CouponStatus.EXPIRED, CouponStatus.UNREDEEMED -> PrimaryButton(
                        if (status == CouponStatus.EXPIRED) stringResource(R.string.ui_offer_expired) else stringResource(R.string.ui_redeem_demo_coupon),
                        status == CouponStatus.UNREDEEMED && !busy
                    ) { vm.openOfferDialog(offer.id) }
                }
            }
        }
    }
    val confirm = offers.find { it.id == confirmId }
    if (confirm != null) {
        // The dialog window can use the system locale. Resolve copy in the wallet locale first.
        val title = stringResource(R.string.ui_redeem_this_demo_offer)
        val offerTitle = offerDisplayTitle(confirm)
        val terms = stringResource(R.string.offer_demo_terms)
        val confirmText = stringResource(if (busy) R.string.ui_redeeming else R.string.ui_redeem)
        val cancelText = stringResource(R.string.account_cancel)
        AlertDialog(
            onDismissRequest = vm::dismissOfferDialog,
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(offerTitle)
                    Text(couponValueLabel(confirm), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    couponConditionLabel(confirm)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(terms)
                }
            },
            confirmButton = { TextButton(vm::submitOfferDialog, enabled = !busy) { Text(confirmText) } },
            dismissButton = { TextButton(vm::dismissOfferDialog, enabled = !busy) { Text(cancelText) } }
        )
    }
}

/** Sort weight: a coupon that can still be spent leads the list, a dead one trails it. */
private fun statusRank(status: CouponStatus): Int = when (status) {
    CouponStatus.USABLE -> 0
    CouponStatus.UNREDEEMED -> 1
    CouponStatus.USED -> 2
    CouponStatus.EXPIRED -> 3
}

/** The three states a coupon can be in, as a chip on its card. */
@Composable
private fun CouponStatusPill(status: CouponStatus) {    val label = stringResource(
        when (status) {
            CouponStatus.USABLE -> R.string.coupon_status_usable
            CouponStatus.USED -> R.string.coupon_status_used
            CouponStatus.EXPIRED -> R.string.coupon_status_expired
            CouponStatus.UNREDEEMED -> R.string.coupon_status_unredeemed
        }
    )
    val container = when (status) {
        CouponStatus.USABLE -> MaterialTheme.colorScheme.secondaryContainer
        CouponStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        CouponStatus.USABLE -> MaterialTheme.colorScheme.onSecondaryContainer
        CouponStatus.EXPIRED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(label, container = container, content = content)
}
