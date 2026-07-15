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

    val activeProfile: MongoConnectionProfile?
        get() = profileRef.get()

    val isConnected: Boolean
        get() = clientRef.get() != null

    suspend fun connect(profile: MongoConnectionProfile): ConnectionTestResult = withContext(Dispatchers.IO) {
        val uri = MongoUriBuilder.build(profile)
        val client = createClient(uri)
        try {
            val started = System.nanoTime()
            val ping = client.getDatabase("admin").runCommand(Document("ping", 1))
            val latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val version = readVersion(client)
            swapClient(client, profile)
            ConnectionTestResult(
                ok = isCommandOk(ping),
                latencyMillis = latency,
                serverVersion = version,
                message = "已连接 " + profile.displayTarget + (version?.let { " · MongoDB $it" } ?: ""),
            )
        } catch (error: Throwable) {
            runCatching { client.close() }
            throw wrapConnectionFailure("连接失败", error)
        }
    }

    suspend fun test(profile: MongoConnectionProfile): ConnectionTestResult = withContext(Dispatchers.IO) {
        val uri = MongoUriBuilder.build(profile)
        val client = createClient(uri)
        try {
            val started = System.nanoTime()
            client.getDatabase("admin").runCommand(Document("ping", 1))
            val latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val version = readVersion(client)
            ConnectionTestResult(
                ok = true,
                latencyMillis = latency,
                serverVersion = version,
                message = "测试成功 · ${latency}ms" + (version?.let { " · MongoDB $it" } ?: ""),
            )
        } catch (error: Throwable) {
            throw wrapConnectionFailure("连接测试失败", error)
        } finally {
            runCatching { client.close() }
        }
    }

    fun requireClient(): MongoClient {
        return clientRef.get()
            ?: throw MongoAdminException.Validation("尚未连接 MongoDB。请先在「连接」页建立会话。")
    }

    fun disconnect() {
        clientRef.getAndSet(null)?.let { runCatching { it.close() } }
        profileRef.set(null)
    }

    private fun swapClient(client: MongoClient, profile: MongoConnectionProfile) {
        clientRef.getAndSet(client)?.let { previous -> runCatching { previous.close() } }
        profileRef.set(profile)
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
}
