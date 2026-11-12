package com.example.jeia

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

/** Écran d'alerte déclenché par un appui long sur la bague. [counter] identifie l'alerte. */
@Serializable data class Alert(val counter: Int) : NavKey

@Serializable data object DevMenu : NavKey
