# UChat Terminal Architecture

The UChat terminal is a real terminal emulator stack, built as eight separated layers. The UI
never assumes what kind of session it displays: today it is a local Ubuntu 24.04 shell through
proot + a native pty, tomorrow it can be SSH — without redesigning a single screen.

```
┌────────────────────────────────────────────────────────────────┐
│ 4. Terminal UI (Compose)         ui/terminal/TerminalScreenView │
│    session tabs · quick scroll-to-bottom · find · paste/copy    │
├────────────────────────────────────────────────────────────────┤
│ 5. Extra-key system              terminal/keys/*                │
│    ExtraKey · KeySequences · ModifierStateHolder                │
│    ExtraKeysStore (layouts CRUD) · ExtraKeysToolbar             │
│ 6. Extra-key editor              ui/terminal/ExtraKeysEditor    │
├────────────────────────────────────────────────────────────────┤
│ 7. Terminal settings             ui/terminal/TerminalSettings   │
│    font · key size · toolbar position/visibility · haptics      │
│    key repeat · modifier mode · cursor blink · scroll button    │
├────────────────────────────────────────────────────────────────┤
│ 3. Renderer (xterm.js WebView)   assets/terminal/index.html     │
│    ANSI/escape parsing · Unicode + box-drawing glyphs           │
│    blinking cursor · 10 000-line scrollback · smooth scroll     │
├────────────────────────────────────────────────────────────────┤
│ 2. Buffer                        terminal/TerminalReplayCache   │
│    bounded 256 KiB per-session replay, fed by ProcessManager    │
├────────────────────────────────────────────────────────────────┤
│ 1. Emulator/parser               xterm.js (MIT, vendored)       │
├────────────────────────────────────────────────────────────────┤
│ 8. Session lifecycle             terminal/backend/*             │
│    TerminalBackend (interface)  → PtyBackend (proot pty today, │
│    SSH backend later) · ProcessManager · foreground service     │
└────────────────────────────────────────────────────────────────┘
```

## Data flow

Output (backend → screen):

```
pty read thread
  → PtySession.outputListeners (CopyOnWriteArrayList, many listeners)
      ├─ ProcessManager tap → TerminalReplayCache.offer(sessionId, bytes)   [always]
      └─ TerminalController listener → coalescing queue (16 ms flush)       [screen attached]
             → WebView.evaluateJavascript("UChatTerm.write(json)")          [main thread]
```

Input (user → backend):

```
Android IME → WebView/xterm.js → UChatTerminal.onTerminalInput → backend.write
Extra key tap → KeySequences.encode(key, stickyModifiers) → controller.sendBytes → backend.write
Paste        → controller.paste → UChatTerm.paste (bracketed paste aware)
```

## Key design decisions

- **Input never detours through JS.** Extra-key bytes go straight from Kotlin to the pty —
  lowest possible latency while output is streaming.
- **Output is coalesced.** Chunks queue up and flush every 16 ms (max 128 KiB per flush) so a
  `cat huge.log` burst paints smoothly instead of flooding the JS bridge.
- **The user is never yanked to the bottom.** The xterm viewport reports its own scroll state
  (`onScrollStateChanged(atBottom)`); the quick scroll-to-bottom button appears only while
  reading history.
- **Detached sessions keep context.** The replay cache stores the last 256 KiB per session even
  when the terminal screen is closed; switching sessions clears + replays it.
- **Keys are data.** An `ExtraKey` is a serializable record; `KeySequences` (pure Kotlin, unit
  tested) turns it into exact bytes: CTRL+C → `0x03`, CTRL+UP → `ESC[1;5A`, `\e[5~` → `ESC[5~`.
- **Modifiers behave like real terminal modifiers.** Momentary / latched / locked modes,
  long-press hard-lock, per-key haptics and repeat.

## Adding a new backend (e.g. SSH)

Implement `TerminalBackend` (5 methods + 2 properties), register the session wherever it is
created, and pass it to `TerminalController.attach()`. No changes in UI, extra keys, editor,
settings or rendering are required.

## Unit tests

| Test class | Covers |
|---|---|
| `KeySequencesTest` | text/UTF-8, CTRL/ALT mapping, CSI, SS3, modifier parameters, escape parsing, combos, long-press |
| `ModifierStateHolderTest` | momentary/latch/lock cycles, consume semantics, hard lock |
| `ExtraKeysStoreTest` | defaults, CRUD, reorder, duplicate, persistence round-trip, corrupt-file recovery |
| `DefaultLayoutsTest` | 6 layouts, spec examples (GIT key, `npm run dev`, CTRL+C), unique ids |
