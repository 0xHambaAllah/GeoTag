package com.geotag.app.ui.screens.route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geotag.app.data.model.GeoLocation
import org.osmdroid.util.GeoPoint

// Icon marker bernomor (1, 2, 3...) seperti contoh: lingkaran berwarna + angka putih
fun createNumberedMarkerIcon(ctx: android.content.Context, number: Int, colorHex: String): android.graphics.drawable.BitmapDrawable {
    val size = 100
    val bitmap = android.graphics.Bitmap.createBitmap(size, size + 24, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val colorPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(colorHex)
        isAntiAlias = true
    }
    val whitePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
    }
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(colorHex)
        textSize = 46f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    // Lingkaran luar berwarna
    canvas.drawCircle(cx, cy, 44f, colorPaint)
    // Lingkaran dalam putih
    canvas.drawCircle(cx, cy, 34f, whitePaint)
    // Angka
    val fm = textPaint.fontMetrics
    canvas.drawText(number.toString(), cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    // Pin segitiga di bawah
    val path = android.graphics.Path().apply {
        moveTo(cx - 11f, cy + 40f)
        lineTo(cx + 11f, cy + 40f)
        lineTo(cx, cy + 58f)
        close()
    }
    canvas.drawPath(path, colorPaint)
    return android.graphics.drawable.BitmapDrawable(ctx.resources, bitmap)
}

