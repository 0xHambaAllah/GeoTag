package com.geotag.app.ui.screens.route

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

/** Kendaraan trip: Motor (default), Mobil, Jalan Kaki. */
enum class VehicleMode(val label: String) {
    MOTOR("Motor"),
    MOBIL("Mobil"),
    JALAN_KAKI("Jalan Kaki")
}

class RouteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    
    private val _selectedLocations = MutableStateFlow<List<GeoLocation>>(emptyList())
    val selectedLocations: StateFlow<List<GeoLocation>> = _selectedLocations
    
    // Rute yang sudah diurutkan (untuk tampil di peta & navigasi)
    private val _sortedRoute = MutableStateFlow<List<GeoLocation>>(emptyList())
    val sortedRoute: StateFlow<List<GeoLocation>> = _sortedRoute
    
    // Titik-titik polyline rute dari OSRM
    private val _routePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routePoints: StateFlow<List<GeoPoint>> = _routePoints
    
    // Info rute: Pair(jarakKm, durasiMenit)
    private val _routeInfo = MutableStateFlow<Pair<Double, Double>?>(null)
    val routeInfo: StateFlow<Pair<Double, Double>?> = _routeInfo
    
    private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    // Kendaraan trip — default Motor
    private val _vehicleMode = MutableStateFlow(VehicleMode.MOTOR)
    val vehicleMode: StateFlow<VehicleMode> = _vehicleMode

    fun setVehicleMode(mode: VehicleMode) {
        if (_vehicleMode.value == mode) return
        _vehicleMode.value = mode
        val route = _sortedRoute.value
        if (route.size >= 2) {
            viewModelScope.launch { fetchOsrmRoute(route) }
        }
    }
    
    val userId: String? get() = repository.firebase.currentUserId
    
    private val myLocations: StateFlow<List<GeoLocation>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            val uid = userId ?: return@flatMapLatest flowOf(emptyList())
            if (query.isEmpty()) {
                repository.getLocations(uid)
            } else {
                repository.searchLocations(uid, query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lokasi dari anggota grup (digabung untuk tampilan)
    private val _groupLocations = MutableStateFlow<List<GeoLocation>>(emptyList())

    /** Gabungan lokasi sendiri + lokasi anggota grup */
    val locations: StateFlow<List<GeoLocation>> = combine(
        myLocations, _groupLocations, _searchQuery
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
    
    init {
        refreshLocation()
        refreshGroupLocations()
    }

    /**
     * Ambil lokasi dari semua anggota grup yang saya ikuti.
     */
    fun refreshGroupLocations() {
        viewModelScope.launch {
            val email = repository.firebase.currentUserEmail ?: return@launch
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
                e.printStackTrace()
            }
        }
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    private fun refreshLocation() {
        viewModelScope.launch {
            val loc = LocationUtils.getCurrentLocation(getApplication())
            _currentLocation.value = loc
        }
    }
    
    fun toggleLocationSelection(location: GeoLocation) {
        val current = _selectedLocations.value.toMutableList()
        val existing = current.find { it.id == location.id }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(location)
        }
        _selectedLocations.value = current
    }
    
    fun clearSelection() {
        _selectedLocations.value = emptyList()
        _sortedRoute.value = emptyList()
        _routePoints.value = emptyList()
        _routeInfo.value = null
    }
    
    /**
     * Urutkan rute dari lokasi terdekat ke terjauh (dari posisi saat ini),
     * lalu ambil garis rute dari OSRM untuk ditampilkan di peta.
     */
    fun sortRoute() {
        val route = calculateOptimizedRoute()
        _sortedRoute.value = route
        if (route.size >= 2) {
            viewModelScope.launch {
                fetchOsrmRoute(route)
            }
        } else {
            _routePoints.value = emptyList()
            _routeInfo.value = null
        }
    }
    
    /**
     * Ambil garis rute dari OSRM (gratis, tanpa API key).
     * Trip dimulai dari lokasi saya sekarang, lalu ke Trip 1 dst.
     * Profil OSRM: driving untuk Motor/Mobil, walking untuk Jalan Kaki.
     * Mengisi _routePoints (polyline) dan _routeInfo (jarak & durasi).
     */
    private suspend fun fetchOsrmRoute(route: List<GeoLocation>) {
        _isLoadingRoute.value = true
        try {
            val profile = when (_vehicleMode.value) {
                VehicleMode.JALAN_KAKI -> "walking"
                else -> "driving"
            }
            // Titik awal = lokasi saya sekarang, lalu Trip 1 dst
            val allPoints = mutableListOf<GeoPoint>()
            _currentLocation.value?.let { allPoints.add(GeoPoint(it.latitude, it.longitude)) }
            allPoints.addAll(route.map { GeoPoint(it.latitude, it.longitude) })
            val coords = allPoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val url = "https://router.project-osrm.org/route/v1/$profile/$coords?overview=full&geometries=geojson"
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val r = routes.getJSONObject(0)
                    val distanceKm = r.optDouble("distance", 0.0) / 1000.0
                    val durationMin = r.optDouble("duration", 0.0) / 60.0
                    _routeInfo.value = Pair(distanceKm, durationMin)
                    val geom = r.optJSONObject("geometry")
                    val coordsArr = geom?.optJSONArray("coordinates")
                    if (coordsArr != null) {
                        val points = mutableListOf<GeoPoint>()
                        for (i in 0 until coordsArr.length()) {
                            val c = coordsArr.getJSONArray(i)
                            points.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                        }
                        _routePoints.value = points
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: garis lurus dari lokasi saya → Trip 1 dst jika OSRM gagal
            val points = mutableListOf<GeoPoint>()
            _currentLocation.value?.let { points.add(GeoPoint(it.latitude, it.longitude)) }
            points.addAll(route.map { GeoPoint(it.latitude, it.longitude) })
            _routePoints.value = points
            _routeInfo.value = null
        } finally {
            _isLoadingRoute.value = false
        }
    }
    
    /**
     * Calculate optimized route using nearest neighbor TSP algorithm
     * Returns list of locations sorted by shortest path from current location
     */
    fun calculateOptimizedRoute(): List<GeoLocation> {
        val selected = _selectedLocations.value
        if (selected.size < 2) return selected
        
        val currentLoc = _currentLocation.value
        
        // If no current location, return as-is
        if (currentLoc == null) return selected
        
        // Nearest neighbor algorithm
        val unvisited = selected.toMutableList()
        val route = mutableListOf<GeoLocation>()
        
        var currentLat = currentLoc.latitude
        var currentLng = currentLoc.longitude
        
        while (unvisited.isNotEmpty()) {
            // Find nearest location
            var nearest: GeoLocation? = null
            var minDistance = Double.MAX_VALUE
            
            for (loc in unvisited) {
                val distance = LocationUtils.calculateDistance(
                    currentLat, currentLng,
                    loc.latitude, loc.longitude
                ).toDouble()
                if (distance < minDistance) {
                    minDistance = distance
                    nearest = loc
                }
            }
            
            nearest?.let {
                route.add(it)
                unvisited.remove(it)
                currentLat = it.latitude
                currentLng = it.longitude
            }
        }
        
        return route
    }
}
