package com.chloemlla.clens.core.mongo

import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Converts common PuTTY PPK v2 private keys into OpenSSH/PKCS#1 PEM material
 * that sshj can load. Focus: ssh-rsa, encryption none or aes256-cbc.
 */
object PpkKeyConverter {
    data class Result(
        val privateKeyPem: String,
        val publicKeyOpenSsh: String?,
        val comment: String?,
    )

    fun looksLikePpk(raw: String): Boolean {
        val text = raw.trim()
        return text.startsWith("PuTTY-User-Key-File", ignoreCase = true) ||
            text.contains("PuTTY-User-Key-File-", ignoreCase = true)
    }

    fun toOpenSshPem(raw: String, passphrase: String? = null): Result {
        val headers = parseHeaders(raw)
        val version = headers.version
        if (version != 2) {
            throw MongoAdminException.Validation("目前仅支持 PuTTY-User-Key-File-2（PPK2），当前为 PPK$version。")
        }
        val alg = headers.algorithm.lowercase()
        if (alg != "ssh-rsa") {
            throw MongoAdminException.Validation("目前 PPK 转换仅支持 ssh-rsa，当前算法：${headers.algorithm}")
        }
        val encryption = headers.encryption.lowercase()
        val publicBlob = decodeLines(headers.publicLines)
        var privateBlob = decodeLines(headers.privateLines)
        if (encryption == "aes256-cbc") {
            val pass = passphrase?.toCharArray()
                ?: throw MongoAdminException.Validation("该 .ppk 已加密，请填写私钥口令。")
            privateBlob = decryptPrivateBlob(privateBlob, pass)
        } else if (encryption != "none") {
            throw MongoAdminException.Validation("不支持的 PPK 加密方式：${headers.encryption}")
        }
        verifyMac(headers, publicBlob, privateBlob, passphrase)
        val rsa = parseRsaPrivate(publicBlob, privateBlob)
        val pem = toPkcs1Pem(rsa)
        val openSshPub = toOpenSshPublic(headers.algorithm, publicBlob, headers.comment)
        return Result(privateKeyPem = pem, publicKeyOpenSsh = openSshPub, comment = headers.comment)
    }

    private data class Headers(
        val version: Int,
        val algorithm: String,
        val encryption: String,
        val comment: String?,
        val publicLines: List<String>,
        val privateLines: List<String>,
        val privateMac: String,
    )

