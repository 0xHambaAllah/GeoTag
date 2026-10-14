package com.geotag.app.ui.screens.camera

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotag.app.data.model.GeoLocation
import com.geotag.app.data.repository.LocationRepository
import com.geotag.app.util.LocationUtils
import com.geotag.app.util.PhotoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class CameraUiState(
    val photoFile: File? = null,
    val photoTaken: Boolean = false,
    val location: Location? = null,
    val address: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val isSaving: Boolean = false,
    val isGettingLocation: Boolean = true,
    val error: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    init {
        getLocation()
    }

    fun getLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGettingLocation = true)
            val location = LocationUtils.getCurrentLocation(getApplication())
            val address = location?.let {
                LocationUtils.getAddressFromLocation(getApplication(), it.latitude, it.longitude)
            } ?: ""
            _uiState.value = _uiState.value.copy(
                location = location,
                address = address,
                isGettingLocation = false
            )
        }
    }

    fun createPhotoFile(): File {
        val file = PhotoUtils.createImageFile(getApplication())
        _uiState.value = _uiState.value.copy(photoFile = file)
        return file
    }

    fun onPhotoTaken() {
        _uiState.value = _uiState.value.copy(photoTaken = true)
        // Refresh location when photo is taken
        getLocation()
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onPhoneChange(phone: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = phone)
    }

    fun saveLocation(onSuccess: () -> Unit) {
        val state = _uiState.value
        val location = state.location
        val photoFile = state.photoFile

        if (state.name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Nama alamat wajib diisi.")
            return
        }
        if (location == null) {
            _uiState.value = _uiState.value.copy(error = "Lokasi belum tersedia. Pastikan GPS aktif.")
            return
        }
        if (photoFile == null || !photoFile.exists()) {
            _uiState.value = _uiState.value.copy(error = "Foto belum diambil.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val userId = repository.firebase.currentUserId ?: throw Exception("User not logged in")
                val geoLocation = GeoLocation(
                    userId = userId,
                    creatorEmail = repository.firebase.currentUserEmail ?: "",
                    name = state.name,
                    phoneNumber = state.phoneNumber,
                    photoLocalPath = photoFile.absolutePath,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = state.address,
                    timestamp = System.currentTimeMillis(),
                    isSynced = false
                )
                repository.saveLocation(geoLocation)
                
                // Trigger auto backup
                com.geotag.app.data.remote.SyncWorker.enqueue(getApplication())
                
                // Instant feedback
                android.widget.Toast.makeText(
                    getApplication(),
                    "Foto disimpan! Backup ke cloud sedang berjalan...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Gagal menyimpan: ${e.message}"
                )
            }
        }
    }
}
