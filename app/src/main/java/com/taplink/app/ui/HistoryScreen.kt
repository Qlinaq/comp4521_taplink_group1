package com.taplink.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.R
import com.taplink.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: WalletViewModel) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val query by vm.text("historyQuery").collectAsStateWithLifecycle()
    val cardId by vm.text("historyCard").collectAsStateWithLifecycle()
    val category by vm.text("historyCategory").collectAsStateWithLifecycle()
    val type by vm.text("historyType").collectAsStateWithLifecycle()
    val from by vm.text("historyFrom").collectAsStateWithLifecycle()
    val to by vm.text("historyTo").collectAsStateWithLifecycle()
    val monthString by vm.text("historyMonth", YearMonth.now(HongKong).toString()).collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var dateField by rememberSaveable { mutableStateOf("") }
    val month = runCatching { YearMonth.parse(monthString) }.getOrDefault(YearMonth.now(HongKong))
    val start = from.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).atStartOfDay(HongKong).toInstant().toEpochMilli() }
    val end = to.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(HongKong).toInstant().toEpochMilli() }
    val invalidDates = start != null && end != null && start >= end
    val filter = TransactionFilter(cardId.ifBlank { null }, Category.entries.find { it.name == category }, TransactionType.entries.find { it.name == type }, start, end, query)
    val filtered = remember(transactions, filter, invalidDates) { if (invalidDates) emptyList() else WalletAnalytics.filter(transactions, filter) }
    val summary = remember(filtered, month) { WalletAnalytics.monthlySpending(filtered, month) }
    val count = listOf(cardId, category, type, from, to).count { it.isNotBlank() }
    // The chart is a framework View, so both its wording and its accessible description are
    // resolved here where the localized resources are available.
    val chartMonth = stringResource(R.string.ui_chart_this_month)
    val chartEmpty = stringResource(R.string.ui_chart_no_spending)
    val chartNote = stringResource(R.string.ui_chart_debits_only)
    val categoryLabels = Category.entries.associateWith { it.label() }
    val chartDescription = stringResource(
        R.string.ui_chart_description,
        Money.format(summary.totalCents),
        categoryLabels.keys.filter { (summary.byCategory[it] ?: 0L) > 0 }
            .joinToString { "${categoryLabels[it]} ${Money.format(summary.byCategory[it] ?: 0)}" }
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Same hero heading as Home: this is a tab root, not a drill-down screen.
            item { PageHeading( stringResource(R.string.ui_activity_insights) , stringResource(R.string.ui_know_where_it_goes) , stringResource(R.string.ui_every_tap_top_up_and_little_moment_in_betwee) , hero = true ) }
            item { Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.set("historyMonth", month.minusMonths(1).toString()) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.ui_previous_month)) }
                    Text(month.format(DateTimeFormatter.ofPattern(monthPattern(LocalAppLocale.current), LocalAppLocale.current)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton({ vm.set("historyMonth", month.plusMonths(1).toString()) }, enabled = month < YearMonth.now(HongKong)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.ui_next_month)) }
                }
                Text(Money.format(summary.totalCents), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ui_monthly_spending_matches_active_filters), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                AndroidView(factory = { SpendingChartView(it) }, update = { it.setCopy(chartMonth, chartEmpty, chartNote, categoryLabels, chartDescription); it.setData(summary, foreground, secondary) }, modifier = Modifier.fillMaxWidth().height((202 + summary.byCategory.count { it.value > 0 } * 30).dp))
                Text(stringResource(R.string.ui_payments_and_pass_purchases_only_top_ups_aut), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { OutlinedTextField(query, { vm.set("historyQuery", it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_search_merchant_or_source)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ vm.set("historyQuery", "") }) { Icon(Icons.Outlined.Close, stringResource(R.string.ui_clear_search)) } }, singleLine = true) }
            item { Panel {
                SectionHeading(if (count == 0) stringResource(R.string.ui_filter_transactions) else stringResource(R.string.ui_filters_active, count), if (expanded) stringResource(R.string.ui_collapse) else stringResource(R.string.auth_password_show)) { expanded = !expanded }
                if (expanded) {
                    ChoiceField( stringResource(R.string.ui_card) , cardId, listOf("" to stringResource(R.string.ui_all_cards)) + cards.map { it.id to it.name }, { vm.set("historyCard", it) })
                    ChoiceField( stringResource(R.string.ui_category) , category, listOf("" to stringResource(R.string.ui_all_categories)) + Category.entries.map { it.name to it.label() }, { vm.set("historyCategory", it) })
                    ChoiceField( stringResource(R.string.ui_transaction_type) , type, listOf("" to stringResource(R.string.ui_all_types)) + TransactionType.entries.map { it.name to it.label() }, { vm.set("historyType", it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({ dateField = "historyFrom" }, Modifier.weight(1f)) { Text(from.ifBlank { stringResource(R.string.ui_start_date) }) }
                        OutlinedButton({ dateField = "historyTo" }, Modifier.weight(1f)) { Text(to.ifBlank { stringResource(R.string.ui_end_date) }) }
                    }
                    if (invalidDates) Notice( stringResource(R.string.ui_end_date_must_be_on_or_after_the_start_date) , true)
                    Text(stringResource(R.string.ui_date_range_includes_both_selected_days_in_ho), style = MaterialTheme.typography.bodySmall)
                    TextButton({ listOf("historyCard", "historyCategory", "historyType", "historyFrom", "historyTo", "historyQuery").forEach { vm.set(it, "") } }) { Text(stringResource(R.string.ui_clear_all_filters_and_search)) }
                }
            } }
            item { SectionHeading(stringResource(if (filtered.size == 1) R.string.ui_transaction_count_one else R.string.ui_transaction_count_many, filtered.size)) }
            if (filtered.isEmpty()) item { Panel { EmptyState( stringResource(R.string.ui_no_matching_activity) , stringResource(R.string.ui_try_a_different_date_merchant_or_filter_your) , Icons.Outlined.SearchOff) } }
            items(filtered, key = { it.id }) { tx -> TransactionRow(tx) { vm.openTransaction(tx) } }
        }
    }
    if (dateField.isNotBlank()) {
        val initial = vm.value(dateField).takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { dateField = "" }, confirmButton = { TextButton({ picker.selectedDateMillis?.let { vm.set(dateField, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; dateField = "" }) { Text(stringResource(R.string.ui_apply)) } }, dismissButton = { TextButton({ dateField = "" }) { Text(stringResource(R.string.account_cancel)) } }) { DatePicker(picker) }
    }
}
