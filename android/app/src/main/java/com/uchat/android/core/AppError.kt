package com.uchat.android.core

/**
 * A user-presentable, copyable error.
 *
 * Every failure path in UChat produces an [AppError] instead of a raw code or exception string.
 * [AppError.diagnostics] is what the "Copy error" button puts on the clipboard — always secret-free
 * (see [redacted]).
 */
data class AppError(
    val title: String,
    val reason: String,
    val detail: String? = null,
    val step: String? = null,
    val cause: Throwable? = null,
) {
    val redacted: AppError
        get() = copy(detail = detail?.redactSecrets(), cause = cause)

    fun diagnostics(): String =
        buildString {
                appendLine("UChat error report")
                appendLine("appVersion : " + BuildInfo.VERSION_NAME)
                appendLine("title      : $title")
                if (step != null) appendLine("step       : $step")
                appendLine("reason     : $reason")
                if (detail != null) appendLine("detail     : ${detail.redactSecrets()}")
                cause?.let { c ->
                    appendLine("exception  : ${c.javaClass.name}: ${c.message?.redactSecrets()}")
                }
            }
            .redactSecrets()

    companion object {
        /**
         * Patterns that look like credentials are replaced before any error text leaves the app.
         */
        private val SECRET_PATTERNS =
            listOf(
                Regex("(?i)(api[_-]?key|token|authorization|password|secret)\\s*[:=]\\s*\\S+"),
                Regex("sk-[A-Za-z0-9]{16,}"),
                Regex("ghp_[A-Za-z0-9]{20,}"),
                Regex("Bearer\\s+\\S+"),
            )

        fun String.redactSecrets(): String {
            var out = this
            SECRET_PATTERNS.forEach { out = out.replace(it, "[REDACTED]") }
            return out
        }
    }
}

/** Static build info resolved once so [AppError] never needs a Context. */
object BuildInfo {
    var VERSION_NAME: String = "dev"
    var VERSION_CODE: Int = 0
}
