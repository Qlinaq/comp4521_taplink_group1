package com.taplink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taplink.app.R
import com.taplink.app.domain.*
import com.taplink.app.ui.theme.LocalHighContrast
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val HongKong: ZoneId = ZoneId.of("Asia/Hong_Kong")

/**
 * Chinese dates read year first ("2026年11月15日"); recomposing "15 11月 2026" from a localized
 * month name looks like a translation glitch, so the pattern itself follows the language.
 */
private fun dayPattern(locale: Locale, withTime: Boolean): String = when (locale.language) {
    "zh" -> if (withTime) "yyyy年M月d日 · HH:mm" else "yyyy年M月d日"
    else -> if (withTime) "d MMM yyyy · HH:mm" else "d MMM yyyy"
}

fun monthPattern(locale: Locale): String = if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy"

/** Turns a stored `2026-10` month key into the reading order of the current language. */
@Composable
fun monthLabel(iso: String): String {
    val locale = LocalAppLocale.current
    return remember(iso, locale) {
        runCatching { YearMonth.parse(iso).format(DateTimeFormatter.ofPattern(monthPattern(locale), locale)) }.getOrDefault(iso)
    }
}

/** Dates follow the interface language; they read [LocalAppLocale] so a language switch redraws them. */
@Composable
fun dateTime(epoch: Long): String {
    val locale = LocalAppLocale.current
    return remember(epoch, locale) { DateTimeFormatter.ofPattern(dayPattern(locale, true), locale).withZone(HongKong).format(Instant.ofEpochMilli(epoch)) }
}
@Composable
fun shortDate(epoch: Long): String {
    val locale = LocalAppLocale.current
    return remember(epoch, locale) { DateTimeFormatter.ofPattern(dayPattern(locale, false), locale).withZone(HongKong).format(Instant.ofEpochMilli(epoch)) }
}
fun amountInput(cents: Long) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
/**
 * Page heading: the eyebrow is a pill rather than loose coloured text, and [hero] puts the whole
 * block on a tinted card. Home uses the hero variant so the first thing on screen is a surface, not
 * three lines of bare text; other screens keep the lighter form.
 */
@Composable
fun PageHeading(eyebrow: String, title: String, subtitle: String? = null, hero: Boolean = false) {
    // A page title is a headline, not a sentence, so a trailing full stop is dropped here rather
    // than in every string resource: one rule for all screens, and a new page cannot forget it.
    // Subheadings below keep their punctuation because they really are sentences.
    val headline = title.trimEnd('.', '。', '．')
    // Three distinct treatments rather than one size repeated: a tracked small-caps pill, a
    // semi-bold headline and a quieter supporting line. A public-service app needs a calm hierarchy,
    // not decoration.
    val content: @Composable ColumnScope.() -> Unit = {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        Text(
            headline,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!hero) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    } else {
        val shape = RoundedCornerShape(26.dp)
        val outline = if (LocalHighContrast.current) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, shape) else Modifier
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().then(outline)
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}
/** Small enclosed label: a chip rather than loose text on the page background. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(15.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    // In high contrast the palette alone is not always enough on an OLED screen, so surfaces gain
    // an explicit border and the boundary survives a colour-blind or glare-heavy environment.
    val outline = if (LocalHighContrast.current) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, shape) else Modifier
    Surface(modifier.fillMaxWidth().then(outline), shape = shape, color = MaterialTheme.colorScheme.surface) {
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
        Text(if (busy) stringResource(R.string.ui_saving) else text)
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
                Text(choices.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.ui_choose), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                choices.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false }) }
            }
        }
    }
}
/**
 * One activity row.
 *
 * With enlarged text the trailing amount squeezes the merchant name and the date into a
 * three-line column, so the amount drops onto its own line instead of fighting for width. This is
 * the same row at every scale; only the arrangement changes.
 */
@Composable
fun TransactionRow(transaction: WalletTransaction, onClick: () -> Unit) {
    val tx = transaction
    val amount = "${if (tx.isDebit) "−" else "+"}${Money.format(tx.amountCents)}"
    val amountColor = if (tx.isDebit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
    val stacked = LocalDensity.current.fontScale > 1.15f
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(if (tx.isDebit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(when { !tx.isDebit -> Icons.Outlined.Add; tx.category == Category.TRANSPORT -> Icons.Outlined.DirectionsTransit; tx.category == Category.FOOD -> Icons.Outlined.Restaurant; else -> Icons.Outlined.ShoppingBag }, null, Modifier.size(21.dp), tint = if (tx.isDebit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (stacked) 2.dp else 0.dp)) {
                Text(tx.merchantName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tx.type.label()} · ${shortDate(tx.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (stacked) Text(amount, style = MaterialTheme.typography.labelLarge, color = amountColor)
            }
            if (!stacked) Text(amount, style = MaterialTheme.typography.labelLarge, color = amountColor)
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

/** Holds the sentence the read-aloud button should speak for the screen that is visible now. */
val LocalVoiceSummary = staticCompositionLocalOf<MutableState<String>?> { null }

/**
 * Declares what this screen says when read aloud. A curated summary is used instead of reading the
 * whole tree: on a wallet the useful sentence is the balance and the next action, not every label.
 */
@Composable
fun VoiceSummary(text: String) {
    val state = LocalVoiceSummary.current ?: return
    LaunchedEffect(text) { if (state.value != text) state.value = text }
}

/** Visible, non-colour cue that a mode is active. Elderly users often miss a switch far up the page. */
@Composable
fun ModeBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/** A short explanatory line, used where a switch needs a plain-language consequence. */
@Composable
fun HelperText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
