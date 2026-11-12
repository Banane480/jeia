package com.example.jeia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.jeia.domain.AlertType
import com.example.jeia.ui.alert.AlertScreen
import com.example.jeia.ui.alert.AlertViewModel
import com.example.jeia.ui.dev.DevMenuScreen
import com.example.jeia.ui.home.HomeScreen
import com.example.jeia.ui.home.HomeViewModel

@Composable
fun MainNavigation(container: AppContainer) {
  val backStack = rememberNavBackStack(Home)
  val ring = container.ringRepository

  // Un appui long sur la bague ouvre l'écran d'alerte, une annulation le referme.
  LaunchedEffect(ring) {
    ring.alertEvents.collect { event ->
      when (event.type) {
        AlertType.ALERT -> backStack.add(Alert(event.counter))
        AlertType.CANCEL -> if (backStack.lastOrNull() is Alert) backStack.removeLastOrNull()
      }
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(
            viewModel = viewModel { HomeViewModel(ring) },
            onOpenDevMenu = { backStack.add(DevMenu) },
          )
        }
        entry<Alert> { key ->
          AlertScreen(
            viewModel = viewModel { AlertViewModel(ring, key.counter) },
            onClose = { backStack.removeLastOrNull() },
          )
        }
        entry<DevMenu> {
          DevMenuScreen(ring = container.fakeRing, onBack = { backStack.removeLastOrNull() })
        }
      },
  )
}
