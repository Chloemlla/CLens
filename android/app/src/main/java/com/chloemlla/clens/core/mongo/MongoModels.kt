package com.chloemlla.clens.core.mongo

import java.util.UUID

data class MongoConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: String = "",
    val host: String = "",
    val port: Int = 27017,
    val username: String = "",
    val password: String = "",
    val authDatabase: String = "admin",
    val defaultDatabase: String = "",
    val replicaSet: String = "",
    val tls: Boolean = false,
    val tlsCaPem: String = "",
    val tlsClientCertPem: String = "",
    val tlsClientKeyPem: String = "",
    val tlsClientKeyPassphrase: String = "",
    val directConnection: Boolean = true,
    val readOnly: Boolean = false,
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshPrivateKeyPem: String = "",
    val sshPrivateKeyPassphrase: String = "",
    val sshRemoteHost: String = "",
    val sshRemotePort: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val displayTarget: String
        get() {
            val base = if (uri.isNotBlank()) {
                MongoUriBuilder.maskUri(uri)
            } else {
                val auth = username.takeIf { it.isNotBlank() }?.let { "$it@" }.orEmpty()
                "$auth$host:$port"
            }
            return if (sshEnabled && sshHost.isNotBlank()) {
                base + " via SSH " + sshHost + ":" + sshPort
            } else {
                base
            }
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

data class GridFsFileSummary(
    val id: String,
    val filename: String,
    val length: Long,
    val uploadDate: String = "",
    val contentType: String = "",
)

data class MongoUserSummary(
    val user: String,
    val db: String,
    val rolesJson: String,
)

data class MongoRoleSummary(
    val role: String,
    val db: String,
    val rolesJson: String,
    val privilegesJson: String,
)

data class CurrentOpSummary(
    val opId: String,
    val op: String = "",
    val ns: String = "",
    val secsRunning: Long? = null,
    val client: String = "",
    val rawJson: String = "",
)

data class CollectionValidatorInfo(
    val validatorJson: String = "{}",
    val validationLevel: String = "strict",
    val validationAction: String = "error",
    val rawJson: String = "{}",
)

data class OpCounterSnapshot(
    val timestampMillis: Long = System.currentTimeMillis(),
    val insert: Long = 0L,
    val query: Long = 0L,
    val update: Long = 0L,
    val delete: Long = 0L,
    val connectionsCurrent: Int? = null,
    val connectionsActive: Int? = null,
    val connectionsAvailable: Int? = null,
)

data class OpCounterRates(
    val insertQps: Double = 0.0,
    val queryQps: Double = 0.0,
    val updateQps: Double = 0.0,
    val deleteQps: Double = 0.0,
    val elapsedMillis: Long = 0L,
)

data class OpsCounterPoint(
    val timestampMillis: Long,
    val insertQps: Double,
    val queryQps: Double,
    val updateQps: Double,
    val deleteQps: Double,
    val connectionsCurrent: Int? = null,
    val connectionsActive: Int? = null,
    val connectionsAvailable: Int? = null,
)

data class OpsCounterPeak(
    val insertQps: Double = 0.0,
    val queryQps: Double = 0.0,
    val updateQps: Double = 0.0,
    val deleteQps: Double = 0.0,
)

data class OpsCounterSampleState(
    val points: List<OpsCounterPoint> = emptyList(),
    val current: OpsCounterPoint? = null,
    val peak: OpsCounterPeak = OpsCounterPeak(),
    val connectionsCurrent: Int? = null,
    val connectionsActive: Int? = null,
    val connectionsAvailable: Int? = null,
)

data class QueryHistoryEntry(
    val id: String,
    val modeAggregate: Boolean,
    val database: String,
    val collection: String,
    val filterJson: String = "{}",
    val sortJson: String = "{}",
    val projectionJson: String = "{}",
    val pipelineJson: String = "[]",
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val title: String
        get() {
            val mode = if (modeAggregate) "agg" else "find"
            return mode + " " + database + "." + collection
        }
}

data class QueryFavoriteEntry(
    val id: String,
    val name: String,
    val database: String = "",
    val collection: String = "",
    val filterJson: String = "{}",
    val sortJson: String = "{}",
    val projectionJson: String = "{}",
    val modeAggregate: Boolean = false,
    val pipelineJson: String = "[]",
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val title: String
        get() {
            val target = listOf(database, collection).filter { it.isNotBlank() }.joinToString(".")
            return if (target.isBlank()) name else "$name · $target"
        }
}

data class AuditLogEntry(
    val id: String,
    val action: String,
    val target: String,
    val detail: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
)

sealed class MongoAdminException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Validation(message: String, cause: Throwable? = null) : MongoAdminException(message, cause)
    class Operation(message: String, cause: Throwable? = null) : MongoAdminException(message, cause)
}
