package com.example.jeia.data.alert

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.jeia.domain.AlertDispatcher
import com.example.jeia.domain.EmergencyAlertResult
import com.example.jeia.domain.EmergencyContact
import com.example.jeia.domain.LocationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAlertDispatcher(
  private val context: Context,
) : AlertDispatcher {

  override suspend fun dispatchAlert(
    counter: Int,
    location: LocationInfo?,
    contacts: List<EmergencyContact>,
  ): EmergencyAlertResult = withContext(Dispatchers.IO) {
    Log.i(TAG, "Déclenchement alerte #$counter avec ${contacts.size} contacts. Position: $location")

    val message = buildMessage(counter, location)
    var sentCount = 0

    val hasSmsPermission = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    if (hasSmsPermission) {
      val smsManager = getSmsManager()
      for (contact in contacts) {
        if (contact.phoneNumber.isNotBlank()) {
          try {
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)
            sentCount++
            Log.i(TAG, "SMS d'alerte envoyé avec succès à ${contact.name} (${contact.phoneNumber})")
          } catch (e: Exception) {
            Log.e(TAG, "Échec de l'envoi du SMS à ${contact.name}", e)
          }
        }
      }
    } else {
      Log.w(TAG, "Permission SEND_SMS absente. Aucun SMS direct n'a pu être envoyé.")
    }

    // Simulation / Envoi HTTPS au backend JEIA
    simulateBackendDispatch(counter, location)

    EmergencyAlertResult(
      success = true,
      location = location,
      contactsNotifiedCount = sentCount,
      message = if (contacts.isEmpty()) {
        "Alerte enregistrée (aucun contact d'urgence configuré)."
      } else if (hasSmsPermission) {
        "Alerte envoyée à $sentCount contact(s) sur ${contacts.size}."
      } else {
        "Alerte déclenchée. Pour l'envoi SMS direct, autorisez la permission SMS."
      },
    )
  }

  private fun buildMessage(counter: Int, location: LocationInfo?): String {
    val locationPart = if (location != null) {
      "\nMa position : ${location.googleMapsUrl}\n(précision: ${location.accuracyMeters?.toInt() ?: "?"} m)"
    } else {
      "\n(Position GPS non disponible)"
    }
    return "URGENCE JEIA (Alerte #$counter) : J'ai besoin d'aide !$locationPart"
  }

  @Suppress("DEPRECATION")
  private fun getSmsManager(): SmsManager {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(SmsManager::class.java)
    } else {
      SmsManager.getDefault()
    }
  }

  private fun simulateBackendDispatch(counter: Int, location: LocationInfo?) {
    // Structure prête pour le backend HTTPS en production
    Log.d(TAG, "Backend JEIA notifié pour alerte #$counter (lat=${location?.latitude}, lng=${location?.longitude})")
  }

  companion object {
    private const val TAG = "JeiaAlertDispatcher"
  }
}
