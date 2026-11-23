package com.taplink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taplink.app.R
import com.taplink.app.domain.AppPreferences
import com.taplink.app.domain.Card
import com.taplink.app.domain.Money
import kotlinx.coroutines.launch

/**
 * Swipeable card stack.
 *
 * Swiping and the dropdown below it are the same action: the dropdown exists because a swipe is not
 * discoverable for every user (and not available to TalkBack without the pager semantics). Selecting
 * a page writes the choice back to preferences, so the widget and next launch agree.
 */
@Composable
fun CardPager(
    cards: List<Card>,
    selected: Card,
    prefs: AppPreferences,
    busy: Boolean,
    onSelect: (Card) -> Unit,
    onToggleBalance: () -> Unit
) {
    val initial = cards.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial) { cards.size }
    val scope = rememberCoroutineScope()
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    val pageHeight = (206 * fontScale).dp

    // The dropdown or a restore can change the selection; keep the pager in step without looping.
    LaunchedEffect(selected.id, cards.size) {
        val index = cards.indexOfFirst { it.id == selected.id }
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }
    LaunchedEffect(pagerState.settledPage, cards.size) {
        cards.getOrNull(pagerState.settledPage)?.let { page -> if (page.id != selected.id) onSelect(page) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(pageHeight),
            pageSpacing = 12.dp,
            userScrollEnabled = !busy,
            pageContent = { index -> CardFace(cards[index], prefs, busy, onToggleBalance) }
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (cards.size > 1) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                    enabled = !busy && pagerState.currentPage > 0
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.home_card_previous)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    cards.indices.forEach { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            Modifier
                                .size(if (active) 10.dp else 8.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(cards.lastIndex)) } },
                    enabled = !busy && pagerState.currentPage < cards.lastIndex
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.home_card_next)) }
            }
            Spacer(Modifier.width(10.dp))
            // Enclosed rather than loose text, so the position reads as part of the control row.
            Pill(stringResource(R.string.home_card_position, pagerState.currentPage + 1, cards.size))
        }
        if (cards.size > 1) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Pill(stringResource(R.string.home_card_swipe_hint), icon = Icons.Outlined.SwapHoriz)
            }
        }
    }
}

@Composable
private fun CardFace(card: Card, prefs: AppPreferences, busy: Boolean, onToggleBalance: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFF1457B5), Color(0xFF087F91))),
                RoundedCornerShape(26.dp)
            )
            .padding(24.dp)
            .semantics { contentDescription = "${card.name}, ${card.maskedNumber}" }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Contactless, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text("TapLink", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
                    Text(
                        stringResource(if (card.isFrozen) R.string.ui_frozen_2 else R.string.ui_demo_card),
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Column {
                Text(stringResource(R.string.ui_available_balance_hkd), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (prefs.balanceVisible) Money.format(card.balanceCents) else "HK$ ••••",
                        Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    IconButton(onClick = onToggleBalance, enabled = !busy) {
                        Icon(
                            if (prefs.balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            stringResource(if (prefs.balanceVisible) R.string.ui_hide_balance else R.string.ui_show_balance),
                            tint = Color.White
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.name, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(card.maskedNumber, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
