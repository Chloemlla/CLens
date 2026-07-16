package com.chloemlla.clens.core.mongo

import org.json.JSONArray
import org.json.JSONObject

enum class VisualFilterOp(val wire: String, val label: String) {
    Eq("\$eq", "等于"),
    Ne("\$ne", "不等于"),
    Gt("\$gt", "大于"),
    Gte("\$gte", "大于等于"),
    Lt("\$lt", "小于"),
    Lte("\$lte", "小于等于"),
    In("\$in", "包含于"),
    Regex("\$regex", "正则"),
    Exists("\$exists", "存在"),
    ;

    companion object {
        fun fromWire(value: String): VisualFilterOp? =
            entries.firstOrNull { it.wire == value || it.name.equals(value, ignoreCase = true) }
    }
}

data class VisualFilterClause(
    val field: String = "",
    val op: VisualFilterOp = VisualFilterOp.Eq,
    val value: String = "",
)

/**
 * Pure builder: list of clauses -> Mongo filter JSON object.
 * Supports \$eq \$ne \$gt \$gte \$lt \$lte \$in \$regex \$exists.
 */
object VisualFilterBuilder {
    fun toFilterJson(clauses: List<VisualFilterClause>, pretty: Boolean = false): String {
        val root = JSONObject()
        clauses
            .map { it.copy(field = it.field.trim()) }
            .filter { it.field.isNotBlank() }
            .forEach { clause ->
                val condition = clauseToCondition(clause) ?: return@forEach
                mergeCondition(root, clause.field, condition)
            }
        return if (pretty) root.toString(2) else root.toString()
    }

    fun fromFilterJson(filterJson: String): List<VisualFilterClause> {
        val text = filterJson.trim().ifBlank { "{}" }
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val clauses = mutableListOf<VisualFilterClause>()
        root.keys().asSequence().forEach { field ->
            if (field.startsWith("\$")) return@forEach
            when (val value = root.opt(field)) {
                is JSONObject -> {
                    val ops = value.keys().asSequence().toList()
                    if (ops.size == 1) {
                        val opKey = ops.first()
                        val op = VisualFilterOp.fromWire(opKey)
                        if (op != null) {
                            clauses += VisualFilterClause(
                                field = field,
                                op = op,
                                value = stringifyValue(value.opt(opKey), op),
                            )
                            return@forEach
                        }
                    }
                    // Multi-op object or unknown: keep as eq of raw object string.
                    clauses += VisualFilterClause(field = field, op = VisualFilterOp.Eq, value = value.toString())
                }
                else -> {
                    clauses += VisualFilterClause(
                        field = field,
                        op = VisualFilterOp.Eq,
                        value = stringifyValue(value, VisualFilterOp.Eq),
                    )
                }
            }
        }
        return clauses
    }

    private fun clauseToCondition(clause: VisualFilterClause): Any? {
        return when (clause.op) {
            VisualFilterOp.Eq -> parseScalar(clause.value)
            VisualFilterOp.Ne,
            VisualFilterOp.Gt,
            VisualFilterOp.Gte,
            VisualFilterOp.Lt,
            VisualFilterOp.Lte,
            -> JSONObject().put(clause.op.wire, parseScalar(clause.value))
            VisualFilterOp.In -> JSONObject().put(clause.op.wire, parseInArray(clause.value))
            VisualFilterOp.Regex -> {
                val pattern = clause.value.trim()
                if (pattern.isEmpty()) return null
                JSONObject().put(VisualFilterOp.Regex.wire, pattern)
            }
            VisualFilterOp.Exists -> {
                val exists = parseExists(clause.value)
                JSONObject().put(VisualFilterOp.Exists.wire, exists)
            }
        }
    }

    private fun mergeCondition(root: JSONObject, field: String, condition: Any) {
        if (!root.has(field)) {
            root.put(field, condition)
            return
        }
        val existing = root.opt(field)
        when {
            existing is JSONObject && condition is JSONObject -> {
                condition.keys().asSequence().forEach { key ->
                    existing.put(key, condition.opt(key))
                }
            }
            else -> root.put(field, condition)
        }
    }

    private fun parseInArray(raw: String): JSONArray {
        val text = raw.trim()
        if (text.isEmpty()) return JSONArray()
        if (text.startsWith("[")) {
            return runCatching { JSONArray(text) }.getOrElse {
                JSONArray().put(text)
            }
        }
        val array = JSONArray()
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            array.put(parseScalar(part))
        }
        return array
    }

    private fun parseExists(raw: String): Boolean {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return true
        return text !in setOf("0", "false", "no", "否", "不存在")
    }

    private fun parseScalar(raw: String): Any {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        when (text.lowercase()) {
            "null" -> return JSONObject.NULL
            "true" -> return true
            "false" -> return false
        }
        text.toLongOrNull()?.let { return it }
        text.toDoubleOrNull()?.let { return it }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            runCatching {
                return if (text.startsWith("{")) JSONObject(text) else JSONArray(text)
            }
        }
        if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
            return text.substring(1, text.length - 1)
        }
        return text
    }

    private fun stringifyValue(value: Any?, op: VisualFilterOp): String {
        return when {
            value == null || value == JSONObject.NULL -> "null"
            op == VisualFilterOp.In && value is JSONArray -> {
                buildList {
                    for (i in 0 until value.length()) {
                        add(stringifyValue(value.opt(i), VisualFilterOp.Eq))
                    }
                }.joinToString(", ")
            }
            value is Boolean || value is Number -> value.toString()
            value is JSONObject || value is JSONArray -> value.toString()
            else -> value.toString()
        }
    }
}
