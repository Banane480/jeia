package com.example.jeia.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.jeia.domain.RingConnectionState
import com.example.jeia.ui.theme.JeiaTheme
import org.junit.Rule
import org.junit.Test

/** Tests UI de [HomeContent]. */
class HomeScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun disconnected_showsConnectButton() {
    composeTestRule.setContent {
      JeiaTheme {
        HomeContent(
          state = HomeUiState(RingConnectionState.DISCONNECTED),
          onConnect = {},
          onDisconnect = {},
          onOpenDevMenu = {},
        )
      }
    }
    composeTestRule.onNodeWithText("Connecter la bague").assertExists()
  }
}
