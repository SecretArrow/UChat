# UChat Terminal Architecture

The UChat terminal is a **real native terminal emulator** — pure Kotlin parser, buffer and
renderer. There is no WebView and no JavaScript bridge anywhere in the pipeline. The UI never
assumes what kind of session it displays: today it is a local Ubuntu 24.04 shell through proot +
a native pty, tomorrow it can be SSH — without redesigning a single screen.

```
┌──────────────────────────────────────────────────────────────────┐
│ 4. Terminal UI (Compose)          ui/terminal/TerminalScreenView │
│    session tabs · close/stop · find · paste/copy · scroll button │
├──────────────────────────────────────────────────────────────────┤
│ 3. Renderer (Compose Canvas)      terminal/render/TerminalView   │
│    GridTransform placement · text/box-drawing runs · cursor ·    │
│    selection · scrollback drag/fling · hidden IME sentinel field │
├──────────────────────────────────────────────────────────────────┤
│ 2. Controller                     terminal/TerminalController    │
│    pty → emulator.feed → renderTick · responses → pty · replay   │
├──────────────────────────────────────────────────────────────────┤
│ 1. Emulator + buffer              terminal/emulator/*            │
│    TerminalEmulator (VT100/xterm state machine) · TerminalBuffer │
│    (cell grid + scrollback) · WcWidth · TerminalColors           │
├──────────────────────────────────────────────────────────────────┤
│ 8. Session backends               terminal/backend/*             │
│    TerminalBackend → PtyBackend (proot pty today, SSH later) ·   │
│    PtySession reader loop · ProcessManager · foreground service  │
├──────────────────────────────────────────────────────────────────┤
│ 5-7. Extra keys, editor, settings  terminal/keys/* · ui/terminal │
└──────────────────────────────────────────────────────────────────┘
```

## Data flow

Output (backend → screen):

```
Pty.nativeRead (reader coroutine, Dispatchers.IO)
  → PtySession.outputListeners (CopyOnWriteArrayList, many listeners)
      ├─ ProcessManager tap → TerminalReplayCache.offer(sessionId, bytes)   [always]
      └─ TerminalController listener → TerminalEmulator.feed(bytes)
             → TerminalBuffer cell grid + scrollback
             → controller.renderTick = buffer.generation   (Compose snapshot state;
               the Canvas draw pass reads it, so Compose coalesces bursts per frame)
```

Input (user → backend):

```
Android IME    → hidden sentinel TextField diff → controller.writeText → backend.write
Hardware keys  → KeyHandler (CSI/SS3/CTRL) → controller.write → backend.write
Extra key tap  → KeySequences.encode(key, modifiers) → controller.write → backend.write
Paste          → controller.paste (bracketed-paste aware, DECSET 2004)
```

Device reports (emulator → host) — **the emulator replies and the controller routes the answer
back to the pty**. Programs like vim, fish or zsh block waiting for these; a dropped reply hangs
them. This path is wired in `TerminalController.init` via `emulator.callbacks.onResponse` and
reuses the exact same `write()` path as user input. With no backend attached a reply is dropped
silently.

| Query from host                | Reply emitted by `TerminalEmulator`        |
|--------------------------------|--------------------------------------------|
| `CSI 6 n` (CPR / DSR 6)        | `ESC[<row>;<col>R` (1-based cursor)         |
| `CSI 5 n` (status)             | `ESC[0n`                                    |
| `CSI c` (primary DA)           | `ESC[?6c` (VT102)                           |
| `CSI > c` (secondary DA)       | `ESC[>0;276;0c`                             |
| `CSI 18 t` (char cell size)    | `ESC[8;<rows>;<cols>t`                      |
| `CSI ?1004h` + focus change    | `ESC[I` / `ESC[O` (via `reportFocus`)       |
| `OSC 10` / `OSC 11` + `?`      | current default fg/bg                       |

## Grid placement (GridTransform)

Pure geometry, unit tested; the renderer applies `translate(offset) + scale`, and
`cellAt()` inverts it for selection so hit-testing stays exact while scaled.

- **Fit screen** (default): the grid is derived from the viewport at 1:1 — identity transform.
- **Fit screen, very large font**: if fewer than `TerminalController.MIN_COLS` (20) columns fit,
  growing the buffer would push the right columns off-screen (there is no horizontal scroll).
  Instead the grid is raised to 20 columns and uniformly scaled DOWN and centered —
  `GridTransform.solveFitScreen` — so the full width is always visible.
- **Fixed size**: the emulator uses the exact user-picked columns/rows; a grid larger than the
  screen is scaled down (never up, to keep glyphs crisp) and centered via
  `GridTransform.solve`.

## Cursor rendering

- DECSCUSR shapes: `0/1` blinking block, `2/3` steady block, `4/5` underline, `6/7` bar.
- Blinking is gated: only when the app setting allows it, the terminal has focus, AND the active
  shape is a blinking variant — steady shapes stay solid.
- Wide characters (CJK/emoji) occupy two cells; the cursor snaps to the head cell and stretches a
  two-cell block, so it never looks offset from the glyph. Unfocused terminals draw a hollow rect.

## Key design decisions

- **No JS anywhere.** Bytes go straight from Kotlin to the pty and back — lowest latency.
- **The user is never yanked to the bottom.** While the user reads scrollback the viewport is
  anchored; incoming output shifts the anchor instead of forcing them down. The scroll-to-bottom
  button appears only while reading history.
- **Detached sessions keep context.** `TerminalReplayCache` stores the last 256 KiB per session;
  re-attaching replays it through the parser, so colors and cursor position are restored.
- **Underlines/strikethrough follow the wcwidth geometry.** Run width is computed with
  `WcWidth.stringWidth` (wide = 2 cells, combining = 0), matching how the buffer allocated cells;
  continuation cells are never drawn twice.
- **Keys are data.** `KeySequences` (pure Kotlin, unit tested) turns `ExtraKey` records into exact
  bytes: CTRL+C → `0x03`, CTRL+UP → `ESC[1;5A`.

## Adding a new backend (e.g. SSH)

Implement `TerminalBackend` (5 methods + 2 properties), register the session wherever it is
created, and pass it to `TerminalController.attach()`. Device-report responses, replay, resize
and exit handling come for free. No changes in UI, extra keys, editor, settings or rendering.

## Unit tests

| Test class | Covers |
|---|---|
| `TerminalEmulatorTest` | parser, wrapping, wide chars, margins, modes, DECSCUSR, CPR/DA/DSR replies (byte exact) |
| `TerminalControllerTest` | device reports reach the backend write path; replies dropped with no backend |
| `GridTransformTest` | fit-screen identity, fixed scale-down/centering, MIN_COLS auto-fit, inverse mapping |
| `KeyHandlerTest` / `KeySequencesTest` | hardware-key encoding, extra-key byte encoding |
| `WcWidthTest` / `TerminalColorsTest` | wcwidth table, palette resolution |
| `ModifierStateHolderTest` / `ExtraKeysStoreTest` / `DefaultLayoutsTest` | modifier machine, layout persistence |
