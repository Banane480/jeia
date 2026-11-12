package com.example.jeia.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jeia.domain.EmergencyContact
import com.example.jeia.domain.EmergencyContactsRepository
import com.example.jeia.domain.LocationInfo
import com.example.jeia.domain.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
  val contacts: List<EmergencyContact> = emptyList(),
  val isGpsTesting: Boolean = false,
  val testLocation: LocationInfo? = null,
)

class EmergencyContactsViewModel(
  private val contactsRepository: EmergencyContactsRepository,
  private val locationProvider: LocationProvider,
) : ViewModel() {

  private val _testGpsState = MutableStateFlow<Pair<Boolean, LocationInfo?>>(false to null)

  val contacts: StateFlow<List<EmergencyContact>> =
    contactsRepository.contacts.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptyList(),
    )

  val testGpsState: StateFlow<Pair<Boolean, LocationInfo?>> = _testGpsState.asStateFlow()

  fun addContact(name: String, phone: String) {
    if (name.isBlank() || phone.isBlank()) return
    viewModelScope.launch {
      contactsRepository.addContact(name, phone)
    }
  }

  fun removeContact(id: String) {
    viewModelScope.launch {
      contactsRepository.removeContact(id)
    }
  }

  fun testGps() {
    viewModelScope.launch {
      _testGpsState.value = true to null
      val loc = locationProvider.getCurrentLocation()
      _testGpsState.value = false to loc
    }
  }
}
