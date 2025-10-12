package com.despacho.palletscanner.data.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.despacho.palletscanner.data.models.ServerConfiguration

// Extensión para crear el DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pallet_scanner_config")

class ConfigurationService(private val context: Context) {

    companion object {
        // Claves para guardar las configuraciones
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_PORT_KEY = stringPreferencesKey("server_port")
        private val HUB_PATH_KEY = stringPreferencesKey("hub_path")
        private val CONNECTION_TIMEOUT_KEY = longPreferencesKey("connection_timeout")
    }

    // Función que lee la configuración guardada
    val serverConfiguration: Flow<ServerConfiguration> = context.dataStore.data.map { preferences ->
        ServerConfiguration(
            serverUrl = preferences[SERVER_URL_KEY] ?: "",
            port = preferences[SERVER_PORT_KEY] ?: "7164",
            hubPath = preferences[HUB_PATH_KEY] ?: "/pallethub",
            connectionTimeout = preferences[CONNECTION_TIMEOUT_KEY] ?: 10000L
        )
    }

    // Función para guardar la configuración
    suspend fun saveServerConfiguration(config: ServerConfiguration) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = config.serverUrl
            preferences[SERVER_PORT_KEY] = config.port
            preferences[HUB_PATH_KEY] = config.hubPath
            preferences[CONNECTION_TIMEOUT_KEY] = config.connectionTimeout
        }
    }
}