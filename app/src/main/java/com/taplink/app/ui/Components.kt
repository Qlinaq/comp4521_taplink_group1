package com.taplink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taplink.app.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val HongKong: ZoneId = ZoneId.of("Asia/Hong_Kong")
fun dateTime(epoch: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH).withZone(HongKong).format(Instant.ofEpochMilli(epoch))
fun shortDate(epoch: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(HongKong).format(Instant.ofEpochMilli(epoch))
fun Enum<*>.label(): String = name.lowercase(Locale.ENGLISH).split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
fun amountInput(cents: Long) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
@Composable
fun PageHeading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
@Composable
fun Notice(text: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, Modifier.size(20.dp), tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
@Composable
fun EmptyState(title: String, description: String, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled && !busy, shape = RoundedCornerShape(15.dp)) {
        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        Text(if (busy) "Saving…" else text)
    }
}
@Composable
fun DetailRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium)
    }
}
@Composable
fun NavigationRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}
@Composable
fun <T> ChoiceField(label: String, selected: T, choices: List<Pair<T, String>>, onSelect: (T) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) {
                Text(choices.firstOrNull { it.first == selected }?.second ?: "Choose…", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                choices.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false }) }
            }
        }
    }
}
@Composable
fun TransactionRow(transaction: WalletTransaction, onClick: () -> Unit) {
    val tx = transaction
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(if (tx.isDebit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(when { !tx.isDebit -> Icons.Outlined.Add; tx.category == Category.TRANSPORT -> Icons.Outlined.DirectionsTransit; tx.category == Category.FOOD -> Icons.Outlined.Restaurant; else -> Icons.Outlined.ShoppingBag }, null, Modifier.size(21.dp), tint = if (tx.isDebit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f)) {
                Text(tx.merchantName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tx.type.label()} · ${shortDate(tx.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${if (tx.isDebit) "−" else "+"}${Money.format(tx.amountCents)}", style = MaterialTheme.typography.labelLarge, color = if (tx.isDebit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}
