package com.example.jeia.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jeia.domain.AckStatus
import com.example.jeia.domain.AlertAck
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

  data object Sent : AlertUiState

  data object Cancelled : AlertUiState
}

/**
 * Flux d'alerte : compte à rebours d'annulation, puis envoi, puis ACK vers la bague.
 */
class AlertViewModel(
  private val repository: RingRepository,
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
      // TODO phase 6 : position GPS puis envoi HTTPS au backend (avec file persistante).
      repository.sendAck(AlertAck(counter, AckStatus.SENT))
      _uiState.value = AlertUiState.Sent
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
