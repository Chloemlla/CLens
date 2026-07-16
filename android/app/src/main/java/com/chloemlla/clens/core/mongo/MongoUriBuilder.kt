package com.chloemlla.clens.core.mongo

import com.mongodb.ConnectionString
import java.net.URLEncoder

object MongoUriBuilder {
    fun build(profile: MongoConnectionProfile): String {
        val directUri = profile.uri.trim()
        if (directUri.isNotBlank()) {
            return validateDirectUri(directUri)
        }

        val host = profile.host.trim().ifBlank {
            throw MongoAdminException.Validation("主机不能为空。")
        }
        if (profile.port !in 1..65535) {
            throw MongoAdminException.Validation("端口必须在 1-65535。")
        }

        val credentials = if (profile.username.isNotBlank()) {
            val user = encode(profile.username.trim())
            val pass = encode(profile.password)
            "$user:$pass@"
        } else {
            ""
        }

        val authSource = profile.authDatabase.trim().ifBlank { "admin" }
        val query = buildList {
            add("authSource=$authSource")
            if (profile.tls) add("tls=true")
            if (profile.directConnection) add("directConnection=true")
            if (profile.replicaSet.isNotBlank()) add("replicaSet=${encode(profile.replicaSet.trim())}")
        }.joinToString("&")

        val pathDb = profile.defaultDatabase.trim().takeIf { it.isNotBlank() }?.let { "/${encode(it)}" }.orEmpty()
        return "mongodb://$credentials$host:${profile.port}$pathDb?$query"
    }

    fun maskUri(uri: String): String {
        return uri.replace(
            Regex("""(mongodb(?:\+srv)?://)([^:/@\s]+):([^@/\s]+)@""", RegexOption.IGNORE_CASE),
        ) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}:***@"
        }
    }

    fun prettyJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "{}"
        return try {
            val element = org.json.JSONTokener(trimmed).nextValue()
            when (element) {
                is org.json.JSONObject -> element.toString(2)
                is org.json.JSONArray -> element.toString(2)
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun validateJsonObject(raw: String, fieldName: String = "JSON"): String {
        val trimmed = raw.trim().ifBlank { "{}" }
        return try {
            val value = org.json.JSONTokener(trimmed).nextValue()
            if (value !is org.json.JSONObject) {
                throw MongoAdminException.Validation("$fieldName 必须是 JSON 对象。")
            }
            value.toString()
        } catch (error: MongoAdminException) {
            throw error
        } catch (error: Exception) {
            throw MongoAdminException.Validation("$fieldName 无效: ${error.message}")
        }
    }

    fun validateJsonArray(raw: String, fieldName: String = "JSON"): String {
        val trimmed = raw.trim().ifBlank { "[]" }
        return try {
            val value = org.json.JSONTokener(trimmed).nextValue()
            if (value !is org.json.JSONArray) {
                throw MongoAdminException.Validation("$fieldName 必须是 JSON 数组。")
            }
            value.toString()
        } catch (error: MongoAdminException) {
            throw error
        } catch (error: Exception) {
            throw MongoAdminException.Validation("$fieldName 无效: ${error.message}")
        }
    }

    fun validateDirectUri(uri: String): String {
        val trimmed = uri.trim()
        val lower = trimmed.lowercase()
        if (!lower.startsWith("mongodb://") && !lower.startsWith("mongodb+srv://")) {
            throw MongoAdminException.Validation("URI 必须以 mongodb:// 或 mongodb+srv:// 开头。")
        }
        return try {
            ConnectionString(trimmed)
            trimmed
        } catch (error: Exception) {
            throw MongoAdminException.Validation("MongoDB URI 无效: " + (error.message ?: "parse error"))
        }
    }

    /**
     * Best-effort parse of a Mongo URI into discrete form fields.
     * Returns null when the scheme is missing / invalid enough that ConnectionString cannot parse it.
     * Credentials remain available to the caller; do not log the result.
     */
    fun parseUriToFormFields(uri: String): ParsedUriFields? {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return null
        return try {
            val cs = ConnectionString(trimmed)
            val hosts = cs.hosts
            val hostPort = hosts?.firstOrNull()
            val host: String
            val port: Int?
            if (hostPort.isNullOrBlank()) {
                host = ""
                port = null
            } else {
                val splitIdx = hostPort.lastIndexOf(':')
                if (splitIdx > 0 && hostPort.indexOf(']') < 0) {
                    host = hostPort.substring(0, splitIdx)
                    port = hostPort.substring(splitIdx + 1).toIntOrNull()
                } else if (hostPort.startsWith("[") && hostPort.contains("]:")) {
                    val end = hostPort.indexOf("]:")
                    host = hostPort.substring(1, end)
                    port = hostPort.substring(end + 2).toIntOrNull()
                } else {
                    host = hostPort.trim('[', ']')
                    port = null
                }
            }
            val query = trimmed.substringAfter('?', missingDelimiterValue = "")
            fun queryParam(name: String): String? {
                if (query.isBlank()) return null
                return query.split('&')
                    .asSequence()
                    .map { part ->
                        val eq = part.indexOf('=')
                        if (eq <= 0) return@map null to null
                        part.substring(0, eq) to part.substring(eq + 1)
                    }
                    .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
                    ?.second
            }
            ParsedUriFields(
                uri = trimmed,
                host = host,
                port = port,
                username = cs.username.orEmpty(),
                password = cs.password?.let { String(it) }.orEmpty(),
                authDatabase = cs.credential?.source
                    ?.takeIf { it.isNotBlank() }
                    ?: queryParam("authSource").orEmpty(),
                defaultDatabase = cs.database.orEmpty(),
                replicaSet = queryParam("replicaSet").orEmpty(),
                tls = queryParam("tls")?.equals("true", ignoreCase = true) == true ||
                    queryParam("ssl")?.equals("true", ignoreCase = true) == true,
                directConnection = queryParam("directConnection")
                    ?.equals("true", ignoreCase = true) == true,
            )
        } catch (_: Exception) {
            null
        }
    }

    data class ParsedUriFields(
        val uri: String,
        val host: String = "",
        val port: Int? = null,
        val username: String = "",
        val password: String = "",
        val authDatabase: String = "",
        val defaultDatabase: String = "",
        val replicaSet: String = "",
        val tls: Boolean = false,
        val directConnection: Boolean = false,
    )

    private fun encode(value: String): String =
        // Use the String charset overload for minSdk 26 compatibility (Charset overload is API 33+).
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
