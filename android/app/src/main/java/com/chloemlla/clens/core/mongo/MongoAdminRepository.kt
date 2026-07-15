package com.chloemlla.clens.core.mongo

import com.mongodb.MongoNamespace
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.changestream.FullDocument
import java.nio.charset.StandardCharsets
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.bson.types.ObjectId
import org.bson.types.Binary
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
        val safeLimit = limit.coerceIn(1, 500)
        val pipeline = ensureAggregateLimit(parsePipeline(pipelineJson), safeLimit)
        val docs = mutableListOf<String>()
        collection(database, collection)
            .aggregate<Document>(pipeline)
            .collect { document ->
                if (docs.size < safeLimit) {
                    docs += pretty(document)
                }
            }
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


    suspend fun validateCollection(database: String, collection: String): String = withContext(Dispatchers.IO) {
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        pretty(
            db.runCommand(
                Document(
                    mapOf(
                        "validate" to requireName(collection, "集合"),
                        "full" to false,
                    ),
                ),
            ),
        )
    }

    suspend fun compactCollection(database: String, collection: String): String = withContext(Dispatchers.IO) {
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        pretty(
            db.runCommand(
                Document(
                    mapOf(
                        "compact" to requireName(collection, "集合"),
                    ),
                ),
            ),
        )
    }

    suspend fun explainAggregate(
        database: String,
        collection: String,
        pipelineJson: String,
    ): String = withContext(Dispatchers.IO) {
        val pipeline = parsePipeline(pipelineJson)
        val command = Document(
            mapOf(
                "explain" to Document(
                    mapOf(
                        "aggregate" to requireName(collection, "集合"),
                        "pipeline" to pipeline,
                        "cursor" to Document(),
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

    suspend fun currentOps(): String = withContext(Dispatchers.IO) {
        val result = sessionManager.requireClient()
            .getDatabase("admin")
            .runCommand(Document("currentOp", 1))
        pretty(result)
    }



    suspend fun listGridFsFiles(database: String, bucketName: String = "fs"): List<GridFsFileSummary> =
        withContext(Dispatchers.IO) {
            val bucket = bucketName.ifBlank { "fs" }
            val files = sessionManager.requireClient()
                .getDatabase(requireName(database, "数据库"))
                .getCollection<Document>("$bucket.files")
            files.find()
                .sort(Sorts.descending("uploadDate"))
                .limit(200)
                .toList()
                .map { doc ->
                    GridFsFileSummary(
                        id = doc["_id"]?.toString().orEmpty(),
                        filename = doc.stringValue("filename"),
                        length = doc.numberLong("length") ?: 0L,
                        uploadDate = doc["uploadDate"]?.toString().orEmpty(),
                        contentType = (doc["metadata"] as? Document)?.stringValue("contentType").orEmpty(),
                    )
                }
        }

    suspend fun uploadGridFsText(
        database: String,
        filename: String,
        content: String,
        bucketName: String = "fs",
    ): String = withContext(Dispatchers.IO) {
        val bucket = bucketName.ifBlank { "fs" }
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        val files = db.getCollection<Document>("$bucket.files")
        val chunks = db.getCollection<Document>("$bucket.chunks")
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val id = ObjectId()
        val chunkSize = 255 * 1024
        val fileDoc = Document(
            mapOf(
                "_id" to id,
                "filename" to requireName(filename, "文件名"),
                "length" to bytes.size.toLong(),
                "chunkSize" to chunkSize,
                "uploadDate" to Date(),
                "metadata" to Document("contentType", "text/plain"),
            ),
        )
        files.insertOne(fileDoc)
        var n = 0
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            val slice = bytes.copyOfRange(offset, end)
            chunks.insertOne(
                Document(
                    mapOf(
                        "files_id" to id,
                        "n" to n,
                        "data" to Binary(slice),
                    ),
                ),
            )
            n += 1
            offset = end
        }
        id.toString()
    }

    suspend fun downloadGridFsText(
        database: String,
        fileId: String,
        bucketName: String = "fs",
        maxBytes: Int = 512 * 1024,
    ): String = withContext(Dispatchers.IO) {
        val bucket = bucketName.ifBlank { "fs" }
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        val files = db.getCollection<Document>("$bucket.files")
        val chunks = db.getCollection<Document>("$bucket.chunks")
        val objectId = parseObjectId(fileId)
        val file = files.find(Filters.eq("_id", objectId)).toList().firstOrNull()
            ?: throw MongoAdminException.Validation("未找到 GridFS 文件。")
        val length = file.numberLong("length") ?: 0L
        if (length > maxBytes) {
            throw MongoAdminException.Validation("文件过大（>$maxBytes bytes），请使用桌面工具下载。")
        }
        val ordered = chunks.find(Filters.eq("files_id", objectId)).sort(Sorts.ascending("n")).toList()
        val out = java.io.ByteArrayOutputStream()
        ordered.forEach { chunk ->
            val data = chunk["data"]
            when (data) {
                is ByteArray -> out.write(data)
                is org.bson.types.Binary -> out.write(data.data)
                else -> Unit
            }
        }
        String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    suspend fun deleteGridFsFile(
        database: String,
        fileId: String,
        bucketName: String = "fs",
    ): Unit = withContext(Dispatchers.IO) {
        val bucket = bucketName.ifBlank { "fs" }
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        val objectId = parseObjectId(fileId)
        db.getCollection<Document>("$bucket.files").deleteOne(Filters.eq("_id", objectId))
        db.getCollection<Document>("$bucket.chunks").deleteMany(Filters.eq("files_id", objectId))
    }

    fun openChangeStream(
        scope: CoroutineScope,
        database: String,
        collectionName: String,
        onEvent: (String) -> Unit,
        onError: (String) -> Unit,
        onClosed: () -> Unit,
    ): Job {
        return scope.launch(Dispatchers.IO) {
            try {
                val coll = collection(database, collectionName)
                coll.watch<Document>()
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .collect { change ->
                        val payload = Document().apply {
                            put("operationType", change.operationType?.toString())
                            put("documentKey", change.documentKey)
                            put("ns", Document("db", database).append("coll", collectionName))
                            change.fullDocument?.let { put("fullDocument", it) }
                        }
                        onEvent(pretty(payload))
                    }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    onClosed()
                    throw error
                }
                onError(error.message ?: "Change Stream 失败（需要副本集/分片集群）")
                onClosed()
            }
        }
    }

    suspend fun listUsersDetailed(authDatabase: String = "admin"): List<MongoUserSummary> =
        withContext(Dispatchers.IO) {
            val result = sessionManager.requireClient()
                .getDatabase(requireName(authDatabase, "认证库"))
                .runCommand(Document("usersInfo", 1))
            val users = result.getList("users", Document::class.java).orEmpty()
            users.mapNotNull { doc ->
                val user = doc.stringValue("user")
                if (user.isBlank()) {
                    null
                } else {
                    MongoUserSummary(
                        user = user,
                        db = doc.stringValue("db", authDatabase),
                        rolesJson = pretty(Document("roles", doc["roles"])),
                    )
                }
            }.sortedBy { it.user }
        }

    suspend fun createUser(
        authDatabase: String,
        user: String,
        password: String,
        rolesJson: String,
    ): Unit = withContext(Dispatchers.IO) {
        if (password.isBlank()) {
            throw MongoAdminException.Validation("密码不能为空。")
        }
        val rolesPayload = normalizeArrayField(rolesJson, "roles")
        val command = Document(
            mapOf(
                "createUser" to requireName(user, "用户名"),
                "pwd" to password,
                "roles" to rolesPayload,
            ),
        )
        sessionManager.requireClient()
            .getDatabase(requireName(authDatabase, "认证库"))
            .runCommand(command)
    }

    suspend fun dropUser(authDatabase: String, user: String): Unit = withContext(Dispatchers.IO) {
        sessionManager.requireClient()
            .getDatabase(requireName(authDatabase, "认证库"))
            .runCommand(Document("dropUser", requireName(user, "用户名")))
    }

    suspend fun listRoles(authDatabase: String = "admin"): List<MongoRoleSummary> =
        withContext(Dispatchers.IO) {
            val result = sessionManager.requireClient()
                .getDatabase(requireName(authDatabase, "认证库"))
                .runCommand(
                    Document("rolesInfo", 1)
                        .append("showPrivileges", true)
                        .append("showBuiltinRoles", false),
                )
            val roles = result.getList("roles", Document::class.java).orEmpty()
            roles.mapNotNull { doc ->
                val role = doc.stringValue("role")
                if (role.isBlank()) {
                    null
                } else {
                    MongoRoleSummary(
                        role = role,
                        db = doc.stringValue("db", authDatabase),
                        rolesJson = pretty(Document("roles", doc["roles"])),
                        privilegesJson = pretty(Document("privileges", doc["privileges"])),
                    )
                }
            }.sortedBy { it.role }
        }

    suspend fun createRole(
        authDatabase: String,
        role: String,
        privilegesJson: String,
        rolesJson: String,
    ): Unit = withContext(Dispatchers.IO) {
        val privileges = normalizeArrayField(privilegesJson, "privileges")
        val roles = normalizeArrayField(rolesJson, "roles")
        val command = Document(
            mapOf(
                "createRole" to requireName(role, "角色名"),
                "privileges" to privileges,
                "roles" to roles,
            ),
        )
        sessionManager.requireClient()
            .getDatabase(requireName(authDatabase, "认证库"))
            .runCommand(command)
    }

    suspend fun dropRole(authDatabase: String, role: String): Unit = withContext(Dispatchers.IO) {
        sessionManager.requireClient()
            .getDatabase(requireName(authDatabase, "认证库"))
            .runCommand(Document("dropRole", requireName(role, "角色名")))
    }

    suspend fun exportDocuments(
        database: String,
        collectionName: String,
        filterJson: String = "{}",
        limit: Int = 200,
    ): String = withContext(Dispatchers.IO) {
        val page = findDocuments(
            database = database,
            collection = collectionName,
            filterJson = filterJson,
            sortJson = "{}",
            projectionJson = "{}",
            limit = limit.coerceIn(1, 1000),
            skip = 0,
        )
        val array = org.json.JSONArray()
        page.documents.forEach { raw ->
            runCatching { array.put(org.json.JSONObject(raw)) }.getOrElse { array.put(raw) }
        }
        array.toString(2)
    }

    suspend fun importDocuments(
        database: String,
        collectionName: String,
        jsonArrayOrDocs: String,
        dropBeforeImport: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        if (dropBeforeImport) {
            runCatching { dropCollection(database, collectionName) }
            createCollection(database, collectionName)
        }
        val payload = jsonArrayOrDocs.trim().ifBlank { "[]" }
        insertDocuments(database, collectionName, payload)
    }

    private fun parseObjectId(raw: String): ObjectId {
        val trimmed = raw.trim()
        if (!ObjectId.isValid(trimmed)) {
            throw MongoAdminException.Validation("无效的 ObjectId。")
        }
        return ObjectId(trimmed)
    }

    private fun normalizeArrayField(raw: String, fieldName: String): Any {
        val trimmed = raw.trim().ifBlank {
            return emptyList<Any>()
        }
        val wrapped = if (trimmed.startsWith("[")) {
            "{\"$fieldName\":$trimmed}"
        } else {
            trimmed
        }
        val doc = parseDocument(wrapped, fieldName)
        return doc[fieldName] ?: emptyList<Any>()
    }


    suspend fun listCurrentOps(): List<CurrentOpSummary> = withContext(Dispatchers.IO) {
        val result = sessionManager.requireClient()
            .getDatabase("admin")
            .runCommand(Document("currentOp", 1))
        val inprog = result.getList("inprog", Document::class.java).orEmpty()
        inprog.mapNotNull { doc ->
            val opId = doc["opid"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CurrentOpSummary(
                opId = opId,
                op = doc.stringValue("op"),
                ns = doc.stringValue("ns"),
                secsRunning = doc.numberLong("secs_running") ?: doc.numberLong("microsecs_running")?.let { it / 1_000_000 },
                client = doc.stringValue("client"),
                rawJson = pretty(doc),
            )
        }
    }

    suspend fun killOp(opId: String): String = withContext(Dispatchers.IO) {
        val idValue: Any = opId.toLongOrNull() ?: opId
        val result = sessionManager.requireClient()
            .getDatabase("admin")
            .runCommand(Document("killOp", 1).append("op", idValue))
        pretty(result)
    }

    suspend fun getCollectionValidator(
        database: String,
        collectionName: String,
    ): CollectionValidatorInfo = withContext(Dispatchers.IO) {
        val db = sessionManager.requireClient().getDatabase(requireName(database, "数据库"))
        val info = db.runCommand(Document("listCollections", 1).append("filter", Document("name", requireName(collectionName, "集合"))))
        val first = info.get("cursor", Document::class.java)
            ?.getList("firstBatch", Document::class.java)
            ?.firstOrNull()
            ?: throw MongoAdminException.Validation("未找到集合元数据。")
        val options = first.get("options", Document::class.java) ?: Document()
        val validator = options.get("validator", Document::class.java) ?: Document()
        CollectionValidatorInfo(
            validatorJson = pretty(validator),
            validationLevel = options.stringValue("validationLevel", "strict"),
            validationAction = options.stringValue("validationAction", "error"),
            rawJson = pretty(first),
        )
    }

    suspend fun setCollectionValidator(
        database: String,
        collectionName: String,
        validatorJson: String,
        validationLevel: String,
        validationAction: String,
    ): String = withContext(Dispatchers.IO) {
        val validator = parseDocument(validatorJson.ifBlank { "{}" }, "validator")
        val command = Document(
            mapOf(
                "collMod" to requireName(collectionName, "集合"),
                "validator" to validator,
                "validationLevel" to validationLevel.ifBlank { "strict" },
                "validationAction" to validationAction.ifBlank { "error" },
            ),
        )
        pretty(
            sessionManager.requireClient()
                .getDatabase(requireName(database, "数据库"))
                .runCommand(command),
        )
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

    companion object {
        fun ensureAggregateLimit(pipeline: List<Document>, limit: Int): List<Document> {
            val safeLimit = limit.coerceIn(1, 500)
            val hasLimitStage = pipeline.any { stage -> stage.containsKey("\$limit") }
            return if (hasLimitStage) {
                pipeline
            } else {
                pipeline + Document("\$limit", safeLimit)
            }
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
