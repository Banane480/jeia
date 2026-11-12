package com.example.jeia.data

import com.example.jeia.data.ble.BleRingRepository
import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertEvent
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingRepository
import com.example.jeia.domain.RingSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Repository permettant de basculer dynamiquement entre le mode Simulation (sans matériel)
 * et le mode Bluetooth Low Energy réel (ESP32 / DA14531).
 */
class SwitchableRingRepository(
  val fakeRepository: FakeRingRepository,
  val bleRepository: BleRingRepository,
) : RingRepository {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _sourceType = MutableStateFlow(RingSourceType.SIMULATION)
  val sourceType: StateFlow<RingSourceType> = _sourceType.asStateFlow()

  private val activeRepository: RingRepository
    get() = when (_sourceType.value) {
      RingSourceType.SIMULATION -> fakeRepository
      RingSourceType.BLUETOOTH -> bleRepository
    }

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  override val connectionState: StateFlow<RingConnectionState> =
    _sourceType.flatMapLatest { source ->
      when (source) {
        RingSourceType.SIMULATION -> fakeRepository.connectionState
        RingSourceType.BLUETOOTH -> bleRepository.connectionState
      }
    }.stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue = RingConnectionState.DISCONNECTED,
    )

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  override val alertEvents: Flow<AlertEvent> =
    _sourceType.flatMapLatest { source ->
      when (source) {
        RingSourceType.SIMULATION -> fakeRepository.alertEvents
        RingSourceType.BLUETOOTH -> bleRepository.alertEvents
      }
    }

  fun setSource(source: RingSourceType) {
    if (_sourceType.value == source) return
    scope.launch {
      activeRepository.disconnect()
      _sourceType.value = source
    }
  }

  override suspend fun connect() {
    activeRepository.connect()
  }

  override suspend fun disconnect() {
    activeRepository.disconnect()
  }

  override suspend fun sendAck(ack: AlertAck) {
    activeRepository.sendAck(ack)
  }
}
