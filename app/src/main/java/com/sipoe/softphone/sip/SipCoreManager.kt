package com.sipoe.softphone.sip

import android.content.Context
import com.sipoe.softphone.BuildConfig
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.data.SipTransport
import com.sipoe.softphone.diag.DiagLog
import com.sipoe.softphone.diag.NetworkSelfTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.LogLevel
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import kotlin.random.Random

object SipCoreManager {
    private const val TAG = "SipCoreManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localSipPort: Int by lazy { Random.nextInt(40000, 60000) }

    private var appContext: Context? = null
    private var core: Core? = null
    private var networkMonitor: NetworkMonitor? = null

    private val _registration = MutableStateFlow(SipRegistrationState())
    val registration: StateFlow<SipRegistrationState> = _registration.asStateFlow()

    val activeCore: Core? get() = core

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun ensureStarted(): Core? {
        core?.let { return it }
        val context = appContext ?: run {
            DiagLog.e(TAG, "initialize() was not called")
            return null
        }
        return runCatching {
            val factory = Factory.instance()
            factory.setLoggerDomain("Sipoe")
            factory.enableLogcatLogs(true)
            factory.loggingService.setLogLevel(LogLevel.Message)
            DiagLog.attachLinphoneLogger()
            val created = factory.createCore(null, null, context)
            created.setUserAgent("Sipoe", BuildConfig.VERSION_NAME)
            created.setAutoIterateEnabled(true)
            created.addListener(coreListener)
            CallController.attach(created, context)
            applyDefaultTransports(created)
            val startResult = created.start()
            created.setNetworkReachable(true)
            networkMonitor = NetworkMonitor(context) { reachable ->
                DiagLog.i(TAG, "Network reachable: $reachable")
                created.setNetworkReachable(reachable)
            }.also { it.start() }
            core = created
            DiagLog.i(TAG, "Core created and started, result=$startResult")
            created
        }.onFailure {
            DiagLog.e(TAG, "Unable to create Linphone core", it)
            _registration.value = SipRegistrationState(
                status = RegistrationStatus.Failed,
                message = "SIP 内核启动失败: ${it.message}",
            )
        }.getOrNull()
    }

    @Synchronized
    fun applyAccount(settings: AccountSettings) {
        val normalized = settings.normalized()
        if (!normalized.isComplete) {
            DiagLog.w(TAG, "Account incomplete, skipping registration")
            clearAccount()
            _registration.value = SipRegistrationState(
                status = RegistrationStatus.Idle,
                message = "账号信息不完整",
            )
            return
        }
        val current = ensureStarted() ?: return
        runCatching {
            current.clearAccounts()
            current.clearAllAuthInfo()

            val factory = Factory.instance()

            val identity = factory.createAddress(normalized.identityUri)
            if (identity == null) {
                DiagLog.e(TAG, "Invalid identity address: ${normalized.identityUri}")
                _registration.value = SipRegistrationState(
                    status = RegistrationStatus.Failed,
                    message = "账号地址格式不正确:${normalized.identityUri}",
                    rawMessage = "Invalid identity address",
                )
                return
            }

            val natPolicy = current.createNatPolicy()
            natPolicy.setIceEnabled(true)
            if (normalized.stunEnabled && normalized.stunServer.isNotBlank()) {
                natPolicy.setStunServer(normalized.stunServer)
                natPolicy.setStunEnabled(true)
            } else {
                natPolicy.setStunEnabled(false)
            }
            current.setNatPolicy(natPolicy)

            val params = current.createAccountParams()
            params.setIdentityAddress(identity)
            val serverAddress = factory.createAddress(normalized.serverAddress)
            if (serverAddress == null) {
                DiagLog.e(TAG, "Invalid server address: ${normalized.serverAddress}")
                _registration.value = SipRegistrationState(
                    status = RegistrationStatus.Failed,
                    message = "服务器地址格式不正确:${normalized.serverAddress}",
                    rawMessage = "Invalid server address",
                )
                return
            }
            params.setServerAddress(serverAddress)
            params.setRegisterEnabled(true)
            params.setExpires(normalized.registerExpires)
            params.setTransport(normalized.transport.toLinphone())
            params.setNatPolicy(natPolicy)

            val account = current.createAccount(params)
            current.addAccount(account)
            current.setDefaultAccount(account)

            val authInfo = factory.createAuthInfo(
                normalized.username,
                null,
                normalized.password,
                null,
                null,
                normalized.domain,
            )
            current.addAuthInfo(authInfo)

            _registration.value = SipRegistrationState(
                status = RegistrationStatus.InProgress,
                identity = normalized.identityUri,
            )
            DiagLog.i(
                TAG,
                "Account applied: ${normalized.identityUri} -> ${normalized.serverAddress} " +
                    "transport=${normalized.transport.label} expires=${normalized.registerExpires} " +
                    "stun=${if (normalized.stunEnabled) normalized.stunServer else "off"}",
            )
        }.onFailure {
            DiagLog.e(TAG, "Unable to apply account", it)
            _registration.value = SipRegistrationState(
                status = RegistrationStatus.Failed,
                message = it.message,
            )
        }
    }

