package com.uchat.android.terminal.keys

/**
 * Layer 5: sticky-modifier state machine.
 *
 * Supports the three user-selectable behaviours (spec: Momentary / Latched / Locked):
 * - MOMENTARY: tap CTRL then C → CTRL+C, modifier auto-clears after the next key.
 * - LATCH: tap = latched (used by next key), tap again = locked, tap again = off.
 * - LOCK: tap toggles locked on/off.
 *
 * Long-pressing a modifier always toggles a hard lock regardless of mode.
 */
class ModifierStateHolder(var mode: ModifierMode = ModifierMode.MOMENTARY) {

    enum class State {
        OFF,
        LATCHED,
        LOCKED
    }

    private val states = HashMap<ModifierKey, State>()

    fun tap(mod: ModifierKey) {
        val current = states[mod] ?: State.OFF
        val next =
            when (mode) {
                ModifierMode.MOMENTARY -> State.LATCHED
                ModifierMode.LATCH ->
                    when (current) {
                        State.OFF -> State.LATCHED
                        State.LATCHED -> State.LOCKED
                        State.LOCKED -> State.OFF
                    }
                ModifierMode.LOCK ->
                    when (current) {
                        State.OFF -> State.LOCKED
                        else -> State.OFF
                    }
            }
        if (next == State.OFF) states.remove(mod) else states[mod] = next
    }

    /** Long-press: hard lock toggle, independent of the configured mode. */
    fun lockToggle(mod: ModifierKey) {
        if (states[mod] == State.LOCKED) states.remove(mod) else states[mod] = State.LOCKED
    }

    /** The set of modifiers currently applied to the next key. */
    fun active(): Set<ModifierKey> = states.filterValues { it != State.OFF }.keys.toSet()

    /**
     * Take the active modifiers for the next key and consume latched/momentary state; locked
     * modifiers stay on.
     */
    fun consume(): Set<ModifierKey> {
        val out = active()
        states.entries.removeAll { it.value == State.LATCHED }
        return out
    }

    fun stateOf(mod: ModifierKey): State = states[mod] ?: State.OFF

    fun release(mod: ModifierKey) {
        states.remove(mod)
    }

    fun clear() {
        states.clear()
    }
}
