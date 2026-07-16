package com.chloemlla.clens.ui.editor

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object DocNodeCodec {
    private val OBJECT_ID_REGEX = Regex("^[0-9a-fA-F]{24}$")

    data class BinaryValue(val base64: String, val subType: String)

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
        return JsonCodeAssist.diagnosticMessages(json)
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
                    DocValueType.Date -> scalar?.trim()
                    DocValueType.GeoPoint -> normalizeGeoPointScalar(scalar)
                    DocValueType.Binary -> normalizeBinaryScalar(scalar)
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

    fun nowIsoDate(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        formatter.timeZone = java.util.TimeZone.getDefault()
        return formatter.format(java.util.Date())
    }

    fun parseDateMillis(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { return it }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                if (pattern.contains("'Z'")) {
                    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                formatter.parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    fun formatIsoDate(millis: Long): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        formatter.timeZone = java.util.TimeZone.getDefault()
        return formatter.format(java.util.Date(millis))
    }

    fun displayValue(node: DocNode): String {
        return when {
            node.isContainer -> {
                val count = node.children?.size ?: 0
                "${node.type.name.lowercase()} · $count 项"
            }
            node.type == DocValueType.Null -> "null"
            node.type == DocValueType.ObjectId -> node.scalar.orEmpty()
            node.type == DocValueType.Date -> node.scalar.orEmpty()
            node.type == DocValueType.GeoPoint -> {
                val point = parseGeoPoint(node.scalar)
                if (point != null) "lat=${point.first}, lng=${point.second}" else node.scalar.orEmpty()
            }
            node.type == DocValueType.Binary -> {
                val binary = parseBinary(node.scalar)
                if (binary != null) {
                    val preview = binary.base64.take(18)
                    val suffix = if (binary.base64.length > 18) "…" else ""
                    "bin/${binary.subType} · $preview$suffix"
                } else {
                    node.scalar.orEmpty()
                }
            }
            else -> node.scalar.orEmpty().ifBlank { "\"\"" }
        }
    }

    fun convertType(root: DocNode, pathKey: String, newType: DocValueType): DocNode {
        val current = findNode(root, pathKey) ?: return root
        if (current.type == newType) return root
        val defaultScalar = defaultScalarFor(newType, current)
        return updateScalar(root, pathKey, newType, defaultScalar)
    }

    fun ensureRootObjectId(root: DocNode): DocNode {
        if (root.type != DocValueType.Object) return root
        val hasId = root.children.orEmpty().any { it.key == "_id" }
        if (hasId) return root
        val idNode = DocNode(
            path = listOf(PathSegment.Key("_id")),
            key = "_id",
            type = DocValueType.ObjectId,
            scalar = generateObjectIdHex(),
            collapsed = false,
        )
        return repath(root.copy(children = listOf(idNode) + root.children.orEmpty()))
    }

    fun deleteNode(root: DocNode, pathKey: String): DocNode {
        if (pathKey.isBlank()) return root
        val target = findNode(root, pathKey) ?: return root
        val parentPath = parentPathKey(target.path)
        if (parentPath == null) return root
        val parent = if (parentPath.isEmpty()) root else findNode(root, parentPath) ?: return root
        val nextChildren = when (parent.type) {
            DocValueType.Object -> parent.children.orEmpty().filterNot { it.pathKey == pathKey }
            DocValueType.Array -> parent.children.orEmpty().filterNot { it.pathKey == pathKey }
            else -> return root
        }
        val updatedParent = parent.copy(children = nextChildren)
        val withParent = if (parentPath.isEmpty()) updatedParent else replaceNode(root, parentPath, updatedParent)
        return repath(withParent)
    }

    fun cloneNode(root: DocNode, pathKey: String): DocNode {
        if (pathKey.isBlank()) return root
        val target = findNode(root, pathKey) ?: return root
        val parentPath = parentPathKey(target.path) ?: return root
        val parent = if (parentPath.isEmpty()) root else findNode(root, parentPath) ?: return root
        val children = parent.children.orEmpty()
        val cloned = deepCopyNode(target)
        val nextChildren = when (parent.type) {
            DocValueType.Object -> {
                val baseKey = (target.key ?: "field") + "_copy"
                val uniqueKey = uniqueObjectKey(children, baseKey)
                children + cloned.copy(key = uniqueKey)
            }
            DocValueType.Array -> children + cloned.copy(key = null)
            else -> return root
        }
        val updatedParent = parent.copy(children = nextChildren, collapsed = false)
        val withParent = if (parentPath.isEmpty()) updatedParent else replaceNode(root, parentPath, updatedParent)
        return repath(withParent)
    }

    fun encodeGeoPoint(lat: Double, lng: Double): String {
        return JSONObject()
            .put("type", "Point")
            .put("coordinates", JSONArray().put(lng).put(lat))
            .toString()
    }

    fun parseGeoPoint(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            if (!isGeoJsonPoint(obj)) return@runCatching null
            val coords = obj.getJSONArray("coordinates")
            val lng = coords.getDouble(0)
            val lat = coords.getDouble(1)
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@runCatching null
            lat to lng
        }.getOrNull()
    }

    fun normalizeGeoPointScalar(raw: String?): String? {
        val point = parseGeoPoint(raw) ?: return null
        return encodeGeoPoint(point.first, point.second)
    }

    fun encodeBinary(base64: String, subType: String = "00"): String {
        val normalizedSubType = normalizeSubType(subType)
        return JSONObject()
            .put("\$binary", JSONObject().put("base64", base64).put("subType", normalizedSubType))
            .toString()
    }

    fun parseBinary(raw: String?): BinaryValue? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            when (val binary = obj.opt("\$binary")) {
                is JSONObject -> {
                    val base64 = binary.optString("base64")
                    val subType = normalizeSubType(binary.optString("subType", "00"))
                    if (base64.isNotBlank() && !isValidBase64(base64)) return@runCatching null
                    BinaryValue(base64 = base64, subType = subType)
                }
                is String -> {
                    val subType = normalizeSubType(obj.optString("\$type", obj.optString("subType", "00")))
                    if (binary.isNotBlank() && !isValidBase64(binary)) return@runCatching null
                    BinaryValue(base64 = binary, subType = subType)
                }
                else -> null
            }
        }.getOrNull()
    }

    fun normalizeBinaryScalar(raw: String?): String? {
        val binary = parseBinary(raw) ?: return null
        return encodeBinary(binary.base64, binary.subType)
    }

    private fun defaultScalarFor(type: DocValueType, current: DocNode): String? {
        return when (type) {
            DocValueType.Null -> null
            DocValueType.Boolean -> if (current.scalar.equals("true", true)) "true" else "false"
            DocValueType.Int32 -> current.scalar?.toIntOrNull()?.toString() ?: "0"
            DocValueType.Int64 -> current.scalar?.toLongOrNull()?.toString() ?: "0"
            DocValueType.Double -> current.scalar?.toDoubleOrNull()?.toString() ?: "0.0"
            DocValueType.ObjectId -> {
                val raw = current.scalar?.trim().orEmpty()
                if (isValidObjectId(raw)) raw else generateObjectIdHex()
            }
            DocValueType.Date -> {
                parseDateMillis(current.scalar)?.let { formatIsoDate(it) } ?: nowIsoDate()
            }
            DocValueType.GeoPoint -> normalizeGeoPointScalar(current.scalar) ?: encodeGeoPoint(0.0, 0.0)
            DocValueType.Binary -> normalizeBinaryScalar(current.scalar) ?: encodeBinary("", "00")
            DocValueType.String -> current.scalar.orEmpty()
            DocValueType.Object, DocValueType.Array -> null
            else -> current.scalar
        }
    }

    private fun parentPathKey(path: List<PathSegment>): String? {
        if (path.isEmpty()) return null
        if (path.size == 1) return ""
        return path.dropLast(1).joinToString(".") { segment ->
            when (segment) {
                is PathSegment.Key -> segment.name
                is PathSegment.Index -> segment.index.toString()
            }
        }
    }

    private fun replaceNode(root: DocNode, pathKey: String, replacement: DocNode): DocNode {
        if (root.pathKey == pathKey) return replacement
        val children = root.children ?: return root
        return root.copy(
            children = children.map { child ->
                if (child.pathKey == pathKey || pathKey.startsWith(child.pathKey + ".")) {
                    replaceNode(child, pathKey, replacement)
                } else {
                    child
                }
            },
        )
    }

    private fun deepCopyNode(node: DocNode): DocNode {
        return node.copy(
            children = node.children?.map { deepCopyNode(it) },
            error = null,
        )
    }

    private fun uniqueObjectKey(children: List<DocNode>, base: String): String {
        if (children.none { it.key == base }) return base
        var index = 2
        while (children.any { it.key == base + index }) {
            index += 1
        }
        return base + index
    }

    private fun repath(node: DocNode, path: List<PathSegment> = emptyList(), key: String? = node.key): DocNode {
        val children = when (node.type) {
            DocValueType.Object -> node.children.orEmpty().map { child ->
                val childKey = child.key ?: "field"
                repath(child, path + PathSegment.Key(childKey), childKey)
            }
            DocValueType.Array -> node.children.orEmpty().mapIndexed { index, child ->
                repath(child, path + PathSegment.Index(index), "[$index]")
            }
            else -> null
        }
        return node.copy(path = path, key = key, children = children)
    }

    private fun fromJsonObject(
        obj: JSONObject,
        path: List<PathSegment>,
        key: String?,
        depth: Int,
        autoExpandDepth: Int,
    ): DocNode {
        if (obj.has("\$oid") && obj.length() == 1) {
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
        if (obj.has("\$date") && obj.length() == 1) {
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
        if (obj.has("\$binary")) {
            val encoded = encodeBinaryFromJson(obj)
            return DocNode(
                path = path,
                key = key,
                type = DocValueType.Binary,
                scalar = encoded,
                collapsed = false,
                error = validateScalar(DocValueType.Binary, encoded),
            )
        }
        if (isGeoJsonPoint(obj)) {
            val coords = obj.getJSONArray("coordinates")
            val lng = coords.optDouble(0)
            val lat = coords.optDouble(1)
            val encoded = encodeGeoPoint(lat, lng)
            return DocNode(
                path = path,
                key = key,
                type = DocValueType.GeoPoint,
                scalar = encoded,
                collapsed = false,
                error = validateScalar(DocValueType.GeoPoint, encoded),
            )
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
            DocValueType.GeoPoint -> {
                val point = parseGeoPoint(node.scalar)
                    ?: throw IllegalArgumentException("${node.displayLabel}: GeoPoint 需要合法 lat/lng")
                JSONObject()
                    .put("type", "Point")
                    .put("coordinates", JSONArray().put(point.second).put(point.first))
            }
            DocValueType.Binary -> {
                val binary = parseBinary(node.scalar)
                    ?: throw IllegalArgumentException("${node.displayLabel}: Binary 需要 base64 + subType")
                JSONObject().put(
                    "\$binary",
                    JSONObject()
                        .put("base64", binary.base64)
                        .put("subType", binary.subType),
                )
            }
            DocValueType.Raw -> {
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
            DocValueType.Date -> if (scalar.isNullOrBlank() || parseDateMillis(scalar) == null) "Date 需为 ISO-8601 或毫秒时间戳" else null
            DocValueType.GeoPoint -> if (parseGeoPoint(scalar) == null) "GeoPoint 需要合法 lat/lng" else null
            DocValueType.Binary -> if (parseBinary(scalar) == null) "Binary 需要 base64 与 subType" else null
            DocValueType.Null -> null
            else -> null
        }
    }

    private fun encodeBinaryFromJson(obj: JSONObject): String {
        val parsed = parseBinary(obj.toString())
        return parsed?.let { encodeBinary(it.base64, it.subType) } ?: obj.toString()
    }

    private fun isGeoJsonPoint(obj: JSONObject): Boolean {
        if (!obj.optString("type").equals("Point", ignoreCase = true)) return false
        val coords = obj.optJSONArray("coordinates") ?: return false
        return coords.length() >= 2
    }

    private fun normalizeSubType(raw: String): String {
        val value = raw.trim().ifBlank { "00" }
        val hex = value.removePrefix("0x")
        return if (hex.length == 1) "0" + hex.lowercase() else hex.lowercase().take(2)
    }

    private fun isValidBase64(value: String): Boolean {
        if (value.isEmpty()) return true
        val normalized = value.replace("\\s".toRegex(), "")
        if (normalized.length % 4 != 0) return false
        return normalized.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))
    }

    private fun normalizeBoolean(raw: String?): String {
        val value = raw?.trim()?.lowercase().orEmpty()
        return if (value == "true") "true" else "false"
    }

    private fun DocValueType.isContainerType(): Boolean {
        return this == DocValueType.Object || this == DocValueType.Array
    }
}
