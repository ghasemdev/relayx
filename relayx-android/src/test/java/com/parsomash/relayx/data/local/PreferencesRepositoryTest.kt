package com.parsomash.relayx.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.parsomash.relayx.domain.model.GatewayConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createRepository(): PreferencesRepository {
        val testFile = tmpFolder.newFile("test_preferences.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        return PreferencesRepository(dataStore)
    }

    @Test
    fun testDefaultConfigValues() = runTest {
        val repo = createRepository()
        val config = repo.getConfig()

        assertEquals("10.0.2.2", config.serverHost)
        assertEquals(8080, config.serverPort)
        assertFalse(config.useHttps)
        assertTrue(config.deviceId.startsWith("pixel-"))
        assertEquals("", config.bearerToken)
        assertFalse(config.forwardingEnabled)
    }

    @Test
    fun testUpdateConfigPersistsAllFields() = runTest {
        val repo = createRepository()
        val customConfig = GatewayConfig(
            serverHost = "192.168.1.50",
            serverPort = 9000,
            useHttps = true,
            deviceId = "custom-device-01",
            bearerToken = "secret-token-xyz",
            forwardingEnabled = true
        )

        repo.updateConfig(customConfig)
        val loaded = repo.getConfig()

        assertEquals("192.168.1.50", loaded.serverHost)
        assertEquals(9000, loaded.serverPort)
        assertTrue(loaded.useHttps)
        assertEquals("custom-device-01", loaded.deviceId)
        assertEquals("secret-token-xyz", loaded.bearerToken)
        assertTrue(loaded.forwardingEnabled)
    }

    @Test
    fun testSetForwardingEnabledOnlyUpdatesToggle() = runTest {
        val repo = createRepository()
        val initialConfig = GatewayConfig(
            serverHost = "custom.host",
            serverPort = 7777,
            useHttps = false,
            deviceId = "my-device",
            bearerToken = "my-token",
            forwardingEnabled = false
        )
        repo.updateConfig(initialConfig)

        repo.setForwardingEnabled(true)
        val updated = repo.getConfig()

        assertTrue(updated.forwardingEnabled)
        assertEquals("custom.host", updated.serverHost)
        assertEquals(7777, updated.serverPort)
        assertEquals("my-device", updated.deviceId)
        assertEquals("my-token", updated.bearerToken)
    }
}
