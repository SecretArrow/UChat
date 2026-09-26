package com.uchat.android.core.format

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Human-readable formatting helpers shared across the app and unit tested. */
object Format {

    /** 1536 → "1.5 KB", 29936675 → "28.5 MB". IEC units (1024 base). */
    fun bytes(value: Long): String {
        if (value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        val exp = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size - 1)
        val scaled = value / 1024.0.pow(exp)
        return String.format(
            Locale.US,
            if (scaled >= 100) "%.0f %s" else "%.1f %s",
            scaled,
            units[exp]
        )
    }

    /** 42 → "00:42", 3725 → "1:02:05". */
    fun duration(seconds: Long): String {
        if (seconds < 0) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /** ETA string, e.g. "34 sec", "2 min 10 sec", "1 h 5 min". */
    fun eta(seconds: Long): String {
        if (seconds <= 0) return "—"
        if (seconds < 60) return "$seconds sec"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "$h h $m min"
            m > 0 -> "$m min ${s}s"
            else -> "$seconds sec"
        }
    }

    /** 1_572_864 → "1.5 MB/s". */
    fun speed(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"

    /** 0..1 → "0%".."100%". */
    fun percent(fraction: Float): String =
        String.format(Locale.US, "%.0f%%", (fraction.coerceIn(0f, 1f)) * 100)
}
