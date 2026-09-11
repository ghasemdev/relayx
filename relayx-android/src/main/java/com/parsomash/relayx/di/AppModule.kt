package com.parsomash.relayx.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.RelayDatabase
import com.parsomash.relayx.data.local.provideDataStore
import com.parsomash.relayx.util.AppDispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.parsomash.relayx")
class AppModule {

    @Single
    fun provideDispatchers(): AppDispatchers = AppDispatchers()

    @Single
    fun provideDatabase(context: Context): RelayDatabase = RelayDatabase.getInstance(context)

    @Single
    fun provideOutboxDao(database: RelayDatabase): OutboxMessageDao = database.outboxMessageDao()

    @Single
    fun provideDataStorePreferences(
        context: Context,
        dispatchers: AppDispatchers
    ): DataStore<Preferences> = provideDataStore(context, dispatchers)
}

@KoinApplication(modules = [AppModule::class])
class RelayApp
