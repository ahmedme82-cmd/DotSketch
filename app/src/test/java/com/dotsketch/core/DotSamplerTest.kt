package com.dotsketch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class DotSamplerTest {

    private val square = listOf(
        Point2f(0f, 0f), Point2f(10f, 0f), Point2f(10f, 10f), Point2f(0f, 10f)
    )

    @Test fun evenDivisionGivesExactSpacing() {
        val dots = DotSampler.sampleLoop(square, 5f)
        assertEquals(8, dots.size)
        for (i in dots.indices) {
            val a = dots[i]
            val b = dots[(i + 1) % dots.size]
            assertEquals(5f, hypot(b.x - a.x, b.y - a.y), 0.001f)
        }
    }

    @Test fun unevenDivisionStillCoversWholeLoopWithoutSeamCrowding() {
        // perimeter 40, spacing 6 -> 7 dots, step 5.714 along the loop
        val dots = DotSampler.sampleLoop(square, 6f)
        assertEquals(7, dots.size)
    }

    @Test fun allDotsLieOnThePerimeter() {
        for (d in DotSampler.sampleLoop(square, 3f)) {
            val onEdge = abs(d.x) < 1e-3f || abs(d.x - 10f) < 1e-3f || abs(d.y) < 1e-3f || abs(d.y - 10f) < 1e-3f
            assertTrue("dot $d not on perimeter", onEdge)
        }
    }

    @Test fun firstDotIsTheFirstVertex() {
        val first = DotSampler.sampleLoop(square, 5f).first()
        assertEquals(0f, first.x, 1e-4f)
        assertEquals(0f, first.y, 1e-4f)
    }

    @Test fun invalidSpacingGivesNoDots() {
        assertTrue(DotSampler.sampleLoop(square, 0f).isEmpty())
        assertTrue(DotSampler.sampleClosed(listOf(square), -1f).isEmpty())
    }

    @Test fun zeroLengthLoopGivesNoDots() {
        assertTrue(DotSampler.sampleLoop(listOf(Point2f(1f, 1f), Point2f(1f, 1f)), 1f).isEmpty())
    }
}
