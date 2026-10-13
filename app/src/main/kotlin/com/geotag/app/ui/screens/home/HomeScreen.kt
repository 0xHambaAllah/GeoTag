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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
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
    var mapError by remember { mutableStateOf<String?>(null) }
    
    // Calculate center position based on locations or current location
    val centerPosition = remember(locations, currentLocation) {
        if (locations.isNotEmpty()) {
            val avgLat = locations.map { it.latitude }.average()
            val avgLng = locations.map { it.longitude }.average()
            LatLng(avgLat, avgLng)
        } else if (currentLocation != null) {
            LatLng(currentLocation.latitude, currentLocation.longitude)
        } else {
            LatLng(-6.2088, 106.8456) // Default: Jakarta
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centerPosition, 15f)
    }
    
    // Update camera when locations change
    LaunchedEffect(locations, centerPosition) {
        if (locations.isNotEmpty()) {
            try {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(centerPosition, 15f),
                    durationMs = 1000
                )
            } catch (e: Exception) {
                mapError = "Animasi peta error: ${e.message}"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false, // Disable to avoid permission issues
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = true,
                compassEnabled = true,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true
            ),
            onMapLoaded = {
                mapError = null
            }
        ) {
            // Add markers for each location
            locations.forEach { location ->
                Marker(
                    state = rememberMarkerState(position = LatLng(location.latitude, location.longitude)),
                    title = location.name.ifEmpty { "Lokasi Foto" },
                    snippet = "${location.address.take(50)}${if (location.address.length > 50) "..." else ""}",
                    onClick = {
                        onMarkerClick(location.id)
                        true
                    }
                )
            }
            
            // Show current location marker if available and no saved locations
            if (locations.isEmpty() && currentLocation != null) {
                Marker(
                    state = rememberMarkerState(position = LatLng(currentLocation.latitude, currentLocation.longitude)),
                    title = "Lokasi Saya",
                    snippet = "Posisi saat ini"
                )
            }
        }
        
        // Info overlay about API key
        if (locations.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${locations.size} lokasi tersimpan. Peta butuh Google Maps API key untuk tampil penuh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        // Show error overlay if any
        mapError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}
