package com.geotag.app.ui.screens.home

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val userId: String? get() = repository.firebase.currentUserId
    val userName: String? get() = repository.firebase.currentUserName
    val userEmail: String? get() = repository.firebase.currentUserEmail

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
        syncFromRemote()
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
            userId?.let { repository.syncFromRemote(it) }
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
    }
}
