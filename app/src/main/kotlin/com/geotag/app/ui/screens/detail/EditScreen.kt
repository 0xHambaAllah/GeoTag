package com.geotag.app.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.geotag.app.data.local.GeoTagDatabase
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.PlusCode
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    locationId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocationRepository(context) }
    var location by remember { mutableStateOf<GeoLocation?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var plusCodeText by remember { mutableStateOf("") }
    var plusCodeError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(locationId) {
        val db = GeoTagDatabase.getDatabase(context)
        var loc = db.locationDao().getLocationById(locationId)
        if (loc == null) {
            // Data anggota grup (diedit Admin App) tidak ada di Room — ambil dari Firestore
            loc = repository.firebase.getLocationById(locationId)
        }
        location = loc
        loc?.let {
            name = it.name
            phone = it.phoneNumber
            address = it.address
            latText = it.latitude.toString()
            lngText = it.longitude.toString()
            plusCodeText = PlusCode.encode(it.latitude, it.longitude)
        }
    }

    fun setPoint(lat: Double, lng: Double) {
        latText = lat.toString()
        lngText = lng.toString()
        plusCodeText = PlusCode.encode(lat, lng)
        plusCodeError = null
    }

    val pickedLat = latText.toDoubleOrNull()
    val pickedLng = lngText.toDoubleOrNull()
    val coordsValid = pickedLat != null && pickedLng != null &&
        pickedLat in -90.0..90.0 && pickedLng in -180.0..180.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Lokasi") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama lokasi") },
                leadingIcon = { Icon(Icons.Default.Label, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nomor telepon") },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Alamat") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Posisi Foto",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Peta geser pin posisi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
                        org.osmdroid.views.MapView(ctx).apply {
                            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                            val la = pickedLat ?: -6.2088
                            val lo = pickedLng ?: 106.8456
                            controller.setCenter(GeoPoint(la, lo))
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                setPoint(p.latitude, p.longitude)
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        })
                        mapView.overlays.add(eventsOverlay)
                        if (coordsValid && pickedLat != null && pickedLng != null) {
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(pickedLat, pickedLng)
                            marker.title = name.ifEmpty { "Posisi foto" }
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(marker)
                        }
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plus Code — tempel dari Google Maps
            OutlinedTextField(
                value = plusCodeText,
                onValueChange = { newValue ->
                    plusCodeText = newValue.uppercase()
                    if (newValue.isNotBlank()) {
                        val decoded = PlusCode.decodeAny(newValue, pickedLat, pickedLng)
                        if (decoded != null) {
                            plusCodeError = null
                            setPoint(decoded.first, decoded.second)
                        } else {
                            plusCodeError = "Plus Code tidak valid. Salin kode lengkap dari Google Maps."
                        }
                    } else {
                        plusCodeError = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Plus Code") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = plusCodeError != null
            )
            if (plusCodeError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    plusCodeError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { newValue ->
                        latText = newValue
                        val la = newValue.toDoubleOrNull()
                        val lo = lngText.toDoubleOrNull()
                        if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                            plusCodeText = PlusCode.encode(la, lo)
                            plusCodeError = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Latitude") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { newValue ->
                        lngText = newValue
                        val la = latText.toDoubleOrNull()
                        val lo = newValue.toDoubleOrNull()
                        if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                            plusCodeText = PlusCode.encode(la, lo)
                            plusCodeError = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Longitude") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
            if ((latText.isNotEmpty() || lngText.isNotEmpty()) && !coordsValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Koordinat tidak valid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        location?.let { loc ->
                            val updated = loc.copy(
                                name = name,
                                phoneNumber = phone,
                                address = address,
                                latitude = pickedLat ?: loc.latitude,
                                longitude = pickedLng ?: loc.longitude
                            )
                            repository.updateLocation(updated)
                        }
                        onSaved()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving && coordsValid
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Save, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan Perubahan")
                }
            }
        }
    }
}
