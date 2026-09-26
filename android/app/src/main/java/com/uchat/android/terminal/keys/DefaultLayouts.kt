package com.uchat.android.terminal.keys

import java.util.UUID

/**
 * Factory for the six built-in extra-key layouts (spec: Default, Linux, Developer, Git, Node.js,
 * Custom). All keys use stable ids derived from the layout so Reset reproduces them exactly.
 */
object DefaultLayouts {

    const val DEFAULT_ID = "builtin-default"
    const val LINUX_ID = "builtin-linux"
    const val DEVELOPER_ID = "builtin-developer"
    const val GIT_ID = "builtin-git"
    const val NODE_ID = "builtin-node"
    const val CUSTOM_ID = "builtin-custom"

    fun all(): List<KeyLayout> = listOf(default(), linux(), developer(), git(), node(), custom())

    fun defaultsState(): ExtraKeysState =
        ExtraKeysState(
            layouts = all(),
            activeLayoutId = DEFAULT_ID,
            toolbarEnabled = true,
        )

    private fun key(id: String, label: String, type: ExtraKeyType, text: String = "") =
        ExtraKey(
            id = id,
            label = label,
            type = type,
            text = text,
            repeat = if (label in REPEAT_KEYS) RepeatBehavior.HOLD else RepeatBehavior.NONE,
        )

    private val REPEAT_KEYS = setOf("↑", "↓", "←", "→", "PgUp", "PgDn")

    private fun combo(mod: ModifierKey, ch: String): ExtraKey =
        ExtraKey(
            id = UUID.randomUUID().toString(),
            label = mod.name + "+" + ch.uppercase(),
            type = ExtraKeyType.COMBO,
            text = ch,
            modifiers = listOf(mod),
        )

    private fun modKey(mod: ModifierKey): ExtraKey =
        ExtraKey(
            id = UUID.randomUUID().toString(),
            label = mod.name,
            type = ExtraKeyType.MODIFIER,
            modifiers = listOf(mod),
        )

    private fun tkey(id: String, name: String, label: String = KeySequences.displayLabel(name)) =
        ExtraKey(
            id = id,
            label = label,
            type = ExtraKeyType.TERMINAL_KEY,
            text = name,
            repeat = if (label in REPEAT_KEYS) RepeatBehavior.HOLD else RepeatBehavior.NONE,
        )

    private fun text(id: String, label: String, send: String, long: String? = null) =
        ExtraKey(
            id = id,
            label = label,
            type = ExtraKeyType.TEXT,
            text = send,
            longPressText = long,
        )

    private fun cmd(id: String, label: String, command: String, long: String? = null) =
        ExtraKey(
            id = id,
            label = label,
            type = ExtraKeyType.COMMAND,
            text = command,
            longPressText = long,
        )

    private fun default(): KeyLayout =
        KeyLayout(
            id = DEFAULT_ID,
            name = "Default",
            builtIn = true,
            keys =
                listOf(
                    tkey("$DEFAULT_ID-esc", "ESC"),
                    tkey("$DEFAULT_ID-tab", "TAB"),
                    modKey(ModifierKey.CTRL),
                    modKey(ModifierKey.ALT),
                    tkey("$DEFAULT_ID-up", "UP"),
                    tkey("$DEFAULT_ID-down", "DOWN"),
                    tkey("$DEFAULT_ID-left", "LEFT"),
                    tkey("$DEFAULT_ID-right", "RIGHT"),
                    tkey("$DEFAULT_ID-home", "HOME"),
                    tkey("$DEFAULT_ID-end", "END"),
                    text("$DEFAULT_ID-slash", "/", "/"),
                    text("$DEFAULT_ID-tilde", "~", "~"),
                    text("$DEFAULT_ID-pipe", "|", "|"),
                    text("$DEFAULT_ID-minus", "-", "-"),
                    text("$DEFAULT_ID-dollar", "$", "$"),
                    text("$DEFAULT_ID-dot", ".", "."),
                ),
        )

