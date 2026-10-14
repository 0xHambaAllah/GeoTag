package com.geotag.app.ui.screens.picker

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Pilih lokasi manual untuk foto galeri tanpa GPS.
 * Wajib isi: alamat + koordinat (tap di peta, ketik angka, atau tombol GPS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    photoPath: String,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocationRepository(context) }

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingAddress by remember { mutableStateOf(false) }
    var mapCenter by remember { mutableStateOf(GeoPoint(-6.2088, 106.8456)) }
    var hasCentered by remember { mutableStateOf(false) }

    fun reverseGeocode(lat: Double, lng: Double) {
        isLoadingAddress = true
        scope.launch(Dispatchers.IO) {
            val result = LocationUtils.getAddressFromLocation(context, lat, lng)
            withContext(Dispatchers.Main) {
                if (result.isNotEmpty()) address = result
                isLoadingAddress = false
            }
        }
    }

    fun setPoint(lat: Double, lng: Double, geocode: Boolean = true) {
        latText = lat.toString()
        lngText = lng.toString()
        if (geocode) reverseGeocode(lat, lng)
    }

    // Center awal: lokasi GPS device bila tersedia
    LaunchedEffect(Unit) {
        val loc = LocationUtils.getCurrentLocation(context)
        if (loc != null) mapCenter = GeoPoint(loc.latitude, loc.longitude)
    }

    val pickedLat = latText.toDoubleOrNull()
    val pickedLng = lngText.toDoubleOrNull()
    val coordsValid = pickedLat != null && pickedLng != null &&
        pickedLat in -90.0..90.0 && pickedLng in -180.0..180.0
    val canSave = address.isNotBlank() && coordsValid && !isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilih Lokasi Foto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "Foto ini tidak punya data lokasi. Tap peta untuk menaruh pin, lalu isi alamat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            // Peta pilih titik
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                        org.osmdroid.views.MapView(ctx).apply {
                            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            controller.setCenter(mapCenter)
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        // Tangkap tap di peta
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                setPoint(p.latitude, p.longitude)
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        })
                        mapView.overlays.add(eventsOverlay)
                        // Marker titik yang dipilih
                        if (coordsValid && pickedLat != null && pickedLng != null) {
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(pickedLat, pickedLng)
                            marker.title = address.ifEmpty { "Lokasi dipilih" }
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(marker)
                        }
                        if (!hasCentered) {
                            hasCentered = true
                            mapView.controller.setCenter(mapCenter)
                        }
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Tombol GPS
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val loc = LocationUtils.getCurrentLocation(context)
                            if (loc != null) {
                                setPoint(loc.latitude, loc.longitude)
                            } else {
                                Toast.makeText(context, "GPS tidak tersedia", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.MyLocation, "Lokasi saya")
                }
            }

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama lokasi (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat *") },
                    placeholder = { Text("Jl. ..., Kota, Provinsi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (isLoadingAddress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude *") },
                        placeholder = { Text("-6.2088") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude *") },
                        placeholder = { Text("106.8456") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                if (latText.isNotEmpty() || lngText.isNotEmpty()) {
                    if (!coordsValid) {
                        Text(
                            "Koordinat tidak valid. Latitude -90 s.d. 90, Longitude -180 s.d. 180.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Button(
                    onClick = {
                        if (!canSave) return@Button
                        isSaving = true
                        scope.launch {
                            try {
                                val uid = repository.firebase.currentUserId ?: ""
                                val geoLocation = GeoLocation(
                                    userId = uid,
                                    creatorEmail = repository.firebase.currentUserEmail ?: "",
                                    name = name,
                                    phoneNumber = "",
                                    photoLocalPath = photoPath,
                                    latitude = pickedLat!!,
                                    longitude = pickedLng!!,
                                    address = address.trim(),
                                    timestamp = System.currentTimeMillis(),
                                    isSynced = false
                                )
                                repository.saveLocation(geoLocation)
                                com.geotag.app.data.remote.SyncWorker.enqueue(context)
                                Toast.makeText(context, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                onSaved()
                            } catch (e: Exception) {
                                isSaving = false
                                Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Simpan Lokasi")
                    }
                }
            }
        }
    }
}
