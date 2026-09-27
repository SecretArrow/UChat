package com.uchat.android.core.fs

/**
 * Pure builders for the shell commands behind the Files screen operations.
 *
 * Everything here is plain JVM and unit-tested: the guards (what may be deleted), the quoting (user
 * names go through single-quote shell escaping) and the base64 write path (content that cannot be
 * typed safely is transported as base64 instead).
 */
object FileOps {

    /** Paths inside Ubuntu that must never be removed by the file manager. */
    private val PROTECTED =
        setOf(
            "/",
            "/root",
            "/usr",
            "/bin",
            "/sbin",
            "/etc",
            "/var",
            "/lib",
            "/lib64",
            "/boot",
            "/dev",
            "/proc",
            "/sys",
            "/system",
            "/data",
            "/root/workspace",
            "/root/downloads",
            "/root/shared",
            "/root/.uchat-scripts",
        )

    /** A user-typed path is only deletable when it is meaningful and not protected. */
    fun isDeletable(path: String): Boolean {
        val clean = path.trim().trimEnd('/')
        if (clean.isEmpty()) return false
        if (clean.split('/').any { it == ".." }) return false
        return clean !in PROTECTED
    }

    /** Shell-quotes a single argument for `bash -c` (single-quote wrapping). */
    fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    fun rename(from: String, to: String): String = "mv -n -- ${quote(from)} ${quote(to)}"

    fun delete(path: String): String = "rm -rf -- ${quote(path)}"

    fun mkdir(path: String): String = "mkdir -p -- ${quote(path)}"

    fun touch(path: String): String = "touch -- ${quote(path)}"

    fun extractTarGz(archive: String, into: String): String =
        "tar -xzf -- ${quote(archive)} -C ${quote(into)}"

    fun extractTar(archive: String, into: String): String =
        "tar -xf -- ${quote(archive)} -C ${quote(into)}"

    fun cat(path: String): String = "cat -- ${quote(path)}"

    /**
     * Writes [base64] (safe alphabet only) to [path]. Base64 keeps every byte — including newlines
     * and quotes — intact without any shell interpretation risk.
     */
    fun writeBase64(path: String, base64: String): String =
        "printf %s ${quote(base64)} | base64 -d > ${quote(path)}"

    /** true when [name] may be used as a new file/folder name in the UI. */
    fun isAcceptableName(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n.length > 255) return false
        if (n == "." || n == "..") return false
        return !n.contains('/') && !n.contains('\u0000')
    }

    /** tar-family archives the UI offers "Extract here" for (".tar.gz", ".tgz", ".tar"). */
    fun isArchive(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar")
    }

    /** File extensions the built-in text editor accepts (everything else only offers Share). */
    private val TEXT_EXTENSIONS =
        setOf(
            "txt",
            "md",
            "json",
            "xml",
            "yml",
            "yaml",
            "toml",
            "ini",
            "conf",
            "cfg",
            "properties",
            "sh",
            "bash",
            "bashrc",
            "profile",
            "kt",
            "java",
            "py",
            "js",
            "ts",
            "tsx",
            "jsx",
            "c",
            "h",
            "cpp",
            "hpp",
            "go",
            "rs",
            "rb",
            "php",
            "html",
            "css",
            "scss",
            "sql",
            "gradle",
            "kts",
            "csv",
            "log",
            "env",
            "gitignore",
            "lock",
            "service",
            "pl",
            "lua",
            "dart",
        )

    fun isTextFile(name: String): Boolean {
        val lower = name.lowercase()
        if (TEXT_EXTENSIONS.any { lower.endsWith(".$it") }) return true
        // Dotfiles like .gitignore / .env carry the extension as the whole name.
        val simple = lower.substringAfterLast('/')
        return simple.removePrefix(".") in TEXT_EXTENSIONS
    }

    /** 256 KiB — the built-in editor refuses larger files (use terminal tools instead). */
    const val MAX_EDIT_BYTES = 256 * 1024
}
