package com.dotsketch.core

import kotlin.math.abs

object ContourFilter {

    /** Shoelace area of a closed loop (absolute value). */
    fun area(loop: List<Point2f>): Float {
        if (loop.size < 3) return 0f
        var sum = 0.0
        for (i in loop.indices) {
            val a = loop[i]
            val b = loop[(i + 1) % loop.size]
            sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return abs(sum / 2.0).toFloat()
    }

    /** Drops loops (outer borders and holes alike) smaller than [minAreaPx]. Removes speckle noise. */
    fun filterByArea(contours: List<List<Point2f>>, minAreaPx: Float): List<List<Point2f>> =
        contours.filter { area(it) >= minAreaPx }
}
