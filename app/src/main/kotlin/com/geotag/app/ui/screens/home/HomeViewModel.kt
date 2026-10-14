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
import com.geotag.app.data.model.LiveLocation
import com.geotag.app.util.AppConstants
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import com.geotag.app.util.PhotoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
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

    // Posisi GPS realtime anggota grup (marker merah di peta)
    private val _liveLocations = MutableStateFlow<List<LiveLocation>>(emptyList())
    val liveLocations: StateFlow<List<LiveLocation>> = _liveLocations

    private var liveSharingJob: Job? = null
    private var liveListenerJob: Job? = null

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
                val others = memberEmails.filter { it != email.lowercase() }
                _groupLocations.value = if (others.isNotEmpty()) {
                    repository.firebase.getLocationsByEmails(others)
                } else {
                    emptyList()
                }
                // Live location: dengarkan posisi anggota grup & bagikan posisi saya
                startLiveListener(others)
                if (others.isNotEmpty()) startLiveSharing() else stopLiveSharing()
            } catch (e: Exception) {
                // Biarkan data lama, jangan crash
                e.printStackTrace()
            }
        }
    }

    /**
     * Bagikan posisi GPS saya ke Firestore setiap 30 detik,
     * hanya saat tergabung dalam grup (ada anggota lain).
     */
    private fun startLiveSharing() {
        if (liveSharingJob?.isActive == true) return
        liveSharingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val loc = LocationUtils.getCurrentLocation(getApplication())
                    val email = userEmail
                    val uid = _userId.value
                    if (loc != null && email != null && uid != null) {
                        repository.firebase.updateLiveLocation(
                            uid = uid,
                            email = email,
                            displayName = repository.firebase.currentUserName ?: "",
                            latitude = loc.latitude,
                            longitude = loc.longitude
                        )
                    }
                } catch (_: Exception) { }
                delay(30_000)
            }
        }
    }

    private fun stopLiveSharing() {
        liveSharingJob?.cancel()
        liveSharingJob = null
    }

    /**
     * Dengarkan posisi realtime anggota grup (buang data basi > 5 menit —
     * user sudah menutup aplikasi).
     */
    private fun startLiveListener(others: List<String>) {
        liveListenerJob?.cancel()
        if (others.isEmpty()) {
            _liveLocations.value = emptyList()
            return
        }
        liveListenerJob = viewModelScope.launch {
            try {
                repository.firebase.listenGroupLiveLocations(others).collect { list ->
                    val now = System.currentTimeMillis()
                    _liveLocations.value = list.filter {
                        now - it.updatedAt < 5 * 60 * 1000
                    }
                }
            } catch (e: Exception) {
                // Listener gagal (misal rules belum ditambahkan) — jangan crash
                e.printStackTrace()
                _liveLocations.value = emptyList()
            }
        }
    }

    override fun onCleared() {
        stopLiveSharing()
        liveListenerJob?.cancel()
        super.onCleared()
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

    fun canManage(location: GeoLocation): Boolean {
        val email = userEmail ?: return false
        if (AppConstants.isAdminApp(email)) return true
        // Data lokal lama tanpa creatorEmail dianggap milik sendiri
        // (data grup selalu punya creatorEmail karena query grup pakai field itu)
        if (location.creatorEmail.isEmpty()) return true
        return location.creatorEmail.equals(email, ignoreCase = true)
    }

    fun deleteLocation(location: GeoLocation) {
        if (!canManage(location)) return
        viewModelScope.launch {
            repository.deleteLocation(location)
        }
    }

    fun logout() {
        repository.firebase.signOut()
        _userId.value = null
    }
    
    fun handleGalleryPhoto(context: Context, uri: Uri, onNeedManualLocation: (String, Double, Double) -> Unit) {
        viewModelScope.launch {
            try {
                // Copy photo to app storage
                val photoFile = PhotoUtils.createImageFile(getApplication())
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(photoFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Extract EXIF GPS data (bila ada, jadi titik awal di picker)
                val exif = ExifInterface(photoFile.absolutePath)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)
                val lat = if (hasGps) latLong[0].toDouble() else 0.0
                val lng = if (hasGps) latLong[1].toDouble() else 0.0

                // Semua foto galeri wajib lewat picker: isi nama + alamat + Plus Code/koordinat
                onNeedManualLocation(photoFile.absolutePath, lat, lng)
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
