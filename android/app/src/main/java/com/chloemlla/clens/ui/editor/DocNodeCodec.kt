package com.chloemlla.clens.ui.editor

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object DocNodeCodec {
    private val OBJECT_ID_REGEX = Regex("^[0-9a-fA-F]{24}$")

    fun emptyObject(collapsed: Boolean = false): DocNode {
        return DocNode(
            path = emptyList(),
            key = null,
            type = DocValueType.Object,
            children = emptyList(),
            collapsed = collapsed,
        )
    }

    fun parse(json: String, autoExpandDepth: Int = 1): DocNode {
        val trimmed = json.trim().ifBlank { "{}" }
        val token = JSONTokener(trimmed).nextValue()
        return when (token) {
            is JSONObject -> fromJsonObject(token, path = emptyList(), key = null, depth = 0, autoExpandDepth = autoExpandDepth)
            is JSONArray -> fromJsonArray(token, path = emptyList(), key = null, depth = 0, autoExpandDepth = autoExpandDepth)
            JSONObject.NULL, null -> DocNode(type = DocValueType.Null, scalar = "null", collapsed = false)
            is Boolean -> DocNode(type = DocValueType.Boolean, scalar = token.toString(), collapsed = false)
            is Number -> numberNode(token, path = emptyList(), key = null)
            is String -> DocNode(type = DocValueType.String, scalar = token, collapsed = false)
            else -> DocNode(type = DocValueType.Raw, scalar = token.toString(), collapsed = false)
        }
    }

    fun tryParse(json: String, autoExpandDepth: Int = 1): Result<DocNode> {
        return runCatching { parse(json, autoExpandDepth) }
    }

    fun serialize(root: DocNode, pretty: Boolean = true): String {
        val value = toJsonValue(root)
        return if (pretty) {
            when (value) {
                is JSONObject -> value.toString(2)
                is JSONArray -> value.toString(2)
                JSONObject.NULL -> "null"
                is String -> JSONObject.quote(value)
                else -> value.toString()
            }
        } else {
            when (value) {
                is JSONObject, is JSONArray -> value.toString()
                JSONObject.NULL -> "null"
                is String -> JSONObject.quote(value)
                else -> value.toString()
            }
        }
    }

    fun diagnostics(json: String): List<String> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return listOf("JSON 不能为空")
        return runCatching {
            JSONTokener(trimmed).nextValue()
            emptyList()
        }.getOrElse { listOf(it.message ?: "JSON 语法错误") }
    }

    fun flattenVisible(root: DocNode): List<DocTreeRow> {
        val rows = mutableListOf<DocTreeRow>()
        fun walk(node: DocNode, depth: Int) {
            rows += DocTreeRow(
                node = node,
                depth = depth,
                isExpandable = node.isContainer && !node.children.isNullOrEmpty(),
            )
            if (node.isContainer && !node.collapsed) {
                node.children.orEmpty().forEach { child -> walk(child, depth + 1) }
            }
        }
        walk(root, 0)
        return rows
    }

    fun toggleCollapsed(root: DocNode, pathKey: String): DocNode {
        return updateNode(root) { node ->
            if (node.pathKey == pathKey && node.isContainer) {
                node.copy(collapsed = !node.collapsed)
            } else {
                node
            }
        }
    }

    fun updateScalar(root: DocNode, pathKey: String, newType: DocValueType, scalar: String?): DocNode {
        return updateNode(root) { node ->
            if (node.pathKey != pathKey) {
                node
            } else {
                val normalized = when (newType) {
                    DocValueType.Null -> null
                    DocValueType.Boolean -> normalizeBoolean(scalar)
                    DocValueType.Int32, DocValueType.Int64, DocValueType.Double -> scalar?.trim()
                    DocValueType.ObjectId -> scalar?.trim()
                    else -> scalar
                }
                val error = validateScalar(newType, normalized)
                node.copy(
                    type = newType,
                    scalar = if (newType == DocValueType.Null) "null" else normalized,
                    children = if (newType.isContainerType()) node.children ?: emptyList() else null,
                    error = error,
                )
            }
        }
    }

    fun findNode(root: DocNode, pathKey: String): DocNode? {
        if (root.pathKey == pathKey) return root
        root.children.orEmpty().forEach { child ->
            findNode(child, pathKey)?.let { return it }
        }
        return null
    }

    fun isValidObjectId(value: String): Boolean = OBJECT_ID_REGEX.matches(value.trim())

    fun generateObjectIdHex(): String {
        val bytes = ByteArray(12)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    private fun fromJsonObject(
        obj: JSONObject,
        path: List<PathSegment>,
        key: String?,
        depth: Int,
        autoExpandDepth: Int,
    ): DocNode {
        if (obj.length() == 1) {
            when {
                obj.has("\$oid") -> {
                    val oid = obj.optString("\$oid")
                    return DocNode(
                        path = path,
                        key = key,
                        type = DocValueType.ObjectId,
                        scalar = oid,
                        collapsed = false,
                        error = validateScalar(DocValueType.ObjectId, oid),
                    )
                }
                obj.has("\$date") -> {
                    val dateValue = obj.opt("\$date")
                    val display = when (dateValue) {
                        is JSONObject -> dateValue.opt("\$numberLong")?.toString() ?: dateValue.toString()
                        else -> dateValue?.toString().orEmpty()
                    }
                    return DocNode(
                        path = path,
                        key = key,
                        type = DocValueType.Date,
                        scalar = display,
                        collapsed = false,
                    )
                }
            }
        }

        val keys = obj.keys().asSequence().toList()
        val children = keys.map { childKey ->
            val childPath = path + PathSegment.Key(childKey)
            fromAny(obj.get(childKey), childPath, childKey, depth + 1, autoExpandDepth)
        }
        return DocNode(
            path = path,
            key = key,
            type = DocValueType.Object,
            children = children,
            collapsed = depth >= autoExpandDepth,
        )
    }

    private fun fromJsonArray(
        array: JSONArray,
        path: List<PathSegment>,
        key: String?,
        depth: Int,
        autoExpandDepth: Int,
    ): DocNode {
        val children = buildList {
            for (i in 0 until array.length()) {
                val childPath = path + PathSegment.Index(i)
                add(fromAny(array.get(i), childPath, "[$i]", depth + 1, autoExpandDepth))
            }
        }
        return DocNode(
            path = path,
            key = key,
            type = DocValueType.Array,
            children = children,
            collapsed = depth >= autoExpandDepth,
        )
    }

    private fun fromAny(
        value: Any?,
        path: List<PathSegment>,
        key: String?,
        depth: Int,
        autoExpandDepth: Int,
    ): DocNode {
        return when (value) {
            null, JSONObject.NULL -> DocNode(path = path, key = key, type = DocValueType.Null, scalar = "null")
            is JSONObject -> fromJsonObject(value, path, key, depth, autoExpandDepth)
            is JSONArray -> fromJsonArray(value, path, key, depth, autoExpandDepth)
            is Boolean -> DocNode(path = path, key = key, type = DocValueType.Boolean, scalar = value.toString())
            is Number -> numberNode(value, path, key)
            is String -> DocNode(path = path, key = key, type = DocValueType.String, scalar = value)
            else -> DocNode(path = path, key = key, type = DocValueType.Raw, scalar = value.toString())
        }
    }

    private fun numberNode(value: Number, path: List<PathSegment>, key: String?): DocNode {
        val type = when (value) {
            is Int, is Short, is Byte -> DocValueType.Int32
            is Long -> DocValueType.Int64
            else -> {
                val asDouble = value.toDouble()
                if (asDouble % 1.0 == 0.0 && asDouble in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                    DocValueType.Int32
                } else if (asDouble % 1.0 == 0.0 && asDouble in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                    DocValueType.Int64
                } else {
                    DocValueType.Double
                }
            }
        }
        val scalar = when (type) {
            DocValueType.Int32 -> value.toInt().toString()
            DocValueType.Int64 -> value.toLong().toString()
            else -> value.toString()
        }
        return DocNode(path = path, key = key, type = type, scalar = scalar)
    }

    private fun toJsonValue(node: DocNode): Any {
        node.error?.let { error ->
            throw IllegalArgumentException("${node.displayLabel}: $error")
        }
        return when (node.type) {
            DocValueType.Object -> {
                val obj = JSONObject()
                node.children.orEmpty().forEach { child ->
                    val childKey = child.key ?: return@forEach
                    obj.put(childKey, toJsonValue(child))
                }
                obj
            }
            DocValueType.Array -> {
                val array = JSONArray()
                node.children.orEmpty().forEach { child -> array.put(toJsonValue(child)) }
                array
            }
            DocValueType.String -> node.scalar.orEmpty()
            DocValueType.Boolean -> normalizeBoolean(node.scalar).toBoolean()
            DocValueType.Null -> JSONObject.NULL
            DocValueType.Int32 -> node.scalar?.trim()?.toIntOrNull()
                ?: throw IllegalArgumentException("${node.displayLabel}: 不是合法 Int32")
            DocValueType.Int64 -> node.scalar?.trim()?.toLongOrNull()
                ?: throw IllegalArgumentException("${node.displayLabel}: 不是合法 Int64")
            DocValueType.Double -> node.scalar?.trim()?.toDoubleOrNull()
                ?: throw IllegalArgumentException("${node.displayLabel}: 不是合法 Double")
            DocValueType.ObjectId -> {
                val oid = node.scalar?.trim().orEmpty()
                if (!isValidObjectId(oid)) {
                    throw IllegalArgumentException("${node.displayLabel}: ObjectId 必须是 24 位十六进制")
                }
                JSONObject().put("\$oid", oid)
            }
            DocValueType.Date -> JSONObject().put("\$date", node.scalar.orEmpty())
            DocValueType.Binary, DocValueType.GeoPoint, DocValueType.Raw -> {
                val raw = node.scalar.orEmpty()
                runCatching { JSONTokener(raw).nextValue() }.getOrDefault(raw)
            }
        }
    }

    private fun updateNode(node: DocNode, transform: (DocNode) -> DocNode): DocNode {
        val updated = transform(node)
        val children = updated.children?.map { updateNode(it, transform) }
        return if (children == null) updated else updated.copy(children = children)
    }

    private fun validateScalar(type: DocValueType, scalar: String?): String? {
        return when (type) {
            DocValueType.Int32 -> if (scalar?.toIntOrNull() == null) "不是合法 Int32" else null
            DocValueType.Int64 -> if (scalar?.toLongOrNull() == null) "不是合法 Int64" else null
            DocValueType.Double -> if (scalar?.toDoubleOrNull() == null) "不是合法 Double" else null
            DocValueType.Boolean -> if (scalar == null || scalar != "true" && scalar != "false") "Boolean 仅支持 true/false" else null
            DocValueType.ObjectId -> if (scalar.isNullOrBlank() || !isValidObjectId(scalar)) "ObjectId 必须是 24 位十六进制" else null
            DocValueType.Null -> null
            else -> null
        }
    }

    private fun normalizeBoolean(raw: String?): String {
        val value = raw?.trim()?.lowercase().orEmpty()
        return if (value == "true") "true" else "false"
    }

    private fun DocValueType.isContainerType(): Boolean {
        return this == DocValueType.Object || this == DocValueType.Array
    }
}
