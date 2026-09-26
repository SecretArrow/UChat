package com.uchat.android.terminal.keys

import kotlinx.serialization.Serializable

/**
 * Extra-key system (spec: "Fully Customizable Keyboard & Extra Keys").
 *
 * Layer 5 of the terminal architecture: the extra-key system is fully independent from the
 * renderer, the emulator and the session backend. Keys are pure data; [KeySequences] turns a key
 * plus the current modifier state into the exact bytes to send.
 */

/** What kind of payload a key carries. */
enum class ExtraKeyType {
    /** Sends [ExtraKey.text] as plain text (e.g. `&&`, `~`, `npm run dev`). */
    TEXT,

    /** Sends a well-known terminal key (e.g. `ESC`, `TAB`, `UP`, `PGUP`, `F5`). */
    TERMINAL_KEY,

    /** A modifier (CTRL/ALT/SHIFT/META) — taps never send bytes directly. */
    MODIFIER,

    /** Modifier combination + base key (e.g. CTRL + `c` → 0x03, CTRL + UP → CSI 1;5A). */
    COMBO,

    /** Raw escape sequence written by the user (e.g. `\e[5~`, `\x1b[1;5A`). */
    ESCAPE_SEQ,

    /** Types the text then presses Enter (e.g. `git status`, `npm run dev`). */
    COMMAND,
}

/** Terminal modifiers that can be combined with other keys. */
enum class ModifierKey {
    CTRL,
    ALT,
    SHIFT,
    META,
}

/** How tapping a modifier key behaves (user customizable — spec: "customize modifier behavior"). */
enum class ModifierMode {
    /** Tap CTRL then tap C → CTRL+C. The modifier clears itself after one key. */
    MOMENTARY,

    /** Tap = latch until the next key, tap twice = locked, tap again = off. */
    LATCH,

    /** Tap toggles the modifier on/off, stays on until tapped again. */
    LOCK,
}

/** Whether holding a key repeats it. */
enum class RepeatBehavior {
    NONE,

    /** Repeat the key while the user keeps holding it (e.g. arrows). */
    HOLD,
}

/** One customizable extra key. Pure data — encoding lives in [KeySequences]. */
@Serializable
data class ExtraKey(
    val id: String,
    val label: String,
    val type: ExtraKeyType,
    /**
     * Payload meaning depends on [type]:
     * - TEXT / COMMAND: the literal text
     * - TERMINAL_KEY: canonical key name (see [KeySequences.TERMINAL_KEYS])
     * - COMBO: the base character or terminal key name (e.g. "c", "UP")
     * - ESCAPE_SEQ: user-written sequence with \e \x1b \u001b \n \t escapes
     */
    val text: String = "",
    /** Modifier combination for COMBO keys (unioned with the sticky modifier state). */
    val modifiers: List<ModifierKey> = emptyList(),
    /** Optional alternate payload sent on long press (e.g. GIT key long-press → `git status`). */
    val longPressText: String? = null,
    val repeat: RepeatBehavior = RepeatBehavior.NONE,
    /** Per-key haptic override; the global setting still applies. */
    val haptic: Boolean = true,
    /** Reserved: optional icon name. Label text is always shown when present. */
    val icon: String? = null,
)

/** A named, ordered collection of extra keys. Multiple layouts are supported (spec: layouts). */
@Serializable
data class KeyLayout(
    val id: String,
    val name: String,
    /** Built-in layouts can be edited and reset, but never deleted. */
    val builtIn: Boolean = false,
    val keys: List<ExtraKey> = emptyList(),
)

/** Persisted extra-keys state: layouts + selection + toolbar preference. */
@Serializable
data class ExtraKeysState(
    val layouts: List<KeyLayout> = emptyList(),
    val activeLayoutId: String = "",
    val toolbarEnabled: Boolean = true,
) {
    fun activeLayout(): KeyLayout? =
        layouts.firstOrNull { it.id == activeLayoutId } ?: layouts.firstOrNull()
}
