package com.geotag.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.geotag.app.data.local.GeoTagDatabase
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    locationId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocationRepository(context) }
    var location by remember { mutableStateOf<GeoLocation?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentDistance by remember { mutableStateOf<Float?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(locationId) {
        val db = GeoTagDatabase.getDatabase(context)
        var loc = db.locationDao().getLocationById(locationId)
        if (loc == null) {
            // Data anggota grup tidak ada di Room lokal — ambil dari Firestore
            loc = repository.firebase.getLocationById(locationId)
        }
        location = loc
        isLoading = false
        val curLoc = LocationUtils.getCurrentLocation(context)
        curLoc?.let { cur ->
            location?.let { l ->
                currentDistance = LocationUtils.calculateDistance(
                    cur.latitude, cur.longitude, l.latitude, l.longitude
                )
            }
        }
    }

    // Hanya pemilik data / Admin App yang boleh edit & hapus
    val canManage = remember(location) {
        val loc = location ?: return@remember false
        val email = repository.firebase.currentUserEmail ?: return@remember false
        if (com.geotag.app.util.AppConstants.isAdminApp(email)) true
        else if (loc.creatorEmail.isEmpty()) true
        else loc.creatorEmail.equals(email, ignoreCase = true)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Lokasi?") },
            text = { Text("Data ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        location?.let { repository.deleteLocation(it) }
                        onDeleted()
                    }
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(location?.name?.ifEmpty { "Detail Lokasi" } ?: "Detail Lokasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        location?.let { loc ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Photo
                val photoModel: Any? = when {
                    loc.photoLocalPath.isNotEmpty() && File(loc.photoLocalPath).exists() -> File(loc.photoLocalPath)
                    loc.photoUrl.isNotEmpty() -> loc.photoUrl
                    else -> null
                }
                AsyncImage(
                    model = photoModel,
                    contentDescription = "Foto lokasi",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    // Name
                    if (loc.name.isNotEmpty()) {
                        Text(loc.name, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Phone
                    if (loc.phoneNumber.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(loc.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.width(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    com.geotag.app.util.WhatsAppUtils.openChat(context, loc.phoneNumber)
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = androidx.compose.ui.graphics.Color(0xFF25D366)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WhatsApp", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Address
                    if (loc.address.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(loc.address, style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Coordinates
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${String.format("%.6f", loc.latitude)}, ${String.format("%.6f", loc.longitude)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Plus Code (akurat di Google Maps)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Plus Code: ${com.geotag.app.util.PlusCode.encode(loc.latitude, loc.longitude)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Distance
                    currentDistance?.let { dist ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NearMe, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${LocationUtils.formatDistance(dist)} dari posisi kamu",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Timestamp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id"))
                        Text(
                            sdf.format(Date(loc.timestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Navigate button
                    Button(
                        onClick = {
                            val uri = Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${loc.latitude},${loc.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Navigasi ke Lokasi Ini", style = MaterialTheme.typography.labelLarge)
                    }

                    // Sync status
                    if (!loc.isSynced) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Belum disinkronkan ke cloud", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Data tidak ditemukan",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onBack) { Text("Kembali") }
                }
            }
        }
    }
}
