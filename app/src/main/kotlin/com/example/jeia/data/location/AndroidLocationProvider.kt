package com.example.jeia.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.jeia.domain.LocationInfo
import com.example.jeia.domain.LocationProvider
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidLocationProvider(
  private val context: Context,
) : LocationProvider {

  private val locationManager: LocationManager? =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  @SuppressLint("MissingPermission")
  override suspend fun getCurrentLocation(): LocationInfo? = withContext(Dispatchers.IO) {
    if (!hasLocationPermission()) return@withContext null
    val manager = locationManager ?: return@withContext null

    // 1. Essai de la dernière position connue pour réactivité immédiate
    val lastGps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    val lastNet = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    val bestLast = pickBestLocation(lastGps, lastNet)

    // Si la dernière position a moins de 60 secondes, on peut l'utiliser
    if (bestLast != null && System.currentTimeMillis() - bestLast.time < 60_000) {
      return@withContext bestLast.toLocationInfo()
    }

    // 2. Sinon, acquisition d'une nouvelle position avec un timeout de 4 secondes
    val freshLocation = withTimeoutOrNull(4_000) {
      requestSingleFreshLocation(manager)
    }

    return@withContext (freshLocation ?: bestLast)?.toLocationInfo()
  }

  @SuppressLint("MissingPermission")
  private suspend fun requestSingleFreshLocation(manager: LocationManager): Location? =
    suspendCancellableCoroutine { continuation ->
      val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
          manager.removeUpdates(this)
          if (continuation.isActive) continuation.resume(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
      }

      continuation.invokeOnCancellation {
        try {
          manager.removeUpdates(listener)
        } catch (_: Exception) {}
      }

      try {
        var requested = false
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
          manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            listener,
            context.mainLooper,
          )
          requested = true
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
          manager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            0L,
            0f,
            listener,
            context.mainLooper,
          )
          requested = true
        }
        if (!requested) {
          continuation.resume(null)
        }
      } catch (e: Exception) {
        if (continuation.isActive) continuation.resume(null)
      }
    }

  private fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  private fun pickBestLocation(a: Location?, b: Location?): Location? = when {
    a == null -> b
    b == null -> a
    a.time > b.time -> a
    else -> b
  }

  private fun Location.toLocationInfo(): LocationInfo = LocationInfo(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    timestamp = time,
  )
}
