package com.example.jeia.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jeia.data.DefaultDataRepository
import com.example.jeia.data.RingConnection
import com.example.jeia.data.RingState
import com.example.jeia.data.rememberLocationMapsLauncher

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val context = LocalContext.current
  val ring = remember { RingConnection(context.applicationContext) }
  val ringState by ring.state.collectAsStateWithLifecycle()
  val openLocation = rememberLocationMapsLauncher()
  val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  } else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { granted ->
    if (permissions.all { granted[it] == true }) ring.connect() else ring.permissionDenied()
  }
  DisposableEffect(ring) { onDispose { ring.close() } }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  when (val state = uiState) {
    MainScreenUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    is MainScreenUiState.Success -> {
      MainScreenContent(
        modifier = modifier,
        ringState = ringState,
        onConnect = {
          if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            ring.connect()
          } else {
            permissionLauncher.launch(permissions)
          }
        },
        onDismiss = ring::dismissAlert,
        onOpenLocation = openLocation,
      )
    }
    is MainScreenUiState.Error -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Erreur : ${state.throwable.message}")
      }
    }
  }
}

@Composable
internal fun MainScreenContent(
  modifier: Modifier = Modifier,
  ringState: RingState = RingState(),
  onConnect: () -> Unit = {},
  onDismiss: () -> Unit = {},
  onOpenLocation: () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  val hasAlert = ringState.alert != "Aucune alerte reçue"

  Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.background) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
      Text(
        text = "Jeia",
        color = colors.primary,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        letterSpacing = (-1).sp,
      )
      Text(
        text = "Bague et alertes",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
      )
      Spacer(Modifier.height(32.dp))

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
      ) {
        Column(modifier = Modifier.padding(24.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("JEIA RING", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
              Spacer(Modifier.height(12.dp))
              Text("Votre bague", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(6.dp))
              Text(ringState.status, style = MaterialTheme.typography.bodyMedium)
            }
            val ringColor = colors.primary
            Canvas(modifier = Modifier.padding(start = 16.dp).size(62.dp)) {
              drawCircle(color = ringColor, radius = size.minDimension * .38f, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            }
          }
          Spacer(Modifier.height(24.dp))
          Button(
            onClick = onConnect,
            enabled = !ringState.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(),
          ) {
            Text("Connecter la bague")
          }
        }
      }

      Spacer(Modifier.height(28.dp))
      Text("Alertes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(12.dp))
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (hasAlert) colors.errorContainer else colors.surfaceContainerLow,
        contentColor = if (hasAlert) colors.onErrorContainer else colors.onSurface,
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            if (hasAlert) "Alerte reçue" else "Dernière alerte",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(Modifier.height(6.dp))
          Text(ringState.alert, style = MaterialTheme.typography.bodyMedium)
          if (hasAlert) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("J’ai compris") }
          }
        }
      }

      Spacer(Modifier.height(28.dp))
      Text("Votre position", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Text(
        "Ouvrez la position de ce téléphone dans Google Maps.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
      )
      Spacer(Modifier.height(14.dp))
      OutlinedButton(
        onClick = onOpenLocation,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
      ) { Text("Ouvrir ma position dans Maps") }
      Spacer(Modifier.height(16.dp))
    }
  }
}
