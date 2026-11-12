package com.example.jeia.data.ble

import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertEvent
import com.example.jeia.domain.AlertType
import java.util.UUID

/**
 * Contrat BLE partagé avec le firmware (ESP32 puis DA14531).
 * Voir `plan-prototype-esp32-android.md`, section 4.
 *
 * Les UUID sont des exemples du plan : à régénérer avec `uuidgen` puis à garder identiques côté firmware.
 */
object JeiaBleContract {
  const val DEVICE_NAME = "JEIA Ring"

  val SERVICE_UUID: UUID = UUID.fromString("4a450001-7c1d-4a8e-9b3f-2f6d8c1a0b11")

  /** Notify : `uint8 type` + `uint16 compteur` (petit-boutiste). */
  val ALERT_EVENT_UUID: UUID = UUID.fromString("4a450002-7c1d-4a8e-9b3f-2f6d8c1a0b11")

  /** Write : `uint16 compteur acquitté` + `uint8 statut`. */
  val ALERT_ACK_UUID: UUID = UUID.fromString("4a450003-7c1d-4a8e-9b3f-2f6d8c1a0b11")

  /** Décode une notification `Alert Event`, ou null si le paquet est invalide. */
  fun decodeAlertEvent(bytes: ByteArray): AlertEvent? {
    if (bytes.size < 3) return null
    val type = AlertType.fromCode(bytes[0].toInt() and 0xFF) ?: return null
    val counter = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
    return AlertEvent(type, counter)
  }

  /** Encode un `Alert Ack` à écrire dans la caractéristique. */
  fun encodeAlertAck(ack: AlertAck): ByteArray =
    byteArrayOf(
      (ack.counter and 0xFF).toByte(),
      ((ack.counter shr 8) and 0xFF).toByte(),
      ack.status.code.toByte(),
    )
}
