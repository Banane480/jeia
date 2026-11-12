package com.example.jeia.ui.alert

import com.example.jeia.domain.AckStatus
import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertDispatcher
import com.example.jeia.domain.AlertEvent
import com.example.jeia.domain.EmergencyAlertResult
import com.example.jeia.domain.EmergencyContact
import com.example.jeia.domain.EmergencyContactsRepository
import com.example.jeia.domain.LocationInfo
import com.example.jeia.domain.LocationProvider
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val mockRepository = object : RingRepository {
    val acks = mutableListOf<AlertAck>()
    override val connectionState: StateFlow<RingConnectionState> =
      MutableStateFlow(RingConnectionState.CONNECTED)
    override val alertEvents: Flow<AlertEvent> = MutableSharedFlow()
    override suspend fun connect() {}
    override suspend fun disconnect() {}
    override suspend fun sendAck(ack: AlertAck) {
      acks.add(ack)
    }
  }

  private val mockLocationProvider = object : LocationProvider {
    override suspend fun getCurrentLocation(): LocationInfo =
      LocationInfo(48.8566, 2.3522, 5f)
  }

  private val mockContactsRepository = object : EmergencyContactsRepository {
    override val contacts: StateFlow<List<EmergencyContact>> =
      MutableStateFlow(listOf(EmergencyContact("1", "Alice", "0601020304")))
    override suspend fun addContact(name: String, phoneNumber: String) {}
    override suspend fun removeContact(id: String) {}
  }

  private val mockAlertDispatcher = object : AlertDispatcher {
    var dispatched = false
    override suspend fun dispatchAlert(
      counter: Int,
      location: LocationInfo?,
      contacts: List<EmergencyContact>,
    ): EmergencyAlertResult {
      dispatched = true
      return EmergencyAlertResult(
        success = true,
        location = location,
        contactsNotifiedCount = contacts.size,
        message = "Alerte test envoyée",
      )
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun countdownCompletes_sendsAlertAndAck() = runTest(testDispatcher) {
    val viewModel = AlertViewModel(
      repository = mockRepository,
      locationProvider = mockLocationProvider,
      emergencyContactsRepository = mockContactsRepository,
      alertDispatcher = mockAlertDispatcher,
      counter = 42,
      countdownSeconds = 2,
    )

    assertTrue(viewModel.uiState.value is AlertUiState.Countdown)

    // Avance de 2 secondes
    advanceTimeBy(2_500)

    val state = viewModel.uiState.value
    assertTrue("L'état devrait être Sent mais est $state", state is AlertUiState.Sent)
    assertTrue("Le dispatcher devrait être appelé", mockAlertDispatcher.dispatched)
    assertEquals(1, mockRepository.acks.size)
    assertEquals(AlertAck(42, AckStatus.SENT), mockRepository.acks.first())
  }

  @Test
  fun cancel_stopsCountdown() = runTest(testDispatcher) {
    val viewModel = AlertViewModel(
      repository = mockRepository,
      locationProvider = mockLocationProvider,
      emergencyContactsRepository = mockContactsRepository,
      alertDispatcher = mockAlertDispatcher,
      counter = 42,
      countdownSeconds = 5,
    )

    viewModel.cancel()
    assertEquals(AlertUiState.Cancelled, viewModel.uiState.value)

    advanceTimeBy(10_000)
    assertEquals(AlertUiState.Cancelled, viewModel.uiState.value)
    assertEquals(0, mockRepository.acks.size)
  }
}
