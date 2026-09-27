package com.uchat.android.terminal.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uchat.android.R
import com.uchat.android.terminal.TerminalController
import com.uchat.android.terminal.emulator.TerminalBuffer
import com.uchat.android.terminal.emulator.TerminalColors
import com.uchat.android.terminal.emulator.TerminalEmulator
import com.uchat.android.terminal.emulator.WcWidth
import com.uchat.android.terminal.input.KeyHandler
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

/** Font cell metrics in pixels (recomputed when the font size changes). */
private class CellMetrics {
    var cellWidth = 1f
    var cellHeight = 1f
    var ascent = 0f
}

/** Breathing room around the grid so text never touches the screen edge. */
private const val HORIZONTAL_INSET_DP = 6
private const val VERTICAL_INSET_DP = 4

/** One endpoint of a text selection, in absolute viewport coordinates. */
private data class SelCell(val row: Int, val col: Int)

private val BACKSPACE_BYTE = byteArrayOf(0x7f)

/** Sentinel char kept inside the hidden IME field so backspace produces an observable edit. */
private const val IME_SENTINEL = "\uE000"

/**
 * The native terminal renderer + input surface — no WebView involved.
 *
 * Rendering: one Compose [Canvas] walking the [TerminalBuffer] cell grid, painting text runs,
 * vector box-drawing, cursor and selection with android.graphics (Termux's proven approach).
 * Redraws are driven by [TerminalController.renderTick] read inside the draw pass.
 *
 * Scrolling: drag + fling. While the user reads history, incoming output anchors the viewport;
 * [TerminalController.requestScrollToBottom] snaps back to the live bottom.
 *
 * Input:
 * - soft keyboard: an invisible sentinel [BasicTextField] captures IME input (composition and
 *   deletes included) and forwards the diff to the pty
 * - hardware/bluetooth: [KeyHandler] intercepts control keys; plain chars flow through the IME path
 *   so every keyboard layout keeps working
 */
