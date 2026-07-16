package com.chloemlla.clens.ui.monitor

import com.chloemlla.clens.core.mongo.CurrentOpSummary

enum class CurrentOpFilter(val label: String) {
    All("全部"),
    Slow("慢操作"),
    Write("写操作"),
    Query("查询"),
}

object CurrentOpFilters {
    const val SLOW_OP_SECS_THRESHOLD = 5L

    fun matchesFilter(op: CurrentOpSummary, filter: CurrentOpFilter): Boolean {
        val type = op.op.lowercase()
        val secs = op.secsRunning
        return when (filter) {
            CurrentOpFilter.All -> true
            CurrentOpFilter.Slow -> secs != null && secs >= SLOW_OP_SECS_THRESHOLD
            CurrentOpFilter.Write -> type in setOf("insert", "update", "remove", "delete") ||
                (type == "command" && (
                    op.rawJson.contains("\"insert\"", ignoreCase = true) ||
                        op.rawJson.contains("\"update\"", ignoreCase = true) ||
                        op.rawJson.contains("\"delete\"", ignoreCase = true)
                    ))
            CurrentOpFilter.Query -> type in setOf("query", "getmore", "find", "aggregate")
        }
    }

    fun matchesQuery(op: CurrentOpSummary, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val haystack = listOf(op.opId, op.op, op.ns, op.client, op.rawJson).joinToString("\n")
        return haystack.contains(q, ignoreCase = true)
    }
}
