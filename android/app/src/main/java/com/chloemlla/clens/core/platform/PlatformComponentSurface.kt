package com.chloemlla.clens.core.platform

/**
 * Android 12 component/backup surface checks (vivo doc 509).
 * Intent-filter components must declare android:exported; app backup stays closed.
 */
object PlatformComponentSurface {
    data class ComponentDecl(
        val tag: String,
        val name: String,
        val exported: Boolean?,
        val hasIntentFilter: Boolean,
    )

    fun extractComponents(manifestXml: String): List<ComponentDecl> {
        val tags = listOf("activity", "activity-alias", "service", "receiver", "provider")
        val results = mutableListOf<ComponentDecl>()
        tags.forEach { tag ->
            val regex = Regex(
                """<$tag\b([\s\S]*?)(?:/>|>([\s\S]*?)</$tag>)""",
                RegexOption.IGNORE_CASE,
            )
            regex.findAll(manifestXml).forEach { match ->
                val attrs = match.groupValues[1]
                val body = match.groupValues.getOrNull(2).orEmpty()
                val name = attr(attrs, "android:name") ?: "unknown"
                val exportedRaw = attr(attrs, "android:exported")
                val exported = when (exportedRaw?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                val hasFilter = body.contains("<intent-filter", ignoreCase = true)
                results += ComponentDecl(tag.lowercase(), name, exported, hasFilter)
            }
        }
        return results
    }

    fun validateExported(components: Collection<ComponentDecl>): List<String> {
        val problems = mutableListOf<String>()
        components.forEach { component ->
            if (component.hasIntentFilter && component.exported == null) {
                problems += "missing-exported:${component.tag}:${component.name}"
            }
        }
        return problems
    }

    fun isAllowBackupDisabled(manifestXml: String): Boolean {
        val value = attr(
            Regex("""<application\b([\s\S]*?)(?:/>|>)""", RegexOption.IGNORE_CASE)
                .find(manifestXml)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty(),
            "android:allowBackup",
        )
        return value.equals("false", ignoreCase = true)
    }

    fun mainActivityIsExportedLauncher(components: Collection<ComponentDecl>): Boolean {
        return components.any {
            it.tag == "activity" &&
                it.name.endsWith(".MainActivity") &&
                it.hasIntentFilter &&
                it.exported == true
        }
    }

    fun fileProviderIsNotExported(components: Collection<ComponentDecl>): Boolean {
        return components.any {
            it.tag == "provider" &&
                it.name.contains("FileProvider") &&
                it.exported == false
        }
    }

    private fun attr(attrs: String, key: String): String? {
        val match = Regex("""\b${Regex.escape(key)}\s*=\s*"([^"]*)"""")
            .find(attrs)
        return match?.groupValues?.getOrNull(1)
    }
}
