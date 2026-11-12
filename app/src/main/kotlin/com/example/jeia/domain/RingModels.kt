package com.example.jeia.domain

/** Type d'événement envoyé par la bague (caractéristique "Alert Event"). */
enum class AlertType(val code: Int) {
  ALERT(1),
  CANCEL(2);

  companion object {
    fun fromCode(code: Int): AlertType? = entries.firstOrNull { it.code == code }
  }
}

/** Événement reçu de la bague. Le [counter] sert à dédupliquer les alertes après une reconnexion. */
data class AlertEvent(val type: AlertType, val counter: Int)

/** Statut renvoyé à la bague (caractéristique "Alert Ack"). */
enum class AckStatus(val code: Int) {
  SENT(0),
  FAILED(1),
}

/** Acquittement écrit vers la bague : sa LED clignote 2 fois quand l'alerte est bien partie. */
data class AlertAck(val counter: Int, val status: AckStatus)

enum class RingConnectionState {
  DISCONNECTED,
  SCANNING,
  CONNECTED,
}

/** Type de source de la bague (matériel réel ou simulateur). */
enum class RingSourceType {
  SIMULATION,
  BLUETOOTH,
}

/** Coordonnées et détails de localisation GPS lors d'une alerte. */
data class LocationInfo(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float? = null,
  val timestamp: Long = System.currentTimeMillis(),
) {
  val googleMapsUrl: String
    get() = "https://maps.google.com/?q=$latitude,$longitude"
}

/** Contact d'urgence prévenu en cas de SOS. */
data class EmergencyContact(
  val id: String,
  val name: String,
  val phoneNumber: String,
)

/** Résultat de la diffusion d'une alerte d'urgence. */
data class EmergencyAlertResult(
  val success: Boolean,
  val location: LocationInfo?,
  val contactsNotifiedCount: Int,
  val message: String,
)

