package com.example.jeia

import android.app.Application
import com.example.jeia.data.FakeRingRepository
import com.example.jeia.domain.RingRepository

/**
 * Injection de dépendances manuelle (Koin viendra si le projet grossit).
 * Pour passer au vrai BLE, remplacer [ringRepository] par `BleRingRepository`.
 */
class AppContainer {
  val fakeRing = FakeRingRepository()
  val ringRepository: RingRepository = fakeRing
}

class JeiaApplication : Application() {
  val container = AppContainer()
}
