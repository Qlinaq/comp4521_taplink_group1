package com.taplink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taplink.app.domain.*
import java.util.Locale
import kotlin.math.*

@Composable
fun ExploreScreen(vm: WalletViewModel, platform: PlatformActions) {
    val locations by vm.locations.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val type by vm.text("locationType").collectAsStateWithLifecycle()
    var map by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("") }
    val latitudeString by vm.locationLatitude.collectAsStateWithLifecycle()
    val longitudeString by vm.locationLongitude.collectAsStateWithLifecycle()
    val latitude = latitudeString.toDoubleOrNull()
    val longitude = longitudeString.toDoubleOrNull()
    val locationNote by vm.locationNote.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    DisposableEffect(vm, platform.cancelLocation) {
        onDispose { vm.cancelLocationRequest(); platform.cancelLocation?.invoke() }
    }
    val nearby = locations.filter { (type.isBlank() || it.type.name == type) && (latitude != null || prefs.manualDistrict == "All districts" || it.district == prefs.manualDistrict) }.let { points -> if (latitude != null && longitude != null) points.sortedBy { distanceKm(latitude!!, longitude!!, it.latitude, it.longitude) } else points }
    ScreenColumn {
        PageHeading("Explore TapLink", "Your city, connected.", "Find a top-up stop, a place to tap, or your next ride.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton({ vm.navigate("Transport") }, Modifier.weight(1f)) { Icon(Icons.Outlined.DirectionsTransit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Transport") }
            FilledTonalButton({ vm.navigate("Offers") }, Modifier.weight(1f)) { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Offers") }
        }
        Panel {
            SectionHeading("Nearby services")
            ChoiceField("Manual district", prefs.manualDistrict, (listOf("All districts", "Sai Kung") + locations.map { it.district }).distinct().map { it to it }, { district -> vm.selectDistrict(district); platform.cancelLocation?.invoke() }, !busy)
            platform.requestLocation?.let { request ->
                OutlinedButton(onClick = { vm.requestLocation(request) }, enabled = !locating && !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (locating) "Locating…" else "Use approximate location") }
            }
            Text(locationNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceField("Service type", type, listOf("" to "All service types") + LocationType.entries.map { it.name to it.label() }, { vm.set("locationType", it) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(!map, { map = false }, label = { Text("List") }, leadingIcon = { Icon(Icons.Outlined.ViewList, null, Modifier.size(18.dp)) })
                FilterChip(map, { map = true }, label = { Text("Map") }, leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)) })
            }
            if (map && nearby.isNotEmpty()) {
                DemoLocationMap(nearby, selected) { selected = it }
                Text("Demo location map · geographic overview, not turn-by-turn navigation. Tap a numbered point for details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Notice("Seeded demonstration locations. Addresses and opening hours are sample content, not a verified live directory.")
        if (nearby.isEmpty()) EmptyState("No demo locations here", "Choose another district or service type.", Icons.Outlined.LocationOff)
        nearby.forEachIndexed { index, location ->
            Panel {
                Text("${if (map) "${index + 1}. " else ""}${location.name}", style = MaterialTheme.typography.titleMedium)
                Text("${location.type.label()} · ${location.district}" + if (latitude != null && longitude != null) " · ≈ ${String.format(Locale.ENGLISH, "%.1f", distanceKm(latitude!!, longitude!!, location.latitude, location.longitude))} km" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(location.address, style = MaterialTheme.typography.bodyMedium)
                if (selected == location.id) { Text(location.description, style = MaterialTheme.typography.bodyMedium); DetailRow("Sample hours", location.openingHours) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton({ selected = if (selected == location.id) "" else location.id }) { Text(if (selected == location.id) "Less detail" else "Details") }
                    platform.openMap?.let { open -> TextButton({ open(location) }) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Open maps") } }
                }
            }
        }
    }
}

private fun distanceKm(a: Double, b: Double, c: Double, d: Double): Double {
    val lat = Math.toRadians(c - a); val lon = Math.toRadians(d - b)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(lon / 2).pow(2)
    return 6371 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

@Composable
private fun DemoLocationMap(points: List<ServiceLocation>, selected: String, onSelect: (String) -> Unit) {
    val minLat = points.minOf { it.latitude } - .008; val maxLat = points.maxOf { it.latitude } + .008
    val minLon = points.minOf { it.longitude } - .008; val maxLon = points.maxOf { it.longitude } + .008
    fun point(location: ServiceLocation, width: Float, height: Float) = Offset(((location.longitude - minLon) / (maxLon - minLon) * width).toFloat(), ((maxLat - location.latitude) / (maxLat - minLat) * height).toFloat())
    Canvas(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFFE6F1F0), RoundedCornerShape(16.dp)).semantics { contentDescription = "Geographic demo map with ${points.size} locations. The numbered locations and details are also listed below." }.pointerInput(points) {
        detectTapGestures { tap -> points.minByOrNull { (point(it, size.width.toFloat(), size.height.toFloat()) - tap).getDistance() }?.let { if ((point(it, size.width.toFloat(), size.height.toFloat()) - tap).getDistance() < 44.dp.toPx()) onSelect(it.id) } }
    }) {
        for (i in 1..6) {
            drawLine(Color.White.copy(alpha = .8f), Offset(size.width * i / 7, 0f), Offset(size.width * i / 7, size.height), 3.dp.toPx())
            drawLine(Color.White.copy(alpha = .8f), Offset(0f, size.height * i / 7), Offset(size.width, size.height * i / 7), 3.dp.toPx())
        }
        points.forEachIndexed { index, location ->
            val center = point(location, size.width, size.height)
            if (location.id == selected) drawCircle(Color(0xFF1266D6).copy(alpha = .2f), 23.dp.toPx(), center)
            drawCircle(if (location.id == selected) Color(0xFF1266D6) else Color(0xFF007F85), 14.dp.toPx(), center)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 13.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                canvas.nativeCanvas.drawText("${index + 1}", center.x, center.y - (paint.ascent() + paint.descent()) / 2, paint)
            }
        }
    }
}