    private fun parseHeaders(raw: String): Headers {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines()
        if (lines.isEmpty()) throw MongoAdminException.Validation("PPK 内容为空。")
        val first = lines.first().trim()
        val versionMatch = Regex("""PuTTY-User-Key-File-(\d+):\s*(.+)""", RegexOption.IGNORE_CASE)
            .matchEntire(first)
            ?: throw MongoAdminException.Validation("不是有效的 PPK 文件头。")
        val version = versionMatch.groupValues[1].toInt()
        val algorithm = versionMatch.groupValues[2].trim()

        fun valueOf(prefix: String): String {
            val line = lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?: throw MongoAdminException.Validation("PPK 缺少字段：$prefix")
            return line.substring(prefix.length).trim()
        }

        val encryption = valueOf("Encryption:")
        val comment = lines.firstOrNull { it.startsWith("Comment:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
        val publicCount = valueOf("Public-Lines:").toIntOrNull()
            ?: throw MongoAdminException.Validation("Public-Lines 无效。")
        val publicStart = lines.indexOfFirst { it.startsWith("Public-Lines:", ignoreCase = true) } + 1
        val publicLines = lines.drop(publicStart).take(publicCount)
        val privateCountLineIdx = lines.indexOfFirst { it.startsWith("Private-Lines:", ignoreCase = true) }
        if (privateCountLineIdx < 0) throw MongoAdminException.Validation("PPK 缺少 Private-Lines。")
        val privateCount = lines[privateCountLineIdx].substringAfter(":").trim().toIntOrNull()
            ?: throw MongoAdminException.Validation("Private-Lines 无效。")
        val privateLines = lines.drop(privateCountLineIdx + 1).take(privateCount)
        val mac = valueOf("Private-MAC:")
        return Headers(
            version = version,
            algorithm = algorithm,
            encryption = encryption,
            comment = comment,
            publicLines = publicLines,
            privateLines = privateLines,
            privateMac = mac,
        )
    }

    private fun decodeLines(lines: List<String>): ByteArray {
        val joined = lines.joinToString("") { it.trim() }
        return try {
            Base64.decode(joined, Base64.DEFAULT)
        } catch (error: IllegalArgumentException) {
            throw MongoAdminException.Validation("PPK Base64 解码失败。", error)
        }
    }

    private fun decryptPrivateBlob(blob: ByteArray, passphrase: CharArray): ByteArray {
        // PuTTY KDF: SHA1(uint32_be(counter) || passphrase) repeated to 40 bytes; use first 32 as AES key, next 16 as IV? 
        // Actually: key = first 32 bytes of concatenated hashes; IV is 16 zero bytes for aes256-cbc in PPK2.
        val passBytes = String(passphrase).toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-1")
        val keyMaterial = ByteArray(40)
        var offset = 0
        var counter = 0
        while (offset < keyMaterial.size) {
            md.reset()
            md.update(
                ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(counter).array(),
            )
            md.update(passBytes)
            val dig = md.digest()
            val copy = minOf(dig.size, keyMaterial.size - offset)
            System.arraycopy(dig, 0, keyMaterial, offset, copy)
            offset += copy
            counter++
        }
        val key = keyMaterial.copyOfRange(0, 32)
        val iv = ByteArray(16) // zeros
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        if (blob.size % 16 != 0) {
            throw MongoAdminException.Validation("加密 PPK 私钥块长度无效。")
        }
        return cipher.doFinal(blob)
    }

    private fun verifyMac(
        headers: Headers,
        publicBlob: ByteArray,
        privateBlob: ByteArray,
        passphrase: String?,
    ) {
        // Private-MAC = hex(HMAC-SHA1(mac_key, sequence of string fields))
        // mac_key = SHA1("putty-private-key-file-mac-key" (+ passphrase if encrypted))
        val md = MessageDigest.getInstance("SHA-1")
        md.update("putty-private-key-file-mac-key".toByteArray(Charsets.US_ASCII))
        if (headers.encryption != "none" && !passphrase.isNullOrEmpty()) {
            md.update(passphrase.toByteArray(Charsets.UTF_8))
        }
        val macKey = md.digest()
        val macData = buildMacData(headers, publicBlob, privateBlob)
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(macKey, "HmacSHA1"))
        val digest = mac.doFinal(macData)
        val expected = headers.privateMac.trim().lowercase()
        val actual = digest.joinToString("") { b -> "%02x".format(b) }
        if (expected != actual) {
            throw MongoAdminException.Validation("PPK 校验失败：Private-MAC 不匹配（口令错误或文件损坏）。")
        }
    }

    private fun buildMacData(headers: Headers, publicBlob: ByteArray, privateBlob: ByteArray): ByteArray {
        // sequence of SSH strings: algorithm, encryption, comment, public_blob, private_blob
        fun sshString(bytes: ByteArray): ByteArray {
            val len = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(bytes.size).array()
            return len + bytes
        }
        fun sshString(text: String): ByteArray = sshString(text.toByteArray(Charsets.US_ASCII))
        return sshString(headers.algorithm) +
            sshString(headers.encryption) +
            sshString(headers.comment.orEmpty()) +
            sshString(publicBlob) +
            sshString(privateBlob)
    }

    private data class RsaPrivate(
        val n: BigInteger,
        val e: BigInteger,
        val d: BigInteger,
        val p: BigInteger,
        val q: BigInteger,
        val iqmp: BigInteger,
    )

    private fun parseRsaPrivate(publicBlob: ByteArray, privateBlob: ByteArray): RsaPrivate {
        val pub = SshBuffer(publicBlob)
        val pubAlg = pub.readString()
        if (pubAlg != "ssh-rsa") {
            throw MongoAdminException.Validation("公钥算法不是 ssh-rsa：$pubAlg")
        }
        val e = pub.readMpint()
        val n = pub.readMpint()
        val priv = SshBuffer(privateBlob)
        val d = priv.readMpint()
        val p = priv.readMpint()
        val q = priv.readMpint()
        val iqmp = priv.readMpint()
        return RsaPrivate(n = n, e = e, d = d, p = p, q = q, iqmp = iqmp)
    }

    private fun toPkcs1Pem(rsa: RsaPrivate): String {
        // RSAPrivateKey ::= SEQUENCE { version=0, n, e, d, p, q, exp1, exp2, coeff }
        val exp1 = rsa.d.mod(rsa.p.subtract(BigInteger.ONE))
        val exp2 = rsa.d.mod(rsa.q.subtract(BigInteger.ONE))
        val seq = derSequence(
            derInteger(BigInteger.ZERO),
            derInteger(rsa.n),
            derInteger(rsa.e),
            derInteger(rsa.d),
            derInteger(rsa.p),
            derInteger(rsa.q),
            derInteger(exp1),
            derInteger(exp2),
            derInteger(rsa.iqmp),
        )
        val b64 = Base64.encodeToString(seq, Base64.NO_WRAP)
        val chunks = b64.chunked(64).joinToString("\n")
        return "-----BEGIN RSA PRIVATE KEY-----\n$chunks\n-----END RSA PRIVATE KEY-----\n"
    }

    private fun toOpenSshPublic(algorithm: String, publicBlob: ByteArray, comment: String?): String {
        val body = Base64.encodeToString(publicBlob, Base64.NO_WRAP)
        return algorithm + " " + body + if (comment.isNullOrBlank()) "" else " $comment"
    }

    private fun derInteger(value: BigInteger): ByteArray {
        var bytes = value.toByteArray() // already two's complement big-endian
        return derTag(0x02, bytes)
    }

    private fun derSequence(vararg parts: ByteArray): ByteArray {
        val body = parts.fold(ByteArray(0)) { acc, item -> acc + item }
        return derTag(0x30, body)
    }

    private fun derTag(tag: Int, body: ByteArray): ByteArray {
        val len = body.size
        val lenBytes = when {
            len < 0x80 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            len <= 0xFFFF -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), len.toByte())
            else -> byteArrayOf(
                0x83.toByte(),
                (len shr 16).toByte(),
                (len shr 8).toByte(),
                len.toByte(),
            )
        }
        return byteArrayOf(tag.toByte()) + lenBytes + body
    }

    private class SshBuffer(private val data: ByteArray) {
        private var pos = 0

        fun readString(): String {
            val bytes = readBytes()
            return bytes.toString(Charsets.US_ASCII)
        }

        fun readMpint(): BigInteger {
            val bytes = readBytes()
            return if (bytes.isEmpty()) BigInteger.ZERO else BigInteger(1, bytes)
        }

        private fun readBytes(): ByteArray {
            if (pos + 4 > data.size) throw MongoAdminException.Validation("PPK 结构截断。")
            val len = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            pos += 4
            if (len < 0 || pos + len > data.size) throw MongoAdminException.Validation("PPK 结构无效。")
            val out = data.copyOfRange(pos, pos + len)
            pos += len
            return out
        }
    }
}
