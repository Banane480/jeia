package com.example.jeia.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jeia.R
import com.example.jeia.domain.EmergencyContact
import com.example.jeia.domain.LocationInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
  viewModel: EmergencyContactsViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val contacts by viewModel.contacts.collectAsStateWithLifecycle()
  val (isTestingGps, testLocation) = viewModel.testGpsState.collectAsStateWithLifecycle().value

  var nameInput by remember { mutableStateOf("") }
  var phoneInput by remember { mutableStateOf("") }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.contacts_title)) },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Formulaire d'ajout
      item {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = stringResource(R.string.contacts_add),
              style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
              value = nameInput,
              onValueChange = { nameInput = it },
              label = { Text(stringResource(R.string.contacts_name_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = phoneInput,
              onValueChange = { phoneInput = it },
              label = { Text(stringResource(R.string.contacts_phone_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              onClick = {
                viewModel.addContact(nameInput, phoneInput)
                nameInput = ""
                phoneInput = ""
              },
              enabled = nameInput.isNotBlank() && phoneInput.isNotBlank(),
              modifier = Modifier.align(Alignment.End),
            ) {
              Text(stringResource(R.string.contacts_save))
            }
          }
        }
      }

      // 2. Liste des contacts
      item {
        Text(
          text = "Contacts enregistrés (${contacts.size})",
          style = MaterialTheme.typography.titleMedium,
        )
      }

      if (contacts.isEmpty()) {
        item {
          Text(
            text = stringResource(R.string.contacts_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
          )
        }
      } else {
        items(contacts, key = { it.id }) { contact ->
          ContactItem(
            contact = contact,
            onDelete = { viewModel.removeContact(contact.id) },
          )
        }
      }

      // 3. Test de position GPS
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Position GPS pour les alertes",
              style = MaterialTheme.typography.titleMedium,
            )
            if (isTestingGps) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text(
                  text = stringResource(R.string.contacts_gps_acquiring),
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            } else if (testLocation != null) {
              Text(
                text = "Dernière position : %.5f, %.5f (±%dm)".format(
                  testLocation.latitude,
                  testLocation.longitude,
                  testLocation.accuracyMeters?.toInt() ?: 0,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Button(
              onClick = viewModel::testGps,
              enabled = !isTestingGps,
            ) {
              Text(stringResource(R.string.contacts_gps_test))
            }
          }
        }
      }

      // 4. Bouton Retour
      item {
        OutlinedButton(
          onClick = onBack,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(R.string.dev_back))
        }
      }
    }
  }
}

@Composable
private fun ContactItem(
  contact: EmergencyContact,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(text = contact.name, style = MaterialTheme.typography.titleSmall)
        Text(
          text = contact.phoneNumber,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      OutlinedButton(onClick = onDelete) {
        Text(stringResource(R.string.contacts_delete))
      }
    }
  }
}
