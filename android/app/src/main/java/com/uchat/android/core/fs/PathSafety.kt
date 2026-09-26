package com.uchat.android.core.fs

import java.io.File

/**
 * Path-safety helpers used by every place that builds a path from user input or from archive
 * entries (zip-slip / tar traversal prevention).
 *
 * All functions are pure JVM — covered by unit tests.
 */
object PathSafety {

    private val VALID_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")

    /** Rejects path traversal, absolute paths, NUL and control characters. */
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.contains('\u0000')) return false
        if (path.startsWith("/")) return false
        if (path.contains("..")) return false
        // Windows drive letters are not valid on Linux targets either.
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
    }

    /** File/dir name chosen by the user in UI forms. */
    fun isSafeName(name: String): Boolean = VALID_NAME.matches(name)

    /**
     * Resolves [relative] inside [base] and guarantees the canonical result stays inside the base
     * directory. Returns null when unsafe.
     */
    fun safeResolve(base: File, relative: String): File? {
        if (!isSafeRelativePath(relative)) return null
        val baseCanonical = base.canonicalFile
        val target = File(baseCanonical, relative)
        return if (target.canonicalPath.startsWith(baseCanonical.canonicalPath + File.separator)) {
            target
        } else {
            null
        }
    }

    /** Returns true when [target] resolves inside [base] (used after archive entry name checks). */
    fun isInside(base: File, target: File): Boolean {
        val baseCanonical = base.canonicalFile.path + File.separator
        return target.canonicalPath.startsWith(baseCanonical)
    }

    /** Archive entry names that must never be extracted. */
    fun isDangerousEntryName(name: String): Boolean {
        val n = name.replace('\\', '/')
        return n.startsWith("/") ||
            n.contains("..") ||
            n.contains('\u0000') ||
            Regex("^[A-Za-z]:").containsMatchIn(n)
    }
}
