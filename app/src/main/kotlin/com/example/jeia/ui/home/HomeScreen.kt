package com.example.jeia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jeia.R
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.ui.theme.JeiaTheme

@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onOpenDevMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  HomeContent(
    state = uiState,
    onConnect = viewModel::connect,
    onDisconnect = viewModel::disconnect,
    onOpenDevMenu = onOpenDevMenu,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
  state: HomeUiState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onOpenDevMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val stateLabel =
        stringResource(
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
          Button(onClick = onConnect) { Text(stringResource(R.string.home_connect)) }
        RingConnectionState.SCANNING -> Unit
        RingConnectionState.CONNECTED ->
          OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.home_disconnect)) }
      }

      OutlinedButton(onClick = onOpenDevMenu) { Text(stringResource(R.string.home_dev_menu)) }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
  JeiaTheme {
    HomeContent(
      state = HomeUiState(RingConnectionState.CONNECTED),
      onConnect = {},
      onDisconnect = {},
      onOpenDevMenu = {},
    )
  }
}
