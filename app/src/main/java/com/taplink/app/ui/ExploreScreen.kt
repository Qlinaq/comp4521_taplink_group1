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
import androidx.compose.ui.res.stringResource
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
import com.taplink.app.R
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
    val locationNoteRaw by vm.locationNote.collectAsStateWithLifecycle()
    val locationNote = localizedMessage(locationNoteRaw).orEmpty()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    DisposableEffect(vm, platform.cancelLocation) {
        onDispose { vm.cancelLocationRequest(); platform.cancelLocation?.invoke() }
    }
    val nearby = locations.filter { (type.isBlank() || it.type.name == type) && (latitude != null || prefs.manualDistrict == stringResource(R.string.ui_all_districts) || it.district == prefs.manualDistrict) }.let { points -> if (latitude != null && longitude != null) points.sortedBy { distanceKm(latitude!!, longitude!!, it.latitude, it.longitude) } else points }
    ScreenColumn {
        // Same hero heading as Home: this is a tab root, not a drill-down screen.
        PageHeading( stringResource(R.string.ui_explore_taplink) , stringResource(R.string.ui_your_city_connected) , stringResource(R.string.ui_find_a_top_up_stop_a_place_to_tap_or_your_ne) , hero = true )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton({ vm.navigate("Transport") }, Modifier.weight(1f)) { Icon(Icons.Outlined.DirectionsTransit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_transport)) }
            FilledTonalButton({ vm.navigate("Offers") }, Modifier.weight(1f)) { Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_offers)) }
        }
        Panel {
            SectionHeading( stringResource(R.string.ui_nearby_services) )
            ChoiceField( stringResource(R.string.ui_manual_district) , prefs.manualDistrict, (listOf(stringResource(R.string.ui_all_districts), stringResource(R.string.ui_sai_kung)) + locations.map { it.district }).distinct().map { it to it }, { district -> vm.selectDistrict(district); platform.cancelLocation?.invoke() }, !busy)
            platform.requestLocation?.let { request ->
                OutlinedButton(onClick = { vm.requestLocation(request) }, enabled = !locating && !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (locating) stringResource(R.string.ui_locating) else stringResource(R.string.ui_use_approximate_location)) }
            }
            Text(locationNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceField( stringResource(R.string.ui_service_type) , type, listOf("" to stringResource(R.string.ui_all_service_types)) + LocationType.entries.map { it.name to it.label() }, { vm.set("locationType", it) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(!map, { map = false }, label = { Text(stringResource(R.string.ui_list)) }, leadingIcon = { Icon(Icons.Outlined.ViewList, null, Modifier.size(18.dp)) })
                FilterChip(map, { map = true }, label = { Text(stringResource(R.string.ui_map)) }, leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)) })
            }
            if (map && nearby.isNotEmpty()) {
                DemoLocationMap(nearby, selected) { selected = it }
                Text(stringResource(R.string.ui_demo_location_map_geographic_overview_not_tu), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Notice( stringResource(R.string.ui_seeded_demonstration_locations_addresses_and) )
        if (nearby.isEmpty()) EmptyState( stringResource(R.string.ui_no_demo_locations_here) , stringResource(R.string.ui_choose_another_district_or_service_type) , Icons.Outlined.LocationOff)
        nearby.forEachIndexed { index, location ->
            Panel {
                Text("${if (map) "${index + 1}. " else ""}${location.name}", style = MaterialTheme.typography.titleMedium)
                Text("${location.type.label()} · ${location.district}" + if (latitude != null && longitude != null) " · ≈ ${String.format(Locale.ENGLISH, "%.1f", distanceKm(latitude!!, longitude!!, location.latitude, location.longitude))} km" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(location.address, style = MaterialTheme.typography.bodyMedium)
                if (selected == location.id) { Text(location.description, style = MaterialTheme.typography.bodyMedium); DetailRow( stringResource(R.string.ui_sample_hours) , location.openingHours) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton({ selected = if (selected == location.id) "" else location.id }) { Text(if (selected == location.id) stringResource(R.string.ui_less_detail) else stringResource(R.string.ui_details)) }
                    platform.openMap?.let { open -> TextButton({ open(location) }) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_open_maps)) } }
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
    val mapDescription = stringResource(R.string.ui_demo_map_description, points.size)
    Canvas(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFFE6F1F0), RoundedCornerShape(16.dp)).semantics { contentDescription = mapDescription }.pointerInput(points) {
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
