package com.geotag.app.ui.screens.home

import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import com.geotag.app.util.PhotoUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _userId = MutableStateFlow<String?>(repository.firebase.currentUserId)
    val userId: StateFlow<String?> = _userId
    val userName: String? get() = repository.firebase.currentUserName
    val userEmail: String? get() = repository.firebase.currentUserEmail

    val locations: StateFlow<List<GeoLocation>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            val uid = _userId.value ?: return@flatMapLatest flowOf(emptyList())
            if (query.isEmpty()) {
                repository.getLocations(uid)
            } else {
                repository.searchLocations(uid, query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lokasi dari anggota grup (diambil dari Firestore, digabung untuk tampilan)
    private val _groupLocations = MutableStateFlow<List<GeoLocation>>(emptyList())

    /** Gabungan lokasi sendiri + lokasi anggota grup (dengan atribusi creatorEmail) */
    val displayLocations: StateFlow<List<GeoLocation>> = combine(
        locations, _groupLocations, _searchQuery
    ) { myLocs, groupLocs, query ->
        val filteredGroup = if (query.isEmpty()) {
            groupLocs
        } else {
            groupLocs.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true)
            }
        }
        (myLocs + filteredGroup)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Jumlah undangan grup pending (untuk badge notifikasi)
    private val _invitationCount = MutableStateFlow(0)
    val invitationCount: StateFlow<Int> = _invitationCount

    fun refreshInvitationCount() {
        viewModelScope.launch {
            val email = userEmail ?: return@launch
            try {
                _invitationCount.value = repository.firebase.getMyInvitations(email).size
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        refreshLocation()
        // Auto restore data saat login
        viewModelScope.launch {
            _userId.collect { uid ->
                if (uid != null) {
                    // Simpan profil user (untuk picker undangan grup)
                    val email = userEmail
                    if (email != null) {
                        repository.firebase.saveUserProfile(
                            uid, email, repository.firebase.currentUserName
                        )
                        // Backfill creatorEmail untuk lokasi lama agar muncul di grup
                        repository.firebase.backfillCreatorEmail(uid, email)
                    }
                    // User baru login, sync data dari Firebase
                    syncFromRemote()
                    refreshGroupLocations()
                    refreshInvitationCount()
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun refreshLocation() {
        viewModelScope.launch {
            val loc = LocationUtils.getCurrentLocation(getApplication())
            _currentLocation.value = loc
        }
    }

    fun syncFromRemote() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _userId.value?.let { repository.syncFromRemote(it) }
            _isRefreshing.value = false
        }
        refreshGroupLocations()
        refreshInvitationCount()
    }

    /**
     * Ambil lokasi dari semua anggota grup yang saya ikuti.
     * Dipanggil saat login, sync, dan kembali dari menu grup.
     * Jika user keluar grup, datanya otomatis tidak muncul lagi.
     */
    fun refreshGroupLocations() {
        viewModelScope.launch {
            val email = userEmail ?: return@launch
            try {
                val groups = repository.firebase.getMyGroups(email)
                val memberEmails = groups
                    .flatMap { it.members }
                    .map { it.lowercase() }
                    .distinct()
                    .filter { it != email.lowercase() }
                _groupLocations.value = if (memberEmails.isNotEmpty()) {
                    repository.firebase.getLocationsByEmails(memberEmails)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                // Biarkan data lama, jangan crash
                e.printStackTrace()
            }
        }
    }
    
    fun triggerBackup() {
        viewModelScope.launch {
            try {
                com.geotag.app.data.remote.SyncWorker.enqueue(getApplication())
                android.widget.Toast.makeText(
                    getApplication(),
                    "Backup dimulai...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    getApplication(),
                    "Gagal memulai backup: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun deleteLocation(location: GeoLocation) {
        viewModelScope.launch {
            repository.deleteLocation(location)
        }
    }

    fun logout() {
        repository.firebase.signOut()
        _userId.value = null
    }
    
    fun handleGalleryPhoto(context: Context, uri: Uri, onNavigateToCamera: () -> Unit) {
        viewModelScope.launch {
            try {
                // Copy photo to app storage
                val photoFile = PhotoUtils.createImageFile(getApplication())
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(photoFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Extract EXIF GPS data
                val exif = ExifInterface(photoFile.absolutePath)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)
                
                val latitude: Double
                val longitude: Double
                
                if (hasGps) {
                    // Photo has GPS data - use it
                    latitude = latLong[0].toDouble()
                    longitude = latLong[1].toDouble()
                } else {
                    // No GPS data - coba ambil lokasi fresh (jangan andalkan cache)
                    var currentLoc = _currentLocation.value
                    if (currentLoc == null) {
                        currentLoc = LocationUtils.getCurrentLocation(getApplication())
                        if (currentLoc != null) {
                            _currentLocation.value = currentLoc
                        }
                    }
                    if (currentLoc == null) {
                        photoFile.delete()
                        Toast.makeText(
                            context,
                            "Tidak bisa mendapatkan lokasi saat ini. Aktifkan GPS dan coba lagi.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    latitude = currentLoc.latitude
                    longitude = currentLoc.longitude
                }
                
                val address = LocationUtils.getAddressFromLocation(
                    getApplication(),
                    latitude,
                    longitude
                )
                
                val uid = _userId.value ?: throw Exception("User not logged in")
                val geoLocation = GeoLocation(
                    userId = uid,
                    creatorEmail = repository.firebase.currentUserEmail ?: "",
                    name = "",
                    phoneNumber = "",
                    photoLocalPath = photoFile.absolutePath,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    timestamp = System.currentTimeMillis(),
                    isSynced = false
                )
                
                repository.saveLocation(geoLocation)
                
                // Trigger auto backup
                com.geotag.app.data.remote.SyncWorker.enqueue(context)
                
                Toast.makeText(
                    context, 
                    "Foto dari galeri berhasil disimpan! Backup ke cloud sedang berjalan...", 
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Gagal memproses foto: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
