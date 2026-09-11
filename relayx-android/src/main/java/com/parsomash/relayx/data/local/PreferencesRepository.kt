package com.parsomash.relayx.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.parsomash.relayx.domain.model.GatewayConfig
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun provideDataStore(
    context: Context,
    dispatchers: AppDispatchers = AppDispatchers()
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = CoroutineScope(dispatchers.io + SupervisorJob()),
    produceFile = { context.preferencesDataStoreFile("gateway_preferences") }
)

@OptIn(ExperimentalUuidApi::class)
@Single
class PreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {

    object PreferencesKeys {
        val SERVER_HOST = stringPreferencesKey("server_host")
        val SERVER_PORT = intPreferencesKey("server_port")
        val USE_HTTPS = booleanPreferencesKey("use_https")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val BEARER_TOKEN = stringPreferencesKey("bearer_token")
        val FORWARDING_ENABLED = booleanPreferencesKey("forwarding_enabled")
    }

    val configFlow: Flow<GatewayConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val host = preferences[PreferencesKeys.SERVER_HOST] ?: "10.0.2.2"
            val port = preferences[PreferencesKeys.SERVER_PORT] ?: 8080
            val useHttps = preferences[PreferencesKeys.USE_HTTPS] ?: false
            var deviceId = preferences[PreferencesKeys.DEVICE_ID] ?: ""
            if (deviceId.isBlank()) {
                deviceId = "pixel-" + Uuid.random().toString().take(8)
            }
            val bearerToken = preferences[PreferencesKeys.BEARER_TOKEN] ?: ""
            val forwardingEnabled = preferences[PreferencesKeys.FORWARDING_ENABLED] ?: false

            GatewayConfig(
                serverHost = host,
                serverPort = port,
                useHttps = useHttps,
                deviceId = deviceId,
                bearerToken = bearerToken,
                forwardingEnabled = forwardingEnabled
            )
        }
        .flowOn(dispatchers.io)

    suspend fun getConfig(): GatewayConfig = withContext(dispatchers.io) {
        configFlow.first()
    }

    suspend fun updateConfig(config: GatewayConfig) = withContext(dispatchers.io) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_HOST] = config.serverHost
            preferences[PreferencesKeys.SERVER_PORT] = config.serverPort
            preferences[PreferencesKeys.USE_HTTPS] = config.useHttps
            preferences[PreferencesKeys.DEVICE_ID] = config.deviceId.ifBlank { "pixel-" + Uuid.random().toString().take(8) }
            preferences[PreferencesKeys.BEARER_TOKEN] = config.bearerToken
            preferences[PreferencesKeys.FORWARDING_ENABLED] = config.forwardingEnabled
        }
    }

    suspend fun setForwardingEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FORWARDING_ENABLED] = enabled
        }
    }
}