    @Synchronized
    fun clearAccount() {
        val current = core ?: run {
            _registration.value = SipRegistrationState()
            return
        }
        current.clearAccounts()
        current.clearAllAuthInfo()
        _registration.value = SipRegistrationState()
        DiagLog.i(TAG, "Account cleared")
    }

    @Synchronized
    fun refreshRegistration() {
        core?.refreshRegisters()
        DiagLog.i(TAG, "Manual registration refresh requested")
    }

    @Synchronized
    fun shutdown() {
        networkMonitor?.stop()
        networkMonitor = null
        core?.let { current ->
            runCatching {
                current.clearAccounts()
                current.stop()
            }
        }
        core = null
        _registration.value = SipRegistrationState()
    }

    fun debugSummary(): List<String> {
        val current = core
        return buildList {
            add("内核: ${if (current != null) "已启动" else "未启动"}")
            if (current != null) {
                val transports = runCatching { current.transports }.getOrNull()
                add("传输端口: udp=${transports?.udpPort} tcp=${transports?.tcpPort} tls=${transports?.tlsPort}")
                add("网络可达: ${current.isNetworkReachable()}")
                val account = current.defaultAccount
                add("默认账号: ${account?.params?.identityAddress?.asString() ?: "无"}")
                add("注册状态: ${account?.state ?: "无"}")
            }
            add("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }

    private fun applyDefaultTransports(current: Core) {
        current.setIpv6Enabled(false)
        val transports = Factory.instance().createTransports()
        transports.setUdpPort(localSipPort)
        transports.setTcpPort(localSipPort)
        transports.setTlsPort(localSipPort + 1)
        val result = current.setTransports(transports)
        DiagLog.i(
            TAG,
            "Transports configured udp=$localSipPort tcp=$localSipPort tls=${localSipPort + 1} " +
                "ipv6=off result=$result",
        )
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String,
        ) {
            val status = when (state) {
                RegistrationState.Ok -> RegistrationStatus.Registered
                RegistrationState.Progress -> RegistrationStatus.InProgress
                RegistrationState.Refreshing -> RegistrationStatus.Registered
                RegistrationState.Failed -> RegistrationStatus.Failed
                RegistrationState.Cleared -> RegistrationStatus.Cleared
                RegistrationState.None -> RegistrationStatus.Idle
            }
            val errorInfo = if (status == RegistrationStatus.Failed) account.errorInfo else null
            val errorCode = errorInfo?.protocolCode?.takeIf { it > 0 }
            val errorPhrase = errorInfo?.phrase
            _registration.value = SipRegistrationState(
                status = status,
                message = if (status == RegistrationStatus.Failed) {
                    mapRegistrationError("$errorCode $errorPhrase $message")
                } else {
                    message
                },
                rawMessage = message,
                errorCode = errorCode,
                errorPhrase = errorPhrase,
                identity = account.params.identityAddress?.asString(),
            )
            DiagLog.i(
                TAG,
                "Registration state=$state raw=\"$message\" code=$errorCode phrase=$errorPhrase " +
                    "reason=${errorInfo?.reason}",
            )
            if (status == RegistrationStatus.Failed) {
                scope.launch {
                    val restricted = NetworkSelfTest.isLocalNetworkRestricted()
                    if (restricted) {
                        DiagLog.e(TAG, "Local network binding is restricted (EPERM), sockets denied")
                        _registration.value = _registration.value.copy(networkRestricted = true)
                    }
                }
            }
        }
    }
}

private fun SipTransport.toLinphone(): TransportType = when (this) {
    SipTransport.UDP -> TransportType.Udp
    SipTransport.TCP -> TransportType.Tcp
    SipTransport.TLS -> TransportType.Tls
}
