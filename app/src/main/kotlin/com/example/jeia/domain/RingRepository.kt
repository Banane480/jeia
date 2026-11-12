package com.example.jeia.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction de la bague JEIA.
 *
 * Deux implémentations prévues :
 * - `FakeRingRepository` : simule la bague (phase 2, sans matériel) ;
 * - `BleRingRepository` : vrai BLE (phases 3 et suivantes).
 */
interface RingRepository {
  val connectionState: StateFlow<RingConnectionState>

  /** Événements d'alerte reçus de la bague. */
  val alertEvents: Flow<AlertEvent>

  suspend fun connect()

  suspend fun disconnect()

  suspend fun sendAck(ack: AlertAck)
}
