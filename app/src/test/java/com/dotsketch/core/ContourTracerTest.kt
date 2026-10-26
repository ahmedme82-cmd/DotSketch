package com.dotsketch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourTracerTest {

    private fun grid(w: Int, h: Int, fill: (Int, Int) -> Boolean): BooleanArray =
        BooleanArray(w * h) { fill(it % w, it / w) }

    @Test fun emptyImageHasNoContours() {
        assertTrue(ContourTracer.trace(BooleanArray(9), 3, 3).isEmpty())
    }

    @Test fun solidBlockGivesOneClosedLoop() {
        val b = grid(5, 5) { x, y -> x in 1..3 && y in 1..3 }
        val loops = ContourTracer.trace(b, 5, 5)
        assertEquals(1, loops.size)
        // diagonal corner cuts: 9 - 4 * 0.125 = 8.5
        assertEquals(8.5f, ContourFilter.area(loops[0]), 0.01f)
    }

    @Test fun ringGivesOuterAndHoleLoops() {
        val b = grid(7, 7) { x, y -> x in 1..5 && y in 1..5 && !(x == 3 && y == 3) }
        val loops = ContourTracer.trace(b, 7, 7)
        assertEquals(2, loops.size)
        val areas = loops.map { ContourFilter.area(it) }.sorted()
        assertEquals(0.5f, areas[0], 0.01f)
        assertEquals(24.5f, areas[1], 0.01f)
    }

    @Test fun saddleKeepsDiagonalPixelsSeparate() {
        val b = booleanArrayOf(true, false, false, true)
        assertEquals(2, ContourTracer.trace(b, 2, 2).size)
    }

    @Test fun fullImageTracesTheBorder() {
        assertEquals(1, ContourTracer.trace(BooleanArray(9) { true }, 3, 3).size)
    }
}
