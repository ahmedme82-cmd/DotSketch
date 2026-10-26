package com.dotsketch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutTest {

    private val settings = PageSettings(
        paperSize = PaperSize.A4,
        orientation = PageOrientation.PORTRAIT,
        marginMm = 10f,
        center = true,
        fitToPage = true,
        userScale = 1f
    )
    private val style = DotStyle(spacingMm = 3f, diameterMm = 1f)
    private val square = listOf(
        Point2f(0f, 0f), Point2f(100f, 0f), Point2f(100f, 100f), Point2f(0f, 100f)
    )

    @Test fun mmToPt() {
        assertEquals(72f, PageLayout.mmToPt(25.4f), 0.001f)
        assertEquals(595.28f, PageLayout.mmToPt(210f), 0.01f)
    }

    @Test fun landscapeSwapsDimensions() {
        val (w, h) = PageLayout.pageDimensionsMm(settings.copy(orientation = PageOrientation.LANDSCAPE))
        assertEquals(297f, w, 0.001f)
        assertEquals(210f, h, 0.001f)
    }

    @Test fun dotsStayInsideMargins() {
        val result = PageLayout.layoutFromContours(listOf(square), 100, 100, settings, style)
        assertNull(result.warning)
        assertTrue(result.dots.isNotEmpty())
        for (d in result.dots) {
            assertTrue(d.xMm - d.radiusMm >= settings.marginMm - 1e-3f)
            assertTrue(d.xMm + d.radiusMm <= 210f - settings.marginMm + 1e-3f)
            assertTrue(d.yMm - d.radiusMm >= settings.marginMm - 1e-3f)
            assertTrue(d.yMm + d.radiusMm <= 297f - settings.marginMm + 1e-3f)
        }
    }

    @Test fun squareImageIsCenteredVertically() {
        val result = PageLayout.layoutFromContours(listOf(square), 100, 100, settings, style)
        val minY = result.dots.minOf { it.yMm }
        val maxY = result.dots.maxOf { it.yMm }
        assertEquals(297f / 2f, (minY + maxY) / 2f, 0.5f)
    }

    @Test fun noContoursGivesWarning() {
        val result = PageLayout.layoutFromContours(emptyList(), 100, 100, settings, style)
        assertTrue(result.dots.isEmpty())
        assertNotNull(result.warning)
    }

    @Test fun hugeMarginGivesWarning() {
        val result = PageLayout.layoutFromContours(listOf(square), 100, 100, settings.copy(marginMm = 150f), style)
        assertTrue(result.dots.isEmpty())
        assertNotNull(result.warning)
    }

    @Test fun spacingIsTrueMillimetres() {
        // 100px square fitted into a 188mm-wide box -> 3 mm spacing = a few dots per ~188mm side
        val result = PageLayout.layoutFromContours(listOf(square), 100, 100, settings, DotStyle(5f, 1f))
        val a = result.dots[0]
        val b = result.dots[1]
        val dist = Math.hypot((b.xMm - a.xMm).toDouble(), (b.yMm - a.yMm).toDouble())
        assertEquals(5.0, dist, 0.35)
    }

    @Test fun fitOffNeverOverflowsPage() {
        val g = PageLayout.computeGeometry(5000, 5000, settings.copy(fitToPage = false), 1f, 210f, 297f)
        assertNotNull(g)
        assertNotNull(g!!.warning)
        assertTrue(5000 * g.scaleMmPerPixel <= 210f - 2 * 10.5f + 0.01f)
    }
}
