package com.example.jeia

import android.app.Application
import android.content.Context
import com.example.jeia.data.FakeRingRepository
import com.example.jeia.data.SharedPreferencesEmergencyContactsRepository
import com.example.jeia.data.SwitchableRingRepository
import com.example.jeia.data.alert.AndroidAlertDispatcher
import com.example.jeia.data.ble.BleRingRepository
import com.example.jeia.data.location.AndroidLocationProvider
import com.example.jeia.domain.AlertDispatcher
import com.example.jeia.domain.EmergencyContactsRepository
import com.example.jeia.domain.LocationProvider
import com.example.jeia.domain.RingRepository

/**
 * Injection de dépendances de l'application Jeia.
 * Supporte le mode Simulation et le mode Bluetooth Low Energy réel.
 */
class AppContainer(context: Context) {
  val fakeRing = FakeRingRepository()
  val bleRing = BleRingRepository(context)
  val switchableRing = SwitchableRingRepository(fakeRing, bleRing)

  val ringRepository: RingRepository = switchableRing

  val emergencyContactsRepository: EmergencyContactsRepository =
    SharedPreferencesEmergencyContactsRepository(context)

  val locationProvider: LocationProvider =
    AndroidLocationProvider(context)

  val alertDispatcher: AlertDispatcher =
    AndroidAlertDispatcher(context)
}

class JeiaApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
