package com.dotsketch.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ContourFilterTest {

    private fun square(size: Float) = listOf(
        Point2f(0f, 0f), Point2f(size, 0f), Point2f(size, size), Point2f(0f, size)
    )

    @Test fun areaOfSquare() {
        assertEquals(100f, ContourFilter.area(square(10f)), 0.001f)
    }

    @Test fun areaIsOrientationIndependent() {
        assertEquals(100f, ContourFilter.area(square(10f).reversed()), 0.001f)
    }

    @Test fun smallLoopsAreDropped() {
        val kept = ContourFilter.filterByArea(listOf(square(10f), square(2f)), 10f)
        assertEquals(1, kept.size)
        assertEquals(100f, ContourFilter.area(kept[0]), 0.001f)
    }

    @Test fun degenerateLoopHasZeroArea() {
        assertEquals(0f, ContourFilter.area(listOf(Point2f(0f, 0f), Point2f(1f, 1f))), 0f)
    }
}
