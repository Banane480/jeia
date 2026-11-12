package com.example.jeia.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

internal fun isRecentLocation(locationTimeNanos: Long, currentTimeNanos: Long): Boolean =
  locationTimeNanos > 0 && locationTimeNanos <= currentTimeNanos &&
    currentTimeNanos - locationTimeNanos <= 30_000_000_000L

@Composable
fun rememberLocationMapsLauncher(): () -> Unit {
  val context = LocalContext.current
  val locationMaps = remember(context) { LocationMaps(context.applicationContext) }
  val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) locationMaps.open() else locationMaps.message("Autorise la localisation pour ouvrir la carte.")
  }
  DisposableEffect(locationMaps) { onDispose { locationMaps.cancel() } }
  return {
    if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
      locationMaps.open()
    } else {
      launcher.launch(permissions)
    }
  }
}

private class LocationMaps(private val context: Context) {
  private val manager = context.getSystemService(LocationManager::class.java)
  private val handler = Handler(Looper.getMainLooper())
  private var listener: LocationListener? = null
  private val timeout = Runnable {
    cancel()
    message("Position indisponible. Réessaie dans un endroit dégagé.")
  }

  @SuppressLint("MissingPermission")
  fun open() {
    cancel()
    val manager = manager ?: run {
      message("La localisation n’est pas disponible sur ce téléphone.")
      return
    }
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val providers = listOfNotNull(
      LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) },
      LocationManager.GPS_PROVIDER.takeIf { fineGranted && manager.isProviderEnabled(it) },
    )
    if (providers.isEmpty()) {
      message("Active la localisation du téléphone et vérifie son autorisation.")
      return
    }
    val locationListener = object : LocationListener {
      override fun onLocationChanged(location: Location) {
        if (listener !== this) return
        if (!isRecentLocation(location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos()) ||
          !location.latitude.isFinite() || !location.longitude.isFinite()
        ) return
        cancel()
        val uri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
          .appendQueryParameter("api", "1")
          .appendQueryParameter("query", "${location.latitude},${location.longitude}")
          .build()
        try {
          context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
          message("Impossible d’ouvrir Google Maps sur ce téléphone.")
        }
      }
    }
    listener = locationListener
    var registered = false
    for (provider in providers) {
      try {
        manager.requestLocationUpdates(provider, 0L, 0f, locationListener, Looper.getMainLooper())
        registered = true
      } catch (_: SecurityException) {
        // A permission may have been revoked since the request was started.
      } catch (_: IllegalArgumentException) {
        // A provider can disappear when the device settings change.
      }
    }
    if (registered) {
      message("Recherche de la position du téléphone…")
      handler.postDelayed(timeout, 30_000)
    } else {
      cancel()
      message("Impossible d’obtenir la position du téléphone.")
    }
  }

  fun cancel() {
    handler.removeCallbacks(timeout)
    listener?.let {
      try {
        manager?.removeUpdates(it)
      } catch (_: SecurityException) {
        // A permission may have been revoked during the request.
      }
    }
    listener = null
  }

  fun message(text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
  }
}
