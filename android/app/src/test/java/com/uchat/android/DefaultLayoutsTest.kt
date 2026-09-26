package com.uchat.android

import com.uchat.android.terminal.keys.DefaultLayouts
import com.uchat.android.terminal.keys.ExtraKeyType
import com.uchat.android.terminal.keys.KeySequences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLayoutsTest {

    @Test
    fun `six built-in layouts exist with the spec names`() {
        val names = DefaultLayouts.all().map { it.name }
        assertEquals(listOf("Default", "Linux", "Developer", "Git", "Node.js", "Custom"), names)
    }

    @Test
    fun `all built-in keys have unique ids`() {
        DefaultLayouts.all().forEach { layout ->
            assertEquals(
                "duplicate ids in ${layout.name}",
                layout.keys.size,
                layout.keys.map { it.id }.toSet().size,
            )
        }
    }

    @Test
    fun `default layout matches the spec examples`() {
        val keys = DefaultLayouts.all().first { it.id == DefaultLayouts.DEFAULT_ID }.keys
        val labels = keys.map { it.label }
        // Spec: ESC, TAB, CTRL, ALT, arrows, /, |, ~, -, $
        listOf("Esc", "Tab", "CTRL", "ALT", "↑", "↓", "←", "→", "/", "|", "~", "-", "$").forEach {
            required ->
            assertTrue("missing $required", required in labels)
        }
    }

    @Test
    fun `modifier keys are typed correctly`() {
        DefaultLayouts.all()
            .flatMap { it.keys }
            .filter { it.label in setOf("CTRL", "ALT", "SHIFT", "META") }
            .forEach { key ->
                assertEquals(ExtraKeyType.MODIFIER, key.type)
                assertEquals(1, key.modifiers.size)
            }
    }

    @Test
    fun `git layout sends real commands`() {
        val git = DefaultLayouts.all().first { it.id == DefaultLayouts.GIT_ID }
        val statusKey = git.keys.first { it.label == "GIT" }
        assertEquals(ExtraKeyType.COMMAND, statusKey.type)
        assertArrayEquals("git status\r".toByteArray(), KeySequences.encode(statusKey))
    }

    @Test
    fun `developer layout includes ctrl combos`() {
        val dev = DefaultLayouts.all().first { it.id == DefaultLayouts.DEVELOPER_ID }
        val ctrlC = dev.keys.first { it.label == "CTRL+C" }
        assertEquals(byteArrayOf(0x03).size, KeySequences.encode(ctrlC).size)
    }

    @Test
    fun `node layout includes npm run dev command`() {
        val node = DefaultLayouts.all().first { it.id == DefaultLayouts.NODE_ID }
        val dev = node.keys.first { it.label == "DEV" }
        assertArrayEquals("npm run dev\r".toByteArray(), KeySequences.encode(dev))
    }

    @Test
    fun `arrows in default layout repeat while held`() {
        val defaults = DefaultLayouts.all().first { it.id == DefaultLayouts.DEFAULT_ID }
        val up = defaults.keys.first { it.label == "↑" }
        assertEquals(com.uchat.android.terminal.keys.RepeatBehavior.HOLD, up.repeat)
    }

    @Test
    fun `custom layout starts empty`() {
        val custom = DefaultLayouts.all().first { it.id == DefaultLayouts.CUSTOM_ID }
        assertEquals(0, custom.keys.size)
    }
}
