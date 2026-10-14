package com.geotag.app.ui.screens.picker

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import com.geotag.app.util.PlusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Input lokasi foto galeri:
 * - Nama alamat (wajib)
 * - Nomor (opsional)
 * - Plus Code / koordinat (wajib) — salin manual dari Google Maps lalu tempel.
 * Alamat jalan terisi otomatis dari koordinat (reverse geocode).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    photoPath: String,
    initialLat: Double,
    initialLng: Double,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocationRepository(context) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf(if (initialLat != 0.0) initialLat.toString() else "") }
    var lngText by remember { mutableStateOf(if (initialLng != 0.0) initialLng.toString() else "") }
    var plusCodeText by remember { mutableStateOf("") }
    var plusCodeError by remember { mutableStateOf<String?>(null) }
    var autoAddress by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingAddress by remember { mutableStateOf(false) }
    var mapCenter by remember { mutableStateOf(GeoPoint(-6.2088, 106.8456)) }
    var hasCentered by remember { mutableStateOf(false) }
    var refLat by remember { mutableStateOf<Double?>(null) }
    var refLng by remember { mutableStateOf<Double?>(null) }

    fun reverseGeocode(lat: Double, lng: Double) {
        isLoadingAddress = true
        scope.launch(Dispatchers.IO) {
            val result = LocationUtils.getAddressFromLocation(context, lat, lng)
            withContext(Dispatchers.Main) {
                autoAddress = result
                isLoadingAddress = false
            }
        }
    }

    fun setPoint(lat: Double, lng: Double, geocode: Boolean = true) {
        latText = lat.toString()
        lngText = lng.toString()
        plusCodeText = PlusCode.encode(lat, lng)
        plusCodeError = null
        if (geocode) reverseGeocode(lat, lng)
    }

    fun applyPlusCode(code: String) {
        val decoded = PlusCode.decodeAny(code, refLat, refLng)
        if (decoded != null) {
            plusCodeError = null
            setPoint(decoded.first, decoded.second)
        } else {
            plusCodeError = "Plus Code tidak valid. Salin kode lengkap dari Google Maps (misal 6P4JC28P+M9)."
        }
    }

    // Init: lokasi referensi (GPS device) + pre-fill dari EXIF bila ada
    LaunchedEffect(Unit) {
        val loc = LocationUtils.getCurrentLocation(context)
        if (loc != null) {
            refLat = loc.latitude
            refLng = loc.longitude
            mapCenter = GeoPoint(loc.latitude, loc.longitude)
        }
        if (initialLat != 0.0 && initialLng != 0.0) {
            plusCodeText = PlusCode.encode(initialLat, initialLng)
            reverseGeocode(initialLat, initialLng)
        }
    }

    val pickedLat = latText.toDoubleOrNull()
    val pickedLng = lngText.toDoubleOrNull()
    val coordsValid = pickedLat != null && pickedLng != null &&
        pickedLat in -90.0..90.0 && pickedLng in -180.0..180.0
    val canSave = name.isNotBlank() && coordsValid && !isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lokasi Foto") },
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
                "Salin Plus Code dari Google Maps lalu tempel di bawah, atau tap peta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                            marker.title = name.ifEmpty { "Lokasi dipilih" }
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
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val loc = LocationUtils.getCurrentLocation(context)
                            if (loc != null) {
                                refLat = loc.latitude
                                refLng = loc.longitude
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

            // Form: Nama alamat (wajib) + Nomor (opsional) + Plus Code (wajib)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama alamat *") },
                    placeholder = { Text("Contoh: Rumah, Toko, Kantor, Masjid") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Label, null) },
                    isError = name.isBlank()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Nomor (opsional)") },
                    placeholder = { Text("08xxxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Phone, null) }
                )
                // Plus Code — salin manual dari Google Maps
                OutlinedTextField(
                    value = plusCodeText,
                    onValueChange = { newValue ->
                        plusCodeText = newValue.uppercase()
                        if (newValue.isNotBlank()) applyPlusCode(newValue) else plusCodeError = null
                    },
                    label = { Text("Plus Code *") },
                    placeholder = { Text("Salin dari Google Maps, tempel di sini") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                    isError = plusCodeError != null,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (plusCodeText.isNotBlank()) {
                                clipboard.setText(AnnotatedString(plusCodeText))
                                Toast.makeText(context, "Plus Code disalin", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, "Salin Plus Code")
                        }
                    }
                )
                if (plusCodeError != null) {
                    Text(
                        plusCodeError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
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
                        label = { Text("Latitude") },
                        placeholder = { Text("-6.2088") },
                        modifier = Modifier.weight(1f),
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
                        label = { Text("Longitude") },
                        placeholder = { Text("106.8456") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                if ((latText.isNotEmpty() || lngText.isNotEmpty()) && !coordsValid) {
                    Text(
                        "Koordinat tidak valid. Latitude -90 s.d. 90, Longitude -180 s.d. 180.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Alamat jalan otomatis dari koordinat
                if (isLoadingAddress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Memuat alamat...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (autoAddress.isNotEmpty()) {
                    Text(
                        autoAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                    name = name.trim(),
                                    phoneNumber = phone.trim(),
                                    photoLocalPath = photoPath,
                                    latitude = pickedLat!!,
                                    longitude = pickedLng!!,
                                    address = autoAddress.ifEmpty { plusCodeText },
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
