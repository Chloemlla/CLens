package com.chloemlla.clens.core.mongo

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

/**
 * SSH local port-forward session for bastion access to MongoDB.
 * Supports password auth and OpenSSH PEM private keys. PPK is not supported.
 */
class SshTunnelSession private constructor(
    val localPort: Int,
    private val client: SSHClient,
    private val forwarderThread: Thread,
    private val closed: AtomicBoolean = AtomicBoolean(false),
) : Closeable {
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { forwarderThread.interrupt() }
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }

    companion object {
        fun open(profile: MongoConnectionProfile): SshTunnelSession {
            validate(profile)
            val sshHost = profile.sshHost.trim()
            val sshPort = profile.sshPort.takeIf { it in 1..65535 } ?: 22
            val username = profile.sshUsername.trim()
            val remoteHost = profile.sshRemoteHost.trim().ifBlank {
                profile.host.trim().ifBlank { "127.0.0.1" }
            }
            val remotePort = when {
                profile.sshRemotePort in 1..65535 -> profile.sshRemotePort
                profile.port in 1..65535 -> profile.port
                else -> 27017
            }

            val client = SSHClient()
            // Mobile ops tool: user explicitly chooses bastion; no TOFU store yet.
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 12_000
            client.timeout = 30_000
            try {
                client.connect(sshHost, sshPort)
                authenticate(client, profile, username)

                val serverSocket = ServerSocket()
                serverSocket.reuseAddress = true
                serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
                val localPort = serverSocket.localPort
                val params = Parameters("127.0.0.1", localPort, remoteHost, remotePort)
                val forwarder = client.newLocalPortForwarder(params, serverSocket)
                val thread = Thread(
                    {
                        try {
                            forwarder.listen()
                        } catch (_: Throwable) {
                            // Socket/thread shutdown is expected on close.
                        } finally {
                            runCatching { serverSocket.close() }
                        }
                    },
                    "clens-ssh-forward-$localPort",
                ).apply {
                    isDaemon = true
                    start()
                }
                return SshTunnelSession(localPort = localPort, client = client, forwarderThread = thread)
            } catch (error: Throwable) {
                runCatching { client.disconnect() }
                runCatching { client.close() }
                throw wrap(error)
            }
        }

        fun validate(profile: MongoConnectionProfile) {
            if (!profile.sshEnabled) return
            if (profile.sshHost.isBlank()) {
                throw MongoAdminException.Validation("SSH 主机不能为空。")
            }
            if (profile.sshPort !in 1..65535) {
                throw MongoAdminException.Validation("SSH 端口必须在 1-65535。")
            }
            if (profile.sshUsername.isBlank()) {
                throw MongoAdminException.Validation("SSH 用户名不能为空。")
            }
            val hasPassword = profile.sshPassword.isNotBlank()
            val hasKey = profile.sshPrivateKeyPem.isNotBlank()
            if (!hasPassword && !hasKey) {
                throw MongoAdminException.Validation("请提供 SSH 密码或 PEM 私钥（二选一）。")
            }
            if (hasKey && looksLikePpk(profile.sshPrivateKeyPem)) {
                throw MongoAdminException.Validation("暂不支持 .ppk 私钥，请先转换为 OpenSSH PEM 格式。")
            }
        }

        fun looksLikePpk(raw: String): Boolean {
            val text = raw.trim()
            return text.startsWith("PuTTY-User-Key-File", ignoreCase = true) ||
                text.contains("PuTTY-User-Key-File", ignoreCase = true)
        }

        private fun authenticate(client: SSHClient, profile: MongoConnectionProfile, username: String) {
            val keyPem = profile.sshPrivateKeyPem.trim()
            if (keyPem.isNotBlank()) {
                val passphrase = profile.sshPrivateKeyPassphrase
                    .takeIf { it.isNotBlank() }
                    ?.toCharArray()
                val finder = passphrase?.let { chars ->
                    object : PasswordFinder {
                        override fun reqPassword(resource: Resource<*>?): CharArray = chars.copyOf()
                        override fun shouldRetry(resource: Resource<*>?): Boolean = false
                    }
                }
                val keys: KeyProvider = client.loadKeys(keyPem, null, finder)
                client.authPublickey(username, keys)
                return
            }
            client.authPassword(username, profile.sshPassword)
        }

        private fun wrap(error: Throwable): MongoAdminException {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
            val lower = detail.lowercase()
            val message = when {
                "auth" in lower || "permission" in lower || "denied" in lower ->
                    "SSH 认证失败：$detail"
                "timeout" in lower || "timed out" in lower ->
                    "SSH 连接超时：$detail"
                "resolve" in lower || "unknown host" in lower ->
                    "SSH 主机不可达：$detail"
                else -> "SSH 隧道建立失败：$detail"
            }
            val cause = error as? Exception ?: Exception(detail, error)
            return MongoAdminException.Operation(message, cause)
        }
    }
}
