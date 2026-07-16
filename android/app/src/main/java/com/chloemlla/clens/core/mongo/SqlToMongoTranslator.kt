package com.chloemlla.clens.core.mongo

import org.json.JSONArray
import org.json.JSONObject

data class SqlFindQuery(
    val collection: String?,
    val filterJson: String,
    val projectionJson: String,
    val sortJson: String,
    val limit: Int?,
    val skip: Int?,
    val shellPreview: String,
)

class SqlTranslateException(message: String) : IllegalArgumentException(message)

/**
 * Phone-friendly SQL subset → Mongo find mapping.
 * Pure CPU translator; no I/O.
 * Supports SELECT/FROM/WHERE/ORDER BY/LIMIT/OFFSET with AND/OR/BETWEEN/IN/LIKE/IS NULL,
 * limited parenthesized boolean groups, and SELECT aliases (AS).
 */
object SqlToMongoTranslator {
    private val forbidden = listOf(
        "JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "CROSS JOIN",
        "GROUP BY", "HAVING", "UNION", "INTERSECT", "EXCEPT",
        "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT",
        "CREATE", "DROP", "ALTER", "TRUNCATE", "WITH",
        "INTO", "VALUES",
        "UNION ALL",
    )

    fun translate(sql: String): SqlFindQuery {
        val raw = sql.trim()
        if (raw.isEmpty()) {
            throw SqlTranslateException("请输入 SQL，例如：SELECT * FROM users WHERE age > 18")
        }
        if (raw.contains(';')) {
            throw SqlTranslateException("不支持多语句 SQL，请去掉分号。")
        }
        val normalized = raw.replace(Regex("\\s+"), " ").trim()
        rejectForbidden(normalized)

        // DISTINCT cannot be honored by find(); accept syntax without client-side dedupe.
        val normalizedNoDistinct = normalized.replace(Regex("(?i)^SELECT\\s+DISTINCT\\s+"), "SELECT ")

        var rest = normalizedNoDistinct
        var skip: Int? = null
        var limit: Int? = null
        var orderBy: String? = null
        var where: String? = null

        Regex("""(?i)\sOFFSET\s+(\d+)\s*$""").find(rest)?.let { match ->
            skip = match.groupValues[1].toInt()
            rest = rest.substring(0, match.range.first).trimEnd()
        }
        Regex("""(?i)\sLIMIT\s+(\d+)\s*$""").find(rest)?.let { match ->
            limit = match.groupValues[1].toInt()
            rest = rest.substring(0, match.range.first).trimEnd()
        }
        Regex("""(?i)\sORDER\s+BY\s+(.+)$""").find(rest)?.let { match ->
            orderBy = match.groupValues[1].trim()
            rest = rest.substring(0, match.range.first).trimEnd()
        }
        Regex("""(?i)\sWHERE\s+(.+)$""").find(rest)?.let { match ->
            where = match.groupValues[1].trim()
            rest = rest.substring(0, match.range.first).trimEnd()
        }

        if (!rest.regionMatches(0, "SELECT ", 0, 7, ignoreCase = true)) {
            throw SqlTranslateException("仅支持 SELECT 查询（Find 子集）。")
        }
        val body = rest.substring(7).trim()
        if (body.isEmpty()) {
            throw SqlTranslateException("SELECT 后缺少字段列表。")
        }

        val fromIdx = indexOfKeyword(body, "FROM")
        val selectList: String
        val collection: String?
        if (fromIdx >= 0) {
            selectList = body.substring(0, fromIdx).trim()
            val fromTail = body.substring(fromIdx + 4).trim()
            if (fromTail.isEmpty()) {
                throw SqlTranslateException("FROM 后缺少集合名。")
            }
            if (fromTail.contains(' ')) {
                throw SqlTranslateException("FROM 仅支持单个集合名，不支持别名/多表。")
            }
            collection = parseIdentifier(fromTail, "集合名")
        } else {
            selectList = body
            collection = null
        }

        val projection = parseProjection(selectList)
        val filter = if (where.isNullOrBlank()) JSONObject() else parseWhere(where)
        val sort = if (orderBy.isNullOrBlank()) JSONObject() else parseOrderBy(orderBy)

        val filterJson = filter.toString(2)
        val projectionJson = projection.toString(2)
        val sortJson = sort.toString(2)
        val shell = buildShellPreview(
            collection = collection ?: "<collection>",
            filter = filter,
            projection = projection,
            sort = sort,
            limit = limit,
            skip = skip,
        )
        return SqlFindQuery(
            collection = collection,
            filterJson = filterJson,
            projectionJson = projectionJson,
            sortJson = sortJson,
            limit = limit,
            skip = skip,
            shellPreview = shell,
        )
    }

