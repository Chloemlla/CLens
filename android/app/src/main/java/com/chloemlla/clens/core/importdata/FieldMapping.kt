package com.chloemlla.clens.core.importdata

/**
 * Source-field rename/skip map for CSV/JSON import.
 *
 * Empty target string means "skip this source field".
 * Iteration order follows [sourceToTarget] insertion order.
 */
data class FieldMapping(
    val sourceToTarget: LinkedHashMap<String, String>,
) {
    val sourceFields: List<String>
        get() = sourceToTarget.keys.toList()

    val targetFields: List<String>
        get() = sourceToTarget.values
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    val skipFields: List<String>
        get() = sourceToTarget.entries
            .asSequence()
            .filter { it.value.trim().isEmpty() }
            .map { it.key }
            .toList()

    fun isSkipped(sourceField: String): Boolean =
        sourceToTarget[sourceField]?.trim().isNullOrEmpty()

    fun targetFor(sourceField: String): String? {
        val target = sourceToTarget[sourceField] ?: return null
        val trimmed = target.trim()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    companion object {
        fun identity(sourceFields: List<String>): FieldMapping {
            val map = LinkedHashMap<String, String>(sourceFields.size)
            sourceFields.forEach { field ->
                val key = field.trim()
                if (key.isNotEmpty() && key !in map) {
                    map[key] = key
                }
            }
            return FieldMapping(map)
        }

        /**
         * Build mapping from parallel source/target lists.
         * [skipFields] forces those sources to empty target (skipped).
         * Missing targets fall back to the source name.
         */
        fun from(
            sourceFields: List<String>,
            targetFields: List<String> = sourceFields,
            skipFields: Collection<String> = emptyList(),
        ): FieldMapping {
            val skip = skipFields
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toHashSet()
            val map = LinkedHashMap<String, String>(sourceFields.size)
            sourceFields.forEachIndexed { index, rawSource ->
                val source = rawSource.trim()
                if (source.isEmpty() || source in map) return@forEachIndexed
                val target = when {
                    source in skip -> ""
                    index < targetFields.size -> targetFields[index].trim()
                    else -> source
                }
                map[source] = target
            }
            return FieldMapping(map)
        }
    }
}
