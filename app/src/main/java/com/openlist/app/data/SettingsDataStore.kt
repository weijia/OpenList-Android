package com.openlist.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SERVER_PORT = intPreferencesKey("server_port")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        val LOG_LEVEL = stringPreferencesKey("log_level")
    }

    suspend fun saveSettings(
        serverPort: Int,
        autoStart: Boolean,
        keepAlive: Boolean,
        logLevel: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SERVER_PORT] = serverPort
            preferences[Keys.AUTO_START] = autoStart
            preferences[Keys.KEEP_ALIVE] = keepAlive
            preferences[Keys.LOG_LEVEL] = logLevel
        }
    }

    suspend fun getServerPort(): Int {
        return context.settingsDataStore.data.map { preferences ->
            preferences[Keys.SERVER_PORT] ?: 5244
        }.first()
    }

    suspend fun getAutoStart(): Boolean {
        return context.settingsDataStore.data.map { preferences ->
            preferences[Keys.AUTO_START] ?: true
        }.first()
    }

    suspend fun getKeepAlive(): Boolean {
        return context.settingsDataStore.data.map { preferences ->
            preferences[Keys.KEEP_ALIVE] ?: true
        }.first()
    }

    suspend fun getLogLevel(): String {
        return context.settingsDataStore.data.map { preferences ->
            preferences[Keys.LOG_LEVEL] ?: "info"
        }.first()
    }
}
