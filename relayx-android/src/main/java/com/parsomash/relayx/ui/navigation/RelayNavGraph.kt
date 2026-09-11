package com.parsomash.relayx.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.parsomash.relayx.ui.dashboard.DashboardScreen
import com.parsomash.relayx.ui.settings.SettingsScreen
import com.parsomash.relayx.viewmodel.DashboardViewModel
import com.parsomash.relayx.viewmodel.SettingsViewModel

enum class Screen(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun MainAppScaffold(
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestPermissions: () -> Unit
) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    viewModel = dashboardViewModel,
                    onRequestPermissions = onRequestPermissions
                )
                Screen.Settings -> SettingsScreen(
                    viewModel = settingsViewModel
                )
            }
        }
    }
}
