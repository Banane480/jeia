package com.example.jeia.data

import android.content.Context
import android.content.SharedPreferences
import com.example.jeia.domain.EmergencyContact
import com.example.jeia.domain.EmergencyContactsRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesEmergencyContactsRepository(
  context: Context,
) : EmergencyContactsRepository {

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
  override val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

  init {
    loadContacts()
  }

  private fun loadContacts() {
    val raw = prefs.getString(KEY_CONTACTS, null) ?: return
    try {
      val jsonArray = JSONArray(raw)
      val list = ArrayList<EmergencyContact>(jsonArray.length())
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          EmergencyContact(
            id = obj.getString("id"),
            name = obj.getString("name"),
            phoneNumber = obj.getString("phone"),
          )
        )
      }
      _contacts.value = list
    } catch (_: Exception) {
      _contacts.value = emptyList()
    }
  }

  private fun saveContacts(list: List<EmergencyContact>) {
    val jsonArray = JSONArray()
    for (contact in list) {
      val obj = JSONObject()
      obj.put("id", contact.id)
      obj.put("name", contact.name)
      obj.put("phone", contact.phoneNumber)
      jsonArray.put(obj)
    }
    prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
    _contacts.value = list
  }

  override suspend fun addContact(name: String, phoneNumber: String) {
    val newContact =
      EmergencyContact(
        id = UUID.randomUUID().toString(),
        name = name.trim(),
        phoneNumber = phoneNumber.trim(),
      )
    val updated = _contacts.value + newContact
    saveContacts(updated)
  }

  override suspend fun removeContact(id: String) {
    val updated = _contacts.value.filterNot { it.id == id }
    saveContacts(updated)
  }

  companion object {
    private const val PREFS_NAME = "jeia_emergency_contacts"
    private const val KEY_CONTACTS = "contacts_json"
  }
}