@Composable
fun TerminalView(
    controller: TerminalController,
    fontSizeSp: Int,
    cursorBlinkEnabled: Boolean,
    fitScreen: Boolean,
    fixedCols: Int,
    fixedRows: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    val insetX = with(density) { HORIZONTAL_INSET_DP.dp.toPx() }
    val insetY = with(density) { VERTICAL_INSET_DP.dp.toPx() }

    val metrics = remember { CellMetrics() }
    var scrollPx by remember { mutableFloatStateOf(0f) }
    var cursorOn by remember { mutableStateOf(true) }
    var selectionAnchor by remember { mutableStateOf<SelCell?>(null) }
    var selectionFocus by remember { mutableStateOf<SelCell?>(null) }
    var lastTotalLines by remember { mutableIntStateOf(-1) }

    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    val textPaint =
        remember(fontSizePx) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = fontSizePx
                val fm = fontMetrics
                metrics.ascent = fm.ascent
                metrics.cellHeight = ceil(fm.descent - fm.ascent).coerceAtLeast(1f)
                // EXACT float advance — ceil() here made glyphs drift progressively against the
                // cell grid (background rects, cursor, box drawing), the main cause of ragged
                // terminal text. With the exact advance every run aligns to the grid perfectly.
                metrics.cellWidth = measureText("M").coerceAtLeast(1f)
            }
        }

    // Grid placement: fit-to-screen (identity) or the fixed grid scaled/centered into view.
    var viewportSize by remember { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }
    var transform by remember { mutableStateOf(GridTransform.IDENTITY) }

    fun applyGrid(size: androidx.compose.ui.unit.IntSize) {
        if (size.width <= 0 || size.height <= 0) return
        val vw = size.width.toFloat()
        val vh = size.height.toFloat()
        transform =
            GridTransform.solve(
                viewportWidth = vw,
                viewportHeight = vh,
                cols = fixedCols,
                rows = fixedRows,
                cellWidth = metrics.cellWidth,
                cellHeight = metrics.cellHeight,
                fitScreen = fitScreen,
            )
        if (fitScreen) {
            val cols = floor((vw - 2 * insetX) / metrics.cellWidth).toInt().coerceAtLeast(2)
            val rows = floor((vh - 2 * insetY) / metrics.cellHeight).toInt().coerceAtLeast(1)
            controller.onGridViewport(cols, rows)
        } else {
            controller.onGridViewport(fixedCols, fixedRows)
        }
    }

    // Re-apply the grid whenever the geometry settings or font change.
    LaunchedEffect(fontSizePx, fitScreen, fixedCols, fixedRows) {
        viewportSize?.let { applyGrid(it) }
    }

    fun scrollLines(): Int = floor(scrollPx / metrics.cellHeight).toInt()

    fun maxScrollPx(): Float = controller.maxScrollLines() * metrics.cellHeight

    fun viewportTop(): Int =
        synchronized(controller.buffer.lock) {
            val total = controller.buffer.totalLines
            (total - controller.buffer.rows - scrollLines()).coerceIn(
                0,
                (total - controller.buffer.rows).coerceAtLeast(0)
            )
        }

    fun cellAt(offset: Offset): SelCell {
        val t = transform
        val x = (offset.x - t.offsetX - insetX) / t.scale
        val y = (offset.y - t.offsetY - insetY) / t.scale
        val col = floor(x / metrics.cellWidth).toInt().coerceAtLeast(0)
        val row = floor(y / metrics.cellHeight).toInt().coerceAtLeast(0)
        return SelCell(viewportTop() + row, col)
    }

    fun clearSelection() {
        selectionAnchor = null
        selectionFocus = null
        controller.reportSelection(null)
    }

    // ---------------------------------------------------------------- scrolling

    val scrollableState = rememberScrollableState { delta ->
        scrollPx = (scrollPx - delta).coerceIn(0f, maxScrollPx().coerceAtLeast(0f))
        controller.reportViewport(atBottom = scrollPx <= metrics.cellHeight / 2f)
        delta
    }

    // Anchor the viewport while reading history; stay glued to the live bottom otherwise.
    LaunchedEffect(controller.renderTick) {
        val total = synchronized(controller.buffer.lock) { controller.buffer.totalLines }
        if (lastTotalLines >= 0 && total != lastTotalLines) {
            val deltaLines = total - lastTotalLines
            scrollPx =
                if (scrollPx > metrics.cellHeight / 2f) {
                    (scrollPx + deltaLines * metrics.cellHeight).coerceIn(0f, maxScrollPx())
                } else {
                    0f
                }
            controller.reportViewport(atBottom = scrollPx <= metrics.cellHeight / 2f)
        }
        lastTotalLines = total
        cursorOn = true
    }

    LaunchedEffect(controller.scrollRequests) { if (controller.scrollRequests > 0) scrollPx = 0f }

    LaunchedEffect(controller.keyboardRequests) {
        if (controller.keyboardRequests > 0) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    // Search hit: bring the row into view.
    LaunchedEffect(controller.searchHit) {
        val hit = controller.searchHit ?: return@LaunchedEffect
        val rows =
            synchronized(controller.buffer.lock) {
                controller.buffer.rows to controller.buffer.totalLines
            }
        val targetTop = (hit - rows.first / 2).coerceAtLeast(0)
        val maxTop = (rows.second - rows.first).coerceAtLeast(0)
        scrollPx = ((maxTop - targetTop).coerceAtLeast(0)) * metrics.cellHeight
    }

    // Cursor blink.
    LaunchedEffect(cursorBlinkEnabled, hasFocus) {
        while (true) {
            delay(530)
            if (cursorBlinkEnabled && controller.emulator.cursorBlinking && hasFocus) {
                cursorOn = !cursorOn
            }
        }
    }

    // ---------------------------------------------------------------- input plumbing

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val sb = StringBuilder()
        for (c in text) {
            when {
                c == '\n' || c == '\r' -> sb.append('\r')
                c == '\t' -> sb.append('\t')
                c.code >= 0x20 || c.code == 0x09 -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) controller.writeText(sb.toString())
    }

    fun sendBackspaces(count: Int) {
        repeat(count.coerceAtMost(256)) { controller.write(BACKSPACE_BYTE) }
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                if (native.keyCode == android.view.KeyEvent.KEYCODE_BACK)
                    return@onPreviewKeyEvent false
                val info =
                    KeyHandler.fromEvent(
                        keyCode = native.keyCode,
                        ctrl = native.isCtrlPressed,
                        alt = native.isAltPressed,
                        shift = native.isShiftPressed,
                        meta = native.isMetaPressed,
                        repeatCount = native.repeatCount,
                    )
                val bytes =
                    KeyHandler.encode(
                        info,
                        controller.emulator.appCursor,
                        controller.emulator.appKeypad
                    )
                if (bytes != null) {
                    controller.write(bytes)
                    true
                } else {
                    false
                }
            }
            .scrollable(
                state = scrollableState,
                orientation = Orientation.Vertical,
                flingBehavior = ScrollableDefaults.flingBehavior(),
            ),
    ) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { size ->
                    viewportSize = size
                    applyGrid(size)
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            clearSelection()
                            runCatching { focusRequester.requestFocus() }
                            keyboard?.show()
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            selectionAnchor = cellAt(offset)
                            selectionFocus = selectionAnchor
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            selectionFocus = cellAt(change.position)
                        },
                        onDragEnd = {
                            val a = selectionAnchor
                            val b = selectionFocus
                            if (a != null && b != null) {
                                val text =
                                    synchronized(controller.buffer.lock) {
                                        controller.buffer.selectionText(a.row, a.col, b.row, b.col)
                                    }
                                controller.reportSelection(text.ifBlank { null })
                            }
                        },
                        onDragCancel = { clearSelection() },
                    )
                },
        ) {
            drawIntoCanvas { composeCanvas ->
                controller.renderTick // snapshot read inside the draw pass → redraw on change
                val canvas = composeCanvas.nativeCanvas
                synchronized(controller.buffer.lock) {
                    val t = transform
                    canvas.save()
                    // Center/scale the grid (fixed mode) and keep text off the screen edges.
                    canvas.translate(t.offsetX + insetX, t.offsetY + insetY)
                    if (t.scale != 1f) canvas.scale(t.scale, t.scale)
                    paintTerminal(
                        canvas,
                        controller,
                        metrics,
                        scrollLines(),
                        cursorOn,
                        hasFocus,
                        selectionAnchor,
                        selectionFocus,
                        textPaint,
                    )
                    canvas.restore()
                }
            }
        }

        // Selection quick actions.
        val selText = controller.selectionText
        if (selText != null) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(selText))
                        clearSelection()
                    },
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.terminal_copy))
                }
                androidx.compose.material3.TextButton(onClick = { clearSelection() }) {
                    androidx.compose.material3.Text(stringResource(R.string.action_cancel))
                }
            }
        }

        // Invisible IME capture surface.
        var fieldValue by remember { mutableStateOf(TextFieldValue(IME_SENTINEL)) }
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                val old = fieldValue.text
                if (new.text == old) return@BasicTextField
                var p = 0
                while (p < old.length && p < new.text.length && old[p] == new.text[p]) p++
                var s = 0
                while (
                    s < old.length - p &&
                        s < new.text.length - p &&
                        old[old.length - 1 - s] == new.text[new.text.length - 1 - s]
                ) {
                    s++
                }
                val deleted = old.length - p - s
                val inserted = new.text.substring(p, new.text.length - s)
                sendBackspaces(deleted)
                sendText(inserted)
                fieldValue = TextFieldValue(IME_SENTINEL)
            },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 10.sp),
            cursorBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
            modifier =
                Modifier.size(1.dp)
                    .offset(y = (-1).dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        val was = hasFocus
                        hasFocus = state.isFocused
                        if (hasFocus != was) controller.emulator.reportFocus(hasFocus)
                    },
        )
    }
}

