@file:OptIn(KoinExperimentalAPI::class)

package com.parsomash.relayx

import android.app.Application
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.data.local.RelayDatabase
import com.parsomash.relayx.di.RelayApp
import com.parsomash.relayx.ui.navigation.navigationModule
import com.parsomash.relayx.util.RelayLogger
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.KoinConfiguration
import org.koin.plugin.module.dsl.koinConfiguration

class RelayApplication : Application(), KoinStartup {

    val database: RelayDatabase by inject()
    val preferencesRepository: PreferencesRepository by inject()

    override fun onKoinStartup(): KoinConfiguration = koinConfiguration<RelayApp> {
        androidLogger()
        androidContext(this@RelayApplication)
        modules(navigationModule)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        RelayLogger.i("App", "RelayApplication initialized successfully with App Startup and Koin Compiler Plugin")
    }

    companion object {
        lateinit var instance: RelayApplication
            private set
    }
}
