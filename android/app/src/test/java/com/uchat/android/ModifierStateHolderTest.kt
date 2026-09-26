package com.uchat.android

import com.uchat.android.terminal.keys.ModifierKey
import com.uchat.android.terminal.keys.ModifierMode
import com.uchat.android.terminal.keys.ModifierStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierStateHolderTest {

    @Test
    fun `momentary mode clears after one consumed key`() {
        val holder = ModifierStateHolder(ModifierMode.MOMENTARY)
        holder.tap(ModifierKey.CTRL)
        assertEquals(setOf(ModifierKey.CTRL), holder.consume())
        assertTrue(holder.active().isEmpty())
    }

    @Test
    fun `momentary tap without use does not stick forever`() {
        val holder = ModifierStateHolder(ModifierMode.MOMENTARY)
        holder.tap(ModifierKey.CTRL)
        holder.consume()
        assertEquals(emptySet<ModifierKey>(), holder.active())
    }

    @Test
    fun `latch mode tap latches second tap locks third releases`() {
        val holder = ModifierStateHolder(ModifierMode.LATCH)
        holder.tap(ModifierKey.CTRL)
        assertEquals(ModifierStateHolder.State.LATCHED, holder.stateOf(ModifierKey.CTRL))
        holder.tap(ModifierKey.CTRL)
        assertEquals(ModifierStateHolder.State.LOCKED, holder.stateOf(ModifierKey.CTRL))
        holder.tap(ModifierKey.CTRL)
        assertEquals(ModifierStateHolder.State.OFF, holder.stateOf(ModifierKey.CTRL))
    }

    @Test
    fun `latched modifier survives consume`() {
        val holder = ModifierStateHolder(ModifierMode.LATCH)
        holder.tap(ModifierKey.CTRL) // latched
        assertEquals(setOf(ModifierKey.CTRL), holder.consume())
        // Latched state was consumed — next key is plain.
        assertEquals(emptySet<ModifierKey>(), holder.active())
    }

    @Test
    fun `locked modifier survives consume`() {
        val holder = ModifierStateHolder(ModifierMode.LATCH)
        holder.tap(ModifierKey.CTRL)
        holder.tap(ModifierKey.CTRL) // locked
        assertEquals(setOf(ModifierKey.CTRL), holder.consume())
        assertEquals(setOf(ModifierKey.CTRL), holder.consume())
        assertEquals(setOf(ModifierKey.CTRL), holder.active())
    }

    @Test
    fun `lock mode toggles on and off`() {
        val holder = ModifierStateHolder(ModifierMode.LOCK)
        holder.tap(ModifierKey.ALT)
        assertEquals(setOf(ModifierKey.ALT), holder.active())
        holder.tap(ModifierKey.ALT)
        assertEquals(emptySet<ModifierKey>(), holder.active())
    }

    @Test
    fun `long press always toggles hard lock`() {
        val holder = ModifierStateHolder(ModifierMode.MOMENTARY)
        holder.lockToggle(ModifierKey.CTRL)
        assertEquals(ModifierStateHolder.State.LOCKED, holder.stateOf(ModifierKey.CTRL))
        // Locked modifier stays active across consumes.
        holder.consume()
        assertEquals(setOf(ModifierKey.CTRL), holder.active())
        holder.lockToggle(ModifierKey.CTRL)
        assertEquals(emptySet<ModifierKey>(), holder.active())
    }

    @Test
    fun `multiple modifiers combine`() {
        val holder = ModifierStateHolder(ModifierMode.MOMENTARY)
        holder.tap(ModifierKey.CTRL)
        holder.tap(ModifierKey.ALT)
        assertEquals(setOf(ModifierKey.CTRL, ModifierKey.ALT), holder.consume())
        assertTrue(holder.active().isEmpty())
    }

    @Test
    fun `clear resets everything`() {
        val holder = ModifierStateHolder(ModifierMode.LATCH)
        holder.tap(ModifierKey.CTRL)
        holder.tap(ModifierKey.CTRL)
        holder.clear()
        assertTrue(holder.active().isEmpty())
    }
}
