package com.uchat.android

import com.uchat.android.terminal.keys.DefaultLayouts
import com.uchat.android.terminal.keys.ExtraKey
import com.uchat.android.terminal.keys.ExtraKeyType
import com.uchat.android.terminal.keys.ExtraKeysStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtraKeysStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(): ExtraKeysStore = ExtraKeysStore(tmp.newFolder())

    @Test
    fun `fresh store provides six built-in layouts with Default active`() {
        val store = newStore()
        val state = store.state.value
        assertEquals(6, state.layouts.size)
        assertEquals(DefaultLayouts.DEFAULT_ID, state.activeLayoutId)
        assertTrue(state.layouts.all { it.builtIn })
    }

    @Test
    fun `add key appends to active layout`() {
        val store = newStore()
        val layoutId = store.state.value.activeLayoutId
        val before = store.activeLayout()!!.keys.size
        store.addKey(
            layoutId,
            ExtraKey(id = "x1", label = "GIT", type = ExtraKeyType.COMMAND, text = "git status")
        )
        assertEquals(before + 1, store.activeLayout()!!.keys.size)
        assertEquals("GIT", store.activeLayout()!!.keys.last().label)
    }

    @Test
    fun `update key replaces in place`() {
        val store = newStore()
        val layoutId = store.state.value.activeLayoutId
        store.addKey(
            layoutId,
            ExtraKey(id = "x1", label = "A", type = ExtraKeyType.TEXT, text = "a")
        )
        store.updateKey(
            layoutId,
            ExtraKey(id = "x1", label = "B", type = ExtraKeyType.TEXT, text = "b")
        )
        val key = store.activeLayout()!!.keys.first { it.id == "x1" }
        assertEquals("B", key.label)
        assertEquals("b", key.text)
    }

    @Test
    fun `delete key removes`() {
        val store = newStore()
        val layoutId = store.state.value.activeLayoutId
        store.addKey(
            layoutId,
            ExtraKey(id = "x1", label = "A", type = ExtraKeyType.TEXT, text = "a")
        )
        store.deleteKey(layoutId, "x1")
        assertNull(store.activeLayout()!!.keys.firstOrNull { it.id == "x1" })
    }

    @Test
    fun `duplicate key inserts copy after original`() {
        val store = newStore()
        val layoutId = store.state.value.activeLayoutId
        store.addKey(
            layoutId,
            ExtraKey(id = "x1", label = "A", type = ExtraKeyType.TEXT, text = "a")
        )
        val layout = store.activeLayout()!!
        val idx = layout.keys.indexOfFirst { it.id == "x1" }
        store.duplicateKey(layoutId, "x1")
        val after = store.activeLayout()!!.keys
        assertEquals("A", after[idx + 1].label)
        assertTrue(after[idx + 1].id != "x1")
    }

    @Test
    fun `move key reorders`() {
        val store = newStore()
        val layoutId = store.state.value.activeLayoutId
        val original = store.activeLayout()!!.keys
        store.moveKey(layoutId, 0, 2)
        val moved = store.activeLayout()!!.keys
        assertEquals(original[1], moved[0])
        assertEquals(original[0], moved[2])
    }

    @Test
    fun `move key with invalid indices is a no-op`() {
        val store = newStore()
        val before = store.activeLayout()!!.keys
        store.moveKey(store.state.value.activeLayoutId, -1, 99)
        assertEquals(before, store.activeLayout()!!.keys)
    }

    @Test
    fun `state persists across store restarts`() {
        val dir = tmp.newFolder()
        val store = ExtraKeysStore(dir)
        store.createLayout("Mine")
        val id = store.state.value.activeLayoutId

        val reloaded = ExtraKeysStore(dir)
        assertEquals(id, reloaded.state.value.activeLayoutId)
        assertEquals("Mine", reloaded.activeLayout()!!.name)
    }

    @Test
    fun `corrupt file falls back to defaults`() {
        val dir = tmp.newFolder()
        File(dir, "extra-keys.json").writeText("{ not json !!!")
        val store = ExtraKeysStore(dir)
        assertEquals(6, store.state.value.layouts.size)
    }

    @Test
    fun `create select rename duplicate delete layout lifecycle`() {
        val store = newStore()
        val createdId = store.createLayout("Node")
        assertEquals(createdId, store.state.value.activeLayoutId)
        store.selectLayout(DefaultLayouts.DEFAULT_ID)
        store.renameLayout(createdId, "Node.js")
        assertEquals("Node.js", store.state.value.layouts.first { it.id == createdId }.name)
        val dupId = store.duplicateLayout(DefaultLayouts.DEFAULT_ID)
        assertNotNull(dupId)
        val dupIdChecked = dupId!!
        assertFalse(store.state.value.layouts.first { it.id == dupIdChecked }.builtIn)
        assertTrue(store.deleteLayout(dupIdChecked))
        // Built-in layouts cannot be deleted.
        assertFalse(store.deleteLayout(DefaultLayouts.DEFAULT_ID))
        assertEquals(7, store.state.value.layouts.size) // 6 builtin + "Node.js"
    }

    @Test
    fun `reset layout restores factory keys`() {
        val store = newStore()
        val layoutId = DefaultLayouts.DEFAULT_ID
        val before = store.state.value.layouts.first { it.id == layoutId }.keys.size
        store.deleteKey(layoutId, store.activeLayout()!!.keys.first().id)
        assertEquals(before - 1, store.state.value.layouts.first { it.id == layoutId }.keys.size)
        store.resetLayout(layoutId)
        assertEquals(before, store.state.value.layouts.first { it.id == layoutId }.keys.size)
    }

    @Test
    fun `reset all restores everything`() {
        val store = newStore()
        store.createLayout("Extra")
        store.resetAll()
        assertEquals(6, store.state.value.layouts.size)
        assertEquals(DefaultLayouts.DEFAULT_ID, store.state.value.activeLayoutId)
    }

    @Test
    fun `toolbar toggle persists`() {
        val dir = tmp.newFolder()
        val store = ExtraKeysStore(dir)
        store.setToolbarEnabled(false)
        assertFalse(ExtraKeysStore(dir).state.value.toolbarEnabled)
    }
}
