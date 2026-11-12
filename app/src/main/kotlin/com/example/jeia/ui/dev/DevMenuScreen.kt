package com.example.jeia.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jeia.R
import com.example.jeia.data.FakeRingRepository

/** Menu développeur : pilote la bague simulée (phase 2, sans matériel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevMenuScreen(
  ring: FakeRingRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val lastAck by ring.lastAck.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.dev_title)) }) },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Button(onClick = ring::simulateAlert) { Text(stringResource(R.string.dev_simulate_press)) }
      OutlinedButton(onClick = ring::simulateCancel) {
        Text(stringResource(R.string.dev_simulate_cancel))
      }
      Text(
        stringResource(
          R.string.dev_last_ack,
          lastAck?.let { "#${it.counter} (${it.status})" } ?: stringResource(R.string.dev_none),
        )
      )
      OutlinedButton(onClick = onBack) { Text(stringResource(R.string.dev_back)) }
    }
  }
}
