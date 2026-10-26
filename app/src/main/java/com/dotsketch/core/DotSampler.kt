package com.dotsketch.core

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object DotSampler {

    fun sampleClosed(contours: List<List<Point2f>>, spacingPx: Float): List<Point2f> {
        if (spacingPx <= 0f) return emptyList()
        val result = ArrayList<Point2f>()
        for (contour in contours) {
            if (contour.size < 2) continue
            result.addAll(sampleLoop(contour, spacingPx))
        }
        return result
    }

    /**
     * Places dots at equal arc-length steps around a closed loop.
     * The step is nudged so the loop divides into a whole number of gaps,
     * so there is no cramped dot pair at the seam.
     */
    fun sampleLoop(points: List<Point2f>, spacingPx: Float): List<Point2f> {
        val n = points.size
        if (n < 2 || spacingPx <= 0f) return emptyList()

        val cum = DoubleArray(n + 1)
        for (i in 0 until n) {
            cum[i + 1] = cum[i] + distance(points[i], points[(i + 1) % n])
        }
        val total = cum[n]
        if (total <= 0.0) return emptyList()

        val count = max(1, (total / spacingPx).roundToInt())
        val step = total / count
        val out = ArrayList<Point2f>(count)

        var seg = 0
        for (k in 0 until count) {
            val target = k * step
            while (seg < n - 1 && cum[seg + 1] <= target) seg++
            val segLen = cum[seg + 1] - cum[seg]
            val t = if (segLen > 0.0) ((target - cum[seg]) / segLen).coerceIn(0.0, 1.0) else 0.0
            val a = points[seg]
            val b = points[(seg + 1) % n]
            out.add(Point2f((a.x + (b.x - a.x) * t).toFloat(), (a.y + (b.y - a.y) * t).toFloat()))
        }
        return out
    }

    private fun distance(a: Point2f, b: Point2f): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}
