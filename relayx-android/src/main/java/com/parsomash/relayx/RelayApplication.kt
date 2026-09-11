package com.parsomash.relayx

import android.app.Application
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.data.local.RelayDatabase
import com.parsomash.relayx.util.RelayLogger

class RelayApplication : Application() {

    lateinit var database: RelayDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = RelayDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        RelayLogger.i("App", "RelayApplication initialized successfully")
    }

    companion object {
        lateinit var instance: RelayApplication
            private set
    }
}
