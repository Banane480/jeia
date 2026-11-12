package com.example.jeia.data

import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertEvent
import com.example.jeia.domain.AlertType
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bague simulée : permet de développer l'appli sans matériel (menu développeur). */
class FakeRingRepository : RingRepository {

  private val _connectionState = MutableStateFlow(RingConnectionState.DISCONNECTED)
  override val connectionState: StateFlow<RingConnectionState> = _connectionState.asStateFlow()

  private val _alertEvents = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 8)
  override val alertEvents: Flow<AlertEvent> = _alertEvents

  private val _lastAck = MutableStateFlow<AlertAck?>(null)

  /** Dernier acquittement "écrit" vers la bague (visible dans le menu développeur). */
  val lastAck: StateFlow<AlertAck?> = _lastAck.asStateFlow()

  private var counter = 0

  override suspend fun connect() {
    _connectionState.value = RingConnectionState.SCANNING
    delay(1_000)
    _connectionState.value = RingConnectionState.CONNECTED
  }

  override suspend fun disconnect() {
    _connectionState.value = RingConnectionState.DISCONNECTED
  }

  override suspend fun sendAck(ack: AlertAck) {
    _lastAck.value = ack
  }

  /** Simule un appui long sur le bouton (ignoré si la bague n'est pas connectée). */
  fun simulateAlert() {
    if (_connectionState.value != RingConnectionState.CONNECTED) return
    counter++
    _alertEvents.tryEmit(AlertEvent(AlertType.ALERT, counter))
  }

  /** Simule une annulation côté bague. */
  fun simulateCancel() {
    if (_connectionState.value != RingConnectionState.CONNECTED) return
    _alertEvents.tryEmit(AlertEvent(AlertType.CANCEL, counter))
  }
}
