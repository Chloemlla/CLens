package com.chloemlla.clens.core.platform

/**
 * Android 11 scoped-storage surface for CLens (vivo doc 428).
 * Keep reads on SAF and writes on app-private/cache FileProvider paths.
 */
object PlatformStorageSurface {
    val allowedFileProviderRoots: Set<String> = setOf(
        "cache-path",
        "external-cache-path",
    )

    val forbiddenFileProviderRoots: Set<String> = setOf(
        "root-path",
        "files-path", // keep private; do not expose full filesDir via provider
        "external-path",
        "external-files-path",
        "external-media-path",
    )

    fun usesLegacyExternalStorage(manifestXml: String): Boolean {
        return Regex(
            """\bandroid:requestLegacyExternalStorage\s*=\s*"true"""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(manifestXml)
    }

    fun extractFileProviderPathTags(filePathsXml: String): List<String> {
        val regex = Regex("""<(cache-path|external-cache-path|files-path|external-path|external-files-path|external-media-path|root-path)\b""")
        return regex.findAll(filePathsXml).map { it.groupValues[1] }.toList()
    }

    fun validateFileProviderPaths(pathTags: Collection<String>): List<String> {
        val problems = mutableListOf<String>()
        pathTags.forEach { tag ->
            if (tag !in allowedFileProviderRoots) {
                problems += "undeclared-file-provider-root:$tag"
            }
            if (tag in forbiddenFileProviderRoots) {
                problems += "forbidden-file-provider-root:$tag"
            }
        }
        return problems
    }

    /**
     * Export files must be created under app cache (shared via FileProvider cache-path).
     */
    fun isAppCacheExportPath(absolutePath: String): Boolean {
        val normalized = absolutePath.replace('\\', '/').lowercase()
        return normalized.contains("/cache/") && normalized.contains("/export/")
    }

    fun isAppPrivateFilesPath(absolutePath: String): Boolean {
        val normalized = absolutePath.replace('\\', '/').lowercase()
        return normalized.contains("/files/") && !normalized.contains("/android/data/")
            || normalized.contains("/files/")
    }
}
