package com.chloemlla.clens.core.mongo

import java.util.UUID

data class MongoConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: String = "",
    val host: String = "127.0.0.1",
    val port: Int = 27017,
    val username: String = "",
    val password: String = "",
    val authDatabase: String = "admin",
    val defaultDatabase: String = "",
    val replicaSet: String = "",
    val tls: Boolean = false,
    val directConnection: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val displayTarget: String
        get() = if (uri.isNotBlank()) {
            MongoUriBuilder.maskUri(uri)
        } else {
            val auth = username.takeIf { it.isNotBlank() }?.let { "$it@" }.orEmpty()
            "$auth$host:$port"
        }
}

data class DatabaseSummary(
    val name: String,
    val sizeOnDisk: Long? = null,
    val empty: Boolean = false,
    val collections: Int? = null,
)

data class CollectionSummary(
    val name: String,
    val type: String = "collection",
    val count: Long? = null,
    val size: Long? = null,
    val storageSize: Long? = null,
    val totalIndexSize: Long? = null,
    val avgObjSize: Double? = null,
    val nindexes: Int? = null,
)

data class DocumentPage(
    val documents: List<String>,
    val countHint: Long? = null,
    val limit: Int,
    val skip: Int,
)

data class IndexSummary(
    val name: String,
    val keysJson: String,
    val unique: Boolean = false,
    val sparse: Boolean = false,
    val expireAfterSeconds: Long? = null,
    val rawJson: String,
)

data class ServerOverview(
    val version: String? = null,
    val gitVersion: String? = null,
    val uptimeSeconds: Long? = null,
    val connectionsCurrent: Int? = null,
    val connectionsAvailable: Int? = null,
    val storageEngine: String? = null,
    val host: String? = null,
    val process: String? = null,
    val rawStatusJson: String = "{}",
    val notes: List<String> = emptyList(),
)

data class QueryResult(
    val documents: List<String>,
    val explainJson: String? = null,
    val durationMillis: Long = 0L,
)

data class ConnectionTestResult(
    val ok: Boolean,
    val latencyMillis: Long,
    val serverVersion: String? = null,
    val message: String,
)

sealed class MongoAdminException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Validation(message: String, cause: Throwable? = null) : MongoAdminException(message, cause)
    class Operation(message: String, cause: Throwable? = null) : MongoAdminException(message, cause)
}