/**
 * Paint the visible window of the buffer. Runs inside the buffer lock.
 *
 * Pass structure per row: selection rect → per-run background rect → text run (+underline/strike) →
 * vector box-drawing glyphs → row-level search highlight → cursor.
 */
private fun paintTerminal(
    canvas: Canvas,
    controller: TerminalController,
    metrics: CellMetrics,
    scrollLines: Int,
    cursorOn: Boolean,
    hasFocus: Boolean,
    selectionAnchor: SelCell?,
    selectionFocus: SelCell?,
    paint: Paint,
) {
    val buffer = controller.buffer
    val cols = buffer.cols
    val rows = buffer.rows
    val cellW = metrics.cellWidth
    val cellH = metrics.cellHeight
    if (cellW <= 0f || cellH <= 0f) return

    canvas.drawColor(TerminalColors.BG_DEFAULT)

    val total = buffer.totalLines
    val maxTop = (total - rows).coerceAtLeast(0)
    val viewportTop = (total - rows - scrollLines).coerceIn(0, maxTop)

    // Selection bounds (absolute rows).
    val selA = selectionAnchor
    val selB = selectionFocus
    var selRowStart = -1
    var selRowEnd = -1
    var selColStart = -1
    var selColEnd = -1
    if (selA != null && selB != null) {
        selRowStart = min(selA.row, selB.row)
        selRowEnd = max(selA.row, selB.row)
        if (selA.row == selB.row) {
            selColStart = min(selA.col, selB.col)
            selColEnd = max(selA.col, selB.col)
        } else {
            selColStart = if (selA.row < selB.row) selA.col else selB.col
            selColEnd = if (selA.row < selB.row) selB.col else selA.col
        }
    }

    val run = StringBuilder()
    val searchHit = controller.searchHit

    for (row in 0 until rows) {
        val viewIndex = viewportTop + row
        val line = buffer.lineAt(viewIndex) ?: continue
        val yTop = row * cellH
        val baseline = yTop - metrics.ascent
        val lineCols = min(cols, line.chars.size)

        // Selection highlight for this row (under everything else).
        if (viewIndex in selRowStart..selRowEnd) {
            val from = if (viewIndex == selRowStart) selColStart.coerceAtLeast(0) else 0
            val to = if (viewIndex == selRowEnd) (selColEnd + 1).coerceAtMost(cols) else cols
            if (to > from) {
                paint.style = Paint.Style.FILL
                paint.color = TerminalColors.SELECTION_COLOR
                canvas.drawRect(from * cellW, yTop, to * cellW, yTop + cellH, paint)
            }
        }

        var runStart = -1
        var runFg = 0
        var runBg = 0
        var runFlags = 0

        fun flushRun(endExclusive: Int) {
            if (runStart < 0 || endExclusive <= runStart) {
                runStart = -1
                return
            }
            val text = run.toString()
            run.setLength(0)
            var fg = runFg
            val bg = runBg
            val flags = runFlags
            // Bold brightens base palette colors (xterm behaviour).
            if (flags and TerminalBuffer.Attr.FLAG_BOLD != 0 && fg in 0..7) fg += 8
            var fgArgb = resolveColor(fg, controller)
            var bgArgb = resolveColor(bg, controller)
            if (flags and TerminalBuffer.Attr.FLAG_REVERSE != 0) {
                val t = fgArgb
                fgArgb = bgArgb
                bgArgb = t
            }
            if (
                bg != TerminalColors.DEFAULT_BG || flags and TerminalBuffer.Attr.FLAG_REVERSE != 0
            ) {
                paint.style = Paint.Style.FILL
                paint.color = bgArgb
                canvas.drawRect(runStart * cellW, yTop, endExclusive * cellW, yTop + cellH, paint)
            }
            drawTextRun(
                canvas,
                paint,
                text,
                runStart * cellW,
                baseline,
                cellW,
                cellH,
                fgArgb,
                flags
            )
            runStart = -1
        }

        var c = 0
        while (c < lineCols) {
            val ch = line.chars[c]
            if (buffer.isContinuationCell(ch)) {
                c++
                continue
            }
            val style = line.styles[c]
            val fg = TerminalBuffer.Attr.fg(style)
            val bg = TerminalBuffer.Attr.bg(style)
            val flags = TerminalBuffer.Attr.flags(style)

            if (BoxDrawing.handles(ch)) {
                flushRun(c)
                paint.color =
                    if (flags and TerminalBuffer.Attr.FLAG_REVERSE != 0) {
                        resolveColor(bg, controller)
                    } else {
                        var col0 = fg
                        if (flags and TerminalBuffer.Attr.FLAG_BOLD != 0 && col0 in 0..7) col0 += 8
                        resolveColor(col0, controller)
                    }
                BoxDrawing.draw(canvas, ch, c * cellW, yTop, (c + 1) * cellW, yTop + cellH, paint)
                c++
                continue
            }

            if (runStart < 0 || fg != runFg || bg != runBg || flags != runFlags) {
                flushRun(c)
                runStart = c
                runFg = fg
                runBg = bg
                runFlags = flags
            }
            run.append(ch)
            // Surrogate pairs: keep both halves inside the same run.
            if (
                Character.isHighSurrogate(ch) &&
                    c + 1 < lineCols &&
                    Character.isLowSurrogate(line.chars[c + 1])
            ) {
                run.append(line.chars[c + 1])
                c += 2
            } else {
                c++
            }
        }
        flushRun(lineCols)

        if (searchHit != null && searchHit == viewIndex) {
            paint.style = Paint.Style.FILL
            paint.color = SEARCH_HIGHLIGHT
            canvas.drawRect(0f, yTop, cols * cellW, yTop + cellH, paint)
        }
    }

    drawCursor(canvas, controller, metrics, viewportTop, cursorOn, hasFocus, paint)
}