    private fun linux(): KeyLayout =
        KeyLayout(
            id = LINUX_ID,
            name = "Linux",
            builtIn = true,
            keys =
                listOf(
                    tkey("$LINUX_ID-esc", "ESC"),
                    tkey("$LINUX_ID-tab", "TAB"),
                    modKey(ModifierKey.CTRL),
                    modKey(ModifierKey.ALT),
                    tkey("$LINUX_ID-up", "UP"),
                    tkey("$LINUX_ID-down", "DOWN"),
                    tkey("$LINUX_ID-left", "LEFT"),
                    tkey("$LINUX_ID-right", "RIGHT"),
                    tkey("$LINUX_ID-home", "HOME"),
                    tkey("$LINUX_ID-end", "END"),
                    tkey("$LINUX_ID-pgup", "PGUP"),
                    tkey("$LINUX_ID-pgdn", "PGDN"),
                    text("$LINUX_ID-pipe", "|", "|"),
                    text("$LINUX_ID-tilde", "~", "~"),
                    text("$LINUX_ID-minus", "-", "-"),
                    text("$LINUX_ID-underscore", "_", "_"),
                ),
        )

    private fun developer(): KeyLayout =
        KeyLayout(
            id = DEVELOPER_ID,
            name = "Developer",
            builtIn = true,
            keys =
                listOf(
                    modKey(ModifierKey.CTRL),
                    tkey("$DEVELOPER_ID-tab", "TAB"),
                    tkey("$DEVELOPER_ID-up", "UP"),
                    tkey("$DEVELOPER_ID-down", "DOWN"),
                    text("$DEVELOPER_ID-pipe", "|", "|"),
                    text("$DEVELOPER_ID-amp", "&&", "&&"),
                    text("$DEVELOPER_ID-dquote", "\"", "\""),
                    text("$DEVELOPER_ID-squote", "'", "'"),
                    text("$DEVELOPER_ID-lt", "<", "<"),
                    text("$DEVELOPER_ID-gt", ">", ">"),
                    text("$DEVELOPER_ID-dollar", "$", "$"),
                    text("$DEVELOPER_ID-underscore", "_", "_"),
                    text("$DEVELOPER_ID-dotdot", "..", ".."),
                    combo(ModifierKey.CTRL, "c"),
                    combo(ModifierKey.CTRL, "d"),
                    combo(ModifierKey.CTRL, "z"),
                    combo(ModifierKey.CTRL, "l"),
                    combo(ModifierKey.CTRL, "r"),
                ),
        )

    private fun git(): KeyLayout =
        KeyLayout(
            id = GIT_ID,
            name = "Git",
            builtIn = true,
            keys =
                listOf(
                    cmd("$GIT_ID-status", "GIT", "git status", "git status -sb"),
                    cmd("$GIT_ID-add", "ADD", "git add -A"),
                    cmd("$GIT_ID-commit", "COMMIT", "git commit"),
                    cmd("$GIT_ID-push", "PUSH", "git push"),
                    cmd("$GIT_ID-pull", "PULL", "git pull"),
                    cmd("$GIT_ID-log", "LOG", "git log --oneline"),
                    text("$GIT_ID-dashdash", "--", "--"),
                    modKey(ModifierKey.CTRL),
                    combo(ModifierKey.CTRL, "c"),
                    tkey("$GIT_ID-tab", "TAB"),
                ),
        )

    private fun node(): KeyLayout =
        KeyLayout(
            id = NODE_ID,
            name = "Node.js",
            builtIn = true,
            keys =
                listOf(
                    cmd("$NODE_ID-dev", "DEV", "npm run dev"),
                    cmd("$NODE_ID-install", "INSTALL", "npm install"),
                    cmd("$NODE_ID-test", "TEST", "npm test"),
                    cmd("$NODE_ID-pnpm", "PNPM", "pnpm dev"),
                    text("$NODE_ID-pipe", "|", "|"),
                    text("$NODE_ID-amp", "&&", "&&"),
                    modKey(ModifierKey.CTRL),
                    combo(ModifierKey.CTRL, "c"),
                    tkey("$NODE_ID-up", "UP"),
                    tkey("$NODE_ID-down", "DOWN"),
                    tkey("$NODE_ID-tab", "TAB"),
                ),
        )

    private fun custom(): KeyLayout =
        KeyLayout(
            id = CUSTOM_ID,
            name = "Custom",
            builtIn = true,
            keys = emptyList(),
        )
}