// Icon marker titik awal (lokasi saya sekarang): lingkaran oranye + teks START
fun createStartMarkerIcon(ctx: android.content.Context): android.graphics.drawable.BitmapDrawable {
    val size = 100
    val bitmap = android.graphics.Bitmap.createBitmap(size, size + 24, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val colorPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FF5722")
        isAntiAlias = true
    }
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawCircle(cx, cy, 44f, colorPaint)
    val fm = textPaint.fontMetrics
    canvas.drawText("START", cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    val path = android.graphics.Path().apply {
        moveTo(cx - 11f, cy + 40f)
        lineTo(cx + 11f, cy + 40f)
        lineTo(cx, cy + 58f)
        close()
    }
    canvas.drawPath(path, colorPaint)
    return android.graphics.drawable.BitmapDrawable(ctx.resources, bitmap)
}

@Composable
fun RouteMapView(
    route: List<GeoLocation>,
    routePoints: List<GeoPoint>,
    currentLocation: android.location.Location?
) {
    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                org.osmdroid.views.MapView(ctx).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                // Garis rute (polyline biru)
                if (routePoints.size >= 2) {
                    val polyline = org.osmdroid.views.overlay.Polyline().apply {
                        setPoints(routePoints)
                        outlinePaint.color = android.graphics.Color.parseColor("#1565C0")
                        outlinePaint.strokeWidth = 10f
                    }
                    mapView.overlays.add(polyline)
                }
                // Marker bernomor sesuai urutan kunjungan
                route.forEachIndexed { index, loc ->
                    val marker = org.osmdroid.views.overlay.Marker(mapView)
                    marker.position = GeoPoint(loc.latitude, loc.longitude)
                    marker.title = "${index + 1}. ${loc.name.ifEmpty { "Tanpa nama" }}"
                    // Marker 1 hijau, sisanya biru (seperti contoh)
                    val color = if (index == 0) "#2E7D32" else "#1565C0"
                    marker.icon = createNumberedMarkerIcon(mapView.context, index + 1, color)
                    marker.setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                    )
                    mapView.overlays.add(marker)
                }
                // Lokasi saat ini = titik awal trip
                currentLocation?.let {
                    val me = org.osmdroid.views.overlay.Marker(mapView)
                    me.position = GeoPoint(it.latitude, it.longitude)
                    me.title = "Titik Awal — Lokasi saya sekarang"
                    me.icon = createStartMarkerIcon(mapView.context)
                    me.setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                    )
                    mapView.overlays.add(me)
                }
                // Fit ke semua marker
                val allPoints = mutableListOf<GeoPoint>()
                allPoints.addAll(route.map { GeoPoint(it.latitude, it.longitude) })
                currentLocation?.let { allPoints.add(GeoPoint(it.latitude, it.longitude)) }
                if (allPoints.isNotEmpty()) {
                    try {
                        val bbox = org.osmdroid.util.BoundingBox.fromGeoPoints(allPoints)
                        mapView.zoomToBoundingBox(bbox.increaseByScale(1.3f), false)
                    } catch (e: Exception) {
                        if (allPoints.size == 1) {
                            mapView.controller.setZoom(15.0)
                            mapView.controller.setCenter(allPoints[0])
                        }
                    }
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    onBack: () -> Unit,
    viewModel: RouteViewModel = viewModel()
) {
    val locations by viewModel.locations.collectAsState()
    val selectedLocations by viewModel.selectedLocations.collectAsState()
    val sortedRoute by viewModel.sortedRoute.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val routeInfo by viewModel.routeInfo.collectAsState()
    val isLoadingRoute by viewModel.isLoadingRoute.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val vehicleMode by viewModel.vehicleMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Rute") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (selectedLocations.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("Hapus Pilihan")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedLocations.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedLocations.size} lokasi dipilih",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = {
                                val route = sortedRoute.ifEmpty { viewModel.calculateOptimizedRoute() }
                                if (route.isNotEmpty()) {
                                    openGoogleMapsRoute(context, route, currentLocation, vehicleMode)
                                }
                            },
                            enabled = selectedLocations.size >= 2
                        ) {
                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mulai Navigasi")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cari lokasi...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Hapus")
                        }
                    }
                },
                singleLine = true
            )
            
            if (locations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Route,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Belum ada lokasi",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Trip dimulai dari lokasi kamu sekarang, lalu ke lokasi terdekat dst.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Peta rute + tombol urutkan (muncul jika 2+ lokasi dipilih)
                if (selectedLocations.size >= 2) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Pemilih kendaraan — default Motor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Kendaraan:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                VehicleMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = vehicleMode == mode,
                                        onClick = { viewModel.setVehicleMode(mode) },
                                        label = { Text(mode.label) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pratinjau Rute",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = { viewModel.sortRoute() },
                                    enabled = !isLoadingRoute
                                ) {
                                    if (isLoadingRoute) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Urutkan Rute")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (sortedRoute.isNotEmpty()) {
                                // Peta dengan marker bernomor + garis rute
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp),
                                ) {
                                    RouteMapView(
                                        route = sortedRoute,
                                        routePoints = routePoints,
                                        currentLocation = currentLocation
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Info jarak & durasi
                                routeInfo?.let { (km, min) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Estimasi Jarak",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.2f km", km).replace('.', ','),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Estimasi Durasi",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            String.format("%.1f menit", min).replace('.', ','),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Urutan kunjungan:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Titik awal = lokasi saya sekarang
                                currentLocation?.let {
                                    Text(
                                        "Titik Awal — Lokasi saya sekarang",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                sortedRoute.forEachIndexed { index, loc ->
                                    Text(
                                        "${index + 1}. ${loc.name.ifEmpty { "Tanpa nama" }}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Text(
                                    "Tekan \"Urutkan Rute\" untuk mengurutkan dari lokasi terdekat dan melihat peta rute.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(locations, key = { it.id }) { location ->
                        LocationCheckboxItem(
                            location = location,
                            isSelected = selectedLocations.any { it.id == location.id },
                            onToggle = { viewModel.toggleLocationSelection(location) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationCheckboxItem(
    location: GeoLocation,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name.ifEmpty { "Tanpa nama" },
                    style = MaterialTheme.typography.titleMedium
                )
                if (location.creatorEmail.isNotEmpty()) {
                    Text(
                        text = "by ${location.creatorEmail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                if (location.address.isNotEmpty()) {
                    Text(
                        text = location.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Icon(
                Icons.Default.LocationOn,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun openGoogleMapsRoute(
    context: android.content.Context,
    route: List<GeoLocation>,
    currentLocation: android.location.Location?,
    vehicleMode: VehicleMode
) {
    if (route.isEmpty()) return

    // Trip dimulai dari lokasi saya sekarang (bila GPS tersedia),
    // lalu ke Trip 1 dst. Google Maps tidak punya mode motor,
    // jadi Motor & Mobil memakai travelmode=driving.
    val origin = if (currentLocation != null) {
        "${currentLocation.latitude},${currentLocation.longitude}"
    } else {
        "${route.first().latitude},${route.first().longitude}"
    }
    val destination = route.last()
    val waypointLocs = if (currentLocation != null) {
        route.dropLast(1)
    } else {
        if (route.size > 2) route.subList(1, route.size - 1) else emptyList()
    }
    val waypoints = waypointLocs.joinToString("|") { "${it.latitude},${it.longitude}" }
    val travelMode = if (vehicleMode == VehicleMode.JALAN_KAKI) "walking" else "driving"

    val uri = buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=$origin")
        append("&destination=${destination.latitude},${destination.longitude}")
        if (waypoints.isNotEmpty()) {
            append("&waypoints=$waypoints")
        }
        append("&travelmode=$travelMode")
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
