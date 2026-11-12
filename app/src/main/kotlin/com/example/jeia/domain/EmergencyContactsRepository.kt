package com.example.jeia.domain

import kotlinx.coroutines.flow.StateFlow

interface EmergencyContactsRepository {
  val contacts: StateFlow<List<EmergencyContact>>

  suspend fun addContact(name: String, phoneNumber: String)

  suspend fun removeContact(id: String)
}
