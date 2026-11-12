package com.example.jeia.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jeia.data.SwitchableRingRepository
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingSourceType
import com.example.jeia.service.JeiaRingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
  val connection: RingConnectionState = RingConnectionState.DISCONNECTED,
  val sourceType: RingSourceType = RingSourceType.SIMULATION,
  val isServiceActive: Boolean = false,
)

class HomeViewModel(
  private val repository: SwitchableRingRepository,
  context: Context,
) : ViewModel() {

  private val appContext = context.applicationContext
  private val _serviceActive = MutableStateFlow(false)

  val uiState: StateFlow<HomeUiState> =
    combine(
      repository.connectionState,
      repository.sourceType,
      _serviceActive,
    ) { conn, source, svcActive ->
      HomeUiState(
        connection = conn,
        sourceType = source,
        isServiceActive = svcActive,
      )
    }.stateIn(
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

  fun setSource(source: RingSourceType) {
    repository.setSource(source)
  }

  fun toggleService() {
    val willBeActive = !_serviceActive.value
    _serviceActive.value = willBeActive
    if (willBeActive) {
      JeiaRingService.start(appContext)
    } else {
      JeiaRingService.stop(appContext)
    }
  }
}
