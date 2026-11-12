package com.example.jeia.domain

interface LocationProvider {
  /** Récupère la meilleure position géographique courante (GPS ou réseau). */
  suspend fun getCurrentLocation(): LocationInfo?
}
