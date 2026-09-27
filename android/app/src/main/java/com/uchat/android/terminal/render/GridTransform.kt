package com.uchat.android.terminal.render

import kotlin.math.min

/**
 * Pure geometry for placing the terminal grid inside the viewport.
 *
 * Two modes (user-configurable in Terminal settings):
 * - **fit screen**: the grid is derived from the viewport at 1:1 scale — identity transform.
 *   Exception: when the font is so large that fewer than [minCols] columns fit (see
 *   [TerminalController.MIN_COLS]), the grid is raised to that minimum and uniformly scaled DOWN
 *   and centered (exactly like fixed mode) so the full width stays visible instead of the right
 *   edge being clipped — there is no horizontal scrolling to reveal it.
 * - **fixed size**: the emulator uses the exact user-picked columns/rows. When that grid is larger
 *   than the screen it is uniformly scaled DOWN (never up, to keep glyphs crisp) and centered, so
 *   the full grid stays visible.
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
                min(1f, min(viewportWidth / gridWidth, viewportHeight / gridHeight))
                    .coerceAtLeast(0.05f)
            val offsetX = ((viewportWidth - gridWidth * scale) / 2f).coerceAtLeast(0f)
            val offsetY = ((viewportHeight - gridHeight * scale) / 2f).coerceAtLeast(0f)
            return GridTransform(scale, offsetX, offsetY)
        }

        /**
         * Fit-screen placement for a viewport-derived grid of [cols]×[rows].
         * - [cols] ≥ [minCols]: identity — every derived column is drawn 1:1.
         * - [cols] < [minCols]: the grid is widened to [minCols] and [solve] scales it down and
         *   centers it, so the whole (wider) grid fits the viewport. The returned [Fitted.cols] is
         *   what [com.uchat.android.terminal.TerminalController.onGridViewport] must receive.
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
