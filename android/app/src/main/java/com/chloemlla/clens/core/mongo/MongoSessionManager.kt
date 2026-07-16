package com.chloemlla.clens.core.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

class MongoSessionManager {
    private val clientRef = AtomicReference<MongoClient?>(null)
    private val profileRef = AtomicReference<MongoConnectionProfile?>(null)
    private val tunnelRef = AtomicReference<SshTunnelSession?>(null)

    val activeProfile: MongoConnectionProfile?
        get() = profileRef.get()

    val isConnected: Boolean
        get() = clientRef.get() != null

    suspend fun connect(profile: MongoConnectionProfile): ConnectionTestResult = withContext(Dispatchers.IO) {
        ensureMongoAuthClassesLoaded()
        val prepared = prepareEndpoint(profile)
        val client = createClient(prepared.uri)
        try {
            val started = System.nanoTime()
            val ping = client.getDatabase("admin").runCommand(Document("ping", 1))
            val latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val version = readVersion(client)
            swapClient(client, profile, prepared.tunnel)
            ConnectionTestResult(
                ok = isCommandOk(ping),
                latencyMillis = latency,
                serverVersion = version,
                message = buildConnectMessage(profile, prepared, version, latency, testing = false),
            )
        } catch (error: Throwable) {
            runCatching { client.close() }
            runCatching { prepared.tunnel?.close() }
            throw wrapConnectionFailure("连接失败", error)
        }
    }

    suspend fun test(profile: MongoConnectionProfile): ConnectionTestResult = withContext(Dispatchers.IO) {
        ensureMongoAuthClassesLoaded()
        val prepared = prepareEndpoint(profile)
        val client = createClient(prepared.uri)
        try {
            val started = System.nanoTime()
            client.getDatabase("admin").runCommand(Document("ping", 1))
            val latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val version = readVersion(client)
            ConnectionTestResult(
                ok = true,
                latencyMillis = latency,
                serverVersion = version,
                message = buildConnectMessage(profile, prepared, version, latency, testing = true),
            )
        } catch (error: Throwable) {
            throw wrapConnectionFailure("连接测试失败", error)
        } finally {
            runCatching { client.close() }
            runCatching { prepared.tunnel?.close() }
        }
    }

    fun requireClient(): MongoClient {
        return clientRef.get()
            ?: throw MongoAdminException.Validation("尚未连接 MongoDB。请先在「连接」页建立会话。")
    }

    fun disconnect() {
        clientRef.getAndSet(null)?.let { runCatching { it.close() } }
        tunnelRef.getAndSet(null)?.let { runCatching { it.close() } }
        profileRef.set(null)
    }

    /**
     * Probe the active session with admin.ping without replacing the client.
     * Throws when no session exists or the ping fails.
     */
    suspend fun healthPing(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val client = clientRef.get()
            ?: throw MongoAdminException.Validation("尚未连接 MongoDB。")
        try {
            val started = System.nanoTime()
            val ping = client.getDatabase("admin").runCommand(Document("ping", 1))
            val latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val ok = isCommandOk(ping)
            ConnectionTestResult(
                ok = ok,
                latencyMillis = latency,
                serverVersion = null,
                message = if (ok) "会话正常 · ${latency}ms" else "会话探测失败",
            )
        } catch (error: Throwable) {
            throw wrapConnectionFailure("会话探测失败", error)
        }
    }

    /**
     * Rebuild the client from [profileRef] and swap safely.
     * Returns the connect result or throws when no profile is retained.
     */
    suspend fun reconnectActive(): ConnectionTestResult {
        val profile = profileRef.get()
            ?: throw MongoAdminException.Validation("没有可重连的活动连接配置。")
        return connect(profile)
    }

    private fun swapClient(
        client: MongoClient,
        profile: MongoConnectionProfile,
        tunnel: SshTunnelSession?,
    ) {
        clientRef.getAndSet(client)?.let { previous -> runCatching { previous.close() } }
        tunnelRef.getAndSet(tunnel)?.let { previous -> runCatching { previous.close() } }
        profileRef.set(profile)
    }

    private data class PreparedEndpoint(
        val uri: String,
        val tunnel: SshTunnelSession? = null,
    )

