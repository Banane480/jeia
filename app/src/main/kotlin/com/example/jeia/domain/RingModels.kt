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
