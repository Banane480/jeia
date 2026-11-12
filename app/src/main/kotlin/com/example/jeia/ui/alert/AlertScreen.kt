package com.example.jeia.ui.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.jeia.ui.theme.JeiaTheme

@Composable
fun AlertScreen(
  viewModel: AlertViewModel,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  AlertContent(state = uiState, onCancel = viewModel::cancel, onClose = onClose, modifier = modifier)
}

@Composable
internal fun AlertContent(
  state: AlertUiState,
  onCancel: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      when (state) {
        is AlertUiState.Countdown -> {
          Text(
            text = stringResource(R.string.alert_countdown, state.secondsLeft),
            style = MaterialTheme.typography.headlineMedium,
          )
          Button(onClick = onCancel) { Text(stringResource(R.string.alert_cancel)) }
        }
        AlertUiState.Sending -> {
          CircularProgressIndicator()
          Text(
            text = stringResource(R.string.alert_sending),
            style = MaterialTheme.typography.headlineMedium,
          )
        }
        AlertUiState.Sent -> {
          Text(
            text = stringResource(R.string.alert_sent),
            style = MaterialTheme.typography.headlineMedium,
          )
          Button(onClick = onClose) { Text(stringResource(R.string.alert_i_am_fine)) }
        }
        AlertUiState.Cancelled -> {
          Text(
            text = stringResource(R.string.alert_cancelled),
            style = MaterialTheme.typography.headlineMedium,
          )
          Button(onClick = onClose) { Text(stringResource(R.string.dev_back)) }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun AlertCountdownPreview() {
  JeiaTheme { AlertContent(state = AlertUiState.Countdown(3), onCancel = {}, onClose = {}) }
}
