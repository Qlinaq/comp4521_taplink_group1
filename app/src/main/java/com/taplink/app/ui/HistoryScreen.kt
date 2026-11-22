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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PageHeading("Activity & insights", "Know where it goes.", "Every tap, top-up, and little moment in between.") }
            item { Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.set("historyMonth", month.minusMonths(1).toString()) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton({ vm.set("historyMonth", month.plusMonths(1).toString()) }, enabled = month < YearMonth.now(HongKong)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
                }
                Text(Money.format(summary.totalCents), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Monthly spending · matches active filters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
                val secondary = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                AndroidView(factory = { SpendingChartView(it) }, update = { it.setData(summary, foreground, secondary) }, modifier = Modifier.fillMaxWidth().height((202 + summary.byCategory.count { it.value > 0 } * 30).dp))
                Text("Payments and pass purchases only. Top-ups, automatic reloads and subsidy credits are excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { OutlinedTextField(query, { vm.set("historyQuery", it) }, Modifier.fillMaxWidth(), label = { Text("Search merchant or source") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ vm.set("historyQuery", "") }) { Icon(Icons.Outlined.Close, "Clear search") } }, singleLine = true) }
            item { Panel {
                SectionHeading(if (count == 0) "Filter transactions" else "Filters · $count active", if (expanded) "Collapse" else "Show") { expanded = !expanded }
                if (expanded) {
                    ChoiceField("Card", cardId, listOf("" to "All cards") + cards.map { it.id to it.name }, { vm.set("historyCard", it) })
                    ChoiceField("Category", category, listOf("" to "All categories") + Category.entries.map { it.name to it.label() }, { vm.set("historyCategory", it) })
                    ChoiceField("Transaction type", type, listOf("" to "All types") + TransactionType.entries.map { it.name to it.label() }, { vm.set("historyType", it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({ dateField = "historyFrom" }, Modifier.weight(1f)) { Text(from.ifBlank { "Start date" }) }
                        OutlinedButton({ dateField = "historyTo" }, Modifier.weight(1f)) { Text(to.ifBlank { "End date" }) }
                    }
                    if (invalidDates) Notice("End date must be on or after the start date.", true)
                    Text("Date range includes both selected days, in Hong Kong time.", style = MaterialTheme.typography.bodySmall)
                    TextButton({ listOf("historyCard", "historyCategory", "historyType", "historyFrom", "historyTo", "historyQuery").forEach { vm.set(it, "") } }) { Text("Clear all filters and search") }
                }
            } }
            item { SectionHeading("${filtered.size} ${if (filtered.size == 1) "transaction" else "transactions"}") }
            if (filtered.isEmpty()) item { Panel { EmptyState("No matching activity", "Try a different date, merchant, or filter. Your other transactions are still saved.", Icons.Outlined.SearchOff) } }
            items(filtered, key = { it.id }) { tx -> TransactionRow(tx) { vm.openTransaction(tx) } }
        }
    }
    if (dateField.isNotBlank()) {
        val initial = vm.value(dateField).takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { dateField = "" }, confirmButton = { TextButton({ picker.selectedDateMillis?.let { vm.set(dateField, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; dateField = "" }) { Text("Apply") } }, dismissButton = { TextButton({ dateField = "" }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}
