package com.parsomash.relayx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parsomash.relayx.domain.usecase.GetGatewayStatsUseCase
import com.parsomash.relayx.domain.usecase.ToggleForwardingUseCase
import com.parsomash.relayx.ui.navigation.MainAppScaffold
import com.parsomash.relayx.ui.theme.RelayxTheme
import com.parsomash.relayx.viewmodel.DashboardViewModel
import com.parsomash.relayx.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as RelayApplication
        val preferencesRepo = app.preferencesRepository
        val database = app.database

        setContent {
            RelayxTheme {
                val dashboardViewModel: DashboardViewModel = viewModel {
                    DashboardViewModel(
                        getGatewayStatsUseCase = GetGatewayStatsUseCase(database.outboxMessageDao()),
                        toggleForwardingUseCase = ToggleForwardingUseCase(preferencesRepo),
                        preferencesRepository = preferencesRepo
                    )
                }

                val settingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(preferencesRepository = preferencesRepo)
                }

                fun checkPermissions(): Boolean {
                    val receiveSms = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECEIVE_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                    val readSms = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                    return receiveSms && readSms
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = permissions[Manifest.permission.RECEIVE_SMS] == true
                    dashboardViewModel.updatePermissionState(granted)
                }

                LaunchedEffect(Unit) {
                    val hasPerms = checkPermissions()
                    dashboardViewModel.updatePermissionState(hasPerms)
                    if (!hasPerms) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                        )
                    }
                }

                MainAppScaffold(
                    dashboardViewModel = dashboardViewModel,
                    settingsViewModel = settingsViewModel,
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                        )
                    }
                )
            }
        }
    }
}