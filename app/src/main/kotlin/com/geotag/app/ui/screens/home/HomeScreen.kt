package com.geotag.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.ui.components.AppFooter
import com.geotag.app.util.LocationUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onRouteClick: () -> Unit,
    onGroupClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val locations by viewModel.displayLocations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val userId by viewModel.userId.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            viewModel.handleGalleryPhoto(context, it, onAddClick)
        }
    }
    
    // Photo source dialog
    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            icon = { Icon(Icons.Default.PhotoCamera, null) },
            title = { Text("Pilih Sumber Foto") },
            text = { Text("Ambil foto baru dengan kamera atau pilih dari galeri?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPhotoSourceDialog = false
                        onAddClick()
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kamera")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoSourceDialog = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Galeri")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GeoTag")
                        viewModel.userEmail?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (com.geotag.app.util.AppConstants.isAdminApp(it)) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                com.geotag.app.util.AppConstants.ADMIN_APP_LABEL,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onGroupClick) {
                        Icon(Icons.Default.Group, "Grup")
                    }
                    IconButton(onClick = onRouteClick) {
                        Icon(Icons.Default.Route, "Buat Rute")
                    }
                    IconButton(onClick = { viewModel.syncFromRemote() }) {
                        Icon(Icons.Default.Refresh, "Sync")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Backup Foto ke Cloud") },
                                onClick = {
                                    showMenu = false
                                    viewModel.triggerBackup()
                                },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore dari Cloud") },
                                onClick = {
                                    showMenu = false
                                    viewModel.syncFromRemote()
                                },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showMenu = false
                                    viewModel.logout()
                                    onLogout()
                                },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPhotoSourceDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Tambah Lokasi")
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
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Google Maps view with markers (always show, even when empty)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (locations.isNotEmpty() || currentLocation != null) {
                    MapView(
                        locations = locations,
                        currentLocation = currentLocation,
                        onMarkerClick = { locationId -> onItemClick(locationId) }
                    )
                } else {
                    // Placeholder when no data
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Map,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Belum ada lokasi tersimpan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
            if (locations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Belum ada lokasi tersimpan",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap + untuk menambah lokasi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(locations, key = { it.id }) { location ->
                        LocationCard(
                            location = location,
                            currentLocation = currentLocation,
                            onClick = { onItemClick(location.id) },
                            onNavigate = {
                                val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    (viewModel.getApplication() as android.app.Application).startActivity(
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}")
                                    (viewModel.getApplication() as android.app.Application).startActivity(
                                        Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            onDelete = { viewModel.deleteLocation(location) }
                        )
                    }
                }
            }
            }
            // Footer sticky di bawah (tidak ikut scroll)
            AppFooter(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun LocationCard(
    location: GeoLocation,
    currentLocation: android.location.Location?,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Lokasi?") },
            text = { Text("Lokasi \"${location.name.ifEmpty { "Tanpa nama" }}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Photo thumbnail
            val photoModel: Any? = when {
                location.photoLocalPath.isNotEmpty() && File(location.photoLocalPath).exists() -> File(location.photoLocalPath)
                location.photoUrl.isNotEmpty() -> location.photoUrl
                else -> null
            }

            AsyncImage(
                model = photoModel,
                contentDescription = "Foto lokasi",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = location.name.ifEmpty { "Tanpa nama" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (location.phoneNumber.isNotEmpty()) {
                    Text(
                        text = location.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (location.address.isNotEmpty()) {
                    Text(
                        text = location.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Distance
                    currentLocation?.let { curLoc ->
                        val distance = LocationUtils.calculateDistance(
                            curLoc.latitude, curLoc.longitude,
                            location.latitude, location.longitude
                        )
                        Icon(
                            Icons.Default.NearMe,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " ${LocationUtils.formatDistance(distance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Timestamp
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " ${formatTimestamp(location.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Atribusi pembuat (by email) + label Admin App
                if (location.creatorEmail.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " by ${location.creatorEmail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (com.geotag.app.util.AppConstants.isAdminApp(location.creatorEmail)) {
                            Spacer(modifier = Modifier.width(4.dp))
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        com.geotag.app.util.AppConstants.ADMIN_APP_LABEL,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    FilledTonalButton(
                        onClick = onNavigate,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Navigasi", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            "Hapus",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    // Sync indicator
                    if (!location.isSynced) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.CloudOff,
                            "Belum disync",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id"))
    return sdf.format(Date(timestamp))
}

// Membuat icon marker dengan label nama di atasnya
fun createLabeledMarkerIcon(ctx: android.content.Context, label: String): android.graphics.drawable.BitmapDrawable {
    val text = label.ifEmpty { "Tanpa nama" }.take(20)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 34f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 20, 20, 20)
        isAntiAlias = true
    }
    val paddingH = 18f
    val paddingV = 12f
    val textWidth = paint.measureText(text)
    val fm = paint.fontMetrics
    val textHeight = fm.descent - fm.ascent
    val labelW = (textWidth + paddingH * 2).toInt()
    val labelH = (textHeight + paddingV * 2).toInt()
    val pinH = 56
    val pinW = 40
    val width = maxOf(labelW, pinW + 20)
    val height = labelH + pinH + 8
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    // Label: rounded rect hitam + teks putih
    val rect = android.graphics.RectF(
        ((width - labelW) / 2f), 0f,
        ((width + labelW) / 2f), labelH.toFloat()
    )
    canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
    canvas.drawText(text, width / 2f, paddingV - fm.ascent, paint)
    // Pin: lingkaran + segitiga di bawah label
    val cx = width / 2f
    val pinTop = labelH + 4f
    val pinPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#2E7D32")
        isAntiAlias = true
    }
    canvas.drawCircle(cx, pinTop + 18f, 18f, pinPaint)
    val path = android.graphics.Path().apply {
        moveTo(cx - 13f, pinTop + 28f)
        lineTo(cx + 13f, pinTop + 28f)
        lineTo(cx, pinTop + 52f)
        close()
    }
    canvas.drawPath(path, pinPaint)
    return android.graphics.drawable.BitmapDrawable(ctx.resources, bitmap)
}

@Composable
fun MapView(
    locations: List<GeoLocation>,
    currentLocation: android.location.Location?,
    onMarkerClick: (String) -> Unit
) {
    val latestOnMarkerClick by rememberUpdatedState(onMarkerClick)
    var hasCentered by remember { mutableStateOf(false) }

    fun computeCenter(): Triple<Double, Double, Double> {
        return when {
            locations.isNotEmpty() -> Triple(
                locations.map { it.latitude }.average(),
                locations.map { it.longitude }.average(),
                if (locations.size == 1) 16.0 else 13.0
            )
            currentLocation != null -> Triple(
                currentLocation.latitude, currentLocation.longitude, 15.0
            )
            else -> Triple(-6.2088, 106.8456, 12.0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                // User agent via Configuration (API osmdroid 6.x yang benar)
                org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                org.osmdroid.views.MapView(ctx).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    val (lat, lng, zoom) = computeCenter()
                    controller.setZoom(zoom)
                    controller.setCenter(org.osmdroid.util.GeoPoint(lat, lng))
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                locations.forEach { loc ->
                    val marker = org.osmdroid.views.overlay.Marker(mapView)
                    marker.position = org.osmdroid.util.GeoPoint(loc.latitude, loc.longitude)
                    marker.title = loc.name.ifEmpty { "Tanpa nama" }
                    marker.icon = createLabeledMarkerIcon(mapView.context, loc.name)
                    marker.setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                    )
                    marker.setOnMarkerClickListener { _, _ ->
                        latestOnMarkerClick(loc.id)
                        true
                    }
                    mapView.overlays.add(marker)
                }
                currentLocation?.let {
                    val me = org.osmdroid.views.overlay.Marker(mapView)
                    me.position = org.osmdroid.util.GeoPoint(it.latitude, it.longitude)
                    me.title = "Lokasi saya"
                    mapView.overlays.add(me)
                }
                if (!hasCentered && (locations.isNotEmpty() || currentLocation != null)) {
                    hasCentered = true
                    val (lat, lng, zoom) = computeCenter()
                    mapView.controller.setZoom(zoom)
                    mapView.controller.setCenter(org.osmdroid.util.GeoPoint(lat, lng))
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )


        
    }
}
