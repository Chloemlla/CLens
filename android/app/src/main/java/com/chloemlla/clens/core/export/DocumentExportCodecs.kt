package com.chloemlla.clens.core.export

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure document export codecs for multi-format share workflows.
 *
 * Callers own size limits and file IO. These helpers stay allocation-conscious
 * for phone use: single-pass field discovery where possible, StringBuilder output,
 * and no intermediate pretty-print copies for JSONL.
 */
enum class DocumentExportFormat(
    val extension: String,
    val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    EXTENDED_JSON_LINES("jsonl", "application/x-ndjson"),
}

object DocumentExportCodecs {
    private const val INVALID_RAW_FIELD = "_raw"

    fun toPrettyJsonArray(documents: List<String>): String {
        val array = JSONArray()
        documents.forEach { raw ->
            runCatching { array.put(JSONObject(raw)) }.getOrElse { array.put(raw) }
        }
        return array.toString(2)
    }

    /**
     * Flatten top-level fields only. Nested objects/arrays become compact JSON
     * string cells. Invalid document JSON is preserved under [INVALID_RAW_FIELD].
     */
    fun toCsv(documents: List<String>): String {
        if (documents.isEmpty()) return ""

        val rows = ArrayList<Map<String, String>>(documents.size)
        val keys = linkedSetOf<String>()
        documents.forEach { raw ->
            val fields = parseTopLevelFields(raw)
            rows += fields
            keys += fields.keys
        }

        val headers = orderHeaders(keys)
        return buildString(capacity = estimateCsvCapacity(documents, headers.size)) {
            appendCsvRow(headers)
            rows.forEach { row ->
                append('\n')
                appendCsvRow(headers.map { header -> row[header].orEmpty() })
            }
        }
    }

    /**
     * Relaxed Extended JSON lines: one document JSON per line (`.jsonl`).
     * Valid objects are re-serialized compactly; invalid input stays on one line.
     */
    fun toExtendedJsonLines(documents: List<String>): String {
        if (documents.isEmpty()) return ""
        return buildString(capacity = estimateLinesCapacity(documents)) {
            documents.forEachIndexed { index, raw ->
                if (index > 0) append('\n')
                append(toSingleLineDocument(raw))
            }
        }
    }

    fun encode(documents: List<String>, format: DocumentExportFormat): String {
        return when (format) {
            DocumentExportFormat.JSON -> toPrettyJsonArray(documents)
            DocumentExportFormat.CSV -> toCsv(documents)
            DocumentExportFormat.EXTENDED_JSON_LINES -> toExtendedJsonLines(documents)
        }
    }

    private fun parseTopLevelFields(raw: String): Map<String, String> {
        return runCatching {
            val obj = JSONObject(raw)
            val fields = LinkedHashMap<String, String>()
            val iterator = obj.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                fields[key] = topLevelCellValue(obj.opt(key))
            }
            fields
        }.getOrElse {
            linkedMapOf(INVALID_RAW_FIELD to raw)
        }
    }

    private fun topLevelCellValue(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject, is JSONArray -> value.toString()
            is Number, is Boolean -> value.toString()
            else -> value.toString()
        }
    }

    private fun orderHeaders(keys: Set<String>): List<String> {
        if (keys.isEmpty()) return emptyList()
        val ordered = ArrayList<String>(keys.size)
        if ("_id" in keys) {
            ordered += "_id"
        }
        ordered += keys.asSequence()
            .filter { it != "_id" && it != INVALID_RAW_FIELD }
            .sorted()
        // Keep fallback raw column last so valid schema stays readable.
        if (INVALID_RAW_FIELD in keys) {
            ordered += INVALID_RAW_FIELD
        }
        return ordered
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append(escapeCsv(value))
        }
    }

    internal fun escapeCsv(value: String): String {
        var needsQuotes = false
        var i = 0
        while (i < value.length) {
            when (value[i]) {
                ',', '"', '\n', '\r' -> {
                    needsQuotes = true
                    break
                }
            }
            i++
        }
        if (!needsQuotes) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun toSingleLineDocument(raw: String): String {
        return runCatching {
            JSONObject(raw).toString()
        }.getOrElse {
            // Keep one record per line even when input is not valid JSON.
            raw.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ')
        }
    }

    private fun estimateCsvCapacity(documents: List<String>, headerCount: Int): Int {
        var total = 0
        documents.forEach { total += it.length }
        // Header row + commas/quotes overhead; prefer one grow over many.
        return total + (documents.size + 1) * (headerCount + 8) + 64
    }

    private fun estimateLinesCapacity(documents: List<String>): Int {
        var total = 0
        documents.forEach { total += it.length }
        return total + documents.size
    }
}
