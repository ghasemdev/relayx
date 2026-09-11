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
import com.parsomash.relayx.ui.navigation.MainAppScaffold
import com.parsomash.relayx.ui.theme.RelayxTheme
import com.parsomash.relayx.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RelayxTheme {
                val dashboardViewModel: DashboardViewModel = koinViewModel()

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
