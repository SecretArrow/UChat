package com.uchat.android.terminal.keys

import com.uchat.android.core.log.Logs
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Layer 5 storage: persists extra-key layouts as JSON and exposes them as a [StateFlow].
 *
 * Pure Kotlin (java.io only) so CRUD behaviour is fully unit-testable. Built-in layouts can be
 * edited and reset but never deleted; user layouts have full lifecycle (create/rename/duplicate/
 * delete/set as default).
 */
class ExtraKeysStore(baseDir: File) {

    private val file = File(baseDir, "extra-keys.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _state = MutableStateFlow(loadOrDefault())
    val state: StateFlow<ExtraKeysState> = _state.asStateFlow()

    // ---------------------------------------------------------------- selection

    fun selectLayout(id: String) {
        if (_state.value.layouts.none { it.id == id }) return
        update { it.copy(activeLayoutId = id) }
    }

    /** "Set as default" — persists the layout that opens next time (same as select). */
    fun setDefaultLayout(id: String) = selectLayout(id)

    fun setToolbarEnabled(enabled: Boolean) {
        update { it.copy(toolbarEnabled = enabled) }
    }

    fun activeLayout(): KeyLayout? = _state.value.activeLayout()

    // ---------------------------------------------------------------- key CRUD

    fun addKey(layoutId: String, key: ExtraKey) {
        update { st ->
            st.copy(
                layouts =
                    st.layouts.map { l -> if (l.id == layoutId) l.copy(keys = l.keys + key) else l }
            )
        }
    }

    fun updateKey(layoutId: String, key: ExtraKey) {
        update { st ->
            st.copy(
                layouts =
                    st.layouts.map { l ->
                        if (l.id == layoutId)
                            l.copy(keys = l.keys.map { if (it.id == key.id) key else it })
                        else l
                    }
            )
        }
    }

    fun deleteKey(layoutId: String, keyId: String) {
        update { st ->
            st.copy(
                layouts =
                    st.layouts.map { l ->
                        if (l.id == layoutId) l.copy(keys = l.keys.filterNot { it.id == keyId })
                        else l
                    }
            )
        }
    }

    fun duplicateKey(layoutId: String, keyId: String) {
        update { st ->
            st.copy(
                layouts =
                    st.layouts.map { l ->
                        if (l.id != layoutId) return@map l
                        val idx = l.keys.indexOfFirst { it.id == keyId }
                        if (idx < 0) return@map l
                        val copy =
                            l.keys[idx].copy(
                                id = UUID.randomUUID().toString(),
                                label = l.keys[idx].label
                            )
                        l.copy(keys = l.keys.toMutableList().apply { add(idx + 1, copy) })
                    }
            )
        }
    }

    fun moveKey(layoutId: String, fromIndex: Int, toIndex: Int) {
        update { st ->
            st.copy(
                layouts =
                    st.layouts.map { l ->
                        if (l.id != layoutId) return@map l
                        if (
                            fromIndex !in l.keys.indices ||
                                toIndex !in l.keys.indices ||
                                fromIndex == toIndex
                        ) {
                            return@map l
                        }
                        val mutable = l.keys.toMutableList()
                        val item = mutable.removeAt(fromIndex)
                        mutable.add(toIndex, item)
                        l.copy(keys = mutable)
                    }
            )
        }
    }

    // ---------------------------------------------------------------- layout CRUD

    fun createLayout(name: String): String {
        val id = UUID.randomUUID().toString()
        update { st ->
            st.copy(layouts = st.layouts + KeyLayout(id = id, name = name), activeLayoutId = id)
        }
        return id
    }

    fun renameLayout(id: String, name: String) {
        update { st ->
            st.copy(layouts = st.layouts.map { if (it.id == id) it.copy(name = name) else it })
        }
    }

    fun duplicateLayout(id: String): String? {
        val source = _state.value.layouts.firstOrNull { it.id == id } ?: return null
        val newId = UUID.randomUUID().toString()
        update { st ->
            st.copy(
                layouts =
                    st.layouts +
                        KeyLayout(
                            id = newId,
                            name = source.name + " copy",
                            builtIn = false,
                            keys = source.keys.map { it.copy(id = UUID.randomUUID().toString()) },
                        ),
                activeLayoutId = newId,
            )
        }
        return newId
    }

    fun deleteLayout(id: String): Boolean {
        val layout = _state.value.layouts.firstOrNull { it.id == id } ?: return false
        if (layout.builtIn) return false
        update { st ->
            val remaining = st.layouts.filterNot { it.id == id }
            st.copy(
                layouts = remaining,
                activeLayoutId =
                    if (st.activeLayoutId == id) remaining.firstOrNull()?.id.orEmpty()
                    else st.activeLayoutId,
            )
        }
        return true
    }

    /** Restore a layout's factory keys (only meaningful for built-in layouts). */
    fun resetLayout(id: String) {
        val factory = DefaultLayouts.all().firstOrNull { it.id == id } ?: return
        update { st -> st.copy(layouts = st.layouts.map { if (it.id == id) factory else it }) }
    }

    fun resetAll() {
        update { DefaultLayouts.defaultsState() }
    }

    // ---------------------------------------------------------------- persistence

    private fun update(transform: (ExtraKeysState) -> ExtraKeysState) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
    }

    private fun persist(state: ExtraKeysState) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ExtraKeysState.serializer(), state))
            if (!tmp.renameTo(file)) {
                file.writeText(json.encodeToString(ExtraKeysState.serializer(), state))
                tmp.delete()
            }
        } catch (e: Exception) {
            Logs.app("extra-keys persist failed: ${e.message}")
        }
    }

    private fun loadOrDefault(): ExtraKeysState =
        try {
            if (file.exists()) {
                val loaded = json.decodeFromString(ExtraKeysState.serializer(), file.readText())
                if (loaded.layouts.isEmpty()) DefaultLayouts.defaultsState() else loaded
            } else {
                DefaultLayouts.defaultsState()
            }
        } catch (e: Exception) {
            Logs.app("extra-keys load failed, using defaults: ${e.message}")
            DefaultLayouts.defaultsState()
        }
}
