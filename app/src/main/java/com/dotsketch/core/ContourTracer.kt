package com.dotsketch.core

/**
 * Marching-squares boundary tracer. Replaces OpenCV findContours.
 *
 * Input: a binary image (true = foreground). Output: closed loops (outer borders AND hole
 * borders) in pixel coordinates. Pure Kotlin, no Android dependencies.
 *
 * Works on a doubled integer grid so edge midpoints have integer keys.
 * Segments are oriented so the foreground is always on the right (y axis pointing down),
 * which guarantees every midpoint has exactly one outgoing segment and loops close.
 */
object ContourTracer {

    fun trace(binary: BooleanArray, width: Int, height: Int): List<List<Point2f>> {
        require(binary.size == width * height) { "binary size must equal width*height" }
        if (width == 0 || height == 0) return emptyList()

        fun v(x: Int, y: Int): Boolean =
            x in 0 until width && y in 0 until height && binary[y * width + x]

        fun key(x: Int, y: Int): Long = ((x + 2).toLong() shl 32) or (y + 2).toLong()

        val next = HashMap<Long, Long>()
        fun seg(x1: Int, y1: Int, x2: Int, y2: Int) {
            next[key(x1, y1)] = key(x2, y2)
        }

        for (i in -1 until height) {
            for (j in -1 until width) {
                val tl = v(j, i)
                val tr = v(j + 1, i)
                val br = v(j + 1, i + 1)
                val bl = v(j, i + 1)
                val idx = (if (tl) 8 else 0) or (if (tr) 4 else 0) or
                    (if (br) 2 else 0) or (if (bl) 1 else 0)
                if (idx == 0 || idx == 15) continue

                // edge midpoints in doubled coordinates
                val tx = 2 * j + 1; val ty = 2 * i          // top
                val rx = 2 * j + 2; val ry = 2 * i + 1      // right
                val bx = 2 * j + 1; val by = 2 * i + 2      // bottom
                val lx = 2 * j;     val ly = 2 * i + 1      // left

                when (idx) {
                    1 -> seg(lx, ly, bx, by)
                    2 -> seg(bx, by, rx, ry)
                    3 -> seg(lx, ly, rx, ry)
                    4 -> seg(rx, ry, tx, ty)
                    5 -> { seg(rx, ry, tx, ty); seg(lx, ly, bx, by) }   // saddle, kept separate
                    6 -> seg(bx, by, tx, ty)
                    7 -> seg(lx, ly, tx, ty)
                    8 -> seg(tx, ty, lx, ly)
                    9 -> seg(tx, ty, bx, by)
                    10 -> { seg(tx, ty, lx, ly); seg(bx, by, rx, ry) }  // saddle, kept separate
                    11 -> seg(tx, ty, rx, ry)
                    12 -> seg(rx, ry, lx, ly)
                    13 -> seg(rx, ry, bx, by)
                    14 -> seg(bx, by, lx, ly)
                }
            }
        }

        val visited = HashSet<Long>(next.size * 2)
        val loops = ArrayList<List<Point2f>>()

        for (start in next.keys.toList()) {
            if (start in visited) continue
            val loop = ArrayList<Point2f>()
            var cur = start
            var closed = false
            while (true) {
                if (!visited.add(cur)) {
                    closed = cur == start
                    break
                }
                loop.add(decode(cur))
                cur = next[cur] ?: break
            }
            if (closed && loop.size >= 3) loops.add(loop)
        }
        return loops
    }

    private fun decode(key: Long): Point2f {
        val x = (key shr 32).toInt() - 2
        val y = (key and 0xFFFFFFFFL).toInt() - 2
        return Point2f(x / 2f, y / 2f)
    }
}
