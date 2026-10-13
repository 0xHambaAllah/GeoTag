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
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val locations by viewModel.locations.collectAsState()
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
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
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
                    
                    // Footer
                    item {
                        AppFooter(modifier = Modifier.padding(top = 16.dp, bottom = 80.dp))
                    }
                }
            }
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

@Composable
fun MapView(
    locations: List<GeoLocation>,
    currentLocation: android.location.Location?,
    onMarkerClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Posisi tengah peta: rata-rata lokasi tersimpan / lokasi saat ini / Jakarta
    val centerLat: Double
    val centerLng: Double
    if (locations.isNotEmpty()) {
        centerLat = locations.map { it.latitude }.average()
        centerLng = locations.map { it.longitude }.average()
    } else if (currentLocation != null) {
        centerLat = currentLocation.latitude
        centerLng = currentLocation.longitude
    } else {
        centerLat = -6.2088
        centerLng = 106.8456 // Default: Jakarta
    }

    // Escape string agar aman disisipkan ke JavaScript
    fun escJs(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")

    // Data marker lokasi sebagai JS array
    val markersJs = remember(locations) {
        locations.joinToString(",\n") { loc ->
            val nama = escJs(loc.name.ifEmpty { "Tanpa nama" })
            val alamat = escJs(loc.address.take(80))
            "{id:'${loc.id}',lat:${loc.latitude},lng:${loc.longitude},name:'$nama',addr:'$alamat'}"
        }
    }
    val currentJs = remember(currentLocation) {
        currentLocation?.let { "{lat:${it.latitude},lng:${it.longitude}}" } ?: "null"
    }

    // HTML peta interaktif: Leaflet + OpenStreetMap (gratis, tanpa API key)
    val mapHtml = remember(markersJs, currentJs, centerLat, centerLng) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html,body{margin:0;padding:0;height:100%;width:100%;overflow:hidden;}
                #map{position:absolute;top:0;left:0;right:0;bottom:0;background:#aad3df;}
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map',{zoomControl:true}).setView([$centerLat,$centerLng],14);
                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
                    maxZoom:19,
                    attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                }).addTo(map);
                var markers = [$markersJs];
                markers.forEach(function(m){
                    var pop = '<b>'+m.name+'</b><br/>'+m.addr;
                    L.marker([m.lat,m.lng]).addTo(map).bindPopup(pop)
                        .on('click',function(){ if(window.Android){ window.Android.onMarkerClick(m.id); } });
                });
                var cur = $currentJs;
                if(cur){
                    L.circleMarker([cur.lat,cur.lng],{radius:9,color:'#1a73e8',fillColor:'#1a73e8',fillOpacity:0.9})
                        .addTo(map).bindPopup('Lokasi saya');
                }
                if(markers.length>0){
                    var grp = L.featureGroup(markers.map(function(m){ return L.marker([m.lat,m.lng]); }));
                    map.fitBounds(grp.getBounds().pad(0.25));
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Bridge klik marker WebView -> Compose (dijalankan di main thread)
    val latestOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val mapBridge = remember {
        object {
            private val handler = android.os.Handler(android.os.Looper.getMainLooper())
            @android.webkit.JavascriptInterface
            fun onMarkerClick(id: String) {
                handler.post { latestOnMarkerClick(id) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Peta interaktif Leaflet + OSM; reload hanya saat data berubah
        var lastLoadedHtml by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    addJavascriptInterface(mapBridge, "Android")
                }
            },
            update = { webView ->
                if (lastLoadedHtml != mapHtml) {
                    lastLoadedHtml = mapHtml
                    webView.loadDataWithBaseURL(
                        "https://www.openstreetmap.org",
                        mapHtml, "text/html", "UTF-8", null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        
        // Overlay with location count and markers info
        if (locations.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomStart)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${locations.size} Lokasi Tersimpan",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        locations.take(3).forEach { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMarkerClick(location.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    location.name.ifEmpty { location.address.take(30) },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (locations.size > 3) {
                            Text(
                                "+ ${locations.size - 3} lokasi lainnya",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
