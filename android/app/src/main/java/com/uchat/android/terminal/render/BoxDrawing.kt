package com.uchat.android.terminal.render

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Vector painter for box-drawing, block and shade glyphs (U+2500–U+259F).
 *
 * Most Android monospace fonts have broken or missing box-drawing coverage; drawing these as
 * lines/fractions of the cell guarantees htop, vim, tmux and git diff separators line up perfectly
 * — the single most visible "real terminal" tell.
 */
object BoxDrawing {

    /** Whether [ch] is painted by this painter. */
    fun handles(ch: Char): Boolean = ch.code in 0x2500..0x259F

    /**
     * Paint [ch] inside the cell rectangle [left, top, right, bottom] using [paint] (color only;
     * stroke settings are applied here).
     */
    fun draw(
        canvas: Canvas,
        ch: Char,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paint: Paint,
    ) {
        val w = right - left
        val h = bottom - top
        val cx = left + w / 2f
        val cy = top + h / 2f
        val thin = (w / 8f).coerceAtLeast(1f)
        val heavy = thin * 2.4f
        val eighth = h / 8f
        val halfW = w / 8f

        fun hline(y: Float, thickness: Float, x1: Float = left, x2: Float = right) {
            paint.style = Paint.Style.FILL
            canvas.drawRect(x1, y - thickness / 2f, x2, y + thickness / 2f, paint)
        }

        fun vline(x: Float, thickness: Float, y1: Float = top, y2: Float = bottom) {
            paint.style = Paint.Style.FILL
            canvas.drawRect(x - thickness / 2f, y1, x + thickness / 2f, y2, paint)
        }

        fun fill(x1: Float, y1: Float, x2: Float, y2: Float) {
            paint.style = Paint.Style.FILL
            canvas.drawRect(x1, y1, x2, y2, paint)
        }

        when (ch.code) {
            0x2500 -> hline(cy, thin) // ─
            0x2501 -> hline(cy, heavy) // ━
            0x2502 -> vline(cx, thin) // │
            0x2503 -> vline(cx, heavy) // ┃
            0x250c -> { // ┌
                hline(cy, thin, cx, right)
                vline(cx, thin, cy, bottom)
            }
            0x250d -> { // ┍
                hline(cy, heavy, cx, right)
                vline(cx, thin, cy, bottom)
            }
            0x250e -> { // ┎
                hline(cy, thin, cx, right)
                vline(cx, heavy, cy, bottom)
            }
            0x250f -> { // ┏
                hline(cy, heavy, cx, right)
                vline(cx, heavy, cy, bottom)
            }
            0x2510 -> { // ┐
                hline(cy, thin, left, cx)
                vline(cx, thin, cy, bottom)
            }
            0x2511 -> { // ┑
                hline(cy, heavy, left, cx)
                vline(cx, thin, cy, bottom)
            }
            0x2512 -> { // ┒
                hline(cy, thin, left, cx)
                vline(cx, heavy, cy, bottom)
            }
            0x2513 -> { // ┓
                hline(cy, heavy, left, cx)
                vline(cx, heavy, cy, bottom)
            }
            0x2514 -> { // └
                hline(cy, thin, cx, right)
                vline(cx, thin, top, cy)
            }
            0x2515 -> { // ┕
                hline(cy, heavy, cx, right)
                vline(cx, thin, top, cy)
            }
            0x2516 -> { // ┖
                hline(cy, thin, cx, right)
                vline(cx, heavy, top, cy)
            }
            0x2517 -> { // ┗
                hline(cy, heavy, cx, right)
                vline(cx, heavy, top, cy)
            }
            0x2518 -> { // ┘
                hline(cy, thin, left, cx)
                vline(cx, thin, top, cy)
            }
            0x2519 -> { // ┙
                hline(cy, heavy, left, cx)
                vline(cx, thin, top, cy)
            }
            0x251a -> { // ┚
                hline(cy, thin, left, cx)
                vline(cx, heavy, top, cy)
            }
            0x251b -> { // ┛
                hline(cy, heavy, left, cx)
                vline(cx, heavy, top, cy)
            }
            0x251c -> { // ├
                hline(cy, thin, cx, right)
                vline(cx, thin)
            }
            0x251d -> { // ┝
                hline(cy, heavy, cx, right)
                vline(cx, thin)
            }
            0x251e -> { // ┞
                hline(cy, heavy, cx, right)
                vline(cx, thin, top, cy)
                vline(cx, thin, cy, bottom)
            }
            0x2520 -> { // ├ heavy vertical
                hline(cy, thin, cx, right)
                vline(cx, heavy)
            }
            0x2521 -> { // ┡
                hline(cy, heavy, cx, right)
                vline(cx, thin, top, cy)
                vline(cx, heavy, cy, bottom)
            }
            0x2522 -> { // ┢
                hline(cy, heavy, cx, right)
                vline(cx, heavy, top, cy)
                vline(cx, thin, cy, bottom)
            }
            0x2523 -> { // ┣
                hline(cy, heavy, cx, right)
                vline(cx, heavy)
            }
            0x2524 -> { // ┤
                hline(cy, thin, left, cx)
                vline(cx, thin)
            }
            0x2525 -> { // ┥
                hline(cy, heavy, left, cx)
                vline(cx, thin)
            }
            0x2528 -> { // ┨
                hline(cy, thin, left, cx)
                vline(cx, heavy)
            }
            0x2529 -> { // ┩
                hline(cy, heavy, left, cx)
                vline(cx, thin, top, cy)
                vline(cx, heavy, cy, bottom)
            }
            0x252a -> { // ┪
                hline(cy, heavy, left, cx)
                vline(cx, heavy, top, cy)
                vline(cx, thin, cy, bottom)
            }
            0x252b -> { // ┫
                hline(cy, heavy, left, cx)
                vline(cx, heavy)
            }
            0x252c -> { // ┬
                hline(cy, thin)
                vline(cx, thin, top, cy)
            }
            0x252f -> { // ┯
                hline(cy, thin)
                vline(cx, heavy, top, cy)
            }
            0x2530 -> { // ┰
                hline(cy, heavy)
                vline(cx, thin, top, cy)
            }
            0x2533 -> { // ┳
                hline(cy, heavy)
                vline(cx, heavy, top, cy)
            }
            0x2534 -> { // ┴
                hline(cy, thin)
                vline(cx, thin, cy, bottom)
            }
            0x2537 -> { // ┷
                hline(cy, thin)
                vline(cx, heavy, cy, bottom)
            }
            0x2538 -> { // ┸
                hline(cy, heavy)
                vline(cx, thin, cy, bottom)
            }
            0x253b -> { // ┻
                hline(cy, heavy)
                vline(cx, heavy, cy, bottom)
            }
            0x253c -> { // ┼
                hline(cy, thin)
                vline(cx, thin)
            }
            0x253f -> { // ┿
                hline(cy, thin)
                vline(cx, heavy)
            }
            0x2540 -> { // ╀
                hline(cy, heavy)
                vline(cx, thin)
            }
            0x2541 -> { // ╁
                hline(cy, thin)
                vline(cx, heavy)
            }
            0x2542 -> { // ╂
                hline(cy, heavy)
                vline(cx, heavy)
            }
            0x254b -> { // ╋
                hline(cy, heavy)
                vline(cx, heavy)
            }
            0x254c -> hline(cy, thin, left, left + w / 2f) // ╌
            0x254d -> hline(cy, heavy, left, left + w / 2f) // ╍
            0x254e -> hline(cy, thin, left + w / 2f, right) // ╎
            0x254f -> hline(cy, heavy, left + w / 2f, right) // ╏
            0x2550 -> { // ═ double horizontal
                hline(cy - thin, thin)
                hline(cy + thin, thin)
            }
            0x2551 -> { // ║ double vertical
                vline(cx - thin, thin)
                vline(cx + thin, thin)
            }
            0x2552 -> { // ╒
                hline(cy, heavy, cx, right)
                vline(cx, thin, cy, bottom)
            }
            0x2553 -> { // ╓
                hline(cy, thin, cx, right)
                vline(cx, heavy, cy, bottom)
            }
            0x2554 -> { // ╔
                hline(cy - thin, thin, cx - thin, right)
                hline(cy + thin, thin, cx + thin, right)
                vline(cx - thin, thin, cy - thin, bottom)
                vline(cx + thin, thin, cy + thin, bottom)
            }
            0x2555 -> { // ╕
                hline(cy, heavy, left, cx)
                vline(cx, thin, cy, bottom)
            }
            0x2556 -> { // ╖
                hline(cy, thin, left, cx)
                vline(cx, heavy, cy, bottom)
            }
            0x2557 -> { // ╗
                hline(cy - thin, thin, left, cx + thin)
                hline(cy + thin, thin, left, cx - thin)
                vline(cx - thin, thin, cy - thin, bottom)
                vline(cx + thin, thin, cy + thin, bottom)
            }
            0x2558 -> { // ╘
                hline(cy, heavy, cx, right)
                vline(cx, thin, top, cy)
            }
            0x2559 -> { // ╙
                hline(cy, thin, cx, right)
                vline(cx, heavy, top, cy)
            }
            0x255a -> { // ╚
                hline(cy - thin, thin, cx - thin, right)
                hline(cy + thin, thin, cx + thin, right)
                vline(cx - thin, thin, top, cy + thin)
                vline(cx + thin, thin, top, cy - thin)
            }
            0x255b -> { // ╛
                hline(cy, heavy, left, cx)
                vline(cx, thin, top, cy)
            }
            0x255c -> { // ╜
                hline(cy, thin, left, cx)
                vline(cx, heavy, top, cy)
            }
            0x255d -> { // ╝
                hline(cy - thin, thin, left, cx + thin)
                hline(cy + thin, thin, left, cx - thin)
                vline(cx - thin, thin, top, cy + thin)
                vline(cx + thin, thin, top, cy - thin)
            }
            0x255e -> { // ╞
                hline(cy, heavy, cx, right)
                vline(cx, thin)
            }
            0x255f -> { // ╟
                hline(cy, thin, cx, right)
                vline(cx, heavy)
            }
            0x2560 -> { // ╠
                hline(cy - thin, thin, cx - thin, right)
                hline(cy + thin, thin, cx - thin, right)
                vline(cx - thin, thin)
                vline(cx + thin, thin)
            }
            0x2561 -> { // ╡
                hline(cy, heavy, left, cx)
                vline(cx, thin)
            }
            0x2562 -> { // ╢
                hline(cy, thin, left, cx)
                vline(cx, heavy)
            }
            0x2563 -> { // ╣
                hline(cy - thin, thin, left, cx + thin)
                hline(cy + thin, thin, left, cx - thin)
                vline(cx - thin, thin)
                vline(cx + thin, thin)
            }
            0x2564 -> { // ╤
                hline(cy - thin, thin)
                hline(cy + thin, thin)
                vline(cx, heavy, top, cy)
            }
            0x2565 -> { // ╥
                vline(cx - thin, thin, top, cy)
                vline(cx + thin, thin, top, cy)
                hline(cy, heavy)
            }
            0x2566 -> { // ╦
                hline(cy - thin, thin)
                hline(cy + thin, thin)
                vline(cx - thin, thin, top, cy + thin)
                vline(cx + thin, thin, top, cy + thin)
            }
            0x2567 -> { // ╧
                hline(cy - thin, thin)
                hline(cy + thin, thin)
                vline(cx, heavy, cy, bottom)
            }
            0x2568 -> { // ╨
                vline(cx - thin, thin, cy, bottom)
                vline(cx + thin, thin, cy, bottom)
                hline(cy, heavy)
            }
            0x2569 -> { // ╩
                hline(cy - thin, thin)
                hline(cy + thin, thin)
                vline(cx - thin, thin, cy - thin, bottom)
                vline(cx + thin, thin, cy - thin, bottom)
            }
            0x256a -> { // ╪
                hline(cy - thin, thin)
                hline(cy + thin, thin)
                vline(cx, heavy)
            }
            0x256b -> { // ╫
                hline(cy, heavy)
                vline(cx - thin, thin)
                vline(cx + thin, thin)
            }
            0x256c -> { // ╬
                hline(cy - thin, thin)
                hline(cy + thin, thin)
                vline(cx - thin, thin)
                vline(cx + thin, thin)
            }
            0x256d -> { // ╭ rounded corner
                hline(cy, thin, cx, right)
                vline(cx, thin, top, cy)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thin
                canvas.drawArc(
                    left,
                    top,
                    left + 4 * thin,
                    top + 4 * thin,
                    180f,
                    90f,
                    false,
                    paint,
                )
            }
            0x256e -> { // ╮
                hline(cy, thin, left, cx)
                vline(cx, thin, top, cy)
            }
            0x256f -> { // ╯
                hline(cy, thin, left, cx)
                vline(cx, thin, cy, bottom)
            }
            0x2570 -> { // ╰
                hline(cy, thin, cx, right)
                vline(cx, thin, cy, bottom)
            }
            0x2571 -> { // ╱
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thin
                canvas.drawLine(left, bottom, right, top, paint)
            }
            0x2572 -> { // ╲
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thin
                canvas.drawLine(left, top, right, bottom, paint)
            }
            0x2573 -> { // ╳
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = thin
                canvas.drawLine(left, bottom, right, top, paint)
                canvas.drawLine(left, top, right, bottom, paint)
            }
            0x2574 -> hline(cy, thin, left, cx) // ╴
            0x2575 -> vline(cx, thin, top, cy) // ╵
            0x2576 -> hline(cy, thin, cx, right) // ╶
            0x2577 -> vline(cx, thin, cy, bottom) // ╷
            0x2578 -> hline(cy, heavy, left, cx) // ╸
            0x2579 -> vline(cx, heavy, top, cy) // ╹
            0x257a -> hline(cy, heavy, cx, right) // ╺
            0x257b -> vline(cx, heavy, cy, bottom) // ╻
            0x257c -> { // ╼
                hline(cy, heavy, left, cx)
                hline(cy, thin, cx, right)
            }
            0x257d -> { // ╽
                vline(cx, heavy, top, cy)
                vline(cx, thin, cy, bottom)
            }
            0x257e -> { // ╾
                hline(cy, thin, left, cx)
                hline(cy, heavy, cx, right)
            }
            0x257f -> { // ╿
                vline(cx, thin, top, cy)
                vline(cx, heavy, cy, bottom)
            }
            // Blocks
            0x2580 -> fill(left, top, right, cy) // ▀
            0x2581 -> fill(left, bottom - eighth, right, bottom) // ▁
            0x2582 -> fill(left, bottom - 2 * eighth, right, bottom) // ▂
            0x2583 -> fill(left, bottom - 3 * eighth, right, bottom) // ▃
            0x2584 -> fill(left, cy, right, bottom) // ▄
            0x2585 -> fill(left, bottom - 5 * eighth, right, bottom) // ▅
            0x2586 -> fill(left, bottom - 6 * eighth, right, bottom) // ▆
            0x2587 -> fill(left, bottom - 7 * eighth, right, bottom) // ▇
            0x2588 -> fill(left, top, right, bottom) // █
            0x2589 -> fill(left, top, right - halfW, bottom) // ▉
            0x258a -> fill(left, top, right - 2 * halfW, bottom) // ▊
            0x258b -> fill(left, top, right - 3 * halfW, bottom) // ▋
            0x258c -> fill(left, top, cx, bottom) // ▌
            0x258d -> fill(left, top, left + 3 * halfW, bottom) // ▍
            0x258e -> fill(left, top, left + 2 * halfW, bottom) // ▎
            0x258f -> fill(left, top, left + halfW, bottom) // ▏
            0x2590 -> fill(cx, top, right, bottom) // ▐
            0x2591,
            0x2592,
            0x2593 -> { // ░ ▒ ▓
                paint.style = Paint.Style.FILL
                paint.alpha =
                    when (ch.code) {
                        0x2591 -> 64
                        0x2592 -> 128
                        else -> 192
                    }
                canvas.drawRect(left, top, right, bottom, paint)
                paint.alpha = 255
            }
            0x2594 -> fill(left, top, right, top + eighth) // ▔
            0x2595 -> fill(right - halfW, top, right, bottom) // ▕
            else -> {} // not handled — caller falls back to the font glyph
        }
    }
}