    private fun prepareEndpoint(profile: MongoConnectionProfile): PreparedEndpoint {
        if (!profile.sshEnabled) {
            return PreparedEndpoint(uri = MongoUriBuilder.build(profile))
        }
        SshTunnelSession.validate(profile)
        val tunnel = SshTunnelSession.open(profile)
        return try {
            val uri = MongoUriBuilder.buildForLocalForward(profile, tunnel.localPort)
            PreparedEndpoint(uri = uri, tunnel = tunnel)
        } catch (error: Throwable) {
            runCatching { tunnel.close() }
            throw error
        }
    }

    private fun buildConnectMessage(
        profile: MongoConnectionProfile,
        prepared: PreparedEndpoint,
        version: String?,
        latency: Long,
        testing: Boolean,
    ): String {
        val base = if (testing) {
            "测试成功 · ${latency}ms"
        } else {
            "已连接 " + profile.displayTarget
        }
        val ssh = if (profile.sshEnabled) {
            " · SSH " + profile.sshHost.trim() + " → 127.0.0.1:" + (prepared.tunnel?.localPort ?: "?")
        } else {
            ""
        }
        val ver = version?.let { " · MongoDB $it" }.orEmpty()
        return base + ssh + ver
    }

    private fun createClient(uri: String): MongoClient {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .applyToClusterSettings { builder ->
                builder.serverSelectionTimeout(8, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { builder ->
                builder.connectTimeout(8, TimeUnit.SECONDS)
                builder.readTimeout(30, TimeUnit.SECONDS)
            }
            .applicationName("CLens-Android")
            .build()
        return MongoClient.create(settings)
    }

    /**
     * Force-load Mongo SCRAM/SASL implementation classes on the coroutine worker
     * before opening sockets. The driver resolves these from async NIO completion
     * threads where an uncaught [NoClassDefFoundError] would kill the process
     * outside [ClensActionRunner]'s try/catch.
     *
     * Class.forName also gives R8 a hard reflective keep root for nested
     * authenticator classes under full mode.
     */
    private fun ensureMongoAuthClassesLoaded() {
        for (className in REQUIRED_MONGO_AUTH_CLASSES) {
            try {
                Class.forName(className)
            } catch (error: Throwable) {
                when (error) {
                    is ClassNotFoundException, is NoClassDefFoundError, is ExceptionInInitializerError -> {
                        throw wrapConnectionFailure("Mongo 认证组件加载失败", error)
                    }
                    else -> throw error
                }
            }
        }
    }

    private suspend fun readVersion(client: MongoClient): String? {
        return runCatching {
            val buildInfo = client.getDatabase("admin").runCommand(Document("buildInfo", 1))
            buildInfo["version"]?.toString()
        }.getOrNull()
    }

    private fun isCommandOk(document: Document): Boolean {
        return when (val value = document["ok"]) {
            is Number -> value.toDouble() == 1.0
            else -> false
        }
    }

    private fun wrapConnectionFailure(prefix: String, error: Throwable): MongoAdminException {
        val detail = when (error) {
            is NoClassDefFoundError, is ClassNotFoundException -> {
                val missing = error.message?.substringAfter("Failed resolution of: ")?.trim()
                    ?: error.message
                    ?: error::class.java.simpleName
                "$prefix: Mongo 驱动类缺失 ($missing)。请升级到修复 R8 keep 规则后的版本。"
            }
            else -> error.message?.takeIf { it.isNotBlank() } ?: prefix
        }
        val cause = error as? Exception ?: Exception(detail, error)
        return MongoAdminException.Operation(detail, cause)
    }

    private companion object {
        val REQUIRED_MONGO_AUTH_CLASSES = listOf(
            "javax.security.sasl.SaslClient",
            "javax.security.sasl.SaslException",
            "com.mongodb.internal.connection.DefaultAuthenticator",
            "com.mongodb.internal.connection.SaslAuthenticator",
            "com.mongodb.internal.connection.SaslAuthenticator\$SaslClientImpl",
            "com.mongodb.internal.connection.ScramShaAuthenticator",
            "com.mongodb.internal.connection.ScramShaAuthenticator\$ScramShaSaslClient",
        )
    }
}
