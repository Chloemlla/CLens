package com.chloemlla.clens.core.importdata

import org.json.JSONArray
import org.json.JSONObject

data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

/**
 * sourceToTarget: empty/blank target means skip the source field.
 */
data class FieldMapping(
    val sourceToTarget: LinkedHashMap<String, String>,
) {
    companion object {
        fun identity(fields: List<String>): FieldMapping {
            val map = LinkedHashMap<String, String>()
            fields.forEach { map[it] = it }
            return FieldMapping(map)
        }
    }
}

object DocumentImportCodecs {
    const val DEFAULT_CHUNK_SIZE: Int = 50

    fun previewJsonFields(jsonArrayText: String): List<String> {
        val fields = LinkedHashSet<String>()
        parseJsonArrayToDocStrings(jsonArrayText).forEach { raw ->
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            val keys = obj.keys()
            while (keys.hasNext()) fields.add(keys.next())
        }
        return fields.toList()
    }

    fun parseJsonArrayToDocStrings(jsonArrayText: String): List<String> {
        val trimmed = jsonArrayText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = runCatching { JSONArray(trimmed) }.getOrElse {
            // single object fallback
            val obj = runCatching { JSONObject(trimmed) }.getOrElse {
                throw IllegalArgumentException("无效的 JSON：需要数组或对象")
            }
            return listOf(obj.toString())
        }
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.get(i)
            when (value) {
                is JSONObject -> out.add(value.toString())
                JSONObject.NULL, null -> Unit
                else -> throw IllegalArgumentException("JSON 数组第 ${i + 1} 项不是对象")
            }
        }
        return out
    }

    fun parseCsv(csvText: String): CsvTable {
        val lines = splitCsvRecords(csvText)
        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV 为空")
        }
        val headers = lines.first()
        if (headers.isEmpty() || headers.all { it.isBlank() }) {
            throw IllegalArgumentException("CSV 缺少表头")
        }
        val rows = lines.drop(1).map { row ->
            if (row.size < headers.size) {
                row + List(headers.size - row.size) { "" }
            } else if (row.size > headers.size) {
                row.take(headers.size)
            } else {
                row
            }
        }
        return CsvTable(headers = headers, rows = rows)
    }

    fun applyCsvMapping(table: CsvTable, mapping: FieldMapping): List<String> {
        val out = ArrayList<String>(table.rows.size)
        table.rows.forEach { row ->
            val obj = JSONObject()
            table.headers.forEachIndexed { index, source ->
                val target = mapping.sourceToTarget[source]?.trim().orEmpty()
                if (target.isEmpty()) return@forEachIndexed
                val cell = row.getOrNull(index).orEmpty()
                obj.put(target, parseCell(cell))
            }
            out.add(obj.toString())
        }
        return out
    }

    fun toJsonArrayString(docStrings: List<String>): String {
        val array = JSONArray()
        docStrings.forEach { raw ->
            array.put(runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("_raw", raw) })
        }
        return array.toString()
    }

    fun chunk(docs: List<String>, size: Int = DEFAULT_CHUNK_SIZE): List<List<String>> {
        val chunkSize = size.coerceAtLeast(1)
        if (docs.isEmpty()) return emptyList()
        val out = ArrayList<List<String>>()
        var i = 0
        while (i < docs.size) {
            val end = (i + chunkSize).coerceAtMost(docs.size)
            out.add(docs.subList(i, end).toList())
            i = end
        }
        return out
    }

    private fun parseCell(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.equals("true", true) -> true
            trimmed.equals("false", true) -> false
            trimmed.equals("null", true) -> JSONObject.NULL
            trimmed.startsWith("{") -> runCatching { JSONObject(trimmed) }.getOrDefault(trimmed)
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrDefault(trimmed)
            trimmed.toLongOrNull() != null -> trimmed.toLong()
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> trimmed
        }
    }

    /**
     * RFC4180-ish record splitter supporting quotes and escaped quotes.
     */
    internal fun splitCsvRecords(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                !inQuotes && c == '"' -> inQuotes = true
                !inQuotes && c == ',' -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString())
                    field.setLength(0)
                    // skip fully empty trailing line
                    if (row.any { it.isNotEmpty() } || records.isNotEmpty()) {
                        records.add(row)
                    }
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            records.add(row)
        }
        return records
    }
}
