package com.chloemlla.clens.core.mongo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Best-effort field name inference for the visual find builder.
 * Sources: sample document JSON strings + index key JSON strings.
 * Manual field entry is always allowed by the UI even when inference returns empty.
 */
object QueryFieldInferencer {
    fun inferFieldNames(
        sampleDocumentsJson: List<String> = emptyList(),
        indexKeysJson: List<String> = emptyList(),
        maxDepth: Int = 3,
        maxFields: Int = 80,
    ): List<String> {
        val ordered = linkedSetOf<String>()
        sampleDocumentsJson.forEach { raw ->
            val root = parseObject(raw) ?: return@forEach
            collectKeys(root, prefix = "", depth = 0, maxDepth = maxDepth, into = ordered)
        }
        indexKeysJson.forEach { raw ->
            val keys = parseObject(raw) ?: return@forEach
            keys.keys().asSequence().forEach { key ->
                if (key.isNotBlank()) ordered += key
            }
        }
        return ordered
            .asSequence()
            .filter { it.isNotBlank() }
            .take(maxFields)
            .toList()
    }

    private fun collectKeys(
        value: Any?,
        prefix: String,
        depth: Int,
        maxDepth: Int,
        into: MutableSet<String>,
    ) {
        if (depth > maxDepth || value == null || value == JSONObject.NULL) return
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.isNullOrBlank()) continue
                    val path = if (prefix.isBlank()) key else "$prefix.$key"
                    into += path
                    collectKeys(value.opt(key), path, depth + 1, maxDepth, into)
                }
            }
            is JSONArray -> {
                if (value.length() == 0) return
                // Inspect first element only to keep inference cheap/stable.
                collectKeys(value.opt(0), prefix, depth + 1, maxDepth, into)
            }
        }
    }

    private fun parseObject(raw: String): JSONObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return runCatching {
            when {
                text.startsWith("{") -> JSONObject(text)
                text.startsWith("[") -> {
                    val array = JSONArray(text)
                    if (array.length() == 0) null else array.optJSONObject(0)
                }
                else -> null
            }
        }.getOrNull()
    }
}
