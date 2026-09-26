package com.uchat.android.ui.servers

/**
 * Pure parser for `ss -tlnp` output. Separated from the Composable so it is trivially unit-testable
 * (spec #76).
 */
object SsParser {

    /** One listening port as reported by `ss -tlnp` inside Ubuntu. */
    data class ListeningServer(
        val port: Int,
        val pid: Long,
        val command: String,
    )

    /** Parses lines like: `LISTEN 0 128 *:5173 *:* users:(("node",pid=4321,fd=20))` */
    fun parse(output: String): List<ListeningServer> =
        output
            .lineSequence()
            .filter { it.contains("LISTEN") }
            .mapNotNull { line ->
                val port =
                    Regex("(?:[:.](\\d{2,5}))\\s").findAll(line).lastOrNull()?.groupValues?.get(1)
                        ?: Regex(":(\\d{2,5})\\b").findAll(line).firstOrNull()?.groupValues?.get(1)
                val pid = Regex("pid=(\\d+)").find(line)?.groupValues?.get(1)
                val command =
                    Regex("\\(\\(\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "unknown"
                if (port == null) {
                    null
                } else {
                    ListeningServer(
                        port = port.toInt(),
                        pid = pid?.toLongOrNull() ?: 0,
                        command = command,
                    )
                }
            }
            .distinctBy { it.port to it.pid }
            .sortedBy { it.port }
            .toList()
}
