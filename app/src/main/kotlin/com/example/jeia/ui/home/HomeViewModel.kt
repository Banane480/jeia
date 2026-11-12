package com.example.jeia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val connection: RingConnectionState = RingConnectionState.DISCONNECTED)

class HomeViewModel(private val repository: RingRepository) : ViewModel() {

  val uiState: StateFlow<HomeUiState> =
    repository.connectionState
      .map { HomeUiState(connection = it) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
      )

  fun connect() {
    viewModelScope.launch { repository.connect() }
  }

  fun disconnect() {
    viewModelScope.launch { repository.disconnect() }
  }
}
