package com.example.jeia.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jeia.domain.AckStatus
import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertDispatcher
import com.example.jeia.domain.EmergencyAlertResult
import com.example.jeia.domain.EmergencyContactsRepository
import com.example.jeia.domain.LocationProvider
import com.example.jeia.domain.RingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlertUiState {
  data class Countdown(val secondsLeft: Int) : AlertUiState

  data object Sending : AlertUiState

  data class Sent(val result: EmergencyAlertResult) : AlertUiState

  data object Cancelled : AlertUiState
}

/**
 * Flux d'alerte :
 * 1. Compte à rebours d'annulation (configurable).
 * 2. Récupération de la position GPS précise.
 * 3. Envoi de l'alerte aux contacts d'urgence et au backend.
 * 4. Écriture de l'ACK vers la bague (la LED clignote 2 fois pour confirmer).
 */
class AlertViewModel(
  private val repository: RingRepository,
  private val locationProvider: LocationProvider,
  private val emergencyContactsRepository: EmergencyContactsRepository,
  private val alertDispatcher: AlertDispatcher,
  private val counter: Int,
  countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
) : ViewModel() {

  private val _uiState = MutableStateFlow<AlertUiState>(AlertUiState.Countdown(countdownSeconds))
  val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

  private val alertJob: Job =
    viewModelScope.launch {
      for (seconds in countdownSeconds downTo 1) {
        _uiState.value = AlertUiState.Countdown(seconds)
        delay(1_000)
      }
      _uiState.value = AlertUiState.Sending

      // 1. Position GPS
      val location = locationProvider.getCurrentLocation()

      // 2. Contacts d'urgence
      val contacts = emergencyContactsRepository.contacts.value

      // 3. Envoi de l'alerte
      val result = alertDispatcher.dispatchAlert(counter, location, contacts)

      // 4. Acquittement vers la bague pour déclencher le clignotement de confirmation
      val ackStatus = if (result.success) AckStatus.SENT else AckStatus.FAILED
      repository.sendAck(AlertAck(counter, ackStatus))

      _uiState.value = AlertUiState.Sent(result)
    }

  fun cancel() {
    if (_uiState.value is AlertUiState.Countdown) {
      alertJob.cancel()
      _uiState.value = AlertUiState.Cancelled
    }
  }

  companion object {
    const val DEFAULT_COUNTDOWN_SECONDS = 5
  }
}
