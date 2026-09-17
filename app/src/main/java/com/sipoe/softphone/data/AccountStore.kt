package com.sipoe.softphone.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sipoe.softphone.diag.DiagLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.accountDataStore by preferencesDataStore(name = "sipoe_account")

/**
 * 本机保存的全部账号与当前激活账号。
 *
 * 同一时间最多只有一个账号处于激活状态([activeId]),它决定 SIP 内核注册哪一个身份。
 */
data class AccountState(
    val accounts: List<AccountSettings> = emptyList(),
    val activeId: String? = null,
) {
    val active: AccountSettings?
        get() = accounts.firstOrNull { it.id == activeId }

    fun find(id: String?): AccountSettings? = accounts.firstOrNull { it.id == id }
}

class AccountStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val flow: Flow<AccountState> = flow {
        migrateLegacyAccount()
        emitAll(context.accountDataStore.data.map { it.toState() })
    }

    /** 新增或更新一个账号,返回落库后的条目(新建时会带上生成的 id)。 */
    suspend fun upsert(settings: AccountSettings): AccountSettings {
        val normalized = settings.normalized()
        val stored = normalized.copy(
            id = normalized.id.ifBlank { UUID.randomUUID().toString() },
            // 只重新加密被编辑的这一条,其它条目的密文保持原样
            password = PasswordCipher.encrypt(normalized.password),
        )
        context.accountDataStore.edit { prefs ->
            val accounts = decode(prefs[Keys.ACCOUNTS])
            val index = accounts.indexOfFirst { it.id == stored.id }
            val updated = if (index >= 0) {
                accounts.toMutableList().also { it[index] = stored }
            } else {
                accounts + stored
            }
            prefs[Keys.ACCOUNTS] = json.encodeToString(updated)
        }
        return stored.copy(password = normalized.password)
    }

    /** 删除账号;若删掉的正是当前激活账号,则回到"无激活账号"状态,不自动切换到别的账号。 */
    suspend fun delete(id: String) {
        context.accountDataStore.edit { prefs ->
            val accounts = decode(prefs[Keys.ACCOUNTS]).filterNot { it.id == id }
            prefs[Keys.ACCOUNTS] = json.encodeToString(accounts)
            if (prefs[Keys.ACTIVE_ID] == id) prefs.remove(Keys.ACTIVE_ID)
        }
    }

    /** 切换激活账号;传 null 表示停用(只注销,不删除配置)。 */
    suspend fun setActive(id: String?) {
        context.accountDataStore.edit { prefs ->
            if (id == null || decode(prefs[Keys.ACCOUNTS]).none { it.id == id }) {
                prefs.remove(Keys.ACTIVE_ID)
            } else {
                prefs[Keys.ACTIVE_ID] = id
            }
        }
    }

    private fun Preferences.toState(): AccountState {
        val accounts = decode(this[Keys.ACCOUNTS]).map {
            it.copy(password = PasswordCipher.decrypt(it.password))
        }
        return AccountState(
            accounts = accounts,
            activeId = this[Keys.ACTIVE_ID]?.takeIf { id -> accounts.any { it.id == id } },
        )
    }

    /** 读取已落库的条目,password 字段仍是密文,只有 [toState] 会解密。 */
    private fun decode(raw: String?): List<AccountSettings> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<AccountSettings>>(raw) }
            .getOrElse {
                DiagLog.e(TAG, "Unable to decode stored accounts", it)
                emptyList()
            }
    }

    /**
     * 把单账号版本遗留的扁平 key 迁移成列表里的第一条,并保持激活。
     * 迁移后旧 key 立即清除,且以 [Keys.ACCOUNTS] 是否存在作为幂等标记。
     */
    private suspend fun migrateLegacyAccount() {
        context.accountDataStore.edit { prefs ->
            if (prefs.contains(Keys.ACCOUNTS)) return@edit

            val legacy = AccountSettings(
                domain = prefs[Keys.DOMAIN].orEmpty(),
                proxy = prefs[Keys.PROXY].orEmpty(),
                username = prefs[Keys.USERNAME].orEmpty(),
                password = prefs[Keys.PASSWORD].orEmpty(),
                transport = prefs[Keys.TRANSPORT]
                    ?.let { stored -> SipTransport.entries.firstOrNull { it.name == stored } }
                    ?: SipTransport.UDP,
                port = prefs[Keys.PORT] ?: 0,
                stunEnabled = prefs[Keys.STUN_ENABLED] ?: true,
                stunServer = prefs[Keys.STUN_SERVER].orEmpty()
                    .ifBlank { AccountSettings.DEFAULT_STUN_SERVER },
                registerExpires = prefs[Keys.EXPIRES] ?: AccountSettings.DEFAULT_EXPIRES,
            )
            if (legacy.isComplete) {
                val id = UUID.randomUUID().toString()
                prefs[Keys.ACCOUNTS] = json.encodeToString(listOf(legacy.copy(id = id)))
                prefs[Keys.ACTIVE_ID] = id
                DiagLog.i(TAG, "Migrated legacy account ${legacy.identityUri} into the account list")
            } else {
                prefs[Keys.ACCOUNTS] = "[]"
            }
            LEGACY_KEYS.forEach { prefs.remove(it) }
        }
    }

    private object Keys {
        val ACCOUNTS = stringPreferencesKey("accounts")
        val ACTIVE_ID = stringPreferencesKey("active_account_id")

        // 单账号版本的遗留 key,仅用于迁移
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

    private companion object {
        const val TAG = "AccountStore"
        val LEGACY_KEYS = with(Keys) {
            listOf(DOMAIN, PROXY, USERNAME, PASSWORD, TRANSPORT, PORT, STUN_ENABLED, STUN_SERVER, EXPIRES)
        }
    }
}