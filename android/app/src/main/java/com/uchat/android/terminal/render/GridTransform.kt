package com.uchat.android.terminal.render

import kotlin.math.min

/**
 * Pure geometry for placing the terminal grid inside the viewport.
 *
 * Two modes (user-configurable in Terminal settings):
 * - **fit screen**: the grid is derived from the viewport at 1:1 scale — identity transform.
 * - **fixed size**: the emulator uses the exact user-picked columns/rows. When that grid is larger
 *   than the screen it is uniformly scaled DOWN (never up, to keep glyphs crisp) and centered, so
 *   the full grid stays visible.
 */
data class GridTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {

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
    }
}
