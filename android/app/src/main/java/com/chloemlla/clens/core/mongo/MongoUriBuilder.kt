package com.chloemlla.clens.core.mongo

import java.net.URLEncoder

object MongoUriBuilder {
    fun build(profile: MongoConnectionProfile): String {
        val directUri = profile.uri.trim()
        if (directUri.isNotBlank()) {
            return directUri
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

    private fun encode(value: String): String =
        // Use the String charset overload for minSdk 26 compatibility (Charset overload is API 33+).
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}