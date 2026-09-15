package com.sipoe.softphone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountDataStore by preferencesDataStore(name = "sipoe_account")

class AccountStore(private val context: Context) {

    val flow: Flow<AccountSettings> = context.accountDataStore.data.map { prefs ->
        AccountSettings(
            domain = prefs[Keys.DOMAIN].orEmpty(),
            proxy = prefs[Keys.PROXY].orEmpty(),
            username = prefs[Keys.USERNAME].orEmpty(),
            password = PasswordCipher.decrypt(prefs[Keys.PASSWORD].orEmpty()),
            transport = prefs[Keys.TRANSPORT]
                ?.let { stored -> SipTransport.entries.firstOrNull { it.name == stored } }
                ?: SipTransport.UDP,
            port = prefs[Keys.PORT] ?: 0,
            stunEnabled = prefs[Keys.STUN_ENABLED] ?: true,
            stunServer = prefs[Keys.STUN_SERVER].orEmpty()
                .ifBlank { AccountSettings.DEFAULT_STUN_SERVER },
            registerExpires = prefs[Keys.EXPIRES] ?: AccountSettings.DEFAULT_EXPIRES,
        )
    }

    suspend fun save(settings: AccountSettings) {
        val normalized = settings.normalized()
        context.accountDataStore.edit { prefs ->
            prefs[Keys.DOMAIN] = normalized.domain
            prefs[Keys.PROXY] = normalized.proxy
            prefs[Keys.USERNAME] = normalized.username
            prefs[Keys.PASSWORD] = PasswordCipher.encrypt(normalized.password)
            prefs[Keys.TRANSPORT] = normalized.transport.name
            prefs[Keys.PORT] = normalized.port
            prefs[Keys.STUN_ENABLED] = normalized.stunEnabled
            prefs[Keys.STUN_SERVER] = normalized.stunServer.trim()
            prefs[Keys.EXPIRES] = normalized.registerExpires
        }
    }

    suspend fun clear() {
        context.accountDataStore.edit { it.clear() }
    }

    private object Keys {
        val DOMAIN = stringPreferencesKey("domain")
        val PROXY = stringPreferencesKey("proxy")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password_encrypted")
        val TRANSPORT = stringPreferencesKey("transport")
        val PORT = intPreferencesKey("port")
        val STUN_ENABLED = booleanPreferencesKey("stun_enabled")
        val STUN_SERVER = stringPreferencesKey("stun_server")
        val EXPIRES = intPreferencesKey("register_expires")
    }
}
