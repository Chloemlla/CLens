package com.chloemlla.clens.core.mongo

import org.json.JSONObject

/**
 * Pure helpers for importing Mongo connection data from free text / QR payloads.
 * Never log the raw payload: callers must treat URIs as secret-bearing.
 */
object UriImportParser {
    data class ImportPayload(
        val uri: String,
        val name: String? = null,
    )

    private val mongoUriRegex = Regex(
        """mongodb(?:\+srv)?://[^\s"'<>\\]+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extract the first `mongodb://` or `mongodb+srv://` token from free text.
     * Trailing punctuation commonly pasted from chat apps is stripped.
     */
    fun extractUri(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = mongoUriRegex.find(text.trim()) ?: return null
        return sanitizeUriCandidate(match.value)
    }

    /**
     * Parse free text or a simple JSON config:
     * `{"name":"...","uri":"mongodb://..."}` (also accepts `connectionString` / `connectionUri`).
     */
    fun parseImportPayload(text: String?): ImportPayload? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()

        parseJsonPayload(trimmed)?.let { return it }

        val uri = extractUri(trimmed) ?: return null
        return ImportPayload(uri = uri)
    }

    private fun parseJsonPayload(raw: String): ImportPayload? {
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        return try {
            val json = JSONObject(raw)
            val name = json.optString("name")
                .ifBlank { json.optString("connectionName") }
                .ifBlank { json.optString("title") }
                .takeIf { it.isNotBlank() }

            val uriCandidate = sequenceOf("uri", "connectionString", "connectionUri", "mongoUri", "mongodbUri")
                .map { key -> json.optString(key) }
                .firstOrNull { it.isNotBlank() }
                ?: return extractUri(raw)?.let { ImportPayload(uri = it, name = name) }

            val uri = extractUri(uriCandidate) ?: sanitizeUriCandidate(uriCandidate.trim())
            if (!looksLikeMongoUri(uri)) return null
            ImportPayload(uri = uri, name = name)
        } catch (_: Exception) {
            null
        }
    }

    fun looksLikeMongoUri(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val lower = value.trim().lowercase()
        return lower.startsWith("mongodb://") || lower.startsWith("mongodb+srv://")
    }

    private fun sanitizeUriCandidate(raw: String): String {
        var candidate = raw.trim()
        // Strip common trailing wrappers from chat / markdown pastes.
        while (candidate.isNotEmpty() && candidate.last() in trailingJunk) {
            candidate = candidate.dropLast(1)
        }
        return candidate
    }

    private val trailingJunk = setOf(',', ';', '.', ')', ']', '}', '"', '\'', '`', '>', '，', '。', '；')
}
