package com.chloemlla.clens.core.mongo

import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Builds an [SSLContext] from optional PEM materials for custom CA / client certs.
 * Returns null when no custom material is present so the driver uses defaults.
 */
object MongoTlsConfig {
    fun hasCustomMaterial(profile: MongoConnectionProfile): Boolean {
        return profile.tlsCaPem.isNotBlank() ||
            profile.tlsClientCertPem.isNotBlank() ||
            profile.tlsClientKeyPem.isNotBlank()
    }

    fun sslContextOrNull(profile: MongoConnectionProfile): SSLContext? {
        if (!hasCustomMaterial(profile)) return null
        return try {
            buildSslContext(profile)
        } catch (error: MongoAdminException) {
            throw error
        } catch (error: Exception) {
            throw MongoAdminException.Validation(
                "TLS 证书材料无效：" + (error.message ?: error::class.java.simpleName),
                error,
            )
        }
    }

    private fun buildSslContext(profile: MongoConnectionProfile): SSLContext {
        val trustManagers = if (profile.tlsCaPem.isNotBlank()) {
            val certs = parseCertificates(profile.tlsCaPem)
            if (certs.isEmpty()) {
                throw MongoAdminException.Validation("CA 证书 PEM 无法解析。")
            }
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)
            certs.forEachIndexed { index, cert ->
                trustStore.setCertificateEntry("ca-$index", cert)
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustStore)
            tmf.trustManagers
        } else {
            null
        }

        val keyManagers = if (profile.tlsClientCertPem.isNotBlank() || profile.tlsClientKeyPem.isNotBlank()) {
            if (profile.tlsClientCertPem.isBlank() || profile.tlsClientKeyPem.isBlank()) {
                throw MongoAdminException.Validation("客户端证书与私钥需要同时提供。")
            }
            val certs = parseCertificates(profile.tlsClientCertPem)
            if (certs.isEmpty()) {
                throw MongoAdminException.Validation("客户端证书 PEM 无法解析。")
            }
            val privateKey = parsePrivateKey(profile.tlsClientKeyPem, profile.tlsClientKeyPassphrase)
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            val password = (profile.tlsClientKeyPassphrase.ifBlank { "clens" }).toCharArray()
            keyStore.load(null, null)
            keyStore.setKeyEntry("client", privateKey, password, certs.toTypedArray())
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password)
            kmf.keyManagers
        } else {
            null
        }

        val ctx = SSLContext.getInstance("TLS")
        ctx.init(keyManagers, trustManagers, null)
        return ctx
    }

    private fun parseCertificates(pem: String): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val input = ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII))
        return factory.generateCertificates(input).filterIsInstance<X509Certificate>()
    }

    private fun parsePrivateKey(pem: String, passphrase: String): java.security.PrivateKey {
        val normalized = pem.replace("\r\n", "\n").trim()
        if (normalized.contains("BEGIN OPENSSH PRIVATE KEY")) {
            throw MongoAdminException.Validation("客户端私钥请使用 PKCS#8 / PKCS#1 PEM，暂不支持 OpenSSH 私有格式。")
        }
        if (normalized.contains("ENCRYPTED") && passphrase.isBlank()) {
            throw MongoAdminException.Validation("加密客户端私钥需要填写口令。")
        }
        if (normalized.contains("ENCRYPTED")) {
            // Avoid pulling BouncyCastle; require unencrypted PKCS8 for mobile form paste.
            throw MongoAdminException.Validation("请提供未加密的 PKCS#8 客户端私钥 PEM（或先在电脑上解密）。")
        }
        val body = extractPemBody(normalized)
        val decoded = Base64.getMimeDecoder().decode(body)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val algorithms = listOf("RSA", "EC", "DSA")
        var last: Exception? = null
        for (alg in algorithms) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(keySpec)
            } catch (error: Exception) {
                last = error
            }
        }
        // PKCS#1 RSA fallback: wrap into PKCS#8
        if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
            val pkcs8 = wrapPkcs1RsaToPkcs8(decoded)
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        }
        throw MongoAdminException.Validation(
            "无法解析客户端私钥：" + (last?.message ?: "unsupported key"),
            last,
        )
    }

    private fun extractPemBody(pem: String): String {
        return pem.lineSequence()
            .filterNot { it.startsWith("-----") || it.isBlank() }
            .joinToString("")
    }

    private fun wrapPkcs1RsaToPkcs8(pkcs1: ByteArray): ByteArray {
        // PrivateKeyInfo ::= SEQUENCE { version INTEGER 0, algorithm, privateKey OCTET STRING }
        val rsaOid = byteArrayOf(
            0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
        )
        val nullParams = byteArrayOf(0x05, 0x00)
        val algId = derSequence(rsaOid + nullParams)
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val octet = derOctetString(pkcs1)
        return derSequence(version + algId + octet)
    }

    private fun derOctetString(body: ByteArray): ByteArray = derTag(0x04, body)

    private fun derSequence(body: ByteArray): ByteArray = derTag(0x30, body)

    private fun derTag(tag: Int, body: ByteArray): ByteArray {
        val len = body.size
        val lenBytes = when {
            len < 0x80 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            len <= 0xFFFF -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(0x83.toByte(), (len shr 16).toByte(), (len shr 8).toByte(), len.toByte())
        }
        return byteArrayOf(tag.toByte()) + lenBytes + body
    }
}
