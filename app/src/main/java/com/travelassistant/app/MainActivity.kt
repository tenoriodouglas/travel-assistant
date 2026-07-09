package com.travelassistant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.travelassistant.app.ui.home.HomeScreen
import com.travelassistant.app.ui.navigation.Destination
import com.travelassistant.app.update.AppUpdater
import com.travelassistant.app.ui.navigation.TopLevelDestination
import com.travelassistant.app.ui.settings.SettingsScreen
import com.travelassistant.app.ui.theme.Background
import com.travelassistant.app.ui.theme.Surface
import com.travelassistant.app.ui.theme.TextMuted
import com.travelassistant.app.ui.theme.TravelAssistantTheme
import com.travelassistant.app.ui.theme.Up

class MainActivity : ComponentActivity() {
    private lateinit var appUpdater: AppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Check Google Play for updates on launch and force an immediate update if available.
        appUpdater = AppUpdater(this)
        appUpdater.checkForUpdate()
        setContent {
            TravelAssistantTheme {
                TravelAssistantApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume an immediate update that was interrupted (e.g. app backgrounded mid-update).
        appUpdater.resumeIfInProgress()
    }
}

@Composable
private fun TravelAssistantApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        containerColor = Background,
        bottomBar = { BottomBar(navController, backStackEntry?.destination) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) { HomeScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun BottomBar(
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
) {
    NavigationBar(containerColor = Surface) {
        TopLevelDestination.entries.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == item.destination.route
            } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Up,
                    selectedTextColor = Up,
                    indicatorColor = Background,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                ),
            )
        }
    }
}
