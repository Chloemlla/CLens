package com.chloemlla.clens.core.util

object SecretSanitizer {
    fun sanitize(value: String): String {
        return value
            .replace(Regex("""[A-Za-z]:\\Users\\[^\\\s]+"""), "[user-home]")
            .replace(Regex("""/home/[^/\s]+"""), "[user-home]")
            .replace(Regex("""/Users/[^/\s]+"""), "[user-home]")
            .replace(Regex("""content://[^\s]+"""), "[content-uri]")
            .replace(Regex("""file://[^\s]+"""), "[file-uri]")
            .replace(
                Regex("""mongodb(?:\+srv)?://[^:\s]+:[^@\s]+@""", RegexOption.IGNORE_CASE),
                "mongodb://[redacted]@",
            )
            .replace(
                Regex("""Bearer\s+[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE),
                "Bearer [redacted]",
            )
            .replace(
                Regex(
                    """([?&](?:key|api_key|apikey|access_token|refresh_token|token|password|secret)=)[^&\s]+""",
                    RegexOption.IGNORE_CASE,
                ),
            ) { match -> "${match.groupValues[1]}[redacted]" }
            .replace(Regex("""sk-[A-Za-z0-9_-]{12,}"""), "sk-[redacted]")
            .replace(Regex("""AIza[0-9A-Za-z_-]{20,}"""), "AIza[redacted]")
    }
}