    private fun rejectForbidden(sql: String) {
        // Mask supported constructs that contain otherwise-forbidden tokens.
        val masked = sql
            .replace(Regex("(?i)IS\\s+NOT\\s+NULL"), " IS_NOT_NULL ")
            .replace(Regex("(?i)IS\\s+NULL"), " IS_NULL ")
            .replace(Regex("(?i)\\bIN\\s*\\((?:[^'\"]|'[^']*'|\"[^\"]*\")*\\)"), " IN_LIST ")
        val haystack = " $masked "
        for (word in forbidden) {
            val pattern = Regex("""(?i)(?<![A-Za-z0-9_`"])${Regex.escape(word)}(?![A-Za-z0-9_`"])""")
            if (pattern.containsMatchIn(haystack)) {
                throw SqlTranslateException("暂不支持 SQL 关键字/结构：$word")
            }
        }
        if (Regex("(?i)\\bCOUNT\\s*\\(").containsMatchIn(sql) ||
            Regex("(?i)\\bSUM\\s*\\(").containsMatchIn(sql) ||
            Regex("(?i)\\bAVG\\s*\\(").containsMatchIn(sql) ||
            Regex("(?i)\\bMIN\\s*\\(").containsMatchIn(sql) ||
            Regex("(?i)\\bMAX\\s*\\(").containsMatchIn(sql)
        ) {
            throw SqlTranslateException("聚合函数请改用 Aggregate JSON（find 子集不支持 COUNT/SUM/AVG/...）。")
        }
    }

    private fun parseProjection(selectList: String): JSONObject {
        val text = selectList.trim()
        if (text == "*" || text.equals("ALL", ignoreCase = true)) {
            return JSONObject()
        }
        val fields = splitTopLevel(text, ',')
        if (fields.isEmpty()) {
            throw SqlTranslateException("SELECT 字段列表为空。")
        }
        val projection = JSONObject()
        fields.forEach { raw ->
            val part = raw.trim()
            if (part.isEmpty()) {
                throw SqlTranslateException("SELECT 字段列表格式无效。")
            }
            val field = parseSelectItem(part)
            projection.put(field, 1)
        }
        return projection
    }

    /**
     * Accepts `field`, `field AS alias`, or `field alias`.
     * Mongo find projection uses the source field path (aliases are not renamed).
     */
    private fun parseSelectItem(part: String): String {
        val asMatch = Regex("(?i)^(.+?)\\s+AS\\s+(.+)$").matchEntire(part)
        if (asMatch != null) {
            val source = asMatch.groupValues[1].trim()
            parseIdentifier(asMatch.groupValues[2].trim(), "别名")
            return parseIdentifier(source, "字段")
        }
        val spaceAlias = Regex("^(.+?)\\s+([A-Za-z_][A-Za-z0-9_]*)$").matchEntire(part)
        if (spaceAlias != null && !part.contains('(')) {
            val source = spaceAlias.groupValues[1].trim()
            if (!source.contains(' ') && (source.startsWith("`") || source.startsWith("\"") || source.startsWith("_") || source[0].isLetter())) {
                parseIdentifier(spaceAlias.groupValues[2].trim(), "别名")
                return parseIdentifier(source, "字段")
            }
        }
        return parseIdentifier(part, "字段")
    }

