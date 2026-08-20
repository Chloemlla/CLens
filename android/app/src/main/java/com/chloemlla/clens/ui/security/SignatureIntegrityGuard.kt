package com.chloemlla.clens.ui.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Verifies the installed APK is signed by the expected release certificate.
 * A repackaged/tampered APK carries a different signature and is rejected.
 *
 * Inactive until EXPECTED_SIGNATURE_SHA256 is populated with the release
 * certificate digest: build a signed APK, run it on a device, read the logged
 * `current_signature_sha256=` line, and paste the value here. Blank = disabled.
 */
object SignatureIntegrityGuard {
    fun assess(context: Context): List<String> {
        if (EXPECTED_SIGNATURE_SHA256.isBlank()) return emptyList()
        return runCatching {
            val digest = signingCertSha256(context)
            when {
                digest == null -> listOf("no-signature")
                !digest.equals(EXPECTED_SIGNATURE_SHA256, ignoreCase = true) -> listOf("signature-mismatch")
                else -> emptyList()
            }
        }.getOrElse { listOf("signature-check-error") }
    }

    /** Logs the running APK's signing-cert digest so the expected value can be captured. */
    fun logCurrentDigest(context: Context) {
        runCatching {
            val digest = signingCertSha256(context) ?: return@runCatching
            Log.w(TAG, "current_signature_sha256=$digest")
        }
    }

    private fun signingCertSha256(context: Context): String? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners ?: return null
            if (signers.isEmpty()) null else sha256(signers[0].toByteArray())
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val signatures = info.signatures ?: return null
            if (signatures.isEmpty()) null else sha256(signatures[0].toByteArray())
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private const val TAG = "SignatureIntegrityGuard"
    private const val EXPECTED_SIGNATURE_SHA256 = ""
}
