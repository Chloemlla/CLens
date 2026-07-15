package com.chloemlla.clens.core.mongo

import com.mongodb.MongoNamespace
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.RenameCollectionOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings

class MongoAdminRepository(
    private val sessionManager: MongoSessionManager,
) {
    private val prettyJsonSettings: JsonWriterSettings = JsonWriterSettings.builder()
        .outputMode(JsonMode.RELAXED)
        .indent(true)
        .build()

    suspend fun listDatabases(): List<DatabaseSummary> = withContext(Dispatchers.IO) {
        sessionManager.requireClient().listDatabases().toList().map { doc ->
            DatabaseSummary(
                name = doc.stringValue("name"),
                sizeOnDisk = doc.numberLong("sizeOnDisk"),
                empty = doc.booleanValue("empty"),
                collections = null,
            )
        }.filter { it.name.isNotBlank() }.sortedBy { it.name }
    }

    suspend fun databaseStats(database: String): String = withContext(Dispatchers.IO) {
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        pretty(db.runCommand(Document("dbStats", 1)))
    }

    suspend fun createDatabase(database: String, firstCollection: String = "_init"): Unit =
        withContext(Dispatchers.IO) {
            val dbName = requireName(database, "数据库")
            val collectionName = requireName(firstCollection, "集合")
            sessionManager.requireClient().getDatabase(dbName).createCollection(collectionName)
        }

    suspend fun dropDatabase(database: String): Unit = withContext(Dispatchers.IO) {
        sessionManager.requireClient().getDatabase(requireName(database, "数据库")).drop()
    }

    suspend fun listCollections(database: String): List<CollectionSummary> = withContext(Dispatchers.IO) {
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        db.listCollections().toList().map { doc ->
            CollectionSummary(
                name = doc.stringValue("name"),
                type = doc.stringValue("type", "collection"),
            )
        }.filter { it.name.isNotBlank() }.sortedBy { it.name }
    }

    suspend fun collectionStats(database: String, collection: String): CollectionSummary =
        withContext(Dispatchers.IO) {
            val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
            val stats = db.runCommand(Document("collStats", requireName(collection, "集合")))
            CollectionSummary(
                name = collection,
                type = "collection",
                count = stats.numberLong("count"),
                size = stats.numberLong("size"),
                storageSize = stats.numberLong("storageSize"),
                totalIndexSize = stats.numberLong("totalIndexSize"),
                avgObjSize = stats.numberDouble("avgObjSize"),
                nindexes = stats.numberInt("nindexes"),
            )
        }

    suspend fun createCollection(database: String, collection: String): Unit = withContext(Dispatchers.IO) {
        sessionManager.requireClient()
            .getDatabase(requireName(database, "数据库"))
            .createCollection(requireName(collection, "集合"))
    }

    suspend fun renameCollection(
        database: String,
        from: String,
        to: String,
        dropTarget: Boolean = false,
    ): Unit = withContext(Dispatchers.IO) {
        val dbName = requireName(database, "数据库")
        sessionManager.requireClient()
            .getDatabase(dbName)
            .getCollection<Document>(requireName(from, "源集合"))
            .renameCollection(
                MongoNamespace(dbName, requireName(to, "目标集合")),
                RenameCollectionOptions().dropTarget(dropTarget),
            )
    }

    suspend fun dropCollection(database: String, collection: String): Unit = withContext(Dispatchers.IO) {
        sessionManager.requireClient()
            .getDatabase(requireName(database, "数据库"))
            .getCollection<Document>(requireName(collection, "集合"))
            .drop()
    }

    suspend fun findDocuments(
        database: String,
        collection: String,
        filterJson: String = "{}",
        sortJson: String = "{}",
        projectionJson: String = "{}",
        limit: Int = 50,
        skip: Int = 0,
    ): DocumentPage = withContext(Dispatchers.IO) {
        val coll = collection(database, collection)
        val filter = parseDocument(filterJson, "filter")
        val sort = parseDocument(sortJson, "sort")
        val projection = parseDocument(projectionJson, "projection")
        val safeLimit = limit.coerceIn(1, 500)
        val safeSkip = skip.coerceAtLeast(0)

        var find = coll.find(filter).skip(safeSkip).limit(safeLimit)
        if (sort.isNotEmpty()) {
            find = find.sort(sort)
        }
        if (projection.isNotEmpty()) {
            find = find.projection(projection)
        }

        val docs = find.toList().map { pretty(it) }
        val countHint = runCatching {
            if (filter.isEmpty()) coll.estimatedDocumentCount() else coll.countDocuments(filter)
        }.getOrNull()
        DocumentPage(documents = docs, countHint = countHint, limit = safeLimit, skip = safeSkip)
    }

    suspend fun insertDocuments(
        database: String,
        collection: String,
        json: String,
    ): Int = withContext(Dispatchers.IO) {
        val coll = collection(database, collection)
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            val array = Document.parse("{\"items\":$trimmed}").getList("items", Document::class.java).orEmpty()
            if (array.isEmpty()) {
                throw MongoAdminException.Validation("插入数组不能为空。")
            }
            coll.insertMany(array)
            array.size
        } else {
            coll.insertOne(parseDocument(trimmed, "document"))
            1
        }
    }

    suspend fun replaceDocument(
        database: String,
        collection: String,
        filterJson: String,
        replacementJson: String,
    ): Long = withContext(Dispatchers.IO) {
        collection(database, collection).replaceOne(
            parseDocument(filterJson, "filter"),
            parseDocument(replacementJson, "replacement"),
        ).modifiedCount
    }

    suspend fun updateDocuments(
        database: String,
        collection: String,
        filterJson: String,
        updateJson: String,
        multi: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        val coll = collection(database, collection)
        val filter = parseDocument(filterJson, "filter")
        val update = parseDocument(updateJson, "update")
        if (multi) {
            coll.updateMany(filter, update).modifiedCount
        } else {
            coll.updateOne(filter, update).modifiedCount
        }
    }

    suspend fun deleteDocuments(
        database: String,
        collection: String,
        filterJson: String,
        multi: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        val coll = collection(database, collection)
        val filter = parseDocument(filterJson, "filter")
        if (filter.isEmpty()) {
            throw MongoAdminException.Validation("删除 filter 不能为空对象，避免误删全表。")
        }
        if (multi) {
            coll.deleteMany(filter).deletedCount
        } else {
            coll.deleteOne(filter).deletedCount
        }
    }

    suspend fun aggregate(
        database: String,
        collection: String,
        pipelineJson: String,
        limit: Int = 100,
    ): QueryResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val pipeline = parsePipeline(pipelineJson)
        val docs = collection(database, collection)
            .aggregate<Document>(pipeline)
            .toList()
            .take(limit.coerceIn(1, 500))
            .map { pretty(it) }
        QueryResult(
            documents = docs,
            durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        )
    }

    suspend fun explainFind(
        database: String,
        collection: String,
        filterJson: String,
        sortJson: String = "{}",
        projectionJson: String = "{}",
    ): String = withContext(Dispatchers.IO) {
        val command = Document(
            mapOf(
                "explain" to Document(
                    mapOf(
                        "find" to requireName(collection, "集合"),
                        "filter" to parseDocument(filterJson, "filter"),
                        "sort" to parseDocument(sortJson, "sort"),
                        "projection" to parseDocument(projectionJson, "projection"),
                    ),
                ),
                "verbosity" to "executionStats",
            ),
        )
        pretty(
            sessionManager.requireClient()
                .getDatabase(requireName(database, "数据库"))
                .runCommand(command),
        )
    }

    suspend fun listIndexes(database: String, collection: String): List<IndexSummary> =
        withContext(Dispatchers.IO) {
            collection(database, collection).listIndexes<Document>().toList().map { doc ->
                IndexSummary(
                    name = doc.stringValue("name"),
                    keysJson = pretty(doc.get("key", Document())),
                    unique = doc.booleanValue("unique"),
                    sparse = doc.booleanValue("sparse"),
                    expireAfterSeconds = doc.numberLong("expireAfterSeconds"),
                    rawJson = pretty(doc),
                )
            }
        }

    suspend fun createIndex(
        database: String,
        collection: String,
        keysJson: String,
        name: String = "",
        unique: Boolean = false,
        sparse: Boolean = false,
        expireAfterSeconds: Long? = null,
    ): String = withContext(Dispatchers.IO) {
        val options = IndexOptions().unique(unique).sparse(sparse)
        if (name.isNotBlank()) {
            options.name(name.trim())
        }
        if (expireAfterSeconds != null && expireAfterSeconds >= 0) {
            options.expireAfter(expireAfterSeconds, TimeUnit.SECONDS)
        }
        collection(database, collection).createIndex(parseDocument(keysJson, "keys"), options)
    }

    suspend fun dropIndex(database: String, collection: String, indexName: String): Unit =
        withContext(Dispatchers.IO) {
            collection(database, collection).dropIndex(requireName(indexName, "索引名"))
        }

    suspend fun serverOverview(): ServerOverview = withContext(Dispatchers.IO) {
        val client = sessionManager.requireClient()
        val notes = mutableListOf<String>()
        val status = runCatching {
            client.getDatabase("admin").runCommand(Document("serverStatus", 1))
        }.onFailure { notes += "serverStatus 不可用: ${it.message}" }.getOrNull()
        val buildInfo = runCatching {
            client.getDatabase("admin").runCommand(Document("buildInfo", 1))
        }.onFailure { notes += "buildInfo 不可用: ${it.message}" }.getOrNull()
        val hostInfo = runCatching {
            client.getDatabase("admin").runCommand(Document("hostInfo", 1))
        }.onFailure { notes += "hostInfo 不可用: ${it.message}" }.getOrNull()

        val connections = status?.get("connections") as? Document
        val storageEngine = status?.get("storageEngine") as? Document
        val system = hostInfo?.get("system") as? Document
        ServerOverview(
            version = buildInfo?.stringValue("version")?.ifBlank { null }
                ?: status?.stringValue("version")?.ifBlank { null },
            gitVersion = buildInfo?.stringValue("gitVersion")?.ifBlank { null },
            uptimeSeconds = status?.numberLong("uptime"),
            connectionsCurrent = connections?.numberInt("current"),
            connectionsAvailable = connections?.numberInt("available"),
            storageEngine = storageEngine?.stringValue("name")?.ifBlank { null },
            host = status?.stringValue("host")?.ifBlank { null }
                ?: system?.stringValue("hostname")?.ifBlank { null },
            process = status?.stringValue("process")?.ifBlank { null },
            rawStatusJson = pretty(status ?: Document("ok", 0)),
            notes = notes,
        )
    }

    suspend fun listUsers(authDatabase: String = "admin"): List<String> = withContext(Dispatchers.IO) {
        val result = sessionManager.requireClient()
            .getDatabase(requireName(authDatabase, "认证库"))
            .runCommand(Document("usersInfo", 1))
        val users = result.getList("users", Document::class.java).orEmpty()
        users.mapNotNull { it.stringValue("user").takeIf(String::isNotBlank) }.sorted()
    }

    suspend fun currentOps(): String = withContext(Dispatchers.IO) {
        val result = sessionManager.requireClient()
            .getDatabase("admin")
            .runCommand(Document("currentOp", 1))
        pretty(result)
    }

    private fun collection(database: String, collection: String) =
        sessionManager.requireClient()
            .getDatabase(requireName(database, "数据库"))
            .getCollection<Document>(requireName(collection, "集合"))

    private fun parseDocument(raw: String, fieldName: String): Document {
        val normalized = MongoUriBuilder.validateJsonObject(raw, fieldName)
        return try {
            Document.parse(normalized)
        } catch (error: Exception) {
            throw MongoAdminException.Validation("$fieldName 解析失败: ${error.message}")
        }
    }

    private fun parsePipeline(raw: String): List<Document> {
        val normalized = MongoUriBuilder.validateJsonArray(raw, "pipeline")
        return try {
            val wrapped = Document.parse("{\"pipeline\":$normalized}")
            wrapped.getList("pipeline", Document::class.java).orEmpty()
        } catch (error: Exception) {
            throw MongoAdminException.Validation("pipeline 解析失败: ${error.message}")
        }
    }

    private fun pretty(document: Document?): String {
        if (document == null) return "{}"
        return document.toJson(prettyJsonSettings)
    }

    private fun requireName(value: String, label: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            throw MongoAdminException.Validation("$label 不能为空。")
        }
        if (trimmed.contains('\u0000')) {
            throw MongoAdminException.Validation("$label 包含非法字符。")
        }
        return trimmed
    }

    private fun Document.stringValue(key: String, default: String = ""): String {
        return when (val value = this[key]) {
            null -> default
            is String -> value
            else -> value.toString()
        }
    }

    private fun Document.booleanValue(key: String, default: Boolean = false): Boolean {
        return when (val value = this[key]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> default
        }
    }

    private fun Document.numberLong(key: String): Long? {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun Document.numberInt(key: String): Int? {
        return when (val value = this[key]) {
            is Number -> value.toInt()
            else -> null
        }
    }

    private fun Document.numberDouble(key: String): Double? {
        return when (val value = this[key]) {
            is Number -> value.toDouble()
            else -> null
        }
    }
}
