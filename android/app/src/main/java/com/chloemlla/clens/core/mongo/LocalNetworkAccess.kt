package com.chloemlla.clens.core.mongo

/**
 * Pure helpers for Android 17 local-network permission gating.
 * Does not touch Android framework APIs so unit tests stay JVM-friendly.
 */
object LocalNetworkAccess {
    fun requiresLocalNetworkPermission(profile: MongoConnectionProfile): Boolean {
        // Device socket target is the SSH bastion when tunneling; otherwise the Mongo host.
        val host = if (profile.sshEnabled) {
            profile.sshHost
        } else {
            resolveDirectHost(profile)
        }
        return hostRequiresLocalNetwork(host)
    }

    fun resolveDirectHost(profile: MongoConnectionProfile): String {
        val uri = profile.uri.trim()
        if (uri.isNotBlank()) {
            return hostFromMongoUri(uri) ?: profile.host
        }
        return profile.host
    }

    fun hostFromMongoUri(uri: String): String? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        val withoutScheme = when {
            trimmed.startsWith("mongodb+srv://", ignoreCase = true) ->
                trimmed.substring("mongodb+srv://".length)
            trimmed.startsWith("mongodb://", ignoreCase = true) ->
                trimmed.substring("mongodb://".length)
            else -> return null
        }
        val afterCreds = withoutScheme.substringAfter('@', missingDelimiterValue = withoutScheme)
        val authority = afterCreds.substringBefore('/').substringBefore('?')
        if (authority.isBlank()) return null
        return stripPort(authority)
    }

    fun hostRequiresLocalNetwork(host: String?): Boolean {
        val raw = host?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val normalized = normalizeHost(raw)
        if (normalized.isEmpty()) return false
        if (isLoopbackHost(normalized)) return false
        if (isPrivateIpv4(normalized) || isLinkLocalIpv4(normalized)) return true
        if (isUniqueLocalOrLinkLocalIpv6(normalized)) return true
        if (hasLocalSuffix(normalized)) return true
        // Single-label hostnames are commonly LAN mDNS / router aliases.
        if (!normalized.contains('.') && !normalized.contains(':')) return true
        return false
    }

    fun isLoopbackHost(host: String): Boolean {
        val h = normalizeHost(host)
        if (h == "localhost" || h == "localhost.") return true
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        if (h.startsWith("127.")) return true
        return false
    }

    private fun normalizeHost(host: String): String {
        var h = host.trim().lowercase()
        if (h.startsWith("[") && h.contains("]")) {
            h = h.substring(1, h.indexOf(']'))
        }
        return h.trimEnd('.')
    }

    private fun stripPort(authority: String): String {
        val value = authority.trim()
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end > 0) return value.substring(1, end)
        }
        // Avoid splitting IPv6 without brackets on ":".
        if (value.count { it == ':' } > 1) return value
        return value.substringBefore('%').substringBefore(':')
    }

    private fun hasLocalSuffix(host: String): Boolean {
        return host.endsWith(".local") ||
            host.endsWith(".lan") ||
            host.endsWith(".home") ||
            host.endsWith(".internal") ||
            host.endsWith(".intranet") ||
            host.endsWith(".corp")
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false
        val a = nums[0]
        val b = nums[1]
        if (a == 10) return true
        if (a == 192 && b == 168) return true
        if (a == 172 && b in 16..31) return true
        // Carrier-grade NAT often used on local edges.
        if (a == 100 && b in 64..127) return true
        return false
    }

    private fun isLinkLocalIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return a == 169 && b == 254
    }

    private fun isUniqueLocalOrLinkLocalIpv6(host: String): Boolean {
        if (!host.contains(':')) return false
        // fe80::/10 link-local, fc00::/7 unique local
        if (host.startsWith("fe8") || host.startsWith("fe9") ||
            host.startsWith("fea") || host.startsWith("feb")
        ) {
            return true
        }
        if (host.startsWith("fc") || host.startsWith("fd")) return true
        return false
    }
}