    private fun parseWhere(where: String): JSONObject {
        if (containsUnaryNotOutsideStrings(where) || containsKeywordOutsideStrings(where, "EXISTS")) {
            throw SqlTranslateException("WHERE 不支持 NOT/EXISTS；请改用 JSON 模式。")
        }
        return parseBooleanExpr(where.trim())
    }

    /**
     * Boolean expression parser with precedence: parentheses > AND > OR.
     */
    private fun parseBooleanExpr(expr: String): JSONObject {
        val orParts = splitKeywordTopLevel(expr, "OR")
        if (orParts.size > 1) {
            val arr = JSONArray()
            orParts.forEach { part -> arr.put(parseBooleanExpr(part)) }
            return JSONObject().put("\$or", arr)
        }
        val andParts = splitKeywordTopLevel(expr, "AND")
        if (andParts.size > 1) {
            val root = JSONObject()
            var canFlatten = true
            for (part in andParts) {
                val obj = parseBooleanExpr(part)
                if (canFlatten && canFlattenInto(root, obj)) {
                    mergeObject(root, obj)
                } else {
                    canFlatten = false
                    break
                }
            }
            if (canFlatten) return root
            val full = JSONArray()
            andParts.forEach { full.put(parseBooleanExpr(it)) }
            return JSONObject().put("\$and", full)
        }
        val trimmed = expr.trim()
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && isWrappingParens(trimmed)) {
            return parseBooleanExpr(trimmed.substring(1, trimmed.length - 1).trim())
        }
        val root = JSONObject()
        parsePredicate(trimmed, root)
        return root
    }

    private fun canFlattenInto(root: JSONObject, obj: JSONObject): Boolean {
        if (obj.has("\$or") || obj.has("\$and")) return false
        obj.keys().asSequence().forEach { key ->
            if (root.has(key)) return false
        }
        return true
    }

    private fun mergeObject(target: JSONObject, source: JSONObject) {
        source.keys().asSequence().forEach { key ->
            target.put(key, source.get(key))
        }
    }

    private fun isWrappingParens(text: String): Boolean {
        if (!text.startsWith("(") || !text.endsWith(")")) return false
        var depth = 0
        var inSingle = false
        var inDouble = false
        for (i in text.indices) {
            val c = text[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble && c == '(' -> depth++
                !inSingle && !inDouble && c == ')' -> {
                    depth--
                    if (depth == 0 && i < text.lastIndex) return false
                }
            }
        }
        return depth == 0
    }

    private fun splitKeywordTopLevel(text: String, keyword: String): List<String> {
        val parts = mutableListOf<String>()
        val pattern = Regex("""(?i)(?<![A-Za-z0-9_`"])${Regex.escape(keyword)}(?![A-Za-z0-9_`"])""")
        var start = 0
        var inSingle = false
        var inDouble = false
        var depth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble && c == '(' -> depth++
                !inSingle && !inDouble && c == ')' -> depth--
                !inSingle && !inDouble && depth == 0 -> {
                    val match = pattern.find(text, i)
                    if (match != null && match.range.first == i) {
                        // Do not split the AND that belongs to BETWEEN x AND y.
                        if (keyword.equals("AND", ignoreCase = true) && isBetweenAndConnector(text, i)) {
                            i = match.range.last + 1
                            continue
                        }
                        parts += text.substring(start, i)
                        i = match.range.last + 1
                        start = i
                        continue
                    }
                }
            }
            i++
        }
        parts += text.substring(start)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parsePredicate(expr: String, root: JSONObject) {
        if (expr.isEmpty()) {
            throw SqlTranslateException("WHERE 存在空条件。")
        }
        val between = Regex("""(?i)^(.+?)\s+BETWEEN\s+(.+?)\s+AND\s+(.+)$""").matchEntire(expr)
        if (between != null) {
            val field = parseIdentifier(between.groupValues[1].trim(), "字段")
            val low = parseScalar(between.groupValues[2].trim())
            val high = parseScalar(between.groupValues[3].trim())
            mergeCondition(root, field, JSONObject().put("\$gte", low).put("\$lte", high))
            return
        }
        val isNull = Regex("""(?i)^(.+?)\s+IS\s+NULL$""").matchEntire(expr)
        if (isNull != null) {
            val field = parseIdentifier(isNull.groupValues[1].trim(), "字段")
            mergeCondition(root, field, JSONObject.NULL)
            return
        }
        val isNotNull = Regex("""(?i)^(.+?)\s+IS\s+NOT\s+NULL$""").matchEntire(expr)
        if (isNotNull != null) {
            val field = parseIdentifier(isNotNull.groupValues[1].trim(), "字段")
            mergeCondition(root, field, JSONObject().put("\$ne", JSONObject.NULL))
            return
        }
        val like = Regex("""(?i)^(.+?)\s+LIKE\s+(.+)$""").matchEntire(expr)
        if (like != null) {
            val field = parseIdentifier(like.groupValues[1].trim(), "字段")
            val pattern = parseScalar(like.groupValues[2].trim())
            if (pattern !is String) {
                throw SqlTranslateException("LIKE 右侧必须是字符串。")
            }
            mergeCondition(root, field, JSONObject().put("\$regex", sqlLikeToRegex(pattern)))
            return
        }
        val inMatch = Regex("""(?i)^(.+?)\s+IN\s*\((.*)\)$""").matchEntire(expr)
        if (inMatch != null) {
            val field = parseIdentifier(inMatch.groupValues[1].trim(), "字段")
            val items = splitTopLevel(inMatch.groupValues[2], ',')
            if (items.isEmpty()) {
                throw SqlTranslateException("IN 列表不能为空。")
            }
            val array = JSONArray()
            items.forEach { array.put(parseScalar(it.trim())) }
            mergeCondition(root, field, JSONObject().put("\$in", array))
            return
        }

        val cmp = Regex("""^(.+?)\s*(<>|!=|>=|<=|=|>|<)\s*(.+)$""").matchEntire(expr)
            ?: throw SqlTranslateException("无法解析条件：$expr")
        val field = parseIdentifier(cmp.groupValues[1].trim(), "字段")
        val op = cmp.groupValues[2]
        val value = parseScalar(cmp.groupValues[3].trim())
        val condition: Any = when (op) {
            "=" -> value
            "!=", "<>" -> JSONObject().put("\$ne", value)
            ">" -> JSONObject().put("\$gt", value)
            ">=" -> JSONObject().put("\$gte", value)
            "<" -> JSONObject().put("\$lt", value)
            "<=" -> JSONObject().put("\$lte", value)
            else -> throw SqlTranslateException("不支持的比较符：$op")
        }
        mergeCondition(root, field, condition)
    }

    private fun parseOrderBy(orderBy: String): JSONObject {
        val parts = splitTopLevel(orderBy, ',')
        if (parts.isEmpty()) {
            throw SqlTranslateException("ORDER BY 不能为空。")
        }
        val sort = JSONObject()
        parts.forEach { raw ->
            val tokens = raw.trim().split(Regex("\\s+"))
            if (tokens.isEmpty() || tokens[0].isBlank()) {
                throw SqlTranslateException("ORDER BY 字段无效。")
            }
            val field = parseIdentifier(tokens[0], "排序字段")
            val dir = when (tokens.getOrNull(1)?.uppercase()) {
                null, "ASC" -> 1
                "DESC" -> -1
                else -> throw SqlTranslateException("ORDER BY 方向仅支持 ASC/DESC。")
            }
            if (tokens.size > 2) {
                throw SqlTranslateException("ORDER BY 子句格式无效：${raw.trim()}")
            }
            sort.put(field, dir)
        }
        return sort
    }

    private fun parseIdentifier(raw: String, label: String): String {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw SqlTranslateException("$label 不能为空。")
        }
        if ((text.startsWith('`') && text.endsWith('`') && text.length >= 2) ||
            (text.startsWith('"') && text.endsWith('"') && text.length >= 2)
        ) {
            val inner = text.substring(1, text.length - 1).trim()
            if (inner.isEmpty()) {
                throw SqlTranslateException("$label 不能为空。")
            }
            if (inner.contains('.')) {
                // dotted paths allowed for fields; collection names should be simple
                return inner
            }
            return inner
        }
        if (!Regex("""^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*$""").matches(text)) {
            throw SqlTranslateException("无效的$label：$text")
        }
        return text
    }

    private fun parseScalar(raw: String): Any {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw SqlTranslateException("缺少字面量值。")
        }
        when (text.lowercase()) {
            "null" -> return JSONObject.NULL
            "true" -> return true
            "false" -> return false
        }
        if ((text.startsWith('\'') && text.endsWith('\'') && text.length >= 2) ||
            (text.startsWith('"') && text.endsWith('"') && text.length >= 2)
        ) {
            return unescapeSqlString(text.substring(1, text.length - 1))
        }
        text.toLongOrNull()?.let { return it }
        text.toDoubleOrNull()?.let { return it }
        // bare identifier treated as string for convenience on mobile typing
        if (Regex("""^[A-Za-z_][A-Za-z0-9_]*$""").matches(text)) {
            return text
        }
        throw SqlTranslateException("无法解析值：$text")
    }

    private fun unescapeSqlString(value: String): String {
        return value.replace("''", "'")
    }

    private fun sqlLikeToRegex(like: String): String {
        val sb = StringBuilder("^")
        like.forEach { ch ->
            when (ch) {
                '%' -> sb.append(".*")
                '_' -> sb.append('.')
                '.', '^', '$', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|', '\\' -> {
                    sb.append('\\').append(ch)
                }
                else -> sb.append(ch)
            }
        }
        sb.append('$')
        return sb.toString()
    }

    private fun mergeCondition(root: JSONObject, field: String, condition: Any) {
        if (!root.has(field)) {
            root.put(field, condition)
            return
        }
        val existing = root.opt(field)
        if (existing is JSONObject && condition is JSONObject) {
            condition.keys().asSequence().forEach { key ->
                existing.put(key, condition.opt(key))
            }
            return
        }
        throw SqlTranslateException("字段 `$field` 条件冲突，请拆成多次查询或使用 JSON 模式。")
    }

    private fun buildShellPreview(
        collection: String,
        filter: JSONObject,
        projection: JSONObject,
        sort: JSONObject,
        limit: Int?,
        skip: Int?,
    ): String {
        val sb = StringBuilder()
        sb.append("db.").append(collection).append(".find(")
        sb.append(filter.toString())
        if (projection.length() > 0) {
            sb.append(", ").append(projection.toString())
        }
        sb.append(')')
        if (sort.length() > 0) {
            sb.append(".sort(").append(sort.toString()).append(')')
        }
        if (skip != null) {
            sb.append(".skip(").append(skip).append(')')
        }
        if (limit != null) {
            sb.append(".limit(").append(limit).append(')')
        }
        return sb.toString()
    }

    private fun indexOfKeyword(text: String, keyword: String): Int {
        val pattern = Regex("""(?i)(?<![A-Za-z0-9_`"])${Regex.escape(keyword)}(?![A-Za-z0-9_`"])""")
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble -> {
                    val match = pattern.find(text, i)
                    if (match != null && match.range.first == i) {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun containsUnaryNotOutsideStrings(text: String): Boolean {
        // Reject bare NOT / NOT (...), but allow "IS NOT NULL".
        val pattern = Regex("""(?i)(?<![A-Za-z0-9_`"])NOT(?![A-Za-z0-9_`"])""")
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble -> {
                    val match = pattern.find(text, i)
                    if (match != null && match.range.first == i) {
                        val before = text.substring(0, i).trimEnd()
                        val isNotNull = before.length >= 2 && before.substring(before.length - 2).equals("IS", ignoreCase = true) &&
                            (before.length == 2 || !before[before.length - 3].isLetterOrDigit())
                        if (!isNotNull) return true
                        i = match.range.last + 1
                        continue
                    }
                }
            }
            i++
        }
        return false
    }

    private fun containsKeywordOutsideStrings(text: String, keyword: String): Boolean {
        val pattern = Regex("""(?i)(?<![A-Za-z0-9_`"])${Regex.escape(keyword)}(?![A-Za-z0-9_`"])""")
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble -> {
                    val match = pattern.find(text, i)
                    if (match != null && match.range.first == i) return true
                }
            }
            i++
        }
        return false
    }

    private fun splitKeyword(text: String, keyword: String): List<String> {
        val parts = mutableListOf<String>()
        val pattern = Regex("""(?i)(?<![A-Za-z0-9_`"])${Regex.escape(keyword)}(?![A-Za-z0-9_`"])""")
        var start = 0
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble -> {
                    val match = pattern.find(text, i)
                    if (match != null && match.range.first == i) {
                        parts += text.substring(start, i)
                        i = match.range.last + 1
                        start = i
                        continue
                    }
                }
            }
            i++
        }
        parts += text.substring(start)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }


    /**
     * True when [andIndex] points at the AND keyword of a BETWEEN ... AND ... pair
     * at top-level depth (no intervening top-level AND/OR after the latest BETWEEN).
     */
    private fun isBetweenAndConnector(text: String, andIndex: Int): Boolean {
        var inSingle = false
        var inDouble = false
        var depth = 0
        var i = 0
        var lastBetweenAtDepth0 = -1
        while (i < andIndex) {
            val c = text[i]
            when {
                c == ''' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                !inSingle && !inDouble && c == '(' -> depth++
                !inSingle && !inDouble && c == ')' -> depth--
                !inSingle && !inDouble && depth == 0 -> {
                    if (text.regionMatches(i, "BETWEEN", 0, 7, ignoreCase = true)) {
                        val beforeOk = i == 0 || !text[i - 1].isLetterOrDigit()
                        val afterOk = i + 7 >= text.length || !text[i + 7].isLetterOrDigit()
                        if (beforeOk && afterOk) {
                            lastBetweenAtDepth0 = i
                            i += 7
                            continue
                        }
                    }
                    if (text.regionMatches(i, "AND", 0, 3, ignoreCase = true)) {
                        val beforeOk = i == 0 || !text[i - 1].isLetterOrDigit()
                        val afterOk = i + 3 >= text.length || !text[i + 3].isLetterOrDigit()
                        if (beforeOk && afterOk) {
                            lastBetweenAtDepth0 = -1
                            i += 3
                            continue
                        }
                    }
                    if (text.regionMatches(i, "OR", 0, 2, ignoreCase = true)) {
                        val beforeOk = i == 0 || !text[i - 1].isLetterOrDigit()
                        val afterOk = i + 2 >= text.length || !text[i + 2].isLetterOrDigit()
                        if (beforeOk && afterOk) {
                            lastBetweenAtDepth0 = -1
                            i += 2
                            continue
                        }
                    }
                }
            }
            i++
        }
        return lastBetweenAtDepth0 >= 0
    }

    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var depth = 0
        for (c in text) {
            when {
                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    sb.append(c)
                }
                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    sb.append(c)
                }
                !inSingle && !inDouble && c == '(' -> {
                    depth++
                    sb.append(c)
                }
                !inSingle && !inDouble && c == ')' -> {
                    depth--
                    sb.append(c)
                }
                !inSingle && !inDouble && depth == 0 && c == separator -> {
                    parts += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            parts += sb.toString()
        }
        return parts
    }
}
