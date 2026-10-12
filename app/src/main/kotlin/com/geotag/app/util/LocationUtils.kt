package com.geotag.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object LocationUtils {
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCoroutine { cont ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(5000)
                .build()
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(context: Context): Flow<Location> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun formatDistance(meters: Float): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            String.format("%.1f km", meters / 1000)
        }
    }

    fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.let { addr ->
                buildString {
                    addr.thoroughfare?.let { append(it) }
                    addr.subLocality?.let { if (isNotEmpty()) append(", "); append(it) }
                    addr.locality?.let { if (isNotEmpty()) append(", "); append(it) }
                    addr.subAdminArea?.let { if (isNotEmpty()) append(", "); append(it) }
                }
            } ?: "Lokasi tidak diketahui"
        } catch (_: Exception) {
            "Lokasi tidak diketahui"
        }
    }
}
