package com.travelassistant.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Markets : Destination("markets")
    data object Settings : Destination("settings")
    data object Detail : Destination("detail/{routeId}") {
        fun create(routeId: String) = "detail/$routeId"
    }
}

enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
) {
    MARKETS(Destination.Markets, "Mercados", Icons.Filled.ShowChart),
    SETTINGS(Destination.Settings, "Ajustes", Icons.Filled.Settings),
}
