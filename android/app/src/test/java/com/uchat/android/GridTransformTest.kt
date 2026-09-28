package com.uchat.android

import com.uchat.android.terminal.TerminalController
import com.uchat.android.terminal.render.GridTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry contract for the terminal grid (fit-screen vs fixed-size modes).
 *
 * REGRESSION (v1.9.0): fixed grids were CENTERED and capped at scale 1.0, so the text block floated
 * in the middle of the screen with huge empty margins — the field report "teksnya hanya sampai ke
 * tengah layar, tidak bisa full layar penuh". The contract is now: fill the viewport as completely
 * as possible (uniform scale, up OR down), anchor TOP-LEFT, never center.
 */
class GridTransformTest {

    private val cellW = 10f
    private val cellH = 20f

    @Test
    fun `fit screen is identity`() {
        val t =
            GridTransform.solve(
                viewportWidth = 1080f,
                viewportHeight = 2000f,
                cols = 80,
                rows = 24,
                cellWidth = cellW,
                cellHeight = cellH,
                fitScreen = true,
            )
        assertEquals(1f, t.scale, 1e-6f)
        assertEquals(0f, t.offsetX, 1e-6f)
        assertEquals(0f, t.offsetY, 1e-6f)
    }

    @Test
    fun `fixed grid narrower than viewport scales UP to fill the width`() {
        // 40 cols × 10 rows = 400×200 px inside a 1000×1000 viewport → width-bound 2.5x.
        val t = GridTransform.solve(1000f, 1000f, cols = 40, rows = 10, cellW, cellH, false)
        assertEquals(2.5f, t.scale, 1e-4f)
        assertEquals(0f, t.offsetX, 1e-6f)
        assertEquals(0f, t.offsetY, 1e-6f)
    }

    @Test
    fun `fixed grid wider than viewport scales down to fill the width`() {
        // 120 cols × 20 rows = 1200×400 px inside an 600×1000 viewport → width-bound 0.5x.
        val t = GridTransform.solve(600f, 1000f, 120, 20, cellW, cellH, fitScreen = false)
        assertEquals(600f / 1200f, t.scale, 1e-6f)
        assertEquals(0f, t.offsetX, 1e-4f)
        assertEquals(0f, t.offsetY, 1e-4f)
    }

    @Test
    fun `fixed grid taller than viewport scales down to fill the height`() {
        val t = GridTransform.solve(2000f, 400f, 40, 50, cellW, cellH, fitScreen = false)
        assertEquals(400f / (50 * cellH), t.scale, 1e-6f)
        assertEquals(0f, t.offsetX, 1e-4f)
        assertEquals(0f, t.offsetY, 1e-4f)
    }

    @Test
    fun `upscaled grid never floats in the middle — offsets are always zero`() {
        // The v1.9.0 bug: centered offsets letterboxed the grid on all four sides. Top-left
        // anchoring means spare space only ever appears at the bottom/right, like any terminal.
        listOf(
                GridTransform.solve(1000f, 1000f, 40, 10, cellW, cellH, false),
                GridTransform.solve(600f, 1000f, 120, 20, cellW, cellH, false),
                GridTransform.solve(2000f, 400f, 40, 50, cellW, cellH, false),
                GridTransform.solve(500f, 700f, 200, 80, cellW, cellH, false),
            )
            .forEach { t ->
                assertEquals(0f, t.offsetX, 1e-6f)
                assertEquals(0f, t.offsetY, 1e-6f)
            }
    }

    @Test
    fun `upscale is capped at 8x so tiny grids stay usable`() {
        // 20×10 grid (200×200 px) inside 4000×4000 px would naively want 20x — clamped to 8x.
        val t = GridTransform.solve(4000f, 4000f, 20, 10, cellW, cellH, fitScreen = false)
        assertEquals(8f, t.scale, 1e-6f)
    }

    @Test
    fun `downscale is floored so giant grids stay renderable`() {
        // 300 cols × 200 rows of 10×20 px cells inside a tiny viewport would want ~0.008x.
        val t = GridTransform.solve(100f, 100f, 300, 200, cellW, cellH, fitScreen = false)
        assertEquals(0.05f, t.scale, 1e-6f)
    }

    @Test
    fun `degenerate inputs fall back to identity`() {
        val zeroCells = GridTransform.solve(100f, 100f, 80, 24, 0f, 0f, fitScreen = false)
        assertEquals(GridTransform.IDENTITY, zeroCells)
        val zeroGrid = GridTransform.solve(100f, 100f, 0, 0, cellW, cellH, fitScreen = false)
        assertEquals(GridTransform.IDENTITY, zeroGrid)
    }

