package com.example.jeia.ui.home

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jeia.R
import com.example.jeia.data.rememberLocationMapsLauncher
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.domain.RingSourceType
import com.example.jeia.ui.theme.JeiaTheme

@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onOpenDevMenu: () -> Unit,
  onOpenEmergencyContacts: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val openLocation = rememberLocationMapsLauncher()

  // Lanceur de permissions pour Android
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { /* callback */ }

  LaunchedEffect(Unit) {
    val permissions = mutableListOf(
      android.Manifest.permission.ACCESS_FINE_LOCATION,
      android.Manifest.permission.ACCESS_COARSE_LOCATION,
      android.Manifest.permission.SEND_SMS,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
      permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    permissionLauncher.launch(permissions.toTypedArray())
  }

  HomeContent(
    state = uiState,
    onConnect = viewModel::connect,
    onDisconnect = viewModel::disconnect,
    onSetSource = viewModel::setSource,
    onToggleService = viewModel::toggleService,
    onOpenDevMenu = onOpenDevMenu,
    onOpenEmergencyContacts = onOpenEmergencyContacts,
    onOpenLocation = openLocation,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
  state: HomeUiState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onSetSource: (RingSourceType) -> Unit,
  onToggleService: () -> Unit,
  onOpenDevMenu: () -> Unit,
  onOpenEmergencyContacts: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenLocation: () -> Unit = {},
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(20.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // 1. Choix du mode : Simulation vs BLE réel
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.home_source_mode),
            style = MaterialTheme.typography.titleMedium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = state.sourceType == RingSourceType.SIMULATION,
              onClick = { onSetSource(RingSourceType.SIMULATION) },
              label = { Text(stringResource(R.string.home_source_sim)) },
            )
            FilterChip(
              selected = state.sourceType == RingSourceType.BLUETOOTH,
              onClick = { onSetSource(RingSourceType.BLUETOOTH) },
              label = { Text(stringResource(R.string.home_source_ble)) },
            )
          }
        }
      }

      // 2. Statut de connexion et bouton de connexion
      Card(
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          val stateLabel = stringResource(
            when (state.connection) {
              RingConnectionState.DISCONNECTED -> R.string.ring_state_disconnected
              RingConnectionState.SCANNING -> R.string.ring_state_scanning
              RingConnectionState.CONNECTED -> R.string.ring_state_connected
            }
          )
          Text(
            text = stringResource(R.string.home_ring_status, stateLabel),
            style = MaterialTheme.typography.titleLarge,
          )

          when (state.connection) {
            RingConnectionState.DISCONNECTED ->
              Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(stringResource(R.string.home_connect))
              }
            RingConnectionState.SCANNING ->
              Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Arrêter la recherche")
              }
            RingConnectionState.CONNECTED ->
              OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(stringResource(R.string.home_disconnect))
              }
          }
        }
      }

      // 3. Option Foreground Service
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.home_bg_service),
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = stringResource(R.string.home_bg_service_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )
          }
          Switch(
            checked = state.isServiceActive,
            onCheckedChange = { onToggleService() },
          )
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("Votre position", style = MaterialTheme.typography.titleMedium)
          Text(
            "Ouvrez la position de ce téléphone dans Google Maps.",
            style = MaterialTheme.typography.bodyMedium,
          )
          OutlinedButton(
            onClick = onOpenLocation,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Ouvrir ma position dans Maps")
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 4. Liens vers Contacts d'urgence et Menu Développeur
      Button(
        onClick = onOpenEmergencyContacts,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.home_emergency_contacts))
      }

      OutlinedButton(
        onClick = onOpenDevMenu,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.home_dev_menu))
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
  JeiaTheme {
    HomeContent(
      state = HomeUiState(
        connection = RingConnectionState.CONNECTED,
        sourceType = RingSourceType.BLUETOOTH,
      ),
      onConnect = {},
      onDisconnect = {},
      onSetSource = {},
      onToggleService = {},
      onOpenDevMenu = {},
      onOpenEmergencyContacts = {},
    )
  }
}