/** Draw the cursor block (or hollow rect when unfocused) plus its glyph. */
private fun drawCursor(
    canvas: Canvas,
    controller: TerminalController,
    metrics: CellMetrics,
    viewportTop: Int,
    cursorOn: Boolean,
    hasFocus: Boolean,
    paint: Paint,
) {
    val emulator = controller.emulator
    if (!emulator.cursorVisible) return
    val buffer = controller.buffer
    val cursorScreenRow = buffer.absoluteRowOf(buffer.cursorRow) - viewportTop
    if (cursorScreenRow < 0 || cursorScreenRow >= buffer.rows) return
    // Blink only when the app allows blinking AND the active DECSCUSR shape is a blinking
    // variant — steady shapes (3/5/7) must stay solid or the cursor appears to jump around.
    if (hasFocus && !cursorOn && emulator.cursorBlinking && emulator.cursorShapeBlinks()) return

    val cols = buffer.cols
    val cellW = metrics.cellWidth
    val cellH = metrics.cellHeight
    // Keep the block glued to the glyph it covers: a wide (CJK/emoji) char occupies two
    // cells, and the cursor may park on its continuation half — snap back to the head cell
    // and stretch the block over both cells so it never looks offset from the text.
    val screenLine = buffer.currentScreen.getOrNull(buffer.cursorRow)
    var anchorCol = buffer.cursorCol.coerceIn(0, cols - 1)
    var blockW = cellW
    if (screenLine != null && anchorCol < screenLine.chars.size) {
        val under = screenLine.chars[anchorCol]
        if (buffer.isContinuationCell(under) && anchorCol > 0) anchorCol--
        val head = screenLine.chars[anchorCol]
        if (!buffer.isContinuationCell(head)) {
            val w = WcWidth.widthOf(head.toString())
            if (w >= 2 && anchorCol + 1 < cols) blockW = cellW * 2f
        }
    }
    val cx = anchorCol * cellW
    val cyTop = cursorScreenRow * cellH

    paint.style = if (hasFocus) Paint.Style.FILL else Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.color = TerminalColors.CURSOR_COLOR
    // DECSCUSR mapping: 4/5 underline, 6/7 bar, everything else (0..3) is a block.
    when (emulator.cursorShape) {
        4,
        5 -> canvas.drawRect(cx, cyTop + cellH - cellH / 5f, cx + blockW, cyTop + cellH, paint)
        6,
        7 -> canvas.drawRect(cx, cyTop, cx + (cellW / 5f).coerceAtLeast(2f), cyTop + cellH, paint)
        else -> canvas.drawRect(cx, cyTop, cx + blockW, cyTop + cellH, paint)
    }

    if (!hasFocus) return
    if (screenLine == null) return
    if (anchorCol >= screenLine.chars.size) return
    val ch = screenLine.chars[anchorCol]
    if (buffer.isContinuationCell(ch) || BoxDrawing.handles(ch)) return
    val glyph =
        if (
            Character.isHighSurrogate(ch) &&
                anchorCol + 1 < screenLine.chars.size &&
                Character.isLowSurrogate(screenLine.chars[anchorCol + 1])
        ) {
            "" + ch + screenLine.chars[anchorCol + 1]
        } else {
            ch.toString()
        }
    paint.style = Paint.Style.FILL
    paint.color = run {
        val cellBg = TerminalBuffer.Attr.bg(screenLine.styles[anchorCol])
        if (cellBg == TerminalColors.DEFAULT_BG) TerminalColors.BG_DEFAULT
        else resolveColor(cellBg, controller)
    }
    canvas.drawText(glyph, cx, cursorScreenRow * cellH - metrics.ascent, paint)
}

