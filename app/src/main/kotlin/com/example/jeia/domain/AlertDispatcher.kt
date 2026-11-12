package com.example.jeia.domain

interface AlertDispatcher {
  /**
   * Diffuse l'alerte d'urgence aux contacts et au backend.
   */
  suspend fun dispatchAlert(
    counter: Int,
    location: LocationInfo?,
    contacts: List<EmergencyContact>,
  ): EmergencyAlertResult
}
