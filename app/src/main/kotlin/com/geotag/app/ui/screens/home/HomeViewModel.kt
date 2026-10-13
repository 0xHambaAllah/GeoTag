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

    init {
        refreshLocation()
        // Auto restore data saat login
        viewModelScope.launch {
            _userId.collect { uid ->
                if (uid != null) {
                    // User baru login, sync data dari Firebase
                    syncFromRemote()
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
                    // No GPS data - use current location
                    val currentLoc = _currentLocation.value
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
                
                // Trigger auto backup to ImgBB
                com.geotag.app.data.remote.SyncWorker.enqueue(context)
                
                Toast.makeText(context, "Foto dari galeri berhasil disimpan", Toast.LENGTH_SHORT).show()
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
