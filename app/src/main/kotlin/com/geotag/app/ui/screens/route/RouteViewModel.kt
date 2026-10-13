package com.geotag.app.ui.screens.route

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RouteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    
    private val _selectedLocations = MutableStateFlow<List<GeoLocation>>(emptyList())
    val selectedLocations: StateFlow<List<GeoLocation>> = _selectedLocations
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation
    
    val userId: String? get() = repository.firebase.currentUserId
    
    val locations: StateFlow<List<GeoLocation>> = _searchQuery
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
    
    init {
        refreshLocation()
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
