package com.uchat.android.terminal.render

import kotlin.math.min

/**
 * Pure geometry for placing the terminal grid inside the viewport.
 *
 * Two modes (user-configurable in Terminal settings):
 * - **fit screen**: the grid is derived from the viewport at 1:1 scale — identity transform.
 *   Exception: when the font is so large that fewer than [minCols] columns fit (see
 *   [TerminalController.MIN_COLS]), the grid is raised to that minimum and uniformly scaled DOWN
 *   (exactly like fixed mode) so the full width stays visible instead of the right edge being
 *   clipped — there is no horizontal scrolling to reveal it.
 * - **fixed size**: the emulator uses the exact user-picked columns/rows. The grid is uniformly
 *   scaled to fill the viewport as completely as possible and anchored to the TOP-LEFT.
 *
 * REGRESSION (v1.9.0 and earlier): fixed grids were CENTERED and never scaled UP (`min(1f, …)`
 * cap), so e.g. a 105×34 preset on a portrait phone rendered as a small block floating in the
 * middle of the screen — the field report "teksnya hanya sampai ke tengah layar, tidak bisa full
 * layar penuh". Centering letterboxed all four sides; the 1.0 cap made small grids physically
 * unable to fill the screen. Both are gone:
 * - the scale is uncapped in BOTH directions. Text and box-drawing are rasterized under the full
 *   canvas transform (vector outlines, not bitmaps), so upscaling stays crisp;
 * - anchoring is TOP-LEFT: a width-bound grid fills 100% of the width with any spare space at the
 *   bottom (a normal terminal look), a height-bound grid fills 100% of the height left-aligned. The
 *   grid can never float in the middle again.
 */
data class GridTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {

    /**
     * Outcome of the fit-screen computation: the placement plus the column count the emulator must
     * use (may be raised to the MIN_COLS floor).
     */
    data class Fitted(val transform: GridTransform, val cols: Int)

    companion object {

        /** Identity placement used by fit-screen mode. */
        val IDENTITY = GridTransform(1f, 0f, 0f)

        /**
         * Contain-and-fill placement for a fixed [cols]×[rows] grid: uniform scale (up or down) so
         * the grid fits entirely inside the viewport, anchored top-left. Width-bound scales fill
         * the full width; height-bound scales fill the full height.
         */
        fun solve(
            viewportWidth: Float,
            viewportHeight: Float,
            cols: Int,
            rows: Int,
            cellWidth: Float,
            cellHeight: Float,
            fitScreen: Boolean,
        ): GridTransform {
            if (fitScreen || cols <= 0 || rows <= 0 || cellWidth <= 0f || cellHeight <= 0f) {
                return IDENTITY
            }
            val gridWidth = cols * cellWidth
            val gridHeight = rows * cellHeight
            val scale =
                min(viewportWidth / gridWidth, viewportHeight / gridHeight).coerceIn(0.05f, 8f)
            // Top-left anchoring: spare space goes to the bottom/right, never around the grid.
            return GridTransform(scale, 0f, 0f)
        }

        /**
         * Fit-screen placement for a viewport-derived grid of [cols]×[rows].
         * - [cols] ≥ [minCols]: identity — every derived column is drawn 1:1.
         * - [cols] < [minCols]: the grid is widened to [minCols] and [solve] scales it down so the
         *   whole (wider) grid fits the viewport. The returned [Fitted.cols] is what
         *   [com.uchat.android.terminal.TerminalController.onGridViewport] must receive.
         */
        fun solveFitScreen(
            viewportWidth: Float,
            viewportHeight: Float,
            cols: Int,
            rows: Int,
            cellWidth: Float,
            cellHeight: Float,
            minCols: Int,
        ): Fitted {
            if (cols >= minCols) return Fitted(IDENTITY, cols)
            val widened = maxOf(cols, minCols)
            return Fitted(
                solve(
                    viewportWidth,
                    viewportHeight,
                    widened,
                    rows,
                    cellWidth,
                    cellHeight,
                    fitScreen = false,
                ),
                widened,
            )
        }
    }
}
