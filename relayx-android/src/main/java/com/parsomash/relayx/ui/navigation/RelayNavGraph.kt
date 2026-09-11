@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.parsomash.relayx.R
import com.parsomash.relayx.ui.dashboard.DashboardScreen
import com.parsomash.relayx.ui.settings.SettingsScreen
import com.parsomash.relayx.viewmodel.DashboardViewModel
import com.parsomash.relayx.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

val LocalRequestPermissions = staticCompositionLocalOf<() -> Unit> { {} }

@Serializable
sealed class AppRoute : NavKey {
    @Serializable
    data object Dashboard : AppRoute()

    @Serializable
    data object Settings : AppRoute()
}

data class BottomNavItem(
    val route: AppRoute,
    val titleResId: Int,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(AppRoute.Dashboard, R.string.nav_dashboard, Icons.Default.Dashboard),
    BottomNavItem(AppRoute.Settings, R.string.nav_settings, Icons.Default.Settings)
)

val navigationModule = module {
    navigation<AppRoute.Dashboard> {
        val dashboardViewModel: DashboardViewModel = koinViewModel()
        val onRequestPermissions = LocalRequestPermissions.current
        DashboardScreen(
            viewModel = dashboardViewModel,
            onRequestPermissions = onRequestPermissions
        )
    }
    navigation<AppRoute.Settings> {
        val settingsViewModel: SettingsViewModel = koinViewModel()
        SettingsScreen(
            viewModel = settingsViewModel
        )
    }
}

@Composable
fun MainAppScaffold(
    onRequestPermissions: () -> Unit
) {
    val backStack = rememberNavBackStack(AppRoute.Dashboard)
    val currentRoute = backStack.lastOrNull() ?: AppRoute.Dashboard

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                backStack.clear()
                                backStack.add(item.route)
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.titleResId)) },
                        label = { Text(stringResource(item.titleResId)) }
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
            CompositionLocalProvider(LocalRequestPermissions provides onRequestPermissions) {
                NavDisplay(
                    backStack = backStack,
                    entryProvider = koinEntryProvider()
                )
            }
        }
    }
}