    @Test
    fun `scaled grid always fits the viewport`() {
        val t = GridTransform.solve(500f, 700f, 200, 80, cellW, cellH, fitScreen = false)
        assertTrue(200 * cellW * t.scale <= 500f + 1e-3f)
        assertTrue(80 * cellH * t.scale <= 700f + 1e-3f)
    }

    @Test
    fun `width-bound scale fills the viewport width exactly`() {
        // The whole point of the fix: the text must reach BOTH screen edges in the common case.
        val t1 = GridTransform.solve(600f, 1000f, 120, 20, cellW, cellH, false)
        assertEquals(600f, 120 * cellW * t1.scale, 1e-3f)
        val t2 = GridTransform.solve(1080f, 2000f, 80, 24, 22f, 44f, false)
        assertEquals(1080f, 80 * 22f * t2.scale, 1e-3f)
    }

    // ---------------------------------------------- fit screen with the MIN_COLS auto-fit floor

    @Test
    fun `fit screen with enough columns stays identity`() {
        val fit =
            GridTransform.solveFitScreen(
                viewportWidth = 1080f,
                viewportHeight = 2000f,
                cols = 80,
                rows = 24,
                cellWidth = cellW,
                cellHeight = cellH,
                minCols = 20,
            )
        assertEquals(GridTransform.IDENTITY, fit.transform)
        assertEquals(80, fit.cols)
    }

    @Test
    fun `fit screen narrower than min cols widens grid and scales down top-left`() {
        // Large font: only 12 columns fit in 150 px, but the emulator must stay at 20 columns
        // (200 px at cellW = 10) — the grid is scaled to 0.75 and anchored top-left so nothing
        // is clipped and nothing floats mid-screen.
        val fit = GridTransform.solveFitScreen(150f, 800f, cols = 12, rows = 10, cellW, cellH, 20)
        assertEquals(20, fit.cols)
        assertEquals(150f / 200f, fit.transform.scale, 1e-6f)
        assertEquals(0f, fit.transform.offsetX, 1e-4f)
        assertEquals(0f, fit.transform.offsetY, 1e-4f)
    }

    @Test
    fun `fit screen narrow grid is fully visible within tolerance`() {
        val fit = GridTransform.solveFitScreen(150f, 800f, cols = 12, rows = 10, cellW, cellH, 20)
        assertTrue(20 * cellW * fit.transform.scale <= 150f + 1e-3f)
        assertTrue(10 * cellH * fit.transform.scale <= 800f + 1e-3f)
        assertTrue(fit.transform.offsetX >= 0f)
        assertTrue(fit.transform.offsetY >= 0f)
    }

    @Test
    fun `fit screen narrow grid honors the real controller minimum`() {
        // One pixel short of fitting MIN_COLS at 1:1 — must widen to MIN_COLS and shrink.
        val fit =
            GridTransform.solveFitScreen(
                viewportWidth = TerminalController.MIN_COLS * cellW - 1f,
                viewportHeight = 1000f,
                cols = 2,
                rows = 10,
                cellWidth = cellW,
                cellHeight = cellH,
                minCols = TerminalController.MIN_COLS,
            )
        assertEquals(TerminalController.MIN_COLS, fit.cols)
        assertTrue(fit.transform.scale < 1f)
    }

    @Test
    fun `fit screen scaled narrow grid inverse maps back onto the grid`() {
        // cellAt() divides by scale and subtracts offsets — verify the round trip: applying the
        // inverse to the viewport edges must land inside the widened grid and compose back.
        val fit = GridTransform.solveFitScreen(150f, 800f, cols = 12, rows = 10, cellW, cellH, 20)
        val t = fit.transform
        fun inverseX(px: Float) = (px - t.offsetX) / t.scale
        fun forwardX(gridX: Float) = gridX * t.scale + t.offsetX
        val left = inverseX(0f)
        val right = inverseX(150f)
        assertTrue(left >= -1e-3f) // left viewport edge stays inside column 0
        assertTrue(right <= 20 * cellW + 1e-3f) // right edge inside the (widened) grid
        assertEquals(150f / t.scale, right - left, 1e-3f) // viewport spans the widened grid
        assertEquals(0f, forwardX(left), 1e-3f) // and composing forward restores the pixel
        assertEquals(150f, forwardX(right), 1e-3f)
    }
}