/** Draw one text run with the given resolved foreground. */
private fun drawTextRun(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    cellW: Float,
    cellH: Float,
    fgArgb: Int,
    flags: Int,
) {
    val concealed = flags and TerminalBuffer.Attr.FLAG_CONCEAL != 0
    if (!concealed && text.isNotBlank()) {
        paint.style = Paint.Style.FILL
        paint.color = fgArgb
        paint.isFakeBoldText = flags and TerminalBuffer.Attr.FLAG_BOLD != 0
        paint.textSkewX = if (flags and TerminalBuffer.Attr.FLAG_ITALIC != 0) -0.22f else 0f
        paint.alpha = if (flags and TerminalBuffer.Attr.FLAG_DIM != 0) 160 else 255
        canvas.drawText(text, x, baseline, paint)
        paint.textSkewX = 0f
        paint.isFakeBoldText = false
        paint.alpha = 255
    }
    val thickness = 1.5f.coerceAtLeast(cellH / 24f)
    if (flags and TerminalBuffer.Attr.FLAG_UNDERLINE != 0) {
        paint.style = Paint.Style.FILL
        paint.color = fgArgb
        val uy = baseline + cellH * 0.08f
        canvas.drawRect(x, uy, x + text.length * cellW, uy + thickness, paint)
    }
    if (flags and TerminalBuffer.Attr.FLAG_STRIKE != 0) {
        paint.style = Paint.Style.FILL
        paint.color = fgArgb
        val sy = baseline - cellH * 0.28f
        canvas.drawRect(x, sy, x + text.length * cellW, sy + thickness, paint)
    }
}

/** Resolve a stored palette index (or DIRECT_RGB packing) to an ARGB int. */
internal fun resolveColor(index: Int, controller: TerminalController): Int {
    if (index >= TerminalEmulator.DIRECT_RGB) {
        val rel = index - TerminalEmulator.DIRECT_RGB
        val r = ((rel shr 6) and 0x7) * 17
        val g = ((rel shr 3) and 0x7) * 17
        val b = (rel and 0x7) * 17
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
    val ov = controller.emulator.paletteOverride
    if (ov != null && index < 256) {
        val c = ov[index]
        if (c != 0) return c
    }
    return TerminalColors.resolve(index)
}

private val SEARCH_HIGHLIGHT = 0x337DEBC2.toInt()
