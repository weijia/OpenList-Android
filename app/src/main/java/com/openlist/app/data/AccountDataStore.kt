package com.openlist.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "accounts")

@Serializable
data class AccountData(
    val id: String,
    val name: String,
    val username: String = "",
    val password: String = "",
    val serverUrl: String = ""
)

class AccountDataStore(private val context: Context) {

    private object Keys {
        val ACCOUNTS = stringPreferencesKey("accounts_list")
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveAccount(account: AccountData) {
        context.accountDataStore.edit { preferences ->
            val currentAccounts = getAccounts().toMutableList()
            val existingIndex = currentAccounts.indexOfFirst { it.id == account.id }
            
            if (existingIndex != -1) {
                currentAccounts[existingIndex] = account
            } else {
                currentAccounts.add(account)
            }
            
            preferences[Keys.ACCOUNTS] = json.encodeToString(currentAccounts)
        }
    }

    suspend fun deleteAccount(accountId: String) {
        context.accountDataStore.edit { preferences ->
            val currentAccounts = getAccounts().toMutableList()
            currentAccounts.removeAll { it.id == accountId }
            preferences[Keys.ACCOUNTS] = json.encodeToString(currentAccounts)
        }
    }

    suspend fun getAccounts(): List<AccountData> {
        return context.accountDataStore.data.map { preferences ->
            val accountsJson = preferences[Keys.ACCOUNTS] ?: "[]"
            try {
                json.decodeFromString<List<AccountData>>(accountsJson)
            } catch (e: Exception) {
                emptyList<AccountData>()
            }
        }.first()
    }

    suspend fun getAccountById(accountId: String): AccountData? {
        return getAccounts().find { it.id == accountId }
    }
}
