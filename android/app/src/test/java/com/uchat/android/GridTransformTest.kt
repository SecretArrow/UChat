package com.uchat.android

import com.uchat.android.terminal.render.GridTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry contract for the terminal grid (fit-screen vs fixed-size modes). */
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
    fun `fixed grid smaller than viewport is centered unscaled`() {
        // 40 cols × 10 rows = 400×200 px inside a 1000×1000 viewport.
        val t =
            GridTransform.solve(1000f, 1000f, cols = 40, rows = 10, cellW, cellH, fitScreen = false)
        assertEquals(1f, t.scale, 1e-6f)
        assertEquals((1000f - 400f) / 2f, t.offsetX, 1e-6f)
        assertEquals((1000f - 200f) / 2f, t.offsetY, 1e-6f)
    }

    @Test
    fun `fixed grid wider than viewport scales down uniformly`() {
        // 120 cols × 20 rows = 1200×400 px inside an 600×1000 viewport.
        val t = GridTransform.solve(600f, 1000f, 120, 20, cellW, cellH, fitScreen = false)
        assertEquals(600f / 1200f, t.scale, 1e-6f)
        assertEquals(0f, t.offsetX, 1e-4f)
        assertEquals((1000f - 400f * 0.5f) / 2f, t.offsetY, 1e-4f)
    }

    @Test
    fun `fixed grid taller than viewport scales down uniformly`() {
        val t = GridTransform.solve(2000f, 400f, 40, 50, cellW, cellH, fitScreen = false)
        assertEquals(400f / (50 * cellH), t.scale, 1e-6f)
    }

    @Test
    fun `never scales up beyond 1`() {
        val t = GridTransform.solve(4000f, 4000f, 20, 10, cellW, cellH, fitScreen = false)
        assertEquals(1f, t.scale, 1e-6f)
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
}
