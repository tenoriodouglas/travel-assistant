package com.travelassistant.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Settings : Destination("settings")
}

enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Destination.Home, "Preços", Icons.Filled.ShowChart),
    SETTINGS(Destination.Settings, "Ajustes", Icons.Filled.Settings),
}
